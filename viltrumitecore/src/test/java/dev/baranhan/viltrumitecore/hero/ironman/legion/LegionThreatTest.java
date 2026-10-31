package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.legion.ThreatScore.Relation;
import dev.baranhan.viltrumitecore.hero.ironman.legion.ThreatScore.Threat;
import org.junit.jupiter.api.Test;

class LegionThreatTest {
   private static final int NEVER = Integer.MAX_VALUE;

   private static Threat threat(Relation relation, int sinceHitOwner, double distance, boolean ordered) {
      return new Threat(relation, sinceHitOwner, NEVER, 4.0F, distance, true, 20.0F, 20.0F, 2.0F, false, false, ordered);
   }

   @Test
   void neverTargetsOwnerOrAllies() {
      for (Relation relation : new Relation[]{Relation.OWNER, Relation.ALLY, Relation.PROTECTED, Relation.PLAYER_NO_PVP}) {
         // Not even when it hit Tony by accident or Tony ordered the attack.
         assertFalse(ThreatScore.eligible(threat(relation, 1, 3.0, true)), relation.name());
         assertEquals(Double.NEGATIVE_INFINITY, ThreatScore.score(threat(relation, 1, 3.0, true), LegionPriority.NEAREST, 16.0));
      }
   }

   @Test
   void neutralOnlyOnAttackOrder() {
      assertFalse(ThreatScore.eligible(threat(Relation.VILLAGER, NEVER, 3.0, false)));
      // A villager stays safe even after a stray hit; only an explicit order makes it a target.
      assertFalse(ThreatScore.eligible(threat(Relation.VILLAGER, 5, 3.0, false)));
      assertTrue(ThreatScore.eligible(threat(Relation.VILLAGER, NEVER, 3.0, true)));
      assertFalse(ThreatScore.eligible(threat(Relation.NEUTRAL, NEVER, 3.0, false)));
      assertTrue(ThreatScore.eligible(threat(Relation.NEUTRAL, 5, 3.0, false)));
      assertTrue(ThreatScore.eligible(threat(Relation.HOSTILE, NEVER, 3.0, false)));
   }

   @Test
   void respectsPvpRule() {
      assertFalse(ThreatScore.eligible(threat(Relation.PLAYER_NO_PVP, 1, 2.0, true)));
      assertFalse(ThreatScore.eligible(threat(Relation.PLAYER_PVP, NEVER, 2.0, false)));
      assertTrue(ThreatScore.eligible(threat(Relation.PLAYER_PVP, 3, 2.0, false)));
      assertTrue(ThreatScore.eligible(threat(Relation.PLAYER_PVP, NEVER, 2.0, true)));
   }

   @Test
   void attackerOfTonyOutranksIdleMonster() {
      double attacker = ThreatScore.score(threat(Relation.HOSTILE, 10, 12.0, false), LegionPriority.NEAREST, 16.0);
      double idle = ThreatScore.score(threat(Relation.HOSTILE, NEVER, 4.0, false), LegionPriority.NEAREST, 16.0);
      assertTrue(attacker > idle);
   }

   @Test
   void hiddenTargetsRankLower() {
      Threat seen = threat(Relation.HOSTILE, NEVER, 8.0, false);
      Threat hidden = new Threat(Relation.HOSTILE, NEVER, NEVER, 4.0F, 8.0, false, 20.0F, 20.0F, 2.0F, false, false, false);
      assertTrue(ThreatScore.score(seen, LegionPriority.NEAREST, 16.0) > ThreatScore.score(hidden, LegionPriority.NEAREST, 16.0));
   }

   @Test
   void priorityChangesTheOrder() {
      Threat weakNear = new Threat(Relation.HOSTILE, NEVER, NEVER, 3.0F, 3.0, true, 8.0F, 20.0F, 0.0F, false, false, false);
      Threat bigFar = new Threat(Relation.HOSTILE, NEVER, NEVER, 10.0F, 14.0, true, 100.0F, 100.0F, 10.0F, false, false, false);
      Threat tonys = new Threat(Relation.HOSTILE, NEVER, NEVER, 3.0F, 14.0, true, 20.0F, 20.0F, 0.0F, false, true, false);
      assertTrue(ThreatScore.score(weakNear, LegionPriority.NEAREST, 16.0) > ThreatScore.score(bigFar, LegionPriority.NEAREST, 16.0));
      assertTrue(ThreatScore.score(bigFar, LegionPriority.STRONGEST, 16.0) > ThreatScore.score(weakNear, LegionPriority.STRONGEST, 16.0));
      assertTrue(ThreatScore.score(tonys, LegionPriority.TONY_TARGET, 16.0) > ThreatScore.score(weakNear, LegionPriority.TONY_TARGET, 16.0));
   }

   @Test
   void orderBeatsEverything() {
      Threat ordered = threat(Relation.NEUTRAL, NEVER, 30.0, true);
      Threat attacker = threat(Relation.HOSTILE, 1, 1.0, false);
      assertTrue(ThreatScore.score(ordered, LegionPriority.NEAREST, 16.0) > ThreatScore.score(attacker, LegionPriority.NEAREST, 16.0));
   }

   @Test
   void reachKeepsAttackersOfTony() {
      assertFalse(ThreatScore.inReach(threat(Relation.HOSTILE, NEVER, 30.0, false), 16.0));
      assertTrue(ThreatScore.inReach(threat(Relation.HOSTILE, 5, 30.0, false), 16.0));
      assertTrue(ThreatScore.inReach(threat(Relation.HOSTILE, NEVER, 30.0, true), 16.0));
   }
}
