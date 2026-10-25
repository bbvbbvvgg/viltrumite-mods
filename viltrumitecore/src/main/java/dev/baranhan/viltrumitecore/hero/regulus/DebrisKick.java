package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Debris Kick: Regulus kicks the ground in front of him. The kick itself
 * never breaks blocks — it rips a spray of shards out of the surface that
 * fly at enormous speed like buckshot: every shard draws its own gaussian
 * yaw/elevation inside the cone. Damage resolves within the impact tick;
 * each shard is a fast segment cast that hits the first living target
 * (damage + knockback), or smashes into blocks (breaking up to
 * DEBRIS_SHARD_PIERCE of them, mobGriefing respected) and bursts into dust.
 * Clients get the shard end points and draw visible streaks, place a
 * bullet-whiz near every listener the spray passes and shake the camera.
 */
public final class DebrisKick {
   private DebrisKick() {
   }

   public static void start(ServerPlayer player, RegulusState state) {
      if (state.busy()) {
         return;
      }
      // A ground kick needs ground: an airborne cast costs nothing and says why.
      if (!player.onGround()) {
         player.displayClientMessage(Component.translatable("message.viltrumitecore.debris.no_ground"), true);
         return;
      }
      state.beginAction(RegulusHero.ACTION_DEBRIS, RegulusRules.DEBRIS_ANIM_TICKS, RegulusRules.DEBRIS_EVENT_TICK, RegulusRules.DEBRIS_EVENT_TICK);
      RegulusPassives.preserveExternalSlowness(player, state);
   }

   static boolean shouldFire(RegulusState state) {
      return RegulusHero.ACTION_DEBRIS.equals(state.actionId) && !state.eventFired && state.actionElapsed >= state.actionEventTick;
   }

   public static void tick(ServerPlayer player, RegulusState state) {
      if (!RegulusHero.ACTION_DEBRIS.equals(state.actionId) || state.eventFired) {
         return;
      }

      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 0, true, false, true));
      if (!shouldFire(state)) {
         return;
      }

      state.eventFired = true;
      RegulusPassives.clearOwnSlowness(player, state, state.actionElapsed);
      ServerLevel level = player.serverLevel();
      BlockPos groundPos = surfaceUnder(level, player);
      BlockState ground = level.getBlockState(groundPos);
      // Knocked up during the windup: nothing to kick out — free abort.
      if (!player.onGround() && ground.getCollisionShape(level, groundPos).isEmpty()) {
         player.displayClientMessage(Component.translatable("message.viltrumitecore.debris.no_ground"), true);
         state.clearAction();
         return;
      }

      state.startCooldown(RegulusAbilities.DEBRIS_KICK, RegulusRules.DEBRIS_COOLDOWN);
      boolean madness = state.madnessTicksLeft > 0;
      erupt(player, state, groundPos, state.hearts(), madness);
      kick(player, level, materialOf(ground), state.hearts(), madness);
   }

   /**
    * The block the feet actually stand on. blockPosition().below() is wrong on
    * partial blocks: on a bottom slab the feet sit at y+0.5, blockPosition() is
    * the slab itself and below() would pick the block UNDER it. Probe just
    * beneath the feet instead; fall back to vanilla's supporting block when
    * the feet hang over an edge.
    */
   private static BlockPos surfaceUnder(ServerLevel level, ServerPlayer player) {
      BlockPos probe = feetSurface(player.getX(), player.getY(), player.getZ());
      if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
         return probe;
      }

      return player.getOnPos();
   }

   /** Pure: the block cell just below the feet (feet y minus a small epsilon). */
   static BlockPos feetSurface(double x, double feetY, double z) {
      return BlockPos.containing(x, feetY - SURFACE_EPSILON, z);
   }

   private static final double SURFACE_EPSILON = 0.05;

   /** The shard material is the real surface; air/fluids fall back to dirt. */
   private static BlockState materialOf(BlockState ground) {
      return ground.isAir() || !ground.getFluidState().isEmpty() ? Blocks.DIRT.defaultBlockState() : ground;
   }

   /**
    * The kick tears the ground out in front of the foot and throws it forward
    * as real flying blocks (shared HeroDebris, same as Thunderclap). Own heart
    * carriers standing on it are spared.
    */
   private static void erupt(ServerPlayer player, RegulusState state, BlockPos groundPos, int hearts, boolean madness) {
      HeroDebris.Eruption spec = madness ? RegulusRules.KICK_ERUPTION.scaled(RegulusRules.MADNESS_ERUPT_SCALE) : RegulusRules.KICK_ERUPTION;
      float damage = RegulusRules.KICK_ERUPT_ENTITY_DAMAGE * RegulusRules.heartBonus(hearts) * (float)spec.power();
      HeroDebris.erupt(player, groundPos, shardDirection(player.getYRot(), 0.0F, 0.0, 0.0), spec, damage, e -> state.carriers.contains(e.getUUID()));
   }

   private static void kick(ServerPlayer player, ServerLevel level, BlockState material, int hearts, boolean madness) {
      float yaw = player.getYRot();
      Vec3 forward = shardDirection(yaw, 0.0F, 0.0, 0.0);
      // Shards leave from the kicked spot: at the toe, just above the ground.
      Vec3 origin = player.position().add(forward.x * 0.8, 0.35, forward.z * 0.8);
      BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, material);

      // Impact at the foot: the ground bursts (the crater itself is erupt()).
      level.sendParticles(debris, origin.x, origin.y, origin.z, 60, 0.45, 0.12, 0.45, 0.35);
      level.sendParticles(ParticleTypes.POOF, origin.x, origin.y, origin.z, 16, 0.4, 0.08, 0.4, 0.08);
      level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, origin.x, origin.y, origin.z, 6, 0.5, 0.1, 0.5, 0.02);
      level.sendParticles(ParticleTypes.EXPLOSION, origin.x + forward.x, origin.y, origin.z + forward.z, 1, 0.0, 0.0, 0.0, 0.0);
      level.playSound(null, BlockPos.containing(origin), material.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.6F, 0.55F);
      level.playSound(null, BlockPos.containing(origin), ViltrumiteCore.REGULUS_KICK_CRACK.get(), SoundSource.PLAYERS, 1.8F, 0.9F + level.random.nextFloat() * 0.2F);
      level.playSound(null, BlockPos.containing(origin), ViltrumiteCore.REGULUS_IMPACT_HEAVY.get(), SoundSource.PLAYERS, 0.9F, 1.35F);

      RandomSource random = player.getRandom();
      Map<UUID, Integer> shardHits = new HashMap<>();
      int shards = RegulusRules.debrisShardCount(madness);
      int pierce = RegulusRules.debrisPierce(madness);
      float aim = aimPitch(player.getXRot());
      float[] ends = new float[shards * 3];
      for (int i = 0; i < shards; i++) {
         // Shotgun: every shard draws its own gaussian yaw and elevation, so
         // the spray is a chaotic cloud rather than evenly spaced lanes.
         double yawOffset = RegulusRules.clampedSpread(random.nextGaussian(), RegulusRules.DEBRIS_YAW_SIGMA, RegulusRules.DEBRIS_CONE_DEGREES / 2.0);
         double elevation = RegulusRules.shardElevation(random.nextGaussian());
         Vec3 dir = shardDirection(yaw, aim, yawOffset, elevation);
         // Each shard starts from a slightly different chunk of the kicked ground.
         Vec3 from = origin.add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.6);
         Vec3 end = fireShard(player, level, from, dir, hearts, pierce, shardHits);
         ends[i * 3] = (float)end.x;
         ends[i * 3 + 1] = (float)end.y;
         ends[i * 3 + 2] = (float)end.z;
      }

      // Clients draw the visible shards, the whiz past their ears and the shake.
      HeroFx.shards(player, origin, madness ? 1.0F : 0.7F, material, ends, ViltrumiteCore.REGULUS_SHARD_WHIZ.get());
   }

   /**
    * One shard: resolved instantly along its path. Stops at the first living
    * target, or after breaking {@code pierce} blocks / hitting an unbreakable
    * one. Leaves dust where it ends and returns that end point.
    */
   private static Vec3 fireShard(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 dir, int hearts, int pierce, Map<UUID, Integer> shardHits) {
      Vec3 from = origin;
      double remaining = RegulusRules.DEBRIS_RANGE;
      int pierced = 0;
      Vec3 end = origin.add(dir.scale(remaining));

      while (remaining > 0.05) {
         Vec3 to = from.add(dir.scale(remaining));
         BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
         Vec3 blockStop = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

         LivingEntity target = firstTarget(player, level, from, blockStop);
         if (target != null) {
            Vec3 hitAt = target.getBoundingBox().inflate(0.3).clip(from, blockStop).orElse(target.position());
            applyHit(player, target, dir, hearts, shardHits);
            level.sendParticles(ParticleTypes.CRIT, hitAt.x, hitAt.y, hitAt.z, 8, 0.15, 0.15, 0.15, 0.4);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, hitAt.x, hitAt.y, hitAt.z, 2, 0.1, 0.1, 0.1, 0.1);
            end = hitAt;
            break;
         }

         if (blockHit.getType() == HitResult.Type.MISS) {
            end = to;
            break;
         }

         BlockPos pos = blockHit.getBlockPos();
         BlockState state = level.getBlockState(pos);
         end = blockHit.getLocation();
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), end.x, end.y, end.z, 14, 0.2, 0.2, 0.2, 0.2);
         if (pierced >= pierce || !HeroDestruction.destroyBlock(level, pos)) {
            break;
         }

         pierced++;
         level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.7F + level.random.nextFloat() * 0.3F);
         double travelled = from.distanceTo(end);
         remaining -= travelled + 0.05;
         from = end.add(dir.scale(0.05));
      }

      // Dust where the shard died.
      level.sendParticles(ParticleTypes.POOF, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.03);
      level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.02);
      return end;
   }

   @Nullable
   private static LivingEntity firstTarget(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 to) {
      AABB sweep = new AABB(from, to).inflate(0.6);
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (Entity entity : level.getEntities(player, sweep, e -> e instanceof LivingEntity living && living.isAlive() && (e instanceof Mob || e instanceof Player) && !e.isSpectator())) {
         double distance = rayDistance(from, to, entity.getBoundingBox().inflate(0.3));
         if (distance < bestDistance) {
            bestDistance = distance;
            best = (LivingEntity)entity;
         }
      }

      return best;
   }

   /**
    * Shard damage stacks up to DEBRIS_MAX_SHARDS_PER_TARGET per target per
    * kick; i-frames are lifted per shard so the spray actually lands. The
    * first landed shard carries the knockback.
    */
   private static void applyHit(ServerPlayer player, LivingEntity target, Vec3 dir, int hearts, Map<UUID, Integer> shardHits) {
      int already = shardHits.getOrDefault(target.getUUID(), 0);
      float damage = RegulusRules.debrisShardDamage(already, hearts);
      if (damage <= 0.0F) {
         return;
      }

      int savedInvulnerable = target.invulnerableTime;
      target.invulnerableTime = 0;
      boolean landed = dev.baranhan.viltrumitecore.hero.HeroAttackContext.hurt(target,player.damageSources().playerAttack(player), damage,dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.WAVE);
      target.invulnerableTime = savedInvulnerable;
      if (!landed) {
         return;
      }

      shardHits.put(target.getUUID(), already + 1);
      if (already == 0 && HeroRegistry.allowsExternalControl(target, ControlKind.IMPULSE)) {
         Vec3 push = new Vec3(dir.x, 0.0, dir.z).normalize().scale(RegulusRules.DEBRIS_KNOCKBACK);
         target.setDeltaMovement(push.x, 0.35, push.z);
         target.hasImpulse = true;
         if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
         }
      }
   }

   /** The spray follows the gaze only mildly: kicks stay low and forward. */
   static float aimPitch(float lookPitch) {
      return Mth.clamp(lookPitch, RegulusRules.DEBRIS_PITCH_UP_LIMIT, RegulusRules.DEBRIS_PITCH_DOWN_LIMIT) * 0.5F;
   }

   /**
    * Direction for a shard: yaw in MC degrees, pitch in MC degrees (positive
    * = down), plus a yaw offset and an elevation (positive = up) in degrees.
    */
   static Vec3 shardDirection(float yawDeg, float pitchDeg, double yawOffsetDeg, double elevationDeg) {
      double yaw = Math.toRadians(yawDeg + yawOffsetDeg);
      double pitch = Math.toRadians(pitchDeg - elevationDeg);
      double cosP = Math.cos(pitch);
      return new Vec3(-Math.sin(yaw) * cosP, -Math.sin(pitch), Math.cos(yaw) * cosP).normalize();
   }

   /** Shared gaze collision helper used by Mania and manual heart assignment. */
   static double rayDistance(Vec3 origin, Vec3 far, AABB box) {
      return box.contains(origin) ? 0.0 : box.clip(origin, far).map(origin::distanceTo).orElse(Double.MAX_VALUE);
   }
}
