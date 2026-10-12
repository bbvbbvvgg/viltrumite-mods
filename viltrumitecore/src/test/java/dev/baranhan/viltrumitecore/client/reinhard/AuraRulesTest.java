package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import org.junit.jupiter.api.Test;

class AuraRulesTest {
   @Test
   void restrainedHasNoAura() {
      assertEquals(0, AuraRules.windMotes(ReinhardMode.RESTRAINED, false));
      assertEquals(0.0, AuraRules.goldSparkChance(ReinhardMode.RESTRAINED, false), 0.0);
      assertEquals(0, AuraRules.windMotes(ReinhardMode.HOLY_SWORD, false), "Holy Sword: sparks only");
      assertEquals(0.0, AuraRules.goldSparkChance(ReinhardMode.KNIGHT, false), 0.0, "Knight: wind only");
   }

   @Test
   void knightWindCount() {
      int n = AuraRules.windMotes(ReinhardMode.KNIGHT, false);
      assertTrue(n >= 6 && n <= 10, "6-10 motes: " + n);
   }

   @Test
   void windMoteCirclesTheFeetRisesAndFades() {
      int n = AuraRules.windMotes(ReinhardMode.KNIGHT, false);
      for (int i = 0; i < n; i++) {
         for (double t = 0.0; t < 5.0; t += 0.05) {
            AuraRules.Mote mote = AuraRules.windMote(i, n, t, 1234);
            assertEquals(AuraRules.WIND_RADIUS, Math.hypot(mote.x(), mote.z()), 1.0E-6);
            assertTrue(mote.y() >= 0.0 && mote.y() <= AuraRules.WIND_RISE + 1.0E-9, "rise " + mote.y());
            assertTrue(mote.alpha() >= 0.0F && mote.alpha() <= 1.0F);
         }
      }
      // Age 0 → bright at the ground, end of life → faded at the top.
      double life = AuraRules.WIND_LIFE_SECONDS;
      AuraRules.Mote young = AuraRules.windMote(0, n, life * 0.1, 0);
      AuraRules.Mote old = AuraRules.windMote(0, n, life * 0.99, 0);
      assertTrue(young.alpha() > old.alpha());
      assertTrue(old.y() > young.y());
   }

   @Test
   void holySparkRate() {
      double expected = AuraRules.goldSparkChance(ReinhardMode.HOLY_SWORD, false) * 20 * 20;
      assertTrue(expected >= 20.0 && expected <= 30.0, "sparks per 20 s: " + expected);
   }

   @Test
   void firstPersonFilter() {
      int fp = AuraRules.windMotes(ReinhardMode.KNIGHT, true);
      assertTrue(fp > 0 && fp < AuraRules.windMotes(ReinhardMode.KNIGHT, false), "a few feet motes below the view: " + fp);
      assertEquals(0.0, AuraRules.goldSparkChance(ReinhardMode.HOLY_SWORD, true), 0.0, "body sparks hidden for the local first-person view");
      assertFalse(AuraRules.goldSparkChance(ReinhardMode.HOLY_SWORD, false) == 0.0);
   }
}
