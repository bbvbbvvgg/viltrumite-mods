package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureRules;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureTrace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mark 42 rocket fist (spec §13.3): the glove flies to the crosshair target, hits
 * and returns to the owner's hand. Steered by hand, no block or entity collision.
 * A loaded fist from a previous session is discarded; the hand comes back.
 */
public class RocketFistEntity extends Entity {
   private static final int TRAIL_LENGTH = 8;
   private static final double TARGET_MARGIN = 4.0;
   private final List<Vec3> trail = new ArrayList<>();
   @Nullable
   private UUID ownerId;
   private int targetId = -1;
   @Nullable
   private Vec3 aim;
   private boolean returning;
   private int age;

   public RocketFistEntity(EntityType<? extends RocketFistEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   /** Tony, or a Legion suit (any {@link dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor}). */
   public void setOwner(net.minecraft.world.entity.Entity owner) {
      this.ownerId = owner.getUUID();
   }

   /** Server: fly to {@code target} when set, else to the fixed aim point; then return. */
   public void launch(@Nullable LivingEntity target, Vec3 aimPoint) {
      this.targetId = target == null ? -1 : target.getId();
      this.aim = aimPoint;
   }

   /** Recent positions, newest last (flame trail on the client). */
   public List<Vec3> trail() {
      return this.trail;
   }

   public boolean returning() {
      return this.returning;
   }

   @Override
   protected void defineSynchedData() {
   }

   @Override
   public void tick() {
      super.tick();
      this.age++;
      Level level = this.level();
      if (level.isClientSide) {
         this.recordTrail();
         return;
      }

      if (!(level instanceof ServerLevel serverLevel)) {
         return;
      }

      dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor owner = this.owner(serverLevel);
      if (owner == null || !owner.body().isAlive()) {
         this.discard();
         return;
      }

      if (this.age > SignatureRules.FIST_MAX_TICKS) {
         this.returning = true;
      }

      if (!this.returning) {
         LivingEntity target = this.target(serverLevel, owner);
         if (target != null && this.getBoundingBox().intersects(target.getBoundingBox())) {
            this.strike(owner, target);
            this.returning = true;
         } else if (target == null && this.aim != null && SignatureRules.reached(this.position(), this.aim, 1.0)) {
            this.returning = true;
         } else {
            Vec3 goal = target != null ? target.getBoundingBox().getCenter() : this.aim != null ? this.aim : this.position();
            this.fly(goal, SignatureRules.FIST_SPEED);
         }
      }

      if (this.returning) {
         Vec3 home = owner.hand(true);
         if (SignatureRules.reached(this.position(), home, SignatureRules.FIST_REATTACH_RANGE)) {
            owner.fistReturned();
            this.discard();
            return;
         }

         this.fly(home, SignatureRules.FIST_RETURN_SPEED);
      }

      this.recordTrail();
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      this.discard();
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Nullable
   private dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor owner(ServerLevel level) {
      if (this.ownerId == null) {
         return null;
      }

      net.minecraft.world.entity.Entity owner = level.getPlayerByUUID(this.ownerId);
      return dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor.resolve(owner != null ? owner : level.getEntity(this.ownerId));
   }

   @Nullable
   private LivingEntity target(ServerLevel level, dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor owner) {
      if (this.targetId < 0 || !(level.getEntity(this.targetId) instanceof LivingEntity living) || !living.isAlive()) {
         return null;
      }

      return living.distanceTo(owner.body()) <= SignatureRules.FIST_RANGE + TARGET_MARGIN ? living : null;
   }

   private void strike(dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor owner, LivingEntity target) {
      Vec3 away = target.position().subtract(owner.body().position());
      Vec3 flat = new Vec3(away.x, 0.0, away.z);
      Vec3 dir = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
      if (owner.mayStrike(target) && SignatureTrace.shot(target, owner.attack(), SignatureRules.FIST_DAMAGE)) {
         SignatureTrace.push(target, dir.scale(SignatureRules.FIST_KNOCKBACK).add(0.0, SignatureRules.FIST_KNOCKBACK_UP, 0.0));
      }

      owner.sound(IronManMarkSounds.ROCKET_FIST_HIT.get(), 1.0F, 1.0F);
   }

   private void fly(Vec3 goal, double speed) {
      Vec3 from = this.position();
      Vec3 next = SignatureRules.homingStep(from, goal, speed);
      Vec3 move = next.subtract(from);
      this.setDeltaMovement(move);
      this.setPos(next.x, next.y, next.z);
      if (move.lengthSqr() > 1.0E-6) {
         double horizontal = Math.sqrt(move.x * move.x + move.z * move.z);
         this.setYRot((float)(Math.atan2(-move.x, move.z) * (180.0 / Math.PI)));
         this.setXRot((float)(-Math.atan2(move.y, horizontal) * (180.0 / Math.PI)));
      }
   }

   private void recordTrail() {
      this.trail.add(this.position());
      if (this.trail.size() > TRAIL_LENGTH) {
         this.trail.remove(0);
      }
   }
}
