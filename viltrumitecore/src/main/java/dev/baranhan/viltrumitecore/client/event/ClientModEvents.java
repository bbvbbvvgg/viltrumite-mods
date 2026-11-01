package dev.baranhan.viltrumitecore.client.event;

import dev.baranhan.viltrumitecore.client.particle.GiantMeteorSmokeParticle;
import dev.baranhan.viltrumitecore.client.render.MeteorRenderer;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public class ClientModEvents {
   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ViltrumiteEntities.METEOR.get(), MeteorRenderer::new);
      // Iron Man projectiles are drawn as pixels by IronManCombatVfx.
      event.registerEntityRenderer(ViltrumiteEntities.REPULSOR_BLAST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.MICRO_MISSILE.get(), dev.baranhan.viltrumitecore.client.ironman.MissileRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.FLARE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.MJOLNIR.get(), dev.baranhan.viltrumitecore.client.thor.MjolnirRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.DIO_KNIFE.get(), dev.baranhan.viltrumitecore.client.dio.DioKnifeRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.CITIZEN.get(), dev.baranhan.viltrumitecore.client.city.CitizenRenderer::new);
      // Thanos (the six-stones world event): his own geo model, stone glows and the snap scene's hooks.
      event.registerEntityRenderer(ViltrumiteEntities.THANOS.get(), dev.baranhan.viltrumitecore.client.thanos.ThanosRenderer::new);
      dev.baranhan.viltrumitecore.client.ironman.mark.MarkVisuals.registerRenderers(event);
      dev.baranhan.viltrumitecore.client.ironman.mark.sig.SignatureVisuals.registerRenderers(event);
      // Sinclair's eight entity types (docs/design/2026-10-28-sinclair-design.md §11).
      dev.baranhan.viltrumitecore.client.sinclair.SinclairEntityRenderers.register(event);
   }

   @SubscribeEvent
   public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet((ParticleType)ViltrumiteParticles.GIANT_METEOR_SMOKE.get(), GiantMeteorSmokeParticle.Provider::new);
      event.registerSpriteSet(ViltrumiteParticles.METEOR_SMOKE.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Provider(s, dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Style.TRAIL));
      event.registerSpriteSet(ViltrumiteParticles.METEOR_BILLOW.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Provider(s, dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Style.BILLOW));
      event.registerSpriteSet(ViltrumiteParticles.METEOR_DUST.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Provider(s, dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Style.DUST));
      event.registerSpriteSet(ViltrumiteParticles.FIRE_SMOKE.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Provider(s, dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.Style.FIRE));
      event.registerSpriteSet(ViltrumiteParticles.METEOR_FLAME.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorFlameParticle.Provider(s, false));
      event.registerSpriteSet(ViltrumiteParticles.FIRE_FLARE.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorFlameParticle.Provider(s, true));
      event.registerSpriteSet(ViltrumiteParticles.METEOR_EMBER.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorEmberParticle.Provider(s, false));
      event.registerSpriteSet(ViltrumiteParticles.METEOR_ASH.get(), s -> new dev.baranhan.viltrumitecore.client.particle.MeteorEmberParticle.Provider(s, true));
   }
}
