package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexMarkEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who is a rabbit right now (§5.4), per logical side: the live {@link HexMarkEntity} of each hexed body,
 * keyed by the body's entity id (unique per JVM side). Size, damage, the action lock and the client
 * rabbit all read this; the mark registers itself when it joins / syncs and leaves when removed, so a
 * relog never leaves a rabbit behind.
 */
public final class HexMarks {
   private static final Map<Integer, HexMarkEntity> SERVER = new ConcurrentHashMap<>();
   private static final Map<Integer, HexMarkEntity> CLIENT = new ConcurrentHashMap<>();

   private HexMarks() {
   }

   private static Map<Integer, HexMarkEntity> side(boolean client) {
      return client ? CLIENT : SERVER;
   }

   public static void put(boolean client, int targetId, HexMarkEntity mark) {
      side(client).put(targetId, mark);
   }

   public static void remove(boolean client, int targetId, HexMarkEntity mark) {
      side(client).remove(targetId, mark);
   }

   @Nullable
   public static HexMarkEntity markOf(@Nullable Entity entity) {
      if (entity == null) {
         return null;
      }

      HexMarkEntity mark = side(entity.level().isClientSide).get(entity.getId());
      if (mark != null && (mark.isRemoved() || mark.level() != entity.level())) {
         return null;
      }

      return mark;
   }

   public static boolean hexed(@Nullable Entity entity) {
      return markOf(entity) != null;
   }

   /** ActionLocks provider (§7). */
   public static boolean locks(LivingEntity entity) {
      return hexed(entity);
   }

   /** Client world change: forget everything of the old level. */
   public static void clearClient() {
      CLIENT.clear();
   }
}
