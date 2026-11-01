package dev.baranhan.viltrumitecore.worldevent.ultron;

/** Life of a node: planned site → under construction → online ⇄ disabled (hacked) → destroyed. */
public enum UltronNodeState {
   PLANNED,
   BUILDING,
   ONLINE,
   DISABLED,
   DESTROYED;

   /** Stands in the world (has a body). */
   public boolean standing() {
      return this == BUILDING || this == ONLINE || this == DISABLED;
   }

   public static UltronNodeState byId(int id) {
      UltronNodeState[] all = values();
      return id >= 0 && id < all.length ? all[id] : DESTROYED;
   }
}
