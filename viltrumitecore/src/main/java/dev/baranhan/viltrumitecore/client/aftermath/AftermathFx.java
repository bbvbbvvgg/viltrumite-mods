package dev.baranhan.viltrumitecore.client.aftermath;

import dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What a ruin looks like, from the emitters the scanner found and the zone's mix: smoke pouring out of
 * windows and breaches (each source with its own strength, pulse, drift and burn-out time, the biggest ruins
 * with tall plumes), dust over fresh rubble, crumbs trickling from broken ceilings and edges, glowing embers
 * and small flames on smouldering debris, a hot glow over magma, and low haze lying in a wrecked quarter.
 * Bounded by one particle budget per tick for all zones (scaled by the particle setting) and by distance:
 * far ruins only smoke, crumbs and embers are drawn near the camera.
 */
final class AftermathFx {
   /** Particles per tick, all ruins together, at the "all" particle setting. */
   static final int BUDGET = 72;
   static final double DRAW = 224.0;
   static final double DETAIL = 72.0;
   static final double SMOKE_DETAIL = 140.0;
   private static int budget;

   private AftermathFx() {
   }

   static int budgetFor(ParticleStatus status) {
      return switch (status) {
         case ALL -> BUDGET;
         case DECREASED -> BUDGET / 2;
         case MINIMAL -> BUDGET / 6;
      };
   }

   static void tick(Minecraft mc, List<ClientZone> nearestFirst, Vec3 cam, RandomSource random, int clock) {
      budget = budgetFor(mc.options.particles().get());
      for (ClientZone zn : nearestFirst) {
         if (budget <= 0) {
            return;
         }

         double edge = zn.distance - zn.radius;
         if (edge > DRAW) {
            continue;
         }

         if (!zn.surged && zn.age < 100 && zn.channel(AftermathRules.DUST) > 0.2F && edge < SMOKE_DETAIL) {
            zn.surged = true;
            surge(mc, zn, random);
         }

         float far = edge < 48.0 ? 1.0F : edge < 120.0 ? 0.7F : 0.45F;
         for (ClientZone.Emitter e : zn.emitters) {
            if (budget <= 0) {
               return;
            }

            double d = Math.sqrt(e.distanceSq(cam.x, cam.y, cam.z));
            emitter(mc, zn, e, d, far, random, clock);
         }

         haze(mc, zn, cam, random);
      }
   }

   /** Pure: smoke puffs per tick of one source (channel 0..1, its strength and life, zone progress 0..1, the pulse phase). */
   static float smokeRate(float smoke, float strength, float life, float progress, float pulse) {
      // a source burns out at its own time and fades over the next 12 % of the ruin's life
      float gate = progress <= life ? 1.0F : Mth.clamp(1.0F - (progress - life) / 0.12F, 0.0F, 1.0F);

      return Mth.clamp(smoke * strength * gate * pulse * 0.55F, 0.0F, 2.0F);
   }

   private static int count(float rate, RandomSource random) {
      int n = (int)rate;
      return n + (random.nextFloat() < rate - n ? 1 : 0);
   }

   private static void emitter(Minecraft mc, ClientZone zn, ClientZone.Emitter e, double d, float far, RandomSource random, int clock) {
      float t = clock * 0.05F + e.phase;
      // each source breathes on its own: slow swell, quicker flutter, the odd gust
      float pulse = 0.65F + 0.25F * Mth.sin(t * 0.7F) + 0.1F * Mth.sin(t * 2.3F + e.phase * 3.0F);
      float progress = zn.progress();
      float sev = 0.6F + zn.severity * 0.8F;
      if (e.smokes()) {
         float rate = smokeRate(zn.channel(AftermathRules.SMOKE), e.strength, e.life, progress, pulse) * far * (e.plume ? 1.8F : 1.0F);
         int n = count(rate, random);
         for (int i = 0; i < n && budget > 0; i++) {
            smoke(mc, zn, e, random, sev);
         }
      }

      if (d > SMOKE_DETAIL) {
         return;
      }

      // dust over fresh rubble, sinking back
      if (e.kind == ClientZone.Emitter.RUBBLE || e.kind == ClientZone.Emitter.BREACH) {
         int n = count(zn.channel(AftermathRules.DUST) * e.strength * 0.5F * far, random);
         for (int i = 0; i < n && budget > 0; i++) {
            spawn(mc, ViltrumiteParticles.RUIN_DUST.get(), e.x + (random.nextDouble() - 0.5) * 2.0, e.y + random.nextDouble() * 1.2,
               e.z + (random.nextDouble() - 0.5) * 2.0, (random.nextDouble() - 0.5) * 0.05, 0.01 + random.nextDouble() * 0.02,
               (random.nextDouble() - 0.5) * 0.05, (0.6F + random.nextFloat() * 0.7F) * sev);
         }
      }

      if (d > DETAIL) {
         return;
      }

      // crumbs trickling down from broken ceilings and edges
      if (e.kind == ClientZone.Emitter.OVERHANG || e.kind == ClientZone.Emitter.WINDOW) {
         if (random.nextFloat() < zn.channel(AftermathRules.DEBRIS) * e.strength * 0.09F) {
            crumbs(mc, e.x, e.y, e.z, e.material, 1 + random.nextInt(3), random, 0.35);
         }
      }

      // smouldering debris: glowing cinders, embers riding up, small flames while it still burns
      float smoulder = zn.channel(AftermathRules.SMOLDER);
      if ((e.burnable || e.kind == ClientZone.Emitter.HOT) && smoulder > 0.02F) {
         if (random.nextFloat() < smoulder * e.strength * 0.35F && budget > 0) {
            spawn(mc, ViltrumiteParticles.METEOR_ASH.get(), e.x + (random.nextDouble() - 0.5) * 0.9, e.y + 0.1, e.z + (random.nextDouble() - 0.5) * 0.9,
               (random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.02, 1.0F);
         }

         if (random.nextFloat() < smoulder * e.strength * 0.12F && budget > 0) {
            spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), e.x, e.y + 0.2, e.z, (random.nextDouble() - 0.5) * 0.05, 0.1 + random.nextDouble() * 0.12,
               (random.nextDouble() - 0.5) * 0.05, 0.6F);
         }

         float fire = zn.channel(AftermathRules.FIRE);
         if (fire > 0.1F && random.nextFloat() < fire * e.strength * 0.4F && budget > 0) {
            spawn(mc, ViltrumiteParticles.METEOR_FLAME.get(), e.x + (random.nextDouble() - 0.5) * 0.7, e.y + random.nextDouble() * 0.3,
               e.z + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0, 0.4F + fire * e.strength * 0.6F);
         }
      }

      // the hot glow of a crater or a burning core, fading as it cools
      float hot = zn.channel(AftermathRules.HOT);
      if (e.kind == ClientZone.Emitter.HOT && hot > 0.05F && random.nextFloat() < hot * 0.22F && budget > 0) {
         spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), e.x + (random.nextDouble() - 0.5), e.y + 0.2, e.z + (random.nextDouble() - 0.5), 0.0, 0.03, 0.0,
            0.8F + hot * 1.2F);
      }

      // the first seconds: hot sparks thrown out of the wreck
      float sparks = zn.channel(AftermathRules.SPARKS);
      if (zn.stage == dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathStage.IMPACT && random.nextFloat() < sparks * 0.05F && budget > 0) {
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), e.x, e.y + 0.3, e.z, (random.nextDouble() - 0.5) * 0.3, 0.15 + random.nextDouble() * 0.25,
            (random.nextDouble() - 0.5) * 0.3, 0.8F);
      }
   }

   private static void smoke(Minecraft mc, ClientZone zn, ClientZone.Emitter e, RandomSource random, float sev) {
      float fire = zn.channel(AftermathRules.FIRE);
      // a burning ruin pushes hot, lit smoke for the first minutes; later it is cold grey
      boolean hotSmoke = (e.burnable || e.kind == ClientZone.Emitter.HOT) && random.nextFloat() < fire * 0.5F;
      if (e.kind == ClientZone.Emitter.WINDOW) {
         double out = 0.05 + random.nextDouble() * 0.07;
         spawn(mc, hotSmoke ? ViltrumiteParticles.FIRE_SMOKE.get() : ViltrumiteParticles.RUIN_SMOKE.get(),
            e.x + (random.nextDouble() - 0.5) * 0.8 * Math.abs(e.dz), e.y + (random.nextDouble() - 0.5) * 0.7, e.z + (random.nextDouble() - 0.5) * 0.8 * Math.abs(e.dx),
            e.dx * out + (random.nextDouble() - 0.5) * 0.01, 0.015 + random.nextDouble() * 0.02, e.dz * out + (random.nextDouble() - 0.5) * 0.01,
            (0.55F + random.nextFloat() * 0.6F) * sev * (0.7F + e.strength * 0.5F));
      } else if (e.plume) {
         spawn(mc, ViltrumiteParticles.RUIN_PLUME.get(), e.x + (random.nextDouble() - 0.5) * 2.5, e.y + 0.5 + random.nextDouble() * 1.5,
            e.z + (random.nextDouble() - 0.5) * 2.5, (random.nextDouble() - 0.5) * 0.02, 0.07 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.02,
            (0.9F + random.nextFloat() * 0.8F) * sev);
      } else {
         spawn(mc, hotSmoke ? ViltrumiteParticles.FIRE_SMOKE.get() : ViltrumiteParticles.RUIN_SMOKE.get(), e.x + (random.nextDouble() - 0.5) * 1.2,
            e.y + 0.2 + random.nextDouble() * 0.6, e.z + (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.04,
            (random.nextDouble() - 0.5) * 0.02, (0.5F + random.nextFloat() * 0.6F) * sev * (0.6F + e.strength * 0.6F));
      }
   }

   /** Low haze over a badly wrecked quarter, near the ground, around the camera when it is inside. */
   private static void haze(Minecraft mc, ClientZone zn, Vec3 cam, RandomSource random) {
      float fog = zn.channel(AftermathRules.FOG);
      if (fog < 0.05F || zn.distance > zn.radius * 2.5 + 32.0) {
         return;
      }

      int n = count(fog * 0.5F, random);
      for (int i = 0; i < n && budget > 0; i++) {
         double a = random.nextDouble() * Mth.TWO_PI;
         double r = Math.sqrt(random.nextDouble()) * zn.radius * 1.3;
         int x = Mth.floor(zn.x + Math.cos(a) * r);
         int z = Mth.floor(zn.z + Math.sin(a) * r);
         if (!mc.level.hasChunk(x >> 4, z >> 4)) {
            continue;
         }

         double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.5 + random.nextDouble() * 3.0;
         if (Math.abs(y - zn.y) > zn.radius + 12.0) {
            y = zn.y + random.nextDouble() * 3.0;
         }

         spawn(mc, ViltrumiteParticles.RUIN_HAZE.get(), x + random.nextDouble(), y, z + random.nextDouble(), (random.nextDouble() - 0.5) * 0.01, 0.0,
            (random.nextDouble() - 0.5) * 0.01, 0.8F + random.nextFloat() * 0.8F);
      }
   }

   /** The fresh dust cloud: a ring of heavy dust rolling out of the ruin (once per hit). */
   private static void surge(Minecraft mc, ClientZone zn, RandomSource random) {
      int n = Math.min(budget + 24, 18 + Math.round(zn.severity * 30));
      for (int i = 0; i < n; i++) {
         double a = random.nextDouble() * Mth.TWO_PI;
         double r = Math.sqrt(random.nextDouble()) * zn.radius * 0.6;
         double x = zn.x + Math.cos(a) * r;
         double z = zn.z + Math.sin(a) * r;
         double v = 0.08 + random.nextDouble() * 0.12;
         spawn(mc, ViltrumiteParticles.RUIN_DUST.get(), x, zn.y - 1.0 + random.nextDouble() * 3.0, z, Math.cos(a) * v, 0.02 + random.nextDouble() * 0.04,
            Math.sin(a) * v, 1.2F + random.nextFloat() * 1.4F + zn.severity);
      }
   }

   /** A shower of crumbs with a dust puff (the scene director's crumbles and collapses). */
   static void shower(ClientZone.Emitter e, int crumbs, float dust) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }

      RandomSource random = mc.level.getRandom();
      crumbs(mc, e.x, e.y, e.z, e.material, crumbs, random, 0.9);
      int n = Math.round(dust * 6.0F);
      for (int i = 0; i < n; i++) {
         spawn(mc, ViltrumiteParticles.RUIN_DUST.get(), e.x + (random.nextDouble() - 0.5) * 1.5, e.y - 1.0 - random.nextDouble() * 2.0,
            e.z + (random.nextDouble() - 0.5) * 1.5, (random.nextDouble() - 0.5) * 0.06, -0.01 + random.nextDouble() * 0.03,
            (random.nextDouble() - 0.5) * 0.06, 0.8F + random.nextFloat() * dust);
      }
   }

   /** A torn wire arcing: a short burst of blue-white sparks. */
   static void arc(ClientZone.Emitter e, int count) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }

      RandomSource random = mc.level.getRandom();
      for (int i = 0; i < count; i++) {
         spawn(mc, ViltrumiteParticles.WIRE_SPARK.get(), e.x + (random.nextDouble() - 0.5) * 0.3, e.y + 0.6 + random.nextDouble() * 0.3,
            e.z + (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.25, 0.05 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.25,
            1.0F);
      }

      spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), e.x, e.y + 0.7, e.z, 0.0, 0.0, 0.0, 0.35F);
   }

   private static void crumbs(Minecraft mc, double x, double y, double z, net.minecraft.world.level.block.state.BlockState material, int n,
      RandomSource random, double spread) {
      if (material == null || material.isAir()) {
         return;
      }

      BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, material);
      BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, material);
      for (int i = 0; i < n; i++) {
         double px = x + (random.nextDouble() - 0.5) * spread;
         double pz = z + (random.nextDouble() - 0.5) * spread;
         spawn(mc, block, px, y, pz, (random.nextDouble() - 0.5) * 0.04, -0.05 - random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.04, 1.0F);
         if (random.nextInt(2) == 0) {
            spawn(mc, dust, px, y - 0.05, pz, 0.0, 0.0, 0.0, 1.0F);
         }
      }
   }

   private static void spawn(Minecraft mc, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz, float scale) {
      budget--;
      Particle p = mc.particleEngine.createParticle(type, x, y, z, dx, dy, dz);
      if (p != null && scale != 1.0F) {
         p.scale(scale);
      }
   }

   /** The ruin wind: a slowly turning breeze with gusts; still air when no ruin is near. */
   static void wind(int clock, boolean active) {
      if (!active) {
         MeteorSmokeParticle.setWind(0.0F, 0.0F);
         return;
      }

      float a = 0.7F + 0.9F * Mth.sin(clock / 2400.0F) + 0.25F * Mth.sin(clock / 330.0F);
      float gust = 0.0022F + 0.0016F * Math.max(0.0F, Mth.sin(clock / 90.0F) * Mth.sin(clock / 37.0F + 1.3F));
      MeteorSmokeParticle.setWind(Mth.cos(a) * gust, Mth.sin(a) * gust);
   }
}
