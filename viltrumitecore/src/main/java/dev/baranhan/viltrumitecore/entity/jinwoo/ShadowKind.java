package dev.baranhan.viltrumitecore.entity.jinwoo;

/**
 * Kinds of shadow soldiers (spec §6.1). Pure numbers: entity code reads them,
 * tests check them. Elites are unique, named and larger.
 */
public enum ShadowKind {
   /** Crested helm, pauldrons, short sword. Slash 5 every 20 t. */
   INFANTRY("infantry", 24.0F, 4.0F, 24.0F, 5.0F, 20, 0.36, 1.0F, 1.0F, null),
   /** Tall knight with plume, cape, long sword. Slash 9 (16 t), lunge 5 blocks 11 (cd 80). Elite: Igris. */
   KNIGHT("knight", 40.0F, 8.0F, 60.0F, 9.0F, 16, 0.33, 1.08F, 1.15F, "igris"),
   /** Chitin humanoid with claws, wings, antennae. Double claw 4+4 (10 t), leap 8 blocks 8 (cd 60). Elite: Beru. */
   INSECT("insect", 30.0F, 6.0F, 50.0F, 4.0F, 10, 0.42, 1.0F, 1.12F, "beru"),
   /** Large horned bear. Paw slam 10 r 3 (28 t), taunt (cd 100). Knockback immune. Elite: Tank. */
   BEAST("beast", 60.0F, 10.0F, 90.0F, 10.0F, 28, 0.31, 1.0F, 1.2F, "tank");

   public static final int KNIGHT_LUNGE_COOLDOWN = 80;
   public static final double KNIGHT_LUNGE_RANGE = 5.0;
   public static final float KNIGHT_LUNGE_DAMAGE = 11.0F;
   public static final int INSECT_LEAP_COOLDOWN = 60;
   public static final double INSECT_LEAP_RANGE = 8.0;
   public static final float INSECT_LEAP_DAMAGE = 8.0F;
   public static final double BEAST_SLAM_RADIUS = 3.0;
   public static final int BEAST_TAUNT_COOLDOWN = 100;
   public static final double BEAST_TAUNT_RANGE = 10.0;
   /** Melee reach of every kind (blocks from the target's box). */
   public static final double REACH = 2.2;

   private final String key;
   public final float health;
   public final float armor;
   public final float eliteHealth;
   public final float damage;
   public final int attackInterval;
   public final double speed;
   public final float baseScale;
   public final float eliteScale;
   private final String eliteName;

   ShadowKind(String key, float health, float armor, float eliteHealth, float damage, int attackInterval, double speed,
      float baseScale, float eliteScale, String eliteName) {
      this.key = key;
      this.health = health;
      this.armor = armor;
      this.eliteHealth = eliteHealth;
      this.damage = damage;
      this.attackInterval = attackInterval;
      this.speed = speed;
      this.baseScale = baseScale;
      this.eliteScale = eliteScale;
      this.eliteName = eliteName;
   }

   public String key() {
      return this.key;
   }

   /** Elite name key (lang {@code entity.viltrumitecore.shadow.<name>}), or null for infantry. */
   public String eliteName() {
      return this.eliteName;
   }

   public float maxHealth(boolean elite) {
      return elite ? this.eliteHealth : this.health;
   }

   public float scale(boolean elite) {
      return elite ? this.eliteScale : this.baseScale;
   }

   public boolean knockbackImmune() {
      return this == BEAST;
   }

   /** Attack clip length in ticks (contact at the middle). */
   public int attackLength() {
      return switch (this) {
         case INFANTRY -> 12;
         case KNIGHT -> 16;
         case INSECT -> 10;
         case BEAST -> 20;
      };
   }

   public int attackContact() {
      return switch (this) {
         case INFANTRY -> 6;
         case KNIGHT -> 9;
         case INSECT -> 3;
         case BEAST -> 12;
      };
   }

   public static ShadowKind byOrdinal(int ordinal) {
      ShadowKind[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INFANTRY;
   }

   public static ShadowKind byKey(String key) {
      for (ShadowKind kind : values()) {
         if (kind.key.equals(key)) {
            return kind;
         }
      }
      return INFANTRY;
   }

   /**
    * Pure classification (spec §5.7). {@code typeId} is the registry path ("wither_skeleton").
    * Returns null when the creature cannot become a shadow (villagers, peaceful animals).
    */
   public static ShadowKind classify(String typeId, boolean player, boolean arthropod, boolean hostile, boolean peaceful) {
      if (player) {
         return KNIGHT;
      }
      switch (typeId) {
         case "wither_skeleton", "vindicator", "evoker", "illusioner", "piglin_brute", "warden":
            return KNIGHT;
         case "polar_bear", "ravager", "hoglin", "zoglin", "iron_golem", "sniffer":
            return BEAST;
         case "villager", "wandering_trader", "zombie_villager_peaceful":
            return null;
         default:
            break;
      }
      if (arthropod) {
         return INSECT;
      }
      if (peaceful && !hostile) {
         return null;
      }
      return hostile ? INFANTRY : null;
   }
}
