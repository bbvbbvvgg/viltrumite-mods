package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Mind;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Power;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Reality;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Soul;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Space;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Time;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ThanosStoneRulesTest {
   @Test
   void sixDistinctStonesInSocketOrder() {
      assertEquals(6, ThanosStone.values().length);
      assertEquals(0b111111, ThanosStone.ALL);
      Set<Integer> colours = new HashSet<>();
      for (ThanosStone stone : ThanosStone.values()) {
         assertTrue(colours.add(stone.rgb), "unique colour " + stone);
         assertEquals(stone, ThanosStone.byIndex(stone.ordinal()));
      }

      assertEquals(ThanosStone.TIME, ThanosStone.first(ThanosStone.ALL), "thumb first");
      assertEquals(ThanosStone.MIND, ThanosStone.byIndex(5), "back of the hand last");
      assertNull(ThanosStone.first(0));
      assertEquals(3, ThanosStone.count(ThanosMove.COMBO_TRINITY.stones));
   }

   @Nested
   class PowerStone {
      @Test
      void waveRunsOutAlongTheGround() {
         assertEquals(0.0, Power.waveRadius(0));
         assertEquals(6.0, Power.waveRadius(10), 1.0E-9);
         assertEquals(Power.WAVE_MAX, Power.waveRadius(1000));
         assertTrue(Power.waveHits(6.0, 0.0, 10));
         assertFalse(Power.waveHits(6.0, 3.0, 10), "jumping or flying over the ring is the counterplay");
         assertFalse(Power.waveHits(9.0, 0.0, 10), "outside the band");
      }

      @Test
      void punchConeAndKnockback() {
         assertTrue(Power.inPunchCone(5.0, 0.9));
         assertFalse(Power.inPunchCone(5.0, 0.2), "behind the fist");
         assertTrue(Power.inPunchCone(1.0, -1.0), "point blank hits all around");
         assertFalse(Power.inPunchCone(10.0, 1.0));
         assertTrue(Power.punchKnockback(1.0) > Power.punchKnockback(8.0));
      }
   }

   @Nested
   class SpaceStone {
      @Test
      void stepsBehindTheTarget() {
         double[] p = Space.behind(0, 0, 0, 1, 10, 10);
         assertEquals(0.0, p[0], 1.0E-9);
         assertEquals(-Space.STEP_BEHIND, p[1], 1.0E-9);
         double[] q = Space.behind(0, 0, 0, 0, -5, 0);
         assertEquals(Space.STEP_BEHIND, q[0], 1.0E-9, "no facing: opposite of where he stands");
      }

      @Test
      void redirectSendsShotsBack() {
         double[] v = Space.redirect(1.0, 0.0, 0.5);
         assertTrue(v[0] < 0 && v[2] < 0);
         assertEquals(Space.REDIRECT_SPEED, -v[0], 1.0E-9);
      }

      @Test
      void pullStopsAtTheHand() {
         assertEquals(0.0, Space.pullSpeed(Space.PULL_STOP, 5));
         assertTrue(Space.pullSpeed(20, 0) < Space.pullSpeed(20, 5), "eases in");
         assertEquals(0.3, Space.pullSpeed(Space.PULL_STOP + 0.3, 10), 1.0E-9, "never overshoots");
      }
   }

   @Nested
   class RealityStone {
      @Test
      void zonesGrowHoldAndRevert() {
         assertEquals(0.0F, Reality.zoneScale(-1));
         assertTrue(Reality.zoneScale(0) > 0.0F);
         assertEquals(1.0F, Reality.zoneScale(Reality.ZONE_GROW + 1));
         assertTrue(Reality.zoneScale(Reality.ZONE_LIFE - 2) < 0.2F, "shrinks back before the end");
         assertEquals(0.0F, Reality.zoneScale(Reality.ZONE_LIFE), "gone: nothing left to revert");
      }

      @Test
      void zoneCountScalesAndCaps() {
         assertEquals(3, Reality.zones(1));
         assertEquals(4, Reality.zones(3));
         assertEquals(6, Reality.zones(30));
         assertTrue(Reality.inZone(1, 0, 1, 1.0F));
         assertFalse(Reality.inZone(1, 0, 1, 0.0F));
         assertFalse(Reality.inZone(0, 5, 0, 1.0F), "above the matter");
      }

      @Test
      void realMirageIsStableAndInRange() {
         for (long seed = -50; seed < 50; seed++) {
            int i = Reality.realIndex(seed, Reality.MIRAGES + 1);
            assertTrue(i >= 0 && i <= Reality.MIRAGES);
            assertEquals(i, Reality.realIndex(seed, Reality.MIRAGES + 1));
         }
      }
   }

   @Nested
   class TimeStone {
      @Test
      void domeClosesHoldsAndOpens() {
         assertEquals(1.0, Time.slowFactor(-1));
         assertEquals(1.0, Time.slowFactor(Time.DOME_LIFE));
         assertEquals(Time.HERO_SLOW, Time.slowFactor(Time.DOME_LIFE / 2), 1.0E-9);
         assertTrue(Time.slowFactor(0) > Time.HERO_SLOW && Time.slowFactor(0) < 1.0);
         assertTrue(Time.slowFactor(Time.DOME_LIFE - 1) > 0.9);
         assertTrue(Time.inside(49.0));
         assertFalse(Time.inside(49.1));
      }

      @Test
      void domeNeverStopsAHeroCompletely() {
         for (int age = 0; age < Time.DOME_LIFE; age++) {
            assertTrue(Time.slowFactor(age) >= Time.HERO_SLOW, "the hero can always walk out");
         }
      }
   }

   @Nested
   class MindStone {
      @Test
      void lookingAwayResistsTheGaze() {
         assertTrue(Mind.caught(0.95, 10));
         assertFalse(Mind.caught(0.5, 10));
         assertFalse(Mind.caught(1.0, 40), "out of range");
         assertEquals(0, Mind.disorientTicks(-1.0, 5));
         assertEquals(Mind.DISORIENT_TICKS, Mind.disorientTicks(1.0, 5));
      }

      @Test
      void ringsCanBeJumped() {
         double r = Mind.ringRadius(0, 10);
         assertTrue(Mind.ringHits(r, 0.0, r));
         assertFalse(Mind.ringHits(r, 2.0, r), "jump over");
         assertEquals(0.0, Mind.ringRadius(2, 10), "later rings start later");
      }
   }

   @Nested
   class SoulStone {
      @Test
      void tethersCapAndSnap() {
         assertEquals(1, Soul.tethers(1));
         assertEquals(3, Soul.tethers(7));
         assertTrue(Soul.tetherHolds(10));
         assertFalse(Soul.tetherHolds(Soul.TETHER_BREAK + 0.1), "run away to break it");
      }

      @Test
      void novaFallsOff() {
         assertEquals(24.0F, Soul.novaDamage(24.0F, 0.0), 1.0E-5);
         assertEquals(12.0F, Soul.novaDamage(24.0F, Soul.NOVA_RADIUS), 1.0E-5);
         assertEquals(0.0F, Soul.novaDamage(24.0F, Soul.NOVA_RADIUS + 0.01));
      }
   }
}
