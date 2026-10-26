package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Live Saw Sphere barriers per level, one registry per logical side (each touched only by its
 * own thread: the server thread for server levels, the client thread for the client level).
 * {@code SawShellCollisionMixin} adds the active cells to every entity's collision list except
 * the caster's, so the server is authoritative and the client predicts its own player against
 * the same cells.
 */
public final class SawShells {
   /** One barrier: its shell, whether it blocks right now and who passes through. */
   public interface Barrier {
      SawShell shell();

      boolean solid();

      int casterId();
   }

   private static final Map<Level, List<Barrier>> SERVER = new WeakHashMap<>();
   private static final Map<Level, List<Barrier>> CLIENT = new WeakHashMap<>();

   private SawShells() {
   }

   private static Map<Level, List<Barrier>> side(Level level) {
      return level.isClientSide() ? CLIENT : SERVER;
   }

   public static void add(Level level, Barrier barrier) {
      side(level).computeIfAbsent(level, l -> new ArrayList<>(2)).add(barrier);
   }

   public static void remove(Level level, Barrier barrier) {
      Map<Level, List<Barrier>> map = side(level);
      List<Barrier> list = map.get(level);
      if (list == null) return;
      list.remove(barrier);
      if (list.isEmpty()) map.remove(level);
   }

   /** Client: the level went away (logout, dimension change). */
   public static void clearClient() {
      CLIENT.clear();
   }

   public static void clearServer() {
      SERVER.clear();
   }

   public static int count(Level level) {
      List<Barrier> list = side(level).get(level);
      return list == null ? 0 : list.size();
   }

   /** Mixin entry: the entity's collision list plus every solid shell cell near its sweep. Allocation-free when idle. */
   public static List<VoxelShape> withShells(Entity entity, List<VoxelShape> shapes, Vec3 movement) {
      Level level = entity.level();
      if (level == null) return shapes;
      Map<Level, List<Barrier>> map = side(level);
      if (map.isEmpty()) return shapes;
      List<Barrier> barriers = map.get(level);
      if (barriers == null || barriers.isEmpty()) return shapes;
      AABB box = entity.getBoundingBox();
      if (movement != null) box = box.expandTowards(movement);
      boolean[] copied = {false};
      List<VoxelShape> out = shapes;
      for (int i = 0; i < barriers.size(); i++) {
         Barrier barrier = barriers.get(i);
         if (!barrier.solid() || barrier.casterId() == entity.getId()) continue;
         out = barrier.shell().collect(box, out, copied);
      }
      return out;
   }

   /** A teleport from {@code from} to {@code to} would jump an intact part of a solid shell. */
   public static boolean blocksTeleport(Entity entity, Vec3 from, Vec3 to) {
      Level level = entity.level();
      List<Barrier> barriers = side(level).get(level);
      if (barriers == null) return false;
      for (Barrier barrier : barriers) {
         if (!barrier.solid() || barrier.casterId() == entity.getId()) continue;
         if (barrier.shell().crosses(from.x, from.y, from.z, to.x, to.y, to.z)) return true;
         // Head height too: a pearl landing just over the floor rim still jumps the dome.
         double h = entity.getBbHeight() * 0.9;
         if (barrier.shell().crosses(from.x, from.y + h, from.z, to.x, to.y + h, to.z)) return true;
      }
      return false;
   }
}
