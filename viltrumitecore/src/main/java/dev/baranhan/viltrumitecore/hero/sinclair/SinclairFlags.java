package dev.baranhan.viltrumitecore.hero.sinclair;

/**
 * Meaning of Sinclair's public snapshot fields (hero-owned wire layout, append only).
 * <ul>
 *   <li>actionId / elapsed / length: {@link SinclairMove} ordinal and its clock;</li>
 *   <li>cooldowns[0..4]: bolt, assistant, hex, misdirection, ultimate; cooldowns[5]: reload ticks left;</li>
 *   <li>resource: bursts left in the magazine; resource2: assistant entity id or -1;</li>
 *   <li>controlTargetId: the volunteer (capture / window / act) entity id;</li>
 *   <li>actionTarget: the act's focus point (hat, line end, chest) or the hex point;</li>
 *   <li>variant: captured act shape ordinal + 1 (0 = none).</li>
 * </ul>
 */
public final class SinclairFlags {
   public static final int VEIL = 0;
   public static final int ASSIST_ON_STAGE = 1;
   public static final int TRIGGER_HELD = 2;
   /** The captured card floats at the left hand (window). */
   public static final int CARD_HELD = 3;
   public static final int RELOADING = 4;
   /** The top hat is off the head (in the hand or thrown). */
   public static final int HAT_OFF = 5;
   /** The player is a rabbit (also derivable from the mark; kept for the HUD). */
   public static final int HEXED = 6;
   /** The volunteer came from a contract (HUD badge). */
   public static final int FROM_CONTRACT = 7;
   /** The number is the fallback Metamorphosis. */
   public static final int FALLBACK = 8;
   /** The server's cinematic switch is on: clients stage the act cameras ({@code /sinclair cinematic}). */
   public static final int STAGE_CAMERA = 9;

   public static final int COOLDOWN_BOLT = 0;
   public static final int COOLDOWN_ASSIST = 1;
   public static final int COOLDOWN_HEX = 2;
   public static final int COOLDOWN_MIS = 3;
   public static final int COOLDOWN_ULT = 4;
   public static final int COOLDOWN_RELOAD = 5;

   public static final int EXTRA_SINCE_SHOT = 0;
   public static final int EXTRA_WINDOW = 1;
   public static final int EXTRA_SHAPE = 2;
   public static final int EXTRA_VEIL = 3;
   public static final int EXTRA_OVATION = 4;
   public static final int EXTRA_POWER = 5;
   public static final int EXTRA_COLOR = 6;
   public static final int EXTRA_ASSIST_LEFT = 7;
   public static final int EXTRA_SWAPS_LEFT = 8;
   public static final int EXTRA_SHOTS = 9;
   public static final int EXTRA_COUNT = 10;

   private SinclairFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }

   public static int with(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }
}
