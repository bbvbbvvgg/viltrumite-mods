package dev.baranhan.viltrumitecore.hero;

/**
 * Bounded hero input/display actions. The wire packet carries the ordinal, so
 * a forged packet can only ever name a value in this list.
 */
public enum HeroAction {
   LIONS_HEART,
   DEBRIS_KICK,
   MANIA,
   GREEDS_EMBRACE,
   COUNTER,
   JUMP,
   RITUAL,
   ASSIGN_HEART,
   LASERS,
   FOCUS,
   ROAR,
   /** Iron Man "Костюм" key: put on / take off the suit. */
   SUIT,
   /** Claimed LMB (Iron Man in flight: fly-by punch; air strike in Task 9). */
   PRIMARY_ATTACK,
   /** Claimed RMB: the current right tool (Iron Man repulsor / nano weapon). */
   SECONDARY_USE,
   /** Claimed MMB: cycle the right tool. */
   TOOL_CYCLE,
   UNIBEAM,
   MISSILES,
   NANO_ARSENAL,
   /** Guard key (F) held: shield. */
   GUARD,
   /** RMB on a HeroInteractable entity instead of the tool. */
   INTERACT,
   /** Iron Man helmet toggle (stage 3). */
   HELMET,
   /** Iron Man scan: press starts, press again cancels. */
   SCAN,
   /** Iron Man flares. */
   COUNTERMEASURES,
   /** Iron Man Stage 4: call Veronica; again while the pod stands = suit menu. */
   VERONICA,
   /** Iron Man Stage 4: the worn mark's signature (page 1 slot 3 in a mark; timeline id). */
   SIGNATURE,
   /** Reinhard: claimed MMB, cycle the mode. */
   MODE_CYCLE;

   public static HeroAction byId(int ordinal) {
      HeroAction[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
   }
}
