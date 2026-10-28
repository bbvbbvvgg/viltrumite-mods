package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import net.minecraft.server.level.ServerPlayer;

/** Press dispatch, gates and the per-tick timeline of Jinwoo's abilities. */
public final class JinwooActions {
   private JinwooActions() {
   }

   /** Why a press is refused (pure gate, spec §5 per-ability conditions). */
   public enum Refusal {
      NONE, DEAD, ANCHORED, COOLDOWN, MANA, BUSY
   }

   public static Refusal gate(boolean alive, boolean anchored, int cooldown, float mana, float cost, boolean busy) {
      if (!alive) return Refusal.DEAD;
      if (anchored) return Refusal.ANCHORED;
      if (busy) return Refusal.BUSY;
      if (cooldown > 0) return Refusal.COOLDOWN;
      if (mana + 1.0E-4F < cost) return Refusal.MANA;
      return Refusal.NONE;
   }

   /** Moves that a new ability press may cut: combos, draw and command poses. */
   static boolean interruptible(JinwooMove move) {
      return move == null || move.isCombo() || move == JinwooMove.DRAW || move == JinwooMove.RIPOSTE || move == JinwooMove.DIVE_LAND
         || move == JinwooMove.SUMMON || move == JinwooMove.RECALL || move == JinwooMove.COMMAND_ATTACK || move == JinwooMove.COMMAND_GUARD
         || move == JinwooMove.COMMAND_SENTINEL;
   }

   public static void press(ServerPlayer player, JinwooState state, JinwooAbility ability) {
      boolean busy = !interruptible(state.move);
      Refusal refusal = gate(player.isAlive(), HeroDamage.isAnchored(player), state.cooldown(ability), state.mana, ability.mana, busy);
      if (refusal != Refusal.NONE) {
         JinwooHero.INSTANCE.onInputRefused(player, ability.action());
         return;
      }
      if (state.blocking) {
         JinwooCombat.endBlock(state);
      }
      boolean ok = switch (ability) {
         case SHADOW_STEP -> JinwooSkills.step(player, state);
         case MUTILATION -> JinwooSkills.rush(player, state);
         case AUTHORITY -> JinwooSkills.authorityGrip(player, state);
         case EXTRACTION -> JinwooSkills.extract(player, state);
         case EXCHANGE -> JinwooSkills.exchange(player, state);
         case MONARCH -> MonarchArise.start(player, state);
         case SUMMON -> summon(player, state);
         case ATTACK -> command(player, state, JinwooMove.COMMAND_ATTACK, ShadowArmy.commandAttack(player, state));
         case GUARD -> command(player, state, JinwooMove.COMMAND_GUARD, ShadowArmy.commandGuard(player, state));
         case SENTINEL -> command(player, state, JinwooMove.COMMAND_SENTINEL, ShadowArmy.commandSentinel(player, state));
         case CHARGE -> JinwooSkills.charge(player, state);
      };
      if (!ok) {
         JinwooHero.INSTANCE.onInputRefused(player, ability.action());
         return;
      }
      state.spend(ability.mana);
      state.cool(ability);
      state.comboStep = -1;
      state.comboQueued = false;
   }

   private static boolean summon(ServerPlayer player, JinwooState state) {
      boolean hadArmy = state.roster.activeCount() > 0 || !state.summonQueue.isEmpty();
      if (!ShadowArmy.summonOrRecall(player, state)) {
         JinwooSkills.hint(player, "message.viltrumitecore.jinwoo.no_ready_shadow");
         return false;
      }
      state.start(hadArmy ? JinwooMove.RECALL : JinwooMove.SUMMON, -1);
      return true;
   }

   private static boolean command(ServerPlayer player, JinwooState state, JinwooMove move, boolean ok) {
      if (!ok) {
         if (ShadowArmy.active(player, state).isEmpty()) {
            JinwooSkills.hint(player, "message.viltrumitecore.jinwoo.no_shadow");
         }
         return false;
      }
      if (state.move == null || interruptible(state.move)) {
         state.start(move, -1);
      }
      return true;
   }

   public static void release(ServerPlayer player, JinwooState state, HeroAction action) {
      if (action == HeroAction.GRAB_THROW) {
         JinwooSkills.authorityRelease(player, state);
      }
   }

   /** One server tick of the timeline. */
   public static void tick(ServerPlayer player, JinwooState state) {
      boolean anchored = HeroDamage.isAnchored(player);
      if (state.iframes > 0) state.iframes--;
      if (anchored && state.move != null) {
         switch (state.move) {
            case EXTRACT -> {
               JinwooSkills.cancelExtract(player, state);
               state.endMove();
            }
            case AUTHORITY_GRIP -> {
               JinwooSkills.releaseHeld(player, state);
               state.endMove();
            }
            default -> {
            }
         }
      }
      JinwooMove move = state.move;
      if (move == null) {
         return;
      }
      state.elapsed++;
      int t = state.elapsed;
      switch (move) {
         case COMBO_1, COMBO_2, COMBO_3, COMBO_4, COMBO_5, LOW_SWEEP, AIR_1, AIR_2 -> {
            if (t == move.contact) JinwooCombat.contact(player, state, move);
         }
         case DIVE -> {
            if (t > 1 && (player.onGround() || player.isInWater())) {
               JinwooCombat.diveLand(player, state);
               return;
            }
         }
         case RIPOSTE -> {
         }
         case STEP -> JinwooSkills.stepTick(player, state);
         case RUSH -> JinwooSkills.rushTick(player, state);
         case AUTHORITY_GRIP, AUTHORITY_SLAM -> {
            JinwooSkills.authorityTick(player, state);
            if (state.move != move) return;
         }
         case EXTRACT -> JinwooSkills.extractTick(player, state);
         case EXCHANGE -> JinwooSkills.exchangeTick(player, state);
         case ARISE -> {
            MonarchArise.tick(player, state);
            if (state.move != move) return;
         }
         case CHARGE -> JinwooSkills.chargeTick(player, state);
         default -> {
         }
      }
      if (state.move == move && state.elapsed >= move.length) {
         finish(player, state, move);
      }
   }

   private static void finish(ServerPlayer player, JinwooState state, JinwooMove move) {
      state.endMove();
      if (move == JinwooMove.AUTHORITY_SLAM) {
         JinwooSkills.releaseHeld(player, state);
      }
      if (move == JinwooMove.CHARGE) {
         JinwooSkills.endCharge(player, state);
      }
      if (move == JinwooMove.ARISE) {
         state.summonQueue.clear();
      }
      if (move.isCombo()) {
         state.comboIdle = 0;
         if (state.comboQueued) {
            JinwooCombat.startNext(player, state);
         }
      }
      if (move == JinwooMove.COMBO_5 || move == JinwooMove.LOW_SWEEP) {
         state.comboStep = -1;
      }
   }

   /** Every running action ends (death, dimension, logout, hero change). */
   public static void cleanup(ServerPlayer player, JinwooState state) {
      JinwooSkills.cancelExtract(player, state);
      JinwooSkills.releaseHeld(player, state);
      if (state.is(JinwooMove.CHARGE)) {
         JinwooSkills.endCharge(player, state);
      }
      if (state.is(JinwooMove.ARISE) && state.elapsed < JinwooRules.ULT_CANCEL_BEFORE) {
         MonarchArise.cancel(player, state);
      }
      state.endMove();
      state.blocking = false;
      state.domainLeft = 0;
      state.domainCenter = null;
      state.comboStep = -1;
      state.comboQueued = false;
      state.iframes = 0;
   }
}
