package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * Every number of the Thanos event (pure: no Minecraft classes, unit-tested). Ticks are server
 * ticks at 20 TPS; distances are blocks.
 */
public final class ThanosRules {
   public static final int TPS = 20;
   /** The fight lasts six minutes of active event ticks. */
   public static final int DURATION_TICKS = 6 * 60 * TPS;
   /** The last minute is the FINAL phase. */
   public static final int FINAL_TICKS = 60 * TPS;
   /** Time pushes the phase: ESCALATION after 2 min, ONSLAUGHT after 4 min. */
   public static final int ESCALATION_TICK = 2 * 60 * TPS;
   public static final int ONSLAUGHT_TICK = 4 * 60 * TPS;
   /** Health pushes the phase: ESCALATION at 70 % left, ONSLAUGHT at 40 %. */
   public static final float ESCALATION_HEALTH = 0.70F;
   public static final float ONSLAUGHT_HEALTH = 0.40F;
   /** Arrival through a Space portal before the clock starts (he is untouchable meanwhile). */
   public static final int INTRO_TICKS = 80;
   /** Heroes within this distance of Thanos (same level) take part: HUD, boss bar, scene. */
   public static final double PARTICIPANT_RADIUS = 160.0;
   /** He is spawned this far in front of the starting player. */
   public static final double SPAWN_DISTANCE = 12.0;
   /** How far below a flying starter his landing ground is searched. */
   public static final double START_DROP = 64.0;

   // ---- health --------------------------------------------------------------------------------
   public static final float BASE_HEALTH = 3000.0F;
   /** Each hero past the first adds this share of the base health. */
   public static final float HEALTH_PER_EXTRA_HERO = 0.6F;
   /** Heroes counted for the scaling (more heroes still fight, the health stops growing). */
   public static final int MAX_COUNTED_HEROES = 8;
   /** Heroes who join within this window after the start raise the health (fraction kept). */
   public static final int RESCALE_WINDOW_TICKS = 30 * TPS;
   /** One hit takes at most this share of the max health (and never less than {@link #MIN_HIT_CAP}). */
   public static final float HIT_CAP = 0.025F;
   public static final float MIN_HIT_CAP = 40.0F;
   /** All damage within one second takes at most this share of the max health. */
   public static final float SECOND_CAP = 0.025F;
   /** A single hit above this share counts as a strong ability (the brain answers defensively). */
   public static final float THREAT_HIT = 0.02F;
   /** Time Rewind gives back this share of the damage of the last {@link #REWIND_WINDOW} ticks... */
   public static final float REWIND_HEAL_SHARE = 0.5F;
   /** ...but never more than this share of the max health. */
   public static final float REWIND_HEAL_CAP = 0.06F;
   public static final int REWIND_WINDOW = 4 * TPS;
   /** The rewound position is the one from this many ticks ago. */
   public static final int REWIND_BACK = 3 * TPS;

   // ---- movement / ranges ---------------------------------------------------------------------
   public static final double CLOSE_RANGE = 5.5;
   public static final double MID_RANGE = 16.0;
   /** A target this high above the ground (and off it) counts as flying. */
   public static final double AIRBORNE_HEIGHT = 3.5;
   public static final double WALK_SPEED = 0.30;
   /** He picks a new target at least every this many ticks (nearest threat first). */
   public static final int RETARGET_TICKS = 40;
   /** Free stalking between moves, before the brain may choose again. */
   public static final int BREATHER_TICKS = 14;
   /** At least this many ticks between two stone moves (melee may fill the gap). */
   public static final int STONE_GAP_TICKS = 30;
   /** Released from someone's time stop within this many ticks: the brain reacts. */
   public static final int TIME_STOP_REACTION_TICKS = 40;
   /** Incoming projectiles (within 12 blocks, closing in) that make him raise the Space ward. */
   public static final int PROJECTILE_THREAT = 2;
   /** At least this many ticks between two moves that carry him through space (no teleport spam). */
   public static final int TELEPORT_GAP_TICKS = 10 * TPS;
   public static final int TELEPORT_GAP_FINAL_TICKS = 7 * TPS;
   /** Time Rewind needs this share of the max health lost recently... */
   public static final float REWIND_MIN_DAMAGE = 0.03F;
   /** ...or only this much right after a time stop or a strong hit (the answer the rules ask for). */
   public static final float REWIND_MIN_DAMAGE_REACTION = 0.01F;
   /** A target but no move for this long (everything gated, a lost path): the fight recovers. */
   public static final int IDLE_RECOVER_TICKS = 8 * TPS;
   /** FINAL: telegraphs x0.8 (never under {@link #MIN_TELEGRAPH_FINAL}), cooldowns x0.7. */
   public static final float FINAL_TELEGRAPH = 0.8F;
   public static final float FINAL_COOLDOWN = 0.7F;
   public static final int MIN_TELEGRAPH = 12;
   public static final int MIN_TELEGRAPH_FINAL = 10;

   private ThanosRules() {
   }

   /** Max health for {@code heroes} participants (at least one counted). */
   public static float maxHealth(int heroes) {
      int counted = Math.max(1, Math.min(MAX_COUNTED_HEROES, heroes));
      return BASE_HEALTH * (1.0F + HEALTH_PER_EXTRA_HERO * (counted - 1));
   }

   /** Damage of his moves scales mildly with the group: solo 1.0, eight heroes 1.35. */
   public static float damageScale(int heroes) {
      int counted = Math.max(1, Math.min(MAX_COUNTED_HEROES, heroes));
      return 1.0F + 0.05F * (counted - 1);
   }

   /** Per-hit cap in health points. */
   public static float hitCap(float maxHealth) {
      return Math.max(MIN_HIT_CAP, HIT_CAP * maxHealth);
   }

   /** Per-second cap in health points (at least one full hit). */
   public static float secondCap(float maxHealth) {
      return Math.max(hitCap(maxHealth), SECOND_CAP * maxHealth);
   }

   /** Telegraph of a move in a phase. */
   public static int telegraph(int base, ThanosPhase phase) {
      if (phase != ThanosPhase.FINAL) {
         return Math.max(MIN_TELEGRAPH, base);
      }

      return Math.max(MIN_TELEGRAPH_FINAL, Math.round(base * FINAL_TELEGRAPH));
   }

   /** Cooldown of a move in a phase. */
   public static int cooldown(int base, ThanosPhase phase) {
      return phase == ThanosPhase.FINAL ? Math.round(base * FINAL_COOLDOWN) : base;
   }

   /** Seconds shown by the HUD for the ticks left (rounded up, so 0:00 only at the very end). */
   public static int secondsLeft(int ticksLeft) {
      return ticksLeft <= 0 ? 0 : (ticksLeft + TPS - 1) / TPS;
   }

   /** "m:ss" for the HUD. */
   public static String clock(int ticksLeft) {
      int s = secondsLeft(ticksLeft);
      return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
   }

   /** Time Rewind heal for {@code recentDamage} taken in the window. */
   public static float rewindHeal(float recentDamage, float maxHealth) {
      return Math.max(0.0F, Math.min(recentDamage * REWIND_HEAL_SHARE, maxHealth * REWIND_HEAL_CAP));
   }

   /** Ticks between two teleports in a phase. */
   public static int teleportGap(ThanosPhase phase) {
      return phase == ThanosPhase.FINAL ? TELEPORT_GAP_FINAL_TICKS : TELEPORT_GAP_TICKS;
   }
}
