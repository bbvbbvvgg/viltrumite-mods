package dev.baranhan.viltrumitecore.codex.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A catalog text: either one plain string (a literal or a lang key) or per-locale strings
 * ({@code {"ru_ru": "...", "en_us": "..."}}, short {@code ru}/{@code en} accepted). Pure: the caller passes
 * the game language and a lang lookup.
 */
public final class LocalText {
   public static final LocalText EMPTY = new LocalText(null, Map.of());
   @Nullable
   private final String plain;
   private final Map<String, String> locales;

   private LocalText(@Nullable String plain, Map<String, String> locales) {
      this.plain = plain == null || plain.isBlank() ? null : plain.strip();
      this.locales = locales;
   }

   public static LocalText of(@Nullable String plain) {
      return plain == null || plain.isBlank() ? EMPTY : new LocalText(plain, Map.of());
   }

   public static LocalText ofLocales(Map<String, String> raw) {
      Map<String, String> map = new LinkedHashMap<>();
      raw.forEach((k, v) -> {
         if (k != null && v != null && !v.isBlank()) {
            map.put(normalizeLocale(k), v.strip());
         }
      });
      return map.isEmpty() ? EMPTY : new LocalText(null, Collections.unmodifiableMap(map));
   }

   static String normalizeLocale(String key) {
      String k = key.toLowerCase(Locale.ROOT).replace('-', '_');
      return switch (k) {
         case "ru" -> "ru_ru";
         case "en", "default", "text" -> "en_us";
         default -> k;
      };
   }

   public boolean isEmpty() {
      return this.plain == null && this.locales.isEmpty();
   }

   /**
    * Text for {@code language} (e.g. {@code ru_ru}): exact locale, then English, then any locale. A plain
    * string goes through {@code translate} (which returns null when it is not a known lang key).
    */
   public String resolve(String language, Function<String, String> translate) {
      if (this.plain != null) {
         String t = translate.apply(this.plain);
         return t != null ? t : this.plain;
      }
      if (this.locales.isEmpty()) {
         return "";
      }
      String lang = normalizeLocale(language == null ? "en_us" : language);
      String v = this.locales.get(lang);
      if (v == null && lang.contains("_")) {
         String prefix = lang.substring(0, lang.indexOf('_') + 1);
         for (Map.Entry<String, String> e : this.locales.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
               v = e.getValue();
               break;
            }
         }
      }
      if (v == null) {
         v = this.locales.get("en_us");
      }
      if (v == null) {
         v = this.locales.values().iterator().next();
      }
      String t = translate.apply(v);
      return t != null ? t : v;
   }

   @Override
   public String toString() {
      return this.plain != null ? this.plain : this.locales.toString();
   }
}
