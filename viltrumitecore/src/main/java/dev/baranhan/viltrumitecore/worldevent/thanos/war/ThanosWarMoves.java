package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The THANOS 2.0 moves (ordinals from {@link ThanosMove#SHIP_CALL}): the ship's lances on his
 * signal, the Power rupture, the Space barrage, the Reality rewrite, the Time anomaly, the Mind
 * compulsion, the Soul harvest, the Gauntlet combos (time stop, six portals and a charged volley;
 * the moon fall; all six stones in turn) and his COMMAND beat. Every hit is announced (markers,
 * the telegraph), goes through {@link ThanosTargets} (hero defences, protections) and every hold
 * through {@link ThanosHolds} (the ledger: no endless control). Projectiles and falling rocks are
 * simulated here; clients draw them from the same launch data.
 */
public final class ThanosWarMoves {
   private static final class Shard {
      final int id;
      Vec3 pos;
      final Vec3 vel;
      int life;
      final float damage;

      Shard(int id, Vec3 pos, Vec3 vel, int life, float damage) {
         this.id = id;
         this.pos = pos;
         this.vel = vel;
         this.life = life;
         this.damage = damage;
      }
   }

   private static final class Phantom {
      final int id;
      Vec3 pos;
      final LivingEntity target;
      int life;
      final float damage;

      Phantom(int id, Vec3 pos, LivingEntity target, int life, float damage) {
         this.id = id;
         this.pos = pos;
         this.target = target;
         this.life = life;
         this.damage = damage;
      }
   }

   private static final class Rock {
      final int id;
      final Vec3 at;
      int left;
      final float damage;
      final double radius;

      Rock(int id, Vec3 at, int left, float damage, double radius) {
         this.id = id;
         this.at = at;
         this.left = left;
         this.damage = damage;
         this.radius = radius;
      }
   }

   private static final class Rift {
      Vec3 at;
      final Vec3 dir;
      int steps;
      int wait;
      final float damage;
      final Set<Integer> hit = new HashSet<>();

      Rift(Vec3 at, Vec3 dir, int steps, float damage) {
         this.at = at;
         this.dir = dir;
         this.steps = steps;
         this.damage = damage;
      }
   }

   /** A gate the barrage / volley fires from. */
   private record Gate(Vec3 at, @Nullable LivingEntity aim) {
   }

   static final double SHARD_SPEED = 1.5;
   static final int SHARD_LIFE = 36;
   static final int PHANTOM_LIFE = 60;
   static final double PHANTOM_SPEED = 0.34;
   static final int ROCK_FALL = 26;
   static final int ANOMALY_DELAY = 40;
   static final double REWRITE_RADIUS = 6.0;
   static final double TIME_STOP_RADIUS = 32.0;
   static final int TIME_STOP_TICKS = 30;

   private final ThanosCombat c;
   private final ThanosWar war;
   private final List<Shard> shards = new ArrayList<>();
   private final List<Phantom> phantoms = new ArrayList<>();
   private final List<Rock> rocks = new ArrayList<>();
   private final List<Rift> rifts = new ArrayList<>();
   private final List<Gate> gates = new ArrayList<>();
   private final List<LivingEntity> frozen = new ArrayList<>();
   @Nullable
   private LivingEntity marked;
   private Vec3 markedAt = Vec3.ZERO;
   private int markLeft;
   private float markDamage;
   @Nullable
   private LivingEntity compelled;
   private Vec3 rewriteAt = Vec3.ZERO;
   private boolean rewriteDown;
   private int ids;

   public ThanosWarMoves(ThanosCombat combat, ThanosWar war) {
      this.c = combat;
      this.war = war;
   }

   private ServerLevel level() {
      return this.c.level;
   }

   private int id() {
      return this.ids++;
   }

   public int shards() {
      return this.shards.size();
   }

   public int phantoms() {
      return this.phantoms.size();
   }

   public int rocks() {
      return this.rocks.size();
   }

   // ---- the move hooks (ThanosCombat) -------------------------------------------------------------
   public void begin(ThanosMove m) {
      ServerLevel level = this.level();
      Vec3 at = this.c.thanos.position();
      switch (m) {
         case SHIP_CALL -> ThanosSounds.play(level, at, ThanosSounds.POWER_CHARGE, 1.4F, 0.7F, 64.0);
         case POWER_RUPTURE -> ThanosSounds.play(level, at, ThanosSounds.POWER_CHARGE, 1.8F, 0.9F, 64.0);
         case SPACE_BARRAGE -> this.planGates(6, 3.2, false);
         case REALITY_REWRITE -> {
            LivingEntity t = this.c.target;
            this.rewriteAt = t == null ? at : WarGround.below(level, t.position().add(0, 1, 0), 24);
            this.rewriteDown = t != null && this.c.airborne(t);
            ThanosTargets.fx(level, ThanosFxS2CPacket.REWRITE, this.c.thanos.getId(), this.rewriteDown ? 1 : 0, this.rewriteAt, Vec3.ZERO,
               (float)REWRITE_RADIUS, this.c.telegraph + m.active);
            ThanosWarSounds.play(level, this.rewriteAt, ThanosWarSounds.REWRITE, 2.0F, 1.0F, 64.0);
         }
         case TIME_ANOMALY -> ThanosSounds.play(level, at, ThanosSounds.TIME_DOME, 1.2F, 1.4F, 64.0);
         case MIND_COMPULSION -> this.c.mind.chargeGaze(this.c.telegraph);
         case SOUL_HARVEST -> ThanosWarSounds.play(level, at, ThanosWarSounds.HARVEST, 2.0F, 0.9F, 72.0);
         case COMBO_TIME_VOLLEY -> ThanosSounds.play(level, at, ThanosSounds.TIME_DOME, 1.6F, 0.8F, 96.0);
         case COMBO_MOON_FALL -> {
            Vec3 sky = this.fightCentre().add(0, 30, 0);
            ThanosTargets.fx(level, ThanosFxS2CPacket.MOON_PORTAL, this.c.thanos.getId(), 0, sky, new Vec3(0, -1, 0), 14.0F, this.c.telegraph + m.active + 10);
            ThanosSounds.play(level, at, ThanosSounds.SPACE_OPEN, 3.0F, 0.6F, 160.0);
         }
         case COMBO_INFINITY -> {
            ThanosTargets.fx(level, ThanosFxS2CPacket.INFINITY_STAGE, this.c.thanos.getId(), -1, this.c.hand(), Vec3.ZERO, 1.0F, this.c.telegraph);
            this.war.say(ThanosWar.VOICE_THANOS, this.c.thanos, "infinity", HeroLine.Priority.ULTIMATE, 0, true);
         }
         case COMMAND -> {
            ThanosTargets.fx(level, ThanosFxS2CPacket.COMMAND, this.c.thanos.getId(), this.war.phase().ordinal(), this.c.hand(), new Vec3(0, 1, 0), 1.0F,
               this.c.telegraph + m.active);
            ThanosWarSounds.play(level, at, ThanosWarSounds.COMMAND, 3.0F, 1.0F, 128.0);
         }
         default -> {
         }
      }
   }

   public void telegraph(ThanosMove m, int t) {
      switch (m) {
         case SHIP_CALL -> {
            if (t == 0) {
               ThanosTargets.fx(this.level(), ThanosFxS2CPacket.COMMAND, this.c.thanos.getId(), -1, this.c.hand(), new Vec3(0, 1, 0), 0.6F, this.c.telegraph);
            }
         }
         case SPACE_BARRAGE, COMBO_TIME_VOLLEY -> {
         }
         default -> {
         }
      }
   }

   public void active(ThanosMove m, int t, float s) {
      switch (m) {
         case SHIP_CALL -> {
            if (t == 0) {
               this.shipCall(m.damage * s);
            }
         }
         case POWER_RUPTURE -> {
            if (t == 0) {
               this.rupture(m.damage * s);
            }
         }
         case SPACE_BARRAGE -> {
            if (t % 5 == 0 && t / 5 < this.gates.size()) {
               this.fireGate(this.gates.get(t / 5), m.damage * s, 1);
            }
         }
         case REALITY_REWRITE -> this.rewrite(m, t, m.damage * s);
         case TIME_ANOMALY -> {
            if (t == 0) {
               this.anomaly(m.damage * s);
            }
         }
         case MIND_COMPULSION -> this.compulsion(m, t);
         case SOUL_HARVEST -> {
            if (t == 0 || t == 24) {
               this.harvest(m.damage * s, t == 0 ? 2 : 1);
            }
         }
         case COMBO_TIME_VOLLEY -> this.timeVolley(m, t, m.damage * s);
         case COMBO_MOON_FALL -> {
            if (t % 6 == 0 && t < m.active - ROCK_FALL / 2) {
               this.moonRock(m.damage * s);
            }
         }
         case COMBO_INFINITY -> this.infinity(m, t, m.damage * s);
         case COMMAND -> {
            if (t == 0) {
               this.war.command();
            }
         }
         default -> {
         }
      }
   }

   /** His move was cut short (a stagger): the holds he set end, nothing staged fires. */
   public void interrupted(@Nullable ThanosMove m) {
      this.releaseFrozen();
      this.releaseCompelled();
      this.gates.clear();
      if (m == ThanosMove.COMBO_MOON_FALL) {
         this.rocks.clear();
         ThanosTargets.fx(this.level(), ThanosFxS2CPacket.CLEAR_WAR, this.c.thanos.getId(), 1, this.c.thanos.position(), Vec3.ZERO, 0.0F, 0);
      }
   }

   public void clear() {
      this.releaseFrozen();
      this.releaseCompelled();
      this.shards.clear();
      this.phantoms.clear();
      this.rocks.clear();
      this.rifts.clear();
      this.gates.clear();
      this.marked = null;
      this.markLeft = 0;
   }

   // ---- simulation -----------------------------------------------------------------------------------
   public void tick() {
      this.tickShards();
      this.tickPhantoms();
      this.tickRocks();
      this.tickRifts();
      this.tickAnomaly();
   }

   private Vec3 fightCentre() {
      LivingEntity t = this.c.target;
      return t != null ? t.position().add(this.c.thanos.position()).scale(0.5) : this.c.thanos.position();
   }

   // ---- SHIP_CALL ------------------------------------------------------------------------------------
   private void shipCall(float damage) {
      LivingEntity t = this.c.target;
      if (t == null) {
         return;
      }

      ThanosPhase p = this.war.phase();
      int warning = ThanosWarRules.strikeWarning(p);
      boolean ok = this.war.ship.strike(SanctuaryShip.aimAt(t, 10), warning, damage, ThanosWarRules.STRIKE_RADIUS, true);
      int ring = p == ThanosPhase.GAUNTLET ? 3 : 2;
      for (int i = 0; i < ring; i++) {
         double a = Math.PI * 2.0 * i / ring + this.war.random.nextDouble();
         Vec3 at = t.position().add(Math.cos(a) * 7.0, 0.0, Math.sin(a) * 7.0);
         this.war.ship.strike(at, warning + 8 + i * 6, damage * 0.7F, ThanosWarRules.STRIKE_RADIUS * 0.8, true);
      }

      if (ok) {
         this.war.say(ThanosWar.VOICE_THANOS, this.c.thanos, "ship_call", HeroLine.Priority.ABILITY, 400, false);
      }
   }

   // ---- POWER_RUPTURE ----------------------------------------------------------------------------------
   private void rupture(float damage) {
      LivingEntity t = this.c.target;
      Vec3 from = this.c.thanos.position();
      Vec3 to = t == null ? from.add(this.c.facing()) : t.position();
      Vec3 dir = new Vec3(to.x - from.x, 0.0, to.z - from.z);
      dir = dir.lengthSqr() < 1.0E-4 ? this.c.facing() : dir.normalize();
      int lines = this.war.phase() == ThanosPhase.GAUNTLET ? 5 : 3;
      for (int i = 0; i < lines; i++) {
         double spread = (i - (lines - 1) * 0.5) * 0.32;
         Vec3 d = new Vec3(dir.x * Math.cos(spread) - dir.z * Math.sin(spread), 0.0, dir.x * Math.sin(spread) + dir.z * Math.cos(spread));
         this.rifts.add(new Rift(from.add(d.scale(1.5)), d, 15, i == (lines - 1) / 2 ? damage : damage * 0.8F));
      }

      ThanosWarSounds.play(this.level(), from, ThanosWarSounds.RUPTURE, 3.0F, 0.9F, 96.0);
      this.war.destruction.impact(from, 6.0F, 0.35F, AftermathCause.DESTRUCTION, false);
   }

   private void tickRifts() {
      ServerLevel level = this.level();
      for (Iterator<Rift> it = this.rifts.iterator(); it.hasNext(); ) {
         Rift r = it.next();
         if (--r.wait > 0) {
            continue;
         }

         r.wait = 2;
         r.at = r.at.add(r.dir.scale(1.7));
         Vec3 g = WarGround.below(level, r.at.add(0, 2.5, 0), 6);
         r.at = new Vec3(r.at.x, g.y, r.at.z);
         ThanosTargets.fx(level, ThanosFxS2CPacket.RUPTURE, this.c.thanos.getId(), r.steps, g, r.dir, 1.8F, 14);
         for (LivingEntity v : ThanosTargets.victims(level, this.c.thanos, g, 1.8)) {
            if (v.getY() > g.y + 3.0 || !r.hit.add(v.getId())) {
               continue;
            }

            if (ThanosTargets.hurt(this.c.thanos, v, r.damage, 1.0F)) {
               ThanosTargets.push(v, r.dir.scale(0.3).add(0, 0.85, 0));
            }
         }

         if (r.steps % 3 == 0) {
            this.war.destruction.crater(g.add(0, -0.3, 0), 1.4, ThanosWarRules.STRIKE_HARDNESS, 4);
         }

         if (--r.steps <= 0) {
            it.remove();
         }
      }
   }

   // ---- SPACE_BARRAGE / the volley's gates -----------------------------------------------------------
   /** Gates around Thanos (or around each frozen hero for the volley). */
   private void planGates(int n, double radius, boolean aroundTargets) {
      this.gates.clear();
      ServerLevel level = this.level();
      List<LivingEntity> aims = new ArrayList<>();
      if (aroundTargets && !this.frozen.isEmpty()) {
         aims.addAll(this.frozen);
      } else if (this.c.target != null) {
         aims.add(this.c.target);
      }

      for (int i = 0; i < n; i++) {
         LivingEntity aim = aims.isEmpty() ? null : aims.get(i % aims.size());
         Vec3 centre = aroundTargets && aim != null ? aim.position().add(0, 1.0, 0) : this.c.thanos.position().add(0, 2.4, 0);
         double a = Math.PI * 2.0 * i / n + (aroundTargets ? 0.4 : 0.0);
         double r = aroundTargets ? 7.0 : radius;
         Vec3 at = centre.add(Math.cos(a) * r, 1.2 + (i % 3) * 1.1, Math.sin(a) * r);
         Vec3 normal = aim == null ? this.c.facing() : aim.position().add(0, 1, 0).subtract(at).normalize();
         this.gates.add(new Gate(at, aim));
         ThanosTargets.fx(level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), -1, at, normal, 1.3F, aroundTargets ? 70 : this.c.telegraph + 34);
      }

      ThanosSounds.play(level, this.c.thanos.position(), ThanosSounds.SPACE_OPEN, 2.0F, 1.2F, 64.0);
   }

   private void fireGate(Gate g, float damage, int count) {
      LivingEntity aim = g.aim() != null && g.aim().isAlive() ? g.aim() : this.c.target;
      if (aim == null) {
         return;
      }

      for (int i = 0; i < count; i++) {
         Vec3 to = aim.position().add(0, aim.getBbHeight() * 0.5, 0);
         double ticks = to.distanceTo(g.at()) / SHARD_SPEED;
         // leads a moving hero a little: a dash still beats it
         Vec3 lead = to.add(aim.getDeltaMovement().scale(Math.min(10.0, ticks) * 0.6));
         Vec3 spread = new Vec3(this.war.random.nextGaussian(), this.war.random.nextGaussian(), this.war.random.nextGaussian()).scale(i * 0.35);
         Vec3 vel = lead.add(spread).subtract(g.at()).normalize().scale(SHARD_SPEED);
         Shard s = new Shard(this.id(), g.at(), vel, SHARD_LIFE, damage);
         this.shards.add(s);
         ThanosTargets.fx(this.level(), ThanosFxS2CPacket.SHARD, s.id, 0, s.pos, vel, (float)SHARD_LIFE, ThanosStone.SPACE.ordinal());
      }

      ThanosWarSounds.play(this.level(), g.at(), ThanosWarSounds.BARRAGE, 1.6F, 0.9F + this.war.random.nextFloat() * 0.3F, 64.0);
   }

   private void tickShards() {
      if (this.shards.isEmpty()) {
         return;
      }

      ServerLevel level = this.level();
      for (Iterator<Shard> it = this.shards.iterator(); it.hasNext(); ) {
         Shard s = it.next();
         Vec3 from = s.pos;
         Vec3 to = from.add(s.vel);
         boolean done = --s.life <= 0;
         LivingEntity hit = null;
         if (!done) {
            double best = Double.MAX_VALUE;
            for (LivingEntity v : ThanosTargets.victims(level, this.c.thanos, new AABB(from, to).inflate(0.55))) {
               double d = v.position().distanceToSqr(from);
               if (d < best) {
                  best = d;
                  hit = v;
               }
            }
         }

         if (hit != null) {
            ThanosTargets.hurt(this.c.thanos, hit, s.damage, 1.0F);
            ThanosTargets.push(hit, s.vel.normalize().scale(0.25));
            this.endShard(s, hit.position().add(0, hit.getBbHeight() * 0.5, 0));
            it.remove();
            continue;
         }

         BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.c.thanos));
         if (bh.getType() != HitResult.Type.MISS || done) {
            this.endShard(s, bh.getType() != HitResult.Type.MISS ? bh.getLocation() : to);
            it.remove();
            continue;
         }

         s.pos = to;
      }
   }

   private void endShard(Shard s, Vec3 at) {
      ThanosTargets.fx(this.level(), ThanosFxS2CPacket.SHARD, s.id, 1, at, s.vel, 0.0F, ThanosStone.SPACE.ordinal());
   }

   // ---- REALITY_REWRITE ------------------------------------------------------------------------------
   /** The ground at the target turns against him: lifted (or, a flyer, dragged down), then slammed. */
   private void rewrite(ThanosMove m, int t, float damage) {
      ServerLevel level = this.level();
      List<LivingEntity> in = ThanosTargets.victims(level, this.c.thanos, this.rewriteAt.add(0, 2, 0), REWRITE_RADIUS);
      if (t < 18) {
         for (LivingEntity v : in) {
            double h = v.getY() - this.rewriteAt.y;
            if (this.rewriteDown) {
               ThanosTargets.drag(v, new Vec3(v.getDeltaMovement().x * 0.5, -0.55, v.getDeltaMovement().z * 0.5));
            } else if (h < 4.5) {
               ThanosTargets.drag(v, new Vec3(v.getDeltaMovement().x * 0.4, 0.22, v.getDeltaMovement().z * 0.4));
            }
         }
      } else if (t == 18) {
         ThanosTargets.fx(level, ThanosFxS2CPacket.REWRITE, this.c.thanos.getId(), 2, this.rewriteAt, Vec3.ZERO, (float)REWRITE_RADIUS, 16);
         ThanosSounds.play(level, this.rewriteAt, ThanosSounds.REALITY_WARP, 2.2F, 0.6F, 72.0);
         for (LivingEntity v : in) {
            if (ThanosTargets.hurt(this.c.thanos, v, damage, 1.0F)) {
               ThanosTargets.drag(v, new Vec3(0.0, -1.3, 0.0));
            }
         }

         this.war.destruction.crater(this.rewriteAt.add(0, -0.4, 0), 2.0, ThanosWarRules.STRIKE_HARDNESS, 10);
         this.war.destruction.impact(this.rewriteAt, 6.0F, 0.3F, AftermathCause.DESTRUCTION, false);
      }
   }

   // ---- TIME_ANOMALY -----------------------------------------------------------------------------------
   /** The target is marked in time: two seconds later he is snapped back to where he stood (and hurt). */
   private void anomaly(float damage) {
      LivingEntity t = this.c.target;
      if (t == null) {
         return;
      }

      this.marked = t;
      this.markedAt = t.position();
      this.markLeft = ANOMALY_DELAY;
      this.markDamage = damage;
      ThanosTargets.fx(this.level(), ThanosFxS2CPacket.ANOMALY_MARK, this.c.thanos.getId(), t.getId(), this.markedAt, Vec3.ZERO, 1.0F, ANOMALY_DELAY);
      ThanosWarSounds.play(this.level(), this.markedAt, ThanosWarSounds.ANOMALY, 1.8F, 1.0F, 48.0);
   }

   private void tickAnomaly() {
      LivingEntity t = this.marked;
      if (t == null || --this.markLeft > 0) {
         return;
      }

      this.marked = null;
      if (!t.isAlive() || t.level() != this.level()) {
         return;
      }

      Vec3 was = t.position();
      ServerLevel level = this.level();
      // the snap back is a hold of its own: the ledger and the hero's protections decide
      boolean moved = false;
      if (this.war.holds.canHold(t, ThanosHolds.Kind.DRAG) && was.distanceToSqr(this.markedAt) > 4.0) {
         int granted = this.war.ledger.grant(t.getUUID(), ThanosControlLedger.MIN_TICKS, this.war.now());
         if (granted > 0) {
            t.teleportTo(this.markedAt.x, this.markedAt.y, this.markedAt.z);
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
            moved = true;
         }
      }

      ThanosTargets.fx(level, ThanosFxS2CPacket.ANOMALY_SNAP, this.c.thanos.getId(), t.getId(), this.markedAt, was.subtract(this.markedAt), moved ? 1.0F : 0.0F, 12);
      ThanosSounds.play(level, this.markedAt, ThanosSounds.TIME_REWIND, 1.6F, 1.3F, 48.0);
      ThanosTargets.hurt(this.c.thanos, t, this.markDamage, 1.0F);
   }

   // ---- MIND_COMPULSION --------------------------------------------------------------------------------
   /** The target walks to Thanos against his will (a drag hold), into the follow-up. */
   private void compulsion(ThanosMove m, int t) {
      LivingEntity target = this.c.target;
      if (t == 0) {
         if (target == null || !this.war.holds.canHold(target, ThanosHolds.Kind.DRAG)) {
            // the mind holds firm: a burst of Mind light instead
            this.c.mind.gaze(6.0F * this.c.scale());
            return;
         }

         int granted = this.war.holds.hold(target, ThanosHolds.Kind.DRAG, this.c.thanos, m.active, () -> this.c.thanos.position().add(this.c.facing().scale(2.4)),
            0.28);
         if (granted > 0) {
            this.compelled = target;
            ThanosTargets.fx(this.level(), ThanosFxS2CPacket.COMPULSION, this.c.thanos.getId(), target.getId(), this.c.hand(), Vec3.ZERO, 1.0F, granted);
            ThanosWarSounds.play(this.level(), target.position(), ThanosWarSounds.COMPULSION, 1.6F, 1.0F, 48.0);
         }

         return;
      }

      LivingEntity v = this.compelled;
      if (v != null && (!this.war.holds.held(v) || v.distanceToSqr(this.c.thanos) < 9.0)) {
         this.releaseCompelled();
      }
   }

   private void releaseCompelled() {
      LivingEntity v = this.compelled;
      this.compelled = null;
      if (v != null) {
         this.war.holds.release(v);
      }
   }

   // ---- SOUL_HARVEST ------------------------------------------------------------------------------------
   /** Souls rise from the ground and hunt the heroes; each that reaches one hurts him and feeds Thanos. */
   private void harvest(float damage, int perHero) {
      ServerLevel level = this.level();
      int made = 0;
      for (ServerPlayer p : this.war.heroes()) {
         if (!this.c.valid(p) || p.distanceToSqr(this.c.thanos) > 36.0 * 36.0) {
            continue;
         }

         for (int i = 0; i < perHero && made < 6; i++) {
            double a = this.war.random.nextDouble() * Math.PI * 2.0;
            Vec3 at = WarGround.below(level, p.position().add(Math.cos(a) * 6.0, 2.0, Math.sin(a) * 6.0), 8).add(0, 0.2, 0);
            Phantom ph = new Phantom(this.id(), at, p, PHANTOM_LIFE, damage);
            this.phantoms.add(ph);
            ThanosTargets.fx(level, ThanosFxS2CPacket.PHANTOM, ph.id, p.getId(), at, Vec3.ZERO, 1.0F, PHANTOM_LIFE);
            made++;
         }
      }
   }

   private void tickPhantoms() {
      ServerLevel level = this.level();
      for (Iterator<Phantom> it = this.phantoms.iterator(); it.hasNext(); ) {
         Phantom ph = it.next();
         LivingEntity t = ph.target;
         if (--ph.life <= 0 || !t.isAlive() || t.level() != level) {
            ThanosTargets.fx(level, ThanosFxS2CPacket.PHANTOM_POP, ph.id, 0, ph.pos, Vec3.ZERO, 0.0F, 0);
            it.remove();
            continue;
         }

         Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(ph.pos);
         double d = to.length();
         if (d < 1.1) {
            if (ThanosTargets.hurt(this.c.thanos, t, ph.damage, 1.0F)) {
               this.c.thanos.stoneHeal(this.c.thanos.getMaxHealth() * 0.004F);
            }

            ThanosTargets.fx(level, ThanosFxS2CPacket.PHANTOM_POP, ph.id, 1, ph.pos, Vec3.ZERO, 1.0F, 0);
            it.remove();
            continue;
         }

         ph.pos = ph.pos.add(to.scale(Math.min(PHANTOM_SPEED, d) / d));
      }
   }

   // ---- COMBO_TIME_VOLLEY -------------------------------------------------------------------------------
   /** Time stops around him, six gates open around the frozen heroes, time resumes into a charged volley. */
   private void timeVolley(ThanosMove m, int t, float damage) {
      ServerLevel level = this.level();
      if (t == 0) {
         this.frozen.clear();
         for (ServerPlayer p : this.war.heroes()) {
            if (this.c.valid(p) && p.distanceToSqr(this.c.thanos) < TIME_STOP_RADIUS * TIME_STOP_RADIUS
               && this.war.holds.hold(p, ThanosHolds.Kind.FREEZE, this.c.thanos, TIME_STOP_TICKS, p::position, 0.0) > 0) {
               this.frozen.add(p);
            }
         }

         ThanosTargets.fx(level, ThanosFxS2CPacket.TIME_STOP, this.c.thanos.getId(), -1, this.c.thanos.position(), Vec3.ZERO, (float)TIME_STOP_RADIUS,
            TIME_STOP_TICKS);
         ThanosWarSounds.play(level, this.c.thanos.position(), ThanosWarSounds.TIME_STOP, 3.0F, 1.0F, 96.0);
         return;
      }

      if (t == 6) {
         this.planGates(6, 7.0, true);
      }

      if (t == TIME_STOP_TICKS) {
         this.releaseFrozen();
         ThanosWarSounds.play(level, this.c.thanos.position(), ThanosWarSounds.VOLLEY, 3.0F, 1.0F, 96.0);
         for (Gate g : this.gates) {
            this.fireGate(g, damage, 3);
         }

         this.gates.clear();
      }
   }

   private void releaseFrozen() {
      for (LivingEntity e : this.frozen) {
         this.war.holds.release(e);
      }

      this.frozen.clear();
   }

   // ---- COMBO_MOON_FALL ---------------------------------------------------------------------------------
   /** A rock out of the sky gate, its landing marked on the ground before it hits. */
   private void moonRock(float damage) {
      ServerLevel level = this.level();
      List<ServerPlayer> heroes = this.war.heroes();
      Vec3 c = this.fightCentre();
      Vec3 aim;
      if (!heroes.isEmpty() && this.war.random.nextInt(3) != 0) {
         ServerPlayer p = heroes.get(this.war.random.nextInt(heroes.size()));
         aim = p.position().add(p.getDeltaMovement().scale(8.0)).add(this.war.random.nextGaussian() * 2.5, 0, this.war.random.nextGaussian() * 2.5);
      } else {
         double a = this.war.random.nextDouble() * Math.PI * 2.0;
         double d = 6.0 + this.war.random.nextDouble() * 20.0;
         aim = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
      }

      Vec3 at = WarGround.top(level, aim.x, aim.z);
      if (Math.abs(at.y - c.y) > 30.0) {
         at = WarGround.below(level, new Vec3(aim.x, c.y + 6.0, aim.z), 20);
      }

      Rock r = new Rock(this.id(), at, ROCK_FALL, damage, 3.6);
      this.rocks.add(r);
      Vec3 sky = c.add(0, 30, 0);
      ThanosTargets.fx(level, ThanosFxS2CPacket.MOON_WARN, r.id, 0, at, sky.subtract(at), (float)r.radius, ROCK_FALL);
   }

   private void tickRocks() {
      ServerLevel level = this.level();
      for (Iterator<Rock> it = this.rocks.iterator(); it.hasNext(); ) {
         Rock r = it.next();
         if (--r.left > 0) {
            continue;
         }

         it.remove();
         for (LivingEntity v : ThanosTargets.victims(level, this.c.thanos, r.at.add(0, 1, 0), r.radius)) {
            double d = v.position().distanceTo(r.at);
            float f = ThanosWarRules.strikeFalloff(d, r.radius + v.getBbWidth() * 0.5);
            if (f > 0.0F && ThanosTargets.hurt(this.c.thanos, v, r.damage * f, 1.0F)) {
               Vec3 out = v.position().subtract(r.at);
               out = new Vec3(out.x, 0.0, out.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : new Vec3(out.x, 0.0, out.z).normalize();
               ThanosTargets.push(v, out.scale(0.7 * f).add(0, 0.5 * f, 0));
            }
         }

         this.war.destruction.crater(r.at, 2.4, ThanosWarRules.STRIKE_HARDNESS, ThanosWarRules.ROCK_BLOCKS);
         this.war.destruction.impact(r.at, 8.0F, 0.5F, AftermathCause.METEOR, true);
         ThanosTargets.fx(level, ThanosFxS2CPacket.DEBRIS_IMPACT, r.id, 2, r.at, new Vec3(0, -1, 0), 3.0F, 0);
         ThanosWarSounds.play(level, r.at, ThanosWarSounds.ROCK_IMPACT, 4.0F, 0.8F + this.war.random.nextFloat() * 0.3F, 128.0);
      }
   }

   // ---- COMBO_INFINITY ------------------------------------------------------------------------------------
   /** All six stones in turn, each a stage of its own, each announced: Power, Space, Reality, Soul, Mind, Time. */
   private void infinity(ThanosMove m, int t, float damage) {
      ServerLevel level = this.level();
      int stage = t / 15;
      boolean first = t % 15 == 0;
      if (!first || stage > 5) {
         return;
      }

      ThanosStone[] order = {ThanosStone.POWER, ThanosStone.SPACE, ThanosStone.REALITY, ThanosStone.SOUL, ThanosStone.MIND, ThanosStone.TIME};
      ThanosStone stone = order[stage];
      ThanosTargets.fx(level, ThanosFxS2CPacket.INFINITY_STAGE, this.c.thanos.getId(), stone.ordinal(), this.c.hand(), Vec3.ZERO, 1.0F, 15);
      ThanosWarSounds.play(level, this.c.thanos.position(), ThanosWarSounds.INFINITY_STAGE, 2.4F, 0.8F + stage * 0.08F, 96.0);
      Vec3 at = this.c.thanos.position();
      switch (stone) {
         case POWER -> this.c.power.wave(at, damage * 0.6F, ThanosStoneRules.Reality.SHAPE_STAR);
         case SPACE -> {
            for (ServerPlayer p : this.war.heroes()) {
               if (this.c.valid(p) && p.distanceToSqr(this.c.thanos) < 24.0 * 24.0 && this.war.holds.canHold(p, ThanosHolds.Kind.DRAG)) {
                  this.war.holds.hold(p, ThanosHolds.Kind.DRAG, this.c.thanos, 12, () -> this.c.thanos.position().add(this.c.facing().scale(4.0)), 0.9);
               }
            }
         }
         case REALITY -> {
            this.c.reality.planZones();
            this.c.reality.spawnZones(damage * 0.15F);
         }
         case SOUL -> this.c.soul.nova(at.add(0, 1, 0), 9.0, damage * 0.7F);
         case MIND -> this.c.mind.pulse(at, damage * 0.4F);
         case TIME -> {
            this.c.time.dome(at, 9.0, 24);
            this.c.power.crush(at, 9.0, damage);
            this.war.destruction.crater(at.add(0, -0.5, 0), 3.0, ThanosWarRules.STRIKE_HARDNESS, 20);
            this.war.destruction.impact(at, 10.0F, 0.6F, AftermathCause.EXPLOSION, true);
         }
      }
   }
}
