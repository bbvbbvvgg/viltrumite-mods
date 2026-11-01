package dev.baranhan.viltrumitecore.city.links;

import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureEvent;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureIncident;
import dev.baranhan.viltrumitecore.city.power.api.OutageInfo;
import dev.baranhan.viltrumitecore.world.collapse.CityCollapseEvent;
import dev.baranhan.viltrumitecore.world.collapse.CollapseInfo;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent;
import dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Glue between the three city systems (docs/city-*-integration.md), server side only:
 * <ul>
 * <li>structural collapse {@code Impact} → one aftermath zone ({@link CityAftermath#registerCollapse}) and one
 * grid incident ({@link CityInfrastructure#damage}); cause, position, scale and event id are kept;</li>
 * <li>while a collapse runs, the city burst stream does not hit the grid a second time for the same falling
 * blocks ({@link #collapseOwns}, read by {@code PowerGrid.onBurst});</li>
 * <li>grid outages and restores → {@link CityAftermath#setPower} with source {@code grid:<district>};
 * blown transformers → small {@code infrastructure} aftermath zones (aftermath never writes blocks, so no loop);</li>
 * <li>{@code /city reset}, restarts and missed events: {@link #reconcilePower} matches the aftermath outages to
 * the grid every 5 s.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class CityDisasterLinks {
   static final String GRID_PREFIX = "grid:";
   static final int RECONCILE_TICKS = 100;
   static final int MAX_TRANSFORMER_ZONES = 4;
   /** A grid cell is 48 x 48 blocks. */
   static final double CELL_AREA = 48.0 * 48.0;

   private static final Map<ResourceKey<Level>, List<CollapseInfo>> ACTIVE = new HashMap<>();
   private static final Set<Long> IMPACTED = new HashSet<>();

   private CityDisasterLinks() {
   }

   // ------------------------------------------------------------------ collapse

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onCollapseStarted(CityCollapseEvent.Started event) {
      if (!event.isCanceled()) {
         ACTIVE.computeIfAbsent(event.getLevel().dimension(), k -> new ArrayList<>()).add(event.info());
      }
   }

   @SubscribeEvent
   public static void onCollapseFinished(CityCollapseEvent.Finished event) {
      long id = event.info().eventId();
      List<CollapseInfo> list = ACTIVE.get(event.getLevel().dimension());
      if (list != null) {
         list.removeIf(i -> i.eventId() == id);
      }

      IMPACTED.remove(id);
   }

   @SubscribeEvent
   public static void onCollapseImpact(CityCollapseEvent.Impact event) {
      CollapseInfo info = event.info();
      if (!IMPACTED.add(info.eventId())) {
         return; // one impact, one zone and one grid hit
      }

      ServerLevel level = event.getLevel();
      String id = "collapse:" + info.eventId();
      float strength = Mth.clamp(info.severity().strength(), 0.0F, 1.0F);
      AftermathCause cause = aftermathCause(info.cause().id());
      CityAftermath.registerCollapse(level, AftermathEvent.of(level, id, info.center(), info.radius(), strength, cause, info.affectedBlocks()),
         box(info.landing()));
      CityInfrastructure.damage(level, Vec3.atCenterOf(info.center()), info.radius(), strength, "collapse", id + ":" + info.cause().id());
   }

   /** Collapse cause id → aftermath cause; ids aftermath does not know (command, secondary, custom) read as a collapse. */
   static AftermathCause aftermathCause(String collapseCause) {
      AftermathCause c = AftermathCause.of(collapseCause);
      return c == AftermathCause.OTHER ? AftermathCause.COLLAPSE : c;
   }

   @org.jetbrains.annotations.Nullable
   static BoundingBox box(@org.jetbrains.annotations.Nullable AABB a) {
      if (a == null) {
         return null;
      }

      return BoundingBox.fromCorners(BlockPos.containing(a.minX, a.minY, a.minZ), BlockPos.containing(a.maxX, a.maxY, a.maxZ));
   }

   /** True while a running collapse owns this point: its falling blocks are reported once, at {@code Impact}. */
   public static boolean collapseOwns(Level level, double x, double y, double z) {
      List<CollapseInfo> list = ACTIVE.get(level.dimension());
      if (list == null || list.isEmpty()) {
         return false;
      }

      for (CollapseInfo i : list) {
         if (i.inZone(x, y, z)) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         List<CollapseInfo> list = ACTIVE.remove(level.dimension());
         if (list != null) {
            list.forEach(i -> IMPACTED.remove(i.eventId()));
         }
      }
   }

   // ------------------------------------------------------------------ power grid

   @SubscribeEvent
   public static void onOutage(InfrastructureEvent.Outage event) {
      applyOutage(event.level(), event.outage());
   }

   @SubscribeEvent
   public static void onRestored(InfrastructureEvent.Restored event) {
      applyOutage(event.level(), event.outage());
   }

   @SubscribeEvent
   public static void onDamaged(InfrastructureEvent.Damaged event) {
      InfrastructureIncident inc = event.incident();
      List<BlockPos> tf = event.transformers();
      for (int i = 0; i < tf.size() && i < MAX_TRANSFORMER_ZONES; i++) {
         BlockPos p = tf.get(i);
         // a blown transformer sparks and smokes; the same incident id never counts twice
         CityAftermath.registerDamage(event.level(), AftermathEvent.of(event.level(), GRID_PREFIX + "transformer:" + inc.id() + ":" + i, p, 3.0F,
            Mth.clamp(inc.severity() * 0.4F, 0.15F, 0.35F), AftermathCause.INFRASTRUCTURE, List.of(p)));
      }
   }

   static void applyOutage(ServerLevel level, OutageInfo o) {
      String source = GRID_PREFIX + o.district();
      CityAftermath.setPower(level, source, o.center(), outageRadius(o.darkCells()), !o.dark());
   }

   /** Radius of a circle as large as the dark cells. */
   static float outageRadius(int darkCells) {
      return (float)Math.max(24.0, Math.sqrt(Math.max(1, darkCells) * CELL_AREA / Math.PI));
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % RECONCILE_TICKS == 0) {
         reconcilePower(level);
      }
   }

   /** Makes the {@code grid:} outages of the aftermath match the grid (after a restart, a reset or a missed event). */
   public static void reconcilePower(ServerLevel level) {
      Set<String> dark = new HashSet<>();
      for (OutageInfo o : CityInfrastructure.outages(level)) {
         if (o.dark()) {
            dark.add(GRID_PREFIX + o.district());
         }
      }

      for (String source : CityAftermath.outageSources(level)) {
         if (source.startsWith(GRID_PREFIX) && !dark.contains(source)) {
            CityAftermath.setPower(level, source, BlockPos.ZERO, 1.0F, true);
         }
      }

      for (OutageInfo o : CityInfrastructure.outages(level)) {
         if (o.dark() && !CityAftermath.outageSources(level).contains(GRID_PREFIX + o.district())) {
            applyOutage(level, o);
         }
      }
   }

   /** {@code /city reset}: the grid is cleared, so every grid outage of the aftermath goes too. */
   public static void onCityReset(ServerLevel level) {
      for (String source : CityAftermath.outageSources(level)) {
         if (source.startsWith(GRID_PREFIX)) {
            CityAftermath.setPower(level, source, BlockPos.ZERO, 1.0F, true);
         }
      }
   }
}
