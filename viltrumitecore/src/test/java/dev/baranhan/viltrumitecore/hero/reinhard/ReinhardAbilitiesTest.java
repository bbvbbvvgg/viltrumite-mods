package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReinhardAbilitiesTest {

   @Test
   void slotsFollowSpecOrder() {
      assertEquals(18, ReinhardAbilities.LOADOUT.length);
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         int index = ability.mode().page() * 6 + ability.slot();
         assertEquals(ability.id(), ReinhardAbilities.LOADOUT[index], ability.name());
      }
   }

   @Test
   void idsUnique() {
      Set<String> ids = new HashSet<>();
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         assertTrue(ability.id().startsWith("reinhard:"), ability.id());
         assertTrue(ids.add(ability.id()), ability.id());
      }
   }

   @Test
   void actionsUnique() {
      Set<HeroAction> actions = new HashSet<>();
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         assertTrue(actions.add(ability.action()), ability.name());
      }
      assertFalse(actions.contains(HeroAction.MODE_CYCLE));
   }

   @Test
   void onlyShippedOwned() {
      assertEquals(2, ReinhardAbilities.SHIPPED_STAGE);
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         assertTrue(ability.stage() > ReinhardAbilities.SHIPPED_STAGE, ability.name());
         assertFalse(ReinhardAbilities.owns(ability.id()), ability.name());
      }
      assertFalse(ReinhardAbilities.owns("viltrumite:chop"));
      assertFalse(ReinhardAbilities.owns(null));
      for (String id : ReinhardAbilities.defaultLoadout()) {
         assertEquals("", id);
      }
      assertEquals(18, ReinhardAbilities.defaultLoadout().length);
      assertEquals(0, ReinhardAbilities.inputSlots().length);
   }

   @Test
   void chopOnHolySwordSlot2() {
      assertEquals("viltrumite:chop", ReinhardAbilities.LOADOUT[13]);
   }

   @Test
   void ordinalFitsExtraCooldowns() {
      assertTrue(ReinhardAbility.values().length <= ReinhardFlags.EXTRA_MODE_CD);
   }
}
