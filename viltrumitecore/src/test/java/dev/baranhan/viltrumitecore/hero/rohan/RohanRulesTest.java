package dev.baranhan.viltrumitecore.hero.rohan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.balance.HeroTier;
import dev.baranhan.viltrumitecore.hero.book.BookClass;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instruction;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instructions;
import org.junit.jupiter.api.Test;

/** Heaven's Door numbers: line duration and its caps, ink, inspiration, the touch cone, PvP limits. */
class RohanRulesTest {
   @Test
   void lineTicksScaleCapAndStruggle() {
      assertEquals(0, RohanRules.lineTicks(0, BookProfile.SIMPLE_MOB, 0), "a class that cannot take the line");
      assertEquals(400, RohanRules.lineTicks(400, BookProfile.SIMPLE_MOB, 0));
      assertEquals(Math.min(BookProfile.BOSS.maxInstruction(), Math.round(400 * BookProfile.BOSS.durationScale())),
         RohanRules.lineTicks(400, BookProfile.BOSS, 0), "a boss holds a line briefly");
      int calm = RohanRules.lineTicks(100, BookProfile.PLAYER, 0);
      int fought = RohanRules.lineTicks(100, BookProfile.PLAYER, 3);
      assertEquals(calm - 3 * RohanRules.STRUGGLE_LINE, fought, "struggling shortens a line");
      assertEquals(20, RohanRules.lineTicks(100, BookProfile.PLAYER, 1000), "never below one second once it took");
   }

   /** PvP: no instruction pins a player for long, whatever the tier (design §8: no permanent lock). */
   @Test
   void playersAreNeverLockedForLong() {
      for (Instruction i : Instructions.all()) {
         int base = i.baseTicks(BookClass.PLAYER);
         for (HeroTier tier : HeroTier.values()) {
            BookProfile p = BookProfile.forTier(tier);
            int t = RohanRules.lineTicks(base, p, 0);
            assertTrue(t <= Math.max(100, p.maxInstruction()), i.id() + " on " + tier + " lasts " + t);
         }
      }
      assertFalse(BookProfile.forTier(HeroTier.S_PLUS).writable(), "S+ books are read-only");
      assertEquals(0, BookProfile.forTier(HeroTier.S_PLUS).lines());
   }

   @Test
   void inkAndInspiration() {
      assertTrue(RohanRules.inkPerTick(RohanRules.CALM_TICKS) > RohanRules.inkPerTick(0), "ink comes back faster at rest");
      assertTrue(RohanRules.inkPerTick(0) > 0.0F);
      assertEquals(RohanRules.INSPIRATION_REREAD, RohanRules.inspiration(false, BookClass.BOSS, true));
      assertEquals(RohanRules.INSPIRATION_BOSS, RohanRules.inspiration(true, BookClass.BOSS, false));
      assertTrue(RohanRules.inspiration(true, BookClass.SIMPLE_MOB, true) >= RohanRules.inspiration(true, BookClass.SIMPLE_MOB, false));
      assertEquals(15.0F * BookProfile.BOSS.costScale(), RohanRules.lineCost(15.0F, BookProfile.BOSS), 1.0E-6);
      assertEquals(0.0F, RohanRules.lineCost(-5.0F, BookProfile.PLAYER));
   }

   @Test
   void coneAndRange() {
      assertTrue(RohanRules.inCone(0, 0, 1, 0, 0, 5, 30));
      assertTrue(RohanRules.inCone(0, 0, 1, 1, 0, 3, 30));
      assertFalse(RohanRules.inCone(0, 0, 1, 0, 0, -5, 30), "behind him");
      assertFalse(RohanRules.inCone(0, 0, 1, 5, 0, 1, 30));
      assertTrue(RohanRules.doorRange(true) > RohanRules.doorRange(false), "a marked body is opened from further away");
   }

   @Test
   void flagsAreDistinctBits() {
      int[] masks = {RohanFlags.STAND_OUT, RohanFlags.SKETCH, RohanFlags.MANUSCRIPT, RohanFlags.READING, RohanFlags.COUNTER, RohanFlags.IN_COMBAT,
         RohanFlags.FLYING};
      int all = 0;
      for (int m : masks) {
         assertEquals(1, Integer.bitCount(m));
         assertEquals(0, all & m);
         all |= m;
         assertTrue(RohanFlags.has(all, m));
      }
      assertFalse(RohanFlags.has(0, RohanFlags.READING));
   }
}
