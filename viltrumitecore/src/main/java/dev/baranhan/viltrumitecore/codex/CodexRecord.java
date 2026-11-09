package dev.baranhan.viltrumitecore.codex;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Pure per-player encyclopedia progress (no Minecraft types, unit-tested): which heroes the player has
 * actually chosen, which contextual interactions he has discovered, which Codex 2.0 entries he has opened
 * and which hero tutorials he has finished. Ids are short lowercase keys ({@code thor},
 * {@code boss.thanos}); anything else is dropped, so a bad catalog or a forged packet never grows a set
 * without bound.
 */
public final class CodexRecord {
   public static final int MAX_ID = 64;
   public static final int MAX_HEROES = 64;
   public static final int MAX_INTERACTIONS = 1024;
   public static final int MAX_ENTRIES = 512;
   public static final int MAX_TUTORIALS = 64;
   private static final Pattern ID = Pattern.compile("[a-z0-9_.:\\-/]{1,64}");

   private final Set<String> heroes = new LinkedHashSet<>();
   private final Set<String> interactions = new LinkedHashSet<>();
   private final Set<String> entries = new LinkedHashSet<>();
   private final Set<String> tutorials = new LinkedHashSet<>();

   public CodexRecord() {
   }

   public CodexRecord(Collection<String> heroes, Collection<String> interactions) {
      this(heroes, interactions, List.of(), List.of());
   }

   public CodexRecord(Collection<String> heroes, Collection<String> interactions, Collection<String> entries, Collection<String> tutorials) {
      heroes.forEach(this::addHero);
      interactions.forEach(this::addInteraction);
      entries.forEach(this::addEntry);
      tutorials.forEach(this::addTutorial);
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

   private static boolean add(Set<String> set, int max, @Nullable String id) {
      String n = normalize(id);
      return n != null && set.size() < max && set.add(n);
   }

   private static boolean remove(Set<String> set, @Nullable String id) {
      String n = normalize(id);
      return n != null && set.remove(n);
   }

   private static boolean has(Set<String> set, @Nullable String id) {
      String n = normalize(id);
      return n != null && set.contains(n);
   }

   /** True when the hero was new. */
   public boolean addHero(@Nullable String key) {
      return add(this.heroes, MAX_HEROES, key);
   }

   /** True when the interaction was new. */
   public boolean addInteraction(@Nullable String id) {
      return add(this.interactions, MAX_INTERACTIONS, id);
   }

   /** True when the Codex 2.0 entry was newly opened. */
   public boolean addEntry(@Nullable String id) {
      return add(this.entries, MAX_ENTRIES, id);
   }

   /** True when the hero's tutorial was newly marked finished. */
   public boolean addTutorial(@Nullable String hero) {
      return add(this.tutorials, MAX_TUTORIALS, hero);
   }

   public boolean removeInteraction(@Nullable String id) {
      return remove(this.interactions, id);
   }

   public boolean removeEntry(@Nullable String id) {
      return remove(this.entries, id);
   }

   public boolean hasHero(@Nullable String key) {
      return has(this.heroes, key);
   }

   public boolean hasInteraction(@Nullable String id) {
      return has(this.interactions, id);
   }

   public boolean hasEntry(@Nullable String id) {
      return has(this.entries, id);
   }

   public boolean hasTutorial(@Nullable String hero) {
      return has(this.tutorials, hero);
   }

   public Set<String> heroes() {
      return Collections.unmodifiableSet(this.heroes);
   }

   public Set<String> interactions() {
      return Collections.unmodifiableSet(this.interactions);
   }

   public Set<String> entries() {
      return Collections.unmodifiableSet(this.entries);
   }

   public Set<String> tutorials() {
      return Collections.unmodifiableSet(this.tutorials);
   }

   public void clear() {
      this.heroes.clear();
      this.interactions.clear();
      this.entries.clear();
      this.tutorials.clear();
   }

   public CodexRecord copy() {
      return new CodexRecord(this.heroes, this.interactions, this.entries, this.tutorials);
   }
}
