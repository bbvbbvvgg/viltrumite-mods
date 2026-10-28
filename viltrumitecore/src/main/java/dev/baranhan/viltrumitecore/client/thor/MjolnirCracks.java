package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Ground cracks and dent around a planted world Mjölnir (rework §6): a
 * decal drawn by the hammer's own renderer, so it exists exactly while the
 * planted body does and is gone the moment the hammer is lifted, recalled
 * or falls (no blocks change, no state to clean up). Lies only on block
 * tops level with the rest point: it is clipped cell by cell, so it never
 * hangs over an edge, a step, a slab of another height or into the air.
 * Grows out of the pit over {@link #GROW_TICKS} after the landing. Client.
 */
public final class MjolnirCracks {
   /** Decal radius at full size, blocks (texture 32 px over 2 blocks = the vanilla 16 px grid). */
   public static final float RADIUS = 1.0F;
   /** Ticks the cracks take to spread after the hammer plants itself. */
   public static final float GROW_TICKS = 5.0F;
   /** Above the ground against z-fighting. */
   private static final float LIFT = 0.004F;
   /** A ground top this close to the rest point counts as level with it. */
   private static final double LEVEL = 0.03;

   /** One clipped piece of the decal over one block cell (world x / z, texture u / v before the quarter turn). */
   public record Piece(double x0, double z0, double x1, double z1, int cellX, int cellZ) {
   }

   private MjolnirCracks() {
   }

   /** Pure: radius {@code age} ticks after planting, ease-out from a third to full. */
   public static float radius(float age) {
      float t = Mth.clamp(age / GROW_TICKS, 0.0F, 1.0F);
      float ease = 1.0F - (1.0F - t) * (1.0F - t);
      return RADIUS * (0.35F + 0.65F * ease);
   }

   /** Pure: the decal square around (x, z) cut along the block grid. */
   public static List<Piece> pieces(double x, double z, double radius) {
      List<Piece> out = new ArrayList<>();
      int minX = Mth.floor(x - radius);
      int maxX = Mth.floor(x + radius - 1.0E-7);
      int minZ = Mth.floor(z - radius);
      int maxZ = Mth.floor(z + radius - 1.0E-7);
      for (int cx = minX; cx <= maxX; cx++) {
         for (int cz = minZ; cz <= maxZ; cz++) {
            out.add(new Piece(Math.max(x - radius, cx), Math.max(z - radius, cz), Math.min(x + radius, cx + 1), Math.min(z + radius, cz + 1), cx, cz));
         }
      }

      return out;
   }

   /** Pure: texture coordinate of a world offset (-r..r) under one of four quarter turns (the head's yaw, snapped). */
   public static float[] uv(double dx, double dz, double radius, int quarter) {
      float u = (float)((dx / radius + 1.0) * 0.5);
      float v = (float)((dz / radius + 1.0) * 0.5);
      return switch (Math.floorMod(quarter, 4)) {
         case 1 -> new float[]{1.0F - v, u};
         case 2 -> new float[]{1.0F - u, 1.0F - v};
         case 3 -> new float[]{v, 1.0F - u};
         default -> new float[]{u, v};
      };
   }

   static void render(MjolnirEntity hammer, float partialTick, PoseStack stack, VertexConsumer buffer) {
      Level level = hammer.level();
      Vec3 rest = hammer.getPosition(partialTick);
      double radius = radius(hammer.plantedAge(partialTick));
      int quarter = Math.round(hammer.getYRot() / 90.0F);
      int groundY = Mth.floor(rest.y - 1.0E-4);
      PoseStack.Pose pose = stack.last();
      Matrix4f matrix = pose.pose();
      Matrix3f normal = pose.normal();
      for (Piece piece : pieces(rest.x, rest.z, radius)) {
         int cellY = groundTop(level, piece.cellX(), piece.cellZ(), groundY, rest.y);
         if (cellY == Integer.MIN_VALUE) {
            continue;
         }

         int light = LevelRenderer.getLightColor(level, new BlockPos(piece.cellX(), cellY + 1, piece.cellZ()));
         float y = LIFT;
         vertex(buffer, matrix, normal, piece.x0(), y, piece.z0(), rest, radius, quarter, light);
         vertex(buffer, matrix, normal, piece.x0(), y, piece.z1(), rest, radius, quarter, light);
         vertex(buffer, matrix, normal, piece.x1(), y, piece.z1(), rest, radius, quarter, light);
         vertex(buffer, matrix, normal, piece.x1(), y, piece.z0(), rest, radius, quarter, light);
      }
   }

   /** Block y whose collision top is level with the rest point (and not covered), or MIN_VALUE. */
   private static int groundTop(Level level, int x, int z, int groundY, double restY) {
      for (int y = groundY; y >= groundY - 1; y--) {
         BlockPos pos = new BlockPos(x, y, z);
         BlockState state = level.getBlockState(pos);
         VoxelShape shape = state.getCollisionShape(level, pos);
         if (shape.isEmpty()) {
            continue;
         }

         double top = y + shape.max(Direction.Axis.Y);
         if (Math.abs(top - restY) <= LEVEL) {
            BlockPos above = pos.above();
            return top < y + 1.0 - 1.0E-4 || !level.getBlockState(above).isCollisionShapeFullBlock(level, above) ? y : Integer.MIN_VALUE;
         }
      }

      return Integer.MIN_VALUE;
   }

   private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, double wx, float y, double wz, Vec3 rest, double radius, int quarter,
      int light) {
      float[] uv = uv(wx - rest.x, wz - rest.z, radius, quarter);
      buffer.vertex(matrix, (float)(wx - rest.x), y, (float)(wz - rest.z)).color(1.0F, 1.0F, 1.0F, 1.0F).uv(uv[0], uv[1])
         .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
   }
}
