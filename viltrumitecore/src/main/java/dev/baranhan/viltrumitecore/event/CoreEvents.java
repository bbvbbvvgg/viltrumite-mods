package dev.baranhan.viltrumitecore.event;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.OpenRaceScreenS2CPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCosmeticsPlayer;
import dev.baranhan.viltrumitecore.worldevent.WorldEventManager;
import dev.baranhan.viltrumitecore.worldevent.WorldEventType;
import dev.baranhan.viltrumitecore.worldevent.WorldEvents;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
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
         ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)player;
         if (!corePlayer.hasChosenRace() && ViltrumiteCoreConfig.INSTANCE.shouldAskRace) {
            CoreMessages.sendToPlayer(new OpenRaceScreenS2CPacket(), player);
         }
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

   @SubscribeEvent
   public static void onLevelTick(LevelTickEvent event) {
      if (event.phase == Phase.END) {
         if (event.level instanceof ServerLevel level) {
            if (level.dimension() == Level.OVERWORLD) {
               if (ViltrumiteCoreConfig.INSTANCE.worldEventsAutoStart && !level.players().isEmpty()) {
                  WorldEventManager manager = WorldEventManager.get(level);
                  if (manager.activeEvents().isEmpty()) {
                     long cooldownTicks = (long)ViltrumiteCoreConfig.INSTANCE.worldEventCooldownMinutes * 60L * 20L;
                     if (level.getGameTime() - manager.lastEventTime() >= cooldownTicks) {
                        if (!(level.random.nextFloat() >= ViltrumiteCoreConfig.INSTANCE.worldEventChancePercent * 0.01F)) {
                           List<WorldEventType> candidates = new ArrayList<>();

                           for (WorldEventType type : WorldEvents.all()) {
                              if (!manager.isRunning(type)) {
                                 candidates.add(type);
                              }
                           }

                           if (!candidates.isEmpty()) {
                              WorldEventType chosen = candidates.get(level.random.nextInt(candidates.size()));
                              ServerPlayer anchor = (ServerPlayer)level.players().get(level.random.nextInt(level.players().size()));
                              manager.start(level, chosen, anchor.position(), chosen.defaultWarmupTicks());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

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
