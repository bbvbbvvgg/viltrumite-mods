package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatModule;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatTimings;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * B-m1: the drawn contact is the server's damage tick. Damage lands on
 * ENTER_MAIN (the end of the intro); the third-person clip's contact key and
 * the first-person thrust sit on that tick, the wind-up strictly before it.
 */
class ThorContactTimingTest {
   @BeforeEach
   void install() {
      ThorCombatModule.install();
      ThorCombatClips.registerAll();
   }

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   @Test
   void everyStrikeClipHitsOnTheDamageTick() {
      for (ThorStrike strike : ThorStrike.values()) {
         int damageTick = ThorAttacks.of(strike).phases().contactTick();
         ThorClips.Clip clip = ThorClips.of(strike.move(), 0, strike.pose());
         float[][] arm = clip.rightArm();
         assertEquals(damageTick, arm[2][0], 1.0E-6, strike + " contact key");
         assertTrue(arm[1][0] > 0.0F && arm[1][0] < damageTick, strike + " wind-up before the contact");
      }
   }

   @Test
   void firstPersonThrustHitsOnTheDamageTick() {
      for (ThorStrike strike : ThorStrike.values()) {
         if (strike.hand() == ThorStrike.Hand.LEFT) {
            continue;
         }

         float[][] rows = ThorFirstPerson.rows(strike.move(), strike.pose());
         assertEquals(ThorAttacks.of(strike).phases().contactTick(), rows[ThorFirstPerson.STRIKE_CONTACT][0], 1.0E-6, strike.name());
         for (int i = 1; i < rows.length; i++) {
            assertTrue(rows[i][0] > rows[i - 1][0], strike + " keys stay ordered");
         }
      }

      float[][] discharge = ThorFirstPerson.rows(ThorMove.ELECTRIC_COMBO, 1);
      assertEquals(ThorCombatTimings.of(ThorMove.ELECTRIC_COMBO).intro(), discharge[ThorFirstPerson.DISCHARGE_CONTACT][0], 1.0E-6);
      assertEquals(ThorCombatTimings.of(ThorMove.WRATH).intro(), ThorFirstPerson.wrathRows()[ThorFirstPerson.WRATH_CONTACT][0], 1.0E-6,
         "the ultimate drives down on the server's strike tick");
   }

   @Test
   void alignScalesBeforeAndShiftsAfter() {
      float[][] base = {{0, 0}, {2, 1}, {4, 2}, {7, 3}, {10, 0}};
      float[][] out = ThorFirstPerson.align(base, 2, 6);
      assertEquals(3.0F, out[1][0], 1.0E-6);
      assertEquals(6.0F, out[2][0], 1.0E-6);
      assertEquals(9.0F, out[3][0], 1.0E-6);
      assertEquals(12.0F, out[4][0], 1.0E-6);
      assertEquals(4.0F, base[2][0], 1.0E-6, "the base row is not mutated");
   }
}
