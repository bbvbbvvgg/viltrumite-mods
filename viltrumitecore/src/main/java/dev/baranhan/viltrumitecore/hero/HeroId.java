package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;

public enum HeroId {
   HUMAN("human"),
   VILTRUMITE("viltrumite"),
   REGULUS("regulus"),
   /** Replaces the Viltrumite race. Saved "viltrumite" ids load as this hero. */
   HOMELANDER("homelander"),
   /** Tony Stark: nano Mark 50, marks, Hulkbuster (spec docs/design/2026-10-09-ironman-design.md). */
   IRON_MAN("ironman"),
   /** Reinhard van Astrea (spec docs/design/2026-10-11-reinhard-design.md). */
   REINHARD("reinhard"),
   /** Thor, God of War Ragnarök (spec docs/design/2026-10-27-thor-design.md). */
   THOR("thor"),
   /** DIO and The World (spec docs/design/2026-10-28-dio-design.md). */
   DIO("dio"),
   /** The Magnificent Sinclair (spec docs/design/2026-10-28-sinclair-design.md). */
   SINCLAIR("sinclair"),
   /** Sung Jinwoo, the Shadow Monarch (spec docs/design/2026-10-28-jinwoo-design.md). */
   JINWOO("jinwoo"),
   CAPTAIN("captain");

   private final String key;

   HeroId(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   @Nullable
   public static HeroId fromKey(@Nullable String key) {
      if (key == null) {
         return null;
      }

      if (VILTRUMITE.key.equals(key)) {
         return HOMELANDER;
      }

      for (HeroId id : values()) {
         if (id.key.equals(key)) {
            return id;
         }
      }

      return null;
   }

   public static HeroId fromLegacyBoolean(boolean isViltrumite) {
      return isViltrumite ? HOMELANDER : HUMAN;
   }

   /**
    * Legacy ViltrumiteCorePlayer.setViltrumite semantics: the boolean only ever
    * toggles HUMAN<->VILTRUMITE and must never convert or clear a different hero.
    */
   public static HeroId legacySet(HeroId current, boolean isViltrumite) {
      if (current == HUMAN || current == VILTRUMITE || current == HOMELANDER) {
         return fromLegacyBoolean(isViltrumite);
      }

      return current;
   }
}
