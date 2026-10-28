package dev.baranhan.viltrumitecore.client.thor;

/**
 * Pure time curve of the WRATH storm (FX id {@code thor:wrath_storm}, spec
 * §3/§7): a slow build-up from the start of the move to the contact tick
 * (cloud gathering, sky arcs into the raised hammer getting thicker and more
 * frequent, rumble), the strike itself (preset {@code thor:wrath_strike},
 * sent by the server), then a long tail to the end of the move: the cloud
 * spreads and thins, aftershock arcs get rarer, the ground keeps crawling.
 *
 * <p>Contact and length come from the move timings (ThorCombatTimings, R2a)
 * so the curve follows any new table; the visual tail lasts at least
 * {@link #MIN_TAIL} ticks even when the move ends earlier.
 */
public record WrathStormCurve(int contact, int length) {
   /** Minimal visual tail after contact, ticks (3 s). */
   public static final int MIN_TAIL = 60;
   /** Fade-out of the cloud after the tail, ticks. */
   public static final int FADE = 20;
   /** Ticks after contact owned by the strike preset (no extra arcs on top). */
   public static final int STRIKE_TICKS = 8;

   public enum Phase {
      GATHER,
      CHARGE,
      STRIKE,
      TAIL,
      DONE
   }

   public WrathStormCurve {
      contact = Math.max(1, contact);
      length = Math.max(contact + 1, length);
   }

   /** Tick the visual tail ends (move end, at least MIN_TAIL after contact). */
   public int tailEnd() {
      return Math.max(this.length, this.contact + MIN_TAIL);
   }

   /** Whole visual life including the cloud fade. */
   public int end() {
      return this.tailEnd() + FADE;
   }

   public Phase phase(float elapsed) {
      if (elapsed < 0.0F || elapsed >= this.end()) {
         return Phase.DONE;
      } else if (elapsed < this.contact) {
         return this.build(elapsed) < 0.45F ? Phase.GATHER : Phase.CHARGE;
      } else if (elapsed < this.contact + STRIKE_TICKS) {
         return Phase.STRIKE;
      }

      return Phase.TAIL;
   }

   /** 0..1 build-up, accelerating towards contact (slow, heavy start). */
   public float build(float elapsed) {
      if (elapsed >= this.contact) {
         return 1.0F;
      }

      float t = clamp(elapsed / this.contact);
      return t * t * (3.0F - 2.0F * t) * (0.35F + 0.65F * t);
   }

   /** 0..1 progress through the tail (0 at contact, 1 at tailEnd). */
   public float tail(float elapsed) {
      return clamp((elapsed - this.contact) / (float)(this.tailEnd() - this.contact));
   }

   /** Cloud density 0..1: gathers, stays full through the strike, thins out over the tail and the fade. */
   public float density(float elapsed) {
      if (elapsed < this.contact) {
         return 0.12F + 0.88F * this.build(elapsed);
      } else if (elapsed < this.tailEnd()) {
         return 1.0F - 0.55F * this.tail(elapsed);
      }

      return 0.45F * (1.0F - clamp((elapsed - this.tailEnd()) / FADE));
   }

   /** Cloud radius (blocks, scale 1): 6 → 15 by contact, spreading to 20 over the tail. */
   public float radius(float elapsed) {
      if (elapsed < this.contact) {
         return 6.0F + 9.0F * this.build(elapsed);
      }

      return 15.0F + 5.0F * this.tail(elapsed);
   }

   /** Ticks between sky arcs: 10 → 2 in the build-up, none during the strike, 5 → 16 over the tail; 0 = none. */
   public int arcInterval(float elapsed) {
      Phase phase = this.phase(elapsed);
      return switch (phase) {
         case GATHER, CHARGE -> Math.max(2, Math.round(10.0F - 8.0F * this.build(elapsed)));
         case TAIL -> elapsed < this.tailEnd() ? Math.round(5.0F + 11.0F * this.tail(elapsed)) : 0;
         default -> 0;
      };
   }

   /** Core half-width of a build-up arc into the hammer (blocks): thin threads become ropes. */
   public float arcWidth(float elapsed) {
      return 0.05F + 0.11F * this.build(elapsed);
   }

   /** Continuous rumble (camera shake per tick, before falloff): grows to the strike, dies over the tail. */
   public float rumble(float elapsed) {
      Phase phase = this.phase(elapsed);
      return switch (phase) {
         case GATHER, CHARGE -> 0.03F + 0.32F * this.build(elapsed) * this.build(elapsed);
         case TAIL -> 0.22F * (1.0F - this.tail(elapsed));
         default -> 0.0F;
      };
   }

   /** Ground crawlers around Thor in the last part of the build-up and the first half of the tail. */
   public boolean crawls(float elapsed) {
      Phase phase = this.phase(elapsed);
      return phase == Phase.CHARGE || phase == Phase.TAIL && this.tail(elapsed) < 0.5F;
   }

   /** Heavy bolts straight into the raised hammer, only just before contact. */
   public boolean hammerBolts(float elapsed) {
      return elapsed < this.contact && this.build(elapsed) > 0.72F;
   }

   /** Flash of the cloud underside 0..1 for this tick (heavier near contact). */
   public float cloudFlash(float elapsed) {
      return this.phase(elapsed) == Phase.STRIKE ? 1.0F : 0.6F * this.build(elapsed) * (1.0F - this.tail(elapsed));
   }

   private static float clamp(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }
}
