package dev.baranhan.viltrumitecore.city.citizen;

/**
 * What a citizen is doing (synced as one byte; the client poses the body from it).
 * Calm: {@link #IDLE}, {@link #WALKING}, {@link #TALKING}. Danger, in escalation order:
 * {@link #ALERT} (noticed something: stops and looks), {@link #STARTLED} (the flinch before
 * running), {@link #FLEEING}, {@link #COVER} (hiding / cowering, arms over the head),
 * {@link #FALLEN} and {@link #GETTING_UP} (knocked down by a shock wave). Afterwards
 * {@link #RECOVERING}: walks again, slowly and watchfully, until calm.
 */
public enum CitizenState {
   IDLE,
   WALKING,
   TALKING,
   ALERT,
   STARTLED,
   FLEEING,
   COVER,
   FALLEN,
   GETTING_UP,
   RECOVERING;

   private static final CitizenState[] VALUES = values();

   public static CitizenState of(int b) {
      return b >= 0 && b < VALUES.length ? VALUES[b] : IDLE;
   }

   /** Calm states: strolling, chatting, standing around (recovering counts as calm but wary). */
   public boolean calm() {
      return this == IDLE || this == WALKING || this == TALKING || this == RECOVERING;
   }

   /** The citizen is in the grip of a danger (running, hiding, down). */
   public boolean panicked() {
      return this == STARTLED || this == FLEEING || this == COVER || this == FALLEN || this == GETTING_UP;
   }

   /** A neighbour in this state makes others nervous (people notice runners and screams). */
   public boolean contagious() {
      return this == STARTLED || this == FLEEING || this == FALLEN;
   }
}
