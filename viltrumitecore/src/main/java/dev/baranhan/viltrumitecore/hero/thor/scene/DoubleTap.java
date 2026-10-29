package dev.baranhan.viltrumitecore.hero.thor.scene;

/**
 * Double-tap detector for the four movement keys (W A S D): a second press
 * of the same key within the window fires once; a different key or a late
 * press starts over. Pure (the client feeds key edges and its tick count).
 */
public final class DoubleTap {
   private final long[] last = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
   private final boolean[] down = new boolean[4];

   /** Feed the four key states of one tick; returns the direction that double-tapped, or -1. */
   public int tick(boolean[] keys, long now, int window) {
      int fired = -1;
      for (int i = 0; i < 4; i++) {
         boolean edge = keys[i] && !this.down[i];
         this.down[i] = keys[i];
         if (!edge) {
            continue;
         }

         if (this.last[i] != Long.MIN_VALUE && now - this.last[i] <= window && fired < 0) {
            fired = i;
            this.last[i] = Long.MIN_VALUE;
         } else {
            this.last[i] = now;
            for (int j = 0; j < 4; j++) {
               if (j != i) {
                  this.last[j] = Long.MIN_VALUE;
               }
            }
         }
      }
      return fired;
   }

   public void reset() {
      for (int i = 0; i < 4; i++) {
         this.last[i] = Long.MIN_VALUE;
      }
   }
}
