package dev.baranhan.viltrumitecore.worldevent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

public class WorldEventManager extends SavedData {
   private static final String NAME = "viltrumite_world_events";
   private final List<WorldEvent> active = new ArrayList<>();
   private long lastEventTime = -4611686018427387904L;

   public static WorldEventManager get(ServerLevel level) {
      return (WorldEventManager)level.getDataStorage().computeIfAbsent(WorldEventManager::load, WorldEventManager::new, "viltrumite_world_events");
   }

   public WorldEvent start(ServerLevel level, WorldEventType type, Vec3 origin, int warmupTicks) {
      if (this.isRunning(type)) {
         return null;
      } else {
         WorldEvent event = type.create();
         event.begin(level, origin, warmupTicks);
         this.active.add(event);
         this.lastEventTime = level.getGameTime();
         this.setDirty();
         return event;
      }
   }

   /** Operator start (command): the only way an event begins while automatic starts are off. */
   public WorldEvent startManual(ServerLevel level, WorldEventType type, Vec3 origin, int warmupTicks) {
      WorldEvent event = this.start(level, type, origin, warmupTicks);
      if (event != null) {
         event.markManual();
      }

      return event;
   }

   /**
    * Pure: does a running event survive? Only command-started events run. An event that a
    * scheduler started (a save from an older build) is finished and its NPCs despawned.
    */
   public static boolean keepsEvent(boolean manual) {
      return manual;
   }

   public boolean stop(ServerLevel level, WorldEventType type) {
      for (WorldEvent event : this.active) {
         if (event.type() == type && !event.isOver()) {
            event.finish(level);
            this.setDirty();
            return true;
         }
      }

      return false;
   }

   public void stopAll(ServerLevel level) {
      for (WorldEvent event : this.active) {
         if (!event.isOver()) {
            event.finish(level);
         }
      }

      this.active.clear();
      this.setDirty();
   }

   public boolean isRunning(WorldEventType type) {
      for (WorldEvent event : this.active) {
         if (event.type() == type && !event.isOver()) {
            return true;
         }
      }

      return false;
   }

   public List<WorldEvent> activeEvents() {
      return this.active;
   }

   public long lastEventTime() {
      return this.lastEventTime;
   }

   private void tick(ServerLevel level) {
      if (!this.active.isEmpty()) {
         Iterator<WorldEvent> it = this.active.iterator();

         while (it.hasNext()) {
            WorldEvent event = it.next();
            if (!keepsEvent(event.isManual())) {
               if (!event.isOver()) {
                  event.finish(level);
               }

               it.remove();
               continue;
            }

            event.tick(level);
            if (event.isOver()) {
               it.remove();
            }
         }

         this.setDirty();
      }
   }

   /**
    * Events never resume from a save: the squad NPCs are not saved with the world, and a
    * warmup that survived a restart would spawn them at server start without a command.
    * A restart ends every event; an operator starts it again by command.
    */
   public static WorldEventManager load(CompoundTag tag) {
      WorldEventManager manager = new WorldEventManager();
      manager.lastEventTime = tag.getLong("LastEventTime");
      return manager;
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();

      for (WorldEvent event : this.active) {
         if (!event.isOver()) {
            list.add(event.save());
         }
      }

      tag.put("Events", list);
      tag.putLong("LastEventTime", this.lastEventTime);
      return tag;
   }

   @EventBusSubscriber(
      modid = "viltrumitecore"
   )
   public static final class Hook {
      @SubscribeEvent
      public static void onLevelTick(LevelTickEvent event) {
         if (event.phase == Phase.END) {
            if (event.level instanceof ServerLevel serverLevel) {
               WorldEventManager.get(serverLevel).tick(serverLevel);
            }
         }
      }

      private Hook() {
      }
   }
}
