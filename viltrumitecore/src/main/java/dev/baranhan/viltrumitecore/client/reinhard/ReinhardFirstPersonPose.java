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
   /**
    * Drawn Reid in first person (REINHARD OVERHAUL 3b item 1): a two-handed guard. The right
    * palm holds the guard end of the hilt low right of the crosshair (NDC ≈(.54,-.47)), the
    * left palm the pommel just below it, and the blade rises across the view to the upper left
    * (tip ≈(-.16,.66)) showing its broad face: a huge sword held in front of the chest, not a
    * stick along the right edge. Fitted in the CPU preview (tools/preview_reinhard_fp.py) with
    * both arm roots below the frame; 28° from the old one-handed guard, so the drawn strokes
    * that start and end here keep short transitions.
    */
   public static final Offset DRAWN_GUARD = new Offset(-.276F,.310F,-.061F,-10.5F,-19.7F,-28.5F);
   public static Offset drawnGuard(ReinhardPoses.ReidPhase phase,double ticks) {
      float w=ReinhardSword.handWeight(phase,ticks);
      return w>0.001F?DRAWN_GUARD.scaled(Math.min(1,w)):Offset.NONE;
   }

   /** Absolute shoulder keys {pitch, yaw, roll, x, y, z} of the sword arm in the drawn strokes
    * (two-handed: the support pass carries the other arm to the pommel and solves its palm),
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
   private static final float[][] SAW = saw();

   /** Saw Sphere, absolute like the drawn strokes (it starts and ends at the guard) and
    * two-handed like them (the support palm stays on the pommel): raised over the rise, then the
    * third-person rhythm, a forehand (raised right, through the middle, out to the left) and a
    * backhand back every {@code SWING_PERIOD} ticks, each contact at the middle of its sweep; the
    * last backhand coils into the big final forehand, held through the plunge, back to the guard. */
   private static float[][] saw() {
      int period = dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.SWING_PERIOD;
      int launch = dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.LAUNCH_START;
      int last = dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.LAST_SWING;
      float fin = dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.FINAL_SWING;
      java.util.List<float[]> keys = new java.util.ArrayList<>();
      keys.add(guard(0));
      keys.add(at(20, RISE_END));
      keys.add(at(launch - period / 2F, DOWN_WIND));
      boolean left = false;
      for (int contact = launch; contact <= last; contact += period) {
         keys.add(at(contact, SWEEP_MID));
         left = !left;
         keys.add(at(contact + period / 2F, left ? SWEEP_END : DOWN_WIND));
      }
      keys.add(at(fin - 3, SWEEP_WIND));
      keys.add(at(fin - 1.5F, SWEEP_IN));
      keys.add(at(fin, SWEEP_MID));
      keys.add(at(fin + 1.5F, SWEEP_END));
      keys.add(at(fin + 8, SWEEP_END));
      keys.add(guard(dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.LENGTH - 4));
      return densify(keys.toArray(float[][]::new));
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

   /**
    * Draw/sheathe, driven by the six-second authoritative Reid phase: the arm pulls with the blade
    * out of the scabbard and hands over to the {@link #DRAWN_GUARD} as the freed blade rises into
    * the guard (a cross-fade on {@link ReinhardSword#handWeight}, never the two stacked).
    */
   public static final Offset REID_PULL = new Offset(-.16F,.22F,-.10F,-52,18,-6);
   public static Offset reid(ReinhardPoses.ReidPhase phase, double ticks) {
      float weight = switch(phase) {
         case DRAWING, SHEATHING -> ReinhardPoses.bladeOut(phase,ticks) * (1 - ReinhardSword.handWeight(phase,ticks));
         default -> 0;
      };
      return weight > 0.001F ? REID_PULL.scaled(weight) : Offset.NONE;
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
         case SAW_SPHERE -> sawSphere(ticks);
         // Both World Cuts move the whole two-handed rig in view space (cutView), not one arm.
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
      // Sword strokes are two-handed: the support arm is carried to the pommel, it does not counter.
      if(ReinhardHands.twoHanded(ability)) return Offset.NONE;
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

   /**
    * View-space pivot of {@link #cutView}: the right palm (blade guard) of the {@link #DRAWN_GUARD}
    * rig, so the hilt travels on a straight line while the rig turns about it.
    */
   static final org.joml.Vector3f CUT_PIVOT = new org.joml.Vector3f(.700F,-.345F,-1.043F);
   /** Rigid keys of the two-handed rig {tick, rx, ry, rz (XYZ degrees), dx, dy, dz (pivot travel),
    * ease into the key: 0 smooth, 1 accelerating, 2 decelerating}. Beats of the third-person clips:
    * raised by 18, hold, the anticipation (coil back) 64..74, an accelerating 6-tick swing whose
    * contact is the BLAST tick 80, overshoot 84, settle 90, back in the guard at 100. Picked in the
    * CPU preview with both arm roots off screen and no turn above 155° between keys. */
   private static final float[][] CUT_FROM_RIGHT = {{0,0,0,0,0,0,0,0},
      {18,37.2F,28.2F,-61.3F,-.177F,.065F,.043F,0},{64,40.9F,27.7F,-61.8F,-.158F,.090F,.043F,0},
      {74,49.4F,26.6F,-62.7F,-.115F,.149F,.043F,0},{80,41.8F,-70.1F,66.0F,-.412F,.014F,-.007F,1},
      {84,26.1F,-36.3F,88.2F,-.676F,-.134F,.093F,2},{90,40.4F,-47.5F,85.7F,-.575F,-.089F,.043F,0},{100,0,0,0,0,0,0,0}};
   /** Raised over the left shoulder; the contact meets the same square cut line. */
   private static final float[][] CUT_FROM_LEFT = {{0,0,0,0,0,0,0,0},
      {18,29.6F,11.7F,-39.5F,-1.173F,.065F,.043F,0},{64,32.8F,13.7F,-39.4F,-1.188F,.090F,.043F,0},
      {74,40.2F,18.4F,-39.0F,-1.223F,.149F,.043F,0},{80,53.9F,-67.8F,78.3F,-.412F,.014F,-.007F,1},
      {84,26.1F,-36.3F,88.2F,-.676F,-.134F,.093F,2},{90,40.4F,-47.5F,85.7F,-.575F,-.089F,.043F,0},{100,0,0,0,0,0,0,0}};
   /** Loaded high over the right shoulder, cut down across the view to the lower left. */
   private static final float[][] CUT_DIAGONAL = {{0,0,0,0,0,0,0,0},
      {18,93.3F,51.6F,-116.4F,-.077F,.205F,.043F,0},{64,95.1F,49.7F,-115.4F,-.059F,.226F,.043F,0},
      {74,99.2F,45.3F,-113.1F,-.015F,.275F,.043F,0},{80,-6.9F,-28.6F,37.0F,-.543F,.014F,-.007F,1},
      {84,8.2F,-24.1F,73.8F,-.818F,-.121F,.093F,2},{90,17.4F,-38.3F,64.4F,-.700F,-.075F,.043F,0},{100,0,0,0,0,0,0,0}};

   /**
    * REINHARD OVERHAUL 3b item 5: both World Cuts in first person as ONE rigid view-space motion of
    * the whole two-handed rig (both camera arms, both palms on the hilt, the blade) on top of the
    * drawn guard. The hands never separate and no arm is re-aimed alone; null outside the cast.
    * {@code sweep} is the normal cut's {@link dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep}.
    */
   @Nullable
   public static org.joml.Matrix4f cutView(@Nullable ReinhardAbility cinematic,int sweep,double ticks) {
      float[][] keys=cutKeys(cinematic,sweep);
      if(keys==null || !Double.isFinite(ticks) || ticks<=0 || ticks>=CUT_LENGTH) return null;
      org.joml.Matrix4f rig=sampleCut(keys,(float)ticks);
      float dip=cutDip(keys,ticks);
      return dip==0 ? rig : new org.joml.Matrix4f().translation(0,-dip,CUT_DIP_BACK*dip).mul(rig);
   }
   /** Drop of the whole rig (blocks, down and a little toward the camera) while the normal cut turns
    * it: the right arm turns with the rig and its shoulder end would otherwise cross the bottom of the
    * view (CPU preview: 76.6..80.2 through the swing, the edge through the raise from the left). The
    * diagonal turns the arm the other way and needs none. Smooth, zero outside the two windows. */
   static float cutDip(float[][] keys,double ticks) {
      if(keys==CUT_DIAGONAL) return 0;
      return CUT_RAISE_DIP*smooth((ticks-6)/6)*(1-smooth((ticks-14)/6))
         +CUT_SWING_DIP*smooth((ticks-74)/4)*(1-smooth((ticks-81)/7));
   }
   static final float CUT_RAISE_DIP=.04F, CUT_SWING_DIP=.2F, CUT_DIP_BACK=.3F;
   static final int CUT_LENGTH=ReinhardRules.WORLD_CUT_BLAST+20;
   @Nullable
   static float[][] cutKeys(@Nullable ReinhardAbility cinematic,int sweep) {
      if(cinematic==ReinhardAbility.DIAGONAL_WORLD_CUT) return CUT_DIAGONAL;
      if(cinematic!=ReinhardAbility.WORLD_CUT) return null;
      return dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep.side(sweep)<0 ? CUT_FROM_LEFT : CUT_FROM_RIGHT;
   }
   private static org.joml.Matrix4f sampleCut(float[][] keys,float t) {
      int i=1;
      while(i<keys.length-1 && t>keys[i][0]) i++;
      float[] a=keys[i-1],b=keys[i];
      float f=Math.max(0,Math.min(1,(t-a[0])/(b[0]-a[0])));
      f=b[7]==1?f*f*f:b[7]==2?1-(1-f)*(1-f)*(1-f):f*f*(3-2*f);
      org.joml.Quaternionf q=cutRotation(a).slerp(cutRotation(b),f);
      org.joml.Vector3f p=CUT_PIVOT;
      return new org.joml.Matrix4f().translation(p.x+a[4]+(b[4]-a[4])*f,p.y+a[5]+(b[5]-a[5])*f,p.z+a[6]+(b[6]-a[6])*f)
         .rotate(q).translate(-p.x,-p.y,-p.z);
   }
   /** Puts a rigid {@link #cutView} on a pose stack as translation + rotation, so the normal
    * matrix turns with it (PoseStack.mulPoseMatrix alone would leave the lighting behind). */
   public static void applyCut(com.mojang.blaze3d.vertex.PoseStack stack,org.joml.Matrix4f cut) {
      stack.translate(cut.m30(),cut.m31(),cut.m32());
      stack.mulPose(cut.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize());
   }
   private static org.joml.Quaternionf cutRotation(float[] k) {
      return new org.joml.Quaternionf().rotationXYZ((float)Math.toRadians(k[1]),(float)Math.toRadians(k[2]),(float)Math.toRadians(k[3]));
   }

   /** The Saw Sphere sword arm (absolute; the support arm is carried to the pommel). */
   public static Offset sawSphere(double ticks) {
      return strike(SAW,ticks);
   }

   public static Offset worldErasure(double ticks,boolean physicalLeft) {
      if (!Double.isFinite(ticks) || ticks<=0 || ticks>=ReinhardRules.ERASE_CLIP_LENGTH) return Offset.NONE;
      float raise=smooth(ticks/15),cut=smooth((ticks-ReinhardRules.ERASE_CLIP_CUT)/10.0);
      return new Offset((physicalLeft ? .10F : -.22F)*raise+.40F*cut,
         .22F*raise-.30F*cut,-.14F*raise-.18F*cut,-20*raise+50*cut,
         (physicalLeft ? -8 : 10)*raise+45*cut,(physicalLeft ? -5 : 5)*raise+12*cut)
         .scaled(windowFade(ticks,ReinhardRules.ERASE_CLIP_LENGTH));
   }

   private static float smooth(double value) {
      double t=Math.max(0,Math.min(1,value));
      return (float)(t*t*(3-2*t));
   }
}
