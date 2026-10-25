package dev.baranhan.viltrumitecore.client.reinhard;

import java.util.Map;
import java.util.WeakHashMap;

/** Render weights only; clocks and authoritative actions remain in the existing systems. */
final class ReinhardMotion {
   private static final Map<Object,State> STATES=new WeakHashMap<>();
   private ReinhardMotion() {}
   static float[] sample(Object owner,boolean crouch,boolean sprint,boolean air,boolean rising,long now) {
      float[] target={crouch?1:0,sprint?1:0,air&&rising?1:0,air&&!rising?1:0};
      State s=STATES.computeIfAbsent(owner,o->new State(now,target.clone()));
      double dt=Math.max(0,Math.min(.1,(now-s.now)/1E9));s.now=now;
      float w=(float)(1-Math.exp(-16*dt));
      for(int i=0;i<4;i++)s.values[i]+=(target[i]-s.values[i])*w;
      return s.values.clone();
   }
   static void reset(Object owner){STATES.remove(owner);}
   private static final class State {
      long now; final float[] values;
      State(long now,float[] values){this.now=now;this.values=values;}
   }
}
