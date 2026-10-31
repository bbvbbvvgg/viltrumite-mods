package dev.baranhan.viltrumitecore.interaction;

/**
 * Result of an applied interaction. {@code fired} = the interaction really happened (cooldown, aftermath and the
 * log follow); {@code cancel} = the triggering hit / impact is consumed; {@code scale} multiplies a HURT hit.
 */
public record InteractionOutcome(boolean fired, boolean cancel, float scale) {
   /** Not applicable after all (a defender's own check refused): the next interaction or the normal mechanic runs. */
   public static final InteractionOutcome PASS = new InteractionOutcome(false, false, 1.0F);
   /** Happened and consumed the hit / impact. */
   public static final InteractionOutcome CONSUMED = new InteractionOutcome(true, true, 1.0F);

   public static InteractionOutcome scaled(float scale) {
      return new InteractionOutcome(true, false, scale);
   }
}
