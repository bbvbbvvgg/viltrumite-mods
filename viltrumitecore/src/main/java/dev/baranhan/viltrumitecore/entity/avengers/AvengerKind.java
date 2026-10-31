package dev.baranhan.viltrumitecore.entity.avengers;

/**
 * The five summoned Avengers (spec §5.7). Order = circle slot after Steve (60° apart) and the synced kind byte
 * (append only). Stats are the fight values; the ceremony makes them untouchable.
 */
public enum AvengerKind {
   IRON_MAN("iron_man", 140.0F, 14.0, 0.34, 0.7F, 2.0F, 0.8),
   THOR("thor", 180.0F, 14.0, 0.34, 0.72F, 2.05F, 0.9),
   HULK("hulk", 300.0F, 10.0, 0.4, 1.6F, 3.3F, 1.0),
   BLACK_WIDOW("black_widow", 80.0F, 5.0, 0.42, 0.6F, 1.8F, 0.2),
   HAWKEYE("hawkeye", 80.0F, 5.0, 0.36, 0.6F, 1.85F, 0.2);

   private final String key;
   public final float health;
   public final double armor;
   public final double speed;
   public final float width;
   public final float height;
   public final double knockbackResist;

   AvengerKind(String key, float health, double armor, double speed, float width, float height, double knockbackResist) {
      this.key = key;
      this.health = health;
      this.armor = armor;
      this.speed = speed;
      this.width = width;
      this.height = height;
      this.knockbackResist = knockbackResist;
   }

   public String key() {
      return this.key;
   }

   /**
    * The hero whose name and portrait a subtitle line spoken by this Avenger shows: Iron Man and Thor are heroes of
    * their own (save keys "ironman" / "thor"), the others speak through Captain America's voice.
    */
   public dev.baranhan.viltrumitecore.hero.HeroId voice() {
      dev.baranhan.viltrumitecore.hero.HeroId own = dev.baranhan.viltrumitecore.hero.HeroId.fromKey(this.key.replace("_", ""));
      return own != null ? own : dev.baranhan.viltrumitecore.hero.HeroId.CAPTAIN;
   }

   /** Circle slot (1..5; Steve is 0). */
   public int slot() {
      return this.ordinal() + 1;
   }

   public static AvengerKind byOrdinal(int i) {
      AvengerKind[] v = values();
      return v[Math.max(0, Math.min(v.length - 1, i))];
   }

   /** Arrives through the Quinjet (Natasha and Clint). */
   public boolean byQuinjet() {
      return this == BLACK_WIDOW || this == HAWKEYE;
   }
}
