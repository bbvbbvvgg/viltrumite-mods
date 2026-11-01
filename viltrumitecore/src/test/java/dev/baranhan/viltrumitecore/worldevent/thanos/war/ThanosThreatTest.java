package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who hurts the war most (decaying scores) and why Thanos does not flip between targets. */
class ThanosThreatTest {
   private static final UUID A = new UUID(0L, 1L);
   private static final UUID B = new UUID(0L, 2L);
   private static final UUID C = new UUID(0L, 3L);

   @Test
   void scoresHalveEachHalfLifeAndAreForgotten() {
      ThanosThreat threat = new ThanosThreat();
      threat.hit(A, 10.0F, false);
      for (int i = 0; i < ThanosThreat.HALF_LIFE; i++) {
         threat.tick();
      }

      assertEquals(5.0, threat.score(A), 1.0E-6);
      for (int i = 0; i < 20 * ThanosThreat.HALF_LIFE; i++) {
         threat.tick();
      }

      assertEquals(0.0, threat.score(A), "forgotten");
      threat.hit(B, 0.0F, true);
      threat.hit(B, -3.0F, true);
      assertEquals(0.0, threat.score(B), "no hit, no score");
      assertEquals(0.0, threat.score(null));
   }

   @Test
   void theRangedShare() {
      ThanosThreat threat = new ThanosThreat();
      threat.hit(A, 6.0F, true);
      threat.hit(A, 4.0F, false);
      assertEquals(0.6, threat.rangedShare(A), 1.0E-6);
      threat.tick();
      assertEquals(0.6, threat.rangedShare(A), 1.0E-6, "the share survives the decay");
      assertEquals(0.0, threat.rangedShare(B));
      assertEquals(0.0, threat.rangedShare(null));
   }

   @Test
   void heHoldsHisTargetAndSwitchesOnlyWhenClearlyBeaten() {
      ThanosThreat threat = new ThanosThreat();
      List<UUID> both = List.of(A, B);
      assertNull(threat.pick(null, both, 0), "no scores, no one");
      assertEquals(A, threat.pick(A, both, 1000), "no scores: keeps his target");

      threat.hit(A, 10.0F, false);
      threat.hit(B, 14.0F, false);
      assertEquals(B, threat.pick(null, both, 0), "the highest score");
      assertEquals(A, threat.pick(A, both, ThanosThreat.HOLD - 1), "held for a while");
      assertEquals(A, threat.pick(A, both, ThanosThreat.HOLD), "14 does not clearly beat 10");
      threat.hit(B, 2.0F, false);
      assertEquals(B, threat.pick(A, both, ThanosThreat.HOLD), "16 beats 10 * 1.3 + 2");
      assertEquals(B, threat.pick(A, List.of(B, C), 0), "his target left: the highest of the rest");
      assertNull(threat.pick(A, List.of(C), 0), "his target left and no one else scored");
      threat.clear();
      assertEquals(0.0, threat.score(B));
   }
}
