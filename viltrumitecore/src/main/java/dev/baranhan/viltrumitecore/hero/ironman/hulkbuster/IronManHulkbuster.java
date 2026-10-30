package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.BodyScale;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.hero.HeldInputs;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Server lifecycle of the Hulkbuster Mark 48 (rework spec §4, §11): the Veronica delivery and suit-up
 * scene (HulkbusterSuitUp), the free-space check of the real final box (1.3 x 3.2), damage on its own
 * durability first, exit and break. The kit itself is HulkbusterCombat (moves) and HulkbusterMovement (§5).
 */
public final class IronManHulkbuster {
   /** Rooted while the scene holds the player (spec §4). */
   private static final UUID ROOT_ID = UUID.fromString("ab5dd421-40de-473b-8e9e-a5cf536461fe");
   private static final float STANDING_WIDTH = 0.6F;
   private static final float STANDING_HEIGHT = 1.8F;
   public static final float BREAK_FX_RADIUS = 4.0F;

   public enum Delivery {
      STARTED,
      BUSY,
      UNAVAILABLE,
      NO_ROOM
   }

   private IronManHulkbuster() {
   }

   /** Veronica choice (spec §4): over nano or a fully-on mark, not on cooldown, only with room for the big body. */
   public static Delivery deliver(ServerPlayer player, IronManState state, Vec3 podTop) {
      HulkbusterLayer layer = state.hulkbuster;
      boolean partial = layer.phase() == HulkbusterLayer.Phase.PARTIAL;
      if (state.suit.busy() || layer.busy() || layer.active() || !state.suit.worn() && !partial) {
         return Delivery.BUSY;
      }

      if (layer.cooldown() > 0) {
         return Delivery.UNAVAILABLE;
      }

      if (freeSpot(player) == null) {
         return Delivery.NO_ROOM;
      }

      boolean air = HulkbusterSuitUp.airRoom(player);
      if (!layer.start(air)) {
         return Delivery.UNAVAILABLE;
      }

      IronManMarks.stopSignature(player, state);
      state.stopCombat();
      HeldInputs.releaseAll(player);
      state.equipSource = HulkbusterSuitUp.podPoint(player, air);
      state.hulkKit.reset();
      HulkbusterSuitUp.begin(player, state);
      return Delivery.STARTED;
   }

   /** Feet position where the big standing body (1.3 x 3.2) fits, at or near the player; null = no room. */
   @Nullable
   public static Vec3 freeSpot(ServerPlayer player) {
      return freeSpot(player, player.position());
   }

   @Nullable
   public static Vec3 freeSpot(ServerPlayer player, Vec3 feet) {
      ServerLevel level = player.serverLevel();
      float width = STANDING_WIDTH * HulkbusterRules.WIDTH_SCALE;
      float height = STANDING_HEIGHT * HulkbusterRules.HEIGHT_SCALE;
      return BodyScale.findFree(feet, width, height, box -> level.noCollision(player, box));
   }

   // ---- per tick ----

   public static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      HulkbusterLayer layer = state.hulkbuster;
      HulkbusterKit kit = state.hulkKit;
      boolean wasBig = layer.big();
      if (controlled && layer.busy()) {
         HulkbusterSuitUp.interrupt(player, state);
      }

      if (layer.needsFitCheck()) {
         fitOrRefuse(player, state);
      }

      HulkbusterLayer.Event event = layer.tick();
      if (layer.phase() == HulkbusterLayer.Phase.EXITING) {
         HulkbusterExit.tick(player, state, layer.ticks());
      } else if (layer.busy()) {
         HulkbusterSuitUp.tick(player, state);
      }

      if (event == HulkbusterLayer.Event.TORSO_CLOSED) {
         // The hitbox grows when the torso closes, after a check of the real final box (spec §4).
         Vec3 feet = freeSpot(player);
         if (feet == null) {
            HulkbusterSuitUp.cancel(player, state, "hud.viltrumitecore.ironman.hulkbuster_no_room");
         } else if (feet.distanceToSqr(player.position()) > 1.0E-4) {
            player.teleportTo(feet.x, feet.y, feet.z);
         }
      } else if (event == HulkbusterLayer.Event.READY) {
         layer.activate();
         state.equipSource = null;
         state.rightTool = RightTool.REPULSOR;
         state.helmet.reset();
         HulkbusterSuitUp.finish(player, state);
      } else if (event == HulkbusterLayer.Event.EXITED) {
         HulkbusterExit.finish(player, state);
      }

      HulkbusterExit.shellTick(player, state);

      if (layer.broken()) {
         breakHulkbuster(player, state);
      }

      if (layer.active()) {
         if (!state.helmet.closed()) {
            // The Mark 48 helmet is always closed.
            state.helmet.reset();
         }

         HulkbusterCombat.tick(player, state, controlled);
         HulkbusterMovement.tick(player, state, controlled);
         HulkbusterCage.tick(player, state);
         damageStage(player, state);
      } else if (kit.timeline.running() || kit.grab.active() || kit.cage != null) {
         HulkbusterCombat.stopAll(player, state, ReleaseReason.NORMAL_END);
         HulkbusterCage.release(player, state, ReleaseReason.NORMAL_END);
      }

      if (!layer.present()) {
         HulkbusterMovement.clear(player);
      }

      root(player, layer.busy());
      if (wasBig != layer.big()) {
         player.refreshDimensions();
      }
   }

   private static void damageStage(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      int stage = HulkbusterRules.damageStage(state.hulkbuster.fraction());
      if (stage > kit.lastStage) {
         HeroFx.shards(player, player.position().add(0.0, 2.2, 0.0), 0.5F + 0.3F * stage, null, new float[0], null);
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
      }

      kit.lastStage = stage;
   }

   private static void fitOrRefuse(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      Vec3 feet = freeSpot(player);
      if (feet == null) {
         // No room (a block placed while away, a tunnel): it goes back to Veronica, no cooldown.
         layer.refuse();
         state.equipSource = null;
         player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.hulkbuster_no_room"), true);
         HulkbusterSounds.notify(player, HulkbusterSounds.Cue.REFUSE);
         return;
      }

      if (feet.distanceToSqr(player.position()) > 1.0E-4) {
         player.teleportTo(feet.x, feet.y, feet.z);
      }

      layer.activate();
   }

   /** Durability 0 or death: it falls apart, Tony stays in his suit. */
   public static void breakHulkbuster(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      if (!layer.present()) {
         return;
      }

      boolean wasBig = layer.big();
      HulkbusterCombat.stopAll(player, state, ReleaseReason.INTERRUPTED);
      HulkbusterCage.release(player, state, ReleaseReason.INTERRUPTED);
      HulkbusterSuitUp.end(player, state);
      if (layer.phase() == HulkbusterLayer.Phase.EXITING) {
         state.equipSource = null;
      }

      layer.breakNow();
      HeroFx.flash(player, player.position().add(0.0, 1.6, 0.0));
      HeroFx.shockwave(player, player.position(), 0.8F, null, BREAK_FX_RADIUS);
      HeroFx.shards(player, player.position().add(0.0, 1.8, 0.0), 1.2F, null, new float[0], null);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.BREAK);
      HulkbusterMovement.clear(player);
      if (wasBig) {
         player.refreshDimensions();
      }
   }

   /** "Костюм": climb out (cooldown after) or cancel the suit-up (no cooldown). True when handled. */
   public static boolean suitKey(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      if (!layer.present()) {
         return false;
      }

      if (layer.busy()) {
         if (!layer.torsoClosed()) {
            HulkbusterSuitUp.cancel(player, state, null);
         }

         return true;
      }

      if (layer.exit()) {
         HulkbusterCombat.stopAll(player, state, ReleaseReason.NORMAL_END);
         HulkbusterCage.release(player, state, ReleaseReason.NORMAL_END);
         HulkbusterExit.begin(player, state);
      }

      return true;
   }

   /**
    * Damage layer between the shield and the mark: the whole hit, no spill. The raised forearms take a
    * frontal hit with the shield rule x1.5 (spec §6.4); a perfect block answers with the counter.
    * Like vanilla armor it lets divine and invulnerability-bypassing damage through.
    */
   public static DamageAbsorb absorb(ServerPlayer player, IronManState state, DamageSource source, float amount) {
      if (!state.hulkbuster.active() || DivineDamage.piercesHeroArmor(source)) {
         return DamageAbsorb.PASS;
      }

      HulkbusterCombat.onHurt(player, state, amount);
      float taken = amount;
      if (HulkbusterCombat.blocking(state) && frontal(player, source)) {
         boolean perfect = HulkbusterCombat.perfect(state);
         taken = amount / HulkbusterRules.BLOCK_FACTOR;
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.BLOCK_HIT);
         HeroFx.flash(player, player.getEyePosition().add(player.getLookAngle().scale(1.2)), 0.5F);
         if (perfect) {
            HulkbusterCombat.counter(player, state);
            return DamageAbsorb.ABSORBED;
         }
      }

      return state.hulkbuster.absorb(taken) ? DamageAbsorb.ABSORBED : DamageAbsorb.PASS;
   }

   private static boolean frontal(ServerPlayer player, DamageSource source) {
      Vec3 from = source.getSourcePosition();
      if (from == null) {
         return false;
      }

      Vec3 to = from.subtract(player.position());
      Vec3 flat = new Vec3(to.x, 0.0, to.z);
      return flat.lengthSqr() > 1.0E-6 && flat.normalize().dot(HulkbusterCombat.flatLook(player)) > HulkbusterRules.BLOCK_CONE_COS;
   }

   // ---- lifecycle ----

   /** Death: it falls apart (cooldown), every hold ends. */
   public static void onDeath(ServerPlayer player, IronManState state) {
      HulkbusterCombat.stopAll(player, state, ReleaseReason.CASTER_DEATH);
      breakHulkbuster(player, state);
   }

   public static void onLogout(ServerPlayer player, IronManState state) {
      HulkbusterCombat.stopAll(player, state, ReleaseReason.CASTER_DISCONNECT);
      HulkbusterCage.release(player, state, ReleaseReason.CASTER_DISCONNECT);
      HulkbusterSuitUp.end(player, state);
   }

   /** Every grab, cage and the ultimate's captive end; the suit stays on (dimension change, hero change). */
   public static void releaseGrab(ServerPlayer player, IronManState state, ReleaseReason reason) {
      HulkbusterCombat.stopAll(player, state, reason);
      HulkbusterCage.release(player, state, reason);
   }

   private static void root(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      boolean has = speed.getModifier(ROOT_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(ROOT_ID, "Hulkbuster suit-up", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(ROOT_ID);
      }
   }
}
