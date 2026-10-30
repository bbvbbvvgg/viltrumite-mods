package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import dev.baranhan.viltrumitecore.hero.ironman.IronManAbilities;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AbilityPagesTest {
   private static String[] thor() {
      String[] slots = new String[18];
      Arrays.fill(slots, "");
      slots[0] = "thor_a";
      slots[1] = "thor_b";
      return slots;
   }

   @Test void ironManAltTogglesOnlyTwoFilledPages() {
      String[] iron = IronManAbilities.defaultLoadout();
      assertEquals(1, AbilityPages.next(0, iron));
      assertEquals(0, AbilityPages.next(1, iron));
   }

   @Test void heroSwitchDoesNotStrandThorOnIronMansPage() {
      int ironPage = AbilityPages.next(0, IronManAbilities.defaultLoadout());
      assertEquals(1, ironPage);
      int newHeroPage = AbilityPages.FIRST;
      assertEquals(0, AbilityPages.next(newHeroPage, thor()));
      assertEquals(1, AbilityPages.next(AbilityPages.FIRST, IronManAbilities.defaultLoadout()));
   }

   @Test void staleSavedPagesAreRepaired() {
      assertEquals(0, AbilityPages.settle(1, thor()));
      assertEquals(0, AbilityPages.settle(2, IronManAbilities.defaultLoadout()));
      assertEquals(0, AbilityPages.settle(7, thor()));
      String[] empty = new String[18];
      Arrays.fill(empty, "");
      assertEquals(0, AbilityPages.settle(2, empty));
      assertEquals(2, AbilityPages.next(2, empty));
   }

   @Test void threeFilledPagesCycle() {
      String[] slots = new String[18];
      Arrays.fill(slots, "");
      slots[0] = "a"; slots[6] = "b"; slots[12] = "c";
      assertEquals(1, AbilityPages.next(0, slots));
      assertEquals(2, AbilityPages.next(1, slots));
      assertEquals(0, AbilityPages.next(2, slots));
   }
}
