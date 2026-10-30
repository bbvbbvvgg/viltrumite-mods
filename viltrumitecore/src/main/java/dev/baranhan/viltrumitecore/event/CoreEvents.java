package dev.baranhan.viltrumitecore.event;

import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCosmeticsPlayer;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE
)
public class CoreEvents {
   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         // No hero choice screen on login: a new player is a Human until he uses Od Lagna's Wish.
      }
   }

   @SubscribeEvent
   public static void onPlayerClone(Clone event) {
      if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
         // Hero identity, session and all 18 ability slots are restored by
         // HeroEvents.onPlayerClone (canonical owner) — keep only chosen-race,
         // cosmetics and flight tuning here.
         ViltrumiteCorePlayer oldCore = (ViltrumiteCorePlayer)oldPlayer;
         ViltrumiteCorePlayer newCore = (ViltrumiteCorePlayer)newPlayer;
         newCore.setChosenRace(oldCore.hasChosenRace());
         ViltrumiteCosmeticsPlayer oldCosmetics = (ViltrumiteCosmeticsPlayer)oldPlayer;
         ViltrumiteCosmeticsPlayer newCosmetics = (ViltrumiteCosmeticsPlayer)newPlayer;
         newCosmetics.setViltrumiteSkin(oldCosmetics.getViltrumiteSkin());
         newCosmetics.setViltrumiteCape(oldCosmetics.getViltrumiteCape());
         newCosmetics.setViltrumiteModel(oldCosmetics.getViltrumiteModel());
         ViltrumiteFlightPlayer oldFlight = (ViltrumiteFlightPlayer)oldPlayer;
         ViltrumiteFlightPlayer newFlight = (ViltrumiteFlightPlayer)newPlayer;
         newFlight.setMaxFlightSpeed(oldFlight.getMaxFlightSpeed());
         newFlight.setThrottleSpeed(oldFlight.getThrottleSpeed());
      }
   }

   // No automatic world events: the Viltrumite squad (Conquest, Thula, Lucan, Vidor) appears only by
   // command (/viltrumite event start <id>). The old random scheduler lived here.

   @SubscribeEvent
   public static void onPlayerDeath(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide()) {
         if (event.getEntity() instanceof Player player
            && player.hasEffect((MobEffect)ViltrumiteEffects.SCOURGE_VIRUS.get())
            && player instanceof ViltrumiteCorePlayer corePlayer) {
            corePlayer.setViltrumite(true);
         }
      }
   }
}
