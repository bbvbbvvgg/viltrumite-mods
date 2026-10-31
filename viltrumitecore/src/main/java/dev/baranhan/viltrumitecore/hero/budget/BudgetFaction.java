package dev.baranhan.viltrumitecore.hero.budget;

import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * FIVE FACTION WAR SYSTEM. Every playable hero belongs to one faction; NPCs of
 * the multiverse events pick their targets from this table. Same faction =
 * allies, the three "hero" factions tolerate each other, demons and chaos are
 * hostile to everyone else. Historical heroes are matched by their save key,
 * never by a HeroId constant, so shared code stays free of hero branches.
 */
public enum BudgetFaction {
   ANIME(0xFF8020),
   COMICS(0x2050D0),
   DEMONS(0xC01010),
   GODS(0xF0D040),
   CHAOS(0x9040FF);

   public enum Relation {
      ALLY,
      NEUTRAL,
      HOSTILE
   }

   /** Factions of the historical (non-expansion) heroes, by save key. */
   private static final Map<String, BudgetFaction> LEGACY = Map.of(
      "viltrumite", COMICS,
      "homelander", COMICS,
      "ironman", COMICS,
      "thor", GODS,
      "reinhard", GODS,
      "regulus", CHAOS
   );

   public final int color;

   BudgetFaction(int color) {
      this.color = color;
   }

   public Relation relationTo(@Nullable BudgetFaction other) {
      if (other == null) {
         return Relation.HOSTILE;
      }

      if (other == this) {
         return Relation.ALLY;
      }

      return this.heroic() && other.heroic() ? Relation.NEUTRAL : Relation.HOSTILE;
   }

   public boolean hostileTo(@Nullable BudgetFaction other) {
      return this.relationTo(other) == Relation.HOSTILE;
   }

   /** The three factions of the "heroes" alliance. */
   public boolean heroic() {
      return this == ANIME || this == COMICS || this == GODS;
   }

   public String langKey() {
      return "faction.viltrumitecore." + this.name().toLowerCase(Locale.ROOT);
   }

   /** Faction of a hero by its save key; null for plain humans and unknown keys. */
   @Nullable
   public static BudgetFaction ofHeroKey(@Nullable String key) {
      if (key == null) {
         return null;
      }

      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         if (spec.key().equals(key)) {
            return spec.faction();
         }
      }

      return LEGACY.get(key);
   }

   @Nullable
   public static BudgetFaction byName(String name) {
      for (BudgetFaction faction : values()) {
         if (faction.name().equalsIgnoreCase(name)) {
            return faction;
         }
      }

      return null;
   }
}
