package dev.baranhan.viltrumitecore.worldevent.thanos.war.order;

/**
 * Cull Obsidian's brute force (pure data; the ordinal is the network id): the hammer, the ground
 * slam, the charge through walls, the chain hook against flyers and snipers, the grab and throw,
 * and his answer to a Hulkbuster: a roar and the clash.
 */
public enum CullMove implements OrderMove {
   //      tel act rec  cd   min  max   weight air    control cover  dmg
   /** A wide hammer swing. */
   SWING(12, 6, 14, 30, 0.0, 5.0, 1.6F, false, false, false, 16.0F),
   /** Both hands on the hammer, down into the ground: a shock ring. */
   SLAM(22, 6, 20, 160, 0.0, 7.5, 1.0F, false, false, false, 20.0F),
   /** Head down, straight through the walls at the target. */
   CHARGE(20, 30, 24, 220, 6.0, 28.0, 1.0F, false, false, true, 24.0F),
   /** The hooked blade on its chain: hooks the target and reels it in (flyers too). */
   CHAIN(16, 24, 14, 240, 6.0, 22.0, 0.9F, true, true, false, 10.0F),
   /** Grabs the target, lifts it overhead and throws it far. */
   GRAB(12, 30, 16, 280, 0.0, 4.0, 0.8F, false, true, false, 12.0F),
   /** A roar that hardens him for a while (first sight of a Hulkbuster, or badly hurt). */
   ROAR(10, 20, 10, 600, 0.0, 999.0, 0.0F, true, false, true, 0.0F),
   /** Against a Hulkbuster only: he charges into the armour, locks with it and drives it back. */
   CLASH(18, 24, 30, 300, 0.0, 20.0, 0.0F, false, false, false, 30.0F);

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

   CullMove(int telegraph, int active, int recovery, int cooldown, double minRange, double maxRange, float weight, boolean air, boolean control,
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
         case SWING -> 1.0;
         case SLAM -> s.heroesClose() >= 2 ? 2.2 : 1.0;
         case CHARGE -> (s.visible() ? 1.0 : 1.8) * (s.ranged() ? 1.5 : 1.0) * (s.hulkbuster() ? 0.6 : 1.0);
         case CHAIN -> s.airborne() ? 2.5 : s.ranged() ? 1.8 : 0.7;
         case GRAB -> s.hulkbuster() ? 0.4 : 1.0;
         case ROAR -> s.hulkbuster() || s.health() < 0.5F ? 5.0 : 0.0;
         case CLASH -> s.hulkbuster() ? 3.0 : 0.0;
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

   public static CullMove byId(int id) {
      CullMove[] all = values();
      return id >= 0 && id < all.length ? all[id] : null;
   }
}
