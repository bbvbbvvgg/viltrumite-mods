package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * Dynamic roles the coordinator ({@link UltronRoles}) hands out: recon, patrol, factory / node guard,
 * assault, repair (and construction), air interception, escort of engineers, machine capture.
 */
public enum UltronRole {
   SCOUT("scout"),
   PATROL("patrol"),
   GUARD("guard"),
   ASSAULT("assault"),
   REPAIR("repair"),
   INTERCEPT("intercept"),
   ESCORT("escort"),
   CAPTURE("capture");

   private final String key;

   UltronRole(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   public static UltronRole byId(int id) {
      UltronRole[] all = values();
      return id >= 0 && id < all.length ? all[id] : PATROL;
   }
}
