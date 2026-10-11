package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.mark.Mark42Parts;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkDamage;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SuitPart;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec;
import org.junit.jupiter.api.Test;

class MarkDamageTest {
   @Test
   void markAbsorbsWholeHitArmorSlowsLoss() {
      MarkDamage.Result hit = MarkDamage.apply(100.0F, 10.0F, 0.6F);
      assertTrue(hit.absorbed());
      assertFalse(hit.broke());
      assertEquals(100.0F - 10.0F * 0.6F, hit.durability(), 1.0E-4F);
      assertEquals(100.0F - 10.0F * MarkDamage.MIN_PLATING, MarkDamage.apply(100.0F, 10.0F, 0.0F).durability(), 1.0E-4F);
      assertEquals(90.0F, MarkDamage.apply(100.0F, 10.0F, Float.NaN).durability(), 1.0E-4F, "broken plating value = full loss");
   }

   @Test
   void breakingHitDoesNotSpill() {
      MarkDamage.Result hit = MarkDamage.apply(3.0F, 500.0F, 0.8F);
      assertTrue(hit.absorbed(), "the hit that empties the mark is still fully absorbed");
      assertTrue(hit.broke());
      assertEquals(0.0F, hit.durability());
      assertFalse(MarkDamage.apply(0.0F, 5.0F, 0.8F).absorbed(), "an empty mark absorbs nothing");
   }

   /** Balance pass 2026-10-11: suits differ, and none is weaker than in 1.24.1. */
   @Test
   void suitsDifferAndNeverLoseArmor() {
      // 1.24.1: armor, toughness, plating (armor-derived loss), durability
      Object[][] before = {
         {MarkId.MARK_7, 30.0, 8.0, 0.40F, 240.0F}, {MarkId.MARK_42, 30.0, 6.0, 0.40F, 200.0F}, {MarkId.MARK_15, 22.0, 4.0, 0.56F, 160.0F},
         {MarkId.MARK_39, 28.0, 6.0, 0.44F, 200.0F}, {MarkId.MARK_17, 30.0, 6.0, 0.40F, 220.0F},
         {MarkId.WAR_MACHINE_MK2, 30.0, 10.0, 0.40F, 300.0F}, {MarkId.IRON_HEART_MK3, 30.0, 12.0, 0.40F, 360.0F}
      };
      for (Object[] row : before) {
         MarkSpec spec = MarkSpec.of((MarkId)row[0]);
         assertTrue(spec.armor() >= (double)row[1], row[0] + " armor");
         assertTrue(spec.toughness() >= (double)row[2], row[0] + " toughness");
         assertTrue(spec.plating() <= (float)row[3] + 1.0E-6F, row[0] + " plating");
         assertTrue(spec.durability() >= (float)row[4], row[0] + " durability");
      }

      // Heavy suits keep more of their durability per hit than the light ones.
      assertTrue(MarkSpec.of(MarkId.IRON_HEART_MK3).plating() < MarkSpec.of(MarkId.WAR_MACHINE_MK2).plating());
      assertTrue(MarkSpec.of(MarkId.WAR_MACHINE_MK2).plating() < MarkSpec.of(MarkId.MARK_7).plating());
      assertTrue(MarkSpec.of(MarkId.MARK_7).plating() < MarkSpec.of(MarkId.MARK_15).plating());
   }

   @Test
   void suitSpecNanoDefaultsAndMarkFactors() {
      assertFalse(SuitSpec.NANO.isMark());
      assertEquals(IronManRules.NANO_ARMOR, SuitSpec.NANO.armor(), 1.0E-9);
      assertEquals(IronManRules.MISSILE_MARKS, SuitSpec.NANO.missileMarks());
      SuitSpec starboost = SuitSpec.mark(MarkId.MARK_39, 0);
      assertEquals(1.35F, starboost.flightSpeedMul(), 1.0E-6F);
      assertEquals(0.6F, starboost.sonicDrainMul(), 1.0E-6F);
      assertEquals(0.85F, starboost.weaponMul(), 1.0E-6F);
      assertTrue(SuitSpec.mark(MarkId.MARK_15, 0).stealth());
      assertTrue(SuitSpec.mark(MarkId.MARK_15, 0).silentFlight());
      assertEquals(0.2F, SuitSpec.mark(MarkId.IRON_HEART_MK3, 0).recoilMul(), 1.0E-6F);
   }

   @Test
   void mark42LosesPartsByThreshold() {
      float max = 100.0F;
      assertEquals(0, Mark42Parts.lostCount(max, max));
      assertEquals(1, Mark42Parts.lostCount(max - max / 14.0F - 0.01F, max));
      assertEquals(13, Mark42Parts.lostCount(0.5F, max), "the last part never drops: at 0 the suit breaks");
      int lostLegs = Mark42Parts.lostMask(max * 5.0F / 14.0F, max);
      assertTrue(Mark42Parts.flightFactor(lostLegs) < 1.0F, "boots lost slow the flight");
      int lostAll = Mark42Parts.lostMask(0.5F, max);
      assertTrue(Mark42Parts.chestLost(lostAll));
      assertTrue(Mark42Parts.helmetLost(lostAll));
      SuitSpec broken = SuitSpec.mark(MarkId.MARK_42, lostAll);
      assertFalse(broken.unibeamOnline(), "lost chest: no Unibeam");
      assertTrue(broken.helmetForcedOpen());
      assertEquals(0.5F, broken.weaponMul(), 1.0E-6F);
      assertEquals(0, lostAll & ~SuitPart.fullMask(MarkId.MARK_42));
   }
}
