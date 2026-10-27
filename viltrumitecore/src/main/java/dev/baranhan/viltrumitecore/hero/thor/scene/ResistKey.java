package dev.baranhan.viltrumitecore.hero.thor.scene;

import javax.annotation.Nullable;

/** The one resist key of a grab, picked at random and kept for the whole hold (decisions 123, 150, 151). */
public enum ResistKey {
   W,
   A,
   S,
   D,
   SPACE;

   private static final ResistKey[] VALUES = values();

   /** {@code roll} is any int (e.g. random.nextInt()). */
   public static ResistKey pick(int roll) {
      return VALUES[Math.floorMod(roll, VALUES.length)];
   }

   @Nullable
   public static ResistKey byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }
}
