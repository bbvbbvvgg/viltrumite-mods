package dev.baranhan.viltrumitecore.codex.data;

import java.util.List;
import java.util.Set;

/**
 * One data-driven Codex 2.0 article (pure). Texts are lang keys {@code codex2.viltrumitecore.<id>.title},
 * {@code .hint} (shown while it is locked) and {@code .<section>} per section; section headings are shared
 * keys {@code codex2.viltrumitecore.section.<section>}.
 */
public record CodexEntryDef(String id, CodexCategory category, int accent, int order, Visual visual, List<String> sections,
   List<UnlockCondition> unlock, List<String> relations) {
   public static final String PREFIX = "codex2.viltrumitecore.";

   /**
    * The portrait: {@code hero} (ref = hero key), {@code skin} (ref = texture), {@code entity} (ref = entity
    * type), {@code halo} or {@code glyph} (the category glyph only).
    */
   public record Visual(String type, String ref, boolean slim) {
      public static final Set<String> TYPES = Set.of("hero", "skin", "entity", "halo", "glyph");

      public static Visual glyph() {
         return new Visual("glyph", "", false);
      }
   }

   public CodexEntryDef {
      sections = List.copyOf(sections);
      unlock = List.copyOf(unlock);
      relations = List.copyOf(relations);
   }

   public String titleKey() {
      return PREFIX + this.id + ".title";
   }

   public String hintKey() {
      return PREFIX + this.id + ".hint";
   }

   public String sectionKey(String section) {
      return PREFIX + this.id + "." + section;
   }

   public static String sectionTitleKey(String section) {
      return PREFIX + "section." + section;
   }

   public boolean openByDefault() {
      return this.unlock.isEmpty() || this.unlock.stream().anyMatch(c -> c.type() == UnlockCondition.Type.DEFAULT);
   }

   public boolean hasCondition(UnlockCondition.Type type) {
      return this.unlock.stream().anyMatch(c -> c.type() == type);
   }
}
