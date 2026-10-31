package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.entity.ControlResistant;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriRules;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * Chitauri Leviathan: an armoured flying giant (~29 blocks). The head flies smooth waypoints with
 * limited turn rates and a vertical undulation; body segments and the tail follow the head's path
 * by arc length (LeviathanTrail), so the whole body takes the same wide curves between buildings.
 * It smashes what the head ploughs through (within the invasion cap), dives on clusters of targets,
 * roars (shockwave), drops troopers from its flanks, staggers under heavy hits and, killed, loses
 * control, falls, crashes into a crater and its segments come down one after another. Hitboxes:
 * the head (this entity), a snout part, ten segment parts and the tail part.
 */
public class ChitauriLeviathan extends Mob implements ChitauriMob, ControlResistant, net.minecraft.world.entity.monster.Enemy {
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ChitauriLeviathan.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> STAGGER = SynchedEntityData.defineId(ChitauriLeviathan.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> FOCUS = SynchedEntityData.defineId(ChitauriLeviathan.class, EntityDataSerializers.INT);

   public static final byte ALIVE = 0, FALLING = 1, CRASHED = 2, FADING = 3;
   public static final byte EVENT_ROAR = 80, EVENT_BITE = 81, EVENT_DEPLOY = 82;
   public static final float MAX_HIT = 120.0F;
   public static final int CORPSE_TICKS = 900;
   public static final int FADE_TICKS = 60;
   public static final double CENTRE_Y = 2.0;
   public static final int PIECES = LeviathanTrail.SEGMENTS + 1;

   enum Mode { EMERGE, CRUISE, DIVE, CLIMB }

   private final LeviathanPart[] parts;
   public final LeviathanTrail trail = new LeviathanTrail(480);
   /** Centres of the body pieces (segments, then the tail), current and previous tick. */
   public final double[][] piece = new double[PIECES][3];
   public final double[][] pieceO = new double[PIECES][3];
   private final double[] fallVy = new double[PIECES];
   private final boolean[] landed = new boolean[PIECES];
   private final double[] tmp = new double[3];

   private Vec3 heading = new Vec3(0, 0, 1);
   private double speed = 0.25;
   private Mode mode = Mode.EMERGE;
   private int modeTicks;
   @Nullable
   private Vec3 waypoint;
   @Nullable
   private Vec3 climbGoal;
   @Nullable
   private LivingEntity focus;
   private int diveCooldown = 200;
   private int roarCooldown = 160;
   private int deployCooldown = 360;
   private float recentDamage;
   private float roll;
   @Nullable
   private Vec3 lastHitAt;
   private Vec3 fallVel = Vec3.ZERO;
   private int stateTicks;
   private int smashFx;
   private int smashSound;
   private boolean piecesReady;

   // client
   public int clientRoarStart = -1000;
   public int clientBiteStart = -1000;
   public int clientDeployStart = -1000;
   public int clientStateStart;
   public float clientRoll;
   public float clientRollO;
   /** Client: the skull turns towards the focus (degrees off the heading, smoothed per tick). */
   public float clientLookYaw;
   public float clientLookYawO;
   public float clientLookPitch;
   public float clientLookPitchO;

   public ChitauriLeviathan(EntityType<? extends ChitauriLeviathan> type, Level level) {
      super(type, level);
      this.parts = new LeviathanPart[PIECES + 1];
      this.parts[0] = new LeviathanPart(this, LeviathanPart.SNOUT, 3.2F, 2.8F, 1.3F);
      for (int i = 0; i < PIECES; i++) {
         float s = LeviathanTrail.scale(i);
         boolean tail = i == LeviathanTrail.SEGMENTS;
         this.parts[i + 1] = new LeviathanPart(this, i, tail ? 2.4F : 4.2F * s, tail ? 2.0F : 3.6F * s, tail ? 0.8F : 1.0F);
      }
      this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.noCulling = true;
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 1500.0)
         .add(Attributes.ARMOR, 14.0)
         .add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FOLLOW_RANGE, 96.0)
         .add(Attributes.ATTACK_DAMAGE, 16.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, ALIVE);
      this.entityData.define(STAGGER, 0);
      this.entityData.define(FOCUS, -1);
   }

   /** Spawns a Leviathan just inside the portal, flying out along {@code dir}; its body waits in the tunnel. */
   public static ChitauriLeviathan emerge(ServerLevel level, Vec3 centre, Vec3 dir) {
      ChitauriLeviathan l = new ChitauriLeviathan(ChitauriEntities.LEVIATHAN.get(), level);
      l.setHeading(dir.normalize());
      Vec3 start = centre.subtract(l.heading.scale(3.0));
      l.setPos(start.x, start.y - CENTRE_Y, start.z);
      l.trail.prefill(start.x, start.y, start.z, l.heading.x, l.heading.y, l.heading.z, LeviathanTrail.bodyLength() + 12.0);
      l.updatePieces();
      l.placeParts();
      level.addFreshEntity(l);
      return l;
   }

   // ------------------------------------------------------------------ state

   public byte state() {
      return this.entityData.get(STATE);
   }

   private void setState(byte s) {
      this.entityData.set(STATE, s);
      this.stateTicks = 0;
   }

   public int staggerTicks() {
      return this.entityData.get(STAGGER);
   }

   public int focusId() {
      return this.entityData.get(FOCUS);
   }

   public Vec3 heading() {
      return this.heading;
   }

   public Vec3 centre() {
      return this.position().add(0, CENTRE_Y, 0);
   }

   private void setHeading(Vec3 dir) {
      this.heading = dir.normalize();
      float yaw = (float)(Mth.atan2(-this.heading.x, this.heading.z) * Mth.RAD_TO_DEG);
      float pitch = (float)(Math.asin(Mth.clamp(this.heading.y, -1.0, 1.0)) * Mth.RAD_TO_DEG);
      this.setYRot(yaw);
      this.yRotO = yaw;
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
      this.setXRot(-pitch);
   }

   public static Vec3 dirOf(float yawDeg, float pitchUpDeg) {
      float y = yawDeg * Mth.DEG_TO_RAD, p = pitchUpDeg * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(y) * Mth.cos(p), Mth.sin(p), Mth.cos(y) * Mth.cos(p));
   }

   @Override
   public boolean allowsControl(ControlKind kind) {
      return kind == ControlKind.FREEZE || kind == ControlKind.STASIS;
   }

   // ------------------------------------------------------------------ parts

   @Override
   public boolean isMultipartEntity() {
      return true;
   }

   @Override
   public PartEntity<?>[] getParts() {
      return this.parts;
   }

   @Override
   public void setId(int id) {
      super.setId(id);
      if (this.parts != null) {
         for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
         }
      }
   }

   public LeviathanPart[] parts() {
      return this.parts;
   }

   private void updatePieces() {
      Vec3 c = this.centre();
      for (int i = 0; i < PIECES; i++) {
         this.pieceO[i][0] = this.piecesReady ? this.piece[i][0] : 0;
         this.pieceO[i][1] = this.piecesReady ? this.piece[i][1] : 0;
         this.pieceO[i][2] = this.piecesReady ? this.piece[i][2] : 0;
         this.trail.sample(c.x, c.y, c.z, LeviathanTrail.offset(i), this.tmp);
         this.piece[i][0] = this.tmp[0];
         this.piece[i][1] = this.tmp[1];
         this.piece[i][2] = this.tmp[2];
         if (!this.piecesReady) {
            System.arraycopy(this.piece[i], 0, this.pieceO[i], 0, 3);
         }
      }
      this.piecesReady = true;
   }

   private void placeParts() {
      Vec3 c = this.centre();
      Vec3 snout = c.add(this.heading.scale(4.6));
      this.parts[0].placeCentre(snout.x, snout.y - 0.3, snout.z);
      for (int i = 0; i < PIECES; i++) {
         this.parts[i + 1].placeCentre(this.piece[i][0], this.piece[i][1], this.piece[i][2]);
      }
   }

   /** Crashed: each piece falls on its own, from the neck back, two ticks apart. */
   private void fallPieces() {
      int since = this.level().isClientSide ? this.tickCount - this.clientStateStart : this.stateTicks;
      if (this.state() == FADING && !this.level().isClientSide) {
         since = 9999;
      }
      for (int i = 0; i < PIECES; i++) {
         System.arraycopy(this.piece[i], 0, this.pieceO[i], 0, 3);
         if (this.landed[i] || since < i * 2) {
            continue;
         }
         this.fallVy[i] = Math.max(-1.4, this.fallVy[i] - 0.06);
         double half = (i == LeviathanTrail.SEGMENTS ? 1.0 : 1.8 * LeviathanTrail.scale(i));
         double ny = this.piece[i][1] + this.fallVy[i];
         BlockPos below = BlockPos.containing(this.piece[i][0], ny - half, this.piece[i][2]);
         boolean ground = ny - half <= this.level().getMinBuildHeight() || ChitauriTerrain.solid(this.level(), below);
         if (ground) {
            this.landed[i] = true;
            this.piece[i][1] = Math.max(this.piece[i][1], below.getY() + 1 + half - 0.4);
            if (!this.level().isClientSide && this.fallVy[i] < -0.35) {
               ServerLevel level = (ServerLevel)this.level();
               Vec3 at = new Vec3(this.piece[i][0], below.getY() + 1, this.piece[i][2]);
               ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.SEGMENT_LAND, at, Vec3.ZERO, LeviathanTrail.scale(i), i), at, 160);
               ChitauriDestruction.smash(level, at.add(0, 0.5, 0), 1.6 * LeviathanTrail.scale(i) + 0.4, 2.0F, 24, Vec3.ZERO, 1);
               if (i % 3 == 0) {
                  ChitauriSounds.playWide(level, at, ChitauriSounds.DEBRIS, 2.5F, 0.6F + this.random.nextFloat() * 0.2F, 96.0);
               }
            }
         } else {
            this.piece[i][1] = ny;
         }
      }
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      Vec3 c = this.centre();
      if (this.trail.size() == 0) {
         Vec3 d = dirOf(this.getYRot(), -this.getXRot());
         this.heading = d;
         this.trail.prefill(c.x, c.y, c.z, d.x, d.y, d.z, LeviathanTrail.bodyLength() + 12.0);
      }
      if (this.state() < CRASHED) {
         this.trail.push(c.x, c.y, c.z);
         this.updatePieces();
      } else {
         this.fallPieces();
      }
      this.placeParts();
      if (this.level().isClientSide) {
         this.heading = dirOf(this.getYRot(), -this.getXRot());
         this.clientRollO = this.clientRoll;
         int st = this.staggerTicks();
         float want = st > 0 ? Mth.sin(this.tickCount * 0.35F) * 18.0F * Math.min(1.0F, st / 12.0F) : 0.0F;
         if (this.state() == FALLING) {
            want = (this.tickCount - this.clientStateStart) * 2.5F;
         }
         this.clientRoll += (want - this.clientRoll) * 0.2F;
         this.clientLookYawO = this.clientLookYaw;
         this.clientLookPitchO = this.clientLookPitch;
         float wantYaw = 0.0F;
         float wantPitch = 0.0F;
         Entity focus = this.state() == ALIVE && this.focusId() >= 0 ? this.level().getEntity(this.focusId()) : null;
         if (focus != null) {
            Vec3 to = focus.getEyePosition().subtract(c);
            double h = Math.sqrt(to.x * to.x + to.z * to.z);
            float yawTo = (float)(Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
            float pitchTo = (float)(Mth.atan2(to.y, Math.max(1.0E-3, h)) * Mth.RAD_TO_DEG);
            wantYaw = Mth.clamp(Mth.wrapDegrees(yawTo - this.getYRot()), -38.0F, 38.0F);
            wantPitch = Mth.clamp(pitchTo + this.getXRot(), -28.0F, 28.0F);
         }
         this.clientLookYaw += (wantYaw - this.clientLookYaw) * 0.12F;
         this.clientLookPitch += (wantPitch - this.clientLookPitch) * 0.12F;
      }
   }

   @Override
   protected void customServerAiStep() {
      if (this.state() != ALIVE) {
         return;
      }
      ServerLevel level = (ServerLevel)this.level();
      this.modeTicks++;
      this.recentDamage *= 0.97F;
      if (this.diveCooldown > 0) {
         this.diveCooldown--;
      }
      if (this.roarCooldown > 0) {
         this.roarCooldown--;
      }
      if (this.deployCooldown > 0) {
         this.deployCooldown--;
      }
      int stagger = this.staggerTicks();
      if (stagger > 0) {
         this.entityData.set(STAGGER, stagger - 1);
      }
      Vec3 c = this.centre();
      Vec3 anchor = ChitauriInvasion.anchor(level);
      if (anchor == null) {
         anchor = c;
      }
      Vec3 area = ChitauriInvasion.areaCentre(level, anchor);
      double areaRadius = ChitauriInvasion.areaRadius(level);
      Vec3 goal;
      double want;
      double yawRate = 2.4;
      switch (this.mode) {
         case EMERGE -> {
            goal = c.add(this.heading.scale(30.0));
            want = 0.22 + Math.min(1.0, this.modeTicks / 60.0) * 0.3;
            if (this.modeTicks > 90) {
               this.mode = Mode.CRUISE;
               this.modeTicks = 0;
            }
         }
         case DIVE -> {
            if (this.focus != null && this.focus.isAlive()) {
               goal = this.focus.position().add(0, 2.5, 0);
            } else {
               goal = this.waypoint != null ? this.waypoint : c.add(this.heading.scale(20));
            }
            want = 0.72;
            yawRate = 3.4;
            if (c.distanceTo(goal) < 7.5) {
               this.bite(level);
               this.startClimb(c);
            } else if (this.modeTicks > 170) {
               this.startClimb(c);
            }
         }
         case CLIMB -> {
            goal = this.climbGoal != null ? this.climbGoal : c.add(this.heading.scale(20)).add(0, 12, 0);
            want = 0.58;
            if (c.distanceTo(goal) < 8.0 || this.modeTicks > 90) {
               this.mode = Mode.CRUISE;
               this.modeTicks = 0;
               this.waypoint = null;
            }
         }
         default -> {
            if (this.waypoint == null || horizontal(c, this.waypoint) < 10.0 || this.modeTicks > 420) {
               this.waypoint = this.pickWaypoint(level, c, area, areaRadius, anchor);
               this.modeTicks = 0;
            }
            goal = this.waypoint;
            want = 0.5;
            if (this.tickCount % 20 == 0) {
               this.lookForPrey(level, c);
            }
            if (this.roarCooldown <= 0) {
               this.roar(level);
            }
            if (this.deployCooldown <= 0) {
               this.deploy(level);
            }
         }
      }
      if (stagger > 0) {
         want = 0.2;
      }
      // turn towards the goal with limited rates; the vertical sine makes the body wave
      Vec3 to = goal.subtract(c);
      float yaw = this.getYRot();
      float pitch = -this.getXRot();
      float wantYaw = (float)(Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
      float wantPitch = (float)(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
      if (this.mode != Mode.DIVE) {
         wantPitch = Mth.clamp(wantPitch, -25.0F, 25.0F) + Mth.sin(this.tickCount * 0.07F) * 7.0F;
      }
      if (stagger > 0) {
         wantPitch = -14.0F;
      }
      Vec3 ahead = c.add(this.heading.scale(10.0));
      int surface = ChitauriTerrain.surface(level, ahead.x, ahead.z);
      if (surface == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, ahead.x, ahead.y, ahead.z)) {
         // never into unloaded chunks: swing back over the city
         this.waypoint = area.add(0, 16, 0);
         to = this.waypoint.subtract(c);
         wantYaw = (float)(Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
         yawRate = 4.0;
         surface = Mth.floor(c.y) - 12;
      }
      double ground = ChitauriDestruction.exhausted() ? surface : Math.min(surface, anchor.y + 14.0);
      double floor = this.mode == Mode.DIVE && this.focus != null ? this.focus.getY() + 1.0 : ground + 4.0;
      if (c.y < floor) {
         wantPitch = Math.max(wantPitch, 18.0F);
      }
      if (c.y > anchor.y + 52.0) {
         wantPitch = Math.min(wantPitch, -12.0F);
      }
      yaw += Mth.clamp(Mth.wrapDegrees(wantYaw - yaw), (float)-yawRate, (float)yawRate);
      pitch += Mth.clamp(wantPitch - pitch, -1.8F, 1.8F);
      pitch = Mth.clamp(pitch, -45.0F, 40.0F);
      this.speed += Mth.clamp(want - this.speed, -0.02, 0.02);
      this.heading = dirOf(yaw, pitch);
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
      this.setXRot(-pitch);
      Vec3 next = c.add(this.heading.scale(this.speed));
      this.setPos(next.x, next.y - CENTRE_Y, next.z);
      this.plough(level, next);
      if (this.tickCount % 10 == 0) {
         ChitauriDestruction.danger(level, next, 32.0F, 60);
      }
      if (this.tickCount % 20 == 0 && this.mode != Mode.DIVE) {
         Player p = level.getNearestPlayer(next.x, next.y, next.z, 40.0, e -> e instanceof Player pl && ChitauriMob.validTarget(pl));
         this.entityData.set(FOCUS, p != null ? p.getId() : -1);
      }
   }

   private static double horizontal(Vec3 a, Vec3 b) {
      double dx = a.x - b.x, dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   /** Breaks what the head ploughs through; dust, debris and the smash sound when it hits a building. */
   private void plough(ServerLevel level, Vec3 c) {
      if (ChitauriDestruction.exhausted()) {
         return;
      }
      Vec3 front = c.add(this.heading.scale(2.2));
      boolean any = false;
      for (int i = 0; i < 7 && !any; i++) {
         Vec3 p = switch (i) {
            case 0 -> front;
            case 1 -> front.add(2.4, 0, 0);
            case 2 -> front.add(-2.4, 0, 0);
            case 3 -> front.add(0, 2.2, 0);
            case 4 -> front.add(0, -2.2, 0);
            case 5 -> front.add(0, 0, 2.4);
            default -> front.add(0, 0, -2.4);
         };
         any = ChitauriTerrain.loaded(level, p.x, p.z) && level.hasChunkAt(BlockPos.containing(p))
            && !level.getBlockState(BlockPos.containing(p)).isAir();
      }
      if (!any) {
         return;
      }
      int n = ChitauriDestruction.smash(level, front, 3.3, 6.0F, 160, this.heading.scale(0.7), 3);
      n += ChitauriDestruction.smash(level, c.add(this.heading.scale(4.6)), 2.1, 6.0F, 60, this.heading.scale(0.9), 1);
      if (n > 6) {
         if (--this.smashFx <= 0) {
            this.smashFx = 3;
            ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.LEVIATHAN_SMASH, front, this.heading, Math.min(4.0F, n / 20.0F + 1.0F),
               this.random.nextInt()), front, 160);
         }
         if (--this.smashSound <= 0) {
            this.smashSound = 12;
            ChitauriSounds.playWide(level, front, ChitauriSounds.LEVIATHAN_SMASH, 3.0F, 0.8F + this.random.nextFloat() * 0.3F, 128.0);
         }
         ChitauriDestruction.danger(level, front, 28.0F, 120);
      }
   }

   private Vec3 pickWaypoint(ServerLevel level, Vec3 c, Vec3 area, double radius, Vec3 anchor) {
      Vec3 best = null;
      double bestScore = -1.0E9;
      for (int i = 0; i < 8; i++) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         double r = this.random.nextDouble() * radius;
         double x = area.x + Math.cos(a) * r;
         double z = area.z + Math.sin(a) * r;
         int s = ChitauriTerrain.surface(level, x, z);
         if (s == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, x, anchor.y + 16, z)) {
            continue;
         }
         double y = anchor.y + 10.0 + this.random.nextDouble() * 16.0;
         // towers pull it in: aim at their upper floors so the run tears through them
         double tower = Math.min(30.0, Math.max(0.0, s - (anchor.y + 12.0)));
         if (ChitauriDestruction.exhausted()) {
            y = Math.max(y, s + 6.0);
            tower = 0.0;
         } else if (tower > 0.0) {
            y = Math.min(y, s - 3.0);
         }
         Vec3 p = new Vec3(x, y, z);
         double dist = horizontal(c, p);
         Vec3 dir = p.subtract(c).normalize();
         double turn = Math.acos(Mth.clamp(dir.dot(this.heading), -1.0, 1.0));
         // long straight legs, no hairpins: wide paths over the city
         double score = -Math.abs(dist - 55.0) * 0.05 - Math.max(0.0, turn - 1.6) * 3.0 + tower * 0.035;
         if (score > bestScore) {
            bestScore = score;
            best = p;
         }
      }
      return best != null ? best : area.add(0, 18, 0);
   }

   private void lookForPrey(ServerLevel level, Vec3 c) {
      if (this.diveCooldown > 0 || this.staggerTicks() > 0) {
         return;
      }
      LivingEntity best = null;
      double bestScore = 0.0;
      for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(48.0), ChitauriMob::validTarget)) {
         double score = t instanceof Player ? 2.0 : 0.0;
         for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(8.0), ChitauriMob::validTarget)) {
            score += 1.0;
         }
         Vec3 to = t.position().subtract(c);
         if (to.normalize().dot(this.heading) < -0.2) {
            score *= 0.4; // behind: not worth a hairpin
         }
         if (score > bestScore) {
            bestScore = score;
            best = t;
         }
      }
      if (best != null && bestScore >= 3.0) {
         this.focus = best;
         this.entityData.set(FOCUS, best.getId());
         this.mode = Mode.DIVE;
         this.modeTicks = 0;
         this.diveCooldown = 260 + this.random.nextInt(120);
         ChitauriSounds.playWide(level, c, ChitauriSounds.LEVIATHAN_ROAR, 4.0F, 1.1F, 160.0);
         this.level().broadcastEntityEvent(this, EVENT_ROAR);
      }
   }

   private void startClimb(Vec3 c) {
      Vec3 h = new Vec3(this.heading.x, 0, this.heading.z);
      h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
      this.climbGoal = c.add(h.scale(26.0)).add(0, 14.0, 0);
      this.mode = Mode.CLIMB;
      this.modeTicks = 0;
      this.focus = null;
   }

   private void bite(ServerLevel level) {
      Vec3 snout = this.centre().add(this.heading.scale(4.6));
      this.level().broadcastEntityEvent(this, EVENT_BITE);
      ChitauriSounds.playWide(level, snout, ChitauriSounds.LEVIATHAN_ROAR, 3.0F, 1.3F, 128.0);
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(snout, snout).inflate(5.5), ChitauriMob::validTarget)) {
         if (e.position().distanceTo(snout) > 6.0) {
            continue;
         }
         e.hurt(this.damageSources().mobAttack(this), (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE));
         if (HeroRegistry.allowsImpulse(e)) {
            Vec3 push = e.position().subtract(snout).normalize().add(this.heading).normalize().scale(1.4);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.6, push.z));
            e.hurtMarked = true;
         }
      }
      ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.LEVIATHAN_ROAR, snout, this.heading, 0.7F, this.random.nextInt()), snout, 160);
      ChitauriDestruction.danger(level, snout, 32.0F, 160);
   }

   private void roar(ServerLevel level) {
      this.roarCooldown = 380 + this.random.nextInt(240);
      Vec3 snout = this.centre().add(this.heading.scale(4.6));
      this.level().broadcastEntityEvent(this, EVENT_ROAR);
      ChitauriSounds.playWide(level, snout, ChitauriSounds.LEVIATHAN_ROAR, 5.0F, 0.9F + this.random.nextFloat() * 0.15F, 220.0);
      ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.LEVIATHAN_ROAR, snout, this.heading, 1.0F, this.random.nextInt()), snout, 192);
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(snout, snout).inflate(10.0), ChitauriMob::validTarget)) {
         if (HeroRegistry.allowsImpulse(e)) {
            Vec3 push = e.position().subtract(snout).normalize().scale(0.9);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.25, push.z));
            e.hurtMarked = true;
         }
      }
      ChitauriDestruction.danger(level, snout, 40.0F, 160);
   }

   /** Troopers jump off the flanks when it flies low over the streets. */
   private void deploy(ServerLevel level) {
      this.deployCooldown = 520 + this.random.nextInt(240);
      Vec3 c = this.centre();
      int s = ChitauriTerrain.surface(level, c.x, c.z);
      if (s == ChitauriTerrain.UNKNOWN || c.y - s > 24.0) {
         this.deployCooldown = 60;
         return;
      }
      int room = ChitauriInvasion.soldierRoom(level);
      if (room < 2) {
         return;
      }
      int n = Math.min(room, 2 + this.random.nextInt(2));
      Vec3 right = new Vec3(-this.heading.z, 0, this.heading.x).normalize();
      for (int i = 0; i < n; i++) {
         double[] p = this.piece[2 + i];
         double side = i % 2 == 0 ? 1.0 : -1.0;
         Vec3 at = new Vec3(p[0], p[1] - 0.5, p[2]).add(right.scale(side * 2.4));
         if (!ChitauriTerrain.ticking(level, at.x, at.y, at.z)) {
            continue;
         }
         ChitauriSoldier soldier = ChitauriInvasion.spawnSoldier(level, at, ChitauriSoldier.ROLE_TROOPER, -1);
         if (soldier != null) {
            soldier.setDeltaMovement(right.scale(side * 0.25).add(this.heading.scale(this.speed * 0.6)));
         }
      }
      this.level().broadcastEntityEvent(this, EVENT_DEPLOY);
   }

   /**
    * Outside stagger (contextual interactions: a meteor, a heavy fist, lightning down the spine). False when the
    * body is not alive or already reeling.
    */
   public boolean staggerBy(int ticks) {
      if (!(this.level() instanceof ServerLevel level) || this.state() != ALIVE || !this.isAlive() || this.staggerTicks() > 0 || ticks <= 0) {
         return false;
      }
      this.stagger(level, ticks);
      return true;
   }

   private void stagger(ServerLevel level, int ticks) {
      if (this.staggerTicks() > 0) {
         return;
      }
      this.entityData.set(STAGGER, ticks);
      this.recentDamage = 0.0F;
      if (this.mode == Mode.DIVE) {
         this.startClimb(this.centre());
      }
      ChitauriSounds.playWide(level, this.centre(), ChitauriSounds.LEVIATHAN_HIT, 4.0F, 0.6F, 160.0);
   }

   // ------------------------------------------------------------------ damage and death

   public boolean hurtFromPart(LeviathanPart part, DamageSource source, float amount) {
      this.lastHitAt = part.getBoundingBox().getCenter();
      return this.hurt(source, amount * part.damageFactor);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity()) || this.state() != ALIVE) {
         this.lastHitAt = null;
         return false;
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL)
         || source.is(DamageTypes.DROWN)) {
         return false;
      }
      float dealt = ChitauriRules.leviathanHit(amount, MAX_HIT, source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD));
      boolean hit = super.hurt(source, dealt);
      if (hit && this.level() instanceof ServerLevel level) {
         Vec3 at = this.lastHitAt != null ? this.lastHitAt : source.getSourcePosition() != null
            ? this.getBoundingBox().getCenter().add(source.getSourcePosition().subtract(this.getBoundingBox().getCenter()).normalize().scale(2.0))
            : this.centre();
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.LEVIATHAN_HIT, at, Vec3.ZERO, dealt, this.getId()), at, 128);
         this.recentDamage += dealt;
         if (this.isAlive() && (dealt >= 30.0F || this.recentDamage >= 90.0F)) {
            this.stagger(level, 46);
         }
      }
      this.lastHitAt = null;
      return hit;
   }

   @Override
   public void die(DamageSource source) {
      boolean first = this.state() == ALIVE;
      super.die(source);
      if (first && this.level() instanceof ServerLevel level) {
         this.setState(FALLING);
         this.fallVel = this.heading.scale(Math.max(0.4, this.speed) * 1.1);
         ChitauriSounds.playWide(level, this.centre(), ChitauriSounds.LEVIATHAN_DEATH, 6.0F, 1.0F, 256.0);
         ChitauriInvasion.onKilled(this, source);
      }
   }

   /** Severed from the hive (invasion closing): the same fall as a kill. */
   public void sever() {
      if (this.state() == ALIVE && this.isAlive()) {
         this.setHealth(0.0F);
         this.die(this.damageSources().generic());
      }
   }

   @Override
   protected void tickDeath() {
      if (!(this.level() instanceof ServerLevel level)) {
         return;
      }
      this.stateTicks++;
      switch (this.state()) {
         case FALLING -> {
            Vec3 v = this.fallVel;
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            double keep = Math.max(0.32, h * 0.985);
            Vec3 hd = h > 1.0E-4 ? new Vec3(v.x / h, 0, v.z / h) : new Vec3(0, 0, 1);
            this.fallVel = new Vec3(hd.x * keep, Math.max(-1.25, v.y - 0.035), hd.z * keep);
            this.setHeading(this.fallVel);
            Vec3 c = this.centre();
            Vec3 next = c.add(this.fallVel);
            this.plough(level, next);
            int surface = ChitauriTerrain.surface(level, next.x, next.z);
            Vec3 probe = next.add(this.fallVel.normalize().scale(2.6));
            boolean impact = this.stateTicks > 240 || next.y < level.getMinBuildHeight() + 2
               || surface != ChitauriTerrain.UNKNOWN && next.y - 1.5 <= surface && ChitauriTerrain.solid(level, BlockPos.containing(probe))
               || ChitauriTerrain.solid(level, BlockPos.containing(probe.x, probe.y - 1.8, probe.z)) && ChitauriDestruction.exhausted();
            if (surface != ChitauriTerrain.UNKNOWN && next.y - 2.0 <= surface) {
               impact = true;
            }
            if (impact) {
               this.crash(level, next);
            } else {
               this.setPos(next.x, next.y - CENTRE_Y, next.z);
               if (this.stateTicks % 10 == 0) {
                  ChitauriDestruction.danger(level, next, 40.0F, 80);
               }
            }
         }
         case CRASHED -> {
            if (this.stateTicks >= CORPSE_TICKS) {
               this.setState(FADING);
            }
         }
         case FADING -> {
            if (this.stateTicks >= FADE_TICKS) {
               this.remove(Entity.RemovalReason.KILLED);
            }
         }
         default -> this.setState(FALLING);
      }
   }

   private void crash(ServerLevel level, Vec3 at) {
      this.setPos(at.x, at.y - CENTRE_Y, at.z);
      this.setState(CRASHED);
      for (int i = 0; i < PIECES; i++) {
         this.fallVy[i] = 0.0;
         this.landed[i] = false;
      }
      ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.LEVIATHAN_CRASH, at, this.fallVel, 1.0F, this.random.nextInt()), at, 256);
      ChitauriSounds.playWide(level, at, ChitauriSounds.LEVIATHAN_CRASH, 6.0F, 0.9F, 256.0);
      ChitauriDestruction.smashLater(level, at, 6.5, 6.0F, this.fallVel.scale(0.6));
      ChitauriDestruction.blastEntities(level, at, 10.0, 24.0F, this, this);
      ChitauriDestruction.danger(level, at, 48.0F, 300);
   }

   // ------------------------------------------------------------------ misc

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (STATE.equals(key) && this.level().isClientSide) {
         this.clientStateStart = this.tickCount;
         if (this.state() == CRASHED) {
            for (int i = 0; i < PIECES; i++) {
               this.fallVy[i] = 0.0;
               this.landed[i] = false;
            }
         }
      }
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_ROAR -> this.clientRoarStart = this.tickCount;
         case EVENT_BITE -> this.clientBiteStart = this.tickCount;
         case EVENT_DEPLOY -> this.clientDeployStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   public float healthFraction() {
      return Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F);
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return ChitauriMob.is(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean isInWall() {
      return false;
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void pushEntities() {
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean isPickable() {
      return this.isAlive();
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 320.0 * 320.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(34.0);
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
      return ChitauriSounds.LEVIATHAN_HIT.get();
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected float getSoundVolume() {
      return 3.0F;
   }

   @Override
   public int getAmbientSoundInterval() {
      return 400;
   }
}
