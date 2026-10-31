package dev.baranhan.viltrumitecore.city.citizen;

/**
 * Personality of one citizen, derived from its appearance seed (pure): the same person
 * always reacts the same way, but a crowd does not move as one. Nothing here is random
 * per tick; the brain adds the dice.
 */
public record CitizenTraits(float bravery, float curiosity, float pace, int reaction, float sociability) {
   public static int mix(int seed, int salt) {
      int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
      h ^= h >>> 16;
      h *= 0x7FEB352D;
      h ^= h >>> 15;
      h *= 0x846CA68B;
      h ^= h >>> 16;
      return h;
   }

   /** Uniform 0..1 from the seed and a salt. */
   public static float unit(int seed, int salt) {
      return (mix(seed, salt) >>> 8) / (float)(1 << 24);
   }

   public static CitizenTraits of(int seed) {
      float bravery = unit(seed, 101);
      float curiosity = unit(seed, 102);
      // walking pace 0.86..1.16 of the base speed, a bit of everyone
      float pace = 0.86F + unit(seed, 103) * 0.30F;
      // ticks between noticing a danger and running: brave people look longer
      int reaction = 3 + Math.round(bravery * 7.0F + unit(seed, 104) * 4.0F);
      return new CitizenTraits(bravery, curiosity, pace, reaction, unit(seed, 105));
   }

   /** Chance per check that a calm person starts running when a neighbour runs past. */
   public float contagion() {
      return 0.45F + (1.0F - this.bravery) * 0.5F;
   }

   /** Extra blocks beyond a danger radius that still feel too close. */
   public float margin() {
      return 3.0F + (1.0F - this.bravery) * 7.0F;
   }

   /** Ticks without any danger in sight before a fleeing/hiding person starts recovering. */
   public int calmDelay() {
      return 60 + Math.round((1.0F - this.bravery) * 100.0F);
   }
}
