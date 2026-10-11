package dev.baranhan.viltrumitecore.hero.ironman.mark;

/**
 * Damage on a worn mark (spec §4.3): while durability lasts the whole hit is
 * absorbed, Tony loses nothing; the hit that empties it does not spill over.
 * Plating ({@link MarkSpec#plating}) only slows the loss.
 */
public final class MarkDamage {
   /** Lowest plating a suit may declare. */
   public static final float MIN_PLATING = 0.2F;

   private MarkDamage() {
   }

   public record Result(float durability, boolean absorbed, boolean broke) {
   }

   /** @param plating loss factor of the suit, clamped to [MIN_PLATING, 1] */
   public static Result apply(float durability, float amount, float plating) {
      if (durability <= 0.0F) {
         return new Result(0.0F, false, false);
      }

      float loss = Float.isFinite(plating) ? Math.max(MIN_PLATING, Math.min(1.0F, plating)) : 1.0F;
      float left = durability - Math.max(0.0F, amount) * loss;
      return left <= 0.0F ? new Result(0.0F, true, true) : new Result(left, true, false);
   }
}
