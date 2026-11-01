package dev.baranhan.viltrumitecore.worldevent.ultron;

import javax.annotation.Nullable;

/** The five phases of the Ultron uprising, then ENDED. Order is the timeline order (the state packet sends the ordinal). */
public enum UltronPhase {
   AWAKENING("awakening"),
   SPREAD("spread"),
   MACHINE_WAR("machine_war"),
   ADAPTATION("adaptation"),
   FINALE("finale"),
   ENDED("ended");

   private final String key;

   UltronPhase(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   public UltronPhase next() {
      return this == ENDED ? ENDED : values()[this.ordinal() + 1];
   }

   /** The machines fight players on sight from the spread on (the awakening only scouts and defends itself). */
   public boolean hostile() {
      return this != AWAKENING && this != ENDED;
   }

   public boolean atLeast(UltronPhase other) {
      return this.ordinal() >= other.ordinal();
   }

   @Nullable
   public static UltronPhase byKey(String key) {
      for (UltronPhase p : values()) {
         if (p.key.equals(key)) {
            return p;
         }
      }
      return null;
   }

   public static UltronPhase byId(int id) {
      UltronPhase[] all = values();
      return id >= 0 && id < all.length ? all[id] : ENDED;
   }
}
