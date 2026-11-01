package dev.baranhan.viltrumitecore.balance;

/**
 * The one table of the global balance model (docs/balance/2026-10-31-global-rebalance.md).
 * Heroes pick their profile through {@code HeroDefinition.balanceProfile}; NPCs,
 * summons and world events read their numbers here. Change a number here, and
 * the matching row of the balance document and {@code BalanceModelTest}.
 */
public final class BalanceTable {
   // ---- hero PvE profiles: tier, resilience, hit cap, heavy-hit cap, boss-hit cap ------------

   /** Reinhard: his own Divine Protection (99.9 %) and HP floor already rule; the shared layer adds nothing. */
   public static final HeroBalanceProfile SWORD_SAINT = new HeroBalanceProfile(HeroTier.S_PLUS, 0.0F, 1.0F, 1.0F, 1.0F);
   /** Regulus outside the Lion's Heart (the stopped heart absorbs everything by itself). */
   public static final HeroBalanceProfile LION = new HeroBalanceProfile(HeroTier.S, 0.80F, 0.25F, 0.45F, 0.40F);
   /** DIO with The World. */
   public static final HeroBalanceProfile VAMPIRE = new HeroBalanceProfile(HeroTier.A_PLUS, 0.80F, 0.25F, 0.45F, 0.40F);
   /** Sung Jinwoo, the Shadow Monarch. */
   public static final HeroBalanceProfile MONARCH = new HeroBalanceProfile(HeroTier.A_PLUS, 0.80F, 0.25F, 0.45F, 0.40F);
   /** Thor (God of War Ragnarök). */
   public static final HeroBalanceProfile THUNDER_GOD = new HeroBalanceProfile(HeroTier.A, 0.85F, 0.25F, 0.45F, 0.40F);
   /**
    * Red Hulk: a heavy melee body (armor 10, toughness 4, 48 HP) whose heat multiplies his blows;
    * slightly less resilient than Thor, and still below Reinhard and Regulus.
    */
   public static final HeroBalanceProfile RAGE_TITAN = new HeroBalanceProfile(HeroTier.A, 0.80F, 0.25F, 0.45F, 0.40F);
   /**
    * Ghost Rider as the Spirit of Vengeance (armor 8, toughness 4, 36 HP, fire-proof): tier A beside Thor and the
    * Red Hulk, a little less resilient than both; Johnny Blaze without the Rider is an ordinary man (UNARMORED).
    */
   public static final HeroBalanceProfile SPIRIT_OF_VENGEANCE = new HeroBalanceProfile(HeroTier.A, 0.75F, 0.25F, 0.45F, 0.40F);
   /**
    * Iron Man in an intact suit (nano Mark 50 or a mark). The suit's armor 28 already
    * removes up to 80 % of small hits, so the body layer is lighter; the cap is what keeps
    * a Viltrumite punch from deleting him (several ordinary punches, two heavy ones).
    */
   public static final HeroBalanceProfile ARMORED = new HeroBalanceProfile(HeroTier.A, 0.40F, 0.25F, 0.50F, 0.40F);
   /** Tony Stark out of the suit: a human. */
   public static final HeroBalanceProfile UNARMORED = HeroBalanceProfile.VANILLA;
   /** Captain America (base form; the ultimate adds the Avengers, not a tougher body). */
   public static final HeroBalanceProfile SUPER_SOLDIER = new HeroBalanceProfile(HeroTier.A_ON_ULTIMATE, 0.65F, 0.30F, 0.50F, 0.45F);
   /** Homelander: the legacy 40 % chassis plus this layer. */
   public static final HeroBalanceProfile SUPE = new HeroBalanceProfile(HeroTier.B_PLUS, 0.50F, 0.30F, 0.50F, 0.50F);
   /** The Magnificent Sinclair. */
   public static final HeroBalanceProfile SHOWMAN = new HeroBalanceProfile(HeroTier.B, 0.50F, 0.30F, 0.50F, 0.50F);

   // ---- hero support system (HeroSustain) ----------------------------------------------------

   /** Server ticks between two sustain passes (food top-up, Regeneration I refresh). */
   public static final int SUSTAIN_INTERVAL = 20;
   /** Amplifier of the shared regeneration: Regeneration I. */
   public static final int SUSTAIN_REGEN_AMPLIFIER = 0;
   /**
    * Saturation the hero keeps at most. 0 keeps healing deterministic: with saturation
    * vanilla heals 1 HP per 10 ticks, which on top of Regeneration I would be near-instant.
    */
   public static final float SUSTAIN_SATURATION_CAP = 0.0F;

   // ---- summoned groups (BalanceRules.groupFactor) -------------------------------------------

   /** Shadow soldiers of one Monarch that hit at full strength before the group factor. */
   public static final int SHADOW_FREE_COUNT = 4;
   /** Iron Legion suits of one owner that hit at full strength before the group factor. */
   public static final int LEGION_FREE_COUNT = 3;
   /** Radius in which an owner's summons count as one fighting group. */
   public static final double GROUP_RADIUS = 64.0;

   // ---- Viltrumite NPCs (base damage, ignore threshold, reduction %, heal factor) ------------

   public record ViltrumiteNpc(float baseDamage, float ignoreThreshold, float reductionPercent, float healFactor) {
   }

   /**
    * Ignore thresholds were 0.2-0.6 HP after a 95.5-98.5 % reduction: Conquest ignored
    * every raw hit under 40, so most hero attacks did literally nothing. Now each NPC
    * ignores raw hits under about 3 (a vanilla fist or a stray arrow) and keeps its
    * huge effective health; Conquest stays the strongest (800 raw EHP, 1.6 Wardens).
    */
   public static final ViltrumiteNpc THULA = new ViltrumiteNpc(18.0F, 0.135F, 95.5F, 0.25F);
   public static final ViltrumiteNpc LUCAN = new ViltrumiteNpc(25.0F, 0.114F, 96.2F, 0.2F);
   public static final ViltrumiteNpc VIDOR = new ViltrumiteNpc(22.0F, 0.123F, 95.9F, 0.2F);
   public static final ViltrumiteNpc CONQUEST = new ViltrumiteNpc(32.0F, 0.075F, 97.5F, 0.3F);

   // ---- Chitauri -----------------------------------------------------------------------------

   /** Rifle bolt: 4 at Normal = 5 hits on an unarmored 20 HP human (Easy 3 = 7 hits, Hard 6 = 4 hits). */
   public static final float CHITAURI_RIFLE = 4.0F;
   /** Cannon bolt (chariots, heavy gunners); its splash adds the explosion step. */
   public static final float CHITAURI_CANNON = 9.0F;
   /** Interceptor twin blasters: light, fast bolts (two per volley). */
   public static final float CHITAURI_INTERCEPTOR = 3.0F;
   /** Siege lancer shell: the direct hit; the blast and the broken cover do the rest. */
   public static final float CHITAURI_SIEGE = 8.0F;
   /** Heavy assault ground slam (radius 3.5, falls off to half at the edge). */
   public static final float CHITAURI_SLAM = 10.0F;

   /**
    * Chitauri bodies (docs/balance/2026-10-31-global-rebalance.md, Chitauri rows). The foot soldier is the mass enemy:
    * 14 HP and armor 2 (was 26 / 6 / toughness 2) so a strong hero's ordinary hit (8-12) drops one in 1-2 hits.
    * The elites stay dangerous through tactics, not a wall of health: a heavy is 60 HP / armor 10 and no single hit
    * takes more than {@link #CHITAURI_HEAVY_HIT_CAP} of it (no one-shot, no endless sponge either).
    */
   public static final float CHITAURI_SOLDIER_HEALTH = 14.0F;
   public static final float CHITAURI_SOLDIER_ARMOR = 2.0F;
   public static final float CHITAURI_CAPTOR_HEALTH = 18.0F;
   public static final float CHITAURI_HEAVY_HEALTH = 60.0F;
   public static final float CHITAURI_HEAVY_ARMOR = 10.0F;
   /** Share of a heavy's max health one hit may take at most (bypass-invulnerability damage ignores the cap). */
   public static final float CHITAURI_HEAVY_HIT_CAP = 0.34F;
   public static final float CHITAURI_COMMANDER_HEALTH = 70.0F;
   public static final float CHITAURI_COMMANDER_HIT_CAP = 0.3F;
   public static final float CHITAURI_INTERCEPTOR_HEALTH = 16.0F;
   public static final float CHITAURI_SIEGE_HEALTH = 90.0F;
   public static final float CHITAURI_SIEGE_HIT_CAP = 0.25F;
   public static final float CHITAURI_BEACON_HEALTH = 40.0F;

   // ---- Ultron uprising (docs/plans/2026-11-01-ultron-uprising.md) -----------------------------

   /** Scout drone zap (beam): a pest, 7 hits on an unarmored human. */
   public static final float ULTRON_DRONE_ZAP = 3.0F;
   /** Combat robot: arm-cannon bolt (5 hits on an unarmored human) and each strike of its blade combo. */
   public static final float ULTRON_SENTRY_BOLT = 4.0F;
   public static final float ULTRON_SENTRY_STRIKE = 5.0F;
   /** Heavy assault robot: ground slam (blast around it), arm swing, charge, and each shoulder missile (plus its splash). */
   public static final float ULTRON_BRUTE_SLAM = 12.0F;
   public static final float ULTRON_BRUTE_SWING = 10.0F;
   public static final float ULTRON_BRUTE_CHARGE = 9.0F;
   public static final float ULTRON_MISSILE = 6.0F;
   /** Flying interceptor: each bolt of its burst, and the dive strike. */
   public static final float ULTRON_INTERCEPTOR_BOLT = 3.0F;
   public static final float ULTRON_INTERCEPTOR_DIVE = 9.0F;
   /**
    * Ultron Prime (a boss: heroes take his hits under their boss cap). Group scale and the core tether
    * ({@code UltronBossRules}) multiply these; the beam hits on each of its six contacts.
    */
   public static final float ULTRON_PRIME_JAB = 9.0F;
   public static final float ULTRON_PRIME_CROSS = 11.0F;
   public static final float ULTRON_PRIME_UPPERCUT = 14.0F;
   public static final float ULTRON_PRIME_BACKHAND = 12.0F;
   public static final float ULTRON_PRIME_PALM = 16.0F;
   public static final float ULTRON_PRIME_BEAM = 4.0F;
   public static final float ULTRON_PRIME_SLAM = 18.0F;
   public static final float ULTRON_PRIME_DIVE = 20.0F;
   public static final float ULTRON_PRIME_DASH = 10.0F;

   // ---- Iron Man ------------------------------------------------------------------------------

   /**
    * Hulkbuster plating: durability lost per point of incoming damage. 0.36 made the
    * 800-point shell worth 2222 raw damage (74 Warden hits) on top of the nano suit;
    * 0.6 keeps it a heavy war machine (1333 raw, 44 Warden hits) without outlasting S.
    */
   public static final float HULKBUSTER_PLATING = 0.6F;

   // ---- Homelander / Sinclair -----------------------------------------------------------------

   /** Laser damage per hit (every 4 ticks): 10 DPS while the eyes stay cool. */
   public static final float HOMELANDER_LASER = 2.0F;
   /** Sinclair's chassis bonus over 20 HP. */
   public static final double SINCLAIR_BONUS_HEALTH = 10.0;
   /** Sinclair's spark shot (his basic ranged attack must beat a bare fist). */
   public static final float SINCLAIR_SPARK = 2.5F;

   // ---- Regulus versus divine damage -------------------------------------------------------

   /**
    * Heart strain a divine hit (Reinhard's) adds to a stopped Lion's Heart even when a
    * Little King subject carries the heart. Seven divine hits force the seizure: the
    * Sword Saint is the one opponent the hosted heart cannot ignore forever.
    */
   public static final int DIVINE_LION_STRAIN = 150;

   private BalanceTable() {
   }
}
