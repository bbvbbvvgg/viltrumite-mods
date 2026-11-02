package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** The kaiju's two damage types (data/viltrumitecore/damage_type): crushed (steps, claws, tail, body) and the breath. */
public final class GodzillaDamage {
   public static final ResourceKey<DamageType> CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "godzilla"));
   public static final ResourceKey<DamageType> BREATH = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "godzilla_breath"));

   private GodzillaDamage() {
   }

   public static DamageSource crush(ServerLevel level, GodzillaEntity by) {
      return of(level, CRUSH, by);
   }

   public static DamageSource breath(ServerLevel level, GodzillaEntity by) {
      return of(level, BREATH, by);
   }

   private static DamageSource of(ServerLevel level, ResourceKey<DamageType> type, GodzillaEntity by) {
      return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), by);
   }
}
