package dev.baranhan.viltrumitecore.worldevent.ultron;

/** The five machine kinds a foundry can build (the boss is not one of them). Caps and build times in {@link UltronRules}. */
public enum UltronUnitKind {
   DRONE("drone"),
   SENTRY("sentry"),
   BRUTE("brute"),
   INTERCEPTOR("interceptor"),
   ENGINEER("engineer");

   private final String key;

   UltronUnitKind(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   public boolean flies() {
      return this == DRONE || this == INTERCEPTOR;
   }

   public static UltronUnitKind byId(int id) {
      UltronUnitKind[] all = values();
      return id >= 0 && id < all.length ? all[id] : SENTRY;
   }
}
