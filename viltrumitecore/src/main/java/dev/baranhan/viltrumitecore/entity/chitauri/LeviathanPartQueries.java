package dev.baranhan.viltrumitecore.entity.chitauri;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * Hero area queries ask for {@code LivingEntity} in a box ({@code getEntitiesOfClass}), but a
 * Leviathan segment is a {@link PartEntity}, not a living entity, so a punch, a shockwave or a
 * beam that hits the tail would miss the whole creature. {@code mixin/LevelPartQueryMixin} calls
 * this after both level queries: every Leviathan segment inside the box brings its Leviathan
 * (once, when the query's type and predicate accept it, within the query's limit).
 */
public final class LeviathanPartQueries {
   private LeviathanPartQueries() {
   }

   public static <T extends Entity> void addTyped(Level level, EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> predicate,
      List<? super T> out, int limit) {
      if (out.size() >= limit) {
         return;
      }
      Collection<PartEntity<?>> parts = level.getPartEntities();
      if (parts.isEmpty()) {
         return;
      }
      for (PartEntity<?> part : parts) {
         ChitauriLeviathan parent = parentHit(part, box);
         if (parent == null) {
            continue;
         }
         T cast = test.tryCast(parent);
         if (cast != null && !out.contains(cast) && predicate.test(cast)) {
            out.add(cast);
            if (out.size() >= limit) {
               return;
            }
         }
      }
   }

   public static void addUntyped(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> out) {
      Collection<PartEntity<?>> parts = level.getPartEntities();
      if (parts.isEmpty()) {
         return;
      }
      for (PartEntity<?> part : parts) {
         ChitauriLeviathan parent = parentHit(part, box);
         // a query that accepted the segment itself hits through it (LeviathanPart.hurt): no second entry
         if (parent != null && parent != except && !out.contains(part) && !out.contains(parent) && predicate.test(parent)) {
            out.add(parent);
         }
      }
   }

   @Nullable
   private static ChitauriLeviathan parentHit(PartEntity<?> part, AABB box) {
      if (part instanceof LeviathanPart lp && lp.getBoundingBox().intersects(box)) {
         ChitauriLeviathan parent = lp.getParent();
         return parent != null && !parent.isRemoved() ? parent : null;
      }
      return null;
   }
}
