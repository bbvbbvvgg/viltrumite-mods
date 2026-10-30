package dev.baranhan.viltrumitecore.entity.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSkills;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Steve's thrown shield (spec §5.3): a real server entity, never saved. TARGET mode homes from enemy to
 * enemy (3 bounces, 5 empowered), BANK flies straight and reflects off blocks (up to 4 walls, every
 * wall adds damage), RETURN homes back to the owner's chest and passes through blocks so it can never
 * get stuck. Owner gone, other dimension or overtime: it is simply back in the hand.
 */
public class ThrownShieldEntity extends Entity {
   public static final int MODE_TARGET = 0;
   public static final int MODE_BANK = 1;
   public static final int MODE_RETURN = 2;
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ThrownShieldEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(ThrownShieldEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Boolean> EMPOWERED = SynchedEntityData.defineId(ThrownShieldEntity.class, EntityDataSerializers.BOOLEAN);

   @Nullable private UUID ownerUuid;
   private final Set<Integer> hit = new HashSet<>();
   private int bounces;
   private int walls;
   private int targetId = -1;
   private int age;
   private double travelled;
   private boolean caught;

   public ThrownShieldEntity(EntityType<? extends ThrownShieldEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(OWNER, -1);
      this.entityData.define(MODE, (byte)MODE_TARGET);
      this.entityData.define(EMPOWERED, false);
   }

   public void setup(ServerPlayer owner, Vec3 start, Vec3 look, boolean bank, boolean empowered, @Nullable LivingEntity target) {
      this.ownerUuid = owner.getUUID();
      this.entityData.set(OWNER, owner.getId());
      this.entityData.set(MODE, (byte)(bank ? MODE_BANK : MODE_TARGET));
      this.entityData.set(EMPOWERED, empowered);
      this.targetId = target == null ? -1 : target.getId();
      this.setPos(start);
      this.setDeltaMovement(look.normalize().scale(CaptainRules.SHIELD_SPEED));
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   public int mode() {
      return this.entityData.get(MODE);
   }

   public boolean empowered() {
      return this.entityData.get(EMPOWERED);
   }

   @Nullable
   public ServerPlayer owner() {
      if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      return level.getServer().getPlayerList().getPlayer(this.ownerUuid);
   }

   public void recall() {
      if (this.mode() != MODE_RETURN) {
         this.entityData.set(MODE, (byte)MODE_RETURN);
         this.hit.clear();
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         Vec3 v = this.getDeltaMovement();
         this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
         return;
      }
      ServerPlayer owner = this.owner();
      CaptainState state = owner == null ? null : CaptainState.of(owner);
      if (owner == null || state == null || !owner.isAlive() || owner.level() != this.level() || state.shieldId != this.getId()) {
         this.discard();
         return;
      }
      this.age++;
      if (this.age > CaptainRules.SHIELD_MAX_FLIGHT + CaptainRules.SHIELD_LOST_TICKS) {
         CaptainSkills.caught(owner, false);
         this.discard();
         return;
      }
      if (this.age > CaptainRules.SHIELD_MAX_FLIGHT || this.travelled > CaptainRules.THROW_RANGE * 2.2) {
         this.recall();
      }
      if (this.age % 6 == 1) {
         CaptainSounds.play(this, CaptainSounds.SHIELD_FLY, 0.45F, this.empowered() ? 1.15F : 1.0F);
      }
      switch (this.mode()) {
         case MODE_TARGET -> this.tickTarget(owner);
         case MODE_BANK -> this.tickBank(owner);
         default -> this.tickReturn(owner, state);
      }
   }

   private void tickTarget(ServerPlayer owner) {
      Entity t = this.targetId >= 0 ? this.level().getEntity(this.targetId) : null;
      Vec3 v = this.getDeltaMovement();
      if (t instanceof LivingEntity living && living.isAlive()) {
         Vec3 aim = living.getBoundingBox().getCenter().subtract(this.position());
         if (aim.lengthSqr() > 1.0E-4) {
            v = v.normalize().lerp(aim.normalize(), 0.55).normalize().scale(CaptainRules.SHIELD_SPEED);
         }
      } else if (this.targetId >= 0) {
         this.nextTarget(owner, this.position());
      }
      if (this.moveAndCollide(owner, v)) {
         return;
      }
      if (t instanceof LivingEntity living && living.isAlive() && this.getBoundingBox().inflate(0.45).intersects(living.getBoundingBox())) {
         this.strike(owner, living, CaptainRules.bounceDamage(this.bounces));
         this.bounces++;
         if (this.bounces >= CaptainRules.maxBounces(this.empowered())) {
            this.recall();
         } else {
            this.nextTarget(owner, living.getBoundingBox().getCenter());
         }
         return;
      }
      // passing enemies on the way are clipped too
      this.sweep(owner, CaptainRules.bounceDamage(this.bounces) * 0.6F);
      if (this.targetId < 0 && this.travelled > CaptainRules.THROW_RANGE) {
         this.recall();
      }
   }

   private void nextTarget(ServerPlayer owner, Vec3 from) {
      LivingEntity best = null;
      double bestD = CaptainRules.BOUNCE_RADIUS * CaptainRules.BOUNCE_RADIUS;
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(CaptainRules.BOUNCE_RADIUS),
         e -> CaptainTargets.mayHit(owner, e) && !this.hit.contains(e.getId()))) {
         double d = e.getBoundingBox().getCenter().distanceToSqr(from);
         if (d < bestD && CaptainTargets.visible(this.level(), from, e.getBoundingBox().getCenter())) {
            bestD = d;
            best = e;
         }
      }
      if (best == null) {
         this.recall();
         return;
      }
      this.targetId = best.getId();
      Vec3 dir = best.getBoundingBox().getCenter().subtract(from);
      if (dir.lengthSqr() > 1.0E-4) {
         this.setDeltaMovement(dir.normalize().scale(CaptainRules.SHIELD_SPEED));
         CaptainFx.around(this, CaptainFx.RICOCHET, 0, this.position(), dir.normalize());
      }
   }

   private void tickBank(ServerPlayer owner) {
      if (this.moveAndCollide(owner, this.getDeltaMovement())) {
         return;
      }
      this.sweep(owner, CaptainRules.bounceDamage(0) + this.walls * 2.0F);
      if (this.travelled > CaptainRules.THROW_RANGE * 1.6) {
         this.recall();
      }
   }

   /** Moves by v with block collision (target/bank). A wall reflects; too many walls send it home. True when it turned home. */
   private boolean moveAndCollide(ServerPlayer owner, Vec3 v) {
      Vec3 from = this.position();
      Vec3 to = from.add(v);
      BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (hit.getType() != HitResult.Type.MISS) {
         Direction face = hit.getDirection();
         Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
         Vec3 r = v.subtract(n.scale(2 * v.dot(n)));
         Vec3 at = hit.getLocation().add(n.scale(0.15));
         this.setPos(at);
         this.walls++;
         CaptainFx.around(this, CaptainFx.RICOCHET, 1, at, n);
         CaptainSounds.playAt(this, at, CaptainSounds.RICOCHET, 0.9F, CaptainSounds.jitter(this, 1.1F));
         CaptainSkills.ricochetHit(owner);
         if (this.walls > CaptainRules.WALL_BOUNCES) {
            this.recall();
            return true;
         }
         if (this.mode() == MODE_TARGET && this.targetId >= 0) {
            // a wall between the shield and its target: aim off the wall at the target directly
            Entity t = this.level().getEntity(this.targetId);
            if (t != null) {
               Vec3 d = t.getBoundingBox().getCenter().subtract(at);
               if (d.lengthSqr() > 1.0E-4 && d.normalize().dot(n) > 0.05) {
                  r = d.normalize().scale(CaptainRules.SHIELD_SPEED);
               }
            }
         }
         this.setDeltaMovement(r);
         return false;
      }
      this.setPos(to);
      this.setDeltaMovement(v);
      this.travelled += v.length();
      return false;
   }

   private void sweep(ServerPlayer owner, float damage) {
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.35),
         e -> CaptainTargets.mayHit(owner, e) && !this.hit.contains(e.getId()))) {
         this.strike(owner, e, damage);
      }
   }

   private void strike(ServerPlayer owner, LivingEntity e, float damage) {
      this.hit.add(e.getId());
      if (CaptainTargets.strike(owner, e, damage)) {
         Vec3 v = this.getDeltaMovement();
         CaptainTargets.push(e, v.normalize().scale(0.45).add(0, 0.2, 0));
         Vec3 at = e.getBoundingBox().getCenter();
         CaptainFx.around(this, CaptainFx.RICOCHET, 2, at, v.normalize().scale(-1));
         CaptainSounds.playAt(this, at, CaptainSounds.RICOCHET, 1.0F, CaptainSounds.jitter(this, 1.0F));
         CaptainSkills.ricochetHit(owner);
         if (this.empowered()) {
            CaptainTargets.stun(owner, e, 10);
         }
      }
   }

   private void tickReturn(ServerPlayer owner, CaptainState state) {
      Vec3 chest = owner.position().add(0, owner.getBbHeight() * 0.62, 0);
      Vec3 d = chest.subtract(this.position());
      double dist = d.length();
      double catchRadius = CaptainRules.CATCH_RADIUS + (state.move == dev.baranhan.viltrumitecore.hero.captain.CaptainMove.ROLL ? CaptainRules.ROLL_CATCH_BONUS : 0);
      if (dist <= catchRadius + CaptainRules.RETURN_SPEED * 0.5) {
         this.caught = true;
         CaptainSkills.caught(owner, true);
         this.discard();
         return;
      }
      double speed = CaptainRules.RETURN_SPEED * (dist > 30 ? 2.0 : 1.0);
      Vec3 v = this.getDeltaMovement().normalize().lerp(d.normalize(), 0.5).normalize().scale(Math.min(speed, dist));
      this.setPos(this.position().add(v));
      this.setDeltaMovement(v);
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.3),
         e -> CaptainTargets.mayHit(owner, e) && !this.hit.contains(e.getId()))) {
         this.strike(owner, e, CaptainRules.RECALL_DAMAGE);
      }
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isNoGravity() {
      return true;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 96 * 96;
   }
}
