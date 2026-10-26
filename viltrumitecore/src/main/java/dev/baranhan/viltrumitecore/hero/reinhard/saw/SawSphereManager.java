package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Saw Sphere scenes per server level (like {@code WorldCutManager}): ticked on the END half of the
 * level tick, dropped (items flushed, tickets released) when the level unloads or the server stops.
 * Pearls, chorus fruit and endermen may not teleport across an intact shell.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class SawSphereManager {
   private static final Map<ServerLevel, SawSphereManager> INSTANCES = new WeakHashMap<>();
   private final List<SawSphereScene> scenes = new ArrayList<>();

   public static SawSphereManager get(ServerLevel level) {
      return INSTANCES.computeIfAbsent(level, l -> new SawSphereManager());
   }

   public void submit(SawSphereScene scene) {
      this.scenes.add(scene);
   }

   public int size() {
      return this.scenes.size();
   }

   public List<SawSphereScene> scenes() {
      return List.copyOf(this.scenes);
   }

   public void tick() {
      if (this.scenes.isEmpty()) return;
      for (SawSphereScene scene : List.copyOf(this.scenes)) {
         if (scene.tick()) this.scenes.remove(scene);
      }
   }

   public void dropAll() {
      for (SawSphereScene scene : List.copyOf(this.scenes)) scene.end(true);
      this.scenes.clear();
   }

   public static void clearAll() {
      for (SawSphereManager manager : List.copyOf(INSTANCES.values())) manager.dropAll();
      INSTANCES.clear();
      SawShells.clearServer();
   }

   private static void forget(ServerPlayer player) {
      for (SawSphereManager manager : INSTANCES.values()) {
         for (SawSphereScene scene : manager.scenes) scene.forget(player);
      }
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         SawSphereManager manager = INSTANCES.remove(level);
         if (manager != null) manager.dropAll();
      }
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) forget(player);
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) forget(player);
   }

   @SubscribeEvent
   public static void onTeleport(EntityTeleportEvent event) {
      if (event instanceof EntityTeleportEvent.TeleportCommand || event instanceof EntityTeleportEvent.SpreadPlayersCommand) return;
      if (event.getEntity().level().isClientSide() || SawShells.count(event.getEntity().level()) == 0) return;
      if (SawShells.blocksTeleport(event.getEntity(), event.getPrev(), event.getTarget())) event.setCanceled(true);
   }
}
