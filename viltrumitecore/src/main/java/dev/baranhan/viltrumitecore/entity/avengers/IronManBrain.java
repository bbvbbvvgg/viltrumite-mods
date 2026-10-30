package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Iron Man Mark VII: always airborne in the fight, circles his target at stand-off range. Repulsors
 * (single / double), shoulder micro-missiles on up to four enemies, the unibeam (piercing channel) and
 * a thruster punch when something gets close.
 */
final class IronManBrain extends AvengerBrain {
   private static final int CD_REPULSOR = 0, CD_MISSILES = 1, CD_UNIBEAM = 2, CD_PUNCH = 3;
   private double orbit;
   private Vec3 beamDir = Vec3.ZERO;

   IronManBrain(AvengerEntity self) {
      super(self);
      this.orbit = self.getRandom().nextDouble() * Math.PI * 2;
   }

   @Override
   public void begin() {
      this.self.setFlying(true);
      this.self.setDeltaMovement(0, 0.6, 0);
      CaptainFx.around(this.self, CaptainFx.THRUST, 0, this.self.position(), Vec3.ZERO);
      CaptainSounds.play(this.self, CaptainSounds.IRON_THRUST, 1.0F, 1.0F);
      this.cd[CD_UNIBEAM] = 200;
      this.cd[CD_MISSILES] = 60;
   }

   @Override
   protected void follow(ServerPlayer owner) {
      this.self.setFlying(true);
      Vec3 goal = this.followPoint(owner).add(0, 3.5, 0);
      this.fly(goal, 0.6);
      this.self.getLookControl().setLookAt(owner.getX() + owner.getLookAngle().x * 10, owner.getEyeY(), owner.getZ() + owner.getLookAngle().z * 10);
   }

   private void fly(Vec3 goal, double maxSpeed) {
      Vec3 at = this.self.position();
      // keep clear of the ground and of walls: rise while something blocks the way
      if (!this.self.level().noCollision(this.self, this.self.getBoundingBox().move(goal.subtract(at).normalize().scale(0.8)))) {
         goal = goal.add(0, 2.0, 0);
      }
      Vec3 d = goal.subtract(at);
      double len = d.length();
      Vec3 want = len < 0.2 ? Vec3.ZERO : d.scale(Math.min(maxSpeed, len * 0.25) / len);
      this.self.setDeltaMovement(this.self.getDeltaMovement().scale(0.6).add(want.scale(0.4)));
      // bob
      this.self.setDeltaMovement(this.self.getDeltaMovement().add(0, Math.sin(this.self.tickCount * 0.15) * 0.01, 0));
   }

   @Override
   protected void engage(LivingEntity t, ServerPlayer owner) {
      this.self.setFlying(true);
      this.face(t);
      double gap = this.gap(t);
      this.orbit += 0.025;
      double r = 7.5;
      Vec3 c = t.position();
      Vec3 goal = new Vec3(c.x + Math.cos(this.orbit) * r, c.y + 3.5 + t.getBbHeight() * 0.3, c.z + Math.sin(this.orbit) * r);
      boolean sees = this.sees(t);
      if (gap < 2.2 && this.cd[CD_PUNCH] <= 0) {
         this.cd[CD_PUNCH] = 30;
         this.start(AvengerAction.ATTACK_3, 12);
         return;
      }
      if (sees && gap < 24 && this.cd[CD_UNIBEAM] <= 0 && gap > 3) {
         this.cd[CD_UNIBEAM] = 340;
         this.beamDir = t.getBoundingBox().getCenter().subtract(this.chest()).normalize();
         this.start(AvengerAction.SPECIAL_2, 44);
         CaptainSounds.play(this.self, CaptainSounds.UNIBEAM, 1.3F, 1.0F);
         return;
      }
      if (gap > 4 && gap < 26 && this.cd[CD_MISSILES] <= 0) {
         this.cd[CD_MISSILES] = 150;
         this.start(AvengerAction.SPECIAL_1, 24);
         return;
      }
      if (sees && gap < 22 && this.cd[CD_REPULSOR] <= 0) {
         this.cd[CD_REPULSOR] = 20;
         this.comboStep++;
         this.start(this.comboStep % 3 == 2 ? AvengerAction.ATTACK_2 : AvengerAction.ATTACK_1, 10);
         return;
      }
      this.fly(goal, gap > 14 ? 0.9 : 0.55);
   }

   private Vec3 chest() {
      return this.self.position().add(0, this.self.getBbHeight() * 0.7, 0).add(this.flatLook().scale(0.3));
   }

   @Override
   protected void onAction(AvengerAction a, int tick) {
      this.self.setDeltaMovement(this.self.getDeltaMovement().scale(0.7));
      LivingEntity t = this.target;
      switch (a) {
         case ATTACK_1, ATTACK_2 -> {
            if (tick == 5 && t != null) {
               this.repulsor(t, a == AvengerAction.ATTACK_2);
            }
         }
         case ATTACK_3 -> {
            if (tick == 2 && t != null) {
               Vec3 d = t.position().subtract(this.self.position()).normalize();
               this.self.setDeltaMovement(d.scale(0.7));
               CaptainFx.around(this.self, CaptainFx.THRUST, 0, this.self.position(), d);
            }
            if (tick == 6) {
               if (this.strikeArc(2.6, 120, 11.0F, 1.3, 0.4) > 0) {
                  CaptainSounds.play(this.self, CaptainSounds.HULK_PUNCH, 0.8F, 1.3F);
               }
            }
         }
         case SPECIAL_1 -> {
            if (tick >= 8 && tick <= 14 && tick % 2 == 0) {
               this.missile((tick - 8) / 2);
            }
         }
         case SPECIAL_2 -> {
            this.self.setDeltaMovement(Vec3.ZERO);
            if (t != null && t.isAlive()) {
               Vec3 want = t.getBoundingBox().getCenter().subtract(this.chest()).normalize();
               this.beamDir = this.beamDir.lerp(want, 0.15).normalize();
               this.face(t);
            }
            if (tick >= 10 && tick <= 38) {
               Vec3 from = this.chest();
               Vec3 end = this.rayEnd(from, from.add(this.beamDir.scale(26)));
               if (tick % 2 == 0) {
                  for (LivingEntity e : this.alongRay(from, end, 0.9)) {
                     if (CaptainTargets.strike(this.self, e, 3.5F)) {
                        CaptainTargets.push(e, this.beamDir.scale(0.25));
                     }
                  }
                  CaptainFx.around(this.self, CaptainFx.UNIBEAM, 3, from, end);
               }
            }
         }
         default -> {
         }
      }
   }

   private void repulsor(LivingEntity t, boolean both) {
      Vec3 from = this.hand(true);
      Vec3 to = t.getBoundingBox().getCenter();
      Vec3 end = this.rayEnd(from, to.add(to.subtract(from).normalize().scale(0.5)));
      boolean clear = end.distanceToSqr(from) >= to.distanceToSqr(from) - 1.0;
      if (clear && CaptainTargets.strike(this.self, t, both ? 10.0F : 7.0F)) {
         CaptainTargets.push(t, to.subtract(from).normalize().scale(both ? 0.9 : 0.5).add(0, 0.2, 0));
      }
      CaptainFx.around(this.self, CaptainFx.REPULSOR, both ? 1 : 0, from, clear ? to : end);
      if (both) {
         CaptainFx.around(this.self, CaptainFx.REPULSOR, 1, this.hand(false), clear ? to : end);
      }
      CaptainSounds.play(this.self, CaptainSounds.REPULSOR, 1.0F, CaptainSounds.jitter(this.self, both ? 0.9F : 1.05F));
   }

   private void missile(int index) {
      LivingEntity main = this.target;
      if (main == null) {
         return;
      }
      List<LivingEntity> near = this.enemiesAround(main.position(), 8.0);
      LivingEntity t = near.isEmpty() ? main : near.get(index % near.size());
      AvengerProjectileEntity p = ViltrumiteEntities.AVENGER_PROJECTILE.get().create(this.self.level());
      if (p == null) {
         return;
      }
      Vec3 look = this.flatLook();
      Vec3 side = new Vec3(-look.z, 0, look.x).scale(index % 2 == 0 ? 0.35 : -0.35);
      Vec3 from = this.self.position().add(0, this.self.getBbHeight() * 0.85, 0).add(side);
      Vec3 v = new Vec3(side.x * 1.2, 0.55, side.z * 1.2).add(look.scale(0.2));
      p.setup(this.self, AvengerProjectileEntity.MISSILE, from, v, t, 7.0F);
      this.self.level().addFreshEntity(p);
      CaptainSounds.play(this.self, CaptainSounds.MISSILE, 0.8F, CaptainSounds.jitter(this.self, 1.0F));
   }
}
