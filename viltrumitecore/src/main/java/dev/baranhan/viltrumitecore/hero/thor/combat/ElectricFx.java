package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Session B's electric visuals and block work: zig-zag spark arcs, rings,
 * visual-only vanilla bolts (no fire, no vanilla damage, no weather change,
 * #137), and the controlled block damage (#6, #18, #30, #104): ordinary
 * blocks only, through HeroDestruction (mobGriefing, unbreakable and
 * protected blocks spared), spread over ticks by CarveJobs.
 */
public final class ElectricFx {
   /** CarveJobs priority of Thor's craters (below big world cuts). */
   public static final int CARVE_PRIORITY = 5;

   private ElectricFx() {
   }

   /** Hand position for discharges (right hand, a little in front). */
   public static Vec3 hand(ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = CombatHits.flat(look);
      Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
      return player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0.0, -0.35, 0.0);
   }

   /** A jagged spark line. */
   public static void arc(ServerLevel level, Vec3 from, Vec3 to) {
      RandomSource random = level.getRandom();
      double length = from.distanceTo(to);
      int pieces = Math.max(2, (int)Math.ceil(length * 1.5));
      Vec3 previous = from;
      for (int i = 1; i <= pieces; i++) {
         double t = i / (double)pieces;
         Vec3 point = from.lerp(to, t);
         if (i < pieces) {
            point = point.add((random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
         }

         int dots = Math.max(2, (int)(previous.distanceTo(point) * 5));
         for (int d = 0; d < dots; d++) {
            Vec3 at = previous.lerp(point, d / (double)dots);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
         }

         previous = point;
      }

      level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 3, 0.1, 0.1, 0.1, 0.02);
   }

   /** A flat ring of sparks. */
   public static void ring(ServerLevel level, Vec3 center, double radius) {
      int points = Math.max(12, (int)(radius * 10));
      for (int i = 0; i < points; i++) {
         double angle = Math.PI * 2.0 * i / points;
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius, 1, 0.0,
            0.15, 0.0, 0.02);
      }
   }

   /** Sparks crawling on an entity / point. */
   public static void crackle(ServerLevel level, Vec3 at, int count) {
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, 0.4, 0.6, 0.4, 0.1);
   }

   /** Vanilla lightning visual only: no fire, no vanilla damage (Thor's damage is dealt separately). */
   public static void bolt(ServerLevel level, Vec3 at) {
      LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
      if (bolt != null) {
         bolt.moveTo(at.x, at.y, at.z);
         bolt.setVisualOnly(true);
         level.addFreshEntity(bolt);
      }
   }

   public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
      level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }

   /** Where the view hits a block within {@code range}, or the end of the ray. */
   public static Vec3 aim(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
   }

   /** The ground under a point (first solid block below within {@code depth}), or the point itself. */
   public static Vec3 ground(ServerLevel level, Vec3 at, int depth) {
      BlockHitResult hit = level.clip(new ClipContext(at.add(0.0, 0.5, 0.0), at.add(0.0, -depth, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
         null));
      return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
   }

   // --- block work ---

   /** Pure: a shallow bowl under {@code center} (blocks), deepest in the middle. */
   public static List<BlockPos> crater(BlockPos center, int radius, double depthScale) {
      List<BlockPos> blocks = new ArrayList<>();
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            double d2 = dx * dx + dz * dz;
            if (d2 > radius * radius + 0.5) {
               continue;
            }

            int depth = Math.max(1, (int)Math.round(Math.sqrt(radius * radius - Math.min(d2, radius * radius)) * depthScale));
            for (int dy = 0; dy < depth; dy++) {
               blocks.add(center.offset(dx, -dy, dz));
            }
         }
      }

      return blocks;
   }

   /** Pure: the one-block column above {@code from} (exclusive) the sky bolt passes through. */
   public static List<BlockPos> column(BlockPos from, int height) {
      List<BlockPos> blocks = new ArrayList<>();
      for (int dy = 1; dy <= height; dy++) {
         blocks.add(from.above(dy));
      }

      return blocks;
   }

   /** Queue the removal of ordinary blocks (HeroDestruction decides each one). */
   public static void carve(ServerLevel level, List<BlockPos> blocks) {
      if (blocks.isEmpty()) {
         return;
      }

      CarveJobs.submit(level, new Removal(blocks));
   }

   /** One CarveJobs job: removes the listed blocks a budget at a time. */
   private static final class Removal implements CarveJobs.Job {
      private final List<BlockPos> blocks;
      private int next;

      Removal(List<BlockPos> blocks) {
         this.blocks = List.copyOf(blocks);
      }

      @Override
      public boolean step(@Nullable ServerLevel level, int budget) {
         if (level == null) {
            return true;
         }

         int used = 0;
         while (this.next < this.blocks.size() && used < budget) {
            BlockPos pos = this.blocks.get(this.next++);
            // Never load or generate a chunk for a carve (same rule as HeroDestruction.clearBlock).
            if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
               HeroDestruction.destroyBlock(level, pos);
               used++;
            }
         }

         return this.next >= this.blocks.size();
      }

      @Override
      public int priority() {
         return CARVE_PRIORITY;
      }

      @Override
      public int remainingWork() {
         return this.blocks.size() - this.next;
      }
   }
}
