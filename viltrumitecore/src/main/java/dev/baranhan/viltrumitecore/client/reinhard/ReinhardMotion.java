package dev.baranhan.viltrumitecore.client.reinhard;

import java.util.Map;
import java.util.WeakHashMap;

/** Render weights only; clocks and authoritative actions remain in the existing systems. */
final class ReinhardMotion {
   private static final Map<Object,State> STATES=new WeakHashMap<>();
   private ReinhardMotion() {}
   /** Rise and fall share the same filter as their complementary ground weight. */
   static float ground(float[] weights) {
      return 1-Math.max(0,Math.min(1,weights[2]+weights[3]));
   }
   static float[] sample(Object owner,boolean crouch,boolean sprint,boolean air,boolean rising,long now) {
      float[] target={crouch?1:0,sprint?1:0,air&&rising?1:0,air&&!rising?1:0};
      State s=STATES.computeIfAbsent(owner,o->new State(now,target.clone()));
      double dt=Math.max(0,Math.min(.1,(now-s.now)/1E9));s.now=now;
      float w=(float)(1-Math.exp(-16*dt));
      for(int i=0;i<4;i++)s.values[i]+=(target[i]-s.values[i])*w;
      return s.values.clone();
   }
   static void reset(Object owner){STATES.remove(owner);PULSES.remove(owner);}

   /** Shortest flight that ends in a landing pulse (a stair hop or a step-up is not one). */
   static final double LAND_MIN_AIR=.35;
   private static final Map<Object,Pulse> PULSES=new WeakHashMap<>();
   /** Pure: landing pulse strength from the time spent airborne (seconds). */
   static float landWeight(double airborne) {
      return airborne<LAND_MIN_AIR?0:(float)Math.min(1,.45+.55*(airborne-LAND_MIN_AIR)/.8);
   }
   /**
    * {air step seconds, landing seconds, landing weight}; seconds -1 = no pulse.
    * {@code stepSeconds} comes from the air-step prediction/observation (AirStepPulse).
    */
   static float[] pulses(Object owner,boolean air,double stepSeconds,long now) {
      Pulse p=PULSES.computeIfAbsent(owner,o->new Pulse());
      if(air && p.airSince==Long.MIN_VALUE)p.airSince=now;
      if(!air && p.airSince!=Long.MIN_VALUE) {
         float w=landWeight((now-p.airSince)/1E9);
         p.airSince=Long.MIN_VALUE;
         if(w>0){p.landAt=now;p.landWeight=w;}
      }
      double land=p.landAt==Long.MIN_VALUE?-1:(now-p.landAt)/1E9;
      return new float[]{(float)stepSeconds,(float)land,p.landWeight};
   }
   /** {crouch, sprint, rise, fall} followed by {step s, land s, land weight}. */
   static float[] withPulses(float[] motion,float[] pulses) {
      float[] out=java.util.Arrays.copyOf(motion,motion.length+pulses.length);
      System.arraycopy(pulses,0,out,motion.length,pulses.length);
      return out;
   }
   private static final class Pulse {
      long airSince=Long.MIN_VALUE,landAt=Long.MIN_VALUE; float landWeight;
   }
   private static final class State {
      long now; final float[] values;
      State(long now,float[] values){this.now=now;this.values=values;}
   }
}
