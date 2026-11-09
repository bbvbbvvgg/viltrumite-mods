package dev.baranhan.viltrumitecore.codex.data;

import java.util.Locale;
import javax.annotation.Nullable;

/** Sections of Codex 2.0 data entries (pure). The order is the order of the tabs. */
public enum CodexCategory {
   HEROES("heroes", "\u2726"),
   BOSSES("bosses", "\u2620"),
   EVENTS("events", "\u2604"),
   DIMENSIONS("dimensions", "\u25C9"),
   COMBOS("combos", "\u2694"),
   SECRETS("secrets", "?");

   public final String key;
   /** One character drawn on toasts and locked cards. */
   public final String glyph;

   CodexCategory(String key, String glyph) {
      this.key = key;
      this.glyph = glyph;
   }

   public String titleKey() {
      return CodexEntryDef.PREFIX + "category." + this.key;
   }

   @Nullable
   public static CodexCategory byKey(@Nullable String key) {
      if (key == null) {
         return null;
      }
      String k = key.trim().toLowerCase(Locale.ROOT);
      for (CodexCategory c : values()) {
         if (c.key.equals(k)) {
            return c;
         }
      }
      return null;
   }
}
