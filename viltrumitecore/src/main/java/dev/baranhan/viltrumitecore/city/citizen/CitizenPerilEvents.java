package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.world.collapse.CityCollapseEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * A building section starts to come down (issue #138): the people standing on it lose their
 * balance and scream, the floors around call for help, the street below panics. Lowest
 * priority, so a listener that cancels the collapse runs first.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class CitizenPerilEvents {
   private CitizenPerilEvents() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onCollapseStarted(CityCollapseEvent.Started event) {
      if (!event.isCanceled()) {
         CitizenPeril.onCollapseStarted(event.getLevel(), event.info());
      }
   }

   /** Peril statistics and the voice budget live as long as their server level (review of PR #145). */
   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (!event.getLevel().isClientSide() && event.getLevel() instanceof Level level) {
         CitizenPeril.forget(level.dimension());
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      CitizenPeril.forgetAll();
   }
}
