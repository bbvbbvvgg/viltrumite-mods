package dev.baranhan.viltrumitecore.hero.reinhard;

import javax.annotation.Nullable;

/** Reinhard's three modes (spec §5). Append only; saved by {@link #key()}. Ability page = ordinal. */
public enum ReinhardMode {
   RESTRAINED("restrained"),
   KNIGHT("knight"),
   HOLY_SWORD("holy_sword");

   private static final ReinhardMode[] VALUES = values();
   private final String key;

   ReinhardMode(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   /** Ability panel page of this mode. */
   public int page() {
      return this.ordinal();
   }

   /** MMB cycle: Restrained → Knight → Holy Sword → Restrained. */
   public ReinhardMode next() {
      return VALUES[(this.ordinal() + 1) % VALUES.length];
   }

   public static ReinhardMode byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RESTRAINED;
   }

   public static ReinhardMode fromKey(@Nullable String key) {
      for (ReinhardMode mode : VALUES) {
         if (mode.key.equals(key)) {
            return mode;
         }
      }

      return RESTRAINED;
   }
}
