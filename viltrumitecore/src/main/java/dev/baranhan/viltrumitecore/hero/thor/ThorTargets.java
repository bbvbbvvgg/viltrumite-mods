package dev.baranhan.viltrumitecore.hero.thor;

import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import dev.baranhan.viltrumitecore.hero.HeroTargets;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** "Any enemy within 4 blocks" for the MMB resolver; runs on both sides. */
public final class ThorTargets {
   private ThorTargets() {
   }

   /** Nearest living, attackable, non-allied entity within {@code radius} of the player, or null. */
   @Nullable
   public static LivingEntity nearestEnemy(Player player, double radius) {
      AABB box = player.getBoundingBox().inflate(radius);
      return player.level().getEntitiesOfClass(LivingEntity.class, box, entity -> isEnemy(player, entity)
            && entity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= radius * radius)
         .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
   }

   /** The shared hero target rule (HeroTargets.canHit): no allies, own pets, own mount, protected players. */
   public static boolean isEnemy(Player player, LivingEntity entity) {
      return HeroTargets.canHit(player, entity);
   }
}
