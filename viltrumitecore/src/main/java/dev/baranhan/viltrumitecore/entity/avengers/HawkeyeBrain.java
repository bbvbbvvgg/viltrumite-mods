package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Hawkeye: keeps his distance and shoots - plain arrows, an explosive arrow into crowds, a shock arrow on
 * the strongest enemy and a three-arrow fan; a bow strike and a back-step when cornered, and a grapple
 * line up onto high ground nearby (a roof, a ledge) to shoot from above.
 */
final class HawkeyeBrain extends AvengerBrain {
   private static final int CD_SHOT = 0, CD_EXPLOSIVE = 1, CD_SHOCK = 2, CD_FAN = 3, CD_PERCH = 4, CD_MELEE = 5, CD_STEP = 6;
   static final int RELEASE = 12;
   private static final double ARROW_SPEED = 2.2;
   @Nullable private Vec3 perch;
   private int arrowType;

   HawkeyeBrain(AvengerEntity self) {
      super(self);
   }

   @Override
   public void begin() {
      this.cd[CD_PERCH] = 20;
   }

   @Override
   protected void engage(LivingEntity t, ServerPlayer owner) {
      this.face(t);
      double gap = this.gap(t);
      boolean sees = this.sees(t);
      if (gap < 2.2) {
         this.self.getNavigation().stop();
         if (this.cd[CD_MELEE] <= 0) {
            this.comboStep = (this.comboStep + 1) % 3;
            this.cd[CD_MELEE] = this.comboStep == 2 ? 26 : 14;
            this.start(this.comboStep == 0 ? AvengerAction.ATTACK_1 : this.comboStep == 1 ? AvengerAction.ATTACK_2 : AvengerAction.ATTACK_3,
               this.comboStep == 2 ? 16 : 10);
            return;
         }
      }
      if (gap < 5.0 && this.cd[CD_STEP] <= 0 && this.self.onGround()) {
         this.cd[CD_STEP] = 50;
         Vec3 away = this.self.position().subtract(t.position());
         Vec3 h = away.horizontalDistanceSqr() < 1.0E-4 ? this.flatLook().scale(-1) : new Vec3(away.x, 0, away.z).normalize();
         this.self.setDeltaMovement(h.scale(0.9).add(0, 0.42, 0));
         this.self.hasImpulse = true;
         this.start(AvengerAction.DODGE, 12);
         CaptainSounds.play(this.self, CaptainSounds.ACROBAT, 0.7F, 0.95F);
         return;
      }
      if (this.cd[CD_PERCH] <= 0 && this.self.onGround()) {
         this.cd[CD_PERCH] = 260;
         Vec3 p = this.findPerch(owner, t);
         if (p != null) {
            this.perch = p;
            this.self.getNavigation().stop();
            this.start(AvengerAction.LEAP, 40);
            CaptainFx.around(this.self, CaptainFx.GRAPPLE, 0, this.hand(false), p);
            CaptainSounds.play(this.self, CaptainSounds.BOW_RELEASE, 0.9F, 0.7F);
            return;
         }
      }
      if (sees && gap < 32 && this.cd[CD_SHOT] <= 0) {
         int crowd = this.enemiesAround(t.position(), 3.5).size();
         this.self.getNavigation().stop();
         if (crowd >= 3 && this.cd[CD_EXPLOSIVE] <= 0) {
            this.cd[CD_EXPLOSIVE] = 140;
            this.draw(AvengerAction.SPECIAL_1, AvengerProjectileEntity.ARROW_EXPLOSIVE);
         } else if (t.getMaxHealth() >= 30 && this.cd[CD_SHOCK] <= 0) {
            this.cd[CD_SHOCK] = 180;
            this.draw(AvengerAction.SPECIAL_2, AvengerProjectileEntity.ARROW_SHOCK);
         } else if (crowd >= 2 && this.cd[CD_FAN] <= 0) {
            this.cd[CD_FAN] = 90;
            this.draw(AvengerAction.SPECIAL_3, AvengerProjectileEntity.ARROW);
         } else {
            this.draw(AvengerAction.SHOOT, AvengerProjectileEntity.ARROW);
         }
         return;
      }
      if (this.perch != null && this.self.position().distanceToSqr(this.perch) < 4.0 && sees) {
         this.self.getNavigation().stop(); // hold the high ground
         return;
      }
      if (gap > 16 || !sees) {
         this.approach(t, 1.2);
      } else if (gap < 8) {
         Vec3 away = this.self.position().subtract(t.position()).normalize().scale(4.0);
         Vec3 goal = this.self.position().add(away);
         this.self.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.2);
      } else {
         this.self.getNavigation().stop();
      }
   }

   private void draw(AvengerAction a, int type) {
      this.cd[CD_SHOT] = 22;
      this.arrowType = type;
      this.start(a, 18);
      CaptainSounds.play(this.self, CaptainSounds.BOW_DRAW, 0.8F, CaptainSounds.jitter(this.self, 1.0F));
   }

   /** A standable roof/ledge 4-12 blocks above the ground near Steve with sight on the target, or null. */
   @Nullable
   private Vec3 findPerch(ServerPlayer owner, LivingEntity t) {
      var level = this.self.level();
      Vec3 base = this.self.position();
      Vec3 best = null;
      double bestScore = Double.MAX_VALUE;
      for (int i = 0; i < 16; i++) {
         double ang = i * Math.PI / 8;
         for (double r = 4; r <= 12; r += 4) {
            int x = (int)Math.floor(base.x + Math.cos(ang) * r);
            int z = (int)Math.floor(base.z + Math.sin(ang) * r);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double rise = top - base.y;
            if (rise < 4 || rise > 12) {
               continue;
            }
            BlockPos head = new BlockPos(x, top, z);
            if (!level.getBlockState(head).isAir() || !level.getBlockState(head.above()).isAir() || !level.getFluidState(head.below()).isEmpty()) {
               continue;
            }
            Vec3 spot = new Vec3(x + 0.5, top, z + 0.5);
            if (spot.distanceToSqr(owner.position()) > 20 * 20) {
               continue;
            }
            Vec3 eye = spot.add(0, 1.6, 0);
            if (!dev.baranhan.viltrumitecore.hero.captain.CaptainTargets.visible(level, eye, t.getBoundingBox().getCenter())
               || !dev.baranhan.viltrumitecore.hero.captain.CaptainTargets.visible(level, this.self.getEyePosition(), spot.add(0, 0.6, 0))) {
               continue;
            }
            double score = spot.distanceToSqr(base) - rise * 4;
            if (score < bestScore) {
               bestScore = score;
               best = spot;
            }
         }
      }
      return best;
   }

   @Override
   protected void onAction(AvengerAction a, int tick) {
      switch (a) {
         case ATTACK_1, ATTACK_2 -> {
            if (tick == (a == AvengerAction.ATTACK_1 ? 4 : 5) && this.strikeArc(2.4, 100, 6.0F, 1.1, 0.3) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.WIDOW_STRIKE, 0.8F, a == AvengerAction.ATTACK_1 ? 0.8F : 0.9F);
            }
         }
         case ATTACK_3 -> { // the kick that makes room for the next arrow
            if (tick == 6 && this.strikeArc(2.5, 90, 5.0F, 1.8, 0.4) > 0) {
               CaptainSounds.play(this.self, CaptainSounds.KICK, 0.9F, 0.9F);
            }
         }
         case SHOOT, SPECIAL_1, SPECIAL_2, SPECIAL_3 -> {
            if (tick == RELEASE && this.target != null) {
               if (a == AvengerAction.SPECIAL_3) {
                  for (int i = -1; i <= 1; i++) {
                     this.loose(AvengerProjectileEntity.ARROW, i * 7.0, 6.0F);
                  }
               } else {
                  this.loose(this.arrowType, 0.0, this.arrowType == AvengerProjectileEntity.ARROW ? 7.0F : this.arrowType == AvengerProjectileEntity.ARROW_SHOCK ? 6.0F : 10.0F);
               }
               CaptainSounds.play(this.self, CaptainSounds.BOW_RELEASE, 1.0F, CaptainSounds.jitter(this.self, 1.0F));
            }
         }
         case LEAP -> this.grapple(tick);
         default -> {
         }
      }
   }

   private void grapple(int tick) {
      if (this.perch == null) {
         this.actionTick = this.actionLength;
         return;
      }
      if (tick < 5) {
         return; // the line bites
      }
      this.self.setNoGravity(true);
      Vec3 goal = this.perch.add(0, 0.2, 0);
      Vec3 d = goal.subtract(this.self.position());
      double len = d.length();
      if (len < 0.8 || tick >= this.actionLength - 1) {
         this.self.setNoGravity(false);
         this.self.setDeltaMovement(Vec3.ZERO);
         if (len < 3.0) {
            this.self.teleportTo(goal.x, goal.y, goal.z);
         }
         this.actionTick = this.actionLength;
         CaptainSounds.play(this.self, CaptainSounds.CATCH, 0.5F, 1.4F);
         return;
      }
      this.self.noPhysics = len > 1.5; // the line pulls him over the ledge lip
      this.self.setDeltaMovement(d.scale(Math.min(1.1, len) / len));
      this.self.hasImpulse = true;
      if (tick % 4 == 0) {
         CaptainFx.around(this.self, CaptainFx.GRAPPLE, 1, this.hand(false), this.perch);
      }
   }

   @Override
   protected void onActionEnd(AvengerAction a) {
      if (a == AvengerAction.LEAP) {
         this.self.noPhysics = false;
         this.self.setNoGravity(false);
      }
   }

   private void loose(int type, double yawOffset, float damage) {
      Vec3 from = this.hand(false).add(0, 0.15, 0);
      Vec3 aim = this.target.getBoundingBox().getCenter();
      double dist = aim.distanceTo(from);
      double t = dist / ARROW_SPEED;
      aim = aim.add(this.target.getDeltaMovement().multiply(t, 0, t)); // lead
      Vec3 dir = aim.subtract(from).normalize();
      if (yawOffset != 0) {
         double r = Math.toRadians(yawOffset);
         dir = new Vec3(dir.x * Math.cos(r) - dir.z * Math.sin(r), dir.y, dir.x * Math.sin(r) + dir.z * Math.cos(r));
      }
      Vec3 v = dir.scale(ARROW_SPEED).add(0, 0.5 * 0.025 * t, 0); // arrows drop 0.025 per tick
      AvengerProjectileEntity p = ViltrumiteEntities.AVENGER_PROJECTILE.get().create(this.self.level());
      if (p != null) {
         p.setup(this.self, type, from, v, this.target, damage);
         this.self.level().addFreshEntity(p);
      }
   }
}
