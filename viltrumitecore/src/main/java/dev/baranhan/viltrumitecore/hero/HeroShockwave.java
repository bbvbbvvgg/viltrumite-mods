package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Landing shockwave shared by every hero. Call it from
 * {@link HeroDefinition#onLanded}: that hook runs from LivingFallEvent, the one
 * place where the real fall distance is still known (vanilla resets
 * fallDistance on the landing tick, before the hero tick runs).
 */
public final class HeroShockwave {
   private HeroShockwave() {
   }

   /**
    * Scaling of a landing shockwave with fall height. Below {@code minFall}
    * nothing happens; at {@code fullPowerFall} and above radius and damage
    * reach their maximum.
    */
   public record Landing(float minFall, float fullPowerFall, double minRadius, double maxRadius, float minDamage, float maxDamage) {
      /** 0 at minFall, 1 at fullPowerFall and above. */
      public float power(float fallDistance) {
         return Math.max(0.0F, Math.min(1.0F, (fallDistance - this.minFall) / (this.fullPowerFall - this.minFall)));
      }

      public double radius(float fallDistance, double multiplier) {
         return (this.minRadius + (this.maxRadius - this.minRadius) * this.power(fallDistance)) * multiplier;
      }

      public float damage(float fallDistance, double multiplier) {
         return (this.minDamage + (this.maxDamage - this.minDamage) * this.power(fallDistance)) * (float)multiplier;
      }
   }

   /**
    * Hit everything around a landing player: falloff damage, an outward shove
    * (impulse policy and anchors honored), a ground crack on big landings,
    * server particles, sounds and the client ring/shake FX.
    *
    * @param multiplier  ×radius and ×damage (a buff like Regulus madness); above 1 also cracks more ground
    * @param spared      entities that take nothing (own allies, carriers)
    * @param impactSound hero impact sound, or null for vanilla sounds only
    * @return false when the fall was too short for a shockwave
    */
   public static boolean land(ServerPlayer player, float fallDistance, Landing spec, double multiplier, Predicate<LivingEntity> spared, @Nullable SoundEvent impactSound) {
      if (fallDistance < spec.minFall()) {
         return false;
      }

      ServerLevel level = player.serverLevel();
      boolean amplified = multiplier > 1.0;
      float power = spec.power(fallDistance);
      double radius = spec.radius(fallDistance, multiplier);
      float damage = spec.damage(fallDistance, multiplier);
      Vec3 feet = player.position();
      BlockState ground = groundUnder(level, player);

      AABB area = player.getBoundingBox().inflate(radius, 2.0, radius);
      ControlManager manager = ControlManager.get(level);
      for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
         if (entity == player || !entity.isAlive() || entity.distanceToSqr(player) > radius * radius || spared.test(entity)) {
            continue;
         }

         double falloff = 1.0 - Math.sqrt(entity.distanceToSqr(player)) / (radius + 0.5);
         dev.baranhan.viltrumitecore.hero.HeroAttackContext.hurt(entity,player.damageSources().playerAttack(player), damage * (float)(0.5 + 0.5 * falloff),dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.WAVE);
         if (HeroRegistry.allowsExternalControl(entity, ControlKind.IMPULSE) && !manager.isAnchored(entity)) {
            Vec3 away = entity.position().subtract(feet);
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            Vec3 push = (flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize()).scale(0.9 + 1.4 * falloff * (0.6 + power));
            entity.setDeltaMovement(push.x, 0.45 + 0.5 * falloff, push.z);
            entity.hasImpulse = true;
            if (entity instanceof ServerPlayer target) {
               target.connection.send(new ClientboundSetEntityMotionPacket(target));
            }
         }
      }

      crack(level, feet, power, amplified);
      BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
      int ring = 20 + (int)(28 * power);
      for (int i = 0; i < ring; i++) {
         double az = Math.PI * 2.0 * i / ring;
         double r = 1.0 + level.random.nextDouble() * radius * 0.4;
         double x = feet.x + Math.cos(az) * r;
         double z = feet.z + Math.sin(az) * r;
         // count=0: the offset is a velocity — dust races outward along the ground.
         level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, feet.y + 0.2, z, 0, Math.cos(az), 0.02, Math.sin(az), 0.18 + 0.12 * power);
         level.sendParticles(debris, x, feet.y + 0.2, z, 0, Math.cos(az), 0.6, Math.sin(az), 0.5);
      }
      level.sendParticles(debris, feet.x, feet.y + 0.2, feet.z, 80 + (int)(80 * power), radius * 0.3, 0.2, radius * 0.3, 0.4);
      level.sendParticles(ParticleTypes.POOF, feet.x, feet.y + 0.3, feet.z, 30, radius * 0.35, 0.1, radius * 0.35, 0.08);
      level.sendParticles(ParticleTypes.EXPLOSION, feet.x, feet.y + 0.5, feet.z, 3 + (int)(4 * power), radius * 0.25, 0.2, radius * 0.25, 0.0);
      if (power > 0.5F) {
         level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y + 0.5, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      if (impactSound != null) {
         level.playSound(null, player.blockPosition(), impactSound, SoundSource.PLAYERS, 2.0F + 2.0F * power, 0.85F - 0.2F * power);
      }
      level.playSound(null, player.blockPosition(), ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.5F, 0.5F);
      level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F + power, 0.6F);
      HeroFx.shockwave(player, feet, Math.max(0.15F, power) * (amplified ? 1.3F : 1.0F), ground, (float)radius);
      return true;
   }

   /** Big landings crack the ground: a shallow scatter of broken surface blocks. */
   private static void crack(ServerLevel level, Vec3 feet, float power, boolean amplified) {
      if (power < 0.35F && !amplified) {
         return;
      }

      int radius = 1 + (int)(2.5F * power) + (amplified ? 1 : 0);
      BlockPos center = BlockPos.containing(feet.x, feet.y - 0.5, feet.z);
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius || level.random.nextFloat() > 0.55F) {
               continue;
            }

            BlockPos pos = center.offset(dx, 0, dz);
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
               HeroDestruction.destroyBlock(level, pos);
            }
         }
      }
   }

   /** The block the player stands on; air and fluids read as dirt for particles. */
   public static BlockState groundUnder(ServerLevel level, Player player) {
      BlockState state = level.getBlockState(player.getOnPos());
      return state.isAir() || !state.getFluidState().isEmpty() ? Blocks.DIRT.defaultBlockState() : state;
   }
}
