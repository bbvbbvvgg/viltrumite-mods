package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Black Widow: twin pistols at mid range, a fast three-hit strike chain up close, the Widow's Bite (an
 * electric dart that stuns), an acrobatic leap-kick onto a target and a back-flip when she gets hit.
 */
final class WidowBrain extends AvengerBrain {
   private static final int CD_MELEE = 0, CD_PISTOL = 1, CD_BITE = 2, CD_LEAP = 3, CD_DODGE = 4, CD_SWEEP = 5;
   private boolean airborne;

   WidowBrain(AvengerEntity self) {
      super(self);
   }

   @Override
   protected void onHurt(LivingEntity attacker) {
      if (this.cd[CD_DODGE] <= 0 && this.self.onGround() && (!this.busy() || this.action == AvengerAction.SHOOT)) {
         this.cd[CD_DODGE] = 70;
         Vec3 away = this.self.position().subtract(attacker.position());
         Vec3 h = away.horizontalDistanceSqr() < 1.0E-4 ? this.flatLook().scale(-1) : new Vec3(away.x, 0, away.z).normalize();
         this.face(attacker);
         this.self.setDeltaMovement(h.scale(0.75).add(0, 0.48, 0));
         this.self.hasImpulse = true;
         this.start(AvengerAction.DODGE, 14);
         CaptainSounds.play(this.self, CaptainSounds.ACROBAT, 0.8F, CaptainSounds.jitter(this.self, 1.1F));
      }
   }

   @Override
   protected void engage(LivingEntity t, ServerPlayer owner) {
      this.face(t);
      double gap = this.gap(t);
      boolean sees = this.sees(t);
      if (gap < 2.4 && this.cd[CD_SWEEP] <= 0 && this.enemiesAround(this.self.position(), 3.0).size() >= 2) {
         this.cd[CD_SWEEP] = 120;
         this.self.getNavigation().stop();
         this.start(AvengerAction.SPECIAL_3, 40);
         CaptainSounds.play(this.self, CaptainSounds.ACROBAT, 0.9F, 0.9F);
         return;
      }
      if (gap < 5.0 && sees && this.cd[CD_BITE] <= 0) {
         this.cd[CD_BITE] = 160;
         this.self.getNavigation().stop();
         this.start(AvengerAction.SPECIAL_1, 14);
         return;
      }
      if (gap > 4.5 && gap < 11 && this.cd[CD_LEAP] <= 0 && this.self.onGround()) {
         this.cd[CD_LEAP] = 100;
         this.self.getNavigation().stop();
         int ticks = (int)Math.max(10, Math.min(20, gap * 1.4));
         this.self.setDeltaMovement(ballistic(this.self.position(), t.position(), ticks));
         this.self.hasImpulse = true;
         this.airborne = false;
         this.start(AvengerAction.LEAP, ticks + 12);
         CaptainSounds.play(this.self, CaptainSounds.ACROBAT, 0.9F, CaptainSounds.jitter(this.self, 1.0F));
         return;
      }
      if (gap > 5 && gap < 20 && sees && this.cd[CD_PISTOL] <= 0) {
         this.cd[CD_PISTOL] = 34;
         this.self.getNavigation().stop();
         this.start(AvengerAction.SHOOT, 18);
         return;
      }
      if (gap < 1.9) {
         this.self.getNavigation().stop();
         if (this.cd[CD_MELEE] <= 0) {
            this.comboStep = (this.comboStep + 1) % 3;
            this.cd[CD_MELEE] = this.comboStep == 0 ? 16 : 8;
            this.start(this.comboStep == 1 ? AvengerAction.ATTACK_1 : this.comboStep == 2 ? AvengerAction.ATTACK_2 : AvengerAction.ATTACK_3,
               this.comboStep == 0 ? 14 : 9);
         }
         return;
      }
      this.approach(t, 1.45);
   }

   @Override
   protected void onAction(AvengerAction a, int tick) {
      switch (a) {
         case ATTACK_1, ATTACK_2 -> {
            if (tick == 4 && this.strikeArc(2.2, 90, 6.0F, 0.4, 0.1) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.WIDOW_STRIKE, 0.9F, CaptainSounds.jitter(this.self, a == AvengerAction.ATTACK_1 ? 1.1F : 1.0F));
            }
         }
         case SPECIAL_3 -> {
            if (tick == 10 || tick == 16) { // low sweep: the scything leg goes all the way round
               if (this.strikeArc(2.6, 360, 6.0F, 0.5, 0.2) > 0) {
                  CaptainSounds.play(this.self, CaptainSounds.WIDOW_STRIKE, 1.0F, 0.8F);
               }
               for (LivingEntity e : this.enemiesAround(this.self.position(), 3.0)) {
                  e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, true));
               }
            }
         }
         case ATTACK_3 -> {
            if (tick == 7 && this.strikeArc(2.5, 120, 9.0F, 1.0, 0.45) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.WIDOW_STRIKE, 1.0F, 0.85F);
            }
         }
         case SHOOT -> {
            if ((tick == 4 || tick == 8 || tick == 12 || tick == 15) && this.target != null) {
               this.shot(tick % 8 == 0);
            }
         }
         case SPECIAL_1 -> {
            if (tick == 6 && this.target != null) {
               Vec3 from = this.hand(false);
               Vec3 to = this.target.getBoundingBox().getCenter();
               Vec3 end = this.rayEnd(from, to);
               boolean clear = end.distanceToSqr(to) < 0.25;
               CaptainFx.around(this.self, CaptainFx.BITE, 0, from, clear ? to : end, clear ? this.target.getId() : -1);
               CaptainSounds.play(this.self, CaptainSounds.WIDOW_BITE, 1.0F, 1.0F);
               if (clear && this.hit(this.target, 5.0F)) {
                  CaptainTargets.stun(this.self, this.target, 50);
                  CaptainFx.around(this.self, CaptainFx.STUN, 50, this.target.getBoundingBox().getCenter(), Vec3.ZERO, this.target.getId());
               }
            }
         }
         case LEAP -> {
            if (!this.self.onGround()) {
               this.airborne = true;
               if (this.target != null && this.gap(this.target) < 1.2) {
                  this.kick();
               }
            } else if (this.airborne || tick > 4) {
               this.kick();
            }
         }
         default -> {
         }
      }
   }

   private void kick() {
      if (this.strikeArc(2.0, 160, 8.0F, 1.1, 0.4) > 0) {
         CaptainSounds.play(this.self, CaptainSounds.WIDOW_STRIKE, 1.0F, 0.8F);
      }
      this.actionTick = this.actionLength;
   }

   private void shot(boolean right) {
      Vec3 from = this.hand(right);
      Vec3 aim = this.target.getBoundingBox().getCenter();
      Vec3 dir = aim.subtract(from).normalize();
      Vec3 to = from.add(dir.scale(24));
      Vec3 end = this.rayEnd(from, to);
      List<LivingEntity> hit = this.alongRay(from, end, 0.25);
      LivingEntity first = null;
      double best = Double.MAX_VALUE;
      for (LivingEntity e : hit) {
         double d = e.distanceToSqr(from);
         if (d < best) {
            best = d;
            first = e;
         }
      }
      if (first != null) {
         end = first.getBoundingBox().getCenter();
         this.hit(first, 3.5F);
      }
      CaptainFx.around(this.self, CaptainFx.SHOT, right ? 1 : 0, from, end);
      CaptainSounds.play(this.self, CaptainSounds.WIDOW_SHOT, 0.9F, CaptainSounds.jitter(this.self, 1.0F));
   }
}
