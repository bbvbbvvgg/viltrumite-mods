package dev.baranhan.viltrumitecore.hero.reinhard;

/**
 * Every Reinhard number (spec docs/design/2026-10-11-reinhard-design.md).
 * Pure: no world access. Ticks, 20 t = 1 s. Starting values; tuning only
 * changes constants here.
 */
public final class ReinhardRules {
   // ---- chassis (spec §3.1) ----
   public static final float MAX_HEALTH = 20.0F;
   /** Share of ordinary damage that never lands. */
   public static final float RESIST = 0.999F;
   /** Ordinary damage never takes him below this. */
   public static final float HP_FLOOR = 1.0F;
   public static final float REGEN_PER_SECOND = 1.0F;
   public static final int REGEN_INTERVAL = 20;
   public static final double KNOCKBACK_RESISTANCE = 1.0;
   /** Blocks under the world's bottom at which the air step lifts him out (vanilla void damage starts at 64). */
   public static final int VOID_RESCUE_DEPTH = 8;
   /** Radius of the search for ground when the column above the fall point is empty. */
   public static final int VOID_SEARCH_RADIUS = 64;

   // ---- modes (spec §5) ----
   public static final int MODE_COOLDOWN = 60;

   // ---- Holy Sword (spec §7.2, used in Stage 4) ----
   public static final float CHOP_BASE_DAMAGE = 20.0F;
   /** Reid idle time before the auto-return to the sheath (spec §7.3: 60 s); HUD bar scale. */
   public static final int REID_IDLE_TICKS = 1200;
   /** Spec §7.3: the draw event lasts 6 s; sheathing 1 s (stage 4). */
   public static final int REID_DRAW_TICKS = 120;
   public static final int REID_SHEATHE_TICKS = 20;
   /** Draw timeline (ticks from the press, spec §7.3): cracks and the pillar grow 20-100, "Reid trembles" at 110, the final at REID_DRAW_TICKS. */
   public static final int REID_CRACK_START = 20;
   public static final int REID_CRACK_END = 100;
   public static final int REID_TREMBLE_TICK = 110;
   /** Gravity lift (spec §7.3.3): radius, max rise over the start Y, rise time, cap for overlapping lifts, max speed per tick. */
   public static final int REID_LIFT_RADIUS = 96;
   public static final double REID_LIFT_MAX = 2.5;
   public static final int REID_LIFT_RISE_TICKS = 100;
   public static final double REID_LIFT_CAP = 3.0;
   public static final double REID_LIFT_STEP = 0.1;
   /** New entities in the radius are searched every this many ticks (tracked ones move every tick). */
   public static final int REID_LIFT_SCAN_INTERVAL = 5;
   /** Final wave (spec §7.3.6): no damage, outward push within this radius. */
   public static final double REID_WAVE_RADIUS = 24.0;
   public static final double REID_WAVE_KNOCKBACK = 1.2;
   /** The scene packet reaches players within this distance of the origin; refreshed every REID_SCENE_REFRESH ticks. */
   public static final double REID_SCENE_RANGE = 96.0;
   public static final int REID_SCENE_REFRESH = 20;
   /** Volume of the private far sound every player on the server hears at the final. */
   public static final float REID_FAR_VOLUME = 0.4F;

   // ---- Sheath Strike (spec §7, Holy Sword slot 3, Reid sheathed only) ----
   /** Everything Reinhard may hit within this sphere around his feet. */
   public static final double SHEATH_RADIUS = 6.0;
   public static final float SHEATH_DAMAGE = 20.0F;
   /** Freeze (stun) length on targets that accept external control. */
   public static final int SHEATH_STUN = 60;
   public static final int SHEATH_COOLDOWN = 160;
   /** Action lock and the clip: the sheathed sword swings in an arc, contact on {@link #SHEATH_HIT_TICK}. */
   public static final int SHEATH_TICKS = 12;
   public static final int SHEATH_HIT_TICK = 6;

   // ---- Divine Protection of the Phoenix (spec §4.2) ----
   /** Online ticks before the next revive (10 min). */
   public static final int PHOENIX_COOLDOWN = 12000;
   /** PersistentCooldowns key. */
   public static final String PHOENIX_KEY = "reinhard:phoenix";
   public static final int PHOENIX_MIN_DIST = 6;
   public static final int PHOENIX_MAX_DIST = 10;

   // ---- Protection Acquisition (spec §4.8) ----
   /** Divine hits of one type in a row that make him immune to it (the last one still lands). */
   public static final int ADAPT_HITS = 3;

   // ---- Blessing of the Holy Sword (spec §4.1) ----
   /** Front cone of the parry, horizontal: cos 60° = ±60° around the look. */
   public static final float PARRY_CONE_COS = 0.5F;
   /** Parry pose flag lifetime after a parried hit. */
   public static final int PARRY_POSE_TICKS = 8;

   // ---- Divine Protection of the Cloudy Sky (spec §4.4) ----
   /** Air steps in a row before touching ground, water, lava or a ladder again. */
   public static final int AIR_STEPS = 5;
   /** Vertical velocity of one air step (blocks/tick; ~2.5 blocks from a standstill). */
   public static final double AIR_STEP_VELOCITY = 0.62;
   /** Super jump launch velocity (~22 blocks, HeroSuperJump.apex). */
   public static final float SUPER_JUMP_VELOCITY = 2.2F;

   // ---- Blessing of Wind Reading (spec §4.5) ----
   public static final int WIND_RADIUS = 32;
   /** Behind = horizontal dot(look, to entity) below this. */
   public static final float WIND_BEHIND_COS = 0.0F;
   /** A player who hit him stays "hostile" this long (5 s). */
   public static final int WIND_RECENT_ATTACK_TICKS = 100;
   /** Server scan period. */
   public static final int WIND_SCAN_INTERVAL = 5;

   // ---- Blessing of Mana Absorption (spec §4.7) ----
   /** Non-allied players this close regen their own resource slower. */
   public static final double MANA_RADIUS = 24.0;
   public static final float MANA_REGEN_FACTOR = 0.5F;

   // ---- Restrained and Knight abilities (spec §6, stage 3) ----
   /** Hit-stop (spec §9.3: 2–3 t): the launch lands this many ticks after the contact. */
   public static final int HIT_STOP_TICKS = 3;
   /** Sky Kick: ticks from the toss to the kick contact (used by the clips too). */
   public static final int KICK_TOSS_TICKS = 6;

   // ---- Sky Kick (spec §6.2) ----
   public static final double KICK_REACH = 3.5;
   /** The light toss before the kick (target vertical velocity). */
   public static final double KICK_TOSS_VELOCITY = 0.45;
   /** Launch velocity: the motion packet cap (±3.9 per axis); apex ≈ 61 blocks. */
   public static final double KICK_VELOCITY = 3.9;
   public static final float KICK_DAMAGE = 20.0F;
   public static final int KICK_COOLDOWN = 200;
   /** Action lock and the toss + kick clips: toss 0-6, contact 6, hit-stop 6-9, launch 9, recovery to 16. */
   public static final int KICK_ACTION_TICKS = 16;
   /** The target may drift this far past the reach during the toss and still be kicked. */
   public static final double KICK_CONTACT_SLACK = 2.0;

   // ---- Light Punch (spec §6.1) ----
   public static final float LIGHT_PUNCH_DAMAGE = 4.0F;
   public static final double LIGHT_PUNCH_KNOCKBACK = 0.6;
   public static final double LIGHT_PUNCH_REACH = 3.5;
   /** Action lock (no cooldown): the clip length. */
   public static final int LIGHT_PUNCH_TICKS = 8;
   public static final int LIGHT_PUNCH_HIT_TICK = 3;

   // ---- Step Dash (spec §6.1) ----
   /** Blocks covered by one dash (the velocity accounts for friction after the push). */
   public static final int STEP_DISTANCE = 6;
   /** Ticks the dash velocity is (re)applied. */
   public static final int STEP_TICKS = 4;
   public static final int STEP_COOLDOWN = 20;
   /** Action lock and clip length (the lean). */
   public static final int STEP_ACTION_TICKS = 6;

   // ---- Catch Projectile (spec §6.1) ----
   /** Ticks the palm is open from the press. */
   public static final int CATCH_WINDOW = 10;
   /** Counted from the press, not from the window's end. */
   public static final int CATCH_COOLDOWN = 40;
   /** The closing hand after a catch (the action lock may outlast the window by this). */
   public static final int CATCH_SNAP_TICKS = 6;

   // ---- Fist Combo (spec §6.2) ----
   public static final float[] COMBO_DAMAGE = {10.0F, 10.0F, 16.0F};
   /** Contact tick of each hit (elapsed ticks of its step). */
   public static final int[] COMBO_HIT_TICKS = {3, 3, 5};
   /** Action lock (no cooldown) of each hit: its clip length. */
   public static final int[] COMBO_STEP_TICKS = {8, 8, 12};
   /** Idle ticks after a hit ends within which the next press continues the chain. */
   public static final int COMBO_CHAIN_WINDOW = 20;
   public static final double COMBO_REACH = 3.5;
   /** Not in the spec: push of each hit along the look (starting values). */
   public static final double[] COMBO_KNOCKBACK = {0.4, 0.4, 1.0};
   public static final double COMBO_WAVE_RADIUS = 4.0;
   /** Not in the spec: the third hit's wave (starting value, open to balance). */
   public static final float COMBO_WAVE_DAMAGE = 8.0F;
   public static final double COMBO_WAVE_KNOCKBACK = 0.8;

   // ---- Grab & Throw (spec §6.2) ----
   public static final double GRAB_REACH = 3.5;
   /** A hold this long ends with the throw by itself. */
   public static final int GRAB_MAX_TICKS = 100;
   /** Height of the holding hand above his feet (chest); the target hangs from it. */
   public static final double GRAB_HAND_HEIGHT = 1.35;
   /** Horizontal blocks the throw covers ("beyond the horizon", spec ~120). */
   public static final int THROW_DISTANCE = 120;
   /** Throw speed: the motion packet cap (±3.9 per axis). */
   public static final double THROW_SPEED = 3.9;
   /** The throw goes along the look raised by this angle. */
   public static final double THROW_UP_DEGREES = 12.0;
   /** Ticks the throw velocity is re-applied (GrabThrow.sustainTicks(120, 3.9 · cos 12°, 0.91)). */
   public static final int THROW_SUSTAIN_TICKS = 21;
   public static final float THROW_DAMAGE = 15.0F;
   /** From the throw, or from a forced release. */
   public static final int GRAB_COOLDOWN = 240;
   /** Action lock and the throw clip: hit-stop 0-3, launch on 3, follow-through to 12. */
   public static final int THROW_ACTION_TICKS = 12;

   // Dash Behind (Knight slot 4, spec §6.2): behind the target under the crosshair, a safe spot (SafeSpot).
   /** Target search range along the look. */
   public static final double BEHIND_RANGE = 24.0;
   /** Extra distance the server accepts for the client's target (movement between the two views). */
   public static final double BEHIND_TOLERANCE = 2.0;
   /** Blocks behind the target's feet, against its horizontal look. */
   public static final double BEHIND_OFFSET = 1.5;
   /** Safe-spot search radius around the wanted spot, and its grid step. */
   public static final double BEHIND_SEARCH = 3.0;
   public static final double BEHIND_SEARCH_STEP = 0.5;
   /** The server's own look ray must pass within this distance of the target's box (it moved during the ping). */
   public static final double BEHIND_AIM_TOLERANCE = 1.0;
   /** The client's spot is kept when within this distance of the server's answer. */
   public static final double BEHIND_CLIENT_TOLERANCE = 1.0;
   public static final int BEHIND_COOLDOWN = 60;
   /** Action lock and the clip: vanish lean, land turned. */
   public static final int BEHIND_ACTION_TICKS = 8;

   // Stomp (Knight slot 5, spec §6.2): a ground slam around his feet.
   public static final double STOMP_RADIUS = 8.0;
   /** Only targets within this height of his feet are on the ground plane. */
   public static final double STOMP_HEIGHT = 3.0;
   public static final float STOMP_DAMAGE = 12.0F;
   /** Not in the spec: the outward push of the slam (starting value, open to balance). */
   public static final double STOMP_KNOCKBACK = 0.8;
   public static final int STOMP_COOLDOWN = 160;
   /** Action lock and the clip: leg up, slam on {@link #STOMP_HIT_TICK}, recover. */
   public static final int STOMP_TICKS = 14;
   public static final int STOMP_HIT_TICK = 8;
   /** The ground torn around his feet before the mode's cap (Knight: top layer, radius 4, few blocks thrown). */
   public static final dev.baranhan.viltrumitecore.hero.HeroDebris.Eruption STOMP_ERUPTION =
      new dev.baranhan.viltrumitecore.hero.HeroDebris.Eruption(4.0, 1.0, 0.0, 14, 0.35, 0.7);

   // Reflect Projectile (Knight slot 6, spec §6.2): the Catch window's mechanics with REFLECT.
   /** Ticks the backhand window is open from the press. */
   public static final int REFLECT_WINDOW = 20;
   /** Counted from the press, not from the window's end. */
   public static final int REFLECT_COOLDOWN = 80;
   /** The swat after a reflection (the action lock may outlast the window by this). */
   public static final int REFLECT_SNAP_TICKS = 6;

   private ReinhardRules() {
   }

   /** Ordinary damage after the 99.9 % resist. */
   public static float resist(float raw) {
      if (!(raw > 0.0F)) {
         return 0.0F;
      }

      return raw * (1.0F - RESIST);
   }

   /** HP loss allowed by the floor: never below {@link #HP_FLOOR}; NaN and infinity safe. */
   public static float floor(float health, float amount) {
      if (Float.isNaN(amount) || Float.isNaN(health) || !(amount > 0.0F)) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(amount, health - HP_FLOOR));
   }

   /** Spec §7.1: Reid may be drawn at any time for now (the "worthy" list is §12). */
   public static boolean canDraw() {
      return true;
   }
}
