package dev.baranhan.viltrumitecore.client.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Playtest: ability key labels showed English / long localized names; Homelander had an empty flight slot. */
class HudLabelsTest {
   @Test
   void lettersDigitsAndFunctionKeysStayAsPrinted() {
      assertEquals("R", KeyLabels.literal("key.keyboard.r"));
      assertEquals("7", KeyLabels.literal("key.keyboard.7"));
      assertEquals("F4", KeyLabels.literal("key.keyboard.f4"));
      assertEquals("N3", KeyLabels.literal("key.keyboard.keypad.3"));
      assertNull(KeyLabels.literal("key.keyboard.space"));
      assertNull(KeyLabels.literal("key.mouse.left"));
   }

   @Test
   void mouseAndNamedKeysAreTranslatable() {
      assertEquals(KeyLabels.PREFIX + "mouse_left", KeyLabels.labelKey("key.mouse.left"));
      assertEquals(KeyLabels.PREFIX + "mouse_right", KeyLabels.labelKey("key.mouse.right"));
      assertEquals(KeyLabels.PREFIX + "mouse_middle", KeyLabels.labelKey("key.mouse.middle"));
      assertEquals(KeyLabels.PREFIX + "mouse_4", KeyLabels.labelKey("key.mouse.4"));
      assertEquals(KeyLabels.PREFIX + "space", KeyLabels.labelKey("key.keyboard.space"));
      assertEquals(KeyLabels.PREFIX + "shift", KeyLabels.labelKey("key.keyboard.left.shift"));
      assertEquals(KeyLabels.PREFIX + "ctrl", KeyLabels.labelKey("key.keyboard.right.control"));
      assertEquals(KeyLabels.PREFIX + "enter", KeyLabels.labelKey("key.keyboard.keypad.enter"));
      assertNull(KeyLabels.labelKey("key.keyboard.grave.accent"));
   }

   @Test
   void keyNamesAreEnglishOnEveryLanguage() {
      assertEquals("LMB", KeyLabels.english("key.mouse.left"));
      assertEquals("RMB", KeyLabels.english("key.mouse.right"));
      assertEquals("MMB", KeyLabels.english("key.mouse.middle"));
      assertEquals("M4", KeyLabels.english("key.mouse.4"));
      assertEquals("SPACE", KeyLabels.english("key.keyboard.space"));
      assertEquals("SHIFT", KeyLabels.english("key.keyboard.left.shift"));
      assertEquals("ENTER", KeyLabels.english("key.keyboard.keypad.enter"));
      assertEquals("INS", KeyLabels.english("key.keyboard.insert"));
      assertEquals("PGUP", KeyLabels.english("key.keyboard.page.up"));
      assertEquals("`", KeyLabels.english("key.keyboard.grave.accent"));
      assertEquals("N+", KeyLabels.english("key.keyboard.keypad.add"));
      assertEquals("WORLD", KeyLabels.english("key.keyboard.world.1").substring(0, 5));
      assertNull(KeyLabels.english("key.keyboard.unknown"));
      assertNull(KeyLabels.english("scancode.42"));
   }

   @Test
   void otherKeysUseAShortLocalizedName() {
      assertEquals("`", KeyLabels.fallback("`"));
      assertEquals("ВСТАВ", KeyLabels.fallback("Вставка"));
      assertEquals("", KeyLabels.fallback(null));
   }

   @Test
   void flightRowShowsOwnedAbilitiesOnly() {
      String[] ids = {"viltrumite:speed_lock", "viltrumite:fast_takeoff", "viltrumite:supersonic_flight"};
      Set<String> homelander = Set.of("viltrumite:fast_takeoff", "viltrumite:supersonic_flight");
      assertArrayEquals(new String[]{"viltrumite:fast_takeoff", "viltrumite:supersonic_flight"}, FlightRow.visible(ids, homelander::contains));
      assertArrayEquals(ids, FlightRow.visible(ids, id -> true), "Viltrumite keeps all three");
      assertArrayEquals(ids, FlightRow.visible(ids, id -> false), "a hero without flight keeps the old row");
   }
}
