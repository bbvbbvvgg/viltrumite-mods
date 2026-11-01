package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.MicroMissileEntity;
import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
import dev.baranhan.viltrumitecore.entity.RocketFistEntity;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManLegionSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.MicroLaser;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.PulseUnibeam;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.ShoulderGun;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureRules;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureTrace;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The server brain of one Legion suit (design §5–§6): its life phases, the
 * steering toward the goal the coordinator's assignment implies, obstacle
 * feelers, dodges and the mark's weapons. The goal and the fire decisions
 * are refreshed every {@link LegionRules#BRAIN_PERIOD} ticks (staggered by
 * slot); movement and weapon channels run every tick, so the flight stays
 * smooth.
 */
public final class LegionSuitBrain {
   private static final int CHANNEL_NONE = 0;
   private static final int CHANNEL_LASER = 1;
   private static final int CHANNEL_PULSE = 2;
   private static final int CHANNEL_GUN = 3;
   private static final int CHANNEL_BOOST = 4;
   private static final int CHANNEL_SLAM = 5;
   private static final int LASER_TICKS = 24;
   private static final int GUN_TICKS = 30;
   private static final int BOOST_TICKS = 8;
   private static final int SLAM_RISE = 8;
   private static final int SLAM_MAX = 32;
   private static final double SLAM_RADIUS = 5.0;
   private static final float SLAM_DAMAGE = 6.0F;
   private static final double TURN_RATE = 14.0;

   private final LegionSuitEntity suit;
   private final MarkId mark;
   private final int slot;
   private final LegionProfile profile;
   private int phaseTicks;
   @Nullable
   private LegionSteering.Arrival arrival;
   private Vec3 arrivalSlot = Vec3.ZERO;
   private Vec3 velocity = Vec3.ZERO;
   private Vec3 goal;
   private float desiredYaw;
   private float desiredPitch;
   private double maxSpeed;
   private Vec3 avoid = Vec3.ZERO;
   private Vec3 impulse = Vec3.ZERO;
   @Nullable
   private LegionTactics.Assignment assignment;
   @Nullable
   private LivingEntity target;
   private int side;
   private int repulsorTimer;
   private int missileTimer;
   private int weaponTimer;
   private int dodgeCooldown;
   private int lastDodged = -1;
   private int teleportCooldown;
   private int farTicks;
   private int stuckWindow;
   private double stuckStart = -1.0;
   private int unstick;
   private boolean rightHand = true;
   private int channel = CHANNEL_NONE;
   private int channelTicks;
   private Vec3 channelPoint = Vec3.ZERO;
   private int camoTicks;
   private int fistId = -1;
   private boolean flybyDone;
   private boolean criticalSaid;

   public LegionSuitBrain(LegionSuitEntity suit, MarkId mark, int slot) {
      this.suit = suit;
      this.mark = mark;
      this.slot = slot;
      this.profile = LegionProfile.of(mark);
      this.side = slot % 2 == 0 ? 1 : -1;
      this.goal = suit.position();
      this.maxSpeed = this.profile.cruise();
      // Weapons do not all fire on the first frame of a fight.
      this.repulsorTimer = 6 + slot * 3;
      this.missileTimer = 40 + slot * 10;
      this.weaponTimer = 50 + slot * 15;
   }

   /** Server: the arrival curve from the call (start in the sky, end at the formation slot). */
   public void arrive(Vec3 slotPos, float yaw) {
      this.arrivalSlot = slotPos;
      this.arrival = LegionSteering.arrival(this.slot, slotPos, yaw);
      this.velocity = Vec3.ZERO;
   }

   /** Where the body is spawned: the curve start, kept under the build limit. */
   public Vec3 startPoint(ServerLevel level) {
      if (this.arrival == null) {
         return this.suit.position();
      }

      double top = level.getMaxBuildHeight() - 4.0;
      LegionSteering.Arrival a = this.arrival;
      if (a.p0().y > top) {
         this.arrival = new LegionSteering.Arrival(new Vec3(a.p0().x, top, a.p0().z), new Vec3(a.p1().x, Math.min(a.p1().y, top), a.p1().z), a.p2(), a.p3());
      }

      return this.arrival.p0();
   }

   /** The coordinator's word for this round. */
   void assign(LegionTactics.Assignment assignment, @Nullable LivingEntity target) {
      if (target != this.target && target != null && this.target != null) {
         this.side = -this.side;
      }

      this.assignment = assignment;
      this.target = target;
   }

   @Nullable
   public LivingEntity target() {
      return this.target;
   }

   @Nullable
   public LegionTactics.Assignment assignment() {
      return this.assignment;
   }

   // ------------------------------------------------------------------ tick

   public void tick(ServerPlayer owner) {
      LegionSquad squad = IronManLegion.squad(owner);
      this.phaseTicks++;
      this.cooldowns();
      switch (this.suit.phase()) {
         case APPROACH -> this.approach(owner, squad);
         case BRAKE -> this.brake(owner, squad);
         case READY -> this.ready(owner, squad);
         case ACTIVE -> this.active(owner, squad);
         case LEAVE -> this.leaveTick(owner);
         case CRASH -> this.crashTick(owner);
      }
   }

   private void cooldowns() {
      if (this.dodgeCooldown > 0) {
         this.dodgeCooldown--;
      }

      if (this.teleportCooldown > 0) {
         this.teleportCooldown--;
      }

      if (this.repulsorTimer > 0) {
         this.repulsorTimer--;
      }

      if (this.missileTimer > 0) {
         this.missileTimer--;
      }

      if (this.weaponTimer > 0) {
         this.weaponTimer--;
      }

      if (this.camoTicks > 0 && --this.camoTicks == 0) {
         this.camo(false);
      }

      if (this.fistId >= 0 && !(this.suit.level().getEntity(this.fistId) instanceof RocketFistEntity fist && fist.isAlive())) {
         this.fistId = -1;
         this.suit.setFlag(LegionSuitEntity.FLAG_FIST_AWAY, false);
      }
   }

   private void enter(LegionPhase phase) {
      this.suit.setPhase(phase);
      this.phaseTicks = 0;
   }

   // --------------------------------------------------------------- phases

   /** Sky signal (hover high up, glints), then the own Bezier curve down to the slot. */
   private void approach(ServerPlayer owner, @Nullable LegionSquad squad) {
      if (this.arrival == null) {
         this.arrive(this.suit.position(), owner.getYRot());
      }

      this.suit.noPhysics = true;
      Vec3 slotNow = this.formationGoal(owner, squad);
      if (this.phaseTicks <= LegionRules.SIGNAL_TICKS) {
         this.suit.setThrottle(0.55F);
         Vec3 hover = this.arrival.p0().add(0.0, Math.sin(this.phaseTicks * 0.2 + this.slot) * 0.05, 0.0);
         this.steerTo(hover.subtract(this.suit.position()));
         this.face(this.arrival.p1().subtract(this.arrival.p0()), 6.0F);
         return;
      }

      int length = LegionRules.arrivalTicks(this.slot);
      double u = (this.phaseTicks - LegionRules.SIGNAL_TICKS) / (double)length;
      double e = LegionSteering.arrivalEase(u);
      Vec3 point = this.arrival.at(e).add(slotNow.subtract(this.arrivalSlot).scale(e));
      Vec3 step = point.subtract(this.suit.position());
      this.steerTo(step);
      this.suit.setThrottle(1.0F);
      this.face(step, 18.0F);
      if (!this.flybyDone && step.length() > 0.7 && this.suit.distanceTo(owner) < 12.0) {
         this.flybyDone = true;
         this.suit.sound(IronManLegionSounds.FLYBY.get(), 1.2F, 0.9F + this.slot * 0.08F);
      }

      if (u >= 1.0) {
         this.enter(LegionPhase.BRAKE);
         this.suit.sound(IronManLegionSounds.BRAKE.get(), 1.0F, 0.95F + this.slot * 0.05F);
      }
   }

   /** Feet first over the slot, full thrust, then settle. */
   private void brake(ServerPlayer owner, @Nullable LegionSquad squad) {
      this.goal = this.formationGoal(owner, squad);
      this.suit.noPhysics = this.insideBlock();
      this.velocity = LegionSteering.arrive(this.suit.position(), this.velocity, this.goal, this.profile.cruise(), 0.14, 4.0);
      this.suit.fly(this.velocity);
      this.suit.setThrottle(1.0F - this.phaseTicks / (float)LegionRules.BRAKE_TICKS * 0.5F);
      this.face(this.formationFacing(owner, squad), 12.0F);
      this.pitchTo(0.0F);
      if (this.phaseTicks >= LegionRules.BRAKE_TICKS) {
         this.enter(LegionPhase.READY);
         this.suit.playMove(LegionMove.READY);
         this.suit.sound(IronManLegionSounds.SERVO.get(), 0.9F, 0.9F + this.slot * 0.06F);
         if (this.suit.onGround()) {
            this.suit.sound(IronManLegionSounds.LAND.get(), 0.8F, 1.0F);
         }
      }
   }

   /** The per-mark ready move, hovering on the slot. */
   private void ready(ServerPlayer owner, @Nullable LegionSquad squad) {
      this.goal = this.formationGoal(owner, squad);
      this.suit.noPhysics = this.insideBlock();
      this.velocity = LegionSteering.arrive(this.suit.position(), this.velocity, this.goal, 0.25, 0.04, 3.0);
      this.suit.fly(this.velocity);
      this.suit.setThrottle(0.4F);
      this.face(this.formationFacing(owner, squad), 8.0F);
      this.pitchTo(0.0F);
      if (this.phaseTicks >= LegionRules.READY_TICKS) {
         this.enter(LegionPhase.ACTIVE);
         if (squad != null) {
            squad.dirty = true;
            IronManLegion.onSuitReady(owner, squad);
         }
      }
   }

   private void active(ServerPlayer owner, @Nullable LegionSquad squad) {
      if (squad == null) {
         return;
      }

      if (squad.order == LegionOrder.RETURN) {
         this.leave(false);
         return;
      }

      LegionTactics.Stance stance = this.assignment == null ? LegionTactics.Stance.GUARD : this.assignment.stance();
      if (stance == LegionTactics.Stance.RETURN || LegionTactics.health(this.suit.fraction()) == LegionTactics.Stance.RETURN) {
         this.criticalLine(squad);
         this.leave(true);
         return;
      }

      if (this.target != null && (!this.target.isAlive() || this.target.isRemoved() || this.target.level() != this.suit.level()
         || !LegionAllies.mayHit(this.suit, this.target))) {
         this.target = null;
      }

      if (LegionRules.brainDue(this.suit.tickCount, this.slot)) {
         this.think(owner, squad, stance);
      }

      this.channelTick(owner);
      this.move(owner);
   }

   /** Engines spool up, the suit climbs away toward Veronica and is gone. */
   public void leave(boolean damaged) {
      if (this.suit.phase() == LegionPhase.LEAVE || this.suit.phase() == LegionPhase.CRASH) {
         return;
      }

      if (this.suit.override() != null) {
         // a cleanup (death, hero change, dimension) takes the suit back from whoever controls it
         this.suit.setOverride(null);
      }

      this.stopChannel();
      this.camo(false);
      this.suit.setAim(-1);
      this.enter(LegionPhase.LEAVE);
      this.suit.playMove(LegionMove.TAKEOFF);
      this.suit.sound(IronManLegionSounds.IGNITE.get(), 1.0F, damaged ? 0.8F : 1.0F);
      ServerPlayer owner = this.suit.owner();
      LegionSquad squad = owner == null ? null : IronManLegion.squad(owner);
      if (squad != null) {
         squad.dirty = true;
      }
   }

   private void leaveTick(@Nullable ServerPlayer owner) {
      Vec3 away = owner == null ? LegionSteering.forward(this.suit.getYRot()) : this.suit.position().subtract(owner.position());
      away = new Vec3(away.x, 0.0, away.z);
      away = away.lengthSqr() < 1.0E-4 ? LegionSteering.forward(this.suit.getYRot()) : away.normalize();
      double lift = Math.min(1.6, 0.1 + this.phaseTicks * 0.05);
      Vec3 desired = away.scale(lift * 0.6).add(0.0, lift, 0.0);
      this.velocity = this.velocity.add(LegionSteering.limit(desired.subtract(this.velocity), 0.12));
      this.suit.noPhysics = this.phaseTicks > 8 || this.insideBlock();
      this.suit.fly(this.velocity);
      this.suit.setThrottle(1.0F);
      this.face(away, 10.0F);
      this.pitchTo(-35.0F);
      if (this.phaseTicks >= LegionRules.LEAVE_TICKS) {
         this.gone(owner, false);
      }
   }

   /** Durability ran out: Veronica takes the mark back broken, the body falls and breaks apart. */
   public void crash() {
      if (this.suit.phase() == LegionPhase.CRASH) {
         return;
      }

      this.stopChannel();
      this.camo(false);
      this.suit.setAim(-1);
      this.suit.setFlag(LegionSuitEntity.FLAG_CRITICAL, true);
      this.enter(LegionPhase.CRASH);
      this.suit.sound(IronManLegionSounds.FAILURE.get(), 1.2F, 1.0F);
      ServerPlayer owner = this.suit.owner();
      LegionSquad squad = owner == null ? null : IronManLegion.squad(owner);
      if (squad != null) {
         this.criticalLine(squad);
         squad.dirty = true;
      }
   }

   private void crashTick(@Nullable ServerPlayer owner) {
      this.suit.noPhysics = false;
      this.velocity = new Vec3(this.velocity.x * 0.96, Math.max(-1.6, this.velocity.y - 0.06), this.velocity.z * 0.96);
      // Sputtering engines: short bursts that fail.
      if (this.phaseTicks % 9 < 3) {
         this.velocity = this.velocity.add(0.0, 0.05, 0.0);
         this.suit.setThrottle(0.5F);
      } else {
         this.suit.setThrottle(0.08F);
      }

      this.suit.fly(this.velocity);
      this.suit.setYRot(this.suit.getYRot() + 9.0F);
      this.suit.yBodyRot = this.suit.getYRot();
      this.suit.yHeadRot = this.suit.getYRot();
      boolean down = this.suit.onGround() || this.suit.verticalCollision || this.suit.horizontalCollision && this.phaseTicks > 10;
      if (down && this.phaseTicks > 4 || this.phaseTicks >= LegionRules.CRASH_TICKS) {
         this.impact();
         this.gone(owner, true);
      }
   }

   private void impact() {
      ServerLevel level = (ServerLevel)this.suit.level();
      IronManMarks.debris(this.suit, this.mark, -1, 4);
      this.suit.sound(IronManLegionSounds.CRASH.get(), 1.4F, 1.0F);
      BlockState ground = level.getBlockState(this.suit.getOnPos());
      HeroFx.shockwave(this.suit, this.suit.position(), 0.6F, ground.isAir() ? null : ground, 2.5F);
      HeroFx.flash(this.suit, this.suit.position().add(0.0, 1.0, 0.0), 0.8F);
   }

   /** The body leaves the world; the roster entry goes back to storage (a crash broke it already). */
   private void gone(@Nullable ServerPlayer owner, boolean crashed) {
      IronManState state = owner == null ? null : IronManState.of(owner);
      if (state != null && state.roster.location(this.mark) == MarkLocation.LEGION) {
         state.roster.move(this.mark, MarkLocation.LEGION, MarkLocation.STORED);
      }

      this.suit.markReleased();
      if (owner != null) {
         IronManLegion.forget(owner, this.suit);
      }

      this.suit.discard();
   }

   private void criticalLine(LegionSquad squad) {
      if (!this.criticalSaid) {
         this.criticalSaid = true;
         squad.event(LegionSquad.EVENT_CRITICAL);
      }
   }

   // ------------------------------------------------------------ hits

   /** Called by the entity after the roster took the damage. */
   public void onHurt(ServerPlayer owner, @Nullable LivingEntity attacker, float amount, boolean broke) {
      LegionSquad squad = IronManLegion.squad(owner);
      if (squad != null) {
         squad.noteSuitHit(attacker, owner.level().getGameTime());
         squad.dirty = true;
      }

      if (this.camoTicks > 0) {
         this.camo(false);
      }

      if (amount >= 2.0F) {
         this.suit.sound(IronManLegionSounds.HIT.get(), 0.9F, 0.85F + this.suit.getRandom().nextFloat() * 0.3F);
      }

      if (broke) {
         IronManState state = IronManState.of(owner);
         if (state != null) {
            // Broken at once in the roster: a relog mid-fall cannot keep a 0-durability mark without its cooldown.
            state.roster.breakMark(this.mark);
         }

         this.crash();
      } else if (this.suit.phase() == LegionPhase.ACTIVE && squad != null && LegionTactics.health(this.suit.fraction()) == LegionTactics.Stance.RETURN) {
         this.criticalLine(squad);
      }
   }

   // ------------------------------------------------------------ thinking

   private void think(ServerPlayer owner, LegionSquad squad, LegionTactics.Stance stance) {
      LivingEntity target = this.target;
      if (target != null && stance != LegionTactics.Stance.GUARD) {
         double time = this.suit.tickCount;
         LegionRole role = this.assignment == null ? this.profile.role() : this.assignment.role();
         Vec3 spot = stance == LegionTactics.Stance.RETREAT
            ? LegionSteering.retreatSpot(target.position(), owner.position(), this.profile.range(), this.profile.altitude())
            : LegionSteering.combatSpot(role, target.position(), owner.position(), this.suit.position(), this.profile.range(), this.profile.altitude(), this.side, time);
         if (role == LegionRole.GUARDIAN || this.profile.role() == LegionRole.GUARDIAN) {
            spot = leash(spot, owner.position(), this.profile.leash());
         }

         if (squad.order == LegionOrder.HOLD && squad.holdPoint != null) {
            spot = leash(spot, squad.holdPoint, LegionRules.HOLD_RADIUS + 6.0);
         }

         this.goal = this.freeSpot(spot);
         Vec3 aim = target.getBoundingBox().getCenter().subtract(this.suit.getEyePosition());
         this.face(aim, 0.0F);
         this.desiredPitch = LegionSteering.pitchOf(aim);
         this.suit.setAim(target.getId());
         this.fire(owner, squad, target, stance);
      } else {
         this.goal = this.formationGoal(owner, squad);
         this.desiredYaw = LegionSteering.yawOf(this.formationFacing(owner, squad), this.suit.getYRot());
         this.desiredPitch = 0.0F;
         this.suit.setAim(-1);
         if (this.channel != CHANNEL_NONE && this.channel != CHANNEL_SLAM) {
            this.stopChannel();
         }
      }

      double toGoal = this.goal.distanceTo(this.suit.position());
      this.maxSpeed = this.profile.cruise() * (toGoal > 20.0 ? 1.55 : toGoal > 10.0 ? 1.2 : 1.0);
      this.feelers();
      this.separation(squad);
      this.dodge(owner);
      this.farFallback(owner, squad);
      this.stuck(toGoal);
      if (this.camoTicks > 0 && this.suit.tickCount % SignatureRules.CAMO_MOB_CHECK == 0) {
         this.camoShake();
      }
   }

   private static Vec3 leash(Vec3 spot, Vec3 center, double radius) {
      Vec3 off = spot.subtract(center);
      Vec3 flat = new Vec3(off.x, 0.0, off.z);
      double d = flat.length();
      return d <= radius || d < 1.0E-4 ? spot : center.add(flat.scale(radius / d)).add(0.0, off.y, 0.0);
   }

   /** Formation slot of this suit for the current order (Tony or the held point as anchor), in free air. */
   private Vec3 formationGoal(ServerPlayer owner, @Nullable LegionSquad squad) {
      LegionOrder order = squad == null ? LegionOrder.GUARD : squad.order;
      if (order == LegionOrder.ATTACK || order == LegionOrder.RETURN) {
         order = LegionOrder.GUARD;
      }

      Vec3 anchor = order == LegionOrder.HOLD && squad.holdPoint != null ? squad.holdPoint : owner.position();
      int index = squad == null ? this.slot : Math.max(0, squad.formationIndex(this.suit));
      int count = squad == null ? 1 : Math.max(1, squad.fighting().size());
      return this.freeSpot(LegionSteering.slot(order, index, count, anchor, owner.getYRot(), this.suit.level().getGameTime()));
   }

   /** Where the suit looks while it has no target: Tony's way (FOLLOW), outward (GUARD ring, HOLD). */
   private Vec3 formationFacing(ServerPlayer owner, @Nullable LegionSquad squad) {
      LegionOrder order = squad == null ? LegionOrder.GUARD : squad.order;
      if (order == LegionOrder.FOLLOW) {
         return LegionSteering.forward(owner.getYRot());
      }

      Vec3 center = order == LegionOrder.HOLD && squad.holdPoint != null ? squad.holdPoint : owner.position();
      Vec3 out = this.suit.position().subtract(center);
      out = new Vec3(out.x, 0.0, out.z);
      if (this.velocity.horizontalDistanceSqr() > 0.09) {
         return this.velocity;
      }

      return out.lengthSqr() < 1.0E-3 ? LegionSteering.forward(owner.getYRot()) : out;
   }

   /** Lift a goal out of solid blocks (slots near walls, low ceilings). */
   private Vec3 freeSpot(Vec3 spot) {
      ServerLevel level = (ServerLevel)this.suit.level();
      Vec3 p = spot;
      for (int i = 0; i < 5; i++) {
         if (free(level, BlockPos.containing(p)) && free(level, BlockPos.containing(p.add(0.0, 1.0, 0.0)))) {
            return p;
         }

         p = p.add(0.0, 1.0, 0.0);
      }

      return spot;
   }

   private static boolean free(ServerLevel level, BlockPos pos) {
      return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   private boolean insideBlock() {
      ServerLevel level = (ServerLevel)this.suit.level();
      return !level.noCollision(this.suit, this.suit.getBoundingBox().deflate(0.05));
   }

   // ----------------------------------------------------------- movement

   private void move(ServerPlayer owner) {
      if (this.channel == CHANNEL_PULSE || this.channel == CHANNEL_GUN && this.mark == MarkId.WAR_MACHINE_MK2) {
         // Heavy channels: hold still and let the weapon work.
         this.goal = this.suit.position().add(this.velocity.scale(0.2));
      }

      Vec3 next;
      if (this.channel == CHANNEL_BOOST) {
         Vec3 dir = this.channelPoint.subtract(this.suit.position());
         next = dir.lengthSqr() < 1.0E-3 ? this.velocity : dir.normalize().scale(2.0);
      } else if (this.channel == CHANNEL_SLAM) {
         next = this.slamVelocity();
      } else {
         next = LegionSteering.arrive(this.suit.position(), this.velocity, this.goal, this.maxSpeed, this.profile.accel(), 5.0);
         next = next.add(this.avoid.scale(this.profile.accel() * 2.2));
      }

      next = next.add(this.impulse);
      this.impulse = this.impulse.scale(0.7);
      if (this.impulse.lengthSqr() < 1.0E-4) {
         this.impulse = Vec3.ZERO;
      }

      double cap = this.channel == CHANNEL_BOOST || this.channel == CHANNEL_SLAM ? 2.6 : this.maxSpeed * 1.35 + LegionRules.DODGE_SPEED;
      this.velocity = LegionSteering.limit(next, cap);
      this.suit.noPhysics = this.unstick > 0;
      if (this.unstick > 0) {
         this.unstick--;
      }

      Vec3 before = this.suit.position();
      this.suit.fly(this.velocity);
      Vec3 moved = this.suit.position().subtract(before);
      if (this.suit.horizontalCollision || this.suit.verticalCollision) {
         // Keep what really happened, so the next step does not pile speed into a wall.
         this.velocity = moved;
      }

      float speed = (float)this.velocity.length();
      this.suit.setThrottle(0.3F + 0.7F * Math.min(1.0F, speed / (float)Math.max(0.2, this.profile.cruise())) + (this.velocity.y > 0.15 ? 0.15F : 0.0F));
      float yaw = this.target != null || this.velocity.horizontalDistanceSqr() < 0.04 ? this.desiredYaw
         : LegionSteering.yawOf(this.velocity, this.desiredYaw);
      this.turnTo(yaw, (float)TURN_RATE);
      this.pitchTo(this.desiredPitch);
   }

   private void steerTo(Vec3 step) {
      this.velocity = step;
      this.suit.fly(step);
   }

   private void face(Vec3 dir, float rate) {
      float yaw = LegionSteering.yawOf(dir, this.suit.getYRot());
      this.desiredYaw = yaw;
      if (rate > 0.0F) {
         this.turnTo(yaw, rate);
         this.pitchTo(LegionSteering.pitchOf(dir) * 0.6F);
      }
   }

   private void turnTo(float yaw, float rate) {
      float next = LegionSteering.turn(this.suit.getYRot(), yaw, rate);
      this.suit.setYRot(next);
      this.suit.yBodyRot = next;
      this.suit.yHeadRot = next;
   }

   private void pitchTo(float pitch) {
      float current = this.suit.getXRot();
      this.suit.setXRot(current + Mth.clamp(pitch - current, -8.0F, 8.0F));
   }

   /** Feeler rays from the chest: front, both diagonals and up (blocks only). */
   private void feelers() {
      Vec3 dir = this.velocity.horizontalDistanceSqr() > 1.0E-3 ? this.velocity : LegionSteering.forward(this.suit.getYRot());
      Vec3 fwd = new Vec3(dir.x, 0.0, dir.z).normalize();
      double look = 3.0 + this.velocity.length() * 10.0;
      Vec3 from = this.suit.position().add(0.0, 0.9, 0.0);
      Vec3 right = LegionSteering.right(fwd);
      double front = this.ray(from, fwd, look);
      double left = this.ray(from, fwd.add(right.scale(-0.8)).normalize(), look * 0.5);
      double rightClear = this.ray(from, fwd.add(right.scale(0.8)).normalize(), look * 0.5);
      double up = this.ray(from, new Vec3(0.0, 1.0, 0.0), 3.0);
      this.avoid = LegionSteering.avoid(fwd, front, left, rightClear, up, look);
   }

   private double ray(Vec3 from, Vec3 dir, double length) {
      BlockHitResult hit = this.suit.level().clip(new ClipContext(from, from.add(dir.scale(length)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.suit));
      return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(from) : length;
   }

   private void separation(LegionSquad squad) {
      List<LegionSuitEntity> others = squad.fighting();
      List<Vec3> points = new java.util.ArrayList<>(others.size());
      for (LegionSuitEntity other : others) {
         if (other != this.suit) {
            points.add(other.position());
         }
      }

      ServerPlayer owner = this.suit.owner();
      if (owner != null) {
         points.add(owner.position());
      }

      Vec3 push = LegionSteering.separation(this.suit.position(), points, 2.2);
      this.impulse = this.impulse.add(push.scale(0.06));
   }

   /** Side-step a projectile that would pass through the suit (chance by mark). */
   private void dodge(ServerPlayer owner) {
      if (this.dodgeCooldown > 0 || this.channel == CHANNEL_SLAM) {
         return;
      }

      Vec3 center = this.suit.getBoundingBox().getCenter();
      AABB box = this.suit.getBoundingBox().inflate(14.0);
      for (Projectile projectile : this.suit.level().getEntitiesOfClass(Projectile.class, box)) {
         Entity shooter = projectile.getOwner();
         if (shooter == owner || shooter instanceof LegionSuitEntity || projectile.getId() == this.lastDodged) {
            continue;
         }

         Vec3 v = projectile.getDeltaMovement();
         Vec3 rel = center.subtract(projectile.position());
         if (v.lengthSqr() < 0.04 || rel.dot(v) <= 0.0) {
            continue;
         }

         if (LegionSteering.closestApproach(center, projectile.position(), v, LegionRules.DODGE_HORIZON) > LegionRules.DODGE_RADIUS) {
            continue;
         }

         this.lastDodged = projectile.getId();
         if (this.suit.getRandom().nextFloat() >= this.profile.dodge()) {
            return;
         }

         boolean left = this.suit.getRandom().nextBoolean();
         this.impulse = this.impulse.add(LegionSteering.dodge(v, left).scale(LegionRules.DODGE_SPEED));
         this.suit.playMove(left ? LegionMove.DODGE_LEFT : LegionMove.DODGE_RIGHT);
         this.dodgeCooldown = LegionRules.DODGE_COOLDOWN;
         return;
      }
   }

   /**
    * Tony is far away (elytra, pearls): a short re-entry behind him instead of
    * an endless chase. Under HOLD the held point is the anchor, not Tony: the
    * suits never leave the point because Tony walked off (Codex review #3).
    */
   private void farFallback(ServerPlayer owner, @Nullable LegionSquad squad) {
      Vec3 hold = squad != null && squad.order == LegionOrder.HOLD ? squad.holdPoint : null;
      Vec3 anchor = fallbackAnchor(squad == null ? LegionOrder.GUARD : squad.order, hold, owner.position());
      double d = this.suit.position().distanceTo(anchor);
      this.farTicks = d > LegionRules.TELEPORT_DISTANCE ? this.farTicks + LegionRules.BRAIN_PERIOD : 0;
      if (this.farTicks < 40 || this.teleportCooldown > 0) {
         return;
      }

      Vec3 at;
      if (hold != null) {
         // Only into loaded ground: an unloaded held point waits until its chunk ticks again.
         if (!this.suit.level().hasChunkAt(BlockPos.containing(hold))) {
            this.farTicks = 0;
            return;
         }

         at = this.freeSpot(hold.add(this.side * 4.0, 6.0, 0.0));
         this.velocity = Vec3.ZERO;
      } else {
         Vec3 back = LegionSteering.forward(owner.getYRot()).scale(-16.0).add(0.0, 12.0, 0.0);
         at = this.freeSpot(owner.position().add(back).add(this.side * 4.0, 0.0, 0.0));
         this.velocity = owner.position().subtract(at).normalize().scale(this.profile.cruise());
      }

      this.suit.moveTo(at.x, at.y, at.z, this.suit.getYRot(), this.suit.getXRot());
      this.teleportCooldown = LegionRules.TELEPORT_COOLDOWN;
      this.farTicks = 0;
      this.stuckStart = -1.0;
   }

   /** What the distance fallback measures from: the held point under HOLD, else Tony. */
   static Vec3 fallbackAnchor(LegionOrder order, @Nullable Vec3 holdPoint, Vec3 ownerPos) {
      return order == LegionOrder.HOLD && holdPoint != null ? holdPoint : ownerPos;
   }

   /** No progress for a while: climb, pass through for a moment and swap side. */
   private void stuck(double toGoal) {
      this.stuckWindow += LegionRules.BRAIN_PERIOD;
      if (this.stuckStart < 0.0) {
         this.stuckStart = toGoal;
      }

      if (this.stuckWindow < LegionRules.STUCK_TICKS) {
         return;
      }

      if (toGoal > 4.0 && this.stuckStart - toGoal < LegionRules.STUCK_PROGRESS) {
         this.unstick = 20;
         this.impulse = this.impulse.add(0.0, 0.6, 0.0);
         this.side = -this.side;
      }

      this.stuckWindow = 0;
      this.stuckStart = toGoal;
   }

   // ------------------------------------------------------------ weapons

   private void fire(ServerPlayer owner, LegionSquad squad, LivingEntity target, LegionTactics.Stance stance) {
      if (this.channel != CHANNEL_NONE) {
         return;
      }

      Vec3 eye = this.suit.getEyePosition();
      double distance = eye.distanceTo(target.getBoundingBox().getCenter());
      if (distance > this.profile.range() * 1.9 + 6.0 || !LegionSquad.clear((ServerLevel)this.suit.level(), eye, target.getEyePosition(), this.suit)) {
         return;
      }

      float effective = target.getHealth() * (1.0F + Math.min(30.0F, target.getArmorValue()) / 25.0F);
      float normalShot = LegionRules.REPULSOR_DAMAGE * 2.0F;
      double ally = this.allyNear(owner, target);
      boolean engage = stance == LegionTactics.Stance.ENGAGE;
      if (engage && this.weaponTimer <= 0 && LegionTactics.heavyAllowed(this.profile.weapon(), effective, normalShot, ally) && this.startSignature(owner, target, ally)) {
         this.weaponTimer = this.profile.weaponPeriod() + this.suit.getRandom().nextInt(20);
         return;
      }

      if (engage && this.profile.missiles() > 0 && this.missileTimer <= 0 && LegionTactics.missilesAllowed(effective, normalShot, ally)) {
         this.missiles(target);
         this.missileTimer = this.profile.missilePeriod() + this.suit.getRandom().nextInt(20);
         return;
      }

      if (this.repulsorTimer <= 0) {
         boolean right = this.rightHand && !this.suit.flag(LegionSuitEntity.FLAG_FIST_AWAY);
         if (this.suit.flag(LegionSuitEntity.FLAG_FIST_AWAY)) {
            right = false;
         }

         this.repulsor(target, right);
         this.rightHand = !this.rightHand;
         int period = this.profile.repulsorPeriod();
         this.repulsorTimer = (stance == LegionTactics.Stance.RETREAT ? period * 3 / 2 : period) + this.suit.getRandom().nextInt(4);
      }
   }

   /** Nearest thing the Legion must not hurt (Tony, allies, villagers) to the target, for splash and beams. */
   private double allyNear(ServerPlayer owner, LivingEntity target) {
      double best = owner.distanceTo(target);
      AABB box = target.getBoundingBox().inflate(LegionRules.SPLASH_SAFE_RADIUS + 1.0);
      for (LivingEntity e : this.suit.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != target && !(e instanceof LegionSuitEntity))) {
         if (!LegionAllies.mayHit(this.suit, e)) {
            best = Math.min(best, e.distanceTo(target));
         }
      }

      return best;
   }

   private Vec3 lead(LivingEntity target, Vec3 from, double speed) {
      Vec3 center = target.getBoundingBox().getCenter();
      double t = Math.min(20.0, from.distanceTo(center) / Math.max(0.5, speed));
      Vec3 v = target.getDeltaMovement();
      return center.add(v.x * t, Math.max(-0.5, v.y) * t * 0.5, v.z * t);
   }

   private void repulsor(LivingEntity target, boolean right) {
      Vec3 from = this.suit.hand(right);
      Vec3 dir = this.lead(target, from, dev.baranhan.viltrumitecore.hero.ironman.IronManRules.REPULSOR_SPEED).subtract(from);
      float damage = LegionRules.REPULSOR_DAMAGE;
      if (this.camoTicks > 0) {
         damage *= SignatureRules.CAMO_FIRST_HIT;
         this.camo(false);
      }

      RepulsorBlastEntity.shoot(this.suit, from, dir, damage, LegionRules.REPULSOR_POWER, LegionRules.REPULSOR_KNOCKBACK);
      this.suit.playMove(right ? LegionMove.FIRE_RIGHT : LegionMove.FIRE_LEFT);
      this.suit.sound(IronManLegionSounds.REPULSOR.get(), 0.8F, 0.95F + this.suit.getRandom().nextFloat() * 0.15F);
   }

   private void missiles(LivingEntity target) {
      int n = this.profile.missiles();
      for (int i = 0; i < n; i++) {
         boolean right = i % 2 == 0;
         Vec3 from = this.suit.shoulder(right);
         Vec3 up = new Vec3(0.0, 0.6, 0.0).add(LegionSteering.right(this.suit.bodyForward()).scale(right ? 0.4 : -0.4));
         Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize().add(up).normalize();
         float factor = LegionRules.MISSILE_FACTOR * (this.mark == MarkId.WAR_MACHINE_MK2 ? 1.2F : 1.0F);
         MicroMissileEntity.launch(this.suit, from, dir, target, factor);
      }

      this.suit.playMove(LegionMove.MISSILES);
      this.suit.sound(IronManCombatSounds.MISSILE_LAUNCH.get(), 0.9F, 1.1F);
   }

   /** The mark's own signature; false when this round is not the right moment for it. */
   private boolean startSignature(ServerPlayer owner, LivingEntity target, double ally) {
      SignatureActor actor = this.suit.signatureActor();
      switch (this.profile.weapon()) {
         case MICRO_LASER -> {
            this.startChannel(CHANNEL_LASER, LegionSuitEntity.FLAG_LASER);
            this.suit.sound(IronManMarkSounds.MICRO_LASER.get(), 0.7F, 1.1F);
         }
         case ROCKET_FIST -> {
            if (this.suit.flag(LegionSuitEntity.FLAG_FIST_AWAY)) {
               return false;
            }

            ServerLevel level = actor.level();
            RocketFistEntity fist = new RocketFistEntity(ViltrumiteEntities.ROCKET_FIST.get(), level);
            Vec3 hand = this.suit.hand(true);
            fist.setPos(hand.x, hand.y, hand.z);
            fist.setOwner(this.suit);
            fist.launch(target, target.getBoundingBox().getCenter());
            level.addFreshEntity(fist);
            this.fistId = fist.getId();
            this.suit.setFlag(LegionSuitEntity.FLAG_FIST_AWAY, true);
            this.suit.playMove(LegionMove.FIRE_RIGHT);
            this.suit.sound(IronManMarkSounds.ROCKET_FIST_LAUNCH.get(), 1.0F, 1.05F);
         }
         case CAMO -> {
            if (this.camoTicks > 0) {
               return false;
            }

            this.camo(true);
         }
         case STARBOOST -> {
            Vec3 past = LegionSteering.combatSpot(LegionRole.FLANKER, target.position(), owner.position(), this.suit.position(), this.profile.range() * 0.7,
               this.profile.altitude() * 0.5, -this.side, this.suit.tickCount);
            this.channelPoint = this.freeSpot(past);
            this.startChannel(CHANNEL_BOOST, LegionSuitEntity.FLAG_BOOST);
            this.side = -this.side;
            this.suit.sound(IronManMarkSounds.STARBOOST.get(), 1.0F, 1.1F);
            HeroFx.flash(this.suit, this.suit.position().add(0.0, 0.3, 0.0), 0.7F);
         }
         case PULSE_UNIBEAM -> {
            this.startChannel(CHANNEL_PULSE, LegionSuitEntity.FLAG_PULSE);
            this.suit.sound(IronManMarkSounds.PULSE_UNIBEAM.get(), 1.0F, 1.0F);
         }
         case SHOULDER_GUN -> this.startChannel(CHANNEL_GUN, LegionSuitEntity.FLAG_GUN);
         case SLAM -> {
            if (!target.onGround() || owner.distanceTo(target) < LegionRules.SPLASH_SAFE_RADIUS + 1.5 || ally < LegionRules.SPLASH_SAFE_RADIUS + 1.0) {
               return false;
            }

            this.channelPoint = target.position();
            this.startChannel(CHANNEL_SLAM, 0);
            this.suit.sound(IronManLegionSounds.IGNITE.get(), 1.0F, 1.2F);
         }
      }

      this.suit.playMove(this.profile.weapon() == LegionWeapon.ROCKET_FIST ? LegionMove.FIRE_RIGHT : LegionMove.SIGNATURE);
      return true;
   }

   private void startChannel(int channel, int flag) {
      this.channel = channel;
      this.channelTicks = 0;
      if (flag != 0) {
         this.suit.setFlag(flag, true);
      }
   }

   private void stopChannel() {
      this.channel = CHANNEL_NONE;
      this.channelTicks = 0;
      this.suit.setFlag(LegionSuitEntity.FLAG_LASER, false);
      this.suit.setFlag(LegionSuitEntity.FLAG_PULSE, false);
      this.suit.setFlag(LegionSuitEntity.FLAG_GUN, false);
      this.suit.setFlag(LegionSuitEntity.FLAG_BOOST, false);
   }

   /** Weapon channels run every tick (beam ticks, pulse timeline, gun cadence, boost and slam flight). */
   private void channelTick(ServerPlayer owner) {
      if (this.channel == CHANNEL_NONE) {
         return;
      }

      int t = this.channelTicks++;
      LivingEntity target = this.target;
      SignatureActor actor = this.suit.signatureActor();
      switch (this.channel) {
         case CHANNEL_LASER -> {
            if (target == null || t >= LASER_TICKS) {
               this.stopChannel();
               return;
            }

            if (t % 2 == 0) {
               MicroLaser.beam(actor, this.suit.aimDirection());
            }

            if (t % SignatureRules.LASER_SOUND_INTERVAL == 0 && t > 0) {
               this.suit.sound(IronManMarkSounds.MICRO_LASER.get(), 0.5F, 1.1F);
            }
         }
         case CHANNEL_PULSE -> {
            if (t >= SignatureRules.pulseTotal()) {
               this.stopChannel();
               return;
            }

            if (SignatureRules.pulseAt(t) >= 0) {
               Vec3 dir = target == null ? this.suit.getLookAngle() : target.getBoundingBox().getCenter().subtract(this.suit.chest()).normalize();
               PulseUnibeam.pulse(actor, this.suit.chest(), dir, SignatureRules.pulseHitsTick(t));
            }
         }
         case CHANNEL_GUN -> {
            if (target == null || t >= GUN_TICKS) {
               this.stopChannel();
               return;
            }

            if (SignatureRules.gunFires(t)) {
               ShoulderGun.round(actor, this.suit.aimDirection());
            }
         }
         case CHANNEL_BOOST -> {
            if (t >= BOOST_TICKS || this.suit.position().distanceTo(this.channelPoint) < 1.5) {
               this.stopChannel();
               if (target != null) {
                  this.repulsor(target, true);
                  this.repulsor(target, false);
                  this.suit.playMove(LegionMove.FIRE_BOTH);
               }
            }
         }
         case CHANNEL_SLAM -> {
            boolean diving = t >= SLAM_RISE;
            boolean down = diving && (this.suit.onGround() || this.suit.verticalCollision);
            if (down || t >= SLAM_MAX) {
               this.stopChannel();
               if (down) {
                  this.slamImpact(owner);
               }
            }
         }
         default -> this.stopChannel();
      }
   }

   private Vec3 slamVelocity() {
      if (this.channelTicks < SLAM_RISE) {
         Vec3 over = this.channelPoint.add(0.0, 6.0, 0.0).subtract(this.suit.position());
         return LegionSteering.limit(over.scale(0.25), 1.2);
      }

      Vec3 down = this.channelPoint.subtract(this.suit.position());
      return down.lengthSqr() < 1.0E-3 ? new Vec3(0.0, -2.2, 0.0) : down.normalize().scale(2.2);
   }

   private void slamImpact(ServerPlayer owner) {
      ServerLevel level = (ServerLevel)this.suit.level();
      Vec3 at = this.suit.position();
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.suit.getBoundingBox().inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS))) {
         if (e == this.suit || e == owner || !e.isAlive() || e.distanceToSqr(at) > SLAM_RADIUS * SLAM_RADIUS || !LegionAllies.mayHit(this.suit, e)) {
            continue;
         }

         double falloff = 1.0 - Math.sqrt(e.distanceToSqr(at)) / SLAM_RADIUS * 0.5;
         if (SignatureTrace.strike(e, this.suit.signatureActor().attack(), (float)(SLAM_DAMAGE * falloff))) {
            Vec3 away = e.position().subtract(at);
            away = new Vec3(away.x, 0.0, away.z);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize().scale(0.8 * falloff);
            SignatureTrace.push(e, away.add(0.0, 0.45, 0.0));
         }
      }

      BlockState ground = level.getBlockState(this.suit.getOnPos());
      HeroFx.slam(this.suit, at, ground.isAir() ? null : ground, (float)SLAM_RADIUS);
      this.suit.sound(IronManMarkSounds.SLAM_IMPACT.get(), 1.2F, 1.05F);
      this.suit.playMove(LegionMove.LAND);
      this.impulse = new Vec3(0.0, 0.5, 0.0);
   }

   private void camo(boolean on) {
      if (this.mark != MarkId.MARK_15) {
         return;
      }

      boolean was = this.suit.flag(LegionSuitEntity.FLAG_CAMO);
      this.suit.setFlag(LegionSuitEntity.FLAG_CAMO, on);
      this.camoTicks = on ? SignatureRules.CAMO_TICKS : 0;
      if (on && !was) {
         this.suit.sound(IronManMarkSounds.CAMO_ON.get(), 0.6F, 1.0F);
         this.camoShake();
      } else if (!on && was) {
         this.suit.sound(IronManMarkSounds.CAMO_OFF.get(), 0.6F, 1.0F);
      }
   }

   /** Mobs lose the cloaked suit. */
   private void camoShake() {
      for (Mob mob : this.suit.level().getEntitiesOfClass(Mob.class, this.suit.getBoundingBox().inflate(SignatureRules.CAMO_MOB_RANGE * 3.0))) {
         if (mob.getTarget() == this.suit) {
            mob.setTarget(null);
         }
      }
   }

   /** Dismissal without a flight (logout, dimension change): stop everything that would outlive the body. */
   public void abort() {
      this.stopChannel();
      if (this.fistId >= 0 && this.suit.level().getEntity(this.fistId) instanceof RocketFistEntity fist) {
         fist.discard();
      }

      this.fistId = -1;
   }
}
