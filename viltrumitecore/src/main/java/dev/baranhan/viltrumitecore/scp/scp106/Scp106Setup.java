package dev.baranhan.viltrumitecore.scp.scp106;

import com.mojang.serialization.Codec;
import dev.baranhan.viltrumitecore.scp.scp106.block.PocketBlocks;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketChunkGenerator;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * SCP-106 module wiring (docs/design/2026-11-02-scp106-design.md): blocks, the creature, the pocket dimension
 * generator, sounds, the natural spawner, the session watchdogs and the rules of the pocket (nothing can be broken,
 * placed, blown up or pearled out of there; dying there is not a way out).
 */
public final class Scp106Setup {
   public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, "viltrumitecore");

   static {
      GENERATORS.register("pocket", () -> PocketChunkGenerator.CODEC);
   }

   private Scp106Setup() {
   }

   public static void init(IEventBus modBus) {
      Scp106Sounds.init();
      PocketBlocks.register(modBus);
      Scp106Entities.register(modBus);
      GENERATORS.register(modBus);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onCommands);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onAboutToStart);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onStarted);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onPlayerTick);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onLogin);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onLogout);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Scp106Setup::onDeath);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onUseBlock);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onBreak);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onPlace);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onBucket);
      MinecraftForge.EVENT_BUS.addListener(Scp106Setup::onExplosion);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onPearl);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Scp106Setup::onChorus);
   }

   private static void onCommands(RegisterCommandsEvent event) {
      Scp106Commands.register(event.getDispatcher());
   }

   private static void onAboutToStart(ServerAboutToStartEvent event) {
      Scp106Pocket.onServerStart(event.getServer());
   }

   private static void onStarted(ServerStartedEvent event) {
      Scp106Pocket.onServerStarted(event.getServer());
   }

   private static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         Scp106Pocket.onServerTick(event.getServer());
         Scp106Spawner.tick(event.getServer());
      }
   }

   private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp) {
         Scp106Pocket.tickPlayer(sp);
      }
   }

   private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         Scp106Pocket.onLogin(sp);
      }
   }

   private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         Scp106Pocket.onLogout(sp);
      }
   }

   private static void onDeath(LivingDeathEvent event) {
      if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer sp && Scp106Pocket.onDeath(sp)) {
         event.setCanceled(true);
      }
   }

   private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer sp && Scp106Pocket.onUseBlock(sp, event.getPos())) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.SUCCESS);
      }
   }

   private static boolean locked(Player p) {
      return p != null && PocketWorld.is(p.level()) && !p.isCreative();
   }

   private static void onBreak(BlockEvent.BreakEvent event) {
      if (locked(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   private static void onPlace(BlockEvent.EntityPlaceEvent event) {
      if (event.getEntity() != null && PocketWorld.is(event.getEntity().level()) && !(event.getEntity() instanceof Player p && p.isCreative())) {
         event.setCanceled(true);
      }
   }

   private static void onBucket(FillBucketEvent event) {
      if (locked(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   private static void onExplosion(ExplosionEvent.Detonate event) {
      if (PocketWorld.is(event.getLevel())) {
         event.getAffectedBlocks().clear();
      }
   }

   private static void onPearl(EntityTeleportEvent.EnderPearl event) {
      if (locked(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   private static void onChorus(EntityTeleportEvent.ChorusFruit event) {
      if (event.getEntityLiving() instanceof Player p && locked(p)) {
         event.setCanceled(true);
      }
   }
}
