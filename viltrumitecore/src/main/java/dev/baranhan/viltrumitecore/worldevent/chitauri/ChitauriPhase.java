package dev.baranhan.viltrumitecore.worldevent.chitauri;

/**
 * Invasion phases in order. {@link #id()} is the network id, {@link #key()} the lang key part.
 * Pure: shared by the server director, the packets and the client portal renderer.
 */
public enum ChitauriPhase {
   OPENING("opening"),
   GROUND_ASSAULT("ground"),
   AIR_ASSAULT("air"),
   LEVIATHAN("leviathan"),
   CLIMAX("climax"),
   CLOSING("closing"),
   ENDED("ended");

   private static final ChitauriPhase[] VALUES = values();
   private final String key;

   ChitauriPhase(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   public int id() {
      return this.ordinal();
   }

   public static ChitauriPhase byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : ENDED;
   }

   public static ChitauriPhase byKey(String key) {
      for (ChitauriPhase phase : VALUES) {
         if (phase.key.equals(key)) {
            return phase;
         }
      }
      return null;
   }

   /** The phase after this one (ENDED stays ENDED). */
   public ChitauriPhase next() {
      return this == ENDED ? ENDED : VALUES[this.ordinal() + 1];
   }

   /** Fighting phases: waves spawn, the portal is fully open. */
   public boolean fighting() {
      return this == GROUND_ASSAULT || this == AIR_ASSAULT || this == LEVIATHAN || this == CLIMAX;
   }
}
