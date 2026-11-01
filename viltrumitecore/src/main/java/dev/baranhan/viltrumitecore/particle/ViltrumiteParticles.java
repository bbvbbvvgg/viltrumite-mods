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
   // City disaster aftermath (docs/city-aftermath-integration.md): ruin smoke styles and torn-wire sparks.
   public static final RegistryObject<SimpleParticleType> RUIN_SMOKE = PARTICLES.register("ruin_smoke", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> RUIN_PLUME = PARTICLES.register("ruin_plume", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> RUIN_HAZE = PARTICLES.register("ruin_haze", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> RUIN_DUST = PARTICLES.register("ruin_dust", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> WIRE_SPARK = PARTICLES.register("wire_spark", () -> new SimpleParticleType(true));

   public static void register(IEventBus eventBus) {
      PARTICLES.register(eventBus);
   }
}
