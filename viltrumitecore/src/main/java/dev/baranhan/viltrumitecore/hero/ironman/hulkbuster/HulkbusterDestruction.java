package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Block destruction of the Hulkbuster (rework spec §8, owner decision 5): only soft blocks under a hardness
 * threshold, only with the mobGriefing gamerule (HeroDestruction.canDestroy), always through the shared
 * CarveJobs budget (never a direct burst of setBlock).
 */
public final class HulkbusterDestruction {
   /** Steps: glass, leaves, flowers, snow (hardness ≤ 0.5). */
   public static final float STEP_HARDNESS = 0.5F;
   /** Punches, throws, dash shoulder, ram: up to wood and glass panes (≤ 2.0). */
   public static final float PUNCH_HARDNESS = 2.0F;
   /** Ultimate crater: up to stone bricks and concrete (≤ 3.0), never obsidian or bedrock. */
   public static final float CRATER_HARDNESS = 3.0F;
   private static final int PRIORITY = 2;

   private HulkbusterDestruction() {
   }

   public static boolean soft(BlockState state, float hardness) {
      return !state.isAir() && hardness >= 0.0F && hardness <= 50.0F && state.getFluidState().isEmpty();
   }

   /** Soft blocks in a sphere around a point. */
   public static void soft(ServerLevel level, Vec3 center, double radius, float maxHardness) {
      if (radius <= 0.0 || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      List<BlockPos> blocks = new ArrayList<>();
      int r = (int)Math.ceil(radius);
      BlockPos origin = BlockPos.containing(center);
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
         if (Vec3.atCenterOf(pos).distanceTo(center) <= radius && breakable(level, pos, maxHardness)) {
            blocks.add(pos.immutable());
         }
      }

      submit(level, blocks);
   }

   /** The crack of the seismic rift: one row of soft surface blocks along the line. */
   public static void line(ServerLevel level, Vec3 start, Vec3 dir, double length, double groundY) {
      if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      List<BlockPos> blocks = new ArrayList<>();
      for (double d = 0.0; d <= length; d += 0.7) {
         Vec3 at = start.add(dir.scale(d));
         BlockPos pos = BlockPos.containing(at.x, groundY + 1.0, at.z);
         for (int dy = 0; dy < 2; dy++) {
            BlockPos p = pos.above(dy);
            if (breakable(level, p, STEP_HARDNESS) && !blocks.contains(p)) {
               blocks.add(p);
            }
         }
      }

      submit(level, blocks);
   }

   /** The ultimate's crater: a bowl of radius r below and around the impact. */
   public static void crater(ServerLevel level, Vec3 center, double radius, float maxHardness) {
      if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      List<BlockPos> blocks = new ArrayList<>();
      int r = (int)Math.ceil(radius);
      BlockPos origin = BlockPos.containing(center.x, center.y - 0.5, center.z);
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, 2, r))) {
         Vec3 c = Vec3.atCenterOf(pos);
         double dx = c.x - center.x;
         double dz = c.z - center.z;
         double dy = (c.y - center.y) * 1.6;
         if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius && breakable(level, pos, maxHardness)) {
            blocks.add(pos.immutable());
         }
      }

      submit(level, blocks);
   }

   private static boolean breakable(ServerLevel level, BlockPos pos, float maxHardness) {
      if (!level.isLoaded(pos) || !HeroDestruction.canDestroy(level, pos)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      float hardness = state.getDestroySpeed(level, pos);
      return soft(state, hardness) && hardness <= maxHardness && !state.hasBlockEntity();
   }

   private static void submit(ServerLevel level, List<BlockPos> blocks) {
      if (!blocks.isEmpty()) {
         CarveJobs.submit(level, new CarveJobs.BlockList(blocks, PRIORITY, HeroDestruction::destroyBlock));
      }
   }
}
