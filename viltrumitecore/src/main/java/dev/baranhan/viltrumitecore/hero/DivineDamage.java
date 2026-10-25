package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * Divine damage (spec §3.2): every damage type in the tag
 * {@code viltrumitecore:divine}. It ignores armor, shields, enchantments and
 * effects (vanilla tags) and the hero armor layers that stand in for armor
 * ({@link #piercesHeroArmor}), but not invulnerability: hero damage hooks
 * (Reinhard's Protection Acquisition and Phoenix) still see it. A future hero
 * deals it by adding its own damage type to the tag.
 */
public final class DivineDamage {
   public static final TagKey<DamageType> DIVINE_TYPES = TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "divine"));
   public static final ResourceKey<DamageType> DIVINE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "divine"));

   private DivineDamage() {
   }

   public static boolean isDivine(@Nullable DamageSource source) {
      return source != null && source.is(DIVINE_TYPES);
   }

   /**
    * A hero armor layer (an Iron Man mark, the Hulkbuster) steps aside like vanilla
    * armor: for divine damage, which {@code bypasses_armor} alone cannot express for
    * a custom layer, and for every source that bypasses invulnerability.
    */
   public static boolean piercesHeroArmor(DamageSource source) {
      return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || isDivine(source);
   }

   /** True when the damage type exists and is in the divine tag. */
   public static boolean isDivineType(ServerLevel level, ResourceKey<DamageType> type) {
      return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(type).map(holder -> holder.is(DIVINE_TYPES)).orElse(false);
   }

   public static DamageSource source(ServerLevel level, @Nullable Entity attacker, ResourceKey<DamageType> type) {
      return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
   }
}
