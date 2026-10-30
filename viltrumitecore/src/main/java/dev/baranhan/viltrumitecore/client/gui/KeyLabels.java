package dev.baranhan.viltrumitecore.client.gui;

import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Short key labels beside the ability slots. Built from the bound key's id
 * ({@code key.mouse.left}, {@code key.keyboard.space}, ...) instead of its
 * translated name: the old parser looked for English words in the localized
 * text, so in Russian «Левая кнопка мыши» or «Пробел» showed up as long
 * untranslated strings. Mouse buttons and named keys get translatable labels
 * ({@code hud.viltrumitecore.key.*}); letters, digits and F-keys stay as printed
 * on the keyboard. Display only — the bindings themselves are untouched.
 */
public final class KeyLabels {
   static final String PREFIX = "hud.viltrumitecore.key.";

   private KeyLabels() {
   }

   public static String shortName(KeyMapping mapping) {
      if (mapping == null || mapping.isUnbound()) {
         return "";
      }
      String id = mapping.getKey().getName();
      String literal = literal(id);
      if (literal != null) {
         return literal;
      }
      String label = labelKey(id);
      if (label != null) {
         return Component.translatable(label).getString();
      }
      return fallback(mapping.getTranslatedKeyMessage().getString());
   }

   /** Pure: letters, digits, F-keys and keypad digits as printed; null for everything else. */
   @Nullable
   static String literal(String id) {
      String k = strip(id, "key.keyboard.");
      if (k == null) {
         return null;
      }
      if (k.length() == 1 && Character.isLetterOrDigit(k.charAt(0))) {
         return k.toUpperCase(java.util.Locale.ROOT);
      }
      if (k.matches("f([1-9]|1[0-9]|2[0-5])")) {
         return k.toUpperCase(java.util.Locale.ROOT);
      }
      if (k.matches("keypad\\.[0-9]")) {
         return "N" + k.charAt(k.length() - 1);
      }
      return null;
   }

   /** Pure: the translatable short label of a mouse button or a named key, null if none. */
   @Nullable
   static String labelKey(String id) {
      String mouse = strip(id, "key.mouse.");
      if (mouse != null) {
         return switch (mouse) {
            case "left" -> PREFIX + "mouse_left";
            case "right" -> PREFIX + "mouse_right";
            case "middle" -> PREFIX + "mouse_middle";
            default -> mouse.matches("[0-9]+") ? PREFIX + "mouse_" + mouse : null;
         };
      }
      String k = strip(id, "key.keyboard.");
      if (k == null) {
         return null;
      }
      return switch (k) {
         case "space" -> PREFIX + "space";
         case "left.shift", "right.shift" -> PREFIX + "shift";
         case "left.control", "right.control" -> PREFIX + "ctrl";
         case "left.alt", "right.alt" -> PREFIX + "alt";
         case "enter", "keypad.enter" -> PREFIX + "enter";
         case "tab" -> PREFIX + "tab";
         case "caps.lock" -> PREFIX + "caps";
         case "backspace" -> PREFIX + "backspace";
         case "up" -> PREFIX + "up";
         case "down" -> PREFIX + "down";
         case "left" -> PREFIX + "left";
         case "right" -> PREFIX + "right";
         default -> null;
      };
   }

   /** Pure: any other key — its localized name, upper case, at most 5 characters. */
   static String fallback(String translated) {
      String s = translated == null ? "" : translated.trim().toUpperCase(java.util.Locale.ROOT);
      return s.length() <= 5 ? s : s.substring(0, 5);
   }

   @Nullable
   private static String strip(String id, String prefix) {
      return id != null && id.startsWith(prefix) ? id.substring(prefix.length()) : null;
   }
}
