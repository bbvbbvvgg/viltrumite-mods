package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.AbilityHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Saw Sphere (overhaul item 19) — Holy Sword slot 4, Reid drawn, in Rain of Cuts' place. The
 * press checks the headroom (4 free blocks over the head), the loaded chunks of the whole sphere
 * and the shell mask, then hands the 13 s scene to the level ({@link SawSphereManager}).
 * Cooldown 8 s from activation; the running scene blocks a new press by itself (busy).
 */
public final class SawSphere implements AbilityHandler {
   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      if (state.sawSphereActive() || !state.reid.drawn() || player.isSpectator()) return false;
      ServerLevel level = player.serverLevel();
      if (!headroom(level, player)) return false;
      double cx = player.getX(), y0 = player.getY(), cz = player.getZ();
      for (ChunkPos pos : SawSphereScene.coveredChunks(cx, cz)) {
         if (!level.hasChunk(pos.x, pos.z)) return false;
      }

      SawShell shell = SawShell.build(cx, y0, cz, SawChoreography.RADIUS, occupancy(level));
      if (shell == null) return false;
      SawSphereScene scene = new SawSphereScene(player, state, shell, player.getYRot());
      state.cooldowns[ReinhardAbility.SAW_SPHERE.ordinal()] = ReinhardRules.SAW_COOLDOWN;
      state.action.start(ReinhardAbility.SAW_SPHERE, SawChoreography.LENGTH, -1);
      state.sawSphere = scene;
      SawSphereManager.get(level).submit(scene);
      scene.begin();
      return true;
   }

   /** {@link ReinhardRules#SAW_HEADROOM} blocks over the head are free of block collision (higher roofs are cut). */
   static boolean headroom(ServerLevel level, ServerPlayer player) {
      AABB box = player.getBoundingBox();
      AABB above = new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + ReinhardRules.SAW_HEADROOM, box.maxZ);
      return !level.getBlockCollisions(player, above).iterator().hasNext();
   }

   /** Real block collision of each half-block cell (FULL inside a box, PARTIAL when one touches it); one shape lookup per block. */
   static SawShell.Occupancy occupancy(ServerLevel level) {
      Long2ObjectOpenHashMap<List<AABB>> cache = new Long2ObjectOpenHashMap<>();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      return (ix, iy, iz) -> {
         int bx = Math.floorDiv(ix, 2), by = Math.floorDiv(iy, 2), bz = Math.floorDiv(iz, 2);
         long key = BlockPos.asLong(bx, by, bz);
         List<AABB> boxes = cache.get(key);
         if (boxes == null) {
            pos.set(bx, by, bz);
            VoxelShape shape = level.isInWorldBounds(pos) ? level.getBlockState(pos).getCollisionShape(level, pos) : net.minecraft.world.phys.shapes.Shapes.empty();
            boxes = shape.isEmpty() ? List.of() : shape.toAabbs();
            cache.put(key, boxes);
         }
         if (boxes.isEmpty()) return SawShell.FREE;
         double x0 = ix * SawShell.CELL - bx, y0 = iy * SawShell.CELL - by, z0 = iz * SawShell.CELL - bz;
         double x1 = x0 + SawShell.CELL, y1 = y0 + SawShell.CELL, z1 = z0 + SawShell.CELL;
         return cellOccupancy(boxes, x0, y0, z0, x1, y1, z1);
      };
   }

   /** Pure: FULL when one box holds the cell, PARTIAL when a box overlaps its interior, else FREE (block-local coordinates). */
   static int cellOccupancy(List<AABB> boxes, double x0, double y0, double z0, double x1, double y1, double z1) {
      double e = 1.0E-6;
      boolean partial = false;
      for (AABB b : boxes) {
         if (b.minX <= x0 + e && b.minY <= y0 + e && b.minZ <= z0 + e && b.maxX >= x1 - e && b.maxY >= y1 - e && b.maxZ >= z1 - e) return SawShell.FULL;
         if (b.maxX > x0 + e && b.minX < x1 - e && b.maxY > y0 + e && b.minY < y1 - e && b.maxZ > z0 + e && b.minZ < z1 - e) partial = true;
      }
      return partial ? SawShell.PARTIAL : SawShell.FREE;
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
      SawSphereScene scene = state.sawSphere;
      if (scene != null) scene.end(true);
      state.sawSphere = null;
   }
}
