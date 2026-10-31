package dev.baranhan.viltrumitecore.hero.ironman.legion;

import java.util.Locale;
import javax.annotation.Nullable;

/** Player-chosen target priority (design §4). Wire ordinal: append only. */
public enum LegionPriority {
   /** Whatever threatens Tony most right now, nearest first. */
   NEAREST,
   /** The strongest enemy (danger and health) first. */
   STRONGEST,
   /** Tony's own target first (his crosshair or the last thing he hit). */
   TONY_TARGET;

   private static final LegionPriority[] VALUES = values();

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static LegionPriority byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public LegionPriority next() {
      return VALUES[(this.ordinal() + 1) % VALUES.length];
   }
}
