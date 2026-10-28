package dev.baranhan.viltrumitecore.hero.act;

import javax.annotation.Nullable;

/**
 * Stage shapes a hero may name for its signature act (spec docs/design/2026-10-28-sinclair-design.md §6).
 * Only the performer (Sinclair) owns the staging of each shape. Append only: the key is the wire/save form.
 */
public enum ActShape {
   /** A beam along the look. */
   BEAM("beam"),
   /** A burst around a thrown point. */
   NOVA("nova"),
   /** A cutting line on the ground. */
   CUT("cut"),
   /** Several homing volleys. */
   BARRAGE("barrage"),
   /** A defensive shell, then a push. */
   AEGIS("aegis"),
   /** The fallback number: the performer's own trick. */
   FINALE("finale");

   private final String key;

   ActShape(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   @Nullable
   public static ActShape byOrdinal(int ordinal) {
      ActShape[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
   }

   @Nullable
   public static ActShape fromKey(@Nullable String key) {
      if (key != null) {
         for (ActShape shape : values()) {
            if (shape.key.equals(key)) {
               return shape;
            }
         }
      }

      return null;
   }
}
