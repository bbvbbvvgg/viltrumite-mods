package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWar;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarSounds;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of the city in Ebony Maw's telekinetic grip (THANOS 2.0): a torn-out block (or a chunk of
 * wall, {@link #size}) that circles him, waits at a point, flies at a hero, falls on one or stands
 * in a barrier ring that stops shots. On impact: limited damage around, a small crater through the
 * war's destruction budget. Heroes can knock one out of the air (any hit shatters it). Never saved.
 */
public class WarDebrisEntity extends Entity implements ThanosAlly {
   public static final byte ORBIT = 0, HOLD = 1, FLY = 2, FALL = 3, BARRIER = 4;
   private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(WarDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
   private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(WarDebrisEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(WarDebrisEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(WarDebrisEntity.class, EntityDataSerializers.INT);

   @Nullable
   private BlackOrderEntity owner;
   private int life = 240;
   private float damage;
   private double radius = 1.5;
   private int craterBlocks;
   private double craterRadius = 1.6;
   @Nullable
   private Vec3 point;
   private double orbitAngle;
   private double orbitRadius = 3.0;
   private double orbitHeight = 2.5;
   private double orbitSpeed = 0.12;
   @Nullable
   private LivingEntity homing;
   private int homingTicks;
   private double speed;
   private boolean done;
   private int catches;

   public WarDebrisEntity(EntityType<? extends WarDebrisEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   /** A new piece at {@code at} (server). */
   @Nullable
   public static WarDebrisEntity spawn(ServerLevel level, @Nullable BlackOrderEntity owner, BlockState state, Vec3 at, float size) {
      WarDebrisEntity d = ViltrumiteEntities.WAR_DEBRIS.get().create(level);
      if (d == null) {
         return null;
      }

      d.owner = owner;
      d.entityData.set(BLOCK, state.isAir() ? Blocks.STONE.defaultBlockState() : state);
      d.entityData.set(SIZE, size);
      d.entityData.set(OWNER, owner == null ? -1 : owner.getId());
      d.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
      return level.addFreshEntity(d) ? d : null;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(BLOCK, Blocks.STONE.defaultBlockState());
      this.entityData.define(MODE, ORBIT);
      this.entityData.define(SIZE, 1.0F);
      this.entityData.define(OWNER, -1);
   }

   public BlockState block() {
      return this.entityData.get(BLOCK);
   }

   public byte mode() {
      return this.entityData.get(MODE);
   }

   public float size() {
      return this.entityData.get(SIZE);
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   private void setMode(byte mode) {
      this.entityData.set(MODE, mode);
   }

   // ---- orders ------------------------------------------------------------------------------------
   public void orbit(double angle, double radius, double height, double speed) {
      this.orbitAngle = angle;
      this.orbitRadius = radius;
      this.orbitHeight = height;
      this.orbitSpeed = speed;
      this.setMode(ORBIT);
   }

   public void hold(Vec3 at) {
      this.point = at;
      this.setMode(HOLD);
   }

   public void barrier(Vec3 at, int ticks) {
      this.point = at;
      this.life = Math.max(this.life, ticks);
      this.setMode(BARRIER);
   }

   /** Thrown: {@code velocity}, steering a little towards {@code homing} for the first ticks. */
   public void launch(Vec3 velocity, @Nullable LivingEntity homing, float damage, double radius, int craterBlocks) {
      this.setDeltaMovement(velocity);
      this.speed = velocity.length();
      this.homing = homing;
      this.homingTicks = homing == null ? 0 : 10;
      this.damage = damage;
      this.radius = radius;
      this.craterBlocks = craterBlocks;
      this.life = 80;
      this.setMode(FLY);
   }

   /** Let go: it falls from here (a dropped piece of a building hits as hard as it was given). */
   public void drop(float damage, double radius, int craterBlocks) {
      this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.0, 0.3));
      this.damage = damage;
      this.radius = radius;
      this.craterBlocks = craterBlocks;
      this.craterRadius = Math.max(1.6, radius * 0.8);
      this.life = 120;
      this.setMode(FALL);
   }

   public void setLife(int ticks) {
      this.life = ticks;
   }

   public int catches() {
      return this.catches;
   }

   // ---- ticking -----------------------------------------------------------------------------------
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         // the client follows the server's positions; a thrown piece is extrapolated a little
         return;
      }

      if (this.done) {
         return;
      }

      if (dev.baranhan.viltrumitecore.hero.control.WorldPause.isPaused(this)) {
         return;
      }

      if (--this.life <= 0 || this.getY() < this.level().getMinBuildHeight()) {
         this.shatter();
         return;
      }

      ThanosWar war = ThanosWar.of(this.level());
      if (war == null && this.tickCount > 2) {
         this.discard();
         return;
      }

      BlackOrderEntity o = this.owner;
      byte mode = this.mode();
      boolean held = mode == ORBIT || mode == HOLD || mode == BARRIER;
      if (held && (o == null || !o.isAlive() || !o.fighting())) {
         this.drop(4.0F, 1.2, 0);
         mode = FALL;
      }

      switch (mode) {
         case ORBIT -> {
            this.orbitAngle += this.orbitSpeed;
            Vec3 c = o.position();
            Vec3 want = c.add(Math.cos(this.orbitAngle) * this.orbitRadius, this.orbitHeight + Math.sin(this.tickCount * 0.15 + this.getId()) * 0.35,
               Math.sin(this.orbitAngle) * this.orbitRadius);
            this.toward(want, 0.9);
         }
         case HOLD -> this.toward(this.point == null ? this.position() : this.point, 0.7);
         case BARRIER -> {
            this.toward(this.point == null ? this.position() : this.point, 0.8);
            this.catchShots();
         }
         case FLY -> {
            Vec3 v = this.getDeltaMovement();
            LivingEntity h = this.homing;
            if (h != null && this.homingTicks-- > 0 && h.isAlive()) {
               Vec3 to = h.position().add(0, h.getBbHeight() * 0.5, 0).subtract(this.position());
               if (to.lengthSqr() > 1.0E-4) {
                  v = v.add(to.normalize().scale(this.speed).subtract(v).scale(0.14));
               }
            }

            this.travel(v.add(0.0, -0.015, 0.0));
         }
         case FALL -> {
            Vec3 v = this.getDeltaMovement().add(0.0, -0.08, 0.0);
            this.travel(new Vec3(v.x * 0.98, Math.max(-2.2, v.y), v.z * 0.98));
         }
         default -> {
         }
      }
   }

   private void toward(Vec3 want, double maxStep) {
      Vec3 d = want.subtract(this.position());
      double len = d.length();
      Vec3 step = len > maxStep ? d.scale(maxStep / len) : d;
      this.setDeltaMovement(step);
      this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
   }

   private void travel(Vec3 v) {
      Vec3 from = this.position();
      Vec3 to = from.add(v);
      double r = this.size() * 0.5 + 0.35;
      if (this.damage > 0.0F) {
         LivingEntity hit = null;
         double best = Double.MAX_VALUE;
         AABB box = new AABB(from, to).inflate(r);
         for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, this::hittable)) {
            double d = e.position().distanceToSqr(from);
            if (d < best) {
               best = d;
               hit = e;
            }
         }

         if (hit != null) {
            this.impact(hit.position().add(0, hit.getBbHeight() * 0.4, 0), hit);
            return;
         }
      }

      BlockHitResult bh = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (bh.getType() != HitResult.Type.MISS) {
         this.setPos(bh.getLocation().x, bh.getLocation().y, bh.getLocation().z);
         this.impact(bh.getLocation(), null);
         return;
      }

      this.setDeltaMovement(v);
      this.setPos(to.x, to.y, to.z);
   }

   private boolean hittable(LivingEntity e) {
      BlackOrderEntity o = this.owner;
      if (o != null) {
         return o.victim(e);
      }

      ThanosWar w = ThanosWar.of(this.level());
      return w != null && ThanosTargets.victim(e, w.thanos);
   }

   private void impact(Vec3 at, @Nullable LivingEntity direct) {
      if (this.done) {
         return;
      }

      this.done = true;
      ServerLevel level = (ServerLevel)this.level();
      ThanosWar war = ThanosWar.of(level);
      if (this.damage > 0.0F) {
         AABB box = new AABB(at, at).inflate(this.radius + 0.5);
         for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, box, this::hittable)) {
            double d = v.position().add(0, v.getBbHeight() * 0.5, 0).distanceTo(at);
            float f = v == direct ? 1.0F : ThanosWarRules.strikeFalloff(d, this.radius + v.getBbWidth() * 0.5);
            if (f <= 0.0F) {
               continue;
            }

            boolean hurt;
            if (this.owner != null && this.owner.isAlive()) {
               hurt = this.owner.strike(v, this.damage * f);
            } else {
               hurt = war != null && ThanosTargets.hurt(war.thanos, v, this.damage * f, war.combat.scale());
            }

            if (hurt) {
               Vec3 push = v.position().subtract(at);
               push = push.lengthSqr() < 1.0E-4 ? this.getDeltaMovement().normalize() : push.normalize();
               ThanosTargets.push(v, push.scale(0.5 * f).add(0.0, 0.25 * f, 0.0));
            }
         }
      }

      if (war != null) {
         if (this.craterBlocks > 0) {
            war.destruction.crater(at, this.craterRadius, ThanosWarRules.STRIKE_HARDNESS, this.craterBlocks);
         }

         if (this.size() >= 1.5F) {
            war.destruction.impact(at, (float)(this.radius * 2.0), 0.3F, AftermathCause.DESTRUCTION, false);
         }
      }

      ThanosTargets.fx(level, ThanosFxS2CPacket.DEBRIS_IMPACT, this.getId(), 0, at, this.getDeltaMovement(), this.size(), Block.getId(this.block()));
      ThanosWarSounds.play(level, at, ThanosWarSounds.DEBRIS_IMPACT, 1.2F + this.size() * 0.4F, 0.8F + this.random.nextFloat() * 0.3F, 48.0);
      this.discard();
   }

   /** Broken apart in the air (shot down, timed out): a burst of grit, no damage. */
   public void shatter() {
      if (this.done) {
         return;
      }

      this.done = true;
      if (this.level() instanceof ServerLevel level) {
         ThanosTargets.fx(level, ThanosFxS2CPacket.DEBRIS_IMPACT, this.getId(), 1, this.position(), Vec3.ZERO, this.size() * 0.6F, Block.getId(this.block()));
      }

      this.discard();
   }

   /** A barrier piece stops the heroes' shots that reach it (they drop, nothing is deleted). */
   private void catchShots() {
      AABB box = this.getBoundingBox().inflate(0.9 + this.size() * 0.4);
      for (Projectile p : this.level().getEntitiesOfClass(Projectile.class, box)) {
         Entity owner = p.getOwner();
         if (owner == null || BlackOrderEntity.friendly(owner)) {
            continue;
         }

         Vec3 v = p.getDeltaMovement();
         if (v.lengthSqr() < 0.01) {
            continue;
         }

         p.setDeltaMovement(v.scale(-0.2).add(0, -0.1, 0));
         p.hurtMarked = true;
         this.catches++;
         if (this.level() instanceof ServerLevel level) {
            ThanosTargets.fx(level, ThanosFxS2CPacket.TK_CATCH, this.ownerId(), 0, p.position(), v, 1.0F, 0);
            if (this.catches % 3 == 1) {
               ThanosWarSounds.play(level, p.position(), ThanosWarSounds.MAW_CATCH, 1.0F, 1.0F + this.random.nextFloat() * 0.2F, 32.0);
            }
         }
      }
   }

   // ---- entity ------------------------------------------------------------------------------------
   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide() || this.done || BlackOrderEntity.friendly(source.getEntity())) {
         return false;
      }

      // a hero knocks it out of the air
      if (source.getEntity() != null || source.getDirectEntity() != null) {
         this.shatter();
         return true;
      }

      return false;
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean isAttackable() {
      return true;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 160.0 * 160.0;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddEntityPacket(this);
   }
}
