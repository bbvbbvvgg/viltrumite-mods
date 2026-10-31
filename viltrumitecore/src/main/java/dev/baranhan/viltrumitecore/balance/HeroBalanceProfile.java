package dev.baranhan.viltrumitecore.balance;

/**
 * PvE combat profile of one hero form (see {@link BalanceTable}).
 *
 * <ul>
 *   <li>{@code resilience}: share of a PvE hit removed after vanilla armor,
 *       enchantments, Resistance and the hero's own layers. A superhuman body,
 *       not invulnerability: 0.8 still lets 20 % of every hit through;</li>
 *   <li>{@code hitCap}: the most of the hero's max HP one ordinary PvE hit may
 *       remove (anti one-shot). Heavy hits ({@link BalanceRules#HEAVY_RAW} and
 *       above, a charged Viltrumite punch) use {@code heavyHitCap}, boss attacks
 *       use {@code bossHitCap}. 1 = no cap.</li>
 * </ul>
 *
 * PvP hits (a player attacker) never use this profile: hero-versus-hero
 * matchups stay with the heroes' own kits and contextual interactions.
 */
public record HeroBalanceProfile(HeroTier tier, float resilience, float hitCap, float heavyHitCap, float bossHitCap) {
   public HeroBalanceProfile {
      resilience = BalanceRules.clamp01(resilience);
      hitCap = BalanceRules.clampCap(hitCap);
      heavyHitCap = Math.max(hitCap, BalanceRules.clampCap(heavyHitCap));
      bossHitCap = Math.max(hitCap, BalanceRules.clampCap(bossHitCap));
   }

   /** Vanilla body: nothing removed, nothing capped. */
   public static final HeroBalanceProfile VANILLA = new HeroBalanceProfile(HeroTier.NONE, 0.0F, 1.0F, 1.0F, 1.0F);

   /** Cap (fraction of max HP) for one hit of this kind. */
   public float capFor(IncomingKind kind) {
      return switch (kind) {
         case HEAVY -> this.heavyHitCap;
         case BOSS -> this.bossHitCap;
         case EXEMPT, PVP -> 1.0F;
         default -> this.hitCap;
      };
   }

   /** Resilience for one hit of this kind (exempt and PvP hits keep it all). */
   public float resilienceFor(IncomingKind kind) {
      return switch (kind) {
         case EXEMPT, PVP -> 0.0F;
         // Gunfire stays a real threat to heroes: half the body layer (BalanceRules.PROJECTILE_RESILIENCE_SHARE).
         case PROJECTILE -> this.resilience * BalanceRules.PROJECTILE_RESILIENCE_SHARE;
         default -> this.resilience;
      };
   }
}
