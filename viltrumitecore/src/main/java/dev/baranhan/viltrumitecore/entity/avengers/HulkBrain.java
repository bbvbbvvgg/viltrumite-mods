package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hulk: heavy hooks and a double-fist smash, huge leaps onto far targets (landing shockwave), a ground
 * pound when surrounded and a roar that staggers everything around him.
 */
final class HulkBrain extends AvengerBrain {
   private static final int CD_MELEE = 0, CD_LEAP = 1, CD_POUND = 2, CD_ROAR = 3;
   private boolean airborne;

   HulkBrain(AvengerEntity self) {
      super(self);
   }

   @Override
   public void begin() {
      this.cd[CD_ROAR] = 0;
   }

   @Override
   protected void engage(LivingEntity t, ServerPlayer owner) {
      this.face(t);
      double gap = this.gap(t);
      if (this.cd[CD_ROAR] <= 0 && gap < 10) {
         this.cd[CD_ROAR] = 500;
         this.start(AvengerAction.SPECIAL_2, 30);
         CaptainSounds.play(this.self, CaptainSounds.HULK_ROAR, 1.6F, 1.0F);
         return;
      }
      if (gap > 8 && gap < 26 && this.cd[CD_LEAP] <= 0 && this.self.onGround()) {
         this.leap(t.position());
         return;
      }
      if (this.cd[CD_POUND] <= 0 && this.enemiesAround(this.self.position(), 5.5).size() >= 3) {
         this.cd[CD_POUND] = 180;
         this.start(AvengerAction.SPECIAL_1, 26);
         return;
      }
      if (gap < 2.4) {
         this.self.getNavigation().stop();
         if (this.cd[CD_MELEE] <= 0) {
            this.cd[CD_MELEE] = 14;
            this.comboStep = (this.comboStep + 1) % 3;
            this.start(this.comboStep == 2 ? AvengerAction.ATTACK_3 : this.comboStep == 0 ? AvengerAction.ATTACK_1 : AvengerAction.ATTACK_2,
               this.comboStep == 2 ? 18 : 13);
         }
         return;
      }
      this.approach(t, 1.35);
   }

   @Override
   protected void unreachable(LivingEntity t) {
      if (this.cd[CD_LEAP] <= 0 && this.self.onGround()) {
         this.leap(t.position()); // can't walk there: jump there
      } else {
         super.unreachable(t);
      }
   }

   private void leap(Vec3 to) {
      this.cd[CD_LEAP] = 120;
      this.self.getNavigation().stop();
      int ticks = (int)Math.max(14, Math.min(30, this.self.position().distanceTo(to) * 1.1));
      this.self.setDeltaMovement(ballistic(this.self.position(), to, ticks));
      this.self.hasImpulse = true;
      this.airborne = false;
      this.start(AvengerAction.LEAP, ticks + 30);
      CaptainSounds.play(this.self, CaptainSounds.HEAVY_LAND, 0.8F, 1.3F);
   }

   @Override
   protected void onAction(AvengerAction a, int tick) {
      switch (a) {
         case ATTACK_1, ATTACK_2 -> {
            if (tick == 7 && this.strikeArc(3.0, 110, 14.0F, 1.4, 0.35) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.HULK_PUNCH, 1.2F, CaptainSounds.jitter(this.self, 0.9F));
            }
         }
         case ATTACK_3 -> {
            if (tick == 10) {
               Vec3 at = this.self.position().add(this.flatLook().scale(2.0));
               this.smash(at, 3.2, 18.0F, 0.7);
            }
         }
         case SPECIAL_1 -> {
            if (tick == 15) {
               this.smash(this.self.position(), 6.0, 14.0F, 0.9);
            }
         }
         case SPECIAL_2 -> {
            if (tick == 8) {
               for (LivingEntity e : this.enemiesAround(this.self.position(), 9.0)) {
                  e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, true));
                  e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false, true));
                  CaptainTargets.push(e, e.position().subtract(this.self.position()).normalize().scale(0.6));
               }
               CaptainFx.around(this.self, CaptainFx.GROUND, 4, this.self.position(), new Vec3(9.0, 0, 0));
            }
         }
         case LEAP -> {
            if (!this.self.onGround()) {
               this.airborne = true;
            } else if (this.airborne || tick > 6) {
               this.smash(this.self.position(), 4.5, 16.0F, 1.0);
               this.actionTick = this.actionLength; // done
            }
         }
         default -> {
         }
      }
   }

   private void smash(Vec3 at, double radius, float damage, double lift) {
      for (LivingEntity e : this.enemiesAround(at.add(0, 0.5, 0), radius)) {
         double d = Math.sqrt(e.getBoundingBox().distanceToSqr(at));
         if (CaptainTargets.strike(this.self, e, (float)(damage * (1.0 - 0.4 * Math.min(1.0, d / radius))))) {
            Vec3 away = e.position().subtract(at);
            Vec3 h = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize();
            CaptainTargets.push(e, h.scale(0.6).add(0, lift * 0.6, 0));
         }
      }
      CaptainFx.around(this.self, CaptainFx.GROUND, 0, at, new Vec3(radius, 0, 0));
      CaptainSounds.playAt(this.self, at, CaptainSounds.HULK_SMASH, 1.4F, CaptainSounds.jitter(this.self, 1.0F));
   }
}
