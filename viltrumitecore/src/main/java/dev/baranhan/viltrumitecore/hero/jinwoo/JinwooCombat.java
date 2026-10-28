package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Dagger dance, block/parry and the daggers themselves (spec §5.1-5.3). */
public final class JinwooCombat {
   private JinwooCombat() {
   }

   /** Legal target of Jinwoo's own hits: never his shadows or allies; players only with PvP. */
   public static boolean mayHit(ServerPlayer player, LivingEntity e) {
      if (e == player || !e.isAlive() || ShadowArmy.ally(player, e)) {
         return false;
      }
      if (e instanceof Player other) {
         return ServerRules.pvp(player) && !other.isCreative() && !other.isSpectator();
      }
      return true;
   }

   public static MobEffectInstance hidden(net.minecraft.world.effect.MobEffect effect, int ticks, int amplifier) {
      return new MobEffectInstance(effect, ticks, amplifier, false, false, true);
   }

   /** One dagger hit. {@code clean} ignores invulnerability frames (Mutilation). Returns true when it landed. */
   public static boolean strike(ServerPlayer player, JinwooState state, LivingEntity target, float damage, boolean clean, int blade) {
      DamageSource source = player.damageSources().playerAttack(player);
      boolean landed;
      if (clean) {
         float before = target.getHealth();
         HeroDamage.applyCleanDamage(target, source, damage);
         landed = target.getHealth() < before || !target.isAlive();
      } else {
         target.invulnerableTime = 0;
         landed = target.hurt(source, damage);
      }
      if (landed) {
         state.mana = Math.min(JinwooRules.MANA_MAX, state.mana + JinwooRules.MANA_PER_HIT);
         state.markTarget = target.getUUID();
         state.markTicks = JinwooRules.MARK_TICKS;
         state.idle = 0;
         Vec3 at = target.getBoundingBox().getCenter();
         JinwooFx.around(player, JinwooFx.CONTACT, blade, at, player.getEyePosition());
         JinwooSounds.playAt(player, at, JinwooSounds.DAGGER_HIT, 0.9F, 0.9F + player.getRandom().nextFloat() * 0.25F);
      }
      return landed;
   }

   /** Any combat action draws the daggers at once (spec §5.3); the assemble look plays on the client. */
   public static void autoDraw(ServerPlayer player, JinwooState state) {
      state.idle = 0;
      if (!state.daggers) {
         state.daggers = true;
         state.drawLeft = JinwooRules.DRAW_TICKS;
         state.sheathLeft = 0;
         JinwooSounds.play(player, JinwooSounds.DAGGER_DRAW, 0.8F, 1.2F);
      }
   }

   // --- LMB (spec §5.1) ---

   public static void primary(ServerPlayer player, JinwooState state) {
      if (state.running()) {
         JinwooMove m = state.move;
         if (m != null && m.isCombo() && JinwooRules.buffers(state.elapsed, m.length)) {
            state.comboQueued = true;
         }
         return;
      }
      startNext(player, state);
   }

   static void startNext(ServerPlayer player, JinwooState state) {
      autoDraw(player, state);
      state.comboQueued = false;
      JinwooMove move;
      if (!player.onGround() && !player.isInWater()) {
         if (state.airHits >= 2) {
            startDive(player, state);
            return;
         }
         move = state.airHits == 0 ? JinwooMove.AIR_1 : JinwooMove.AIR_2;
         state.airHits++;
         state.comboStep = state.airHits - 1;
         JinwooFx.toOwner(player, JinwooFx.MOTION, JinwooRules.AIR_FLOAT_TICKS, new Vec3(0, -0.08 * JinwooRules.AIR_GRAVITY, 0), new Vec3(1, 0, 0));
      } else if (player.isShiftKeyDown()) {
         move = JinwooMove.LOW_SWEEP;
         state.comboStep = -1;
      } else {
         int step = JinwooRules.nextComboStep(state.comboStep, state.comboIdle);
         state.comboStep = step;
         move = JinwooMove.combo(step);
      }
      state.start(move, -1);
      state.moveDir = player.getLookAngle();
      float pitch = 0.95F + player.getRandom().nextFloat() * 0.15F;
      if (move == JinwooMove.COMBO_3) {
         JinwooSounds.play(player, JinwooSounds.SPIN, 1.0F, pitch);
      } else if (move == JinwooMove.COMBO_5) {
         JinwooSounds.play(player, JinwooSounds.FINISHER, 1.0F, 1.0F);
      } else {
         JinwooSounds.play(player, JinwooSounds.DAGGER_SWING, 0.9F, pitch);
      }
   }

   private static void startDive(ServerPlayer player, JinwooState state) {
      state.airHits = 3;
      state.start(JinwooMove.DIVE, -1);
      Vec3 look = player.getLookAngle();
      Vec3 h = new Vec3(look.x, 0, look.z);
      h = h.lengthSqr() > 1.0E-4 ? h.normalize().scale(0.35) : Vec3.ZERO;
      JinwooFx.motion(player, new Vec3(h.x, -JinwooRules.DIVE_SPEED, h.z), JinwooRules.DIVE_MAX_TICKS);
      JinwooSounds.play(player, JinwooSounds.DIVE, 1.0F, 1.0F);
   }

   /** Contact of the running combo move. */
   static void contact(ServerPlayer player, JinwooState state, JinwooMove move) {
      int stepIndex = Math.max(0, Math.min(4, state.comboStep));
      float damage = switch (move) {
         case LOW_SWEEP -> JinwooRules.LOW_DAMAGE;
         case AIR_1 -> JinwooRules.COMBO_DAMAGE[0];
         case AIR_2 -> JinwooRules.COMBO_DAMAGE[1];
         default -> JinwooRules.COMBO_DAMAGE[stepIndex];
      };
      List<LivingEntity> targets = move == JinwooMove.COMBO_3
         ? JinwooTargets.around(player, player.position().add(0, 1.0, 0), JinwooRules.SPIN_RADIUS, e -> mayHit(player, e))
         : JinwooTargets.arc(player, JinwooRules.REACH, JinwooRules.ARC_DEGREES, e -> mayHit(player, e));
      int blade = switch (move) {
         case COMBO_1, AIR_1 -> 0;
         case COMBO_2, AIR_2 -> 1;
         default -> 2;
      };
      int[] ids = new int[Math.min(8, targets.size())];
      int n = 0;
      for (LivingEntity t : targets) {
         if (!state.hit.add(t.getUUID())) {
            continue;
         }
         if (strike(player, state, t, damage, false, blade)) {
            if (n < ids.length) ids[n++] = t.getId();
            if (move == JinwooMove.LOW_SWEEP) {
               t.addEffect(hidden(MobEffects.MOVEMENT_SLOWDOWN, JinwooRules.LOW_SLOW_TICKS, 1));
            }
            if (move == JinwooMove.COMBO_5 && HeroRegistry.allowsImpulse(t)) {
               t.setDeltaMovement(t.getDeltaMovement().add(0, JinwooRules.FINISHER_LIFT, 0));
               t.hurtMarked = true;
            }
         }
      }
      if (move == JinwooMove.COMBO_5 && n > 0) {
         int[] all = new int[n + 1];
         System.arraycopy(ids, 0, all, 0, n);
         all[n] = player.getId();
         HeroFx.hitStop(player, player.position(), JinwooRules.FINISHER_HIT_STOP, all);
      }
   }

   /** Dive landing (spec §5.1 air 3rd hit): 10 in radius 3 and a shadow-wave ring. */
   static void diveLand(ServerPlayer player, JinwooState state) {
      for (LivingEntity t : JinwooTargets.around(player, player.position(), JinwooRules.DIVE_RADIUS, e -> mayHit(player, e))) {
         strike(player, state, t, JinwooRules.DIVE_DAMAGE, false, 2);
      }
      JinwooFx.around(player, JinwooFx.WAVE, 1, player.position(), new Vec3(JinwooRules.DIVE_RADIUS, 0, 0));
      JinwooSounds.play(player, JinwooSounds.LANDING, 1.0F, 0.85F);
      player.fallDistance = 0.0F;
      state.start(JinwooMove.DIVE_LAND, -1);
      JinwooFx.motion(player, Vec3.ZERO, 1);
   }

   // --- block / parry (spec §5.2) ---

   public static void block(ServerPlayer player, JinwooState state, boolean pressed) {
      if (pressed) {
         if (!state.daggers || state.cooldowns[JinwooAbility.BLOCK_CD] > 0 || (state.running() && !state.move.isCombo())) {
            return;
         }
         state.endMove();
         state.blocking = true;
         state.blockTicks = 0;
         state.idle = 0;
         JinwooSounds.play(player, JinwooSounds.BLOCK, 0.6F, 1.3F);
      } else if (state.blocking) {
         endBlock(state);
      }
   }

   static void endBlock(JinwooState state) {
      state.blocking = false;
      state.cooldowns[JinwooAbility.BLOCK_CD] = JinwooRules.BLOCK_COOLDOWN;
   }

   /** Incoming hit while blocking. Null = not covered (behind / no source). */
   static dev.baranhan.viltrumitecore.hero.DamageAbsorb absorb(ServerPlayer player, JinwooState state, DamageSource source, float raw) {
      Vec3 from = source.getSourcePosition();
      if (from == null && source.getEntity() != null) {
         from = source.getEntity().position();
      }
      if (from == null) {
         return null;
      }
      Vec3 look = player.getLookAngle();
      Vec3 d = from.subtract(player.position());
      if (!JinwooRules.inArc(look.x, look.z, d.x, d.z, JinwooRules.BLOCK_ARC_DEGREES)) {
         return null;
      }
      Entity attacker = source.getEntity();
      Vec3 at = player.getEyePosition().add(look.scale(0.6));
      if (state.blockTicks < JinwooRules.PARRY_WINDOW) {
         if (attacker instanceof LivingEntity living && living != player) {
            living.addEffect(hidden(MobEffects.MOVEMENT_SLOWDOWN, JinwooRules.PARRY_STUN, 3));
            living.addEffect(hidden(MobEffects.WEAKNESS, JinwooRules.PARRY_STUN, 1));
            if (mayHit(player, living) && living.distanceToSqr(player) < 6 * 6) {
               strike(player, state, living, JinwooRules.PARRY_RIPOSTE, false, 0);
            }
         }
         JinwooFx.around(player, JinwooFx.PARRY, 0, at, Vec3.ZERO);
         HeroFx.flash(player, at, 0.6F);
         JinwooSounds.play(player, JinwooSounds.PARRY, 1.0F, 1.0F);
         state.blocking = false;
         state.start(JinwooMove.RIPOSTE, attacker == null ? -1 : attacker.getId());
         return dev.baranhan.viltrumitecore.hero.DamageAbsorb.ABSORBED;
      }
      state.mana = Math.max(0.0F, state.mana - JinwooRules.BLOCK_MANA);
      JinwooFx.around(player, JinwooFx.CONTACT, 2, at, Vec3.ZERO);
      JinwooSounds.play(player, JinwooSounds.BLOCK, 0.9F, 0.9F + player.getRandom().nextFloat() * 0.2F);
      return dev.baranhan.viltrumitecore.hero.DamageAbsorb.reduceTo(raw * JinwooRules.BLOCK_FACTOR);
   }

   // --- draw / sheath (spec §5.3) ---

   public static void toggleDaggers(ServerPlayer player, JinwooState state) {
      if (state.running() || state.blocking) {
         return;
      }
      if (state.daggers) {
         state.start(JinwooMove.SHEATH, -1);
         state.sheathLeft = JinwooRules.SHEATH_TICKS;
         JinwooSounds.play(player, JinwooSounds.DAGGER_SHEATH, 0.9F, 1.0F);
      } else {
         state.start(JinwooMove.DRAW, -1);
         state.daggers = true;
         state.drawLeft = JinwooRules.DRAW_TICKS;
         state.idle = 0;
         JinwooSounds.play(player, JinwooSounds.DAGGER_DRAW, 1.0F, 1.0F);
      }
   }

   /** Per tick: draw/sheath clocks, the 8 s idle dissolve, the block clock, the mark. */
   static void tick(ServerPlayer player, JinwooState state) {
      if (state.drawLeft > 0) state.drawLeft--;
      if (state.sheathLeft > 0 && --state.sheathLeft == 0) {
         state.daggers = false;
      }
      if (state.markTicks > 0 && --state.markTicks == 0) {
         state.markTarget = null;
      }
      if (state.comboIdle < 1000) state.comboIdle++;
      if (player.onGround() || player.isInWater()) {
         state.airHits = 0;
      }
      if (state.blocking) {
         if (++state.blockTicks >= JinwooRules.BLOCK_MAX || !state.daggers) {
            endBlock(state);
         }
         state.idle = 0;
      }
      if (state.daggers && !state.running() && !state.blocking && state.sheathLeft == 0 && ++state.idle >= JinwooRules.DAGGER_IDLE) {
         state.start(JinwooMove.SHEATH, -1);
         state.sheathLeft = JinwooRules.SHEATH_TICKS;
         JinwooSounds.play(player, JinwooSounds.DAGGER_SHEATH, 0.6F, 1.1F);
      }
   }

   /** Mark victim id for shadows (spec §5.1). */
   static boolean marked(ShadowSoldierEntity s, JinwooState state, LivingEntity e) {
      return state.markTarget != null && state.markTarget.equals(e.getUUID());
   }
}
