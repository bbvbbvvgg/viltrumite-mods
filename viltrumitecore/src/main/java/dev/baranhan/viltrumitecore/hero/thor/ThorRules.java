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

   /** Refusal feedback (vanilla dispenser click, the project's refusal sound). */
   public static final float REFUSE_VOLUME = 0.4F;
   public static final float REFUSE_PITCH = 1.6F;
   /** Draw / holster sounds (vanilla armour equip sounds, no imported audio). */
   public static final float DRAW_VOLUME = 0.8F;
   public static final float DRAW_PITCH = 0.8F;
   public static final float HOLSTER_VOLUME = 0.7F;
   public static final float HOLSTER_PITCH = 0.9F;

   private ThorRules() {
   }
}
