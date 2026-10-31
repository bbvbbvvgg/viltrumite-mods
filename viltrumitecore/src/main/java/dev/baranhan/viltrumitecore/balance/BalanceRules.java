package dev.baranhan.viltrumitecore.balance;

/**
 * Pure numbers of the global balance model. No Minecraft classes: every
 * formula here is unit-tested and mirrors the vanilla 1.20.1 code it names.
 *
 * <p>Vanilla references (Minecraft 1.20.1, Normal difficulty):
 * <ul>
 *   <li>Iron golem: 100 HP, armor 0, attack damage 15; {@code IronGolem.doHurtTarget}
 *       deals {@code 15/2 + nextInt(15)} = 7.5..21.5 (mean 14.5), one swing per 20 ticks
 *       at best (it uses the 20-tick melee goal and its 10-tick attack animation);</li>
 *   <li>Warden: 500 HP, armor 0, knockback resistance 1, attack damage 30, melee
 *       cooldown 18 ticks ({@code WardenAi} MeleeAttack.create(18)); sonic boom 10 damage
 *       that bypasses armor and enchantments, 40-tick cooldown after its 34-tick charge;</li>
 *   <li>A hit's armor step is {@code CombatRules.getDamageAfterAbsorb}; Regeneration I
 *       heals 1 HP every 50 ticks; natural regeneration at food 18+ with no
 *       saturation heals 1 HP every 80 ticks.</li>
 * </ul>
 */
public final class BalanceRules {
   public static final float GOLEM_HEALTH = 100.0F;
   public static final float GOLEM_ATTACK_MIN = 7.5F;
   public static final float GOLEM_ATTACK_MAX = 21.5F;
   public static final float GOLEM_ATTACK_MEAN = 14.5F;
   public static final int GOLEM_ATTACK_INTERVAL = 20;

   public static final float WARDEN_HEALTH = 500.0F;
   public static final float WARDEN_MELEE = 30.0F;
   public static final int WARDEN_MELEE_INTERVAL = 18;
   public static final float WARDEN_SONIC_BOOM = 10.0F;
   public static final int WARDEN_SONIC_CYCLE = 34 + 40;

   /** Raw (pre-armor) amount from which a mob melee hit counts as heavy (a charged Viltrumite punch). */
   public static final float HEAVY_RAW = 100.0F;
   /** Share of a hero's resilience that applies to projectiles (Chitauri fire must stay a threat). */
   public static final float PROJECTILE_RESILIENCE_SHARE = 0.5F;

   /** Regeneration I (amplifier 0): one heart-half every 50 ticks. */
   public static final int REGEN_I_INTERVAL = 50;
   /** Vanilla natural regeneration interval at food 18+ once saturation is spent. */
   public static final int NATURAL_REGEN_INTERVAL = 80;

   private BalanceRules() {
   }

   public static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   /** A per-hit cap is a share of max HP in (0, 1]. */
   public static float clampCap(float value) {
      return value <= 0.0F ? 1.0F : Math.min(1.0F, value);
   }

   // ---- vanilla formulas ---------------------------------------------------------------------

   /** {@code CombatRules.getDamageAfterAbsorb} (1.20.1). */
   public static float afterArmor(float damage, float armor, float toughness) {
      float f = 2.0F + toughness / 4.0F;
      float g = Math.max(armor - damage / f, armor * 0.2F);
      g = Math.min(g, 20.0F);
      return damage * (1.0F - g / 25.0F);
   }

   /** Resistance effect step ({@code LivingEntity.getDamageAfterMagicAbsorb}). */
   public static float afterResistance(float damage, int amplifier) {
      if (amplifier < 0) {
         return damage;
      }

      int reduction = (amplifier + 1) * 5;
      return Math.max(damage * (25 - reduction) / 25.0F, 0.0F);
   }

   /** Difficulty scaling of a mob hit on a player ({@code Player.hurt}): 0 peaceful, 1 easy, 2 normal, 3 hard. */
   public static float difficultyScaled(float damage, int difficulty) {
      return switch (difficulty) {
         case 0 -> 0.0F;
         case 1 -> Math.min(damage / 2.0F + 1.0F, damage);
         case 3 -> damage * 3.0F / 2.0F;
         default -> damage;
      };
   }

   /** Legacy Viltrumite chassis step ({@code PlayerStatsMixin}): returns 0 when the hit is ignored. */
   public static float viltrumiteChassis(float raw, float reductionPercent, float ignoreThreshold) {
      float reduced = raw * (1.0F - reductionPercent * 0.01F);
      return reduced < ignoreThreshold ? 0.0F : reduced;
   }

   /** Smallest raw hit a Viltrumite chassis does not ignore. */
   public static float viltrumiteIgnoredBelow(float reductionPercent, float ignoreThreshold) {
      float pass = 1.0F - reductionPercent * 0.01F;
      return pass <= 0.0F ? Float.POSITIVE_INFINITY : ignoreThreshold / pass;
   }

   /** Raw damage a Viltrumite chassis absorbs before its HP runs out. */
   public static float viltrumiteEffectiveHealth(float health, float reductionPercent) {
      float pass = 1.0F - reductionPercent * 0.01F;
      return pass <= 0.0F ? Float.POSITIVE_INFINITY : health / pass;
   }

   // ---- the hero PvE layer -------------------------------------------------------------------

   /**
    * HP a hero loses from one PvE hit: the amount after armor and the hero's own
    * layers, minus the profile's resilience, capped at a share of max HP.
    */
   public static float heroHit(float afterArmor, float maxHealth, float resilience, float cap) {
      if (afterArmor <= 0.0F) {
         return afterArmor;
      }

      float kept = afterArmor * (1.0F - clamp01(resilience));
      float limit = Math.max(1.0F, clampCap(cap) * Math.max(1.0F, maxHealth));
      return Math.min(kept, limit);
   }

   /** Hits needed to take {@code health} with hits of {@code perHit} (healing ignored). */
   public static int hitsToKill(float health, float perHit) {
      if (perHit <= 0.0F) {
         return Integer.MAX_VALUE;
      }

      return (int)Math.ceil(health / perHit - 1.0E-4F);
   }

   /** HP per second healed by Regeneration I plus vanilla natural regeneration at full food. */
   public static float sustainHealPerSecond() {
      return 20.0F / REGEN_I_INTERVAL + 20.0F / NATURAL_REGEN_INTERVAL;
   }

   /**
    * Seconds to lose {@code health} to {@code damagePerSecond}, with {@code healPerSecond}
    * of sustain; infinite when the sustain outheals the damage.
    */
   public static float secondsToDie(float health, float damagePerSecond, float healPerSecond) {
      float net = damagePerSecond - healPerSecond;
      return net <= 0.0F ? Float.POSITIVE_INFINITY : health / net;
   }

   // ---- summoned groups -----------------------------------------------------------------------

   /**
    * Damage factor of each summon when {@code active} summons of one owner fight:
    * full damage up to {@code freeCount}, then {@code sqrt(freeCount / active)}, so a
    * group of n deals {@code sqrt(freeCount * n)} summons' worth - it still grows with
    * the army, but never linearly.
    */
   public static float groupFactor(int active, int freeCount) {
      if (active <= freeCount || freeCount <= 0) {
         return 1.0F;
      }

      return (float)Math.sqrt((double)freeCount / active);
   }

   /** Group damage of {@code active} summons dealing {@code perSummon} each (in summons' worth times perSummon). */
   public static float groupDamage(int active, int freeCount, float perSummon) {
      return active * perSummon * groupFactor(active, freeCount);
   }
}
