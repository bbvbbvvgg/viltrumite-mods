package dev.baranhan.viltrumitecore.hero.combat;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Hierarchical dotted combat tags ({@code weapon.hammer}, {@code element.lightning}).
 * A query matches its own tag and every tag below it ({@code weapon} matches
 * {@code weapon.hammer}). Interaction rules ask tags and traits, never a
 * concrete hero or weapon: a new weapon joins every existing rule by its tags.
 */
public final class CombatTags {
   private static final Pattern FORMAT = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");

   /** Any weapon (root of the weapon kinds). */
   public static final String WEAPON = "weapon";
   public static final String WEAPON_HAMMER = "weapon.hammer";
   public static final String WEAPON_AXE = "weapon.axe";
   public static final String WEAPON_SWORD = "weapon.sword";
   /** A carried shield (raised = GUARD stance). */
   public static final String WEAPON_SHIELD = "weapon.shield";
   /** Elemental charge of an attack or a weapon. */
   public static final String ELEMENT = "element";
   public static final String ELEMENT_LIGHTNING = "element.lightning";
   public static final String ELEMENT_FROST = "element.frost";
   /** Weapon / body traits. */
   public static final String TRAIT = "trait";
   public static final String TRAIT_DIVINE = "trait.divine";
   public static final String TRAIT_THROWABLE = "trait.throwable";
   public static final String TRAIT_RETURNING = "trait.returning";
   public static final String TRAIT_UNBREAKABLE = "trait.unbreakable";
   /** A blade whose cut severs anything mortal (contextual interactions: breaks a raised guard). */
   public static final String TRAIT_ANNIHILATING = "trait.annihilating";
   /** Worn armour. */
   public static final String ARMOR = "armor";
   /** Powered armour with a reactor (soaks lightning into energy). */
   public static final String ARMOR_POWERED = "armor.powered";
   /** Unarmed body. */
   public static final String BODY = "body";
   public static final String BODY_BARE = "body.bare";

   private CombatTags() {
   }

   /** True for a well-formed tag or query. */
   public static boolean valid(String tag) {
      return tag != null && FORMAT.matcher(tag).matches();
   }

   /** The tag itself, throwing for a malformed one (registration time). */
   public static String require(String tag) {
      if (!valid(tag)) {
         throw new IllegalArgumentException("combat tag must be dotted lowercase: " + tag);
      }

      return tag;
   }

   /** Does {@code tag} equal {@code query} or sit below it? */
   public static boolean matches(String tag, String query) {
      return tag != null && query != null
         && (tag.equals(query) || tag.length() > query.length() && tag.startsWith(query) && tag.charAt(query.length()) == '.');
   }

   /** Does any tag match the query? */
   public static boolean has(Collection<String> tags, String query) {
      for (String tag : tags) {
         if (matches(tag, query)) {
            return true;
         }
      }

      return false;
   }

   /** Number of segments: deeper queries are more specific. */
   public static int depth(String query) {
      if (query == null || query.isEmpty()) {
         return 0;
      }

      int depth = 1;
      for (int i = 0; i < query.length(); i++) {
         if (query.charAt(i) == '.') {
            depth++;
         }
      }

      return depth;
   }
}
