package dev.baranhan.viltrumitecore.client.gui;

import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;

/**
 * Short key labels beside the ability slots. Built from the bound key's id
 * ({@code key.mouse.left}, {@code key.keyboard.space}, ...) instead of its
 * translated name: the old parser looked for English words in the localized
 * text, so in Russian «Левая кнопка мыши» or «Пробел» showed up as long
 * untranslated strings. Mouse buttons and named keys get translatable labels
 * ({@code hud.viltrumitecore.key.*}); letters, digits and F-keys stay as printed
 * on the keyboard. Since Captain America (2012) the panel shows English names
 * for every key on every game language ({@link #english}); the translatable
 * labels stay for compatibility. Display only — the bindings are untouched.
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
      // English on every game language (owner's request): the panel speaks keyboard, not locale
      String english = english(id);
      if (english != null) {
         return english;
      }
      return fallback(mapping.getTranslatedKeyMessage().getString());
   }

   /**
    * Pure: the English short label of a mouse button or any non-letter keyboard key, independent of the
    * game language (LMB, SPACE, ENTER, PGUP, `...); null only for ids that are neither mouse nor keyboard.
    */
   @Nullable
   static String english(String id) {
      String mouse = strip(id, "key.mouse.");
      if (mouse != null) {
         return switch (mouse) {
            case "left" -> "LMB";
            case "right" -> "RMB";
            case "middle" -> "MMB";
            default -> mouse.matches("[0-9]+") ? "M" + mouse : fallback(mouse);
         };
      }
      String k = strip(id, "key.keyboard.");
      if (k == null) {
         return null;
      }
      return switch (k) {
         case "space" -> "SPACE";
         case "left.shift", "right.shift" -> "SHIFT";
         case "left.control", "right.control" -> "CTRL";
         case "left.alt", "right.alt" -> "ALT";
         case "left.win", "right.win" -> "WIN";
         case "enter", "keypad.enter" -> "ENTER";
         case "tab" -> "TAB";
         case "caps.lock" -> "CAPS";
         case "backspace" -> "BKSP";
         case "escape" -> "ESC";
         case "insert" -> "INS";
         case "delete" -> "DEL";
         case "home" -> "HOME";
         case "end" -> "END";
         case "page.up" -> "PGUP";
         case "page.down" -> "PGDN";
         case "print.screen" -> "PRTSC";
         case "scroll.lock" -> "SCRLK";
         case "num.lock" -> "NUMLK";
         case "pause" -> "PAUSE";
         case "menu" -> "MENU";
         case "up" -> "\u2191";
         case "down" -> "\u2193";
         case "left" -> "\u2190";
         case "right" -> "\u2192";
         case "grave.accent" -> "`";
         case "minus" -> "-";
         case "equal" -> "=";
         case "left.bracket" -> "[";
         case "right.bracket" -> "]";
         case "backslash" -> "\\";
         case "semicolon" -> ";";
         case "apostrophe" -> "'";
         case "comma" -> ",";
         case "period" -> ".";
         case "slash" -> "/";
         case "keypad.add" -> "N+";
         case "keypad.subtract" -> "N-";
         case "keypad.multiply" -> "N*";
         case "keypad.divide" -> "N/";
         case "keypad.decimal" -> "N.";
         case "keypad.equal" -> "N=";
         default -> k.equals("unknown") ? null : fallback(k.replace(".", ""));
      };
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
