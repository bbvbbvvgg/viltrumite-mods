package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure combat rules of Regulus Reborn (design §5–§6). */
class RegulusRulesTest {

   @Test
   void livingHostMakesTheLionFree() {
      assertEquals(500, RegulusRules.strainStep(500, true, true));
   }

   @Test
   void hostlessLionStrainsAndRestingHeartRecovers() {
      assertEquals(500 + RegulusRules.STRAIN_LION_TICK, RegulusRules.strainStep(500, true, false));
      assertEquals(500 - RegulusRules.STRAIN_RECOVERY, RegulusRules.strainStep(500, false, false));
      assertEquals(0, RegulusRules.strainStep(3, false, true));
      assertEquals(RegulusRules.STRAIN_MAX, RegulusRules.strainStep(RegulusRules.STRAIN_MAX - 1, true, false));
   }

   @Test
   void strainIsClampedAndSeizesAtMax() {
      assertEquals(RegulusRules.STRAIN_MAX, RegulusRules.strainAdd(990, RegulusRules.STRAIN_BACKLASH));
      assertEquals(0, RegulusRules.strainAdd(10, -50));
      assertTrue(RegulusRules.seizes(RegulusRules.STRAIN_MAX));
      assertFalse(RegulusRules.seizes(RegulusRules.STRAIN_MAX - 1));
   }

   @Test
   void hostlessCountdownMatchesTheStrainSteps() {
      for (int strain = 0; strain <= RegulusRules.STRAIN_MAX; strain += 7) {
         int s = strain;
         int ticks = 0;
         while (!RegulusRules.seizes(s)) {
            s = RegulusRules.strainStep(s, true, false);
            ticks++;
         }

         assertEquals(ticks, RegulusRules.hostlessTicksLeft(strain), "strain " + strain);
      }
   }

   @Test
   void aHostlessLionHoldsForSeveralSecondsFromRest() {
      // canon limit: without a living heart he can stop his own for only a few seconds
      int fromRest = RegulusRules.hostlessTicksLeft(RegulusRules.STRAIN_LION_ON);
      assertTrue(fromRest >= 60 && fromRest <= 120, "hostless hold " + fromRest + "t");
   }

   @Test
   void lionDoublesDamageAndSeveranceWidens() {
      assertTrue(RegulusRules.sandDamage(true) > RegulusRules.sandDamage(false));
      assertTrue(RegulusRules.severDamage(true) > RegulusRules.severDamage(false));
      assertEquals(RegulusRules.SEVER_WIDTH_START, RegulusRules.severWidth(-4.0), 1.0E-9);
      assertEquals(RegulusRules.SEVER_WIDTH_END, RegulusRules.severWidth(RegulusRules.SEVER_RANGE * 2), 1.0E-9);
      assertEquals((RegulusRules.SEVER_WIDTH_START + RegulusRules.SEVER_WIDTH_END) / 2.0, RegulusRules.severWidth(RegulusRules.SEVER_RANGE / 2.0), 1.0E-9);
   }

   @Test
   void recoilAndReplyScaleWithTheHit() {
      assertEquals(3.0F, RegulusRules.recoil(10.0F), 1.0E-6F);
      assertEquals(0.0F, RegulusRules.recoil(-5.0F), 1.0E-6F);
      assertEquals(RegulusRules.REPLY_DAMAGE + 5.0F, RegulusRules.replyDamage(10.0F), 1.0E-6F);
      assertEquals(RegulusRules.REPLY_DAMAGE, RegulusRules.replyDamage(-1.0F), 1.0E-6F);
   }

   @Test
   void replyWindowIsHalfOpen() {
      assertFalse(RegulusRules.replyWindow(RegulusRules.REPLY_WINDOW_START - 1));
      assertTrue(RegulusRules.replyWindow(RegulusRules.REPLY_WINDOW_START));
      assertTrue(RegulusRules.replyWindow(RegulusRules.REPLY_WINDOW_END - 1));
      assertFalse(RegulusRules.replyWindow(RegulusRules.REPLY_WINDOW_END));
   }

   @Test
   void unbreakableBlocksStayUnbroken() {
      assertFalse(RegulusRules.breakable(-1.0F, 100.0F));
      assertTrue(RegulusRules.breakable(0.0F, RegulusRules.SAND_BLOCK_HARDNESS));
      assertFalse(RegulusRules.breakable(50.0F, RegulusRules.TRAMPLE_HARDNESS_LION));
      assertTrue(RegulusRules.SAND_BLOCK_HARDNESS < RegulusRules.SEVER_BLOCK_HARDNESS);
      assertTrue(RegulusRules.TRAMPLE_HARDNESS < RegulusRules.SAND_BLOCK_HARDNESS);
      assertTrue(RegulusRules.TRAMPLE_HARDNESS_LION < RegulusRules.SEVER_BLOCK_HARDNESS);
   }

   @Test
   void ultTimelineIsOrdered() {
      assertTrue(0 < RegulusRules.ULT_SCOOP);
      assertTrue(RegulusRules.ULT_SCOOP < RegulusRules.ULT_SENTENCE);
      assertTrue(RegulusRules.ULT_SENTENCE < RegulusRules.ULT_FLICK);
      assertTrue(RegulusRules.ULT_FLICK < RegulusRules.ULT_FLICK_CONTACT);
      assertTrue(RegulusRules.ULT_FLICK_CONTACT < RegulusRules.ULT_RESOLVE);
      assertTrue(RegulusRules.ULT_RESOLVE < RegulusRules.ULT_LENGTH);
      assertEquals(RegulusRules.UltPhase.ACCUSE, RegulusRules.ultPhase(0));
      assertEquals(RegulusRules.UltPhase.SCOOP, RegulusRules.ultPhase(RegulusRules.ULT_SCOOP));
      assertEquals(RegulusRules.UltPhase.SENTENCE, RegulusRules.ultPhase(RegulusRules.ULT_SENTENCE));
      assertEquals(RegulusRules.UltPhase.FLICK, RegulusRules.ultPhase(RegulusRules.ULT_FLICK_CONTACT));
      assertEquals(RegulusRules.UltPhase.RESOLVE, RegulusRules.ultPhase(RegulusRules.ULT_LENGTH - 1));
   }

   @Test
   void exactlyFiveWavesInsideTheSentence() {
      int waves = 0;
      for (int t = 0; t < RegulusRules.ULT_LENGTH; t++) {
         if (RegulusRules.ultWaveTick(t)) {
            waves++;
            assertEquals(RegulusRules.UltPhase.SENTENCE, RegulusRules.ultPhase(t), "wave at " + t);
         }
      }

      assertEquals(RegulusRules.ULT_WAVES, waves);
   }

   @Test
   void invulnerabilityOnlyWhileTimeIsStopped() {
      assertTrue(RegulusHero.absolutelyInvulnerable(true, false));
      assertTrue(RegulusHero.absolutelyInvulnerable(false, true));
      assertFalse(RegulusHero.absolutelyInvulnerable(false, false));
      assertTrue(RegulusHero.scanInfoFor(false).protections().isEmpty());
      assertFalse(RegulusHero.scanInfoFor(true).protections().isEmpty());
   }

   @Test
   void littleKingHostRule() {
      assertTrue(LittleKing.hostValid(true, 10.0, false, 0));
      assertTrue(LittleKing.hostValid(true, RegulusRules.KING_LEASH, false, 2));
      assertFalse(LittleKing.hostValid(false, 1.0, false, 0));
      assertFalse(LittleKing.hostValid(true, RegulusRules.KING_LEASH + 0.1, false, 0));
      assertFalse(LittleKing.hostValid(true, 1.0, true, 0));
      assertFalse(LittleKing.hostValid(true, 1.0, false, 3));
      assertTrue(RegulusRules.KING_MAX_SUBJECTS >= 1);
   }

   @Test
   void touchComboCyclesAndLionHitsHarder() {
      assertEquals(RegulusMove.TOUCH_BACKHAND, RegulusMove.touch(0));
      assertEquals(RegulusMove.TOUCH_PALM, RegulusMove.touch(1));
      assertEquals(RegulusMove.TOUCH_FLICK, RegulusMove.touch(2));
      assertEquals(RegulusMove.TOUCH_BACKHAND, RegulusMove.touch(3));
      assertEquals(RegulusMove.TOUCH_FLICK, RegulusMove.touch(-1));
      for (int i = 0; i < 3; i++) {
         RegulusMove move = RegulusMove.touch(i);
         assertTrue(move.touchDamage(true) > move.touchDamage(false));
      }

      assertEquals(0.0F, RegulusMove.SAND.touchDamage(true));
   }

   @Test
   void movesHaveContactInsideTheClip() {
      for (RegulusMove move : RegulusMove.values()) {
         assertTrue(move.length() > 0, move.name());
         assertTrue(move.contact() < move.length(), move.name());
         assertEquals(move, RegulusMove.byId(move.ordinal()));
      }

      assertEquals(null, RegulusMove.byId(-1));
      assertEquals(null, RegulusMove.byId(RegulusMove.values().length));
   }

   @Test
   void trampleRingFinishesInsideTheMove() {
      for (boolean lion : new boolean[]{false, true}) {
         int travel = RegulusRules.trampleTravelTicks(lion);
         assertTrue(travel > 0);
         assertTrue(RegulusRules.TRAMPLE_IMPACT + travel < RegulusRules.TRAMPLE_TICKS, "lion=" + lion);
      }
      assertTrue(RegulusRules.trampleRadius(true) > RegulusRules.trampleRadius(false));
      assertTrue(RegulusRules.trampleDamage(true) > RegulusRules.trampleDamage(false));
      assertTrue(RegulusRules.trampleCraterRadius(true) > RegulusRules.trampleCraterRadius(false));
      assertTrue(RegulusRules.trampleBlockBudget(true) > RegulusRules.trampleBlockBudget(false));
   }

   @Test
   void trampleRingCatchesOnlyAtItsFrontAndOnTheGround() {
      double below = RegulusRules.TRAMPLE_BAND_BELOW;
      double above = RegulusRules.TRAMPLE_BAND_ABOVE;
      assertTrue(RegulusRules.trampleCatches(3.0, 0.0, 2.5, 3.4, below, above));
      // already passed / not reached yet
      assertFalse(RegulusRules.trampleCatches(1.0, 0.0, 2.5, 3.4, below, above));
      assertFalse(RegulusRules.trampleCatches(5.0, 0.0, 2.5, 3.4, below, above));
      // the front has a little slack so a fast ring does not skip anyone
      assertTrue(RegulusRules.trampleCatches(3.4 + RegulusRules.TRAMPLE_FRONT - 0.01, 0.0, 2.5, 3.4, below, above));
      // jumping over the ring dodges it on the ground
      assertFalse(RegulusRules.trampleCatches(3.0, 2.0, 2.5, 3.4, below, above));
      assertFalse(RegulusRules.trampleCatches(3.0, -2.0, 2.5, 3.4, below, above));
      // in the air the band is symmetric and wider
      double air = RegulusRules.TRAMPLE_AIR_BAND;
      assertTrue(RegulusRules.trampleCatches(3.0, -1.4, 2.5, 3.4, air, air));
   }

   @Test
   void onlyOverwhelmingHitsStrainTheStoppedHeart() {
      // global rebalance: the hosted Lion is still absolute against everyone but the S+ hero
      assertEquals(500, RegulusRules.strainFromHit(500, false));
      assertEquals(500 + dev.baranhan.viltrumitecore.balance.BalanceTable.DIVINE_LION_STRAIN, RegulusRules.strainFromHit(500, true));
      int strain = 0;
      int hits = 0;
      while (!RegulusRules.seizes(strain)) {
         strain = RegulusRules.strainStep(RegulusRules.strainFromHit(strain, true), true, true);
         hits++;
      }
      assertEquals(7, hits, "the Sword Saint forces the seizure through a living host in seven hits");
   }
}
