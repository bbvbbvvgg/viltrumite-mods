package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutErasureProbe;
import org.junit.jupiter.api.Test;

class WorldCutErasureTest {
   @Test void firstPieceIsCutAtBlast() { assertEquals(80.0, WorldCutErasureProbe.cutTick(0), 1E-9); }
   @Test void lastPieceStartsBeforeTheFrontEnds() {
      assertTrue(WorldCutErasureProbe.cutTick(WorldCutErasureProbe.segments() - 1) < 110.0);
   }
   @Test void headIsTheVisualFront() {
      for (int k = 0; k < WorldCutErasureProbe.segments(); k++) {
         assertEquals(WorldCutErasureProbe.offset(k), WorldCutErasureProbe.front(WorldCutErasureProbe.cutTick(k)), 1E-4);
         assertEquals(0F, WorldCutErasureProbe.strokeTime(k, WorldCutErasureProbe.cutTick(k)), 1E-6);
      }
   }
   @Test void piecesOverlapAndStayInsideTheCut() {
      for (int k = 0; k + 1 < WorldCutErasureProbe.segments(); k++)
         assertTrue(WorldCutErasureProbe.offset(k) + WorldCutErasureProbe.length(k) > WorldCutErasureProbe.offset(k + 1));
      int last = WorldCutErasureProbe.segments() - 1;
      assertEquals(200.0, WorldCutErasureProbe.offset(last) + WorldCutErasureProbe.length(last), 1E-3);
   }
   @Test void chainIsBoundedInTime() {
      assertFalse(WorldCutErasureProbe.active(60));
      assertTrue(WorldCutErasureProbe.active(80));
      assertTrue(WorldCutErasureProbe.active(120));
      assertFalse(WorldCutErasureProbe.active(140));
   }
   @Test void axisFollowsTheSurfaceSmoothly() {
      int[] surface = new int[200];
      for (int i = 100; i < 200; i++) surface[i] = 10;
      var axis = WorldCutErasureProbe.axis(surface);
      assertEquals(201, axis.length);
      for (int i = 1; i < axis.length; i++) assertTrue(Math.abs(axis[i].y - axis[i - 1].y) <= 10.0 / 7 + 1E-9);
      assertEquals(2.0, axis[0].horizontalDistance() - 0, 1E-9);
   }
}
