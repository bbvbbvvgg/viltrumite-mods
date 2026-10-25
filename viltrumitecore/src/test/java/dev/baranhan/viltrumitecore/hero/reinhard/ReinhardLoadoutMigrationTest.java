package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import org.junit.jupiter.api.Test;

/** PR #41 review: retired Reinhard ids migrate instead of resetting all 18 slots. */
class ReinhardLoadoutMigrationTest {
   private static final String CHOP = ReinhardAbilities.CHOP;

   @Test
   void untouchedFormerDefaultsMoveToTheNewDefault() {
      for (String[] old : ReinhardAbilities.PREVIOUS_DEFAULTS) {
         assertNull(HeroRegistry.repairedSlots(old.clone(), ReinhardHero.INSTANCE));
      }
   }

   @Test
   void customizedLegacyLoadoutKeepsItsOtherSlots() {
      String[] saved = {
         "reinhard:step_dash", "", "reinhard:catch", "", "", "",
         "reinhard:sky_kick", "reinhard:dash_behind", "reinhard:stomp", "reinhard:grab_throw", "reinhard:reflect", "",
         "reinhard:reid", CHOP, "reinhard:diagonal_world_cut", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"};
      String[] repaired = HeroRegistry.repairedSlots(saved, ReinhardHero.INSTANCE);
      assertArrayEquals(new String[]{
         "reinhard:step_dash", "", "", "", "", "",
         "reinhard:sky_kick", "reinhard:step_dash", "", "", "", "",
         "reinhard:reid", CHOP, "reinhard:step_dash", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"}, repaired);
      for (String id : repaired) {
         assertTrue(id.isEmpty() || ReinhardAbilities.owns(id), id);
      }
   }

   @Test
   void successorTakesTheRetiredSlotWhenThePageLacksIt() {
      String[] saved = new String[18];
      java.util.Arrays.fill(saved, "");
      saved[11] = "reinhard:stomp";
      saved[16] = "reinhard:diagonal_world_cut";
      String[] migrated = ReinhardAbilities.migrate(saved);
      assertEquals("reinhard:sky_kick", migrated[11]);
      assertEquals("reinhard:world_cut", migrated[16]);
   }

   @Test
   void migrationIsIdempotentAndLeavesCurrentLayoutsAlone() {
      String[] current = ReinhardAbilities.defaultLoadout();
      assertArrayEquals(current, ReinhardAbilities.migrate(current));
      String[] saved = ReinhardAbilities.PREVIOUS_DEFAULTS[0].clone();
      saved[0] = "";
      String[] once = ReinhardAbilities.migrate(saved);
      assertArrayEquals(once, ReinhardAbilities.migrate(once));
   }

   @Test
   void foreignIdsStillResetTheLoadout() {
      String[] saved = ReinhardAbilities.defaultLoadout();
      saved[3] = "viltrumite:punch";
      assertNull(HeroRegistry.repairedSlots(saved, ReinhardHero.INSTANCE));
   }

   @Test
   void otherHeroesAreNotMigrated() {
      String[] slots = {"a", "", "b"};
      HeroRegistry.registerDefaults();
      for (var id : dev.baranhan.viltrumitecore.hero.HeroId.values()) {
         if (id == dev.baranhan.viltrumitecore.hero.HeroId.REINHARD) continue;
         assertArrayEquals(slots, HeroRegistry.get(id).migrateLoadout(slots.clone()), id.name());
      }
   }
}
