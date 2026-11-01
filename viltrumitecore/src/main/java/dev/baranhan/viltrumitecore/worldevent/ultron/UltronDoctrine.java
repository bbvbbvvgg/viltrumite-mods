package dev.baranhan.viltrumitecore.worldevent.ultron;

/** Ultron's strategy of the moment ({@link UltronStrategy} picks and switches it). */
public enum UltronDoctrine {
   /** Build the network out, avoid open fights. */
   EXPANSION("expansion"),
   /** Assault columns on a district / player cluster, heavies in front. */
   SIEGE("siege"),
   /** Hunter squads chase the most dangerous player Ultron has seen. */
   HUNT("hunt"),
   /** Capture golems and Iron Legion suits with the uplinks, engineers hack. */
   ASSIMILATION("assimilation");

   private final String key;

   UltronDoctrine(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   public static UltronDoctrine byId(int id) {
      UltronDoctrine[] all = values();
      return id >= 0 && id < all.length ? all[id] : EXPANSION;
   }
}
