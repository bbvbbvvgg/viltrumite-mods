package dev.baranhan.viltrumitecore.hero.ironman.legion;

/**
 * Iron Legion numbers (docs/design/2026-10-31-ironman-legion-design.md). Ticks
 * unless stated; distances in blocks. Pure: unit tests read them directly.
 */
public final class LegionRules {
   /** Suits per owner. */
   public static final int MAX_SUITS = 3;
   /** Call cooldown that starts when the last suit has left (extraCooldowns[4]). */
   public static final int CALL_COOLDOWN = 200;
   /** Sky signal (glints and a far engine roar) before the suits break through the clouds. */
   public static final int SIGNAL_TICKS = 30;
   /** Arrival flight of the first suit; the others are staggered by {@link #ARRIVAL_STAGGER}. */
   public static final int ARRIVAL_TICKS = 56;
   public static final int ARRIVAL_STAGGER = 9;
   /** Height of the arrival start above the owner. */
   public static final double ARRIVAL_HEIGHT = 46.0;
   /** Horizontal distance of the arrival start from the owner. */
   public static final double ARRIVAL_SIDE = 34.0;
   /** Feet-first brake over the formation slot. */
   public static final int BRAKE_TICKS = 14;
   /** The per-mark ready move before GUARD. */
   public static final int READY_TICKS = 26;
   /** Engines spool down, the suit climbs away and leaves. */
   public static final int LEAVE_TICKS = 50;
   /** Crash: engines fail, the suit falls and breaks. */
   public static final int CRASH_TICKS = 44;

   /** Suit brain period (staggered by slot) and coordinator period (staggered by owner). */
   public static final int BRAIN_PERIOD = 2;
   public static final int SQUAD_PERIOD = 10;

   /** GUARD interception radius around Tony; FOLLOW only answers threats inside the smaller one. */
   public static final double GUARD_RADIUS = 16.0;
   public static final double FOLLOW_RADIUS = 10.0;
   /** HOLD: radius around the held point. */
   public static final double HOLD_RADIUS = 10.0;
   /** Threat board scan radius around Tony (and around a held point). */
   public static final double SCAN_RADIUS = 28.0;
   /** Crosshair pick range for ATTACK and HOLD orders. */
   public static final double ORDER_REACH = 64.0;
   /** How long an attacker of Tony or of a suit stays on the board. */
   public static final int THREAT_MEMORY = 200;
   /** An attack on Tony this recent makes the nearest suit break off and defend him. */
   public static final int INTERRUPT_WINDOW = 40;
   /** A threat this close to Tony counts as "on Tony". */
   public static final double INTERRUPT_RADIUS = 10.0;

   /** Below this durability fraction a suit fights from range; below the second it goes home. */
   public static final float RETREAT_FRACTION = 0.30F;
   public static final float RETURN_FRACTION = 0.12F;

   /** Fallback: far behind Tony (portal-free chase, elytra) for this long → a short re-entry jump. */
   public static final double TELEPORT_DISTANCE = 72.0;
   public static final int TELEPORT_COOLDOWN = 200;
   /** No progress toward the goal for this long → climb and change side. */
   public static final int STUCK_TICKS = 60;
   public static final double STUCK_PROGRESS = 1.0;

   /** Suit repulsor: weaker than Tony's own (3.0), several suits fire. */
   public static final float REPULSOR_DAMAGE = 2.5F;
   public static final float REPULSOR_POWER = 0.45F;
   public static final double REPULSOR_KNOCKBACK = 0.35;
   /** Missile damage factor of a Legion volley (Tony's missiles are 1.0). */
   public static final float MISSILE_FACTOR = 0.7F;
   /** Heavy weapons are not used when a normal shot already finishes the target (×this margin). */
   public static final float OVERKILL_MARGIN = 1.5F;
   /** No splash weapon while Tony or another protected ally stands this close to the target. */
   public static final double SPLASH_SAFE_RADIUS = 4.0;
   /** Planned damage per target is capped at its effective health × this (no five-suit pile on a zombie). */
   public static final float FOCUS_CAP = 1.6F;

   /** Projectile dodge: look-ahead and the miss distance that triggers a side step. */
   public static final int DODGE_HORIZON = 10;
   public static final double DODGE_RADIUS = 1.6;
   public static final int DODGE_COOLDOWN = 30;
   public static final double DODGE_SPEED = 0.9;

   /** Durability lost per point of incoming damage is MarkSpec.plating; armor attribute of the shell. */
   public static final double SHELL_ARMOR = 10.0;

   private LegionRules() {
   }

   /** Arrival flight length of the suit in slot {@code index}. */
   public static int arrivalTicks(int index) {
      return ARRIVAL_TICKS + ARRIVAL_STAGGER * Math.max(0, index);
   }

   /** The brain of slot {@code slot} runs on this tick (spreads three suits over the period). */
   public static boolean brainDue(int tickCount, int slot) {
      return Math.floorMod(tickCount + slot, BRAIN_PERIOD) == 0;
   }

   /** The coordinator of an owner runs on this game tick (owners spread over the period). */
   public static boolean squadDue(long gameTime, int ownerHash) {
      return Math.floorMod(gameTime + ownerHash, SQUAD_PERIOD) == 0;
   }

   /** Durability fraction in [0, 1]. */
   public static float fraction(float durability, float max) {
      return max <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, durability / max));
   }
}
