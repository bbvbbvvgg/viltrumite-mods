package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumiteflight.mixin.FallingBlockEntityInvoker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flying-block debris shared by every hero: the Viltrumite Thunderclap
 * treatment, where a block is turned into a real FallingBlockEntity and thrown.
 * Thrown blocks land and stay where they come down. Bedrock and mobGriefing
 * are honored through {@link HeroDestruction}.
 */
public final class HeroDebris {
   private HeroDebris() {
   }

   /**
    * Shape and force of a ground eruption (a forward crater thrown as blocks).
    *
    * @param radius    horizontal crater radius in blocks
    * @param depth     how many layers down the crater reaches
    * @param ahead     crater centre in front of the source; blocks further than this behind the centre stay
    * @param maxFlying blocks thrown as entities; the rest of the crater shatters
    * @param speed     horizontal block speed (blocks/tick, randomized ±30%)
    * @param power     multiplier on vertical block speed and on the launch of standing entities
    */
   public record Eruption(double radius, double depth, double ahead, int maxFlying, double speed, double power) {
      /** The same eruption, bigger: size and force ×scale, thrown-block cap ×scale². */
      public Eruption scaled(double scale) {
         return new Eruption(this.radius * scale, this.depth * scale, this.ahead, (int)Math.round(this.maxFlying * scale * scale), this.speed * scale, this.power * scale);
      }
   }

   /**
    * Throw one block: the block is replaced by air and a falling-block entity
    * with the given velocity. The velocity is rounded to 1/8000 so the client
    * motion packet matches the server exactly. {@code hurtPerBlock > 0} makes
    * the block hurt what it lands on (vanilla anvil rule, capped at hurtMax).
    */
   public static FallingBlockEntity launchBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float hurtPerBlock, int hurtMax) {
      return launchBlock(level, pos, state, velocity, hurtPerBlock, hurtMax, 3);
   }

   /** Same debris physics, with caller-selected source removal flags for quiet carve work. */
   public static FallingBlockEntity launchBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, float hurtPerBlock, int hurtMax,
      int removalFlags) {
      FallingBlockEntity block = FallingBlockEntityInvoker.invokeConstructor(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, state);
      block.dropItem = false;
      block.time = 1;
      if (hurtPerBlock > 0.0F) {
         block.setHurtsEntities(hurtPerBlock, hurtMax);
      }

      block.setDeltaMovement(round(velocity.x), round(velocity.y), round(velocity.z));
      block.hasImpulse = true;
      HeroDestruction.setAir(level, pos, removalFlags);
      level.addFreshEntity(block);
      return block;
   }

   /** True when the block may be thrown: destroyable, dry, no block entity, not a two-part block. */
   public static boolean canLaunch(ServerLevel level, BlockPos pos, BlockState state) {
      return HeroDestruction.canDestroy(level, pos)
         && state.getFluidState().isEmpty()
         && !state.hasBlockEntity()
         && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
         && !state.hasProperty(BlockStateProperties.BED_PART);
   }

   /**
    * Tear the ground out in front of {@code source} and throw it forward.
    * The block under the source's own feet stays. Living things standing on
    * the torn ground take {@code entityDamage} and are thrown forward/up
    * (impulse policy honored); {@code spared} entities are skipped.
    *
    * @return the number of blocks thrown as entities
    */
   public static int erupt(ServerPlayer source, BlockPos groundPos, Vec3 forward, Eruption spec, float entityDamage, Predicate<LivingEntity> spared) {
      ServerLevel level = source.serverLevel();
      Vec3 center = Vec3.atBottomCenterOf(groundPos).add(forward.scale(spec.ahead()));
      RandomSource random = level.random;

      List<BlockPos> torn = new ArrayList<>();
      int r = (int)Math.ceil(spec.radius());
      int d = (int)Math.ceil(spec.depth());
      BlockPos base = BlockPos.containing(center.x, groundPos.getY(), center.z);
      for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -d, -r), base.offset(r, 1, r))) {
         double dx = pos.getX() + 0.5 - center.x;
         double dz = pos.getZ() + 0.5 - center.z;
         int dy = pos.getY() - groundPos.getY();
         // Ragged rim: a little noise on the radius.
         double jitter = 0.82 + random.nextDouble() * 0.3;
         if (!inEruption(dx, dy, dz, forward.x, forward.z, spec.radius() * jitter, spec.depth(), spec.ahead())) {
            continue;
         }

         // Keep the block under the source so it does not drop into its own hole.
         double px = pos.getX() + 0.5 - source.getX();
         double pz = pos.getZ() + 0.5 - source.getZ();
         if (dy <= 0 && px * px + pz * pz < 0.8) {
            continue;
         }

         if (canLaunch(level, pos, level.getBlockState(pos))) {
            torn.add(pos.immutable());
         }
      }

      int flying = 0;
      if (!torn.isEmpty()) {
         // Top layers first (cleared ground lets the lower ones out), nearest
         // first within a layer; past the cap the far rim just shatters.
         torn.sort(Comparator.<BlockPos>comparingInt(pos -> -pos.getY()).thenComparingDouble(pos -> pos.getCenter().distanceToSqr(center)));
         for (BlockPos pos : torn) {
            BlockState state = level.getBlockState(pos);
            boolean solid = !state.getCollisionShape(level, pos).isEmpty();
            if (!solid || flying >= spec.maxFlying()) {
               HeroDestruction.destroyBlock(level, pos);
               continue;
            }

            Vec3 out = new Vec3(pos.getX() + 0.5 - source.getX(), 0.0, pos.getZ() + 0.5 - source.getZ());
            out = out.lengthSqr() < 1.0E-4 ? forward : out.normalize();
            Vec3 flat = forward.scale(0.65).add(out.scale(0.35)).normalize();
            double speed = spec.speed() * (0.65 + random.nextDouble() * 0.6);
            double up = (0.45 + random.nextDouble() * 0.55 + Math.max(0, groundPos.getY() - pos.getY()) * 0.12) * spec.power();
            launchBlock(level, pos, state, new Vec3(flat.x * speed, up, flat.z * speed), 2.0F, 12);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.15);
            flying++;
         }

         level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 0.6, center.z, 14, spec.radius() * 0.4, 0.2, spec.radius() * 0.4, 0.03);
         level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 3, spec.radius() * 0.3, 0.1, spec.radius() * 0.3, 0.0);
         level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.55F);
      }

      // Whoever stands on the torn ground goes up with it.
      AABB zone = new AABB(center, center).inflate(spec.radius(), 2.0, spec.radius());
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != source && e.isAlive() && !e.isSpectator() && !spared.test(e))) {
         target.hurt(level.damageSources().playerAttack(source), entityDamage);
         if (HeroRegistry.allowsExternalControl(target, ControlKind.IMPULSE)) {
            target.setDeltaMovement(forward.scale(1.1 * spec.power()).add(0.0, 0.85 * spec.power(), 0.0));
            target.hasImpulse = true;
            target.hurtMarked = true;
            if (target instanceof ServerPlayer hitPlayer) {
               hitPlayer.connection.send(new ClientboundSetEntityMotionPacket(hitPlayer));
            }
         }
      }

      return flying;
   }

   /**
    * Pure: whether a block offset from the eruption centre is inside the
    * crater — a flattened half-ellipsoid reaching {@code depth} down and one
    * layer up (grass, plants), cut off more than {@code ahead} behind the centre.
    */
   public static boolean inEruption(double dx, double dy, double dz, double forwardX, double forwardZ, double radius, double depth, double ahead) {
      if (dy > 1.0 || dy < -depth) {
         return false;
      }

      if (dx * forwardX + dz * forwardZ < -ahead) {
         return false;
      }

      double horizontal = (dx * dx + dz * dz) / (radius * radius);
      double vertical = dy < 0.0 ? dy * dy / (depth * depth) : 0.0;
      return horizontal + vertical <= 1.0;
   }

   private static double round(double value) {
      return Math.round(value * 8000.0) / 8000.0;
   }
}
