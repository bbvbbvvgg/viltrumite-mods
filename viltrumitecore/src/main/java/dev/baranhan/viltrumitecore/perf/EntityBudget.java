package dev.baranhan.viltrumitecore.perf;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Purely visual entities (suit debris) share one live cap that shrinks with server load. Debris within
 * {@link #NEAR} blocks of a player may fill 85% of it, far debris only 60%, so the pieces the player actually sees
 * survive the longest. Gameplay entities are never touched; entities loaded from disk always pass.
 * -Dviltrumitecore.perf.cosmeticEntities (600), .cosmeticEntitiesFloor (160); kill switch
 * -Dviltrumitecore.perf.entities=false. Server thread only.
 */
@Mod.EventBusSubscriber(modid = ViltrumiteCore.MOD_ID)
public final class EntityBudget {
   public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("viltrumitecore.perf.entities"));
   public static final int BASE = Math.max(1, Integer.getInteger("viltrumitecore.perf.cosmeticEntities", 600));
   public static final int FLOOR = Math.max(1, Math.min(BASE, Integer.getInteger("viltrumitecore.perf.cosmeticEntitiesFloor", 160)));
   public static final double NEAR = 48.0;
   private static final TickBudget BUDGET = PerfRegistry.register(new TickBudget("debris", BASE));
   private static int live;

   private EntityBudget() {
   }

   /** The live cap at this server pressure (0..1): BASE when healthy, FLOOR at full pressure. */
   public static int capacityFor(double pressure) {
      return Math.max(FLOOR, (int)Math.round(BASE * Degrade.scale(pressure, (double)FLOOR / BASE)));
   }

   /** Priority of a new cosmetic entity: near a player it matters more. */
   public static PerfPriority priority(boolean nearPlayer) {
      return nearPlayer ? PerfPriority.NORMAL : PerfPriority.LOW;
   }

   public static int live() {
      return live;
   }

   static boolean cosmetic(Entity entity) {
      return entity.getType() == ViltrumiteEntities.SUIT_DEBRIS.get();
   }

   @SubscribeEvent
   public static void onServerStarting(ServerStartingEvent event) {
      live = 0;
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.START) {
         BUDGET.reset(capacityFor(ServerPerf.pressure()));
         BUDGET.occupy(live);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onJoin(EntityJoinLevelEvent event) {
      Level level = event.getLevel();
      Entity entity = event.getEntity();
      if (level.isClientSide() || !cosmetic(entity)) {
         return;
      }

      if (ENABLED && !event.loadedFromDisk() && !BUDGET.tryTake(priority(level.getNearestPlayer(entity, NEAR) != null))) {
         event.setCanceled(true);
         return;
      }

      live++;
   }

   @SubscribeEvent
   public static void onLeave(EntityLeaveLevelEvent event) {
      if (!event.getLevel().isClientSide() && cosmetic(event.getEntity()) && live > 0) {
         live--;
      }
   }
}
