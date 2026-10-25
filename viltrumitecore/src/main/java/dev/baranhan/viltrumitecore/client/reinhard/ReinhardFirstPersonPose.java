package dev.baranhan.viltrumitecore.client.reinhard;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;
import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.under;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import javax.annotation.Nullable;

/** View-adapted shoulder poses, using the same PoseKeys interpolation as Regulus/Iron Man.
 * Rows are ticks, pitch/yaw/roll degrees and camera-space block offsets. Contacts match
 * the authoritative ability timelines; no frame counter or second animation engine.
 */
public final class ReinhardFirstPersonPose {
   private ReinhardFirstPersonPose() {}

   public record Offset(float x, float y, float z, float pitch, float yaw, float roll) {
      public static final Offset NONE = new Offset(0, 0, 0, 0, 0, 0);
      Offset scaled(float w) { return new Offset(x*w, y*w, z*w, pitch*w, yaw*w, roll*w); }
   }

   private static final float[][] JAB = {under(0), key(2.6F,-24,-14,6,.03F,.07F,.10F),
      key(4,-12,4,-3,-.12F,.06F,-.44F), key(5,-10,3,-2,-.10F,.05F,-.38F), under(10)};
   private static final float[][] DASH = {under(0),key(1,16,0,-8,.03F,-.12F,.14F),
      key(4,20,0,-9,.04F,-.14F,.16F),under(6)};
   private static final float[][] HOOK = {under(0),key(2.8F,-36,-28,-14,.14F,.12F,.10F),
      key(4,-14,38,12,-.28F,.05F,-.36F),key(5,-12,42,10,-.24F,.03F,-.30F),under(10)};
   private static final float[][] HAMMER = {under(0),key(4.8F,-72,12,8,.02F,.30F,.08F),
      key(6,28,-8,-6,-.06F,-.14F,-.40F),key(7,24,-10,-6,-.05F,-.12F,-.34F),under(14)};
   private static final float[][] KICK = {under(0),key(2,-34,-4,-6,-.03F,.18F,-.08F),
      key(4,-12,2,-4,0,.07F,-.04F),key(6,22,6,-12,.05F,-.14F,.15F),
      key(9,22,6,-12,.05F,-.14F,.15F),under(16)};
   private static final float[][] GRAB = {under(0),key(2,-28,-5,-3,-.04F,.12F,-.34F),
      key(6,-26,-5,-3,-.04F,.11F,-.32F)};
   private static final float[][] THROW = {key(0,-26,-5,-3,-.04F,.11F,-.32F),
      key(2,-54,18,10,.08F,.22F,.18F),key(3,34,-20,-6,-.12F,-.10F,-.46F),
      key(4,30,-24,-6,-.10F,-.08F,-.40F),under(12)};
   private static final float[][] BEHIND = {under(0),key(2,28,-8,-14,.05F,-.18F,.14F),
      key(4,-20,14,5,-.07F,.06F,-.12F),under(8)};
   private static final float[][] STOMP = {under(0),key(6,-26,0,-12,.03F,.18F,.06F),
      key(8,24,6,8,-.03F,-.20F,-.08F),key(9,18,4,6,-.02F,-.15F,-.06F),under(14)};
   private static final float[][] CATCH_READY = {under(0),key(2,-42,-8,-10,-.07F,.18F,-.14F)};
   private static final float[][] CATCH = {key(0,-42,-8,-10,-.07F,.18F,-.14F),
      key(1,-30,10,4,-.09F,.18F,-.28F),key(4,-42,-8,-10,-.07F,.18F,-.14F)};
   private static final float[][] GUARD = {under(0),key(2,-12,-30,20,.12F,.15F,-.12F)};
   private static final float[][] SWAT = {key(0,-12,-30,20,.12F,.15F,-.12F),
      key(2,-18,48,-8,-.28F,.15F,-.18F),key(6,-12,-30,20,.12F,.15F,-.12F)};
   private static final float[][] SHEATH = {under(0),key(4,-46,-42,-12,.16F,.18F,.08F),
      key(6,-8,48,12,-.30F,.04F,-.18F),key(8,-6,52,12,-.24F,.02F,-.14F),under(12)};
   private static final float[][] WORLD_CUT = {under(0),key(10,-100,8,0,-.08F,.45F,-.12F),
      key(13,-105,8,0,-.08F,.47F,-.12F),key(20,25,8,0,-.08F,-.18F,-.42F),
      key(78,25,8,0,-.08F,-.18F,-.42F),under(80)};
   private static final float[][] RAIN = rain();

   private static float[][] rain() {
      float[][] keys = new float[15][];
      keys[0] = under(0);
      for (int i=0;i<12;i++) {
         float side = (i&1)==0 ? -1 : 1;
         keys[i+1] = key((i+1)*18F/12,-24,side*34,side*9,side*.16F,.08F,-.24F);
      }
      keys[13]=key(19,-14,0,0,0,.03F,-.10F);
      keys[14]=under(20);
      return keys;
   }

   private static Offset sample(float[][] keys, double ticks) {
      if (!Double.isFinite(ticks)) return Offset.NONE;
      float t=(float)Math.max(0,ticks);
      return new Offset(PoseKeys.sample(keys,t,3,0,false), PoseKeys.sample(keys,t,4,0,false),
         PoseKeys.sample(keys,t,5,0,false), PoseKeys.sample(keys,t,0,0,false),
         PoseKeys.sample(keys,t,1,0,false), PoseKeys.sample(keys,t,2,0,false));
   }

   /** Draw/sheathe remains driven by the six-second authoritative Reid phase. */
   public static final Offset REID_PULL = new Offset(-.16F,.22F,-.10F,-52,18,-6);
   public static Offset reid(ReinhardPoses.ReidPhase phase, double ticks) {
      return switch(phase) {
         case DRAWING, SHEATHING -> REID_PULL.scaled(ReinhardPoses.bladeOut(phase,ticks));
         default -> Offset.NONE;
      };
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks) {
      return of(ability,phase,ticks,-1);
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks, int length) {
      if (ability==null || !Double.isFinite(ticks)) return Offset.NONE;
      return switch(ability) {
         case LIGHT_PUNCH -> sample(JAB,ticks);
         case STEP_DASH -> sample(DASH,ticks);
         case FIST_COMBO -> sample(phase<=0 ? JAB : phase==1 ? HOOK : HAMMER,ticks);
         case SKY_KICK -> sample(KICK,ticks);
         case GRAB_THROW -> sample(phase==ReinhardClips.PHASE_SNAP ? THROW : GRAB,ticks);
         case DASH_BEHIND -> sample(BEHIND,ticks);
         case STOMP -> sample(STOMP,ticks);
         case CATCH -> sample(phase==ReinhardClips.PHASE_SNAP && ticks<ReinhardRules.CATCH_SNAP_TICKS ? CATCH : CATCH_READY,ticks).scaled(windowFade(ticks,length));
         case REFLECT -> sample(phase==ReinhardClips.PHASE_SNAP && ticks<ReinhardRules.REFLECT_SNAP_TICKS ? SWAT : GUARD,ticks).scaled(windowFade(ticks,length));
         case SHEATH_STRIKE -> sample(SHEATH,ticks);
         case CUT_RAIN -> sample(RAIN,ticks);
         case WORLD_CUT -> worldCut(ticks,false);
         case WORLD_ERASURE -> worldErasure(ticks,false);
         default -> Offset.NONE;
      };
   }

   /** Counterbalance on the free arm; Knight's back hand remains hidden by the stance policy. */
   public static Offset support(@Nullable ReinhardAbility ability,double ticks,int length) {
      if(ability==null) return Offset.NONE;
      Offset main=of(ability,0,ticks,length);
      return new Offset(-main.x()*.25F,main.y()*.3F,-main.z()*.15F,-main.pitch()*.2F,-main.yaw()*.2F,-main.roll()*.4F);
   }

   private static float windowFade(double ticks,int length) {
      return length>0 ? smooth((length-ticks)/2) : 1;
   }

   public static Offset worldCut(double ticks,boolean physicalLeft) {
      if (!Double.isFinite(ticks) || ticks<=0 || ticks>=ReinhardRules.WORLD_CUT_BLAST) return Offset.NONE;
      Offset right=sample(WORLD_CUT,ticks);
      return physicalLeft ? new Offset(right.x()-.10F,right.y(),right.z(),right.pitch(),-right.yaw(),right.roll()) : right;
   }

   public static Offset worldErasure(double ticks,boolean physicalLeft) {
      if (!Double.isFinite(ticks) || ticks<=0 || ticks>=ReinhardRules.ERASE_LENGTH) return Offset.NONE;
      float raise=smooth(ticks/15),cut=smooth((ticks-ReinhardRules.ERASE_CRACK_END)/10.0);
      return new Offset((physicalLeft ? .10F : -.22F)*raise+.40F*cut,
         .22F*raise-.30F*cut,-.14F*raise-.18F*cut,-20*raise+50*cut,
         (physicalLeft ? -8 : 10)*raise+45*cut,(physicalLeft ? -5 : 5)*raise+12*cut)
         .scaled(windowFade(ticks,ReinhardRules.ERASE_LENGTH));
   }

   private static float smooth(double value) {
      double t=Math.max(0,Math.min(1,value));
      return (float)(t*t*(3-2*t));
   }
}
