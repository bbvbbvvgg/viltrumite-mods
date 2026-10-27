package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.HeroShockwave;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Session B melee: the LMB / RMB combo moves (HAMMER_COMBO with Mjölnir in
 * the hand, FIST_COMBO bare-handed) and the MMB melee branch HAMMER_STRIKE
 * (chosen only by A's resolver; this is its handler, #138–#143). The
 * strikes follow ComboBook; no auto turn, no block, no parry (#112, #132).
 */
public final class MeleeMoves {
   /** HAMMER_COMBO and FIST_COMBO. */
   public static final ThorMoveHandler COMBO = new ThorMoveHandler() {
      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ThorCombatState combat = ThorCombatState.of(context.state());
         ComboBook.Button button = context.input() == ThorInput.STANCE ? ComboBook.Button.RMB : ComboBook.Button.LMB;
         if (context.state().timeline.queued() == context.move()) {
            combat.pendingButton = button;
            return;
         }

         beginStep(player, context.state(), combat, context.move(), button);
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ThorCombatState combat = ThorCombatState.of(context.state());
         switch (event) {
            case CHAIN -> {
               ComboBook.Button button = combat.pendingButton == null ? ComboBook.Button.LMB : combat.pendingButton;
               combat.pendingButton = null;
               beginStep(player, context.state(), combat, context.move(), button);
            }
            case ENTER_MAIN -> land(player, context.state(), combat, context.move());
            case END -> stepEnded(player, context.state(), combat);
            default -> {
            }
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         ThorCombatState combat = ThorCombatState.of(context.state());
         context.state().combatPose = 0;
         combat.finisherTick = combat.chainMove != null ? combat.ticks : -1;
         if (context.player() != null) {
            endChain(context.player(), context.state(), combat);
         } else {
            combat.resetChain();
         }
      }
   };

   /** HAMMER_STRIKE: the MMB melee blow along the view (ctx.target() is only the resolver's reason, never an aim). */
   public static final ThorMoveHandler STRIKE = new ThorMoveHandler() {
      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ThorState state = context.state();
         ThorCombatState combat = ThorCombatState.of(state);
         // A chain just cut by MMB (or still open after its last step) ends in the finisher (#144: MMB flows out of a combo).
         boolean finisher = combat.finisherTick == combat.ticks || combat.chainMove != null && !combat.chainButtons.isEmpty();
         if (combat.chainMove != null) {
            endChain(player, state, combat);
         }

         combat.strike = finisher ? ThorStrike.MMB_FINISHER : ThorStrike.MMB_BLOW;
         combat.strikeAttackId = combat.nextAttackId();
         captureStep(player, combat);
         state.combatPose = combat.strike.pose();
         swingSound(player, combat.strike);
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ThorCombatState combat = ThorCombatState.of(context.state());
         if (event == StagedTimeline.Event.ENTER_MAIN) {
            List<LivingEntity> hits = landStrike(player, context.state(), combat, ThorMove.HAMMER_STRIKE, combat.strikeAttackId);
            if (context.state().mode == ThorMode.ELECTRIC) {
               // The same blow, no extra electric damage: only an MMB_STRIKE action for the recognizer (#147).
               ElectricCombos.strikeAction(player, context.state(), combat, hits);
            }
         } else if (event == StagedTimeline.Event.END) {
            context.state().combatPose = 0;
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         context.state().combatPose = 0;
      }
   };

   private MeleeMoves() {
   }

   /** Context of a chain's first press. */
   public static ComboBook.Starter starter(ServerPlayer player) {
      if (!player.onGround() && !player.getAbilities().flying && !player.isInWater()) {
         return ComboBook.Starter.AIRBORNE;
      } else if (player.isSprinting()) {
         return ComboBook.Starter.SPRINT;
      }

      return player.isShiftKeyDown() ? ComboBook.Starter.SNEAK : ComboBook.Starter.ANY;
   }

   static void captureStep(ServerPlayer player, ThorCombatState combat) {
      combat.stepDirection = MoveDirection.classify(combat.velocity.x, combat.velocity.z, player.getYRot());
      combat.stepAirborne = !player.onGround();
   }

   /** A combo step begins: pick its strike from the chain so far. */
   static void beginStep(ServerPlayer player, ThorState state, ThorCombatState combat, ThorMove move, ComboBook.Button button) {
      boolean fresh = combat.chainMove != move || combat.chainLast || state.timeline.step() == 0 && combat.ticks > combat.chainOpenUntil;
      if (fresh) {
         restartChain(player, state, combat, move);
      }

      combat.chainButtons.add(button);
      ComboBook.Match match = ComboBook.match(move, combat.chainStarter, combat.chainButtons);
      if (match == null) {
         restartChain(player, state, combat, move);
         combat.chainButtons.add(button);
         match = ComboBook.match(move, combat.chainStarter, combat.chainButtons);
      }

      if (match == null) {
         combat.strike = null;
         state.combatPose = 0;
         return;
      }

      combat.chainRoute = match.route();
      combat.chainLast = match.last();
      combat.chainCompleted = match.completed();
      combat.chainOpenUntil = Integer.MAX_VALUE;
      combat.strike = match.strike();
      combat.strikeAttackId = combat.nextAttackId();
      captureStep(player, combat);
      state.combatPose = match.strike().pose();
      if (match.last()) {
         // The route ends with this strike: presses now belong to a new chain (with Mjölnir back in the hand: hammer strikes, #166).
         ThorBridge.closeComboWindow(state);
      } else {
         ThorBridge.openComboWindow(state, move, move.length() + ThorCombatTimings.COMBO_GRACE);
      }

      lunge(player, match.strike());
      swingSound(player, match.strike());
   }

   private static void restartChain(ServerPlayer player, ThorState state, ThorCombatState combat, ThorMove move) {
      if (combat.chainMove != null) {
         endChain(player, state, combat);
      }

      combat.resetChain();
      combat.chainMove = move;
      combat.chainStarter = starter(player);
      combat.chainPosted = false;
      combat.chainTarget = null;
   }

   /** The step finished with nothing buffered: a finished route ends now, an open one waits the grace window. */
   static void stepEnded(ServerPlayer player, ThorState state, ThorCombatState combat) {
      state.combatPose = 0;
      if (combat.chainLast) {
         endChain(player, state, combat);
      } else {
         combat.chainOpenUntil = combat.ticks + ThorCombatTimings.COMBO_GRACE;
      }
   }

   /**
    * Close the chain: COMBO_END once, the combo window closes. A melee chain
    * never reports success: by the contract success = true only for a
    * completed electric combo (the sky lightning trigger).
    */
   static void endChain(ServerPlayer player, ThorState state, ThorCombatState combat) {
      ThorMove move = combat.chainMove;
      if (move != null && !combat.chainPosted) {
         combat.chainPosted = true;
         ThorEvents.post(meleeComboEnd(player.getUUID(), combat.chainTarget, move, combat.strikeAttackId,
            CombatHits.hammerBlow(move, CombatHits.hammerInHand(player))));
      }

      if (move != null && ThorBridge.comboWindow(state) == move) {
         ThorBridge.closeComboWindow(state);
      }

      combat.resetChain();
   }

   /** Pure: the COMBO_END of a melee chain (success always false; only electric combos succeed). */
   public static dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent meleeComboEnd(java.util.UUID thor, @Nullable java.util.UUID target, ThorMove move,
      int attackId, boolean hammerInHand) {
      return CombatHits.event(ThorEventType.COMBO_END, thor, target, move, attackId, hammerInHand, 0.0F, false);
   }

   /** Chain grace ran out (server tick). */
   static void tick(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (combat.chainMove != null && !state.timeline.is(combat.chainMove) && combat.ticks > combat.chainOpenUntil) {
         endChain(player, state, combat);
      }
   }

   static void land(ServerPlayer player, ThorState state, ThorCombatState combat, ThorMove move) {
      if (combat.strike == null) {
         return;
      }

      List<LivingEntity> hits = landStrike(player, state, combat, move, combat.strikeAttackId);
      if (!hits.isEmpty()) {
         combat.chainTarget = hits.get(0).getUUID();
      }
   }

   /** Resolve the current strike: targets along the view, damage, push, ground effects. */
   static List<LivingEntity> landStrike(ServerPlayer player, ThorState state, ThorCombatState combat, ThorMove move, int attackId) {
      ThorStrike strike = combat.strike;
      List<LivingEntity> landed = new ArrayList<>();
      if (strike == null) {
         return landed;
      }

      ServerLevel level = player.serverLevel();
      float damage = strike.damage() * ThorCombatConfig.get().meleeDamageScale;
      for (LivingEntity target : CombatHits.strikeTargets(player, strike)) {
         if (CombatHits.hit(player, target, damage, move, attackId)) {
            landed.add(target);
            CombatHits.push(target, player.position(), strike.knockback(), strike.lift());
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.3);
         }
      }

      boolean hammer = move != ThorMove.FIST_COMBO;
      if (!landed.isEmpty()) {
         ThorFeedback.play(player, hammer ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, hammer ? 0.7F : 1.0F);
         if (strike.heavy()) {
            ThorFeedback.play(player, SoundEvents.ANVIL_LAND, 0.35F, 0.6F);
            HeroFx.hitStop(player, landed.get(0).getBoundingBox().getCenter(), 3, ids(player, landed));
         }
      }

      if (strike.shockRadius() > 0) {
         Vec3 center = player.position().add(CombatHits.flat(player.getLookAngle()).scale(strike.shape() == ThorStrike.Shape.SLAM ? strike.reach() : 1.0));
         if (strike.crack() > 0) {
            HeroFx.slam(player, center, HeroShockwave.groundUnder(level, player), strike.shockRadius());
            if (ThorCombatConfig.get().meleeBreaksBlocks) {
               ElectricFx.carve(level, ElectricFx.crater(BlockPos.containing(center).below(), strike.crack(), 0.5));
            }
         } else {
            HeroFx.shockwave(player, center, 0.6F, HeroShockwave.groundUnder(level, player), strike.shockRadius());
         }

         ThorFeedback.play(player, SoundEvents.GENERIC_EXPLODE, 0.5F, 1.4F);
      }

      return landed;
   }

   private static int[] ids(ServerPlayer player, List<LivingEntity> targets) {
      int[] ids = new int[targets.size() + 1];
      ids[0] = player.getId();
      for (int i = 0; i < targets.size(); i++) {
         ids[i + 1] = targets.get(i).getId();
      }

      return ids;
   }

   /** Running / falling strikes carry the body (own movement, never toward a chosen target). */
   private static void lunge(ServerPlayer player, ThorStrike strike) {
      Vec3 look = CombatHits.flat(player.getLookAngle());
      Vec3 motion = player.getDeltaMovement();
      switch (strike) {
         case LUNGE, TACKLE -> player.setDeltaMovement(look.x * 0.7, Math.max(motion.y, 0.1), look.z * 0.7);
         case FALLING_SLAM -> player.setDeltaMovement(motion.x * 0.3, -1.4, motion.z * 0.3);
         default -> {
            return;
         }
      }

      player.hurtMarked = true;
   }

   private static void swingSound(ServerPlayer player, @Nullable ThorStrike strike) {
      if (strike == null) {
         return;
      }

      boolean fist = strike.move() == ThorMove.FIST_COMBO;
      ThorFeedback.play(player, fist ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_SWEEP, fist ? 0.6F : 0.9F,
         fist ? 1.2F : strike.heavy() ? 0.6F : 0.85F);
   }
}
