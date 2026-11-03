package dev.baranhan.viltrumitecore.scp.scp106;

/** Behaviour states of SCP-106 (design §2.2); the id is synced to clients and drives the animations. */
public enum Scp106State {
   /** Rising out of a corroded patch of floor. */
   EMERGE,
   /** Slowly following its prey. */
   STALK,
   /** Standing still, staring, breathing. */
   STARE,
   /** Passing through a wall, a door or a floor (no collision, leaves corrosion on both faces). */
   PHASE,
   /** Sinking into the floor (to move elsewhere or to leave). */
   SINK,
   /** Under the surface, invisible, moving to where it will emerge. */
   SUBMERGED,
   /** It has its prey: the capture animation, the player is pulled into the floor. */
   CAPTURE,
   /** Hurt enough: it sinks away and does not come back for a while. */
   RETREAT;

   private static final Scp106State[] VALUES = values();

   public static Scp106State byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : STALK;
   }
}
