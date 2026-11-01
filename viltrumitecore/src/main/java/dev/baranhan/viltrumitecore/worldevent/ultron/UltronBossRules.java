package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * Ultron Prime's numbers (pure, unit-tested): health for the group, damage caps per hit and per second,
 * the three combat stages, and what each powered node feeds him through its tether. The network's
 * state decides how hard the finale is: every node the players took down before is a buff he lacks.
 */
public final class UltronBossRules {
   public enum Stage {
      ASSEMBLY("assembly"),
      TACTICAL("tactical"),
      AERIAL("aerial"),
      OVERDRIVE("overdrive"),
      DEFEATED("defeated");

      private final String key;

      Stage(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }

      public static Stage byId(int id) {
         Stage[] all = values();
         return id >= 0 && id < all.length ? all[id] : TACTICAL;
      }
   }

   public static final float BASE_HEALTH = 1800.0F;
   public static final float HEALTH_PER_EXTRA_HERO = 0.5F;
   public static final int MAX_COUNTED_HEROES = 6;
   public static final float HIT_CAP = 0.03F;
   public static final float MIN_HIT_CAP = 30.0F;
   public static final float SECOND_CAP = 0.05F;
   /** Health share where the stages change. */
   public static final float AERIAL_AT = 0.65F;
   public static final float OVERDRIVE_AT = 0.30F;
   /** Transition cinematic (the move's length) — he takes {@link #TRANSITION_REDUCTION} less damage during it. */
   public static final float TRANSITION_REDUCTION = 0.8F;
   /** Tether buffs per powered node. */
   public static final float BULWARK_REDUCTION = 0.12F;
   public static final float CORE_REDUCTION = 0.10F;
   public static final float MAX_REDUCTION = 0.45F;
   public static final float REPAIR_REGEN = 0.004F;
   public static final float MAX_REGEN = 0.012F;
   public static final float UPLINK_COOLDOWN = 0.88F;
   public static final float MIN_COOLDOWN_FACTOR = 0.6F;
   public static final float CORE_DAMAGE = 0.10F;
   public static final int FOUNDRY_SUMMON_TICKS = 30 * UltronRules.TPS;
   /** SINGULARITY / ASCENDANT: each learned protocol (activations) adds this reduction, at most {@link #MAX_LEARNED}. */
   public static final float LEARNED_REDUCTION = 0.04F;
   public static final float MAX_LEARNED = 0.12F;

   private UltronBossRules() {
   }

   public static float maxHealth(int heroes) {
      int counted = Math.max(1, Math.min(MAX_COUNTED_HEROES, heroes));
      return BASE_HEALTH * (1.0F + HEALTH_PER_EXTRA_HERO * (counted - 1));
   }

   public static float hitCap(float maxHealth) {
      return Math.max(MIN_HIT_CAP, HIT_CAP * maxHealth);
   }

   public static float secondCap(float maxHealth) {
      return Math.max(hitCap(maxHealth), SECOND_CAP * maxHealth);
   }

   /** The stage a fighting Ultron should be in at this health share (never back to an earlier one). */
   public static Stage stageFor(float healthShare, Stage current) {
      Stage want = healthShare <= OVERDRIVE_AT ? Stage.OVERDRIVE : healthShare <= AERIAL_AT ? Stage.AERIAL : Stage.TACTICAL;
      return want.ordinal() > current.ordinal() ? want : current;
   }

   /** What the powered nodes give him right now. */
   public record Tethers(float reduction, float regenPerSecond, float cooldownFactor, float damageFactor, boolean summons, boolean sensors) {
      public static final Tethers NONE = new Tethers(0.0F, 0.0F, 1.0F, 1.0F, false, false);
   }

   /**
    * Buffs from the active nodes: bulwarks and the core harden him, repair nodes heal him (share of max health
    * per second), uplinks speed his moves, the core adds damage, foundries send help, sensors sharpen his aim.
    */
   public static Tethers tethers(int bulwarks, int repairs, int uplinks, boolean core, int foundries, int sensors, int learned, boolean ascendant) {
      float reduction = Math.min(MAX_REDUCTION, bulwarks * BULWARK_REDUCTION + (core ? CORE_REDUCTION : 0.0F)
         + (ascendant ? Math.min(MAX_LEARNED, learned * LEARNED_REDUCTION) : 0.0F));
      float regen = Math.min(MAX_REGEN, repairs * REPAIR_REGEN);
      float cooldown = Math.max(MIN_COOLDOWN_FACTOR, (float)Math.pow(UPLINK_COOLDOWN, uplinks));
      float damage = 1.0F + (core ? CORE_DAMAGE : 0.0F);
      return new Tethers(reduction, regen, cooldown, damage, foundries > 0, sensors > 0);
   }

   /** Damage he takes from a hit after the tether reduction and a transition's armour. */
   public static float incoming(float amount, Tethers t, boolean transition) {
      float a = amount * (1.0F - t.reduction());
      return transition ? a * (1.0F - TRANSITION_REDUCTION) : a;
   }

   /** Move damage scales mildly with the group: solo 1.0, six heroes 1.25. */
   public static float damageScale(int heroes) {
      int counted = Math.max(1, Math.min(MAX_COUNTED_HEROES, heroes));
      return 1.0F + 0.05F * (counted - 1);
   }
}
