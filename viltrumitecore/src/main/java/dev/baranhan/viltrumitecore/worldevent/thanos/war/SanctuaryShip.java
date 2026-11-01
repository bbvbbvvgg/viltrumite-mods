package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Sanctuary II on the server: its path ({@link ShipPath}: the arrival glide, the hover, the
 * departure; clients draw the same path from the synced start, hover and ticks) and its guns. A
 * strike is always announced first (a marker on the ground for {@code warning} ticks), then a lance
 * comes down a column at the point: limited damage with falloff, a small crater and the city's
 * grid, collapse ledger and aftermath hear the impact. From the Invasion on it also bombards the
 * streets around the fight, never within {@link ThanosWarRules#BOMBARD_SAFE} blocks of a hero.
 */
public final class SanctuaryShip {
   private static final class Strike {
      final int id;
      final Vec3 at;
      final float damage;
      final double radius;
      final boolean crater;
      int warn;

      Strike(int id, Vec3 at, float damage, double radius, boolean crater, int warn) {
         this.id = id;
         this.at = at;
         this.damage = damage;
         this.radius = radius;
         this.crater = crater;
         this.warn = warn;
      }
   }

   private final ThanosWar war;
   private int state = ShipPath.NONE;
   private int stateTick;
   private int delay = -1;
   private Vec3 start = Vec3.ZERO;
   private Vec3 hover = Vec3.ZERO;
   private final List<Strike> strikes = new ArrayList<>();
   private boolean bombard;
   private int bombardTimer = 60;
   private int ids;
   private int fired;

   SanctuaryShip(ThanosWar war) {
      this.war = war;
   }

   // ---- path ---------------------------------------------------------------------------------------
   /** Plans the arrival: the hover point off to one side of {@code around}, the start far beyond it. */
   void start(Vec3 around) {
      double angle = this.war.random.nextDouble() * Math.PI * 2.0;
      Vec3 side = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
      this.hover = new Vec3(around.x + side.x * ThanosWarRules.SHIP_OFFSET, around.y + ThanosWarRules.SHIP_ALTITUDE, around.z + side.z * ThanosWarRules.SHIP_OFFSET);
      // it comes in over the horizon, a quarter turn off the line to the city, and sweeps in
      double come = angle + Math.PI * (0.35 + 0.3 * this.war.random.nextDouble());
      Vec3 from = new Vec3(Math.cos(come), 0.0, Math.sin(come));
      this.start = this.hover.add(from.scale(ThanosWarRules.SHIP_START_DISTANCE)).add(0.0, ThanosWarRules.SHIP_START_RISE, 0.0);
      this.state = ShipPath.NONE;
      this.stateTick = 0;
      this.delay = ThanosWarRules.SHIP_DELAY;
   }

   public int state() {
      return this.state;
   }

   public int stateTick() {
      return this.stateTick;
   }

   public Vec3 start() {
      return this.start;
   }

   public Vec3 hover() {
      return this.hover;
   }

   public Vec3 position() {
      return ShipPath.at(this.state, this.stateTick, this.start, this.hover);
   }

   public float yaw() {
      return ShipPath.yaw(this.state, this.stateTick, this.start, this.hover);
   }

   public boolean present() {
      return this.state == ShipPath.ARRIVING || this.state == ShipPath.HOVER || this.state == ShipPath.LEAVING;
   }

   /** Over the city with a free gun: SHIP_CALL can be answered. */
   public boolean ready() {
      return this.state == ShipPath.HOVER && this.strikes.size() <= ThanosWarRules.MAX_STRIKES - 3;
   }

   public boolean bombarding() {
      return this.bombard && this.state == ShipPath.HOVER && !this.war.over();
   }

   public int strikes() {
      return this.strikes.size();
   }

   public int fired() {
      return this.fired;
   }

   void bombard(boolean on) {
      this.bombard = on;
      this.bombardTimer = 80;
   }

   void onPhase(ThanosPhase phase) {
      if (phase == ThanosPhase.GAUNTLET && this.state == ShipPath.HOVER) {
         ThanosWarSounds.play(this.war.level, this.position(), ThanosWarSounds.SHIP_HORN, 8.0F, 0.8F, 420.0);
      }
   }

   /** Ticks 0.. of the path; the strikes' warnings run down and fire. */
   void tick(ThanosEvent.Stage stage) {
      if (this.delay > 0 && --this.delay == 0) {
         this.state = ShipPath.ARRIVING;
         this.stateTick = 0;
         ThanosWarSounds.play(this.war.level, this.war.thanos.position(), ThanosWarSounds.SHIP_ARRIVAL, 8.0F, 1.0F, 480.0);
         this.war.sync(true);
      }

      if (this.present()) {
         this.stateTick++;
      }

      if (this.state == ShipPath.ARRIVING && this.stateTick >= ThanosWarRules.SHIP_ARRIVAL) {
         this.state = ShipPath.HOVER;
         this.stateTick = 0;
         ThanosWarSounds.play(this.war.level, this.position(), ThanosWarSounds.SHIP_HORN, 8.0F, 1.0F, 420.0);
         this.war.say(ThanosWar.VOICE_THANOS, this.war.thanos, "ship", dev.baranhan.viltrumitecore.hero.speech.HeroLine.Priority.ABILITY, 0, false);
         this.war.sync(true);
      } else if (this.state == ShipPath.LEAVING && this.stateTick >= ThanosWarRules.SHIP_LEAVE) {
         this.state = ShipPath.GONE;
         this.stateTick = 0;
         this.war.sync(true);
      }

      this.tickStrikes();
      if (this.bombarding() && stage == ThanosEvent.Stage.FIGHT && --this.bombardTimer <= 0) {
         ThanosPhase phase = this.war.phase();
         this.bombardTimer = ThanosWarRules.bombardEvery(phase) + this.war.random.nextInt(60);
         this.volley(phase == ThanosPhase.GAUNTLET ? 2 : 1 + this.war.random.nextInt(2));
      }
   }

   /** Off it goes (victory, the snap scene over): turns away and climbs out of sight. */
   void leave() {
      this.cancelStrikes();
      this.bombard = false;
      if (this.state == ShipPath.ARRIVING || this.state == ShipPath.HOVER) {
         this.hover = this.position();
         this.state = ShipPath.LEAVING;
         this.stateTick = 0;
         ThanosWarSounds.play(this.war.level, this.hover, ThanosWarSounds.SHIP_DEPART, 8.0F, 1.0F, 480.0);
      } else if (this.state == ShipPath.NONE) {
         this.delay = -1;
         this.state = ShipPath.GONE;
      }

      this.war.sync(true);
   }

   void cancelStrikes() {
      if (!this.strikes.isEmpty()) {
         ThanosTargets.fx(this.war.level, ThanosFxS2CPacket.CLEAR_WAR, 0, 0, this.war.thanos.position(), Vec3.ZERO, 0.0F, 0);
      }

      this.strikes.clear();
   }

   void clear() {
      this.strikes.clear();
      this.bombard = false;
      this.delay = -1;
      this.state = ShipPath.GONE;
   }

   // ---- guns -----------------------------------------------------------------------------------------
   /**
    * Announces a strike at {@code aim} (snapped to the floor below, a hero on the ground: at his
    * feet). Returns false when every gun is busy or the ship is not over the city.
    */
   public boolean strike(Vec3 aim, int warning, float damage, double radius, boolean crater) {
      if (!this.present() || this.state == ShipPath.LEAVING || this.strikes.size() >= ThanosWarRules.MAX_STRIKES) {
         return false;
      }

      Vec3 at = WarGround.below(this.war.level, aim, 48);
      Strike s = new Strike(this.ids++, at, damage, radius, crater, Math.max(8, warning));
      this.strikes.add(s);
      ThanosTargets.fx(this.war.level, ThanosFxS2CPacket.SHIP_WARN, s.id, 0, at, Vec3.ZERO, (float)radius, s.warn);
      ThanosWarSounds.play(this.war.level, at, ThanosWarSounds.SHIP_CHARGE, 2.5F, 0.9F + this.war.random.nextFloat() * 0.2F, 72.0);
      return true;
   }

   /** Where a lance aimed at {@code target} should land: his feet on the ground, below him in the air. */
   public static Vec3 aimAt(LivingEntity target, int lead) {
      Vec3 v = target.getDeltaMovement();
      return target.position().add(v.x * lead, 0.0, v.z * lead);
   }

   private void tickStrikes() {
      Iterator<Strike> it = this.strikes.iterator();
      while (it.hasNext()) {
         Strike s = it.next();
         if (--s.warn > 0) {
            continue;
         }

         it.remove();
         this.fire(s);
      }
   }

   private void fire(Strike s) {
      Vec3 gun = ShipPath.gun(this.position(), this.yaw(), s.at);
      this.fired++;
      ThanosTargets.fx(this.war.level, ThanosFxS2CPacket.SHIP_LANCE, s.id, 0, s.at, gun.subtract(s.at), (float)s.radius, 14);
      ThanosWarSounds.play(this.war.level, s.at, ThanosWarSounds.SHIP_LANCE, 5.0F, 0.9F + this.war.random.nextFloat() * 0.2F, 200.0);
      // the column: on the ground and anyone flying above the marker
      double r = s.radius;
      AABB box = new AABB(s.at.x - r, s.at.y - 2.0, s.at.z - r, s.at.x + r, Math.max(s.at.y + 4.0, gun.y), s.at.z + r);
      for (LivingEntity v : ThanosTargets.victims(this.war.level, this.war.thanos, box)) {
         double dx = v.getX() - s.at.x;
         double dz = v.getZ() - s.at.z;
         double d = Math.sqrt(dx * dx + dz * dz);
         float f = ThanosWarRules.strikeFalloff(d, r + v.getBbWidth() * 0.5);
         if (f <= 0.0F) {
            continue;
         }

         if (ThanosTargets.hurt(this.war.thanos, v, s.damage * f, 1.0F)) {
            Vec3 out = d < 0.01 ? new Vec3(0.0, 0.0, 0.0) : new Vec3(dx / d, 0.0, dz / d);
            ThanosTargets.push(v, out.scale(0.6 * f).add(0.0, v.getY() > s.at.y + 3.0 ? -0.8 : 0.45 * f, 0.0));
         }
      }

      if (s.crater) {
         this.war.destruction.crater(s.at, ThanosWarRules.STRIKE_CRATER, ThanosWarRules.STRIKE_HARDNESS, ThanosWarRules.STRIKE_BLOCKS);
      }

      this.war.destruction.impact(s.at, (float)(r * 1.6), 0.6F, AftermathCause.EXPLOSION, true);
   }

   /** The bombardment: {@code count} strikes on the streets around the fight, away from every hero. */
   private void volley(int count) {
      Vec3 c = this.war.thanos.position();
      double safe = ThanosWarRules.STRIKE_RADIUS + ThanosWarRules.BOMBARD_SAFE;
      for (int i = 0; i < count; i++) {
         for (int tries = 0; tries < 12; tries++) {
            double a = this.war.random.nextDouble() * Math.PI * 2.0;
            double d = 12.0 + this.war.random.nextDouble() * (ThanosWarRules.BOMBARD_RANGE - 12.0);
            Vec3 p = WarGround.top(this.war.level, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
            if (Math.abs(p.y - c.y) > 40.0 || near(p, safe)) {
               continue;
            }

            this.strike(p, ThanosWarRules.strikeWarning(this.war.phase()) + 10, ThanosWarRules.STRIKE_DAMAGE * 0.75F, ThanosWarRules.STRIKE_RADIUS, true);
            break;
         }
      }
   }

   private boolean near(Vec3 p, double safe) {
      for (ServerPlayer h : this.war.heroes()) {
         double dx = h.getX() - p.x;
         double dz = h.getZ() - p.z;
         if (dx * dx + dz * dz < safe * safe) {
            return true;
         }
      }

      return false;
   }

   /** The teleport beam (the Black Order arriving or leaving): from the hub to {@code ground}. */
   public void beam(Vec3 ground, int ticks) {
      Vec3 from = this.present() ? ShipPath.emitter(0, this.position(), this.yaw()) : ground.add(0.0, 140.0, 0.0);
      ThanosTargets.fx(this.war.level, ThanosFxS2CPacket.BEAM, 0, 0, ground, from.subtract(ground), 1.6F, ticks);
      ThanosWarSounds.play(this.war.level, ground, ThanosWarSounds.SHIP_BEAM, 4.0F, 1.0F, 160.0);
   }

   /** Debug helper: the strikes waiting, for the commands. */
   @Nullable
   public Vec3 lastStrike() {
      return this.strikes.isEmpty() ? null : this.strikes.get(this.strikes.size() - 1).at;
   }

   static float lerp(float t, float a, float b) {
      return Mth.lerp(t, a, b);
   }
}
