package dev.baranhan.viltrumitecore.client.render.scene;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

/**
 * Cinematic scenes that draw an entity themselves (World Erasure's dissolving target) hide it from
 * the level pass here. Each rule is a live predicate, so nothing stays hidden once its scene ends.
 */
public final class SceneEntityHide {
   private static final List<Predicate<Entity>> RULES = new CopyOnWriteArrayList<>();
   private SceneEntityHide() {}

   public static void register(Predicate<Entity> rule) {RULES.add(rule);}

   /** Is the entity drawn by a scene instead of the level this frame? A failing rule never hides. */
   public static boolean hidden(Entity entity) {
      if (entity == null || RULES.isEmpty()) return false;
      for (Predicate<Entity> rule : RULES) {
         try {
            if (rule.test(entity)) return true;
         } catch (RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Scene entity hide rule failed; entity stays visible", error);
         }
      }
      return false;
   }
}
