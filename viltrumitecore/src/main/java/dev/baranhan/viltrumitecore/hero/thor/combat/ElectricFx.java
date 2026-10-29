package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Session B's electric visuals and block work: zig-zag spark arcs, rings,
 * client-drawn lightning bolts (one FX packet; no LightningBolt entity, so no
 * fire, vanilla damage, weather, copper, lightning rod, sculk event, sky
 * flash or world-wide thunder, #137 / B-m6), and the controlled block damage (#6, #18, #30, #104): ordinary
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

   /**
    * A jagged spark line. One compact FX packet per viewer (B-M8); each
    * client draws the sparks itself ({@code HeroImpactFx}, same look as the
    * old server particles: ~5 sparks a block plus an END_ROD tip).
    */
   public static void arc(ServerLevel level, Vec3 from, Vec3 to) {
      HeroFx.electricArc(level, from, to);
   }

   /** A flat ring of sparks (one packet per viewer, drawn by the client). */
   public static void ring(ServerLevel level, Vec3 center, double radius) {
      HeroFx.electricRing(level, center, radius);
   }

   /** Sparks crawling on an entity / point (one packet per viewer). */
   public static void crackle(ServerLevel level, Vec3 at, int count) {
      HeroFx.electricCrackle(level, at, count);
   }

   /** Pure: sparks the client draws along one arc (the old server count: ~5 a block, at least 2 per piece). */
   public static int arcSparks(double length) {
      int pieces = Math.max(2, (int)Math.ceil(length * 1.5));
      return Math.max(pieces * 2, (int)Math.ceil(length * 5.0));
   }

   /** Pure: sparks of one ring of {@code radius} (the old server count). */
   public static int ringSparks(double radius) {
      return Math.max(12, (int)(radius * 10));
   }

   /** Height of the drawn bolt channel above the strike point. */
   public static final float BOLT_HEIGHT = 40.0F;

   /**
    * A lightning bolt onto {@code at}, described by the server and drawn by
    * each nearby client (B-m6): no entity, so nothing in the world reacts to
    * a visual (Thor's damage is dealt separately by the move).
    */
   public static void bolt(ServerLevel level, Vec3 at) {
      HeroFx.electricBolt(level, at, BOLT_HEIGHT, 1.0F, ThorSounds.boltCrack());
      ThorSounds.bolt(level, at, 1.0F);
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

   /**
    * The sky bolt's path above {@code from}: air passes, ordinary blocks are
    * carved, and the first block it may not break (unbreakable, protected,
    * mobGriefing off) or an unloaded position ends the path; nothing above
    * it is cut (blocked, not jumped over).
    */
   public static List<BlockPos> skyPath(ServerLevel level, BlockPos from, int height) {
      List<BlockPos> path = new ArrayList<>();
      for (BlockPos pos : column(from, height)) {
         if (!level.isLoaded(pos) || pos.getY() >= level.getMaxBuildHeight()) {
            break;
         }

         if (level.getBlockState(pos).isAir()) {
            continue;
         }

         if (!HeroDestruction.canDestroy(level, pos)) {
            break;
         }

         path.add(pos);
      }

      return path;
   }

   /** Pure twin of {@link #skyPath} over a per-block verdict (tests): AIR passes, BREAK is cut, STOP ends the path. */
   public enum PathBlock {
      AIR,
      BREAK,
      STOP
   }

   public static int skyPathLength(List<PathBlock> column) {
      int cut = 0;
      for (PathBlock block : column) {
         if (block == PathBlock.STOP) {
            break;
         } else if (block == PathBlock.BREAK) {
            cut++;
         }
      }

      return cut;
   }

   /** Queue the removal of ordinary blocks (HeroDestruction decides each one). */
   public static void carve(ServerLevel level, List<BlockPos> blocks) {
      if (blocks.isEmpty()) {
         return;
      }

      CarveJobs.submit(level, new CarveJobs.BlockList(blocks, CARVE_PRIORITY, HeroDestruction::destroyBlock));
   }
}
