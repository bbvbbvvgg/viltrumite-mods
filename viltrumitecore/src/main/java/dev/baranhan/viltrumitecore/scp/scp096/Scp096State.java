package dev.baranhan.viltrumitecore.scp.scp096;

/**
 * Behaviour state of SCP-096, synced to clients as one byte (docs/design/2026-11-05-scp096-design.md §1).
 * Calm states never chase; enraged states keep their targets until every one of them is gone.
 */
public enum Scp096State {
   /** Sitting with the face in its hands, sobbing; the head comes up now and then (face open for a moment). */
   SIT,
   /** Standing, swaying, covering the face when someone is close. */
   STAND,
   /** Slow, unsure walk over the pedestrian grid; the arms hang, the face is open. */
   WANDER,
   /** The face was seen: the head snaps to the observer, the body locks. */
   TRIGGERED,
   /** Hysteria before the chase: thrashing, clawing at the face, screaming. */
   HYSTERIA,
   /** The chase: inhuman speed, smashing, climbing, leaping until every target is gone. */
   CHASE,
   /** Calming down after the last target: slows, sits back down, cries again. */
   CALMING;

   private static final Scp096State[] VALUES = values();

   public static Scp096State byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : SIT;
   }

   public static Scp096State byName(String name) {
      for (Scp096State s : VALUES) {
         if (s.name().equalsIgnoreCase(name)) {
            return s;
         }
      }
      return SIT;
   }

   public int id() {
      return this.ordinal();
   }

   public boolean calm() {
      return this == SIT || this == STAND || this == WANDER;
   }

   public boolean enraged() {
      return this == TRIGGERED || this == HYSTERIA || this == CHASE;
   }
}
