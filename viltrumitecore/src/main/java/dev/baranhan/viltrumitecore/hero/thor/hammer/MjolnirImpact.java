package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground strikes of the world hammer: the first fall (§3.3, crater r 4–6)
 * and the death-point strike, stronger than it (№169 / №209). Blocks go
 * only where HeroDestruction allows (mobGriefing, unbreakable = protection);
 * protected blocks stay.
 */
final class MjolnirImpact {
   private MjolnirImpact() {
   }

   /** Pure: depth of the crater bowl at horizontal offset (dx, dz), -1 outside. */
   static int bowlDepth(int radius, int dx, int dz) {
      double h2 = (double)dx * dx + (double)dz * dz;
      double r2 = (double)radius * radius;
      if (h2 > r2) {
         return -1;
      }

      return (int)Math.round(Math.sqrt(r2 - h2) * MjolnirTuning.CRATER_DEPTH);
   }

   /**
    * Pure, A-m2: damage without an attacker skips vanilla's PvP rule, so
    * with PvP off it spares players. OPEN(owner): whether the world event
    * (first fall) is exempt; conservative: it spares them too.
    */
   static boolean spared(boolean victimIsPlayer, boolean attackerKnown, boolean pvpAllowed) {
      return victimIsPlayer && !attackerKnown && !pvpAllowed;
   }

   /** The first appearance: huge direct damage, shock wave, crater; the hammer then lies on its side at the bottom. */
   static Vec3 firstFall(ServerLevel level, @Nullable Entity hammer, Vec3 at) {
      BlockPos surface = BlockPos.containing(at.x, at.y - 0.01, at.z);
      BlockState material = level.getBlockState(surface);
      wave(level, at, MjolnirTuning.FALL_WAVE_RADIUS, MjolnirTuning.FALL_DIRECT_DAMAGE, MjolnirTuning.FALL_WAVE_DAMAGE, MjolnirTuning.FALL_WAVE_PUSH,
         hammer, null);
      Vec3 rest = crater(level, surface, MjolnirTuning.CRATER_RADIUS);
      effects(level, at, material, MjolnirTuning.CRATER_RADIUS * 2.0F, 6.0F);
      return rest;
   }

   /** №169: the strike into the death point, stronger than the first fall; the dead owner is never the target. */
   static Vec3 deathStrike(ServerLevel level, Vec3 at, @Nullable Entity hammer, @Nullable UUID owner) {
      BlockPos center = BlockPos.containing(at);
      BlockState material = level.getBlockState(center.below());
      wave(level, at, MjolnirTuning.DEATH_WAVE_RADIUS, MjolnirTuning.DEATH_IMPACT_DAMAGE, MjolnirTuning.DEATH_IMPACT_DAMAGE * 0.5F,
         MjolnirTuning.FALL_WAVE_PUSH * 1.4, hammer, owner);
      Vec3 rest = crater(level, center.below(), MjolnirTuning.DEATH_IMPACT_RADIUS);
      effects(level, at, material, MjolnirTuning.DEATH_IMPACT_RADIUS * 2.0F, 8.0F);
      return rest;
   }

   /** A bowl of radius r under {@code surface}; returns where the hammer lies (bottom middle). */
   static Vec3 crater(ServerLevel level, BlockPos surface, int radius) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            int depth = bowlDepth(radius, dx, dz);
            if (depth < 0) {
               continue;
            }

            for (int dy = -depth + 1; dy <= 2; dy++) {
               BlockPos pos = surface.offset(dx, dy, dz);
               if (!level.getBlockState(pos).isAir()) {
                  HeroDestruction.clearBlock(level, pos);
               }
            }
         }
      }

      // Rest on whatever is left under the middle (protected blocks may remain).
      BlockPos probe = surface.above(2);
      int floor = level.getMinBuildHeight();
      while (probe.getY() > floor && level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
         probe = probe.below();
      }

      double top = level.getBlockState(probe).getCollisionShape(level, probe).isEmpty() ? probe.getY() : probe.getY()
         + level.getBlockState(probe).getCollisionShape(level, probe).max(net.minecraft.core.Direction.Axis.Y);
      return new Vec3(surface.getX() + 0.5, top, surface.getZ() + 0.5);
   }

   /** Damage falls off with distance (direct hit inside 2 blocks), then the push (after a landed hit only). */
   static void wave(ServerLevel level, Vec3 at, double radius, float direct, float edge, double push, @Nullable Entity hammer, @Nullable UUID spare) {
      DamageSource source = level.damageSources().thrown(hammer == null ? null : hammer, null);
      AABB box = new AABB(at, at).inflate(radius);
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
         if (living.getUUID().equals(spare) || spared(living instanceof Player, false, level.getServer().isPvpAllowed())) {
            continue;
         }

         double distance = living.position().distanceTo(at);
         if (distance > radius) {
            continue;
         }

         float damage = distance <= 2.0 ? direct : (float)(edge * (1.0 - distance / radius));
         if (living.hurt(source, damage) && HeroRegistry.allowsImpulse(living)) {
            Vec3 away = living.position().subtract(at).multiply(1.0, 0.0, 1.0);
            Vec3 dir = away.lengthSqr() < 1.0E-6 ? Vec3.ZERO : away.normalize();
            double strength = push * (1.0 - distance / radius);
            living.push(dir.x * strength, 0.35 + strength * 0.3, dir.z * strength);
            living.hurtMarked = true;
         }
      }
   }

   private static void effects(ServerLevel level, Vec3 at, BlockState material, float radius, float volume) {
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : material),
         at.x, at.y + 0.5, at.z, 160, radius * 0.25, 0.8, radius * 0.25, 0.4);
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 80, radius * 0.2, 1.0, radius * 0.2, 0.6);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, volume, 0.55F);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, volume, 0.7F);
      MjolnirFx.slam(level, at, material, radius);
   }
}
