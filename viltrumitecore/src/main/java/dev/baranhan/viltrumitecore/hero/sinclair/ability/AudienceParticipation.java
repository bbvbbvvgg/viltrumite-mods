package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.act.StageAct;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairLines;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScene;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyScene;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Audience Participation (spec §5.6, §6): capture a volunteer in a 6° cone ≤ 20 (20 t, the card flies out
 * at 14), hold the card for 240 t, press again to perform the chosen number. Copying reads only the
 * volunteer's data contract ({@code signatureAct}) and the public hero id / mob type.
 */
public final class AudienceParticipation {
   private AudienceParticipation() {
   }

   public static void press(ServerPlayer player, SinclairState state) {
      switch (state.ult) {
         case WINDOW -> perform(player, state);
         case CAPTURE, PERFORM -> {
         }
         case NONE -> capture(player, state);
      }
   }

   private static void capture(ServerPlayer player, SinclairState state) {
      if (state.ultCooldown > 0 || state.busy()) {
         SinclairHero.refuse(player, state);
         return;
      }

      LivingEntity target = volunteer(player);
      if (target == null) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.ult = SinclairState.Ult.CAPTURE;
      state.volunteer = target.getUUID();
      state.volunteerId = target.getId();
      state.captured = null;
      state.startMove(SinclairMove.CAPTURE);
      SinclairSounds.play(player.level(), target.position().add(0.0, target.getBbHeight() + 1.0, 0.0), SinclairSounds.ULT_CAPTURE, 1.0F, 1.0F);
      HeroSpeech.ability(player, dev.baranhan.viltrumitecore.hero.sinclair.SinclairAbilities.AUDIENCE_PARTICIPATION);
   }

   /** §5.6: the living player or mob nearest the crosshair in a 6° cone, ≤ 20 blocks, in line of sight. */
   @Nullable
   public static LivingEntity volunteer(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      AABB box = player.getBoundingBox().inflate(SinclairRules.ULT_RANGE);
      LivingEntity best = null;
      double bestCos = -2.0;
      for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> SinclairCombat.enemy(player, e))) {
         Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
         if (to.lengthSqr() > SinclairRules.ULT_RANGE * SinclairRules.ULT_RANGE
            || !SinclairRules.inCone(look.x, look.y, look.z, to.x, to.y, to.z, SinclairRules.ULT_CONE_DEGREES) || !player.hasLineOfSight(candidate)) {
            continue;
         }

         double cos = to.normalize().dot(look);
         if (cos > bestCos) {
            bestCos = cos;
            best = candidate;
         }
      }

      return best;
   }

   @Nullable
   public static LivingEntity volunteerEntity(ServerPlayer player, SinclairState state) {
      if (state.volunteerId < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(state.volunteerId);
      return entity instanceof LivingEntity living && living.isAlive() && living.getUUID().equals(state.volunteer) ? living : null;
   }

   /** §6.2: contract → repertoire → fallback. Only the pure data hook of the other hero is called. */
   public static SinclairRepertoire.Choice read(LivingEntity target) {
      if (target instanceof Player other && other instanceof HeroPlayer hero) {
         HeroId id = hero.getHeroId();
         StageAct contract = null;
         boolean read;
         try {
            contract = HeroRegistry.get(id).signatureAct(other);
            read = true;
         } catch (RuntimeException e) {
            read = false;
         }

         return SinclairRepertoire.choose(contract, read, id.key(), null);
      }

      ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
      return SinclairRepertoire.choose(null, false, null, type == null ? null : type.toString());
   }

   public static void tickMove(ServerPlayer player, SinclairState state) {
      if (state.move == SinclairMove.CAPTURE) {
         LivingEntity target = volunteerEntity(player, state);
         if (state.moveTick < SinclairRules.ULT_CARD_TICK && (target == null || target.distanceToSqr(player) > SinclairRules.ULT_RANGE * SinclairRules.ULT_RANGE * 2.25)) {
            // The volunteer left before the card came out: no number, no cooldown.
            cancel(state);
            state.clearMove();
            return;
         }

         if (state.moveTick == SinclairRules.ULT_CARD_TICK && target != null) {
            SinclairRepertoire.Choice choice = read(target);
            state.captured = choice.act();
            state.capturedFrom = choice.source();
            SinclairSounds.play(player.level(), Stagecraft.leftHand(player), SinclairSounds.ULT_CARD, 1.0F, 1.0F);
         }
      } else if (state.move == SinclairMove.COPY) {
         if (state.copy == null || !state.copy.tick(player, state.moveTick)) {
            // A participant was lost mid-scene: let go now, the card is already read.
            state.clearMove();
            endCopy(player, state, ReleaseReason.INTERRUPTED);
         }
      } else if (state.move != null && state.move.isAct() && state.scene != null) {
         state.scene.tick(player, state, state.moveTick);
      }
   }

   /**
    * The capture move ended: with the cinematic on (and both bodies free to hold) the copy cinematic runs
    * first; otherwise, or after it, the window opens with the card in the left hand.
    */
   public static void onMoveEnd(ServerPlayer player, SinclairState state, SinclairMove move) {
      if (move == SinclairMove.CAPTURE && state.ult == SinclairState.Ult.CAPTURE) {
         if (state.captured == null) {
            state.captured = SinclairRepertoire.fallback();
            state.capturedFrom = SinclairRepertoire.Source.FALLBACK;
         }

         CopyScene copy = CopyScene.tryStart(player, volunteerEntity(player, state));
         if (copy != null) {
            state.copy = copy;
            state.triggerHeld = false;
            state.startMove(SinclairMove.COPY);
            return;
         }

         openWindow(state);
      } else if (move == SinclairMove.COPY) {
         endCopy(player, state, ReleaseReason.NORMAL_END);
      } else if (move.isAct() && state.ult == SinclairState.Ult.PERFORM) {
         finish(player, state);
      }
   }

   private static void openWindow(SinclairState state) {
      state.ult = SinclairState.Ult.WINDOW;
      state.window = SinclairRules.ULT_WINDOW;
   }

   /** The cinematic is over (or cut short): both bodies are let go and the window opens. */
   private static void endCopy(ServerPlayer player, SinclairState state, ReleaseReason reason) {
      if (state.copy != null) {
         state.copy.stop(player, reason);
         state.copy = null;
      }

      if (state.ult == SinclairState.Ult.CAPTURE && state.captured != null) {
         openWindow(state);
      }
   }

   public static void tick(ServerPlayer player, SinclairState state) {
      if (state.ult == SinclairState.Ult.WINDOW && --state.window <= 0) {
         cancel(state);
         state.ultCooldown = SinclairRules.ULT_UNUSED_COOLDOWN;
         HeroSpeech.trigger(player, HeroLineTrigger.event(SinclairLines.EVENT_WINDOW_LOST));
      }
   }

   private static void perform(ServerPlayer player, SinclairState state) {
      if (state.busy() || state.captured == null) {
         SinclairHero.refuse(player, state);
         return;
      }

      StageAct act = state.captured;
      state.ult = SinclairState.Ult.PERFORM;
      state.window = 0;
      state.ultCooldown = SinclairRules.ULT_COOLDOWN;
      state.scene = new ActScene(act, volunteerEntity(player, state));
      state.startMove(SinclairMove.act(act.shape()));
      state.scene.begin(player, state);
      HeroSpeech.trigger(player, HeroLineTrigger.event(SinclairLines.event(act.shape())));
      AssistantEntity assistant = Stagecraft.onStage(player, state);
      if (assistant != null) {
         assistant.gesture(AssistantEntity.ACT, state.move.length() - (state.scene.script().length() - state.scene.script().bowStart()));
      }
   }

   private static void finish(ServerPlayer player, SinclairState state) {
      if (state.scene != null) {
         state.scene.stop(player, state);
      }

      cancel(state);
   }

   /** Back to NONE without touching the cooldown. */
   public static void cancel(SinclairState state) {
      state.ult = SinclairState.Ult.NONE;
      state.captured = null;
      state.capturedFrom = SinclairRepertoire.Source.FALLBACK;
      state.volunteer = null;
      state.volunteerId = -1;
      state.window = 0;
      state.scene = null;
   }

   /** Interrupt (death, hero change, dimension): props go, the freeze lets go. */
   public static void abort(ServerPlayer player, SinclairState state) {
      if (state.copy != null) {
         state.copy.stop(player, ReleaseReason.INTERRUPTED);
         state.copy = null;
      }

      if (state.scene != null) {
         state.scene.stop(player, state);
      }

      cancel(state);
   }
}
