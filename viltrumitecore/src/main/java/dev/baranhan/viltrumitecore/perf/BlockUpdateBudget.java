package dev.baranhan.viltrumitecore.perf;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The shared per-level block-update allowance of all destruction and restore queues. {@link #BASE} per tick on a
 * healthy server, falling smoothly to {@link #FLOOR} as MSPT climbs. Work that does not fit is deferred by its
 * queue, never dropped: buildings still fall and cities still heal, just over more ticks.
 */
@Mod.EventBusSubscriber(modid = ViltrumiteCore.MOD_ID)
public final class BlockUpdateBudget {
   public static final int BASE = Integer.getInteger("viltrumitecore.perf.blocksPerTick", 1536);
   public static final int FLOOR = Math.min(BASE, Integer.getInteger("viltrumitecore.perf.blocksFloor", 256));
   private static final Map<ResourceKey<Level>, TickBudget> BUDGETS = new HashMap<>();

   private BlockUpdateBudget() {
   }

   /** Capacity for a server pressure 0..1. Pure. */
   public static int capacityFor(double pressure) {
      if (BASE <= 0) {
         return 0;
      }

      return Math.max(FLOOR, (int)Math.round(BASE * Degrade.scale(pressure, (double)FLOOR / BASE)));
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase == TickEvent.Phase.START && event.level instanceof ServerLevel level) {
         budget(level.dimension()).reset(capacityFor(ServerPerf.pressure()));
      }
   }

   /** Grants up to {@code wanted} block updates this tick for work of the given priority. */
   public static int take(ServerLevel level, PerfPriority priority, int wanted) {
      return budget(level.dimension()).take(priority, wanted);
   }

   public static void refund(ServerLevel level, int unused) {
      budget(level.dimension()).refund(unused);
   }

   public static synchronized TickBudget budget(ResourceKey<Level> dimension) {
      return BUDGETS.computeIfAbsent(dimension, k -> PerfRegistry.register(new TickBudget("blocks " + k.location().getPath(), BASE)));
   }
}
