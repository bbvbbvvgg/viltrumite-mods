package dev.baranhan.viltrumitecore.hero.reinhard.melee;

/**
 * Every ordinary strike of Reinhard as data: timeline length, swing path, damage,
 * knockback and feel. The action phase on the wire is the move index, so the client
 * picks the clip, first-person keys and trail from the same row. Contacts here match
 * the authored clips in {@code tools/assets/make_reinhard_clips.py}.
 */
public final class MeleeMoves {
   private MeleeMoves() {
   }

   /**
    * @param ticks     timeline length (the lock of this strike)
    * @param chainFrom a next press is buffered from this tick on
    * @param hitStop   hit-stop ticks on a landed hit (0 = none)
    * @param heavy     finisher feel: bigger flash, heavier sound, small ground shock
    */
   public record Move(String clip, int ticks, int chainFrom, MeleeArc.Swing swing, float damage, double knockback, int hitStop,
      boolean heavy) {
   }

   // Restrained fists (LMB): right jab, left jab, right hook. Vanilla held-item damage on the first target.
   public static final Move[] FIST = {
      new Move("light_punch", 10, 4, new MeleeArc.Swing(4, -2, -2, -1, 3.9, 10, 4, 4, 1), 4.0F, .45, 0, false),
      new Move("light_punch_left", 10, 4, new MeleeArc.Swing(-4, -2, 2, -1, 3.9, 10, 4, 4, 1), 4.0F, .45, 0, false),
      new Move("light_hook", 12, 5, new MeleeArc.Swing(48, 4, -36, -4, 3.7, 10, 4, 6, 2), 5.0F, .7, 1, false)};

   // Reid drawn (LMB): four-stroke chain + crouch sweep. Index = action phase.
   public static final int SLASH_DOWN = 0;
   public static final int SLASH_RISE = 1;
   public static final int SLASH_SWEEP = 2;
   public static final int SLASH_FINISH = 3;
   public static final int SLASH_LOW = 4;
   public static final Move[] REID = {
      new Move("reid_slash_down", 13, 5, new MeleeArc.Swing(58, 42, -52, -34, 4.9, 9, 4, 6, 4), 9.0F, .35, 1, false),
      new Move("reid_slash_rise", 13, 5, new MeleeArc.Swing(-54, -36, 56, 40, 4.9, 9, 4, 6, 4), 9.0F, .35, 1, false),
      new Move("reid_slash_sweep", 15, 6, new MeleeArc.Swing(84, 4, -84, -2, 5.1, 9, 5, 8, 6), 10.0F, .5, 1, false),
      new Move("reid_slash_finish", 22, 12, new MeleeArc.Swing(0, 72, 0, -48, 5.4, 12, 8, 10, 4), 16.0F, .85, 3, true),
      new Move("reid_slash_low", 16, 7, new MeleeArc.Swing(-76, -24, 76, -30, 4.9, 9, 5, 8, 6), 12.0F, .45, 2, false)};

   // Knight Fist Combo (slot 1): jab, hook, hammer; contact = ReinhardRules.COMBO_HIT_TICKS.
   public static final MeleeArc.Swing[] KNIGHT = {
      new MeleeArc.Swing(4, -2, -2, -1, 4.0, 10, 4, 4, 1),
      new MeleeArc.Swing(46, 4, -34, -4, 4.0, 10, 4, 4, 2),
      new MeleeArc.Swing(0, 40, 0, -30, 4.0, 12, 6, 6, 1)};

   /** Ticks without a strike after which the chain starts over. */
   public static final int CHAIN_WINDOW = 14;

   /** Pure: the next index of a chain of {@code length} after {@code last} (−1 none) and {@code idle} free ticks. */
   public static int next(int last, int idle, int length) {
      return last >= 0 && last < length - 1 && idle <= CHAIN_WINDOW ? last + 1 : 0;
   }

   /** Pure: the Reid move of a press — crouch forces the low sweep and does not advance the chain. */
   public static int reidMove(int last, int idle, boolean crouching) {
      return crouching ? SLASH_LOW : next(last == SLASH_LOW ? -1 : last, idle, SLASH_LOW);
   }

   public static Move fist(int phase) {
      return FIST[Math.floorMod(phase, FIST.length)];
   }

   public static Move reid(int phase) {
      return REID[Math.floorMod(phase, REID.length)];
   }
}
