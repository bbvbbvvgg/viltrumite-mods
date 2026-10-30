package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Thor numbers (spec docs/design/2026-10-27-thor-design.md, "Сводка чисел";
 * timings: docs/design/2026-10-28-thor-rework.md §4).
 * Pure constants only.
 */
public final class ThorRules {
   /** MMB melee branch: any enemy this close (blocks, eye to bounding box). */
   public static final double MELEE_ENEMY_RADIUS = 4.0;

   /**
    * Draw Mjölnir from the belt: reach (intro), grip — the hammer changes place here (main), settle (finish).
    * Audit v3: MMB draws from the belt now, so the draw answers like a strike: 18 ticks, grip at tick 6–9, the
    * settle cancellable (was 14/4/16 for the old automatic draw; 4/2/4 before that).
    * The clips "draw" / "draw_fp" have the same length (ThorAttacks, ThorClipContractTest).
    */
   public static final int DRAW_INTRO = 6;
   public static final int DRAW_MAIN = 3;
   public static final int DRAW_FINISH = 9;
   /** Holster Mjölnir on the belt, same three phases (was 4/2/4). */
   public static final int HOLSTER_INTRO = 14;
   public static final int HOLSTER_MAIN = 4;
   public static final int HOLSTER_FINISH = 14;

   /**
    * Priority tiers of the staged moves: a higher tier interrupts a lower one
    * outside its cancel window. Tuned by the sessions that author the moves.
    */
   public static final int PRIORITY_UTILITY = 1;
   public static final int PRIORITY_ATTACK = 2;
   public static final int PRIORITY_HEAVY = 3;
   public static final int PRIORITY_GRAB = 4;
   public static final int PRIORITY_FATALITY = 5;
   public static final int PRIORITY_ULTIMATE = 6;

   // Refusal and draw / holster sounds: ThorCue.REFUSE / DRAW / HOLSTER (R5).

   private ThorRules() {
   }
}
