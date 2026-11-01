package dev.baranhan.viltrumitecore.worldevent.aftermath;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.hero.fx.CombatNoise;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Forge wiring of the city disaster aftermath (server side; safe on a dedicated server). */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class AftermathEvents {
   /** QA: {@code -Dviltrumitecore.aftermath.log=true} logs zone changes and the mean server cost every 30 s. */
   static final boolean QA_LOG = Boolean.getBoolean("viltrumitecore.aftermath.log");
   private static long qaNanos;
   private static int qaTicks;

   private static boolean listening;

   private AftermathEvents() {
   }

   @SubscribeEvent
   public static void onLevelLoad(LevelEvent.Load event) {
      if (event.getLevel() instanceof ServerLevel && !listening) {
         listening = true;
         CombatNoise.listen((level, at, radius) -> CityAftermath.hint(level, AftermathCause.HERO, at.x, at.y, at.z, radius));
      }
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         AftermathTracker.unload(level);
      }
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase != TickEvent.Phase.END || event.side.isClient() || !(event.level instanceof ServerLevel level) || !CityAftermath.enabled()) {
         return;
      }

      long start = QA_LOG ? System.nanoTime() : 0L;
      long now = level.getGameTime();
      AftermathTracker.flush(level, now);
      AftermathData data = AftermathData.get(level);
      if (now % 20 == 0) {
         CityAftermath.maintain(level, data, now);
      }

      if (now % 100 == 7 && !data.zones.isEmpty()) {
         CityManager city = CityManager.of(level);
         if (city != null && city.enabled()) {
            CityAftermath.citizens(level, data, city, now);
         }
      }

      AftermathNet.tick(level, data, now);
      if (QA_LOG) {
         qaNanos += System.nanoTime() - start;
         qaTicks++;
         if (now % 600 == 0) {
            CityAftermath.LOG.info("AMQA server {} zones={} outages={} mean={}us/tick over {} ticks", level.dimension().location(), data.zones.size(),
               data.outages.size(), qaTicks == 0 ? 0 : qaNanos / 1000L / qaTicks, qaTicks);
            qaNanos = 0L;
            qaTicks = 0;
         }
      }
   }

   /** Explosions name their cause before their blocks go (same tick). */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onExplosion(ExplosionEvent.Detonate event) {
      if (!(event.getLevel() instanceof ServerLevel level) || !CityAftermath.enabled() || event.getAffectedBlocks().isEmpty()) {
         return;
      }

      Explosion e = event.getExplosion();
      Vec3 at = e.getPosition();
      AftermathCause cause = causeOf(e.getDirectSourceEntity(), e.getIndirectSourceEntity());
      float r = (float)Math.min(24.0, 2.0 + Math.cbrt(event.getAffectedBlocks().size()) * 1.6);
      CityAftermath.hint(level, cause, at.x, at.y, at.z, r);
   }

   static AftermathCause causeOf(Entity direct, Entity owner) {
      for (Entity e : new Entity[]{direct, owner}) {
         if (e instanceof MeteorEntity) {
            return AftermathCause.METEOR;
         }

         if (e instanceof ChitauriLeviathan) {
            return AftermathCause.LEVIATHAN;
         }

         if (e instanceof ChitauriMob) {
            return AftermathCause.CHITAURI;
         }
      }

      if (owner instanceof Player || direct instanceof Player) {
         return AftermathCause.HERO;
      }

      if (direct != null && direct.getClass().getName().startsWith("dev.baranhan.viltrumitecore.")) {
         return AftermathCause.HERO; // repulsor blasts, micro missiles, rocket fists...
      }

      return AftermathCause.EXPLOSION;
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onBreak(BlockEvent.BreakEvent event) {
      if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
         AftermathTracker.handBroken(level, event.getPos());
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      AftermathNet.logout(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         AftermathNet.reset(p);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         AftermathNet.reset(p);
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      // the CombatNoise listener stays registered for the next integrated server
      AftermathNet.serverStopped();
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent event) {
      AftermathCommands.register(event.getDispatcher());
   }
}
