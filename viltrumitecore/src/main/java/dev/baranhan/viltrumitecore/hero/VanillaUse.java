package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.entity.PartEntity;

/**
 * RMB arbitration between a hero and vanilla "use" (Thor B-B1): who owns a
 * right click aimed at this hit with these hands. A hero that opts in
 * ({@link HeroDefinition#yieldsToVanillaUse}) takes RMB only when vanilla
 * would do nothing useful with it: both hands empty (an item in either hand
 * is used by vanilla: food, potions, bows, shields, pearls, buckets, leads,
 * blocks, armor, ...), not aimed at a block (use / place / open / doors /
 * buttons / chests), and not aimed at an entity that has its own empty-hand
 * interaction or that the hero must not hit (non-living entities such as
 * boats, minecarts, item frames, a lying Mjölnir; villagers, mounts, armor
 * stands, the player's own pets; allies and anything else {@link HeroTargets}
 * refuses). No per-item exception table: the rule is about which side owns
 * the click. Both sides; the decision itself is {@link #decide} (pure).
 */
public final class VanillaUse {
   /** Who owns the right click. */
   public enum Owner {
      HERO,
      VANILLA
   }

   /** What the crosshair is on, reduced to what the arbitration needs. */
   public enum Aim {
      NOTHING,
      BLOCK,
      /** An entity the hero may fight and that has no empty-hand vanilla interaction. */
      HOSTILE_ENTITY,
      /** An entity vanilla interacts with (or the hero must not hit). */
      INTERACTIVE_ENTITY
   }

   private VanillaUse() {
   }

   /** Pure: the owner of a right click. */
   public static Owner decide(Aim aim, boolean handsEmpty) {
      if (!handsEmpty) {
         return Owner.VANILLA;
      }

      return switch (aim) {
         case NOTHING, HOSTILE_ENTITY -> Owner.HERO;
         case BLOCK, INTERACTIVE_ENTITY -> Owner.VANILLA;
      };
   }

   /** Does vanilla own this right click for {@code player}? */
   public static boolean vanillaOwns(Player player, @Nullable HitResult hit) {
      boolean handsEmpty = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
      return decide(aim(player, hit), handsEmpty) == Owner.VANILLA;
   }

   static Aim aim(Player player, @Nullable HitResult hit) {
      if (hit == null || hit.getType() == HitResult.Type.MISS) {
         return Aim.NOTHING;
      } else if (hit.getType() == HitResult.Type.BLOCK) {
         return Aim.BLOCK;
      } else if (hit instanceof EntityHitResult entityHit) {
         return interactive(player, entityHit.getEntity()) ? Aim.INTERACTIVE_ENTITY : Aim.HOSTILE_ENTITY;
      }

      return Aim.NOTHING;
   }

   /** An entity with its own empty-hand vanilla interaction, or one the hero must not hit. */
   public static boolean interactive(Player player, @Nullable Entity entity) {
      Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
      if (!(target instanceof LivingEntity)) {
         // Boats, minecarts, item frames, leash knots, a lying Mjölnir (its own pick-up), ...
         return true;
      }

      if (target instanceof AbstractVillager || target instanceof ArmorStand || target instanceof AbstractHorse || target instanceof Saddleable) {
         return true;
      }

      if (target instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
         return true;
      }

      return !HeroTargets.canHit(player, target);
   }
}
