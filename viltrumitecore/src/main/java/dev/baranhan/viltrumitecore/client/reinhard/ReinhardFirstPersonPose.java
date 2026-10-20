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

   private static final float[][] JAB = {under(0), key(2,-24,-14,6,.03F,.07F,.10F),
      key(4,-12,4,-3,-.12F,.06F,-.44F), key(5,-10,3,-2,-.10F,.05F,-.38F), under(10)};
   private static final float[][] DASH = {under(0),key(1,16,0,-8,.03F,-.12F,.14F),
      key(4,20,0,-9,.04F,-.14F,.16F),under(6)};
   private static final float[][] HOOK = {under(0),key(2,-36,-28,-14,.14F,.12F,.10F),
      key(4,-14,38,12,-.28F,.05F,-.36F),key(5,-12,42,10,-.24F,.03F,-.30F),under(10)};
   private static final float[][] HAMMER = {under(0),key(3.5F,-72,12,8,.02F,.30F,.08F),
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
      key(66,-105,8,0,-.08F,.47F,-.12F),key(80,25,8,0,-.08F,-.18F,-.42F),
      key(86,25,8,0,-.08F,-.18F,-.42F),under(100)};
   private static final float[][] RAIN = rain();

   private static float[][] rain() {
      float[][] keys = new float[27][];
      keys[0]=under(0);keys[1]=key(4,-45,-24,-6,-.1F,.18F,.04F);
      for(int i=0;i<12;i++) {
         float side=(i&1)==0?-1:1;
         float t=ReinhardRules.CUT_FIRST_TICK+i*ReinhardRules.CUT_INTERVAL;
         keys[2+i*2]=key(t-1.5F,-45,-side*24,-side*6,-side*.1F,.18F,.04F);
         keys[3+i*2]=key(t,12,side*32,side*8,side*.12F,.04F,-.30F);
      }
      keys[26]=under(ReinhardRules.CUT_TICKS);
      return keys;
   }

   private static Offset sample(float[][] keys, double ticks) {
      return sample(keys,ticks,false);
   }
   private static Offset strike(float[][] keys,double ticks) { return sample(keys,ticks,true); }
   private static float channel(float[][] keys,float t,int c,boolean continuous) {
      return continuous?PoseKeys.sampleContinuous(keys,t,c,0,false):PoseKeys.sample(keys,t,c,0,false);
   }
   private static Offset sample(float[][] keys, double ticks,boolean continuous) {
      if (!Double.isFinite(ticks)) return Offset.NONE;
      float t=(float)Math.max(0,ticks);
      return new Offset(channel(keys,t,3,continuous), channel(keys,t,4,continuous),
         channel(keys,t,5,continuous), channel(keys,t,0,continuous),
         channel(keys,t,1,continuous), channel(keys,t,2,continuous));
   }

   /** Draw/sheathe remains driven by the six-second authoritative Reid phase. */
   public static final Offset REID_PULL = new Offset(-.16F,.22F,-.10F,-52,18,-6);
   public static Offset reid(ReinhardPoses.ReidPhase phase, double ticks) {
      float weight = switch(phase) {
         case DRAWING -> ReinhardPoses.bladeOut(phase,ticks)
            * ReinhardPlayback.reidWeight(false,ticks/20.0,ReinhardRules.REID_DRAW_TICKS/20.0);
         case SHEATHING -> ReinhardPoses.bladeOut(phase,ticks)
            * ReinhardPlayback.reidWeight(true,ticks/20.0,ReinhardRules.REID_SHEATHE_TICKS/20.0);
         default -> 0;
      };
      return weight > 0 ? REID_PULL.scaled(weight) : Offset.NONE;
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks) {
      return of(ability,phase,ticks,-1);
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks, int length) {
      if (ability==null || !Double.isFinite(ticks)) return Offset.NONE;
      return switch(ability) {
         case LIGHT_PUNCH -> strike(JAB,ticks);
         case STEP_DASH -> sample(DASH,ticks);
         case FIST_COMBO -> strike(phase<=0 ? JAB : phase==1 ? HOOK : HAMMER,ticks);
         case SKY_KICK -> sample(KICK,ticks);
         case GRAB_THROW -> sample(phase==ReinhardClips.PHASE_SNAP ? THROW : GRAB,ticks);
         case DASH_BEHIND -> sample(BEHIND,ticks);
         case STOMP -> sample(STOMP,ticks);
         case CATCH -> sample(phase==ReinhardClips.PHASE_SNAP && ticks<ReinhardRules.CATCH_SNAP_TICKS ? CATCH : CATCH_READY,ticks).scaled(windowFade(ticks,length));
         case REFLECT -> sample(phase==ReinhardClips.PHASE_SNAP && ticks<ReinhardRules.REFLECT_SNAP_TICKS ? SWAT : GUARD,ticks).scaled(windowFade(ticks,length));
         case SHEATH_STRIKE -> strike(SHEATH,ticks);
         case CUT_RAIN -> strike(RAIN,ticks);
         case WORLD_CUT -> worldCut(ticks,false);
         case DIAGONAL_WORLD_CUT -> diagonal(ticks,false);
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
      if (!Double.isFinite(ticks) || ticks<=0 || ticks>=ReinhardRules.WORLD_CUT_BLAST+20) return Offset.NONE;
      Offset right=sample(WORLD_CUT,ticks);
      return physicalLeft ? new Offset(right.x()-.10F,right.y(),right.z(),right.pitch(),-right.yaw(),right.roll()) : right;
   }

   public static Offset diagonal(double ticks,boolean physicalLeft) {
      Offset o=worldCut(ticks,physicalLeft);
      float w=smooth(ticks/16)*(1-smooth((ticks-86)/14));
      float sweep=smooth((ticks-66)/14);
      return new Offset(o.x()+(.12F-.24F*sweep)*w,o.y(),o.z(),o.pitch(),o.yaw()+(-24+48*sweep)*w,o.roll()+(-22+44*sweep)*w);
   }
   public static Offset rain(double ticks,boolean physicalLeft) {
      Offset o=strike(RAIN,ticks);
      return physicalLeft ? new Offset(o.x()-.1F,o.y(),o.z(),o.pitch(),-o.yaw(),o.roll()) : o;
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
