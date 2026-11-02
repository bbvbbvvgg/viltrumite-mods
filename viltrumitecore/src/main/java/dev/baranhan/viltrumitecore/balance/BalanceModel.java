package dev.baranhan.viltrumitecore.balance;

import java.util.List;

/**
 * Pure calculator behind the balance document's tables
 * (docs/balance/2026-10-31-global-rebalance.md) and {@code BalanceModelTest}: the
 * survivability sheet of each hero form, read from the same constants the game uses,
 * pushed through the same formulas the damage path runs (legacy chassis step, vanilla
 * armor, the hero's PvE profile). No AI, movement or randomness: a sheet answers how
 * many hits of a given size a hero takes, not who wins a fight.
 */
public final class BalanceModel {
   /**
    * One hero form. {@code chassisReduction} is the legacy Viltrumite-kit percentage (Homelander);
    * {@code ownResist} a hero's own multiplicative resist (Reinhard's 99.9 %); {@code hpFloor}
    * true when the hero cannot drop below 1 HP from ordinary damage.
    */
   public record HeroSheet(String name, float maxHealth, float armor, float toughness, float chassisReduction, float ownResist,
      boolean hpFloor, HeroBalanceProfile profile) {

      /** HP lost to one hit of {@code raw} of kind {@code kind} (Normal difficulty already applied to raw). */
      public float hpPerHit(float raw, IncomingKind kind) {
         float hit = raw;
         if (this.chassisReduction > 0.0F) {
            hit = hit * (1.0F - this.chassisReduction * 0.01F);
         }

         hit = kind == IncomingKind.EXEMPT ? hit : BalanceRules.afterArmor(hit, this.armor, this.toughness);
         hit *= 1.0F - this.ownResist;
         if (kind == IncomingKind.EXEMPT || kind == IncomingKind.PVP) {
            return hit;
         }

         return BalanceRules.heroHit(hit, this.maxHealth, this.profile.resilienceFor(kind), this.profile.capFor(kind));
      }

      /** Same, for a hit that bypasses armor but still reaches the hero layers (Warden sonic boom). */
      public float hpPerArmorPiercingHit(float raw, IncomingKind kind) {
         float hit = raw * (1.0F - this.chassisReduction * 0.01F) * (1.0F - this.ownResist);
         return BalanceRules.heroHit(hit, this.maxHealth, this.profile.resilienceFor(kind), this.profile.capFor(kind));
      }

      /** Hits to drop the hero from full HP (healing ignored); infinite with an HP floor. */
      public int hitsToKill(float raw, IncomingKind kind) {
         return this.hpFloor ? Integer.MAX_VALUE : BalanceRules.hitsToKill(this.maxHealth, this.hpPerHit(raw, kind));
      }

      /** Seconds the hero lasts against the Warden's melee chain, with the hero sustain healing. */
      public float secondsVersusWardenMelee() {
         if (this.hpFloor) {
            return Float.POSITIVE_INFINITY;
         }

         float dps = this.hpPerHit(BalanceRules.WARDEN_MELEE, IncomingKind.BOSS) * 20.0F / BalanceRules.WARDEN_MELEE_INTERVAL;
         return BalanceRules.secondsToDie(this.maxHealth, dps, BalanceRules.sustainHealPerSecond());
      }

      /** Seconds the hero lasts against an iron golem's average swing, with the hero sustain healing. */
      public float secondsVersusGolem() {
         if (this.hpFloor) {
            return Float.POSITIVE_INFINITY;
         }

         float dps = this.hpPerHit(BalanceRules.GOLEM_ATTACK_MEAN, IncomingKind.MELEE) * 20.0F / BalanceRules.GOLEM_ATTACK_INTERVAL;
         return BalanceRules.secondsToDie(this.maxHealth, dps, BalanceRules.sustainHealPerSecond());
      }
   }

   public static final HeroSheet HUMAN = new HeroSheet("Human", 20.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, HeroBalanceProfile.VANILLA);
   public static final HeroSheet REINHARD = new HeroSheet("Reinhard", 20.0F, 0.0F, 0.0F, 0.0F, 0.999F, true, BalanceTable.SWORD_SAINT);
   public static final HeroSheet REGULUS = new HeroSheet("Regulus (Lion off)", 40.0F, 8.0F, 0.0F, 0.0F, 0.0F, false, BalanceTable.LION);
   public static final HeroSheet DIO = new HeroSheet("DIO", 40.0F, 6.0F, 0.0F, 0.0F, 0.0F, false, BalanceTable.VAMPIRE);
   public static final HeroSheet JINWOO = new HeroSheet("Jinwoo", 40.0F, 6.0F, 2.0F, 0.0F, 0.0F, false, BalanceTable.MONARCH);
   public static final HeroSheet THOR = new HeroSheet("Thor", 44.0F, 8.0F, 4.0F, 0.0F, 0.0F, false, BalanceTable.THUNDER_GOD);
   public static final HeroSheet RED_HULK = new HeroSheet("Red Hulk", 48.0F, 10.0F, 4.0F, 0.0F, 0.0F, false, BalanceTable.RAGE_TITAN);
   public static final HeroSheet GHOST_RIDER = new HeroSheet("Ghost Rider (Rider form)", 36.0F, 8.0F, 4.0F, 0.0F, 0.0F, false,
      BalanceTable.SPIRIT_OF_VENGEANCE);
   public static final HeroSheet IRON_MAN_SUIT = new HeroSheet("Iron Man (nano suit)", 40.0F, 28.0F, 8.0F, 0.0F, 0.0F, false, BalanceTable.ARMORED);
   public static final HeroSheet TONY = new HeroSheet("Tony (no suit)", 20.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, BalanceTable.UNARMORED);
   public static final HeroSheet CAPTAIN = new HeroSheet("Captain America", 40.0F, 8.0F, 4.0F, 0.0F, 0.0F, false, BalanceTable.SUPER_SOLDIER);
   public static final HeroSheet HOMELANDER = new HeroSheet("Homelander", 40.0F, 10.0F, 0.0F, 40.0F, 0.0F, false, BalanceTable.SUPE);
   public static final HeroSheet SINCLAIR = new HeroSheet("Sinclair", 20.0F + (float)BalanceTable.SINCLAIR_BONUS_HEALTH, 4.0F, 0.0F, 0.0F, 0.0F, false,
      BalanceTable.SHOWMAN);

   public static final List<HeroSheet> HEROES = List.of(REINHARD, REGULUS, DIO, JINWOO, THOR, RED_HULK, GHOST_RIDER, IRON_MAN_SUIT, CAPTAIN, HOMELANDER, SINCLAIR);

   /** Raw damage the Hulkbuster shell soaks before it breaks. */
   public static float hulkbusterEffectiveHealth(float durability, float plating) {
      return plating <= 0.0F ? Float.POSITIVE_INFINITY : durability / plating;
   }

   /** Raw amount of a Viltrumite kit punch ({@code PunchImpactManager}): 2 x base + 3 x base x throttle. */
   public static float viltrumitePunch(float baseDamage, float throttle) {
      return baseDamage * 2.0F + throttle * baseDamage * 3.0F;
   }

   /** Raw amount of a Viltrumite kit chop ({@code ChopImpactManager}): 1.75 x base. */
   public static float viltrumiteChop(float baseDamage) {
      return baseDamage * 1.75F;
   }

   private BalanceModel() {
   }
}
