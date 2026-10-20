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
         if (ability != ReinhardAbility.SHEATH_STRIKE) assertEquals(ReinhardAbilities.active(ability)?ability.id():"", ReinhardAbilities.LOADOUT[index], ability.name());
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
      assertEquals(6, ReinhardAbilities.SHIPPED_STAGE);
      String[] loadout = ReinhardAbilities.defaultLoadout();
      assertEquals(18, loadout.length);
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         boolean shipped = ReinhardAbilities.active(ability);
         assertEquals(shipped, ReinhardAbilities.owns(ability.id()), ability.name());
         int index = ability.mode().page() * 6 + ability.slot();
         if(ability!=ReinhardAbility.SHEATH_STRIKE) assertEquals(shipped ? ability.id() : "", loadout[index], ability.name());
         else assertEquals(ReinhardAbility.DIAGONAL_WORLD_CUT.id(),loadout[index]);
      }
      assertTrue(ReinhardAbilities.owns("viltrumite:chop"), "the kit chop ships with stage 4");
      assertEquals("viltrumite:chop", loadout[13]);
      assertFalse(ReinhardAbilities.owns(null));
      assertEquals("reinhard:world_erasure", loadout[17], "Holy Sword slot 6 ships with Stage 6");
   }

   @Test
   void chopOnHolySwordSlot2() {
      assertEquals("viltrumite:chop", ReinhardAbilities.LOADOUT[13]);
   }

   @Test
   void ordinalFitsExtraCooldowns() {
      Set<Integer> used=new HashSet<>();
      for(var a:ReinhardAbility.values()) {
         assertTrue(used.add(a.snapshotIndex()));
         assertTrue(a.snapshotIndex()!=ReinhardFlags.EXTRA_MODE_CD && a.snapshotIndex()!=ReinhardFlags.EXTRA_PHOENIX);
      }
      assertEquals(16,ReinhardAbility.DIAGONAL_WORLD_CUT.snapshotIndex());
   }
}
