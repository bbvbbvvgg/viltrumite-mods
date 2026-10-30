package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.ironman.IronManAbilities;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Spec item 7 (2026-10-28): Alt pages after Iron Man → Thor → Iron Man; Thor never sticks. */
class AbilityPagesTest {
   private static String[] thor() {
      String[] slots = new String[18];
      Arrays.fill(slots, "");
      slots[0] = "thor_a";
      slots[1] = "thor_b";
      slots[2] = "thor_c";
      slots[3] = "thor_d";
      return slots;
   }

   @Test
   void ironManAltTogglesItsTwoPages() {
      String[] iron = IronManAbilities.defaultLoadout();
      assertEquals(1, AbilityPages.next(0, iron), "page 1 -> 2");
      assertEquals(0, AbilityPages.next(1, iron), "page 2 -> 1, never the empty third page");
      assertEquals(1, AbilityPages.next(AbilityPages.next(1, iron), iron));
   }

   @Test
   void ironManThorIronManScenario() {
      String[] iron = IronManAbilities.defaultLoadout();
      int page = AbilityPages.next(0, iron);
      assertEquals(1, page, "Iron Man on his second page");
      // Hero change: the loadout is replaced and the page resets (HeroRegistry.changeHero).
      page = AbilityPages.FIRST;
      String[] thor = thor();
      assertEquals(true, AbilityPages.filled(page, thor), "Thor's keys are live on the first page");
      // Thor has one page: even a forced swap stays on it.
      assertEquals(0, AbilityPages.next(page, thor));
      // Back to Iron Man: Alt toggles 1 <-> 2 again.
      page = AbilityPages.FIRST;
      assertEquals(1, AbilityPages.next(page, iron));
      assertEquals(0, AbilityPages.next(1, iron));
   }

   @Test
   void oldSaveOnAnEmptyPageIsSettled() {
      assertEquals(0, AbilityPages.settle(1, thor()), "Thor saved on page 2 (before the reset) is moved back");
      assertEquals(0, AbilityPages.settle(2, IronManAbilities.defaultLoadout()));
      assertEquals(1, AbilityPages.settle(1, IronManAbilities.defaultLoadout()), "a filled page stays");
      assertEquals(0, AbilityPages.settle(7, thor()), "out of range");
      String[] empty = new String[18];
      Arrays.fill(empty, "");
      assertEquals(0, AbilityPages.settle(2, empty), "Human: nothing anywhere -> first page");
      assertEquals(2, AbilityPages.next(2, empty), "no filled page: Alt leaves the page alone");
   }

   @Test
   void threeFilledPagesCycleAll() {
      String[] legacy = new String[18];
      Arrays.fill(legacy, "");
      legacy[0] = "a";
      legacy[6] = "b";
      legacy[12] = "c";
      assertEquals(1, AbilityPages.next(0, legacy));
      assertEquals(2, AbilityPages.next(1, legacy));
      assertEquals(0, AbilityPages.next(2, legacy));
   }
}
