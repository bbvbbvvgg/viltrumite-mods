package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronStrategy.Situation;
import org.junit.jupiter.api.Test;

class UltronStrategyTest {
   private static UltronScenario scenario(UltronScenario.Variant variant) {
      for (long seed = 0; seed < 500; seed++) {
         UltronScenario s = UltronScenario.of(seed);
         if (s.variant() == variant) {
            return s;
         }
      }
      throw new AssertionError("no seed for " + variant);
   }

   private static Situation spread() {
      return new Situation(UltronPhase.SPREAD, 4, 2, 8, 14, 1, 1, 0, 0.0F, 0, 0);
   }

   private static Situation huntable() {
      return new Situation(UltronPhase.MACHINE_WAR, 0, 1, 20, 24, 2, 1, 0, 90.0F, 2, 0);
   }

   @Test
   void spreadingNetworkExpands() {
      for (UltronScenario.Variant v : UltronScenario.Variant.values()) {
         if (v != UltronScenario.Variant.HUNTER) {
            assertEquals(UltronDoctrine.EXPANSION, UltronStrategy.best(spread(), scenario(v)), v.toString());
         }
      }
   }

   @Test
   void dangerousPlayersGetHunted() {
      assertEquals(UltronDoctrine.HUNT, UltronStrategy.best(huntable(), scenario(UltronScenario.Variant.HUNTER)));
   }

   @Test
   void captureNeedsAnUplink() {
      UltronScenario quiet = scenario(UltronScenario.Variant.QUIET_INFECTION);
      Situation targets = new Situation(UltronPhase.MACHINE_WAR, 0, 2, 10, 24, 1, 2, 4, 0.0F, 1, 0);
      assertEquals(UltronDoctrine.ASSIMILATION, UltronStrategy.best(targets, quiet));
      Situation noUplink = new Situation(UltronPhase.MACHINE_WAR, 0, 2, 10, 24, 1, 0, 4, 0.0F, 1, 0);
      assertNotEquals(UltronDoctrine.ASSIMILATION, UltronStrategy.best(noUplink, quiet));
   }

   @Test
   void holdsBeforeSwitchingUnlessInvalid() {
      UltronScenario hunter = scenario(UltronScenario.Variant.HUNTER);
      Situation s = huntable();
      // siege still makes sense here: hold it until the minimum passed
      assertEquals(UltronDoctrine.SIEGE, UltronStrategy.decide(UltronDoctrine.SIEGE, UltronStrategy.MIN_HOLD - 1, s, hunter));
      assertEquals(UltronDoctrine.HUNT, UltronStrategy.decide(UltronDoctrine.SIEGE, UltronStrategy.MIN_HOLD, s, hunter));
      // assimilation without anything to capture is invalid: switch at once
      assertEquals(UltronDoctrine.HUNT, UltronStrategy.decide(UltronDoctrine.ASSIMILATION, 0, s, hunter));
   }

   @Test
   void smallAdvantagesDoNotFlipTheDoctrine() {
      UltronScenario singularity = scenario(UltronScenario.Variant.SINGULARITY);
      for (int units = 0; units <= 24; units++) {
         Situation s = new Situation(UltronPhase.MACHINE_WAR, 0, 1, units, 24, 1, 1, 0, 30.0F, 1, 0);
         UltronDoctrine best = UltronStrategy.best(s, singularity);
         for (UltronDoctrine current : UltronDoctrine.values()) {
            UltronDoctrine next = UltronStrategy.decide(current, UltronStrategy.MIN_HOLD, s, singularity);
            if (next != current) {
               assertEquals(best, next);
               float cur = UltronStrategy.score(current, s, singularity);
               assertTrue(UltronStrategy.score(best, s, singularity) >= cur * UltronStrategy.SWITCH_MARGIN
                  || cur < UltronStrategy.INVALID * singularity.doctrineWeight(current));
            }
         }
      }
   }
}
