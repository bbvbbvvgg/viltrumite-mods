package dev.baranhan.viltrumitecore.worldevent.thanos.war.order;

/**
 * Ebony Maw's telekinesis (pure data; the ordinal is the network id). He keeps his distance and makes
 * the city his weapon: torn-out blocks thrown in volleys, heroes lifted and hurled, a debris barrier
 * around Thanos, a piece of a building dropped on a hero. Catching projectiles is passive (no move).
 */
public enum MawMove implements OrderMove {
   //      tel act rec  cd   min  max  weight air    control cover  dmg
   /** One torn-out block thrown fast at the target. */
   SPEAR(14, 8, 12, 70, 3.0, 30.0, 1.0F, true, false, false, 9.0F),
   /** Several blocks torn from the buildings around him, circling, then thrown one after another (at several heroes). */
   VOLLEY(24, 30, 16, 140, 4.0, 34.0, 1.2F, true, false, false, 8.0F),
   /** The target lifted and held in the air, then hurled away (its protections and the ledger apply). */
   GRAB(16, 50, 14, 300, 0.0, 22.0, 0.9F, true, true, false, 10.0F),
   /** A ring of debris around Thanos (or himself) for five seconds: shots at him are caught or cut down. */
   BARRIER(12, 100, 10, 380, 0.0, 999.0, 0.0F, true, false, true, 0.0F),
   /** A piece of a building above the target torn loose and dropped on it. */
   TOPPLE(30, 20, 16, 420, 0.0, 28.0, 0.8F, false, false, false, 16.0F),
   /** He drifts away from a hero who got too close. */
   BLINK(10, 4, 6, 160, 0.0, 7.0, 0.0F, true, false, true, 0.0F);

   private final int telegraph;
   private final int active;
   private final int recovery;
   private final int cooldown;
   private final double minRange;
   private final double maxRange;
   private final float weight;
   private final boolean air;
   private final boolean control;
   private final boolean cover;
   private final float damage;

   MawMove(int telegraph, int active, int recovery, int cooldown, double minRange, double maxRange, float weight, boolean air, boolean control,
      boolean cover, float damage) {
      this.telegraph = telegraph;
      this.active = active;
      this.recovery = recovery;
      this.cooldown = cooldown;
      this.minRange = minRange;
      this.maxRange = maxRange;
      this.weight = weight;
      this.air = air;
      this.control = control;
      this.cover = cover;
      this.damage = damage;
   }

   @Override
   public double situational(OrderBrain.Situation s) {
      return switch (this) {
         case SPEAR -> s.distance() > 12.0 ? 1.3 : 1.0;
         case VOLLEY -> (s.structuresNear() ? 1.4 : 0.8) * (s.heroesClose() >= 2 ? 1.3 : 1.0);
         case GRAB -> s.airborne() ? 1.8 : s.ranged() ? 1.5 : 1.0;
         // the barrier is his answer to fire on Thanos or on himself
         case BARRIER -> s.thanosThreatened() || s.projectiles() >= 2 ? 6.0 : s.projectiles() == 1 ? 1.5 : 0.0;
         case TOPPLE -> s.structuresNear() && !s.airborne() ? 1.4 : 0.0;
         case BLINK -> s.distance() < 5.0 || s.heroesClose() >= 2 ? 5.0 : 0.0;
      };
   }

   @Override
   public int telegraph() {
      return this.telegraph;
   }

   @Override
   public int active() {
      return this.active;
   }

   @Override
   public int recovery() {
      return this.recovery;
   }

   @Override
   public int cooldown() {
      return this.cooldown;
   }

   @Override
   public double minRange() {
      return this.minRange;
   }

   @Override
   public double maxRange() {
      return this.maxRange;
   }

   @Override
   public float weight() {
      return this.weight == 0.0F ? 1.0F : this.weight;
   }

   @Override
   public boolean air() {
      return this.air;
   }

   @Override
   public boolean control() {
      return this.control;
   }

   @Override
   public boolean throughCover() {
      return this.cover;
   }

   @Override
   public float damage() {
      return this.damage;
   }

   public static MawMove byId(int id) {
      MawMove[] all = values();
      return id >= 0 && id < all.length ? all[id] : null;
   }
}
