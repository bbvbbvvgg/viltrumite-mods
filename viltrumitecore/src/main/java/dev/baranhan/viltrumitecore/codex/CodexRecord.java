package dev.baranhan.viltrumitecore.codex;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Pure per-player encyclopedia progress (no Minecraft types, unit-tested): which heroes the player has
 * actually chosen and which contextual interactions he has discovered. Ids are short lowercase keys
 * ({@code thor}, {@code thunder_and_vibranium}); anything else is dropped, so a bad catalog or a forged
 * packet never grows the set without bound.
 */
public final class CodexRecord {
   public static final int MAX_ID = 64;
   public static final int MAX_HEROES = 64;
   public static final int MAX_INTERACTIONS = 1024;
   private static final Pattern ID = Pattern.compile("[a-z0-9_.:\\-/]{1,64}");

   private final Set<String> heroes = new LinkedHashSet<>();
   private final Set<String> interactions = new LinkedHashSet<>();

   public CodexRecord() {
   }

   public CodexRecord(Collection<String> heroes, Collection<String> interactions) {
      heroes.forEach(this::addHero);
      interactions.forEach(this::addInteraction);
   }

   public static boolean validId(@Nullable String id) {
      return id != null && ID.matcher(id).matches();
   }

   /** Normalises a catalog/command id: trimmed, lowercase; null when it is not a valid id. */
   @Nullable
   public static String normalize(@Nullable String id) {
      if (id == null) {
         return null;
      }
      String n = id.trim().toLowerCase(java.util.Locale.ROOT);
      return validId(n) ? n : null;
   }

   /** True when the hero was new. */
   public boolean addHero(@Nullable String key) {
      String n = normalize(key);
      return n != null && this.heroes.size() < MAX_HEROES && this.heroes.add(n);
   }

   /** True when the interaction was new. */
   public boolean addInteraction(@Nullable String id) {
      String n = normalize(id);
      return n != null && this.interactions.size() < MAX_INTERACTIONS && this.interactions.add(n);
   }

   public boolean removeInteraction(@Nullable String id) {
      String n = normalize(id);
      return n != null && this.interactions.remove(n);
   }

   public boolean hasHero(@Nullable String key) {
      String n = normalize(key);
      return n != null && this.heroes.contains(n);
   }

   public boolean hasInteraction(@Nullable String id) {
      String n = normalize(id);
      return n != null && this.interactions.contains(n);
   }

   public Set<String> heroes() {
      return Collections.unmodifiableSet(this.heroes);
   }

   public Set<String> interactions() {
      return Collections.unmodifiableSet(this.interactions);
   }

   public void clear() {
      this.heroes.clear();
      this.interactions.clear();
   }

   public CodexRecord copy() {
      return new CodexRecord(this.heroes, this.interactions);
   }
}
