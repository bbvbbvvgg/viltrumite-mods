package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Slot ids, actions, migration of the first kit and the slot grey rule. */
class RegulusAbilitiesTest {
   private static final Path ICONS = Path.of("src/main/binassets/assets/viltrumitecore/textures/gui/ability/regulus");

   @Test
   void ownSlotsMapToDistinctActionsAndBack() {
      Set<HeroAction> actions = new HashSet<>();
      for (String id : RegulusAbilities.OWN) {
         HeroAction action = RegulusAbilities.actionFor(id);
         assertNotNull(action, id);
         assertTrue(actions.add(action), "duplicate action " + action);
         assertEquals(id, RegulusAbilities.abilityFor(action));
         assertTrue(RegulusAbilities.owns(id));
      }

      assertNull(RegulusAbilities.actionFor(null));
      assertNull(RegulusAbilities.actionFor("regulus:mania"));
      assertFalse(RegulusAbilities.owns(RegulusAbilities.GREEDS_TOUCH));
      assertFalse(RegulusAbilities.owns("thor:mjolnir"));
   }

   @Test
   void cooldownIndexFollowsTheWireOrder() {
      assertEquals(RegulusFlags.COOLDOWN_LION, RegulusAbilities.cooldownIndex(RegulusAbilities.LIONS_HEART));
      assertEquals(RegulusFlags.COOLDOWN_SAND, RegulusAbilities.cooldownIndex(RegulusAbilities.STOPPED_SAND));
      assertEquals(RegulusFlags.COOLDOWN_SEVER, RegulusAbilities.cooldownIndex(RegulusAbilities.AIR_SEVERANCE));
      assertEquals(RegulusFlags.COOLDOWN_TRAMPLE, RegulusAbilities.cooldownIndex(RegulusAbilities.TRAMPLE));
      assertEquals(RegulusFlags.COOLDOWN_REPLY, RegulusAbilities.cooldownIndex(RegulusAbilities.RIGHT_OF_REPLY));
      assertEquals(RegulusFlags.COOLDOWN_ULT, RegulusAbilities.cooldownIndex(RegulusAbilities.KINGDOM_OF_ONE));
      assertEquals(-1, RegulusAbilities.cooldownIndex(null));
   }

   @Test
   void retiredIdsMigrateInPlace() {
      String[] slots = {"regulus:lions_heart", "regulus:debris_kick", "", "regulus:mania", "regulus:greeds_embrace", "regulus:counter",
         "regulus:evangelium", "regulus:punch", null, "thor:mjolnir"};
      RegulusAbilities.migrate(slots);
      assertArrayEquals(new String[]{RegulusAbilities.LIONS_HEART, RegulusAbilities.STOPPED_SAND, "", RegulusAbilities.AIR_SEVERANCE,
         RegulusAbilities.TRAMPLE, RegulusAbilities.RIGHT_OF_REPLY, RegulusAbilities.KINGDOM_OF_ONE, "", null, "thor:mjolnir"}, slots);
   }

   @Test
   void theRetiredProcessionBecomesTrample() {
      String[] slots = {"regulus:procession", "regulus:procession"};
      RegulusAbilities.migrate(slots);
      assertArrayEquals(new String[]{RegulusAbilities.TRAMPLE, RegulusAbilities.TRAMPLE}, slots);
      assertTrue(RegulusAbilities.owns(RegulusAbilities.TRAMPLE));
   }

   @Test
   void previousDefaultsMigrateToTheNewDefaultPage() {
      String[] fresh = RegulusAbilities.defaultLoadout();
      assertEquals(18, fresh.length);
      for (String[] old : RegulusAbilities.previousDefaultLoadouts()) {
         String[] migrated = RegulusAbilities.migrate(old.clone());
         for (int i = 0; i < 5; i++) {
            assertEquals(fresh[i], migrated[i], "slot " + i);
         }
      }
   }

   @Test
   void everySlotAndPanelIconExists() {
      for (String id : RegulusAbilities.OWN) {
         assertTrue(RegulusAbilities.icon(id).getPath().endsWith("/regulus/" + RegulusAbilities.key(id) + ".png"));
         assertTrue(Files.exists(ICONS.resolve(RegulusAbilities.key(id) + ".png.b64")), id);
      }

      assertTrue(Files.exists(ICONS.resolve("greeds_touch.png.b64")));
      assertTrue(Files.exists(ICONS.resolve("little_king.png.b64")));
      assertNull(RegulusAbilities.icon("regulus:mania"));
   }

   @Test
   void greyRule() {
      int[] none = new int[6];
      int[] lionCooling = {40, 0, 0, 0, 0, 0};
      int lion = RegulusFlags.with(0, RegulusFlags.LION, true);
      assertFalse(RegulusAbilities.grey(RegulusFlags.COOLDOWN_SAND, none, 0));
      assertTrue(RegulusAbilities.grey(RegulusFlags.COOLDOWN_SAND, none, RegulusFlags.with(0, RegulusFlags.SEIZED, true)));
      assertTrue(RegulusAbilities.grey(RegulusFlags.COOLDOWN_LION, lionCooling, 0));
      // the active Lion can always be released
      assertFalse(RegulusAbilities.grey(RegulusFlags.COOLDOWN_LION, lionCooling, lion));
      assertTrue(RegulusAbilities.grey(RegulusFlags.COOLDOWN_LION, none, RegulusFlags.with(0, RegulusFlags.LION_LOCKED, true)));
      // Trample works with and without the Lion
      assertFalse(RegulusAbilities.grey(RegulusFlags.COOLDOWN_TRAMPLE, none, 0));
      assertFalse(RegulusAbilities.grey(RegulusFlags.COOLDOWN_TRAMPLE, none, lion));
      assertTrue(RegulusAbilities.grey(RegulusFlags.COOLDOWN_TRAMPLE, new int[]{0, 0, 0, 5, 0, 0}, lion));
      assertTrue(RegulusAbilities.grey(RegulusFlags.COOLDOWN_ULT, new int[]{0, 0, 0, 0, 0, 9}, lion));
      assertFalse(RegulusAbilities.grey(-1, none, RegulusFlags.with(0, RegulusFlags.SEIZED, true)));
   }

   @Test
   void flagBitsRoundTrip() {
      int flags = 0;
      for (int bit = 0; bit <= RegulusFlags.REPLY_ARMED; bit++) {
         flags = RegulusFlags.with(flags, bit, true);
         assertTrue(RegulusFlags.has(flags, bit));
      }

      flags = RegulusFlags.with(flags, RegulusFlags.SEIZED, false);
      assertFalse(RegulusFlags.has(flags, RegulusFlags.SEIZED));
      assertTrue(RegulusFlags.has(flags, RegulusFlags.LION));
   }
}
