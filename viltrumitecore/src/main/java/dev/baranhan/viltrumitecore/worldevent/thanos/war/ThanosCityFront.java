package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import net.minecraft.world.phys.Vec3;

/**
 * The city side of the war through the city's own public API (no rewrite of it): the alarm leases
 * (warning on arrival, attack with the Black Order, critical from the Invasion on, all-clear at the
 * end) and the blackouts the bombardment causes ({@link CityInfrastructure#damage}). Where the city
 * grid is not available the clients play the war's own siren instead (the ALARM flag).
 */
public final class ThanosCityFront {
   public static final String ALARM_SOURCE = "thanos_war";
   private final ThanosWar war;
   private AlarmStage stage = AlarmStage.NONE;
   private int lease;
   private int blackout = ThanosWarRules.BLACKOUT_EVERY;
   private int blackouts;

   ThanosCityFront(ThanosWar war) {
      this.war = war;
   }

   public AlarmStage stage() {
      return this.stage;
   }

   public int blackouts() {
      return this.blackouts;
   }

   void begin() {
      this.set(AlarmStage.WARNING);
   }

   void onPhase(ThanosPhase phase) {
      this.set(switch (phase) {
         case ARRIVAL -> AlarmStage.WARNING;
         case BLACK_ORDER -> AlarmStage.ACTIVE;
         case INVASION, GAUNTLET -> AlarmStage.CRITICAL;
      });
      if (phase == ThanosPhase.INVASION) {
         this.blackout = 80;
      }
   }

   private void set(AlarmStage next) {
      this.stage = next;
      this.lease = 0;
   }

   void tick() {
      if (this.stage != AlarmStage.NONE && --this.lease <= 0) {
         this.lease = ThanosWarRules.ALARM_LEASE / 2;
         if (CityInfrastructure.available(this.war.level)) {
            CityInfrastructure.raiseAlarm(this.war.level, ALARM_SOURCE, this.stage, this.war.thanos.position(), ThanosWarRules.ALARM_RADIUS,
               ThanosWarRules.ALARM_LEASE);
         }
      }

      if (!this.war.over() && this.war.phase().atLeast(ThanosPhase.INVASION) && --this.blackout <= 0) {
         this.blackout = ThanosWarRules.BLACKOUT_EVERY + this.war.random.nextInt(120);
         this.blackout();
      }
   }

   /** A district goes dark near the fight (a hit substation, a cut line). */
   void blackout() {
      if (!CityInfrastructure.available(this.war.level)) {
         return;
      }

      Vec3 c = this.war.thanos.position();
      double a = this.war.random.nextDouble() * Math.PI * 2.0;
      double d = 10.0 + this.war.random.nextDouble() * 30.0;
      Vec3 at = WarGround.top(this.war.level, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
      CityInfrastructure.damage(this.war.level, at, ThanosWarRules.BLACKOUT_RADIUS, ThanosWarRules.BLACKOUT_SEVERITY, "thanos", "thanos_war_blackout");
      this.blackouts++;
   }

   /** No city grid here: the clients sound the war's own siren while the alarm stands. */
   boolean sirenFallback() {
      return this.stage != AlarmStage.NONE && !this.war.over() && !CityInfrastructure.available(this.war.level);
   }

   void allClear() {
      if (this.stage != AlarmStage.NONE) {
         this.stage = AlarmStage.NONE;
         if (CityInfrastructure.available(this.war.level)) {
            CityInfrastructure.clearAlarm(this.war.level, ALARM_SOURCE);
         }
      }
   }

   void end() {
      this.allClear();
   }
}
