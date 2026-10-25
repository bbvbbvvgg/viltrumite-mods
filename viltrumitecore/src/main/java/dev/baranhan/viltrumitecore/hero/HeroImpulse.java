package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Impulse rule for entities a hero power moves without hitting them (lifts,
 * waves). The living go through {@link HeroRegistry#allowsImpulse}; among the
 * non-living only items, XP orbs and falling blocks move — never projectiles,
 * vehicles, frames or armor stands. Pure class checks.
 */
public final class HeroImpulse {
   private HeroImpulse() {
   }

   public static boolean mayMoveNonLiving(Entity entity) {
      return entity != null && mayMoveNonLiving(entity.getClass());
   }

   public static boolean mayMoveNonLiving(Class<?> type) {
      return ItemEntity.class.isAssignableFrom(type) || ExperienceOrb.class.isAssignableFrom(type) || FallingBlockEntity.class.isAssignableFrom(type);
   }

   /** A living type a lift may move (then {@link HeroRegistry#allowsImpulse} decides per entity): armor stands never. */
   public static boolean mayLiftLiving(Class<?> type) {
      return LivingEntity.class.isAssignableFrom(type) && !ArmorStand.class.isAssignableFrom(type);
   }
}
