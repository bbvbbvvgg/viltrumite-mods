package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Madness power, after the light novel: Regulus swings and the air in front
 * of his fist stops in time — an unbreakable blade that slices everything in
 * its path. Fired from the punch impact tick while mad: hits every living
 * thing in a long thin capsule once and cuts a one-block slit through
 * terrain (HeroDestruction, so mobGriefing/protection still apply).
 */
public final class RegulusAirBlade {
   private RegulusAirBlade() {
   }

   public static void onPunch(ServerPlayer player, RegulusState state) {
      if (state.madnessTicksLeft <= 0) {
         return;
      }

      fire(player, state);
   }

   /** Pure: distance from point p to the segment a-b. */
   static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
      Vec3 ab = b.subtract(a);
      double len2 = ab.lengthSqr();
      double t = len2 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
      return p.distanceTo(a.add(ab.scale(t)));
   }

   private static void fire(ServerPlayer player, RegulusState state) {
      ServerLevel level = player.serverLevel();
      Vec3 dir = player.getLookAngle().normalize();
      Vec3 origin = player.getEyePosition().add(dir.scale(0.8)).add(0.0, -0.25, 0.0);
      Vec3 end = origin.add(dir.scale(RegulusRules.AIR_BLADE_RANGE));
      float damage = RegulusRules.airBladeDamage(state.hearts());

      AABB sweep = new AABB(origin, end).inflate(RegulusRules.AIR_BLADE_HALF_WIDTH + 1.0);
      Set<Entity> hit = new HashSet<>();
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator())) {
         if (state.carriers.contains(target.getUUID()) || !hit.add(target)) {
            continue;
         }

         Vec3 center = target.getBoundingBox().getCenter();
         double reach = RegulusRules.AIR_BLADE_HALF_WIDTH + target.getBbWidth() * 0.5;
         if (distanceToSegment(center, origin, end) > reach) {
            continue;
         }

         int saved = target.invulnerableTime;
         target.invulnerableTime = 0;
         boolean landed = dev.baranhan.viltrumitecore.hero.HeroAttackContext.hurt(target,player.damageSources().playerAttack(player), damage,dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.SHOT);
         if (!landed) {
            target.invulnerableTime = saved;
            continue;
         }

         level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
         level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 12, 0.3, 0.4, 0.3, 0.5);
         if (HeroRegistry.allowsExternalControl(target, ControlKind.IMPULSE)) {
            Vec3 push = dir.scale(1.4);
            target.setDeltaMovement(push.x, Math.max(0.3, push.y), push.z);
            target.hasImpulse = true;
            if (target instanceof ServerPlayer sp) {
               sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
         }
      }

      cut(level, origin, dir);
      for (double d = 1.0; d < RegulusRules.AIR_BLADE_RANGE; d += 2.5) {
         Vec3 p = origin.add(dir.scale(d));
         level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      level.playSound(null, BlockPos.containing(origin), ViltrumiteCore.REGULUS_AIR_BLADE.get(), SoundSource.PLAYERS, 2.0F, 0.9F + level.random.nextFloat() * 0.2F);
      HeroFx.blade(player, origin, end, ViltrumiteCore.REGULUS_SHARD_WHIZ.get());
   }

   /** One-block slit, a block above and below the line: the blade's cut. */
   private static void cut(ServerLevel level, Vec3 origin, Vec3 dir) {
      Set<BlockPos> done = new HashSet<>();
      int budget = 90;
      for (double d = 0.5; d < RegulusRules.AIR_BLADE_RANGE && budget > 0; d += 0.5) {
         Vec3 p = origin.add(dir.scale(d));
         for (int dy = -RegulusRules.AIR_BLADE_CUT_DEPTH; dy <= RegulusRules.AIR_BLADE_CUT_DEPTH; dy++) {
            BlockPos pos = BlockPos.containing(p.x, p.y + dy, p.z);
            if (!done.add(pos)) {
               continue;
            }

            BlockState block = level.getBlockState(pos);
            if (block.isAir() || !block.getFluidState().isEmpty()) {
               continue;
            }

            if (HeroDestruction.destroyBlock(level, pos)) {
               budget--;
            }
         }
      }
   }
}
