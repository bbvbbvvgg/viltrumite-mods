package dev.baranhan.viltrumitecore.entity.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSkills;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import dev.baranhan.viltrumitecore.hero.captain.ShieldFlight;
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
 * Steve's thrown shield (spec §5.3, rework §16): a real server entity, never saved. TARGET mode ricochets from
 * enemy to enemy (5 bounces, 8 empowered) with a soft assist — the next enemy is the nearest one in a cone around
 * the flight line, close to the hit point and to Steve, in sight; in flight the shield bends at most 20° a tick and
 * gives up on an enemy it overshot. A free throw that hits a wall looks for an enemy off the wall. Every enemy is
 * hit at most once on the way out (hit set). BANK flies straight and reflects off blocks (up to 4 walls, every wall
 * adds damage), RETURN homes back to the owner's chest and passes through blocks so it can never get stuck. Owner
 * gone, other dimension or overtime: it is simply back in the hand.
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
   /** Enemies the shield gave up on this flight (overshot / out-ran it): never picked again. */
   private final Set<Integer> skipped = new HashSet<>();
   private int bounces;
   private int enemyHits;
   private int chase;
   private int offLine;
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
      if (this.age > CaptainRules.SHIELD_MAX_FLIGHT || this.travelled > CaptainRules.THROW_RANGE * 2.2 + this.bounces * CaptainRules.BOUNCE_RADIUS) {
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
      LivingEntity target = this.liveTarget();
      if (target == null && this.targetId >= 0) {
         // the target died or vanished mid-flight: hop on to the next one, or come home
         if (!this.nextTarget(owner, this.position(), this.getDeltaMovement())) {
            this.recall();
            return;
         }
         target = this.liveTarget();
      }
      Vec3 v = this.getDeltaMovement();
      if (target != null) {
         Vec3 aim = target.getBoundingBox().getCenter().subtract(this.position());
         if (aim.lengthSqr() > 1.0E-4) {
            Vec3 dir = v.normalize();
            this.chase++;
            this.offLine = ShieldFlight.degrees(dir, aim) > CaptainRules.SHIELD_GIVE_UP_DEGREES ? this.offLine + 1 : 0;
            if (this.offLine > CaptainRules.SHIELD_GIVE_UP_TICKS || this.chase > CaptainRules.SHIELD_CHASE_TICKS) {
               // overshot or out-ran: no U-turn homing — the next enemy ahead, or home
               this.skipped.add(target.getId());
               if (!this.nextTarget(owner, this.position(), v)) {
                  this.recall();
                  return;
               }
               target = this.liveTarget();
               v = this.getDeltaMovement();
            } else {
               v = ShieldFlight.steer(dir, aim, CaptainRules.SHIELD_TURN_DEGREES).scale(CaptainRules.SHIELD_SPEED);
            }
         }
      }
      if (this.moveAndCollide(owner, v)) {
         return;
      }
      if (target == null && this.targetId >= 0) {
         target = this.liveTarget(); // a wall bounce found one
      }
      if (target != null && target.isAlive() && this.getBoundingBox().inflate(0.45).intersects(target.getBoundingBox())) {
         Vec3 incoming = this.getDeltaMovement();
         this.strike(owner, target, CaptainRules.bounceDamage(this.bounces));
         this.bounces++;
         if (this.bounces >= CaptainRules.maxBounces(this.empowered())
            || !this.nextTarget(owner, target.getBoundingBox().getCenter(), incoming)) {
            this.recall();
         }
         return;
      }
      // passing enemies on the way are clipped too
      this.sweep(owner, CaptainRules.bounceDamage(this.bounces) * 0.6F);
      if (this.targetId < 0 && this.travelled > CaptainRules.THROW_RANGE) {
         this.recall();
      }
   }

   @Nullable
   private LivingEntity liveTarget() {
      Entity t = this.targetId >= 0 ? this.level().getEntity(this.targetId) : null;
      return t instanceof LivingEntity living && living.isAlive() && !this.hit.contains(living.getId()) ? living : null;
   }

   /**
    * Soft next-target assist (rework §16): the best-scored enemy (CaptainRules.bounceScore — nearest, a bit more
    * when ahead) within BOUNCE_RADIUS of {@code from}, inside the cone around {@code incoming}, not too far above or
    * below, within THROW_RANGE + BOUNCE_LEASH of Steve, in sight and not hit or skipped yet. Sends the shield at it.
    */
   private boolean nextTarget(ServerPlayer owner, Vec3 from, Vec3 incoming) {
      Vec3 home = owner.position();
      double leash = CaptainRules.THROW_RANGE + CaptainRules.BOUNCE_LEASH;
      LivingEntity best = null;
      double bestScore = Double.POSITIVE_INFINITY;
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(CaptainRules.BOUNCE_RADIUS),
         e -> CaptainTargets.mayHit(owner, e) && !this.hit.contains(e.getId()) && !this.skipped.contains(e.getId()))) {
         Vec3 c = e.getBoundingBox().getCenter();
         Vec3 to = c.subtract(from);
         double score = CaptainRules.bounceScore(to.length(), ShieldFlight.degrees(incoming, to), c.y - from.y);
         if (score < bestScore && c.distanceTo(home) <= leash && CaptainTargets.visible(this.level(), from, c)) {
            bestScore = score;
            best = e;
         }
      }
      if (best == null) {
         this.targetId = -1;
         return false;
      }
      this.targetId = best.getId();
      this.chase = 0;
      this.offLine = 0;
      Vec3 dir = best.getBoundingBox().getCenter().subtract(from);
      if (dir.lengthSqr() > 1.0E-4) {
         this.setDeltaMovement(dir.normalize().scale(CaptainRules.SHIELD_SPEED));
         CaptainFx.around(this, CaptainFx.RICOCHET, 0, this.position(), dir.normalize());
      }
      return true;
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
         CaptainSkills.ricochetHit(owner, CaptainRules.wallGain(this.empowered()));
         if (this.walls > CaptainRules.WALL_BOUNCES) {
            this.recall();
            return true;
         }
         if (this.mode() == MODE_TARGET && this.targetId < 0) {
            // a free throw off a wall: the soft assist looks for an enemy along the reflected line
            this.setDeltaMovement(r);
            this.nextTarget(owner, at, r);
            return false;
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
         CaptainSkills.ricochetHit(owner, CaptainRules.bounceGain(this.enemyHits++, this.empowered()));
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
