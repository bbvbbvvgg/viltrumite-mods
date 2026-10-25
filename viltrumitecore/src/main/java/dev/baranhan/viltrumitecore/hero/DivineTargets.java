package dev.baranhan.viltrumitecore.hero;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;

/**
 * Divine beings (spec §3.3): players through {@link HeroDefinition#isDivine},
 * mobs through the entity tag {@code viltrumitecore:divine}. Absolute
 * invulnerability is a separate property ({@link HeroDefinition#isAbsolutelyInvulnerable}).
 */
public final class DivineTargets {
   public static final TagKey<EntityType<?>> DIVINE_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("viltrumitecore", "divine"));

   /**
    * How an execute-style power treats a target. Divine wins over absolute.
    * Nested so the pure rule loads without the registry statics above (unit tests).
    */
   public enum Verdict {
      DIVINE,
      ABSOLUTE,
      MORTAL;

      public static Verdict of(boolean divine, boolean absolute) {
         if (divine) {
            return DIVINE;
         }

         return absolute ? ABSOLUTE : MORTAL;
      }
   }

   private DivineTargets() {
   }

   public static Verdict verdict(boolean divine, boolean absolute) {
      return Verdict.of(divine, absolute);
   }

   public static boolean isDivine(Entity entity) {
      if (entity instanceof PartEntity<?> part) {
         return isDivine(part.getParent());
      }

      if (entity instanceof Player player) {
         return HeroRegistry.get(player).isDivine(player);
      }

      return entity != null && entity.getType().is(DIVINE_ENTITIES);
   }

   public static boolean isAbsolutelyInvulnerable(Entity entity) {
      if (entity instanceof PartEntity<?> part) {
         return isAbsolutelyInvulnerable(part.getParent());
      }

      return entity instanceof Player player && HeroRegistry.get(player).isAbsolutelyInvulnerable(player);
   }

   public static Verdict verdict(Entity entity) {
      return verdict(isDivine(entity), isAbsolutelyInvulnerable(entity));
   }
}
