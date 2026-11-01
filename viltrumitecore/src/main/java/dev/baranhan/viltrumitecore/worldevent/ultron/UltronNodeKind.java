package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * What a network node does. CORE is the root of the power graph (and an uplink); UPLINK controls
 * machines around it (captures golems and Legion suits); FOUNDRY builds units; BULWARK shields its
 * linked neighbours and the units near it; SENSOR sees players (intel, adaptation); REPAIR mends
 * nodes and units. Numbers live in {@link UltronRules}.
 */
public enum UltronNodeKind {
   CORE("core"),
   UPLINK("uplink"),
   FOUNDRY("foundry"),
   BULWARK("bulwark"),
   SENSOR("sensor"),
   REPAIR("repair");

   private final String key;

   UltronNodeKind(String key) {
      this.key = key;
   }

   public String key() {
      return this.key;
   }

   /** Captures machines in range (the core is an uplink too). */
   public boolean controls() {
      return this == CORE || this == UPLINK;
   }

   public static UltronNodeKind byId(int id) {
      UltronNodeKind[] all = values();
      return id >= 0 && id < all.length ? all[id] : UPLINK;
   }
}
