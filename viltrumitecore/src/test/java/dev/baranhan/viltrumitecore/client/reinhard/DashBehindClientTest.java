package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import org.junit.jupiter.api.Test;

/** Dash Behind prediction: one packet per key edge, the local cooldown follows the server's answer. */
class DashBehindClientTest {
   @Test
   void mergedKeyTakesExactlyOnePathPerPress() {
      DashBehindClient.Route route = new DashBehindClient.Route();
      assertTrue(route.claim(true, true), "Knight press with a target: Dash Behind packet, no generic press");
      assertTrue(route.claim(false, false), "its release stays with it even after the target is gone");
      assertFalse(route.claim(true, false), "no target: the generic Step Dash press");
      assertFalse(route.claim(false, true), "and its generic release");
   }

   @Test
   void variantIsDecidedOncePerTick() {
      DashBehindClient.Decision<String> decision = new DashBehindClient.Decision<>();
      Object player = new Object();
      assertEquals("zombie", decision.get(player, 10, () -> "zombie"));
      assertEquals("zombie", decision.get(player, 10, () -> null), "the predictor and the interceptor of one tick agree");
      assertNull(decision.get(player, 11, () -> null), "the next tick resolves again");
      assertEquals("cow", decision.get(new Object(), 11, () -> "cow"), "a new player object resolves again");
   }

   @Test
   void refusalClearsLocalCooldown() {
      DashBehindClient.Edge edge = new DashBehindClient.Edge();
      assertTrue(edge.predict(true), "moved locally");
      assertFalse(edge.predict(true), "the local cooldown blocks a second prediction");
      edge.onAnswer(0);
      assertTrue(edge.predict(true), "the server refused (cooldown 0): the next press is predicted again");
   }

   @Test
   void acceptanceSyncsServerCooldown() {
      DashBehindClient.Edge edge = new DashBehindClient.Edge();
      assertTrue(edge.predict(true));
      edge.onAnswer(ReinhardRules.BEHIND_COOLDOWN - 4);
      for (int i = 0; i < ReinhardRules.BEHIND_COOLDOWN - 5; i++) {
         edge.tick();
      }

      assertFalse(edge.predict(true), "one tick left on the server's cooldown");
      edge.tick();
      assertTrue(edge.predict(true));
   }

   @Test
   void unpredictedAcceptanceStillStartsCooldown() {
      DashBehindClient.Edge edge = new DashBehindClient.Edge();
      // The client found no spot, the server did and teleported him.
      edge.onAnswer(ReinhardRules.BEHIND_COOLDOWN);
      assertFalse(edge.predict(true));
   }

   @Test
   void gateRefusalPredictsNothing() {
      DashBehindClient.Edge edge = new DashBehindClient.Edge();
      assertFalse(edge.predict(false));
      assertTrue(edge.predict(true), "a refused gate spent no local cooldown");
   }
}
