package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** DIMENSIONAL CHAOS MODIFIERS of the Multiverse Collapse event. */
public enum ChaosModifier {
   /** Twice as many regular enemies in the invasion and war phases. */
   DOUBLE_TROUBLE,
   /** Shorter pauses and timeouts, one extra champion, faster boss specials. */
   BOSS_MAYHEM,
   /** NPC ability cooldowns cut to a third. */
   ULTIMATE_CHAOS,
   /** Champions are drawn at random from the boss list instead of the fixed pair. */
   RANDOM_CHAMPIONS;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public String langKey() {
      return "multiverse.viltrumitecore.modifier." + this.id();
   }

   public static ChaosModifier byId(String id) {
      for (ChaosModifier modifier : values()) {
         if (modifier.id().equalsIgnoreCase(id) || modifier.name().equalsIgnoreCase(id)) {
            return modifier;
         }
      }

      return null;
   }

   /** Parses "double_trouble boss_mayhem"; unknown words are reported through {@code unknown}. */
   public static Set<ChaosModifier> parse(String text, StringBuilder unknown) {
      Set<ChaosModifier> set = EnumSet.noneOf(ChaosModifier.class);
      if (text == null) {
         return set;
      }

      for (String word : text.trim().split("[\\s,]+")) {
         if (word.isEmpty()) {
            continue;
         }

         if (word.equalsIgnoreCase("all")) {
            set.addAll(EnumSet.allOf(ChaosModifier.class));
            continue;
         }

         ChaosModifier modifier = byId(word);
         if (modifier == null) {
            if (unknown.length() > 0) {
               unknown.append(", ");
            }
            unknown.append(word);
         } else {
            set.add(modifier);
         }
      }

      return set;
   }
}
