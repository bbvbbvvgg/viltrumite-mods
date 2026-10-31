package dev.baranhan.viltrumitecore.worldevent.thanos;

import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.MindStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.PowerStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.RealityStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.SoulStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.SpaceStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.stone.TimeStone;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Thanos's fight on the server: target choice, stalking, the brain's moves (telegraph -> active
 * -> recovery) and every lingering stone effect. Runs only while the fight runs and the world
 * is not stopped; a frozen Thanos (time stop, a hero's control) skips his own part while the
 * effects already in the world finish.
 */
public final class ThanosCombat {
   public final ThanosEvent event;
   public final ServerLevel level;
   public final ThanosEntity thanos;
   public final ThanosBrain brain;
   public final PowerStone power = new PowerStone(this);
   public final SpaceStone space = new SpaceStone(this);
   public final RealityStone reality = new RealityStone(this);
   public final TimeStone time = new TimeStone(this);
   public final MindStone mind = new MindStone(this);
   public final SoulStone soul = new SoulStone(this);
   @Nullable
   public ThanosMove move;
   public int moveTick;
   public int telegraph;
   public ThanosPhase movePhase = ThanosPhase.OPENING;
   @Nullable
   public LivingEntity target;
   /** Aim point locked at the move start (the telegraph marks it). */
   public Vec3 aim = Vec3.ZERO;
   /** Move-owned second point (portal exit, dome centre). */
   public Vec3 aux = Vec3.ZERO;
   private float channelStart;
   private int breather = ThanosRules.BREATHER_TICKS;
   private int retarget;
   private int stunned;
   private int frozenFor;
   private int releasedAgo = 1000;
   private Vec3 stuckAnchor = Vec3.ZERO;
   private int stuckTicks;
   private int bulldozes;
   private int moves;

   public ThanosCombat(ThanosEvent event, ServerLevel level, ThanosEntity thanos, long seed) {
      this.event = event;
      this.level = level;
      this.thanos = thanos;
      this.brain = new ThanosBrain(seed);
   }

   public float scale() {
      return ThanosRules.damageScale(this.event.heroCount());
   }

   public int movesStarted() {
      return this.moves;
   }

   /**
    * One fight tick. {@code frozen}: Thanos himself is held (a hero's time stop or control);
    * {@code worldStopped}: a time stop holds the world too, so his lingering effects wait as well.
    */
   public void tick(boolean frozen, boolean worldStopped) {
      if (!worldStopped) {
         this.power.tick();
         this.space.tick();
         this.reality.tick();
         this.time.tick();
         this.mind.tick();
         this.soul.tick();
      }

      if (frozen) {
         this.frozenFor++;
         this.thanos.getNavigation().stop();
         return;
      }

      if (this.frozenFor > 10) {
         this.releasedAgo = 0;
      }

      this.frozenFor = 0;
      this.releasedAgo = Math.min(1000, this.releasedAgo + 1);
      this.brain.tick();
      if (this.stunned > 0) {
         this.stunned--;
         this.thanos.getNavigation().stop();
         return;
      }

      if (this.move != null) {
         this.tickMove();
         return;
      }

      this.updateTarget();
      this.stalk();
      if (this.breather > 0) {
         this.breather--;
         return;
      }

      ThanosMove next = this.brain.choose(this.situation());
      if (next != null) {
         this.start(next);
      }
   }

   // ---- targets -------------------------------------------------------------------------------------
   private void updateTarget() {
      if (this.target != null && (!this.valid(this.target) || --this.retarget <= 0)) {
         this.target = null;
      }

      if (this.target == null) {
         this.retarget = ThanosRules.RETARGET_TICKS;
         LivingEntity attacker = this.thanos.getLastHurtByMob();
         if (attacker != null && this.valid(attacker) && this.thanos.tickCount - this.thanos.getLastHurtByMobTimestamp() < 60
            && this.thanos.distanceToSqr(attacker) < 48 * 48) {
            this.target = attacker;
         } else {
            this.target = this.nearestHero();
         }
      }

      this.thanos.setTargetId(this.target == null ? -1 : this.target.getId());
   }

   private boolean valid(LivingEntity e) {
      return e.isAlive() && e.level() == this.level && ThanosTargets.victim(e, this.thanos)
         && e.distanceToSqr(this.thanos) <= ThanosRules.PARTICIPANT_RADIUS * ThanosRules.PARTICIPANT_RADIUS
         && (!(e instanceof ServerPlayer p) || this.event.isParticipant(p));
   }

   @Nullable
   private LivingEntity nearestHero() {
      LivingEntity best = null;
      double bestD = Double.MAX_VALUE;
      for (ServerPlayer p : this.event.heroes()) {
         double d = p.distanceToSqr(this.thanos);
         if (d < bestD) {
            bestD = d;
            best = p;
         }
      }

      return best;
   }

   public List<ServerPlayer> heroes() {
      return this.event.heroes();
   }

   private ThanosBrain.Situation situation() {
      LivingEntity t = this.target;
      double distance = t == null ? 999.0 : Math.sqrt(t.distanceToSqr(this.thanos));
      int close = 0;
      for (ServerPlayer p : this.heroes()) {
         if (p.distanceToSqr(this.thanos) < 64.0) {
            close++;
         }
      }

      float max = this.thanos.getMaxHealth();
      return new ThanosBrain.Situation(this.thanos.phase(), t != null, distance, t != null && airborne(t), this.event.heroCount(), close,
         this.incomingProjectiles(), this.releasedAgo <= ThanosRules.TIME_STOP_REACTION_TICKS, this.thanos.threatened(),
         this.thanos.getHealth() / max, this.thanos.damageCap().recent() / max);
   }

   /** Off the ground and well above it (flying heroes). */
   public boolean airborne(LivingEntity e) {
      if (e.onGround()) {
         return false;
      }

      Vec3 from = e.position();
      HitResult hit = this.level.clip(new ClipContext(from, from.add(0, -ThanosRules.AIRBORNE_HEIGHT, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
      return hit.getType() == HitResult.Type.MISS;
   }

   private int incomingProjectiles() {
      Vec3 c = this.chest();
      int n = 0;
      for (Projectile p : this.level.getEntitiesOfClass(Projectile.class, this.thanos.getBoundingBox().inflate(12.0))) {
         if (p.getOwner() == this.thanos) {
            continue;
         }

         Vec3 v = p.getDeltaMovement();
         Vec3 to = c.subtract(p.position());
         double speed = v.length();
         if (speed > 0.2 && v.dot(to) > 0.5 * speed * to.length()) {
            n++;
         }
      }

      return n;
   }

   // ---- movement ---------------------------------------------------------------------------------
   private void stalk() {
      LivingEntity t = this.target;
      if (t == null) {
         this.thanos.getNavigation().stop();
         return;
      }

      this.thanos.faceTowards(t.position(), 12.0F);
      double d = Math.sqrt(this.thanos.distanceToSqr(t));
      if (d < 3.2) {
         this.thanos.getNavigation().stop();
         this.stuckTicks = 0;
         return;
      }

      if (this.thanos.tickCount % 10 == 0) {
         this.thanos.getNavigation().moveTo(t, 1.0);
      }

      // footfalls are played by the clients in step with his walk cycle (ThanosFx)

      // Stuck in the city: push through soft obstacles, then let the Space stone carry him.
      if (this.thanos.position().distanceToSqr(this.stuckAnchor) > 1.0) {
         this.stuckAnchor = this.thanos.position();
         this.stuckTicks = 0;
         return;
      }

      if (++this.stuckTicks >= 50 && d > 6.0) {
         this.stuckTicks = 0;
         if (this.bulldozes < 2 && this.bulldoze()) {
            this.bulldozes++;
         } else {
            this.bulldozes = 0;
            if (this.brain.ready(ThanosMove.SPACE_STEP) && this.thanos.phase().atLeast(ThanosMove.SPACE_STEP.minPhase)) {
               this.start(ThanosMove.SPACE_STEP);
            }
         }
      }
   }

   /** Breaks the blocks right in front of him (doorway, fence, wall), at most 18. */
   private boolean bulldoze() {
      Vec3 look = Vec3.directionFromRotation(0, this.thanos.getYRot());
      BlockPos base = BlockPos.containing(this.thanos.position().add(look.scale(1.6)));
      int broken = 0;
      for (int up = 0; up < 4; up++) {
         for (int side = -1; side <= 1; side++) {
            BlockPos pos = base.offset((int)Math.round(-look.z * side), up, (int)Math.round(look.x * side));
            if (broken < 18 && this.level.getBlockEntity(pos) == null && this.level.getBlockState(pos).getDestroySpeed(this.level, pos) <= 6.0F
               && HeroDestruction.destroyBlock(this.level, pos)) {
               broken++;
            }
         }
      }

      if (broken > 0) {
         ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.SLAM, 1.0F, 1.1F, 40.0);
      }

      return broken > 0;
   }

   // ---- moves ------------------------------------------------------------------------------------
   /** Starts a move now (the brain's choice or a forced reaction). */
   public void start(ThanosMove next) {
      this.move = next;
      this.moveTick = 0;
      this.movePhase = this.thanos.phase();
      this.telegraph = ThanosRules.telegraph(next.telegraph, this.movePhase);
      this.brain.commit(next, this.movePhase);
      this.thanos.startMove(next);
      this.thanos.getNavigation().stop();
      this.channelStart = this.thanos.damageCap().total();
      this.aim = this.target == null ? this.thanos.position() : this.target.position();
      this.aux = this.aim;
      this.moves++;
      ThanosTargets.fx(this.level, ThanosFxS2CPacket.TELEGRAPH, this.thanos.getId(), this.target == null ? -1 : this.target.getId(), this.aim,
         Vec3.ZERO, this.telegraph, next.ordinal());
      this.onBegin(next);
   }

   private void tickMove() {
      ThanosMove m = this.move;
      if (m == null) {
         return;
      }

      if (this.target != null && !this.valid(this.target)) {
         this.target = null;
      }

      int t = this.moveTick++;
      if (t < this.telegraph) {
         this.onTelegraph(m, t);
      } else if (t < this.telegraph + m.active) {
         this.onActive(m, t - this.telegraph);
      } else if (t >= this.telegraph + m.active + m.recovery) {
         this.finish();
         return;
      }

      if (channelled(m) && t >= this.telegraph && this.thanos.damageCap().total() - this.channelStart >= 0.06F * this.thanos.getMaxHealth()) {
         this.stun();
      }
   }

   /** Moves he holds still for: heavy damage meanwhile staggers him (counterplay). */
   private static boolean channelled(ThanosMove m) {
      return m == ThanosMove.SOUL_TETHER || m == ThanosMove.COMBO_SOUL_MIRAGE || m == ThanosMove.COMBO_ETERNITY || m == ThanosMove.MIND_PULSE;
   }

   private void stun() {
      this.soul.breakAll();
      this.move = null;
      this.thanos.startMove(null);
      this.thanos.react(ThanosEntity.REACT_STUN);
      this.stunned = 40;
      this.breather = ThanosRules.BREATHER_TICKS;
      ThanosTargets.fx(this.level, ThanosFxS2CPacket.STUN, this.thanos.getId(), -1, this.chest(), Vec3.ZERO, 40, 0);
      ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.STUN, 1.6F, 1.0F, 64.0);
   }

   private void finish() {
      this.move = null;
      this.thanos.startMove(null);
      this.breather = this.thanos.phase() == ThanosPhase.FINAL ? ThanosRules.BREATHER_TICKS / 2 : ThanosRules.BREATHER_TICKS;
   }

   /** Cuts the running move short (no valid destination, target gone). */
   public void abort() {
      if (this.move != null) {
         this.finish();
      }
   }

   private void onBegin(ThanosMove m) {
      switch (m) {
         case POWER_PUNCH, POWER_WAVE, COMBO_TIME_CRUSH -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.POWER_CHARGE, 1.8F, 1.0F, 64.0);
         case SPACE_STEP -> this.space.planStep();
         case SPACE_PULL -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.SPACE_OPEN, 1.5F, 1.1F, 64.0);
         case SPACE_WARD -> this.space.ward(m.active);
         case REALITY_ZONE -> this.reality.planZones();
         case REALITY_MIRAGE, COMBO_SOUL_MIRAGE -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.REALITY_WARP, 1.5F, 0.9F, 64.0);
         case TIME_DOME, TIME_REWIND, COMBO_ETERNITY -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.TIME_DOME, 1.2F, 1.2F, 64.0);
         case MIND_GAZE, COMBO_MIND_STEP -> this.mind.chargeGaze(this.telegraph);
         case MIND_PULSE -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.MIND_GAZE, 1.4F, 1.3F, 64.0);
         case SOUL_TETHER, SOUL_NOVA -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.SOUL_TETHER, 1.4F, 0.8F, 64.0);
         case COMBO_PORTAL_PUNCH -> this.space.planPortalPunch();
         case COMBO_TRINITY -> this.space.portalRing(this.telegraph + m.active);
         default -> ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.SWING, 1.0F, 0.8F, 32.0);
      }

      if (m == ThanosMove.COMBO_ETERNITY) {
         this.mind.chargeGaze(this.telegraph);
      }
   }

   private void onTelegraph(ThanosMove m, int t) {
      if (this.target != null && m != ThanosMove.TIME_REWIND && m != ThanosMove.SPACE_WARD) {
         this.thanos.faceTowards(this.target.position(), 6.0F);
      }

      if (m == ThanosMove.TIME_DOME && t == 0) {
         this.aux = this.target != null ? this.target.position() : this.thanos.position();
         this.time.telegraphDome(this.aux, this.telegraph);
      }

      if (m == ThanosMove.COMBO_TIME_CRUSH && t == 0) {
         this.aux = this.thanos.position();
         this.time.telegraphDome(this.aux, this.telegraph);
      }
   }

   private void onActive(ThanosMove m, int t) {
      float s = this.scale();
      switch (m) {
         case CLEAVE -> {
            if (t == 0) {
               this.lunge(0.7);
               this.power.cleave(m.damage * s);
            }
         }
         case HAMMER -> {
            if (t == 0) {
               this.power.hammer(m.damage * s);
            }
         }
         case POWER_PUNCH -> {
            if (t == 0) {
               this.lunge(1.1);
            }

            if (t == 3) {
               this.power.punch(this.hand(), this.facing(), m.damage * s, ThanosStoneRules.Power.PUNCH_REACH, true);
            }
         }
         case POWER_WAVE -> {
            if (t == 0) {
               this.power.wave(this.thanos.position(), m.damage * s, ThanosStoneRules.Reality.SHAPE_CIRCLE);
            }
         }
         case SPACE_STEP -> {
            if (t == 0) {
               this.space.step();
            }
         }
         case SPACE_PULL -> {
            if (t == 0) {
               this.space.pull(this.target, m.active);
            }

            if (t == m.active - 1) {
               this.space.endPull(m.damage * s);
            }
         }
         case SPACE_WARD -> {
         }
         case REALITY_ZONE -> {
            if (t == 0) {
               this.reality.spawnZones(m.damage * s);
            }
         }
         case REALITY_MIRAGE -> {
            if (t == 0) {
               this.reality.mirages(false);
            }

            if (t == 40) {
               this.reality.strike(m.damage * s);
            }
         }
         case TIME_DOME -> {
            if (t == 0) {
               this.time.dome(this.aux, ThanosStoneRules.Time.DOME_RADIUS, ThanosStoneRules.Time.DOME_LIFE);
            }
         }
         case TIME_REWIND -> {
            if (t == 0) {
               this.time.rewind();
            }
         }
         case MIND_GAZE -> {
            if (t == 0) {
               this.mind.gaze(m.damage * s);
            }
         }
         case MIND_PULSE -> {
            if (t == 0) {
               this.mind.pulse(this.thanos.position(), m.damage * s);
            }
         }
         case SOUL_TETHER -> {
            if (t == 0) {
               this.soul.tether(this.handPos(), m.active, false);
            }
         }
         case SOUL_NOVA -> {
            if (t == 0) {
               this.soul.nova(this.thanos.position().add(0, 1.0, 0), ThanosStoneRules.Soul.NOVA_RADIUS, m.damage * s);
            }
         }
         case COMBO_PORTAL_PUNCH -> {
            if (t == 6) {
               this.space.portalPunch(m.damage * s);
            }
         }
         case COMBO_TIME_CRUSH -> {
            if (t == 0) {
               this.time.dome(this.aux, ThanosStoneRules.Time.DOME_RADIUS, 40);
            }

            if (t == 12) {
               this.power.crush(this.aux, ThanosStoneRules.Time.DOME_RADIUS, m.damage * s);
            }
         }
         case COMBO_SOUL_MIRAGE -> {
            if (t == 0) {
               this.reality.mirages(true);
            }
         }
         case COMBO_MIND_STEP -> {
            if (t == 0) {
               this.mind.gaze(6.0F * s);
               this.space.planStep();
            }

            if (t == 4) {
               this.space.step();
            }

            if (t == 10) {
               this.lunge(0.6);
               this.power.cleave(m.damage * s);
            }
         }
         case COMBO_TRINITY -> {
            if (t == 0) {
               this.power.wave(this.thanos.position(), m.damage * s, ThanosStoneRules.Reality.SHAPE_STAR);
            }

            if (t % 10 == 5) {
               this.reality.shards(10.0F * s);
            }
         }
         case COMBO_ETERNITY -> {
            if (t == 0) {
               this.time.dome(this.thanos.position(), 10.0, m.active);
               this.mind.gaze(6.0F * s);
            }

            if (t == 20) {
               this.soul.nova(this.thanos.position().add(0, 1.0, 0), 10.0, m.damage * s);
            }

            if (t == 24) {
               this.soul.tether(this.handPos(), m.active - 24, false);
            }
         }
      }
   }

   // ---- geometry ----------------------------------------------------------------------------------
   /** Horizontal facing (unit). */
   public Vec3 facing() {
      return Vec3.directionFromRotation(0.0F, this.thanos.getYRot());
   }

   /** The gauntlet (left hand) in the world, at rest beside his body. */
   public Vec3 handPos() {
      float yaw = this.thanos.getYRot() * Mth.DEG_TO_RAD;
      Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
      return this.thanos.position().add(left.scale(1.0)).add(this.facing().scale(0.5)).add(0.0, 2.3, 0.0);
   }

   /** Where the fist lands for a punch: in front of the chest. */
   public Vec3 hand() {
      return this.thanos.position().add(this.facing().scale(1.8)).add(0.0, 1.9, 0.0);
   }

   public Vec3 chest() {
      return this.thanos.position().add(0.0, this.thanos.getBbHeight() * 0.62, 0.0);
   }

   public Vec3 eyes() {
      return this.thanos.position().add(0.0, this.thanos.getBbHeight() * 0.88, 0.0);
   }

   private void lunge(double speed) {
      Vec3 f = this.facing();
      this.thanos.scriptedMotion(new Vec3(f.x * speed, Math.max(0.0, this.thanos.getDeltaMovement().y), f.z * speed));
   }

   /** Ends every lingering effect at once (stop, outcome). */
   public void clearAll() {
      this.power.clear();
      this.space.clear();
      this.reality.clear();
      this.time.clear();
      this.mind.clear();
      this.soul.clear();
      this.move = null;
      this.thanos.startMove(null);
      this.thanos.getNavigation().stop();
   }
}
