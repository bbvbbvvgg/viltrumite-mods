package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Thor numbers (spec docs/design/2026-10-27-thor-design.md, "Сводка чисел").
 * Pure constants only.
 */
public final class ThorRules {
   /** MMB melee branch: any enemy this close (blocks, eye to bounding box). */
   public static final double MELEE_ENEMY_RADIUS = 4.0;

   /** Draw Mjölnir from the belt: reach (intro), grip — the hammer changes place here (main), settle (finish). */
   public static final int DRAW_INTRO = 4;
   public static final int DRAW_MAIN = 2;
   public static final int DRAW_FINISH = 4;
   /** Holster Mjölnir on the belt, same three phases. */
   public static final int HOLSTER_INTRO = 4;
   public static final int HOLSTER_MAIN = 2;
   public static final int HOLSTER_FINISH = 4;

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
