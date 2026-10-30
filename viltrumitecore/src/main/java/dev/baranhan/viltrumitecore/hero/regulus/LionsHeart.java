package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * «Сердце льва» (spec §5.1): the time of his own body is stopped. Absolute invulnerability, no
 * control, no knockback, no healing, recoil to melee attackers and projectiles stopped dead; the
 * price is the heart strain unless a Little King subject carries the heart (§5.0).
 */
public final class LionsHeart {
   static final UUID SPEED_ID = UUID.fromString("4e5a1c0b-7d3e-4a7c-9b1f-2c6d8e0f1a01");

   private LionsHeart() {
   }

   /** LIONS_HEART press: toggle (the off-toggle always reaches the running Lion). */
   static void press(ServerPlayer player, RegulusState state) {
      if (state.lion) {
         turnOff(player, state, true);
         return;
      }

      if (state.lionLock > 0) {
         RegulusSounds.play(player.level(), player.position().add(0.0, 1.2, 0.0), RegulusSounds.LION_DENY, 0.7F, 1.0F);
         RegulusHero.refuse(player, state);
         return;
      }

      if (state.lionCooldown > 0 || state.busy() && state.move.locksCasting()) {
         return;
      }

      state.startMove(RegulusMove.LION_ON);
      HeroSpeech.ability(player, RegulusAbilities.LIONS_HEART);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move == RegulusMove.LION_ON && state.moveTick == RegulusRules.LION_PROTECT_AT) {
         state.lion = true;
         state.lionTicks = 0;
         state.strain = RegulusRules.strainAdd(state.strain, RegulusRules.STRAIN_LION_ON);
         RegulusSounds.play(player.level(), player.position().add(0.0, 1.2, 0.0), RegulusSounds.LION_ON, 1.0F, 1.0F);
         RegulusNet.fx((ServerLevel) player.level(), RegulusFxS2CPacket.LION_ON, player, -1, player.position().add(0.0, 1.0, 0.0));
      }
   }

   /** Every tick: strain, speed, healing pause, seizure. */
   static void tick(ServerPlayer player, RegulusState state) {
      boolean hostAlive = LittleKing.hostAlive(player, state);
      state.strain = RegulusRules.strainStep(state.strain, state.lion, hostAlive);
      if (state.lion) {
         state.lionTicks++;
         state.lionHealth = state.lionTicks == 1 ? player.getHealth() : Math.min(state.lionHealth, player.getHealth());
         if (player.getHealth() > state.lionHealth) {
            // the body's time stands still: no healing while the Lion holds
            player.setHealth(state.lionHealth);
         }

         player.clearFire();
         if (!hostAlive && state.lionTicks % 20 == 0) {
            RegulusSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), RegulusSounds.STRAIN, 0.35F + state.strain / 2000.0F,
               0.9F + state.strain / 2500.0F);
         }

         if (RegulusRules.seizes(state.strain)) {
            seizure(player, state);
         }
      }

      speed(player, state.lion);
   }

   static void turnOff(ServerPlayer player, RegulusState state, boolean voluntary) {
      if (!state.lion) {
         return;
      }

      state.lion = false;
      state.lionTicks = 0;
      state.lionCooldown = RegulusRules.LION_COOLDOWN;
      speed(player, false);
      if (voluntary && !state.busy()) {
         state.startMove(RegulusMove.LION_OFF);
      }

      if (state.move == RegulusMove.PROCESSION) {
         Procession.stop(player, state);
      }

      RegulusSounds.play(player.level(), player.position().add(0.0, 1.2, 0.0), RegulusSounds.LION_OFF, 0.9F, 1.0F);
      RegulusNet.fx((ServerLevel) player.level(), RegulusFxS2CPacket.LION_OFF, player, -1, player.position().add(0.0, 1.0, 0.0));
   }

   /** §5.0: the stopped heart gives out. Forced off, stunned, hurt, locked; strain drops to 400. */
   static void seizure(ServerPlayer player, RegulusState state) {
      boolean wasLion = state.lion;
      state.lion = false;
      state.lionTicks = 0;
      speed(player, false);
      if (state.move == RegulusMove.PROCESSION) {
         Procession.stop(player, state);
      }

      state.startMove(RegulusMove.SEIZURE);
      state.lionLock = RegulusRules.SEIZURE_LOCK;
      state.strain = RegulusRules.SEIZURE_RESET;
      state.lionCooldown = Math.max(state.lionCooldown, RegulusRules.LION_COOLDOWN);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, true));
      HeroDamage.applyInternal(player, RegulusRules.SEIZURE_DAMAGE);
      RegulusSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), RegulusSounds.SEIZURE, 1.0F, 1.0F);
      RegulusNet.fx((ServerLevel) player.level(), RegulusFxS2CPacket.SEIZURE, player, -1, player.position().add(0.0, 1.3, 0.0), Vec3.ZERO,
         wasLion ? 1.0F : 0.0F, 0);
      HeroSpeech.trigger(player, HeroLineTrigger.event(RegulusLines.EVENT_SEIZURE));
   }

   /** Melee recoil (§5.1): 30 % back to a living attacker that struck the Lion, at most once per 10 ticks. */
   static void recoil(ServerPlayer self, RegulusState state, DamageSource source, float raw) {
      if (state.recoilGap > 0 || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == self || source.getEntity() != attacker) {
         return;
      }

      state.recoilGap = RegulusRules.RECOIL_GAP;
      state.recoilPulse++;
      float back = RegulusRules.recoil(raw);
      if (back > 0.0F) {
         attacker.hurt(self.damageSources().thorns(self), back);
         Vec3 away = attacker.position().subtract(self.position());
         RegulusCombat.push(attacker, away, 0.6, 0.15);
      }

      Vec3 at = attacker.getBoundingBox().getCenter().lerp(self.getBoundingBox().getCenter(), 0.5);
      RegulusSounds.play(self.level(), at, RegulusSounds.RECOIL, 0.9F, 0.95F + self.getRandom().nextFloat() * 0.1F);
      RegulusNet.fx((ServerLevel) self.level(), RegulusFxS2CPacket.RECOIL, self, attacker.getId(), at);
   }

   /** A projectile that reaches the Lion stops dead in the air and drops (§5.1). */
   static void stopProjectile(ServerPlayer self, RegulusState state, Projectile projectile) {
      Vec3 v = projectile.getDeltaMovement();
      Vec3 back = v.lengthSqr() < 1.0E-6 ? Vec3.ZERO : v.normalize().scale(-0.35);
      projectile.setPos(projectile.position().add(back));
      projectile.setDeltaMovement(0.0, -0.02, 0.0);
      projectile.hasImpulse = true;
      if (projectile instanceof AbstractHurtingProjectile hurting) {
         hurting.xPower = 0.0;
         hurting.yPower = -0.01;
         hurting.zPower = 0.0;
      }

      state.recoilPulse++;
      RegulusSounds.play(self.level(), projectile.position(), RegulusSounds.PROJECTILE_STOP, 0.8F, 0.9F + self.getRandom().nextFloat() * 0.2F);
      RegulusNet.fx((ServerLevel) self.level(), RegulusFxS2CPacket.PROJECTILE_STOP, self, projectile.getId(), projectile.position());
   }

   static void speed(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      boolean has = speed.getModifier(SPEED_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Regulus Lion's Heart", RegulusRules.LION_SPEED, AttributeModifier.Operation.MULTIPLY_BASE));
      } else if (!on && has) {
         speed.removeModifier(SPEED_ID);
      }
   }
}
