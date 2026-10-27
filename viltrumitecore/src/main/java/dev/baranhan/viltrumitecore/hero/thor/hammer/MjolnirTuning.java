package dev.baranhan.viltrumitecore.hero.thor.hammer;

/**
 * Session A numbers. Every value marked PLACEHOLDER is a temporary,
 * not author-approved number (spec "[ОТКРЫТО]" / "[PLACEHOLDER]"); the
 * approved ones cite the decision number. Ticks are server ticks (20/s),
 * distances blocks, speeds blocks per tick.
 */
public final class MjolnirTuning {
   // --- approved ---
   /** №33: autonomous range of a throw (target search and homing). */
   public static final double THROW_RANGE = 128.0;
   /** №190: first fall about 60 s after the first player join. */
   public static final int FIRST_FALL_DELAY = 60 * 20;
   /** №174: crater radius 4–6 blocks; PLACEHOLDER pick inside the approved range. */
   public static final int CRATER_RADIUS = 5;
   /** №195: the lift attempt lasts 1.5–2 s; PLACEHOLDER pick inside the approved range. */
   public static final int LIFT_TICKS = 35;

   // --- PLACEHOLDER: first fall ---
   /** PLACEHOLDER: "near the chosen player, at a safe distance" (blocks, horizontal). */
   public static final double FALL_SITE_MIN = 18.0;
   public static final double FALL_SITE_MAX = 32.0;
   /** PLACEHOLDER: no player closer than this to the impact point. */
   public static final double FALL_SITE_CLEARANCE = 10.0;
   /** PLACEHOLDER: site candidates per attempt; an attempt every FALL_RETRY ticks while waiting (№192). */
   public static final int FALL_SITE_TRIES = 16;
   public static final int FALL_RETRY = 40;
   /** PLACEHOLDER: "from a great height" (blocks above the site) and the fall speed. */
   public static final double FALL_HEIGHT = 160.0;
   public static final double FALL_SPEED = 2.0;
   /** PLACEHOLDER: "a few seconds" of thunder and electric trail before the impact: the trail starts this high. */
   public static final int FALL_THUNDER_GAP = 30;
   /** PLACEHOLDER: "huge" direct hit, shock wave radius / damage / push. */
   public static final float FALL_DIRECT_DAMAGE = 60.0F;
   public static final double FALL_WAVE_RADIUS = 12.0;
   public static final float FALL_WAVE_DAMAGE = 10.0F;
   public static final double FALL_WAVE_PUSH = 1.6;
   /** PLACEHOLDER: crater bowl depth as a share of the radius; the site may vary this much in height. */
   public static final double CRATER_DEPTH = 0.55;
   public static final int FALL_SITE_FLATNESS = 3;

   // --- PLACEHOLDER: free fall (lost support, released) ---
   public static final double FALL_GRAVITY = 0.08;
   public static final double FREE_FALL_MAX = 2.5;
   /** PLACEHOLDER: below the world bottom by this much the void recovery starts. */
   public static final int VOID_MARGIN = 16;
   /**
    * PLACEHOLDER (№217 open): void recoveries to the last resting point, then as many to the world spawn,
    * before the hammer is parked at the spawn (A-m17: never an endless recovery loop).
    */
   public static final int VOID_RETRIES = 3;
   // --- PLACEHOLDER: throw ---
   public static final double THROW_SPEED = 2.2;
   public static final double THROW_SPEED_CHARGED = 3.2;
   /** PLACEHOLDER: wind-up before the release, max hold (charging) and follow-through (move phases). */
   public static final int THROW_WINDUP = 4;
   public static final int THROW_HOLD_MAX = 40;
   public static final int THROW_FOLLOW = 8;
   /** PLACEHOLDER: MMB held this long (server ticks) makes the throw charged (№98). */
   public static final int CHARGE_TICKS = 12;
   public static final float THROW_DAMAGE = 14.0F;
   public static final float THROW_DAMAGE_CHARGED = 24.0F;
   /** PLACEHOLDER: "an enemy near the aim direction" — cone half-angle cosine at launch. */
   public static final double AIM_CONE_COS = 0.94;
   /** PLACEHOLDER: after a pierce the next target is searched this far from the hammer. */
   public static final double NEXT_TARGET_RADIUS = 24.0;
   /** PLACEHOLDER: homing turn rate (0..1 of the way to the target direction per tick). */
   public static final double HOMING_TURN = 0.35;
   /** PLACEHOLDER: gravity of a ballistic throw (№55). */
   public static final double GRAVITY = 0.05;
   /** PLACEHOLDER: charged throw block breaking (№11): total and per tick, speed kept per block. */
   public static final int CHARGED_BREAK_TOTAL = 64;
   public static final int CHARGED_BREAK_PER_TICK = 8;
   public static final double CHARGED_BREAK_DRAG = 0.97;
   /** PLACEHOLDER: a lost throw without owner times out to a fall after this many ticks. */
   public static final int THROW_MAX_TICKS = 20 * 20;

   // --- PLACEHOLDER: recall ---
   /** PLACEHOLDER: adaptive return speed (№159): min + distance * k, capped. */
   public static final double RETURN_MIN_SPEED = 1.2;
   public static final double RETURN_SPEED_PER_BLOCK = 0.06;
   public static final double RETURN_MAX_SPEED = 6.0;
   /** PLACEHOLDER: simplified far return through unloaded chunks (no physics): share of the distance per tick, capped. */
   public static final double REMOTE_SPEED_SHARE = 0.05;
   public static final double REMOTE_MAX_SPEED = 64.0;
   /** PLACEHOLDER: catch distance to the palm, and the last metres where the handle turns to it (№157). */
   public static final double CATCH_DISTANCE = 1.4;
   public static final double TURN_DISTANCE = 6.0;
   public static final float RETURN_DAMAGE = 14.0F;
   /** PLACEHOLDER: blocks broken per tick on the way back (№12). */
   public static final int RETURN_BREAK_PER_TICK = 12;
   /** PLACEHOLDER: the brief slow-down of the catch (№156), velocity factor once. */
   public static final double CATCH_SLOW = 0.55;
   /** PLACEHOLDER: rip out of a block (№158), upward jerk. */
   public static final double RIP_JERK = 0.8;
   /** PLACEHOLDER: appear point in the Thor's dimension (№205): behind and above him. */
   public static final double TRANSFER_BACK = 24.0;
   public static final double TRANSFER_UP = 14.0;

   // --- PLACEHOLDER: death point impact (№169/№209: stronger than the first fall) ---
   public static final int DEATH_IMPACT_RADIUS = 8;
   public static final float DEATH_IMPACT_DAMAGE = 80.0F;
   public static final double DEATH_WAVE_RADIUS = 18.0;

   // --- PLACEHOLDER: drops ---
   /** PLACEHOLDER: hero change — "falls briefly and heavily nearby" (№207), offset from the body. */
   public static final double DROP_NEAR = 1.2;
   /**
    * Approved by the author (session A review): a Thor who dies with Mjölnir
    * in the hand drops it where he died; after the respawn he recalls it.
    */
   public static final boolean DEATH_IN_HAND_DROPS = true;

   // --- PLACEHOLDER: lift attempt ---
   public static final int LIFT_COOLDOWN = 20;
   /** PLACEHOLDER: the attempt stops when the lifter walks this far away from where it began. */
   public static final double LIFT_MOVE_CANCEL = 1.0;
   public static final double LIFT_REACH = 3.5;
   /** PLACEHOLDER: push of the lifter when the active Thor recalls (№212). */
   public static final double LIFT_RIP_PUSH = 1.4;

   // --- PLACEHOLDER: Y pull (№220 details are open) ---
   public static final double PULL_SPEED = 3.0;
   /** PLACEHOLDER: the pull is always this much faster than the hammer, so Thor catches up. */
   public static final double PULL_CATCH_UP = 1.0;
   /** PLACEHOLDER: closer than this Thor moves with the hammer instead of towards it. */
   public static final double PULL_ATTACH = 2.5;
   public static final int PULL_MAX_TICKS = 100;
   /** PLACEHOLDER: no fall damage this long after the pull ends. */
   public static final int PULL_FALL_GRACE = 60;

   // --- PLACEHOLDER: transformation (№186, №215) ---
   public static final int TRANSFORM_TICKS = 30;
   /** PLACEHOLDER: tick of the scene when the existing hammer is called (lightning, change, hand out, arrival). */
   public static final int TRANSFORM_RECALL_AT = 12;
   /** PLACEHOLDER: "underground" = no sky and at least this many solid blocks above the head. */
   public static final int UNDERGROUND_SOLID = 6;
   /** PLACEHOLDER: deepest column the underground lightning carves (№214). */
   public static final int TRANSFORM_CARVE_MAX = 96;

   // --- PLACEHOLDER: sound ---
   /** PLACEHOLDER: one block-break sound at most this often (№161, merged for quality). */
   public static final int BREAK_SOUND_GAP = 4;
   /** PLACEHOLDER: visual / sound reach of impacts and the catch flash (blocks). */
   public static final double FX_RANGE = 160.0;

   private MjolnirTuning() {
   }
}
