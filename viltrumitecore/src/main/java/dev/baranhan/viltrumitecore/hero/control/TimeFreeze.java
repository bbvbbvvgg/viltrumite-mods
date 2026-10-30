package dev.baranhan.viltrumitecore.hero.control;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Shared "stopped time" seam: an entity held by a time-freeze source does not
 * tick at all (vanilla 1.20.3 tick-freeze style), so its velocity, AI/brain,
 * fuses, lifetimes and its owner's hero runtime simply wait and resume where
 * they were. Hooks: {@code TimeFreezeLevelMixin} (server entity ticks),
 * {@code TimeFreezePlayerMixin} (server player tick incl. hero runtimes and
 * PlayerTickEvent), {@code TimeFreezeMoveMixin} (rotation lock), client
 * {@code TimeFreezeClientLevelMixin}/{@code TimeFreezeMouseMixin}.
 *
 * <p>Nothing is persisted: server sources answer from their live state (a
 * frozen entity is matched by its current entity id, so a respawned or
 * re-joined player is never frozen by a stale record) and the client set is
 * rebuilt from the sources' packets and cleared on logout/level change.
 */
public final class TimeFreeze {
   /** A server-side owner of frozen entities (DIO's stopped-time fields). */
   public interface Source {
      boolean active();

      boolean freezes(Entity entity);

      /** {yaw, pitch} a frozen player must keep, or null. */
      @Nullable
      default float[] lockedView(Player player) {
         return null;
      }

      /**
       * Does this source stop the level itself (scheduled block/fluid ticks, block events,
       * random ticks, block entities)? DIO's local fields do not; a world pause does.
       */
      default boolean stopsWorld(net.minecraft.world.level.Level level) {
         return false;
      }
   }

   private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
   /** Client knowledge (client thread only): entity ids frozen by any received field. */
   private static final IntOpenHashSet CLIENT = new IntOpenHashSet();
   /**
    * Client knowledge of a world-wide stop (WorldPauseClient): every entity but {@link #clientWorldExempt}
    * (the pause owner, whose own cast still plays) is held, and the level's own clocks wait.
    */
   private static boolean clientWorld;
   private static int clientWorldExempt = Integer.MIN_VALUE;
   /** The local view is pinned (world pause), independent of whether the local player is "frozen". */
   private static boolean clientViewLock;

   private TimeFreeze() {
   }

   public static void register(Source source) {
      if (!SOURCES.contains(source)) {
         SOURCES.add(source);
      }
   }

   /** Does this entity skip its tick right now? Dead entities are never held. */
   public static boolean isFrozen(Entity entity) {
      if (!entity.isAlive()) {
         return false;
      }

      if (entity.level().isClientSide()) {
         return clientFrozen(entity.getId());
      }

      for (int i = 0; i < SOURCES.size(); i++) {
         Source source = SOURCES.get(i);
         if (source.active() && source.freezes(entity)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public static float[] lockedView(Player player) {
      if (player.level().isClientSide() || !player.isAlive()) {
         return null;
      }

      for (Source source : SOURCES) {
         if (source.active()) {
            float[] view = source.lockedView(player);
            if (view != null) {
               return view;
            }
         }
      }

      return null;
   }

   /** Is this level's own simulation stopped (block ticks, block entities, particles, weather clocks)? */
   public static boolean worldStopped(net.minecraft.world.level.Level level) {
      if (level.isClientSide()) {
         return clientWorld;
      }

      for (int i = 0; i < SOURCES.size(); i++) {
         Source source = SOURCES.get(i);
         if (source.active() && source.stopsWorld(level)) {
            return true;
         }
      }

      return false;
   }

   /** Pure: a frozen look differs enough from the lock to be corrected (wraps yaw). */
   public static boolean viewDrifted(float yaw, float pitch, float lockYaw, float lockPitch) {
      float dy = ((yaw - lockYaw) % 360.0F + 540.0F) % 360.0F - 180.0F;
      return Math.abs(dy) > 0.5F || Math.abs(pitch - lockPitch) > 0.5F;
   }

   // --- client side (fed by the sources' client packets) --------------------------------------

   public static void setClientFrozen(IntOpenHashSet ids) {
      CLIENT.clear();
      CLIENT.addAll(ids);
   }

   public static boolean clientFrozen(int entityId) {
      return clientWorld && entityId != clientWorldExempt || !CLIENT.isEmpty() && CLIENT.contains(entityId);
   }

   /** World pause (client thread): hold every entity but {@code exemptId}, and pin the local view. */
   public static void setClientWorld(boolean frozen, int exemptId, boolean viewLock) {
      clientWorld = frozen;
      clientWorldExempt = frozen ? exemptId : Integer.MIN_VALUE;
      clientViewLock = frozen && viewLock;
   }

   public static boolean clientWorldStopped() {
      return clientWorld;
   }

   public static boolean clientViewLocked() {
      return clientViewLock;
   }

   /** DIO's field set only; the world-pause flags have their own owner (WorldPauseClient). */
   public static void clearClient() {
      CLIENT.clear();
   }
}
