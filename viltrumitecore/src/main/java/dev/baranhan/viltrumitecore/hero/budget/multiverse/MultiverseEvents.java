package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server tick, death, logout and shutdown hooks of the multiverse modes. */
@Mod.EventBusSubscriber(modid = "viltrumitecore")
public final class MultiverseEvents {
   private MultiverseEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         MultiverseCollapse.tick(event.getServer());
         MultiverseBossRush.tick(event.getServer());
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof MultiverseFighter fighter && !fighter.level().isClientSide) {
         MultiverseBossRush.onDeath(fighter);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity().getServer() != null) {
         MultiverseBossRush.stop(event.getEntity().getServer(), event.getEntity().getUUID(), null);
      }
   }

   @SubscribeEvent
   public static void onStopping(ServerStoppingEvent event) {
      MultiverseCollapse.stop(event.getServer(), null);
      MultiverseBossRush.stopAll(event.getServer());
   }

   @Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.MOD)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void onAttributes(EntityAttributeCreationEvent event) {
         event.put(ViltrumiteEntities.MULTIVERSE_FIGHTER.get(), MultiverseFighter.createAttributes().build());
      }
   }
}
