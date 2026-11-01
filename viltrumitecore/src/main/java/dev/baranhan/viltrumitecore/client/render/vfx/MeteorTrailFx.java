package dev.baranhan.viltrumitecore.client.render.vfx;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The entry trail of a meteor, spawned once per client tick along the path of the last tick (sub-steps,
 * so a fast meteor leaves a continuous trail instead of beads): white-yellow flame licks right behind the
 * core, a hot glow bloom, sparks flung off with part of the meteor's speed, and turbulent smoke that keeps
 * a little of the motion (inertia) and darkens as it cools. Density falls with the camera distance (LOD)
 * while the far particles grow, so the trail reads from across the city by day and by night; a per-tick
 * budget shared by every meteor keeps a dense rain bounded. Visual only: hitbox and physics are untouched.
 */
public final class MeteorTrailFx {
   /** Full detail out to here, half to {@link #HALF}, a sparse but large trail to {@link #MAX}. */
   public static final double FULL = 80.0;
   public static final double HALF = 160.0;
   public static final double MAX = 320.0;
   /** Particles all meteors together may spawn in one client tick. */
   public static final int TICK_BUDGET = 220;
   /** Centre of the rendered core relative to the entity position (the renderer lifts the 4-block core by 0.5). */
   public static final double CORE_LIFT = 0.5;

   private static long budgetTick = Long.MIN_VALUE;
   private static int spent;

   private MeteorTrailFx() {
   }

   /** Pure: detail factor by camera distance, 1 near, 0 beyond {@link #MAX}. */
   public static float lod(double distance) {
      if (distance <= FULL) {
         return 1.0F;
      }
      if (distance <= HALF) {
         return 0.5F;
      }
      return distance <= MAX ? 0.25F : 0.0F;
   }

   /** Pure: size boost of far particles so a thinner far trail stays visible (1 near, up to 3). */
   public static float farScale(double distance) {
      return (float)Mth.clamp(1.0 + (distance - 40.0) / 90.0, 1.0, 3.0);
   }

   public static void tick(MeteorEntity meteor) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.isPaused()) {
         return;
      }

      long now = mc.level.getGameTime();
      if (now != budgetTick) {
         budgetTick = now;
         spent = 0;
      }

      Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 core = meteor.position().add(0.0, CORE_LIFT, 0.0);
      double distance = core.distanceTo(camera);
      float lod = lod(distance);
      if (lod <= 0.0F || spent >= TICK_BUDGET) {
         return;
      }

      RandomSource random = meteor.level().getRandom();
      Vec3 v = meteor.getDeltaMovement();
      double speed = v.length();
      Vec3 dir = speed < 0.05 ? new Vec3(0.0, -1.0, 0.0) : v.scale(1.0 / speed);
      Vec3 last = core.subtract(v);
      float grow = farScale(distance);
      int steps = lod >= 1.0F ? 3 : lod >= 0.5F ? 2 : 1;

      for (int i = 0; i < steps; i++) {
         Vec3 p = lerp(last, core, (i + random.nextFloat()) / steps);
         // flame licks wrapped around the back of the core, trailing with a quarter of the speed
         int flames = lod >= 1.0F ? 3 : 2;
         for (int f = 0; f < flames; f++) {
            Vec3 at = p.subtract(dir.scale(1.2 + random.nextDouble() * 1.6)).add(sphere(random, 1.4));
            Vec3 vel = v.scale(-0.22).add(sphere(random, 0.07));
            spawn(mc, ViltrumiteParticles.METEOR_FLAME.get(), at, vel, (2.1F + random.nextFloat() * 1.3F) * grow);
         }

         // the white-hot bloom right behind the core
         if (lod >= 0.5F) {
            Vec3 at = p.subtract(dir.scale(0.6)).add(sphere(random, 0.5));
            spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), at, v.scale(0.35), (2.0F + random.nextFloat()) * grow);
         }

         // turbulent smoke farther back, keeping a little of the meteor's motion
         int smoke = lod >= 1.0F ? 2 : 1;
         for (int s = 0; s < smoke; s++) {
            Vec3 at = p.subtract(dir.scale(2.6 + random.nextDouble() * 2.4)).add(sphere(random, 1.5));
            Vec3 vel = v.scale(0.08).add(sphere(random, 0.05));
            spawn(mc, ViltrumiteParticles.METEOR_SMOKE.get(), at, vel, (1.5F + random.nextFloat() * 0.9F) * Math.min(2.2F, grow));
         }
      }

      // sparks shed off the surface, fewer far away
      int sparks = lod >= 1.0F ? 4 : lod >= 0.5F ? 2 : random.nextInt(2);
      for (int s = 0; s < sparks; s++) {
         Vec3 at = core.add(sphere(random, 2.0));
         Vec3 vel = v.scale(-0.3 + random.nextDouble() * 0.5).add(sphere(random, 0.35));
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), at, vel, (1.2F + random.nextFloat() * 1.5F) * Math.min(2.0F, grow));
      }
   }

   static void spawn(Minecraft mc, ParticleOptions type, Vec3 at, Vec3 vel, float scale) {
      if (spent >= TICK_BUDGET) {
         return;
      }
      spent++;
      // createParticle skips the vanilla 32-block cut of addParticle: a meteor trail must read from far away
      Particle particle = mc.particleEngine.createParticle(type, at.x, at.y, at.z, vel.x, vel.y, vel.z);
      if (particle != null && scale != 1.0F) {
         particle.scale(scale);
      }
   }

   public static Vec3 sphere(RandomSource random, double radius) {
      return new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(2.0 * radius);
   }

   private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
      return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
   }
}
