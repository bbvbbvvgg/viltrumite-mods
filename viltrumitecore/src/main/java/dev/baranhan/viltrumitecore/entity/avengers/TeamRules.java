package dev.baranhan.viltrumitecore.entity.avengers;

/**
 * Numbers and pure decisions of the three joint moves of the team (rework §15), driven by {@link TeamMoves}:
 * the vault (Natasha throws Clint up, he shoots from the air), the charged unibeam (Thor's lightning into Iron
 * Man's chest, a stronger beam) and the shield strike (Thor asks Steve to raise the shield and hammers it: a
 * shockwave). Times are ticks, distances blocks. Unit-tested in TeamRulesTest.
 */
public final class TeamRules {
   public static final int VAULT = 0;
   public static final int BEAM = 1;
   public static final int SHIELD = 2;
   public static final int MOVES = 3;
   /** Cooldown of each move after it played out / after it was cancelled, and its first chance in the fight. */
   static final int[] COOLDOWN = {600, 800, 900};
   static final int[] CANCEL_COOLDOWN = {200, 300, 400};
   static final int[] FIRST = {200, 300, 140};
   /** No two joint moves back to back. */
   public static final int GAP = 160;
   /** How often the coordinator looks for a chance. */
   public static final int CHECK_INTERVAL = 10;

   // --- 15.1 the vault: Natasha + Clint ---
   public static final double VAULT_PAIR_RANGE = 10.0;
   public static final double VAULT_HEADROOM = 9.0;
   public static final double VAULT_ENEMY_RANGE = 20.0;
   /** Clint's run-up to Natasha's cupped hands; longer means the path is blocked: cancel. */
   public static final int VAULT_RUN_TICKS = 28;
   public static final double VAULT_CONTACT = 1.4;
   /** Launch speed (up): about 7 blocks with mob gravity and drag, see {@link #apex}. */
   public static final double VAULT_LAUNCH = 1.1;
   public static final int VAULT_RISE_MAX = 20;
   /** Clint hangs at the top for 2.5 s, sinking slowly, and looses four arrows. */
   public static final int VAULT_HANG = 50;
   public static final double VAULT_HANG_SINK = 0.025;
   static final int[] VAULT_SHOTS = {10, 22, 34, 46};
   /** Ticks from the in-air draw (TEAM_2 clip start) to the loose; the draw of the first shot must fall inside the hang (stageT >= 1). */
   public static final int VAULT_AIR_RELEASE = 8;
   public static final double VAULT_SHOT_RANGE = 30.0;
   public static final float VAULT_ARROW_DAMAGE = 7.0F;
   public static final float VAULT_EXPLOSIVE_DAMAGE = 10.0F;
   public static final int VAULT_FALL_MAX = 80;
   /** Natasha's heave clip (team_2) and when she is free again. */
   public static final int VAULT_HEAVE_TICKS = 20;

   // --- 15.2 the charged unibeam: Thor + Iron Man ---
   public static final double BEAM_PAIR_MIN = 3.0;
   public static final double BEAM_PAIR_MAX = 18.0;
   public static final double BEAM_TARGET_MIN = 5.0;
   public static final double BEAM_TARGET_MAX = 24.0;
   /** Iron Man takes his place above Thor (between him and the target). */
   public static final int BEAM_POSITION_TICKS = 30;
   public static final double BEAM_SPOT_OUT = 3.5;
   public static final double BEAM_SPOT_UP = 3.5;
   /** Thor: Mjolnir up, the sky bolt hits it at BEAM_SKY_BOLT, the arc into Tony's chest from BEAM_ARC_START. */
   public static final int BEAM_CHARGE_TICKS = 36;
   public static final int BEAM_SKY_BOLT = 10;
   public static final int BEAM_ARC_START = 14;
   public static final int BEAM_FIRE_TICKS = 30;
   public static final double BEAM_RANGE = 32.0;
   public static final double BEAM_WIDTH = 1.4;
   /** Damage of one beam pulse (every 2 ticks, through the team budget); the plain unibeam pulses 3.5. */
   public static final float BEAM_PULSE_DAMAGE = 6.0F;
   public static final int BEAM_STUN = 20;

   // --- 15.3 the shield strike: Thor + Steve ---
   public static final double SHIELD_CALL_RANGE = 16.0;
   public static final double SHIELD_ENEMY_RANGE = 9.0;
   /** Thor's place in front of Steve. */
   public static final double SHIELD_SPOT = 3.0;
   public static final int SHIELD_APPROACH_TICKS = 50;
   /** Steve has 4 s to raise the shield toward Thor. */
   public static final int SHIELD_WINDOW = 80;
   public static final double SHIELD_MAX_DISTANCE = 5.0;
   /** Steve walked off: the move is cancelled. */
   public static final double SHIELD_LEAVE_DISTANCE = 9.0;
   /** Tick of Thor's strike clip (team_3) at which Mjolnir meets the shield. */
   public static final int SHIELD_IMPACT = 7;
   public static final int SHIELD_STRIKE_TICKS = 24;
   public static final double SHOCK_RADIUS = 9.0;
   public static final float SHOCK_DAMAGE = 14.0F;
   public static final int SHOCK_STUN = 50;
   public static final int SHOCK_MAX_TARGETS = 10;
   /** Kinetic charge the lightning leaves in the shield. */
   public static final int SHOCK_KINETIC = 30;

   private TeamRules() {
   }

   /** A joint move is only worth it on a crowd or a tough enemy. */
   public static boolean worthIt(int enemies, float toughestMaxHealth) {
      return enemies >= 2 || toughestMaxHealth >= 40.0F;
   }

   /**
    * Server check of Steve's answer to Thor's call: the shield is raised (guard on, shield in the hand), Thor is
    * in the frontal guard arc and close, and the guard went up after the call — a guard that was already held
    * before Thor spoke does not count, so the strike never fires by accident.
    */
   public static boolean blockCounts(boolean guarding, boolean shieldInHand, boolean frontal, int guardTicks, int ticksSinceCall, double distance) {
      return guarding && shieldInHand && frontal && guardTicks <= ticksSinceCall && distance <= SHIELD_MAX_DISTANCE;
   }

   /** Shockwave damage at {@code distance} from Steve: full at the shield, half at the edge, 0 outside. */
   public static float shockDamage(double distance) {
      if (distance > SHOCK_RADIUS) {
         return 0.0F;
      }
      return (float)(SHOCK_DAMAGE * (1.0 - 0.5 * Math.max(0.0, distance) / SHOCK_RADIUS));
   }

   /**
    * The move to try now: none while one runs or during the gap, otherwise the first ready one whose cooldown is
    * over, starting at {@code rotation} so every move gets its turn.
    */
   public static int pick(int[] cooldowns, int gap, boolean running, int rotation, boolean[] ready) {
      if (running || gap > 0) {
         return -1;
      }
      for (int i = 0; i < MOVES; i++) {
         int m = Math.floorMod(rotation + i, MOVES);
         if (cooldowns[m] <= 0 && ready[m]) {
            return m;
         }
      }
      return -1;
   }

   /** Height a mob launched straight up at {@code v} blocks/tick rises (vanilla: -0.08 gravity, 0.98 drag a tick). */
   public static double apex(double v) {
      double y = 0;
      for (int i = 0; i < 200 && v > 0; i++) {
         y += v;
         v = (v - 0.08) * 0.98;
      }
      return y;
   }

   /** Ticks to the apex of that launch. */
   public static int riseTicks(double v) {
      int n = 0;
      while (v > 0 && n < 200) {
         v = (v - 0.08) * 0.98;
         n++;
      }
      return n;
   }
}
