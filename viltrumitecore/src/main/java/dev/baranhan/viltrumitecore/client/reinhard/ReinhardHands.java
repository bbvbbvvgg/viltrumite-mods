package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import javax.annotation.Nullable;

/**
 * REINHARD OVERHAUL 3b item 2: ONE owner of Reinhard's support (left) hand per frame, shared by
 * the third-person grip solve and the first-person rig so both views agree.
 * <ul>
 * <li>the SHEATH owns it only while Reid is in the sheath or travelling through it (draw, sheathe);
 * it never claims the hand while Reid is drawn, the sheath is empty then;</li>
 * <li>the HILT (pommel) owns it while the drawn sword is held in two hands;</li>
 * <li>otherwise the hand is FREE and its own animation clip owns it: the sprint pump, a one-handed
 * stroke, Lethal Chop, an off-hand swing or the Blessing's defense reaction.</li>
 * </ul>
 * A release fades the hilt (or the sheath) out and back in; the released hand never jumps to the
 * other weapon part, so no two systems drive the same arm towards different targets.
 */
final class ReinhardHands {
   private ReinhardHands() {}

   /** Weights (0..1) of the two weapon owners of the support hand; the rest is the clip's. */
   record Support(float sheath, float hilt) {}

   /** Actions that wield Reid in BOTH hands: the support hand stays on the hilt through them. */
   static boolean twoHanded(@Nullable ReinhardAbility action) {
      return action==ReinhardAbility.CUT_RAIN || action==ReinhardAbility.WORLD_CUT
         || action==ReinhardAbility.DIAGONAL_WORLD_CUT || action==ReinhardAbility.WORLD_ERASURE;
   }

   /** The support hand during {@code action}: drawn strokes are one-handed, and any other action
    * whose clip animates the left arm (a left strike) takes the hand for its own motion. Reid's
    * draw/sheathe and Sheath Strike own their grips themselves. 0 held .. 1 free. */
   static float actionRelease(@Nullable ReinhardAbility action,boolean clipMovesLeftArm,double ticks,int length) {
      if(action==null || action==ReinhardAbility.REID || action==ReinhardAbility.SHEATH_STRIKE || twoHanded(action)) return 0;
      return action==ReinhardAbility.REID_STRIKE || clipMovesLeftArm ? ReinhardSword.strikeRelease(ticks,length) : 0;
   }

   /** The strongest of the claims on the free hand this frame (each 0..1). */
   static float release(float... claims) {
      float r=0;
      for(float c:claims) if(c>r) r=c;
      return Math.min(1,r);
   }

   /** Who holds the support hand: {@code holyWeight} is the Holy Sword stance weight. */
   static Support support(ReinhardPoses.ReidPhase phase,double reidTicks,float holyWeight,float release) {
      float held=1-Math.max(0,Math.min(1,release));
      float sheath=switch(phase) {
         case SHEATHED -> held;
         case DRAWN -> 0;
         // The draw and the sheathe are the sheath's own motion: the hand guides the hilt in it.
         default -> 1;
      };
      return new Support(holyWeight*sheath,holyWeight*ReinhardSword.twoHandWeight(phase,reidTicks)*held);
   }
}
