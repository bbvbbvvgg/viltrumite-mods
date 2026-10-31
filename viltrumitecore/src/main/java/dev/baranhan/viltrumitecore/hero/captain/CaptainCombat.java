package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Steve's hand-to-hand (spec §5.1-5.2): the shield chain (4 hits) or, with the shield away, the fist chain
 * (3 hits); guard and parry with the counter, the return strike after a catch, the roll sweep. Server only.
 */
public final class CaptainCombat {
   private CaptainCombat() {
   }

   // --- LMB ---

   public static void primary(ServerPlayer player, CaptainState state) {
      state.idle = 0;
      if (state.guarding) {
         endGuard(state);
      }
      if (state.running()) {
         CaptainMove m = state.move;
         if (m.isCombo() && CaptainRules.buffers(state.elapsed, m.length) || m == CaptainMove.CATCH || m == CaptainMove.LANDING
            || m == CaptainMove.PARRY || m == CaptainMove.RUSH_BASH && state.elapsed > 4) {
            state.comboQueued = true;
         } else if (m == CaptainMove.ROLL) {
            state.comboQueued = true;
         }
         return;
      }
      startNext(player, state);
   }

   static void startNext(ServerPlayer player, CaptainState state) {
      state.comboQueued = false;
      CaptainMove move;
      boolean shield = state.shieldInHand();
      if (state.counterLeft > 0 && shield) {
         move = CaptainMove.COUNTER;
         state.counterLeft = 0;
         state.comboStep = -1;
      } else if (state.catchLeft > 0 && shield) {
         move = CaptainMove.RETURN_STRIKE;
         state.catchLeft = 0;
         state.comboStep = -1;
      } else if (state.rollFollow > 0) {
         move = CaptainMove.ROLL_SWEEP;
         state.rollFollow = 0;
         state.comboStep = -1;
      } else if (state.rushFollow > 0 && shield) {
         // Rush -> LMB: straight into the rising shield uppercut (third shield hit).
         move = CaptainMove.SHIELD_3;
         state.rushFollow = 0;
         state.comboStep = 2;
      } else if (shield) {
         int step = CaptainRules.nextStep(lastStep(state, true), state.comboIdle, 4);
         state.comboStep = step;
         move = CaptainMove.shield(step);
      } else {
         int step = CaptainRules.nextStep(lastStep(state, false), state.comboIdle, 3);
         state.comboStep = step;
         move = CaptainMove.fist(step);
      }
      state.start(move);
      state.moveDir = player.getLookAngle();
      float pitch = CaptainSounds.jitter(player, 1.0F);
      switch (move) {
         case SHIELD_4, COUNTER, RETURN_STRIKE -> CaptainSounds.play(player, CaptainSounds.SHIELD_SWING, 1.0F, 0.8F);
         case FIST_1, FIST_2, ROLL_SWEEP -> CaptainSounds.play(player, CaptainSounds.FIST_SWING, 0.8F, pitch);
         case FIST_3 -> CaptainSounds.play(player, CaptainSounds.FIST_SWING, 0.9F, 0.8F);
         default -> CaptainSounds.play(player, CaptainSounds.SHIELD_SWING, 0.85F, pitch);
      }
      if (move == CaptainMove.SHIELD_4 || move == CaptainMove.COUNTER) {
         // the finisher and the counter step in
         Vec3 h = flat(player.getLookAngle()).scale(0.35);
         CaptainFx.motion(player, new Vec3(h.x, 0, h.z), 3, true);
      }
   }

   /** The chain continues only in the same set (switching shield/fists restarts it). */
   private static int lastStep(CaptainState state, boolean shield) {
      if (state.comboStep < 0) {
         return -1;
      }
      return state.lastChainShield == shield ? state.comboStep : -1;
   }

   static Vec3 flat(Vec3 v) {
      Vec3 h = new Vec3(v.x, 0, v.z);
      return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
   }

   /** Contact of the running combo move. */
   static void contact(ServerPlayer player, CaptainState state, CaptainMove move) {
      List<LivingEntity> targets;
      float damage;
      switch (move) {
         case ROLL_SWEEP -> {
            targets = CaptainTargets.around(player, player.position().add(0, 0.5, 0), 2.6, e -> CaptainTargets.mayHit(player, e));
            damage = 7.0F;
         }
         case SHIELD_4 -> {
            targets = CaptainTargets.arc(player, CaptainRules.REACH + 0.4, 140, e -> CaptainTargets.mayHit(player, e));
            damage = CaptainRules.SHIELD_DAMAGE[3];
         }
         case COUNTER -> {
            targets = CaptainTargets.arc(player, CaptainRules.REACH + 0.6, 130, e -> CaptainTargets.mayHit(player, e));
            damage = CaptainRules.COUNTER_DAMAGE;
         }
         case RETURN_STRIKE -> {
            targets = CaptainTargets.arc(player, CaptainRules.REACH + 0.6, 130, e -> CaptainTargets.mayHit(player, e));
            damage = CaptainRules.RETURN_STRIKE_DAMAGE;
         }
         default -> {
            targets = CaptainTargets.arc(player, CaptainRules.REACH, CaptainRules.ARC_DEGREES, e -> CaptainTargets.mayHit(player, e));
            int i = move.ordinal() - (move.ordinal() >= CaptainMove.FIST_1.ordinal() ? CaptainMove.FIST_1.ordinal() : CaptainMove.SHIELD_1.ordinal());
            damage = move.ordinal() >= CaptainMove.FIST_1.ordinal() ? CaptainRules.FIST_DAMAGE[Math.min(2, i)] : CaptainRules.SHIELD_DAMAGE[Math.min(3, i)];
         }
      }
      boolean shield = move.usesShield() || move == CaptainMove.RETURN_STRIKE;
      int kind = switch (move) {
         case FIST_1, FIST_2 -> 1;
         case FIST_3, ROLL_SWEEP -> 2;
         case COUNTER, RETURN_STRIKE, SHIELD_4 -> 3;
         default -> 0;
      };
      Vec3 look = flat(player.getLookAngle());
      int[] ids = new int[Math.min(8, targets.size()) + 1];
      int n = 0;
      for (LivingEntity t : targets) {
         if (!state.hit.add(t.getId())) {
            continue;
         }
         if (!CaptainTargets.strike(player, t, damage)) {
            continue;
         }
         if (n < ids.length - 1) ids[n++] = t.getId();
         state.kinetic = CaptainRules.gain(state.kinetic, CaptainRules.GAIN_COMBO);
         state.kineticIdle = 0;
         Vec3 at = t.getBoundingBox().getCenter();
         CaptainFx.around(player, CaptainFx.CONTACT, kind, at, player.getEyePosition());
         CaptainSounds.playAt(player, at, shield ? CaptainSounds.SHIELD_HIT : move == CaptainMove.FIST_3 || move == CaptainMove.ROLL_SWEEP
            ? CaptainSounds.KICK : CaptainSounds.FIST_HIT, 1.0F, CaptainSounds.jitter(player, shield ? 1.0F : 1.1F));
         switch (move) {
            case SHIELD_3 -> CaptainTargets.push(t, new Vec3(look.x * 0.3, CaptainRules.UPPERCUT_LIFT, look.z * 0.3));
            case FIST_2 -> CaptainTargets.push(t, new Vec3(0, CaptainRules.UPPERCUT_LIFT * 0.8, 0));
            case FIST_3 -> CaptainTargets.push(t, look.scale(CaptainRules.KICK_KNOCKBACK).add(0, 0.25, 0));
            case SHIELD_4 -> {
               CaptainTargets.push(t, look.scale(0.8).add(0, 0.35, 0));
               CaptainTargets.stun(player, t, 12);
            }
            case COUNTER -> {
               CaptainTargets.push(t, look.scale(1.3).add(0, 0.4, 0));
               CaptainTargets.stun(player, t, CaptainRules.COUNTER_STUN);
               CaptainFx.around(player, CaptainFx.STUN, CaptainRules.COUNTER_STUN, at, Vec3.ZERO, t.getId());
            }
            case RETURN_STRIKE -> CaptainTargets.push(t, look.scale(CaptainRules.RETURN_STRIKE_KNOCKBACK).add(0, 0.3, 0));
            case ROLL_SWEEP -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, false, true));
            default -> CaptainTargets.push(t, look.scale(0.35).add(0, 0.1, 0));
         }
      }
      if (move == CaptainMove.SHIELD_4) {
         // the overhead finisher also shakes everyone around the impact point
         Vec3 at = player.position().add(look.scale(1.6));
         for (LivingEntity t : CaptainTargets.around(player, at, CaptainRules.FINISHER_RADIUS, e -> CaptainTargets.mayHit(player, e))) {
            if (state.hit.add(t.getId())) {
               CaptainTargets.strike(player, t, CaptainRules.FINISHER_AREA_DAMAGE);
               CaptainTargets.push(t, t.position().subtract(at).normalize().scale(0.5).add(0, 0.3, 0));
            }
         }
         CaptainFx.around(player, CaptainFx.FINISHER, 0, at, new Vec3(CaptainRules.FINISHER_RADIUS, 0, 0));
         CaptainSounds.playAt(player, at, CaptainSounds.FINISHER, 1.0F, 1.0F);
      }
      if ((move == CaptainMove.SHIELD_4 || move == CaptainMove.COUNTER) && n > 0) {
         ids[n++] = player.getId();
         int[] all = new int[n];
         System.arraycopy(ids, 0, all, 0, n);
         HeroFx.hitStop(player, player.position(), 3, all);
      }
      if (move == CaptainMove.COUNTER && n > 0) {
         CaptainSounds.play(player, CaptainSounds.COUNTER, 1.0F, 1.0F);
      }
   }

   // --- guard / parry (spec §5.2) ---

   public static void guard(ServerPlayer player, CaptainState state, boolean pressed) {
      if (pressed) {
         if (state.shieldBroken > 0) {
            // the guard was broken (Reid's stroke): the arm cannot raise the shield yet
            CaptainSounds.play(player, CaptainSounds.REFUSE, 0.6F, 0.8F);
            return;
         }
         if (state.running() && !(state.move.isCombo() || state.move == CaptainMove.CATCH || state.move == CaptainMove.LANDING
            || state.move == CaptainMove.PARRY)) {
            return;
         }
         state.endMove();
         state.guarding = true;
         state.guardTicks = 0;
         state.idle = 0;
         CaptainSounds.play(player, CaptainSounds.GUARD_UP, 0.7F, state.shieldInHand() ? 1.0F : 1.3F);
      } else if (state.guarding) {
         endGuard(state);
      }
   }

   static void endGuard(CaptainState state) {
      state.guarding = false;
      state.guardTicks = 0;
   }

   /** Joint move check (server): {@code from} lies in Steve's frontal guard arc. */
   public static boolean facing(ServerPlayer player, Vec3 from) {
      return frontal(player, from);
   }

   /**
    * Joint move with Thor (rework §15.3): Mjolnir struck the raised shield. Steve braces under the blow (his own
    * move, synced to every client like the rest of his timeline), the lightning leaves kinetic charge in the
    * shield and a short invulnerability covers the shockwave.
    */
   public static void teamBrace(ServerPlayer player, CaptainState state, int kinetic) {
      boolean was = CaptainRules.empowered(state.kinetic);
      endGuard(state);
      state.start(CaptainMove.TEAM_BRACE);
      state.kinetic = CaptainRules.gain(state.kinetic, kinetic);
      state.kineticIdle = 0;
      state.idle = 0;
      state.iframes = Math.max(state.iframes, 12);
      Vec3 look = flat(player.getLookAngle());
      Vec3 at = player.getEyePosition().add(look.scale(0.7)).add(0, -0.35, 0);
      CaptainFx.around(player, CaptainFx.PARRY, 0, at, look);
      if (!was && CaptainRules.empowered(state.kinetic)) {
         CaptainFx.around(player, CaptainFx.CHARGED, 0, player.position(), Vec3.ZERO);
         CaptainSounds.play(player, CaptainSounds.CHARGED, 0.8F, 1.0F);
      }
   }

   /**
    * Contextual interaction {@code reid_shatters_vibranium}: a blade that annihilates rather than strikes met the
    * raised shield. The guard breaks for {@code ticks} (no raise, no parry), the stored kinetic charge and the
    * counter window are lost and Steve reels (his own synced move SHIELD_SHATTERED). The shield item stays.
    */
   public static void shatterGuard(ServerPlayer player, CaptainState state, int ticks) {
      shatterGuard(player, state, ticks, CaptainLines.SHIELD_SHATTERED);
   }

   /** {@link #shatterGuard(ServerPlayer, CaptainState, int)} with Steve's own answer to who broke it. */
   public static void shatterGuard(ServerPlayer player, CaptainState state, int ticks, dev.baranhan.viltrumitecore.hero.speech.HeroLine line) {
      endGuard(state);
      state.shieldBroken = Math.max(state.shieldBroken, ticks);
      state.kinetic = 0;
      state.kineticIdle = 0;
      state.counterLeft = 0;
      state.idle = 0;
      state.start(CaptainMove.SHIELD_SHATTERED);
      CaptainSounds.play(player, CaptainSounds.REFUSE, 0.9F, 0.6F);
      dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.say(player, line);
   }

   /** True when {@code from} lies in the frontal guard arc of the player. */
   static boolean frontal(ServerPlayer player, Vec3 from) {
      Vec3 look = flat(player.getLookAngle());
      Vec3 d = from.subtract(player.position());
      Vec3 h = new Vec3(d.x, 0, d.z);
      if (h.lengthSqr() < 0.01) {
         return true;
      }
      return CaptainRules.inGuardArc(h.normalize().dot(look));
   }

   /** Incoming hit while guarding. Null = not covered (behind / no source position). */
   @Nullable
   static DamageAbsorb absorb(ServerPlayer player, CaptainState state, DamageSource source, float raw) {
      Vec3 from = source.getSourcePosition();
      if (from == null && source.getEntity() != null) {
         from = source.getEntity().position();
      }
      if (from == null || !frontal(player, from)) {
         return null;
      }
      Entity attacker = source.getEntity();
      boolean shield = state.shieldInHand();
      float pass = CaptainRules.guarded(raw, shield, true, state.guardTicks);
      onBlocked(player, state, attacker instanceof LivingEntity l ? l : null, false, from);
      return pass <= 0.0F ? DamageAbsorb.ABSORBED : DamageAbsorb.reduceTo(pass);
   }

   /** A blocked hit or projectile: kinetic charge, parry and counter window, FX. */
   static void onBlocked(ServerPlayer player, CaptainState state, @Nullable LivingEntity attacker, boolean projectile, Vec3 from) {
      boolean shield = state.shieldInHand();
      Vec3 look = flat(player.getLookAngle());
      Vec3 at = player.getEyePosition().add(look.scale(0.7)).add(0, -0.35, 0);
      state.idle = 0;
      state.kineticIdle = 0;
      boolean wasEmpowered = CaptainRules.empowered(state.kinetic);
      if (CaptainRules.isParry(shield, true, state.guardTicks)) {
         state.kinetic = CaptainRules.gain(state.kinetic, CaptainRules.GAIN_PARRY);
         state.counterLeft = CaptainRules.COUNTER_WINDOW;
         if (attacker != null && attacker != player && !projectile && attacker.distanceToSqr(player) < 7 * 7 && CaptainTargets.mayHit(player, attacker)) {
            CaptainTargets.stun(player, attacker, CaptainRules.PARRY_STUN);
            CaptainTargets.push(attacker, attacker.position().subtract(player.position()).normalize().scale(CaptainRules.PARRY_KNOCKBACK).add(0, 0.2, 0));
            CaptainFx.around(player, CaptainFx.STUN, CaptainRules.PARRY_STUN, attacker.getBoundingBox().getCenter(), Vec3.ZERO, attacker.getId());
         }
         CaptainFx.around(player, CaptainFx.PARRY, projectile ? 1 : 0, at, look);
         HeroFx.flash(player, at, 0.5F);
         CaptainSounds.play(player, CaptainSounds.PARRY, 1.0F, 1.0F);
         HeroSpeech.say(player, CaptainLines.ALL_DAY);
         endGuard(state);
         state.start(CaptainMove.PARRY);
      } else {
         state.kinetic = CaptainRules.gain(state.kinetic, shield ? CaptainRules.GAIN_BLOCK : 2);
         CaptainFx.around(player, CaptainFx.BLOCK, shield ? 0 : 1, at, look);
         CaptainSounds.play(player, CaptainSounds.BLOCK, shield ? 1.0F : 0.6F, CaptainSounds.jitter(player, shield ? 1.0F : 1.4F));
      }
      if (!wasEmpowered && CaptainRules.empowered(state.kinetic)) {
         CaptainFx.around(player, CaptainFx.CHARGED, 0, player.position(), Vec3.ZERO);
         CaptainSounds.play(player, CaptainSounds.CHARGED, 0.8F, 1.0F);
      }
   }

   // --- per tick ---

   static void tick(ServerPlayer player, CaptainState state) {
      if (state.guarding) {
         state.guardTicks++;
         state.idle = 0;
         if (!player.isAlive()) {
            endGuard(state);
         }
      }
      if (state.move == null) {
         return;
      }
      CaptainMove move = state.move;
      state.elapsed++;
      if (CaptainSkills.step(player, state, move)) {
         return; // a dynamic skill move manages its own clock
      }
      if (move.contact >= 0 && state.elapsed == move.contact && (move.isCombo())) {
         contact(player, state, move);
      }
      if (state.move == move && state.elapsed >= move.length) {
         finish(player, state, move);
      }
   }

   /** End of a timed move; a queued LMB continues the chain at once. */
   static void finish(ServerPlayer player, CaptainState state, CaptainMove move) {
      if (move.isCombo()) {
         state.comboIdle = 0;
         state.lastChainShield = move.usesShield();
      }
      state.endMove();
      if (state.comboQueued) {
         state.comboQueued = false;
         startNext(player, state);
      }
   }
}
