package dev.baranhan.viltrumitecore.hero.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.balance.HeroTier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resistance ladder: stronger bodies get shorter, harder, costlier books; S+ is read-only and never locked forever. */
class BookProfileTest {
   @Test
   void tierLadderIsMonotonic() {
      HeroTier[] order = {HeroTier.B, HeroTier.A, HeroTier.A_PLUS, HeroTier.S, HeroTier.S_PLUS};
      BookProfile prev = null;
      for (HeroTier t : order) {
         BookProfile p = BookProfile.forTier(t);
         if (prev != null) {
            assertTrue(p.windowTicks() <= prev.windowTicks(), t + " window");
            assertTrue(p.resistChance() >= prev.resistChance(), t + " resist");
            assertTrue(p.costScale() >= prev.costScale(), t + " cost");
            assertTrue(p.durationScale() <= prev.durationScale(), t + " duration");
         }
         prev = p;
      }
   }

   @Test
   void sPlusIsReadOnlyAndReflects() {
      BookProfile p = BookProfile.forTier(HeroTier.S_PLUS);
      assertEquals(BookAccess.READ_ONLY, p.access());
      assertFalse(p.writable());
      assertTrue(p.reflectTicks() > 0);
      assertTrue(p.partialReading());
   }

   @Test
   void everyWritableProfileHasFiniteCap() {
      for (BookProfile p : List.of(BookProfile.ANIMAL, BookProfile.SIMPLE_MOB, BookProfile.SMART_MOB, BookProfile.CITIZEN,
         BookProfile.SUMMON, BookProfile.BOSS, BookProfile.PLAYER, BookProfile.forTier(HeroTier.S), BookProfile.forTier(HeroTier.A))) {
         assertTrue(p.windowTicks() > 0);
         assertTrue(p.resistChance() >= 0 && p.resistChance() <= 1);
         assertTrue(p.costScale() > 0);
      }
      assertTrue(BookProfile.PLAYER.maxInstruction() > 0, "players are never written into forever");
      assertTrue(BookProfile.BOSS.maxInstruction() > 0, "bosses are never written into forever");
   }

   @Test
   void withersKeepOtherFields() {
      BookProfile p = BookProfile.SMART_MOB.withAccess(BookAccess.CLOSED).withWindow(0).withNotes("x").withSenses(true, true);
      assertEquals(BookAccess.CLOSED, p.access());
      assertEquals(1, p.windowTicks(), "window is clamped to at least one tick");
      assertEquals(BookProfile.SMART_MOB.resistChance(), p.resistChance());
      assertEquals(List.of("x"), p.notes());
      assertTrue(p.sightImmune() && p.guardsFront());
      assertFalse(p.writable());
      assertEquals(BookClass.SMART_MOB, p.bookClass());
   }

   @Test
   void defaultTiersFallBackToPlayer() {
      assertEquals(BookProfile.PLAYER, BookProfile.forTier(HeroTier.B));
      assertEquals(BookProfile.PLAYER, BookProfile.forTier(HeroTier.NONE));
   }
}
