package dev.baranhan.viltrumitecore.client.collapse;

import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Collapse dust and debris on the existing particle styles (Meteor Rain smoke sprites, vanilla block chips):
 * a dust wall that follows the streets (rays stop at walls, so the cloud rolls down open space instead of
 * through buildings), a rising plume and bursts of chips. Every spawn takes a budget; callers scale it by
 * distance and by the number of collapses on screen.
 */
final class CollapseDust {
   static final int RAYS = 16;

   private CollapseDust() {
   }

   static Particle puff(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz, float scale) {
      Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
      if (p != null && scale != 1.0F) {
         p.scale(scale);
      }
      return p;
   }

   /** Free length of a street ray at {@code y} (stops at a wall two blocks high), at most {@code max}. */
   static double free(ClientLevel level, double x, int y, double z, double ax, double az, double max) {
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (double s = 1.0; s <= max; s += 1.0) {
         int bx = Mth.floor(x + ax * s), bz = Mth.floor(z + az * s);
         p.set(bx, y, bz);
         if (!level.isLoaded(p)) {
            return s;
         }
         boolean low = !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
         p.set(bx, y + 1, bz);
         boolean high = !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
         if (low && high) {
            return s;
         }
      }
      return max;
   }

   /** The ground slam: a dust wall rolling out along the open streets around {@code at}. */
   static void streetWall(ClientLevel level, Vec3 at, int surfaceY, float strength, int budget, RandomSource r) {
      double max = 8.0 + 16.0 * strength;
      int per = Math.max(1, budget / RAYS);
      for (int k = 0; k < RAYS; k++) {
         double a = (k + r.nextDouble() * 0.6) * Mth.TWO_PI / RAYS;
         double ax = Math.cos(a), az = Math.sin(a);
         double free = free(level, at.x, surfaceY, at.z, ax, az, max);
         double speed = 0.12 + 0.95 * free / 14.0 * (0.8 + 0.4 * r.nextDouble());
         for (int i = 0; i < per; i++) {
            double s = r.nextDouble() * Math.min(free, 3.0);
            double v = speed * (0.55 + 0.45 * r.nextDouble());
            puff(ViltrumiteParticles.COLLAPSE_DUST.get(), at.x + ax * s + (r.nextDouble() - 0.5), surfaceY + 0.4 + r.nextDouble() * 1.4,
               at.z + az * s + (r.nextDouble() - 0.5), ax * v, 0.01 + r.nextDouble() * 0.03, az * v, 0.8F + strength * 0.5F);
         }
      }
   }

   /** The column rising over the pile. */
   static void plume(Vec3 at, int surfaceY, float radius, int count, RandomSource r) {
      for (int i = 0; i < count; i++) {
         double a = r.nextDouble() * Mth.TWO_PI, d = r.nextDouble() * radius;
         puff(ViltrumiteParticles.COLLAPSE_PLUME.get(), at.x + Math.cos(a) * d, surfaceY + 0.5 + r.nextDouble() * 3.0, at.z + Math.sin(a) * d,
            Math.cos(a) * 0.04, 0.05 + r.nextDouble() * 0.2, Math.sin(a) * 0.04, 0.9F + r.nextFloat() * 0.6F);
      }
   }

   /** Block chips flying out of a hit or a landing. */
   static void chips(BlockState state, Vec3 at, double spread, double speed, int count, RandomSource r) {
      if (state == null || state.isAir()) {
         return;
      }
      BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
      for (int i = 0; i < count; i++) {
         puff(opt, at.x + (r.nextDouble() - 0.5) * spread, at.y + (r.nextDouble() - 0.3) * spread, at.z + (r.nextDouble() - 0.5) * spread,
            (r.nextDouble() - 0.5) * speed, r.nextDouble() * speed, (r.nextDouble() - 0.5) * speed, 1.0F);
      }
   }

   /** Fine dust trickling down from a broken edge. */
   static void trickle(BlockState state, double x, double y, double z) {
      if (state == null || state.isAir()) {
         return;
      }
      puff(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y, z, 0.0, 0.0, 0.0, 1.0F);
   }

   /** A small cloud puff (impact zone, trails). */
   static void small(double x, double y, double z, double vx, double vy, double vz, float scale) {
      puff(ViltrumiteParticles.METEOR_DUST.get(), x, y, z, vx, vy, vz, scale);
   }
}
