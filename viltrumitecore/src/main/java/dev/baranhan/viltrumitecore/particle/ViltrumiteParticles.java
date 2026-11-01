package dev.baranhan.viltrumitecore.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ViltrumiteParticles {
   public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "viltrumitecore");
   public static final RegistryObject<SimpleParticleType> GIANT_METEOR_SMOKE = PARTICLES.register("giant_meteor_smoke", () -> new SimpleParticleType(true));

   // Meteor Rain 2.0: trail smoke, impact billow and dust, fire smoke, flame licks, sparks and ash
   public static final RegistryObject<SimpleParticleType> METEOR_SMOKE = PARTICLES.register("meteor_smoke", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> METEOR_BILLOW = PARTICLES.register("meteor_billow", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> METEOR_DUST = PARTICLES.register("meteor_dust", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> FIRE_SMOKE = PARTICLES.register("fire_smoke", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> METEOR_FLAME = PARTICLES.register("meteor_flame", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> FIRE_FLARE = PARTICLES.register("fire_flare", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> METEOR_EMBER = PARTICLES.register("meteor_ember", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> METEOR_ASH = PARTICLES.register("meteor_ash", () -> new SimpleParticleType(true));

   /** Structural collapse: the dust wall that rolls along the streets after the slam (world/collapse). */
   public static final RegistryObject<SimpleParticleType> COLLAPSE_DUST = PARTICLES.register("collapse_dust", () -> new SimpleParticleType(true));
   /** Structural collapse: the column of dust rising over the pile. */
   public static final RegistryObject<SimpleParticleType> COLLAPSE_PLUME = PARTICLES.register("collapse_plume", () -> new SimpleParticleType(true));

   public static void register(IEventBus eventBus) {
      PARTICLES.register(eventBus);
   }
}
