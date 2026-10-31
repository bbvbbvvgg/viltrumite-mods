package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regressions for the five Codex review findings on PR #98. */
class LegionReviewRegressionTest {
   private static ThreatScore.Threat orderedThreat(double toOwner) {
      return new ThreatScore.Threat(ThreatScore.Relation.HOSTILE, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, 3.0F, toOwner, true,
         20.0F, 20.0F, 2, false, false, true);
   }

   // ---- #1 ATTACK ignores a distant explicit target

   @ParameterizedTest
   @ValueSource(doubles = {16.0, 28.0, 29.0, 40.0, 63.0, 64.0})
   void orderedTargetReachesTheBoardAtEveryPickDistance(double distance) {
      Vec3 owner = Vec3.ZERO;
      Vec3 target = new Vec3(distance, 0.0, 0.0);
      AABB scan = new AABB(owner, owner).inflate(LegionRules.SCAN_RADIUS);
      List<String> scanned = new ArrayList<>();
      if (scan.contains(target)) {
         scanned.add("target");
      }

      // The pick accepts it, the board must see it once, and the score keeps it in reach.
      assertTrue(distance <= LegionRules.ORDER_REACH);
      List<String> board = LegionSquad.withOrdered(scanned, "target");
      assertEquals(1, board.stream().filter("target"::equals).count(), "ordered target exactly once at " + distance);
      assertTrue(ThreatScore.eligible(orderedThreat(distance)));
      assertTrue(ThreatScore.inReach(orderedThreat(distance), LegionOrder.ATTACK.engageRadius()));
   }

   @Test
   void scanBoxAloneMissedThe29BlockTarget() {
      AABB scan = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(LegionRules.SCAN_RADIUS);
      assertFalse(scan.contains(new Vec3(29.0, 0.0, 0.0)), "the bug: the 28-block box does not hold a 29-block target");
      assertEquals(List.of("a", "b"), LegionSquad.withOrdered(List.of("a", "b"), null));
      assertEquals(List.of("a", "b"), LegionSquad.withOrdered(List.of("a", "b"), "b"));
   }

   // ---- #2 own Legion blocks Tony's signature rays

   @ParameterizedTest
   @ValueSource(ints = {1, 2, 3})
   void raysPassThroughOwnSuitsToTheEnemyBehind(int suitsInFront) {
      UUID tony = UUID.randomUUID();
      UUID enemyId = UUID.randomUUID();
      record Body(String name, UUID commander, boolean suit) {
      }

      List<Body> alongRay = new ArrayList<>();
      for (int i = 0; i < suitsInFront; i++) {
         alongRay.add(new Body("suit" + i, tony, true));
      }

      alongRay.add(new Body("zombie", null, false));
      // Tony shoots; then one of his suits shoots along the same line.
      Body first = alongRay.stream().filter(b -> !LegionAllies.sameSquad(tony, false, b.commander(), b.suit())).findFirst().orElseThrow();
      assertEquals("zombie", first.name());
      Body fromSuit = alongRay.stream().filter(b -> !LegionAllies.sameSquad(tony, true, b.commander(), b.suit())).findFirst().orElseThrow();
      assertEquals("zombie", fromSuit.name());
      assertFalse(LegionAllies.sameSquad(tony, false, enemyId, true), "another player's suit still stops the ray");
      assertFalse(LegionAllies.sameSquad(tony, false, tony, false), "a player is never spared from himself by this rule");
      assertFalse(LegionAllies.sameSquad(null, true, null, true));
   }

   // ---- #3 HOLD teleports suits to the owner

   @Test
   void holdMeasuresTheFallbackFromTheHeldPoint() {
      Vec3 hold = new Vec3(100.0, 64.0, 100.0);
      Vec3 owner = new Vec3(300.0, 64.0, 100.0);
      Vec3 suitAtHold = hold.add(3.0, 5.0, 0.0);
      Vec3 anchor = LegionSuitBrain.fallbackAnchor(LegionOrder.HOLD, hold, owner);
      assertSame(hold, anchor);
      assertTrue(suitAtHold.distanceTo(owner) > LegionRules.TELEPORT_DISTANCE, "Tony walked off far");
      assertTrue(suitAtHold.distanceTo(anchor) < LegionRules.TELEPORT_DISTANCE, "no fallback while the suit holds its point");
      for (LegionOrder order : new LegionOrder[] {LegionOrder.GUARD, LegionOrder.FOLLOW, LegionOrder.ATTACK, LegionOrder.RETURN}) {
         assertSame(owner, LegionSuitBrain.fallbackAnchor(order, hold, owner), order + " still follows Tony");
      }

      assertSame(owner, LegionSuitBrain.fallbackAnchor(LegionOrder.HOLD, null, owner), "HOLD without a point falls back to Tony");
   }

   // ---- #4 two Legions hurt each other with PvP off

   @Test
   void legionHitsFollowTheOwnersPvpRules() {
      assertFalse(LegionAllies.pvpAllowed(false, false, true, false), "PvP off: no damage between Legions");
      assertFalse(LegionAllies.pvpAllowed(false, true, false, false), "same team without friendly fire: no damage");
      assertFalse(LegionAllies.pvpAllowed(false, true, true, true), "spectator: no damage");
      assertFalse(LegionAllies.pvpAllowed(true, true, true, false), "own owner: never");
      assertTrue(LegionAllies.pvpAllowed(false, true, true, false), "PvP on, foes: damage lands");
   }

   // ---- #5 orders bypass canAct

   @Test
   void ordersNeedTheFullLegionActionGate() {
      assertTrue(IronManLegion.commandAllowed(true, true, true, false, false));
      assertFalse(IronManLegion.commandAllowed(false, true, true, false, false), "hero changed");
      assertFalse(IronManLegion.commandAllowed(true, false, true, false, false), "dead");
      assertFalse(IronManLegion.commandAllowed(true, true, false, false, false), "canAct refuses (spectator, anchored, suit entry)");
      assertFalse(IronManLegion.commandAllowed(true, true, true, true, false), "action lock");
      assertFalse(IronManLegion.commandAllowed(true, true, true, false, true), "held by an anchoring hero");
   }
}
