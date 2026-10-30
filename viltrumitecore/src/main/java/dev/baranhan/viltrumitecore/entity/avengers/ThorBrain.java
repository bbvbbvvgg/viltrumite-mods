package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Thor (MCU 2012): Mjolnir swings (two swings and an overhead smash), a thrown hammer that flies back to
 * his hand, a called-down lightning storm on a crowd, and a hammer-spin flight onto far targets that lands
 * with a thunder ring.
 */
final class ThorBrain extends AvengerBrain {
   private static final int CD_MELEE = 0, CD_THROW = 1, CD_STORM = 2, CD_FLIGHT = 3;
   /** Tick of SPECIAL_1 at which the hammer leaves the hand (the renderer hides it from here). */
   static final int THROW_RELEASE = 7;
   @Nullable private AvengerProjectileEntity hammer;
   @Nullable private Vec3 flightGoal;

   ThorBrain(AvengerEntity self) {
      super(self);
   }

   @Override
   public void begin() {
      this.cd[CD_STORM] = 40;
   }

   @Override
   protected void engage(LivingEntity t, ServerPlayer owner) {
      this.face(t);
      double gap = this.gap(t);
      boolean sees = this.sees(t);
      if (this.cd[CD_STORM] <= 0 && gap < 18 && sees && this.enemiesAround(t.position(), 6.0).size() >= 2) {
         this.cd[CD_STORM] = 300;
         this.self.getNavigation().stop();
         this.start(AvengerAction.SPECIAL_2, 34);
         CaptainSounds.play(this.self, CaptainSounds.HAMMER_SPIN, 1.2F, 0.9F);
         return;
      }
      if (gap > 10 && gap < 28 && this.cd[CD_FLIGHT] <= 0) {
         this.flight(t);
         return;
      }
      if (gap > 4 && gap < 22 && sees && this.cd[CD_THROW] <= 0) {
         this.cd[CD_THROW] = 110;
         this.self.getNavigation().stop();
         this.start(AvengerAction.SPECIAL_1, 90);
         CaptainSounds.play(this.self, CaptainSounds.HAMMER_SWING, 1.0F, 0.8F);
         return;
      }
      if (gap < 2.6) {
         this.self.getNavigation().stop();
         if (this.cd[CD_MELEE] <= 0) {
            this.comboStep = (this.comboStep + 1) % 3;
            this.cd[CD_MELEE] = this.comboStep == 2 ? 22 : 12;
            this.start(this.comboStep == 1 ? AvengerAction.ATTACK_1 : this.comboStep == 2 ? AvengerAction.ATTACK_2 : AvengerAction.ATTACK_3,
               this.comboStep == 0 ? 20 : 12);
            CaptainSounds.play(this.self, CaptainSounds.HAMMER_SWING, 0.9F, CaptainSounds.jitter(this.self, 1.0F));
         }
         return;
      }
      this.approach(t, 1.25);
   }

   @Override
   protected void unreachable(LivingEntity t) {
      if (this.cd[CD_FLIGHT] <= 0) {
         this.flight(t);
      } else {
         super.unreachable(t);
      }
   }

   private void flight(LivingEntity t) {
      this.cd[CD_FLIGHT] = 160;
      this.self.getNavigation().stop();
      this.flightGoal = t.position();
      this.self.setFlying(true);
      this.self.setDeltaMovement(this.self.getDeltaMovement().add(0, 0.9, 0));
      this.start(AvengerAction.LEAP, 60);
      CaptainSounds.play(this.self, CaptainSounds.HAMMER_SPIN, 1.0F, 1.2F);
   }

   @Override
   protected void onAction(AvengerAction a, int tick) {
      switch (a) {
         case ATTACK_1, ATTACK_2 -> {
            if (tick == 6 && this.strikeArc(3.2, 120, 11.0F, 1.0, 0.3) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.HAMMER_HIT, 1.0F, CaptainSounds.jitter(this.self, 1.0F));
            }
         }
         case ATTACK_3 -> {
            if (tick == 11) {
               Vec3 at = this.self.position().add(this.flatLook().scale(2.0));
               for (LivingEntity e : this.enemiesAround(at.add(0, 0.5, 0), 3.0)) {
                  if (CaptainTargets.strike(this.self, e, 15.0F)) {
                     CaptainTargets.push(e, e.position().subtract(at).normalize().scale(0.5).add(0, 0.55, 0));
                     CaptainTargets.stun(this.self, e, 20);
                  }
               }
               CaptainFx.around(this.self, CaptainFx.GROUND, 1, at, new Vec3(3.0, 0, 0));
               HeroFx.electricRing((ServerLevel)this.self.level(), at.add(0, 0.1, 0), 3.0);
               CaptainSounds.playAt(this.self, at, CaptainSounds.HAMMER_HIT, 1.3F, 0.8F);
            }
         }
         case SPECIAL_1 -> {
            if (tick == THROW_RELEASE && this.target != null) {
               Vec3 from = this.hand(true).add(0, 0.3, 0);
               Vec3 v = this.target.getBoundingBox().getCenter().subtract(from).normalize().scale(1.5);
               AvengerProjectileEntity p = ViltrumiteEntities.AVENGER_PROJECTILE.get().create(this.self.level());
               if (p != null) {
                  p.setup(this.self, AvengerProjectileEntity.HAMMER, from, v, this.target, 12.0F);
                  this.self.level().addFreshEntity(p);
                  this.hammer = p;
               }
            } else if (tick > THROW_RELEASE && (this.hammer == null || this.hammer.isRemoved())) {
               if (this.hammer != null) {
                  CaptainSounds.play(this.self, CaptainSounds.CATCH, 0.7F, 0.6F);
               }
               this.hammer = null;
               this.actionTick = this.actionLength; // caught
            } else if (tick == THROW_RELEASE && this.target == null) {
               this.actionTick = this.actionLength;
            }
         }
         case SPECIAL_2 -> {
            if (tick == 16) {
               HeroFx.electricArc((ServerLevel)this.self.level(), this.hand(true).add(0, 0.9, 0), this.hand(true).add(0, 9.0, 0));
               CaptainSounds.play(this.self, CaptainSounds.THUNDER, 1.4F, 1.0F);
            }
            if (tick == 22 && this.target != null) {
               this.storm(this.target.position());
            }
         }
         case LEAP -> this.flightTick(tick);
         default -> {
         }
      }
   }

   private void storm(Vec3 center) {
      ServerLevel level = (ServerLevel)this.self.level();
      List<LivingEntity> hit = this.enemiesAround(center.add(0, 0.5, 0), 6.0);
      int bolts = 0;
      for (LivingEntity e : hit) {
         if (bolts++ >= 4) {
            break;
         }
         HeroFx.electricBolt(level, e.position(), 22.0F, 0.8F, CaptainSounds.THUNDER.get());
         if (CaptainTargets.strike(this.self, e, 13.0F)) {
            CaptainTargets.stun(this.self, e, 24);
            CaptainFx.around(this.self, CaptainFx.STUN, 24, e.getBoundingBox().getCenter(), Vec3.ZERO, e.getId());
         }
      }
      if (bolts == 0) {
         HeroFx.electricBolt(level, center, 22.0F, 0.8F, CaptainSounds.THUNDER.get());
      }
      HeroFx.electricRing(level, center.add(0, 0.1, 0), 5.0);
   }

   private void flightTick(int tick) {
      Vec3 goal = this.target != null ? this.target.position() : this.flightGoal;
      if (goal == null) {
         this.land(false);
         return;
      }
      this.flightGoal = goal;
      Vec3 d = goal.add(0, tick < 12 ? 4.0 : 0.6, 0).subtract(this.self.position());
      double len = d.length();
      if (tick > 8 && (len < 2.2 || this.self.horizontalCollision || this.self.onGround() && tick > 14)) {
         this.land(true);
         return;
      }
      Vec3 want = len < 1.0E-3 ? Vec3.ZERO : d.scale(Math.min(1.3, len) / len);
      this.self.setDeltaMovement(this.self.getDeltaMovement().scale(0.4).add(want.scale(0.6)));
      this.self.hasImpulse = true;
      if (tick % 6 == 0) {
         CaptainFx.around(this.self, CaptainFx.THRUST, 2, this.self.position(), this.self.getDeltaMovement());
      }
      if (tick >= this.actionLength - 1) {
         this.land(false);
      }
   }

   private void land(boolean strike) {
      this.self.setFlying(false);
      this.self.setDeltaMovement(this.self.getDeltaMovement().multiply(0.2, 0.0, 0.2).add(0, -0.6, 0));
      this.actionTick = this.actionLength;
      if (!strike) {
         return;
      }
      Vec3 at = this.self.position();
      for (LivingEntity e : this.enemiesAround(at.add(0, 0.5, 0), 4.0)) {
         if (CaptainTargets.strike(this.self, e, 12.0F)) {
            CaptainTargets.push(e, e.position().subtract(at).normalize().scale(0.7).add(0, 0.45, 0));
         }
      }
      HeroFx.electricRing((ServerLevel)this.self.level(), at.add(0, 0.1, 0), 4.0);
      CaptainFx.around(this.self, CaptainFx.GROUND, 1, at, new Vec3(4.0, 0, 0));
      CaptainSounds.playAt(this.self, at, CaptainSounds.HEAVY_LAND, 1.2F, 1.0F);
   }

   @Override
   protected void onActionEnd(AvengerAction a) {
      if (a == AvengerAction.LEAP) {
         this.self.setFlying(false);
      }
      if (a == AvengerAction.SPECIAL_1 && this.hammer != null && !this.hammer.isRemoved()) {
         this.hammer.discard(); // never leave Mjolnir behind
         this.hammer = null;
      }
   }
}
