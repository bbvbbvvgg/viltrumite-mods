package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UltronIntelTest {
   private static final UUID A = new UUID(1L, 1L);
   private static final UUID B = new UUID(2L, 2L);

   @Test
   void sectorsGoAroundTheCentre() {
      assertEquals(0, UltronIntel.sector(0, 0, 10, 1));
      assertEquals(1, UltronIntel.sector(0, 0, 0.1, 10));
      assertEquals(2, UltronIntel.sector(0, 0, -0.1, 10));
      assertEquals(4, UltronIntel.sector(0, 0, -10, -0.1));
      assertEquals(7, UltronIntel.sector(0, 0, 10, -1));
      for (int s = 0; s < UltronIntel.SECTORS; s++) {
         double a = UltronIntel.sectorAngle(s);
         assertEquals(s, UltronIntel.sector(5, 5, 5 + Math.cos(a) * 30, 5 + Math.sin(a) * 30));
      }
   }

   @Test
   void focusNeedsConcentratedLosses() {
      UltronIntel intel = new UltronIntel();
      for (int i = 0; i < 3; i++) {
         intel.lost(2);
      }
      assertEquals(-1, intel.focus(), "too few");
      intel.lost(2);
      assertEquals(2, intel.focus());
      for (int i = 0; i < 4; i++) {
         intel.lost(5);
      }
      assertEquals(-1, intel.focus(), "spread over two sectors");
      intel.lost(-1);
      intel.lost(99);
      assertEquals(8.0F, intel.totalLosses(), 1.0E-5);
   }

   @Test
   void threatTracksTheDangerousPlayerAndFades() {
      UltronIntel intel = new UltronIntel();
      intel.hurtBy(A, 50.0F);
      assertNull(intel.get(A), "nobody saw him: no threat on record");
      intel.saw(A, 0, 64, 0, 0, false);
      intel.saw(B, 10, 80, 0, 0, true);
      intel.hurtBy(A, 30.0F);
      intel.killedBy(B, 10.0F);
      assertSame(intel.get(A), intel.top(0, 100));
      assertEquals(30.0F, intel.topThreat(0, 100));
      assertTrue(intel.aerial(0, 100));
      assertEquals(2, intel.seen(0, 100));
      for (int s = 1; s <= (int)UltronIntel.THREAT_HALF_LIFE_S; s++) {
         intel.second(s * UltronRules.TPS);
      }
      assertEquals(15.0F, intel.get(A).threat, 0.2F);
      assertEquals(0, intel.seen(UltronIntel.FORGET_TICKS + 200, 100), "old sightings are outside the window");
      intel.second(UltronIntel.FORGET_TICKS + 1);
      assertNull(intel.get(A), "forgotten");
   }
}
