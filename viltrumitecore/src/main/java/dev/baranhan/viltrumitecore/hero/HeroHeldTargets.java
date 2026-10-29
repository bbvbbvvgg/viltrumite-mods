package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Shared attachment lookup. A lease is not an attachment; the hero explicitly owns the target. */
public final class HeroHeldTargets {
   private HeroHeldTargets() {}
   @Nullable public static Player holder(Entity target) {
      if (target == null || !target.isAlive()) return null;
      for (Player player : target.level().players()) {
         HeroDefinition hero = HeroRegistry.get(player);
         if (player != target && player.isAlive() && hero != null && hero.heldTargetId(player) == target.getId()) return player;
      }
      return null;
   }

   /** The target is held by a hero whose hold anchors it ({@link HeroDefinition#anchorsHeldTarget}). */
   public static boolean anchored(Entity target) {
      Player holder = holder(target);
      HeroDefinition hero = holder == null ? null : HeroRegistry.get(holder);
      return hero != null && hero.anchorsHeldTarget(holder);
   }
}
