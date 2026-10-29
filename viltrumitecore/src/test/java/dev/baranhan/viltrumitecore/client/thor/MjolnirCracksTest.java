package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The planted hammer's crack decal: growth, grid clipping and the quarter turns. */
class MjolnirCracksTest {
   @Test
   void growsOutOfThePitAndStops() {
      assertEquals(0.35F * MjolnirCracks.RADIUS, MjolnirCracks.radius(0.0F), 1.0E-6);
      assertTrue(MjolnirCracks.radius(2.0F) > MjolnirCracks.radius(1.0F));
      assertEquals(MjolnirCracks.RADIUS, MjolnirCracks.radius(MjolnirCracks.GROW_TICKS), 1.0E-6);
      assertEquals(MjolnirCracks.RADIUS, MjolnirCracks.radius(1000.0F), 1.0E-6, "planted before it came into view: full size");
   }

   @Test
   void piecesTileTheSquareAlongTheBlockGrid() {
      List<MjolnirCracks.Piece> pieces = MjolnirCracks.pieces(0.5, 0.5, 1.0);
      assertEquals(9, pieces.size(), "centred in a block: 3 x 3 cells");
      double area = 0.0;
      for (MjolnirCracks.Piece piece : pieces) {
         assertTrue(piece.x0() >= piece.cellX() && piece.x1() <= piece.cellX() + 1);
         assertTrue(piece.z0() >= piece.cellZ() && piece.z1() <= piece.cellZ() + 1);
         area += (piece.x1() - piece.x0()) * (piece.z1() - piece.z0());
      }

      assertEquals(4.0, area, 1.0E-9);
      assertEquals(4, MjolnirCracks.pieces(1.0, 1.0, 1.0).size(), "on a block corner: 2 x 2 cells");
   }

   @Test
   void quarterTurnsKeepTheTextureInsideItsSquare() {
      assertArrayEquals(new float[]{0.0F, 0.0F}, MjolnirCracks.uv(-1.0, -1.0, 1.0, 0), 1.0E-6F);
      assertArrayEquals(new float[]{0.5F, 0.5F}, MjolnirCracks.uv(0.0, 0.0, 1.0, 3), 1.0E-6F, "centre stays the centre");
      for (int q = -1; q <= 4; q++) {
         float[] uv = MjolnirCracks.uv(1.0, -1.0, 1.0, q);
         assertTrue(uv[0] >= 0.0F && uv[0] <= 1.0F && uv[1] >= 0.0F && uv[1] <= 1.0F, "quarter " + q);
      }
      assertArrayEquals(MjolnirCracks.uv(0.3, -0.7, 1.0, 0), MjolnirCracks.uv(0.3, -0.7, 1.0, 4), 1.0E-6F);
   }
}
