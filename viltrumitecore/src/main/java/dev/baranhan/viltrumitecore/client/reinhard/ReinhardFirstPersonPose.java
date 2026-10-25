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
   /**
    * Drawn Reid in first person: the hand low right, the blade on a rising diagonal across
    * the view towards the crosshair like the third-person guard (guard at NDC ≈(.67,-.73),
    * tip ≈(.21,.52)), never a vertical bar over the screen. Applied while Reid is in hand.
    */
   public static final Offset DRAWN_GUARD = new Offset(.20F,.20F,-.10F,-25,0,-45);
   public static Offset drawnGuard(ReinhardPoses.ReidPhase phase,double ticks) {
      float w=ReinhardSword.handWeight(phase,ticks);
      return w>0.001F?DRAWN_GUARD.scaled(Math.min(1,w)):Offset.NONE;
   }

   /** Absolute shoulder keys {pitch, yaw, roll, x, y, z} of the one-handed drawn strokes,
    * fitted (FirstPersonBladeProbeTest fit:) so the blade crosses the view along the same
    * MeleeArc path the server tests: wind-up at the arc start, mid at its centre, end at its end. */
   private static final float[] DOWN_WIND={-10,-15.6F,-47,.302F,.381F,-.130F},DOWN_MID={-5.3F,-15.9F,1.9F,.031F,.073F,-.103F},
      DOWN_END={20.3F,-11.3F,32.5F,-.203F,-.192F,.019F},RISE_END={-10,-18,-51.9F,.245F,.355F,-.177F},
      SWEEP_MID={-11.7F,-28.3F,7.5F,-.013F,.084F,-.206F},
      SWEEP_WIND={-7.2F,15,-118.8F,.5F,.5F,-.108F},SWEEP_END={36.9F,-12.7F,24.2F,-.498F,-.014F,.067F},
      // Between the cocked wind-up and the middle the arm turns about the shoulder's vertical,
      // so the blade comes round level through the right of the view instead of over the top.
      SWEEP_IN=turned(SWEEP_MID,-60),FINISH_WIND={10,-35.3F,-30.9F,-.153F,.5F,-.237F},
      FINISH_MID={-10.6F,-29.2F,-.8F,-.038F,.175F,-.294F},FINISH_END={-12.2F,-39.4F,50,.006F,-.031F,-.134F},
      LOW_START={32.5F,-12.5F,25,-.409F,-.178F,.052F},LOW_MID={-20,-40.9F,18.8F,-.019F,-.056F,-.169F},
      LOW_END={-26.9F,-80,38,.197F,-.014F,-.181F};
   /** Key {@code k} with the arm turned {@code degrees} about the view vertical through the shoulder. */
   static float[] turned(float[] k,float degrees) {
      org.joml.Quaternionf q=new org.joml.Quaternionf().rotateY((float)Math.toRadians(degrees))
         .rotateX((float)Math.toRadians(k[0])).rotateY((float)Math.toRadians(k[1])).rotateZ((float)Math.toRadians(k[2]));
      org.joml.Vector3f e=q.getEulerAnglesXYZ(new org.joml.Vector3f());
      return new float[]{(float)Math.toDegrees(e.x),(float)Math.toDegrees(e.y),(float)Math.toDegrees(e.z),k[3],k[4],k[5]};
   }
   private static float[] at(float t,float[] k) { return key(t,k[0],k[1],k[2],k[3],k[4],k[5]); }
   private static float[] guard(float t) {
      Offset g=DRAWN_GUARD;
      return key(t,g.pitch(),g.yaw(),g.roll(),g.x(),g.y(),g.z());
   }
   /** Index = MeleeMoves.REID move; key ticks follow each move's contact window. */
   private static final float[][][] REID_STRIKES = {
      densify(guard(0),at(3,DOWN_WIND),at(4.5F,DOWN_MID),at(6,DOWN_END),at(8,DOWN_END),guard(13)),
      densify(guard(0),at(3,DOWN_END),at(4.5F,DOWN_MID),at(6,RISE_END),at(8,RISE_END),guard(13)),
      densify(guard(0),at(4,SWEEP_WIND),at(5,SWEEP_IN),at(6,SWEEP_MID),at(8,SWEEP_END),at(10,SWEEP_END),guard(15)),
      densify(guard(0),at(4.5F,FINISH_WIND),at(7,FINISH_WIND),at(8.5F,FINISH_MID),at(10,FINISH_END),at(14,FINISH_END),guard(22)),
      densify(guard(0),at(4,LOW_START),at(6,LOW_MID),at(8,LOW_END),at(10,LOW_END),guard(16))};

   /** Rows every {@value #DENSE_DEGREES}° of shortest-arc turn between keys: the per-channel
    * monotone sampling then follows the arm's real arc instead of an Euler-angle detour
    * (a wide sweep would otherwise bulge up through the top of the view). */
   static float[][] densify(float[]... keys) {
      java.util.List<float[]> out=new java.util.ArrayList<>();
      org.joml.Quaternionf qa=new org.joml.Quaternionf(),qb=new org.joml.Quaternionf(),q=new org.joml.Quaternionf();
      org.joml.Vector3f e=new org.joml.Vector3f();
      for(int i=0;i<keys.length;i++) {
         if(i>0) {
            float[] a=keys[i-1],b=keys[i];
            euler(a,qa);euler(b,qb);
            double turn=Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qa.dot(qb)))));
            int n=(int)Math.ceil(turn/DENSE_DEGREES);
            for(int j=1;j<n;j++) {
               float f=j/(float)n;
               q.set(qa).slerp(qb,f).getEulerAnglesXYZ(e);
               float[] row=new float[7];
               row[0]=a[0]+(b[0]-a[0])*f;
               float[] angles={(float)Math.toDegrees(e.x),(float)Math.toDegrees(e.y),(float)Math.toDegrees(e.z)};
               for(int c=0;c<3;c++) {
                  float near=a[c+1]+(b[c+1]-a[c+1])*f;
                  row[c+1]=angles[c]+360*Math.round((near-angles[c])/360);
                  row[c+4]=a[c+4]+(b[c+4]-a[c+4])*f;
               }
               out.add(row);
            }
         }
         out.add(keys[i]);
      }
      return out.toArray(new float[0][]);
   }
   static final float DENSE_DEGREES=25;
   private static void euler(float[] row,org.joml.Quaternionf out) {
      out.identity().rotateX((float)Math.toRadians(row[1])).rotateY((float)Math.toRadians(row[2])).rotateZ((float)Math.toRadians(row[3]));
   }
   /** The drawn stroke of Reid move {@code phase}: absolute (it starts and ends at the guard). */
   public static Offset reidStrike(int phase,double ticks) {
      return strike(REID_STRIKES[Math.floorMod(phase,REID_STRIKES.length)],ticks);
   }
   /** The free left arm's small counter-motion to a drawn stroke (zero at the guard). */
   public static Offset reidSupport(int phase,double ticks) {
      Offset s=reidStrike(phase,ticks),g=DRAWN_GUARD;
      return counter(new Offset(s.x()-g.x(),s.y()-g.y(),s.z()-g.z(),s.pitch()-g.pitch(),s.yaw()-g.yaw(),s.roll()-g.roll()));
   }
   /** Sum of two shoulder offsets (a pose on top of the drawn guard). */
   public static Offset plus(Offset a,Offset b) {
      if(b==Offset.NONE) return a;
      if(a==Offset.NONE) return b;
      return new Offset(a.x()+b.x(),a.y()+b.y(),a.z()+b.z(),a.pitch()+b.pitch(),a.yaw()+b.yaw(),a.roll()+b.roll());
   }
   /** A short dip of the sword arm on an air step: {@code seconds} since the step, -1 = none. */
   public static Offset airStep(double seconds) {
      if(!(seconds>=0) || seconds>=AIR_STEP_SECONDS) return Offset.NONE;
      float w=(float)Math.sin(Math.PI*seconds/AIR_STEP_SECONDS);
      return new Offset(0,-.06F*w,.03F*w,7*w,0,-3*w);
   }
   static final double AIR_STEP_SECONDS=.3;
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
         case REID_STRIKE -> reidStrike(phase,ticks);
         case LIGHT_PUNCH -> strike(phase==2 ? HOOK : JAB,ticks);
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
         case WORLD_CUT -> worldCut(ticks,false,phase);
         case DIAGONAL_WORLD_CUT -> diagonal(ticks,false);
         case WORLD_ERASURE -> worldErasure(ticks,false);
         default -> Offset.NONE;
      };
   }

   /** Counterbalance on the free arm; Knight's back hand remains hidden by the stance policy. */
   public static Offset support(@Nullable ReinhardAbility ability,double ticks,int length) {
      return support(ability,0,ticks,length);
   }
   /** Counterbalance of the arm that does not strike in move {@code phase} of {@code ability}. */
   public static Offset support(@Nullable ReinhardAbility ability,int phase,double ticks,int length) {
      if(ability==null) return Offset.NONE;
      if(ability==ReinhardAbility.REID_STRIKE) return reidSupport(phase,ticks);
      return counter(of(ability,ability==ReinhardAbility.LIGHT_PUNCH?phase:0,ticks,length));
   }
   private static Offset counter(Offset main) {
      return new Offset(-main.x()*.25F,main.y()*.3F,-main.z()*.15F,-main.pitch()*.2F,-main.yaw()*.2F,-main.roll()*.4F);
   }
   /** Light punch move 1 is the left jab: the left arm strikes, the right one counters. */
   public static boolean leftStrike(@Nullable ReinhardAbility ability,int phase) {
      return ability==ReinhardAbility.LIGHT_PUNCH && phase==1;
   }

   /** Weight of the drawn guard under another action: out over its first ticks, back over its last. */
   public static float guardUnder(double ticks,int length) {
      if(!Double.isFinite(ticks)) return 1;
      float in=smooth(ticks/4);
      float out=length>0?smooth((length-ticks)/4):1;
      return 1-in*out;
   }

   private static float windowFade(double ticks,int length) {
      return length>0 ? smooth((length-ticks)/2) : 1;
   }

   public static Offset worldCut(double ticks,boolean physicalLeft) {
      if (!Double.isFinite(ticks) || ticks<=0 || ticks>=ReinhardRules.WORLD_CUT_BLAST+20) return Offset.NONE;
      Offset right=sample(WORLD_CUT,ticks);
      return physicalLeft ? new Offset(right.x()-.10F,right.y(),right.z(),right.pitch(),-right.yaw(),right.roll()) : right;
   }

   /**
    * Normal World Cut of a {@link dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep}: the arm is raised
    * toward that shoulder through the hold, comes down square at the contact (tick 80) and follows through a little
    * past the centre, the same path as the third-person clip. View-space offsets like {@link #diagonal}.
    */
   public static Offset worldCut(double ticks,boolean physicalLeft,int sweep) {
      Offset o=worldCut(ticks,physicalLeft);
      if(o==Offset.NONE) return o;
      float side=dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep.side(sweep);
      // Same beats as the clip: raised upright by tick 20, settled over the shoulder by tick 30.
      float w=smooth((ticks-16)/14)*(1-smooth((ticks-86)/14));
      float lean=1-smooth((ticks-(ReinhardRules.WORLD_CUT_BLAST-14))/14);
      float through=smooth((ticks-ReinhardRules.WORLD_CUT_BLAST)/4)*(1-smooth((ticks-88)/10));
      float lateral=(lean-.3F*through)*side*w;
      return new Offset(o.x()+.12F*lateral,o.y(),o.z(),o.pitch(),o.yaw()-20*lateral,o.roll()-24*lateral);
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
