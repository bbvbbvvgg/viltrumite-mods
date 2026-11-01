package dev.baranhan.viltrumitecore.city.power.api;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.PowerGrid;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Entry point for other modules (Ultron, building collapse, aftermath, map scripts) into the city infrastructure
 * of Living City 3.0. Every call is a no-op in worlds without a prepared city (and outside its dimension), so
 * callers never need to check first. Server thread only. See {@code docs/city-infrastructure-integration.md}.
 */
public final class CityInfrastructure {
   private CityInfrastructure() {
   }

   @Nullable
   private static PowerGrid grid(@Nullable Level level) {
      if (level == null || level.isClientSide) {
         return null;
      }
      CityManager city = CityManager.of(level);
      return city == null ? null : city.power();
   }

   /** True when {@code level} hosts the living city and its power grid. */
   public static boolean available(@Nullable Level level) {
      return grid(level) != null;
   }

   /**
    * Reports damage to the infrastructure: the grid works out the surge, blackout, blown transformers and a
    * possible cascade around {@code at}. {@code severity} 0..1 (0.3 dims a block, 0.6 blows a transformer, 0.9 a
    * meteor strike), {@code radius} the physical radius of the hit in blocks. Returns the incident, or null when
    * the level has no city, the point is outside it or the city is being reset.
    */
   @Nullable
   public static InfrastructureIncident damage(ServerLevel level, Vec3 at, float radius, float severity, String cause, String source) {
      PowerGrid g = grid(level);
      return g == null ? null : g.damage(at.x, at.y, at.z, radius, severity, cause, source);
   }

   /**
    * Holds the city emergency at {@code stage} or higher for {@code leaseTicks} (refresh the lease while the threat
    * lasts; it expires on its own). One lease per {@code sourceId}; the strongest lease sets the sirens. When the
    * last active or critical lease ends the city sounds the all-clear and starts restoring power.
    */
   public static void raiseAlarm(ServerLevel level, String sourceId, AlarmStage stage, Vec3 at, float radius, int leaseTicks) {
      PowerGrid g = grid(level);
      if (g != null) {
         g.alarm().lease(sourceId, stage, at, radius, leaseTicks);
      }
   }

   /** Ends the lease of {@code sourceId} early. */
   public static void clearAlarm(ServerLevel level, String sourceId) {
      PowerGrid g = grid(level);
      if (g != null) {
         g.alarm().release(sourceId);
      }
   }

   public static AlarmStage alarmStage(Level level) {
      PowerGrid g = grid(level);
      return g == null ? AlarmStage.NONE : g.alarm().stage();
   }

   /** False when the grid cell of {@code pos} is dark or damaged (true outside the city). */
   public static boolean powered(Level level, BlockPos pos) {
      PowerGrid g = grid(level);
      return g == null || g.powered(pos.getX(), pos.getZ());
   }

   /** Share of the city's cells without power, 0..1. */
   public static float darkFraction(Level level) {
      PowerGrid g = grid(level);
      return g == null ? 0.0F : g.darkFraction();
   }

   /** Districts with at least one dark cell. */
   public static List<OutageInfo> outages(Level level) {
      PowerGrid g = grid(level);
      return g == null ? List.of() : g.outages();
   }

   /** The latest incidents (newest first, at most 16). */
   public static List<InfrastructureIncident> recentIncidents(Level level) {
      PowerGrid g = grid(level);
      return g == null ? List.of() : g.recentIncidents();
   }
}
