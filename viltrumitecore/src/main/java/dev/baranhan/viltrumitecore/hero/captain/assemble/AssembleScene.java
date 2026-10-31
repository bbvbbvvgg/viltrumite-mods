package dev.baranhan.viltrumitecore.hero.captain.assemble;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerAction;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.entity.avengers.QuinjetEntity;
import dev.baranhan.viltrumitecore.entity.avengers.TeamMoves;
import dev.baranhan.viltrumitecore.hero.captain.CaptainAbility;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * «Avengers, assemble!» (spec §5.7): the server timeline of the call, the arrivals (Quinjet drop of Natasha and
 * Clint, Iron Man braking in, Thor inside a lightning bolt, Hulk's leap), the walk to the circle, the circle
 * signatures, the fight (each Avenger's own brain) and the departures. No camera control, no cutscene: the
 * world keeps running and Steve only stands still until the circle breaks up. Every step has a deadline, so a
 * blocked path never stalls the scene; with no sky above, sky arrivals become ground / horizontal ones.
 */
public final class AssembleScene {
   private static final int MODE_FLY = 0, MODE_FALL = 1, MODE_RUN = 2, MODE_BOLT = 3, MODE_LEAP = 4;
   private static final int DEPART_QUINJET = 30;

   private final ServerPlayer owner;
   private final CaptainState state;
   private final ServerLevel level;
   private FormationPlanner.Plan plan;
   private final Map<AvengerKind, Member> members = new EnumMap<>(AvengerKind.class);
   @Nullable private QuinjetEntity quinjet;
   @Nullable private QuinjetEntity pickup;
   private Vec3 jetDir = Vec3.ZERO;
   private double jetHalf = 90;
   private double jetAlt;
   private Vec3 hover = Vec3.ZERO;
   private int t = -1;
   private int circleStart = -1;
   private int departStart = Integer.MAX_VALUE;
   private boolean pickupPlanned;
   private boolean finished;
   /** The joint moves of the fight (rework §15). */
   private final TeamMoves moves;

   AssembleScene(ServerPlayer owner, CaptainState state) {
      this.owner = owner;
      this.state = state;
      this.level = (ServerLevel)owner.level();
      this.moves = new TeamMoves(owner);
   }

   public ServerPlayer owner() {
      return this.owner;
   }

   public TeamMoves moves() {
      return this.moves;
   }

   public ServerLevel level() {
      return this.level;
   }

   /** Fight ticks left (0 outside the fight). */
   public int fightLeft() {
      return this.circleStart >= 0 && this.t >= this.circleStart + CaptainRules.CIRCLE_TICKS ? Math.max(0, this.departStart - this.t) : 0;
   }

   /** Avengers on the field. */
   public int alive() {
      int n = 0;
      for (Member m : this.members.values()) {
         if (m.e != null && m.e.isAlive() && !m.gone) {
            n++;
         }
      }
      return n;
   }

   public boolean inCeremony() {
      return this.circleStart < 0 || this.t < this.circleStart + CaptainRules.CIRCLE_TICKS;
   }

   boolean done() {
      return this.finished;
   }

   boolean has(AvengerEntity a) {
      Member m = this.members.get(a.kind());
      return m != null && m.e == a && !m.gone;
   }

   boolean has(QuinjetEntity q) {
      return q == this.quinjet || q == this.pickup;
   }

   // --- start ---

   void start() {
      Vec3 steve = this.owner.position();
      float yaw = this.owner.getYRot();
      this.plan = FormationPlanner.plan(this.level, steve, yaw);
      this.t = 0;
      this.state.start(CaptainMove.ASSEMBLE_CALL);
      HeroSpeech.ability(this.owner, CaptainAbility.ASSEMBLE.id());
      CaptainSounds.play(this.owner, CaptainSounds.ASSEMBLE, 1.4F, 1.0F);
      CaptainFx.near(this.level, steve, CaptainRules.OBSERVER_RANGE, CaptainFx.ASSEMBLE, this.owner.getId(), 0, steve, this.plan.center());
      for (AvengerKind k : AvengerKind.values()) {
         this.members.put(k, this.prepare(k));
      }
      if (this.plan.sky()) {
         this.jetDir = FormationPlanner.dir(yaw);
         this.jetHalf = this.loadedHalf(this.plan.center(), this.jetDir, 90);
         this.jetAlt = this.cruise(this.plan.center(), this.jetDir, this.jetHalf);
      }
   }

   private Member prepare(AvengerKind k) {
      Member m = new Member(k);
      Vec3 place = this.plan.place(k);
      Vec3 out = FormationPlanner.dir(this.plan.yawOf(k));
      Vec3 land = place;
      if (k != AvengerKind.THOR) {
         var pushed = FormationPlanner.stand(this.level, k, place.add(out.scale(k == AvengerKind.HULK ? 3.0 : 2.5)), this.owner.position());
         if (pushed.isPresent()) {
            land = pushed.get();
         }
      }
      m.land = land;
      boolean sky = this.plan.sky();
      switch (k) {
         case IRON_MAN -> {
            m.mode = MODE_FLY;
            m.t0 = CaptainRules.IRONMAN_START;
            m.t1 = CaptainRules.IRONMAN_LAND;
            if (sky) {
               m.from = land.add(out.scale(30)).add(0, Math.min(26, this.plan.ceiling() - 2), 0);
            } else {
               Vec3 d = this.openDir(land, out);
               m.from = land.add(d.scale(Math.max(3.0, FormationPlanner.clearAlong(this.level, land.add(0, 1.0, 0), d, 18) - 1.0))).add(0, 0.6, 0);
            }
         }
         case THOR -> {
            m.mode = MODE_BOLT;
            m.t0 = CaptainRules.THOR_BOLT;
            m.t1 = CaptainRules.THOR_BOLT;
            m.from = land;
         }
         case HULK -> {
            m.mode = MODE_LEAP;
            m.t0 = CaptainRules.HULK_START;
            m.t1 = CaptainRules.HULK_LAND;
            if (sky) {
               double half = this.loadedHalf(land, out, 40);
               double gx = land.x + out.x * half, gz = land.z + out.z * half;
               m.from = new Vec3(gx, FormationPlanner.ground(this.level, gx, gz, land.y), gz);
               m.apex = 16.0;
            } else {
               Vec3 d = this.openDir(land, out);
               m.from = land.add(d.scale(Math.max(3.0, FormationPlanner.clearAlong(this.level, land.add(0, 1.5, 0), d, 14) - 1.5)));
               m.apex = Mth.clamp(this.plan.ceiling() - k.height - 0.5, 0.3, 2.0);
            }
         }
         case BLACK_WIDOW, HAWKEYE -> {
            int delay = k == AvengerKind.HAWKEYE ? CaptainRules.HAWKEYE_JUMP_DELAY : 0;
            if (sky) {
               m.mode = MODE_FALL;
               m.t0 = CaptainRules.QUINJET_JUMP + delay;
               m.t1 = m.t0 + CaptainRules.DROP_TICKS;
            } else {
               m.mode = MODE_RUN;
               Vec3 back = FormationPlanner.dir(this.plan.yaw() + 180.0 + (k == AvengerKind.HAWKEYE ? 25 : -25));
               Vec3 d = FormationPlanner.clearAlong(this.level, land.add(0, 1.0, 0), back, 12) > 5 ? back : this.openDir(land, out);
               double run = Math.max(3.0, FormationPlanner.clearAlong(this.level, land.add(0, 1.0, 0), d, 12) - 1.0);
               m.from = land.add(d.scale(run));
               m.t1 = CaptainRules.FORM_START - 6 + delay;
               m.t0 = m.t1 - (int)Mth.clamp(run / 0.33, 8, 40);
            }
         }
      }
      return m;
   }

   /** The flat direction with the longest free line from {@code at} (out first, then 60° steps). */
   private Vec3 openDir(Vec3 at, Vec3 out) {
      Vec3 best = out;
      double bestD = -1;
      for (int i = 0; i < 6; i++) {
         Vec3 d = rotate(out, i * 60.0);
         double free = FormationPlanner.clearAlong(this.level, at.add(0, 1.0, 0), d, 18);
         if (free > bestD + 0.5) {
            bestD = free;
            best = d;
         }
      }
      return best;
   }

   private static Vec3 rotate(Vec3 v, double deg) {
      double r = Math.toRadians(deg);
      return new Vec3(v.x * Math.cos(r) - v.z * Math.sin(r), 0, v.x * Math.sin(r) + v.z * Math.cos(r));
   }

   /** Path half length (≤ max) whose ends are in entity-ticking chunks. */
   private double loadedHalf(Vec3 c, Vec3 d, double max) {
      double half = max;
      while (half > 24 && !(this.level.isPositionEntityTicking(BlockPos.containing(c.add(d.scale(half))))
         && this.level.isPositionEntityTicking(BlockPos.containing(c.add(d.scale(-half)))))) {
         half -= 8;
      }
      return half;
   }

   /** Quinjet cruise height: clear of the terrain along the line, a little above the circle. */
   private double cruise(Vec3 c, Vec3 d, double half) {
      double top = c.y;
      for (double s = -half; s <= half; s += 6) {
         Vec3 p = c.add(d.scale(s));
         top = Math.max(top, FormationPlanner.ground(this.level, p.x, p.z, c.y));
      }
      double alt = Math.max(c.y + 16, top + 12);
      return Math.min(alt, Math.min(c.y + 60, this.level.getMaxBuildHeight() - 8));
   }

   private Vec3 jetAt(int tick) {
      double v = FormationPlanner.quinjetV(tick);
      Vec3 c = this.plan.center();
      Vec3 p = c.add(this.jetDir.scale(FormationPlanner.quinjetS(v, this.jetHalf)));
      return new Vec3(p.x, this.jetAlt + 18.0 * v * v * (v < 0 ? 1.0 : 1.4), p.z);
   }

   // --- tick ---

   void tick() {
      if (this.finished) {
         return;
      }
      this.t++;
      int t = this.t;
      if (t == CaptainRules.CALL_TICKS && this.state.is(CaptainMove.ASSEMBLE_CALL)) {
         this.state.start(CaptainMove.ASSEMBLE_WAIT);
      }
      if (this.circleStart < 0 && this.inCeremonyGuard()) {
         this.state.start(CaptainMove.ASSEMBLE_WAIT); // something knocked Steve out of the pose
      }
      this.tickQuinjet(t);
      for (Member m : this.members.values()) {
         if (!m.gone) {
            this.tickMember(m, t);
         }
      }
      if (this.circleStart < 0) {
         boolean ready = true;
         for (Member m : this.members.values()) {
            if (!m.gone && (m.e == null || !m.ready)) {
               ready = false;
            }
         }
         if (CaptainRules.circleMayStart(t, ready)) {
            this.beginCircle(t);
         }
      } else {
         int c = t - this.circleStart;
         if (c < CaptainRules.CIRCLE_TICKS) {
            this.circleCues(c);
         } else if (c == CaptainRules.CIRCLE_TICKS) {
            this.beginFight();
         }
         if (t == this.departStart) {
            this.moves.cancel();
            this.beginDeparture();
         }
         if (t >= this.departStart) {
            this.tickPickup(t - this.departStart);
            boolean any = this.quinjet != null || this.pickup != null;
            for (Member m : this.members.values()) {
               any |= !m.gone;
            }
            if (!any || t >= this.departStart + CaptainRules.DEPART_TICKS) {
               this.finished = true;
            }
         } else if (c > CaptainRules.CIRCLE_TICKS && this.alive() == 0) {
            this.moves.cancel();
            this.finished = true; // the whole team retreated
         } else if (c > CaptainRules.CIRCLE_TICKS) {
            this.moves.tick(this.fighters());
         }
      }
   }

   private boolean inCeremonyGuard() {
      return this.t > CaptainRules.CALL_TICKS && (this.state.move == null || !this.state.move.ceremony());
   }

   private void tickQuinjet(int t) {
      if (!this.plan.sky()) {
         return;
      }
      if (t == CaptainRules.QUINJET_START) {
         this.quinjet = this.spawnJet(this.jetAt(t));
      }
      if (this.quinjet != null) {
         if (this.quinjet.isRemoved()) {
            this.quinjet = null;
            return;
         }
         this.quinjet.moveTo(this.jetAt(t + 1));
         if (t == CaptainRules.QUINJET_JUMP - 14) {
            this.quinjet.setRamp(true);
            CaptainSounds.play(this.quinjet, CaptainSounds.QUINJET_RAMP, 2.0F, 1.0F);
         }
         if (t == CaptainRules.QUINJET_JUMP + 26) {
            this.quinjet.setRamp(false);
         }
         if (t >= CaptainRules.QUINJET_END) {
            this.quinjet.discard();
            this.quinjet = null;
         }
      }
   }

   @Nullable
   private QuinjetEntity spawnJet(Vec3 at) {
      QuinjetEntity q = ViltrumiteEntities.QUINJET.get().create(this.level);
      if (q == null) {
         return null;
      }
      q.setOwner(this.owner);
      q.moveTo(at.x, at.y, at.z, (float)Math.toDegrees(Math.atan2(-this.jetDir.x, this.jetDir.z)), 0.0F);
      this.level.addFreshEntity(q);
      return q;
   }

   /** Where a jumper leaves the Quinjet: the open ramp at the back. */
   private Vec3 rampAt(int tick) {
      Vec3 p = this.jetAt(tick);
      return p.subtract(this.jetDir.scale(4.2)).add(0, -0.6, 0);
   }

   private void tickMember(Member m, int t) {
      if (m.e != null && m.e.isRemoved()) {
         m.gone = true;
         return;
      }
      if (m.departAt >= 0) {
         this.tickDepart(m, t - m.departAt);
         return;
      }
      if (this.circleStart >= 0) {
         int c = t - this.circleStart;
         if (c < CaptainRules.CIRCLE_TICKS && m.e != null) {
            m.e.scriptTo(this.plan.place(m.kind), this.plan.yawOf(m.kind));
         }
         return; // the fight belongs to the brains
      }
      if (t < m.t0) {
         return;
      }
      if (m.e == null) {
         if (t == m.t0 || m.mode != MODE_FALL) {
            this.spawn(m, t);
         }
         if (m.e == null) {
            return;
         }
      }
      AvengerEntity e = m.e;
      if (!m.landed) {
         double tau = (t - m.t0) / (double)Math.max(1, m.t1 - m.t0);
         Vec3 at;
         switch (m.mode) {
            case MODE_FLY -> at = m.from.lerp(m.land, FormationPlanner.brake(tau));
            case MODE_FALL -> {
               Vec3 p = m.from.lerp(m.land, tau);
               at = new Vec3(p.x, m.from.y + (m.land.y - m.from.y) * FormationPlanner.fall(tau), p.z);
            }
            case MODE_LEAP -> at = FormationPlanner.arc(m.from, m.land, tau, m.apex);
            default -> at = m.from.lerp(m.land, tau);
         }
         Vec3 d = m.land.subtract(m.from);
         float yaw = d.horizontalDistanceSqr() > 0.01 ? (float)Math.toDegrees(Math.atan2(-d.x, d.z)) : this.plan.yawOf(m.kind);
         e.scriptTo(at, yaw);
         if (m.mode == MODE_FLY && t % 3 == 0) {
            CaptainFx.around(e, CaptainFx.THRUST, 0, at, m.land.subtract(at).normalize().scale(-1));
         }
         if (t >= m.t1) {
            this.landed(m);
         }
         return;
      }
      // formation walk (after the landing pose)
      if (t < m.landedAt + 14 || t < CaptainRules.FORM_START - 8) {
         e.scriptTo(e.position(), e.getYRot());
         return;
      }
      Vec3 place = this.plan.place(m.kind);
      Vec3 d = place.subtract(e.position());
      double len = d.length();
      if (len < 0.12) {
         m.ready = true;
         e.scriptTo(place, this.plan.yawOf(m.kind));
         return;
      }
      if (t >= CaptainRules.FORM_DEADLINE - 1 && len > 0.12) {
         // late: a short dash onto the place
         CaptainFx.around(e, CaptainFx.THRUST, 1, e.position(), place);
         e.scriptTo(place, this.plan.yawOf(m.kind));
         m.ready = true;
         return;
      }
      double speed = m.kind == AvengerKind.HULK ? 0.2 : 0.17;
      float yaw = len > 0.6 ? (float)Math.toDegrees(Math.atan2(-d.x, d.z)) : this.plan.yawOf(m.kind);
      e.scriptTo(e.position().add(d.scale(Math.min(speed, len) / len)), Mth.approachDegrees(e.getYRot(), yaw, 25.0F));
   }

   private void spawn(Member m, int t) {
      AvengerEntity e = ViltrumiteEntities.AVENGER.get().create(this.level);
      if (e == null) {
         m.gone = true;
         return;
      }
      if (m.mode == MODE_FALL) {
         m.from = this.rampAt(t);
      }
      e.setup(this.owner, m.kind);
      e.setPhase(AvengerEntity.PHASE_ARRIVAL);
      Vec3 d = m.land.subtract(m.from);
      float yaw = d.horizontalDistanceSqr() > 0.01 ? (float)Math.toDegrees(Math.atan2(-d.x, d.z)) : this.plan.yawOf(m.kind);
      e.moveTo(m.from.x, m.from.y, m.from.z, yaw, 0.0F);
      e.scriptTo(m.from, yaw);
      m.e = e;
      this.level.addFreshEntity(e);
      switch (m.mode) {
         case MODE_FLY -> {
            e.setFlying(true);
            e.play(AvengerAction.ARRIVE);
            CaptainSounds.play(e, CaptainSounds.IRON_THRUST, 2.0F, 0.9F);
         }
         case MODE_FALL -> {
            e.play(AvengerAction.ARRIVE);
            CaptainSounds.play(e, CaptainSounds.ACROBAT, 1.0F, 1.0F);
         }
         case MODE_LEAP -> {
            e.play(AvengerAction.ARRIVE);
            CaptainSounds.play(e, CaptainSounds.HEAVY_LAND, 1.6F, 0.7F);
         }
         case MODE_BOLT -> {
            HeroFx.electricBolt(this.level, m.land, this.plan.sky() ? 40.0F : (float)Math.max(3.0, this.plan.ceiling()), 1.4F, CaptainSounds.THUNDER.get());
            HeroFx.electricRing(this.level, m.land.add(0, 0.1, 0), 3.0);
            CaptainFx.near(this.level, m.land, 64, CaptainFx.GROUND, e.getId(), 1, m.land, new Vec3(3.0, 0, 0));
            this.landed(m);
         }
         default -> {
         }
      }
   }

   private void landed(Member m) {
      AvengerEntity e = m.e;
      m.landed = true;
      m.landedAt = this.t;
      e.setFlying(false);
      e.scriptTo(m.land, e.getYRot());
      e.play(AvengerAction.LAND);
      switch (m.kind) {
         case IRON_MAN -> {
            CaptainSounds.play(e, CaptainSounds.IRON_LAND, 1.4F, 1.0F);
            CaptainFx.around(e, CaptainFx.GROUND, 2, m.land, new Vec3(2.2, 0, 0));
         }
         case HULK -> {
            CaptainSounds.play(e, CaptainSounds.HULK_SMASH, 1.8F, 0.85F);
            CaptainSounds.play(e, CaptainSounds.HEAVY_LAND, 1.6F, 0.8F);
            CaptainFx.around(e, CaptainFx.GROUND, 3, m.land, new Vec3(5.0, 0, 0));
         }
         case BLACK_WIDOW, HAWKEYE -> {
            if (m.mode == MODE_FALL) {
               CaptainSounds.play(e, CaptainSounds.LANDING, 1.0F, 1.1F);
               CaptainFx.around(e, CaptainFx.GROUND, 2, m.land, new Vec3(1.6, 0, 0));
            }
         }
         default -> {
         }
      }
   }

   // --- circle ---

   private void beginCircle(int t) {
      this.circleStart = t;
      this.departStart = t + CaptainRules.CIRCLE_TICKS + CaptainRules.FIGHT_TICKS;
      this.state.start(CaptainMove.ASSEMBLE_RAISE);
      for (Member m : this.members.values()) {
         if (m.gone) {
            continue;
         }
         if (m.e == null) {
            this.spawn(m, t);
            if (m.e == null) {
               continue;
            }
         }
         m.landed = true;
         m.ready = true;
         m.e.setFlying(false);
         m.e.setPhase(AvengerEntity.PHASE_CIRCLE);
         m.e.scriptTo(this.plan.place(m.kind), this.plan.yawOf(m.kind));
         m.e.play(AvengerAction.CIRCLE);
      }
   }

   private void circleCues(int c) {
      AvengerEntity iron = this.body(AvengerKind.IRON_MAN);
      AvengerEntity thor = this.body(AvengerKind.THOR);
      AvengerEntity hulk = this.body(AvengerKind.HULK);
      AvengerEntity widow = this.body(AvengerKind.BLACK_WIDOW);
      AvengerEntity hawk = this.body(AvengerKind.HAWKEYE);
      if (c == 8 && thor != null) {
         CaptainSounds.play(thor, CaptainSounds.HAMMER_SPIN, 1.2F, 1.0F);
      }
      if (c == 10) {
         CaptainSounds.play(this.owner, CaptainSounds.GUARD_UP, 1.0F, 0.9F);
         CaptainFx.near(this.level, this.owner.position(), CaptainRules.OBSERVER_RANGE, CaptainFx.ASSEMBLE, this.owner.getId(), 1, this.owner.position(), this.plan.center());
      }
      if (c == 12 && iron != null) {
         CaptainSounds.play(iron, CaptainSounds.FACEPLATE, 1.0F, 1.0F);
      }
      if ((c == 14 || c == 22) && hulk != null) {
         CaptainSounds.play(hulk, CaptainSounds.HULK_CHEST, 1.4F, c == 14 ? 1.0F : 0.92F);
      }
      if (c == 16 && widow != null) {
         CaptainSounds.play(widow, CaptainSounds.CATCH, 0.6F, 1.8F);
      }
      if (c == 20 && hawk != null) {
         CaptainSounds.play(hawk, CaptainSounds.BOW_DRAW, 1.0F, 1.0F);
      }
      if (c == 34 && hulk != null) {
         CaptainSounds.play(hulk, CaptainSounds.HULK_ROAR, 1.8F, 1.0F);
      }
      if (c == 44 && thor != null) {
         Vec3 top = thor.position().add(0, thor.getBbHeight() + 0.9, 0);
         HeroFx.electricArc(this.level, top.add(0, Math.min(14.0, this.plan.ceiling() - 1.0), 0), top);
         CaptainSounds.play(thor, CaptainSounds.THUNDER, 1.4F, 1.1F);
      }
   }

   private void beginFight() {
      if (this.state.move != null && this.state.move.ceremony()) {
         this.state.endMove();
      }
      for (Member m : this.members.values()) {
         if (m.gone || m.e == null) {
            continue;
         }
         m.e.setPhase(AvengerEntity.PHASE_FIGHT);
         m.e.play(AvengerAction.NONE);
         if (m.e.brain != null) {
            m.e.brain.begin();
         }
      }
   }

   /** The Avengers still in the fight (not gone, not leaving), for the joint moves. */
   private Map<AvengerKind, AvengerEntity> fighters() {
      Map<AvengerKind, AvengerEntity> out = new EnumMap<>(AvengerKind.class);
      for (Member m : this.members.values()) {
         if (!m.gone && m.e != null && m.departAt < 0) {
            out.put(m.kind, m.e);
         }
      }
      return out;
   }

   @Nullable
   private AvengerEntity body(AvengerKind k) {
      Member m = this.members.get(k);
      return m == null || m.gone || m.e == null ? null : m.e;
   }

   // --- departure ---

   /** Ends the fight now (graceful dismissal). */
   void departNow() {
      if (this.circleStart < 0 || this.inCeremony()) {
         this.finished = true;
         return;
      }
      if (this.t < this.departStart) {
         this.departStart = this.t + 1;
      }
   }

   private void beginDeparture() {
      boolean jumpers = false;
      for (Member m : this.members.values()) {
         if (!m.gone && m.e != null && m.departAt < 0 && m.kind.byQuinjet()) {
            jumpers = true;
         }
      }
      Vec3 c = this.owner.position();
      this.pickupPlanned = jumpers && this.plan.sky() && FormationPlanner.clearAbove(this.level, c.add(0, 2.0, 0), 20) >= 20;
      if (this.pickupPlanned) {
         this.hover = c.add(0, Math.max(10.0, FormationPlanner.ground(this.level, c.x, c.z, c.y) - c.y + 9.0), 0);
         this.jetDir = FormationPlanner.dir(this.owner.getYRot() + 90.0);
      }
      for (Member m : this.members.values()) {
         if (!m.gone && m.e != null && m.departAt < 0) {
            this.depart(m, this.pickupPlanned && m.kind.byQuinjet());
         }
      }
   }

   /** One Avenger retreats (lethal hit in the fight). */
   void retreat(AvengerEntity a) {
      Member m = this.members.get(a.kind());
      if (m != null && m.e == a && m.departAt < 0) {
         this.depart(m, false);
      }
   }

   private void depart(Member m, boolean byJet) {
      AvengerEntity e = m.e;
      m.departAt = this.t;
      m.byJet = byJet;
      m.departFrom = e.position();
      e.getNavigation().stop();
      e.setPhase(AvengerEntity.PHASE_DEPART);
      e.play(AvengerAction.DEPART);
      Vec3 away = e.position().subtract(this.owner.position());
      m.departDir = away.horizontalDistanceSqr() < 0.01 ? FormationPlanner.dir(e.getYRot()) : new Vec3(away.x, 0, away.z).normalize();
      if (m.kind == AvengerKind.HULK) {
         double free = FormationPlanner.clearAlong(this.level, e.position().add(0, 1.5, 0), m.departDir, 30);
         double apexRoom = FormationPlanner.clearAbove(this.level, e.position().add(0, e.getBbHeight(), 0), 20);
         m.departTo = e.position().add(m.departDir.scale(Math.max(2.0, free - 2.0)));
         m.apex = Mth.clamp(apexRoom - 0.5, 0.3, 14.0);
      }
   }

   private void tickDepart(Member m, int d) {
      AvengerEntity e = m.e;
      if (e == null) {
         m.gone = true;
         return;
      }
      switch (m.kind) {
         case IRON_MAN, THOR -> {
            int lift = m.kind == AvengerKind.THOR ? 12 : 6;
            if (d < lift) {
               e.scriptTo(e.position(), e.getYRot());
               if (d == 2) {
                  CaptainSounds.play(e, m.kind == AvengerKind.THOR ? CaptainSounds.HAMMER_SPIN : CaptainSounds.IRON_THRUST, 1.4F, 1.0F);
               }
               return;
            }
            e.setFlying(true);
            double k = d - lift;
            double room = FormationPlanner.clearAbove(this.level, e.position().add(0, e.getBbHeight(), 0), 3);
            Vec3 step = room > 1.0 ? new Vec3(m.departDir.x * 0.12 * k * 0.3, 0.25 + 0.06 * k, m.departDir.z * 0.12 * k * 0.3)
               : m.departDir.scale(0.3 + 0.05 * k);
            e.scriptTo(e.position().add(step), e.getYRot());
            if (d % 3 == 0) {
               CaptainFx.around(e, CaptainFx.THRUST, m.kind == AvengerKind.THOR ? 2 : 0, e.position(), step.scale(-1));
            }
            if (d > 44 || e.position().distanceToSqr(m.departFrom) > 60 * 60) {
               this.leave(m, false);
            }
         }
         case HULK -> {
            if (d < 8) {
               e.scriptTo(e.position(), (float)Math.toDegrees(Math.atan2(-m.departDir.x, m.departDir.z)));
               return;
            }
            double tau = (d - 8) / 26.0;
            e.scriptTo(FormationPlanner.arc(m.departFrom, m.departTo, tau, m.apex), e.getYRot());
            if (d == 8) {
               CaptainSounds.play(e, CaptainSounds.HEAVY_LAND, 1.6F, 0.75F);
               CaptainFx.around(e, CaptainFx.GROUND, 2, m.departFrom, new Vec3(3.0, 0, 0));
            }
            if (tau >= 1.0) {
               this.leave(m, true);
            }
         }
         default -> {
            if (!m.byJet) {
               e.scriptTo(e.position(), e.getYRot());
               if (d >= 20) {
                  this.leave(m, true);
               }
               return;
            }
            int jump = DEPART_QUINJET + 6 + (m.kind == AvengerKind.HAWKEYE ? 5 : 0);
            if (d < jump) {
               e.scriptTo(e.position(), e.getYRot());
               return;
            }
            Vec3 ramp = this.pickup != null ? this.pickup.position().subtract(FormationPlanner.dir(this.pickup.getYRot()).scale(3.5)) : this.hover;
            double tau = (d - jump) / 14.0;
            e.scriptTo(FormationPlanner.arc(m.departFrom, ramp, tau, 1.0), e.getYRot());
            if (d == jump) {
               e.play(AvengerAction.LEAP);
               CaptainFx.around(e, CaptainFx.GRAPPLE, 0, e.position().add(0, 1.2, 0), ramp);
               CaptainSounds.play(e, CaptainSounds.ACROBAT, 1.0F, 1.1F);
            }
            if (tau >= 1.0) {
               this.leave(m, false);
            }
         }
      }
   }

   private void leave(Member m, boolean puff) {
      if (m.e != null) {
         if (puff) {
            CaptainFx.around(m.e, CaptainFx.THRUST, 3, m.e.position(), Vec3.ZERO);
            CaptainSounds.play(m.e, CaptainSounds.DEPART, 0.8F, 1.0F);
         }
         m.e.vanish();
      }
      m.gone = true;
   }

   private void tickPickup(int d) {
      if (!this.pickupPlanned) {
         return;
      }
      Vec3 far = this.hover.add(this.jetDir.scale(-70)).add(0, 20, 0);
      Vec3 away = this.hover.add(this.jetDir.scale(80)).add(0, 30, 0);
      if (d == 0) {
         this.pickup = this.spawnJetAt(far);
      }
      QuinjetEntity q = this.pickup;
      if (q == null) {
         return;
      }
      if (q.isRemoved()) {
         this.pickup = null;
         return;
      }
      if (d <= DEPART_QUINJET) {
         q.moveTo(far.lerp(this.hover, FormationPlanner.brake((d + 1) / (double)DEPART_QUINJET)));
      } else if (d < 60) {
         q.moveTo(this.hover.add(0, Math.sin(d * 0.2) * 0.08, 0));
      } else {
         q.moveTo(this.hover.lerp(away, FormationPlanner.fall((d - 59) / 30.0)));
      }
      if (d == DEPART_QUINJET - 6) {
         q.setRamp(true);
         CaptainSounds.play(q, CaptainSounds.QUINJET_RAMP, 2.0F, 1.0F);
      }
      if (d == 58) {
         q.setRamp(false);
      }
      if (d >= CaptainRules.DEPART_TICKS - 1) {
         q.discard();
         this.pickup = null;
      }
   }

   @Nullable
   private QuinjetEntity spawnJetAt(Vec3 at) {
      QuinjetEntity q = ViltrumiteEntities.QUINJET.get().create(this.level);
      if (q == null) {
         return null;
      }
      q.setOwner(this.owner);
      q.moveTo(at.x, at.y, at.z, (float)Math.toDegrees(Math.atan2(-this.jetDir.x, this.jetDir.z)), 0.0F);
      this.level.addFreshEntity(q);
      return q;
   }

   // --- removal ---

   void memberGone(AvengerEntity a) {
      Member m = this.members.get(a.kind());
      if (m != null && m.e == a) {
         m.gone = true;
      }
   }

   /** Removes everything now (no departure). */
   void dispose(boolean unused) {
      this.moves.cancel();
      this.finished = true;
      for (Member m : this.members.values()) {
         if (m.e != null && !m.e.isRemoved()) {
            m.e.vanish();
         }
         m.gone = true;
      }
      if (this.quinjet != null) {
         this.quinjet.discard();
         this.quinjet = null;
      }
      if (this.pickup != null) {
         this.pickup.discard();
         this.pickup = null;
      }
   }

   private static final class Member {
      final AvengerKind kind;
      @Nullable AvengerEntity e;
      int mode;
      int t0;
      int t1;
      Vec3 from = Vec3.ZERO;
      Vec3 land = Vec3.ZERO;
      double apex;
      boolean landed;
      int landedAt;
      boolean ready;
      boolean gone;
      int departAt = -1;
      boolean byJet;
      Vec3 departFrom = Vec3.ZERO;
      Vec3 departDir = Vec3.ZERO;
      Vec3 departTo = Vec3.ZERO;

      Member(AvengerKind kind) {
         this.kind = kind;
      }
   }
}
