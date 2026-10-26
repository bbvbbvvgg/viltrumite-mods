package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import org.joml.Matrix4f;

/** Transition cache for existing sampled poses, not an animation clock. View states never share bones. */
final class ReinhardRecovery {
   private static final Map<Object, Bones> BODY=new WeakHashMap<>(), ARMS=new WeakHashMap<>();
   private static final Map<Object, OffsetState[]> FP=new WeakHashMap<>();
   private static final Map<Object, Long> VISIBLE_UNTIL=new WeakHashMap<>();
   private static final Map<Object, Object> FP_WEAPON_KEYS=new WeakHashMap<>();
   private static final Map<Object, ViewState> CUT_VIEW=new WeakHashMap<>();
   private ReinhardRecovery() {}

   static boolean visible(Object player,boolean active,long now) {
      if(active) VISIBLE_UNTIL.put(player,now+300_000_000L);
      return active || now<VISIBLE_UNTIL.getOrDefault(player,Long.MIN_VALUE);
   }
   /** The first-person view's own state key per player (its motion smoothing must not share the
    * third-person body's, which the inventory paper doll also advances). */
   static Object firstPersonKey(Object player) { return FP_WEAPON_KEYS.computeIfAbsent(player,p->new Object()); }
   static float remaining(long since,long now) {
      double seconds=Math.max(0,(now-since)/1E9);
      return seconds>=.30 ? 0 : (float)Math.exp(-24*seconds);
   }
   static float angle(float from,float to,float weight) {
      float delta=(float)Math.atan2(Math.sin(from-to),Math.cos(from-to));
      return to+delta*weight;
   }
   static void body(Object player,BakedGeoModel model,int key,long now) { bones(BODY,player,model,key,now); }
   static void arms(Object player,BakedGeoModel model,int key,long now) { bones(ARMS,player,model,key,now); }
   private static void bones(Map<Object,Bones> states,Object player,BakedGeoModel model,int key,long now) {
      Bones state=states.computeIfAbsent(player,p->new Bones(key));
      if(state.key!=key) {state.key=key;state.since=key<0?now:Long.MIN_VALUE/2;state.from=state.last;}
      float w=remaining(state.since,now);
      Map<String,float[]> next=new HashMap<>();
      for(GeoBone bone:model.allBones()) {
         float[] to={bone.rotX,bone.rotY,bone.rotZ,bone.posX,bone.posY,bone.posZ};
         float[] from=state.from.get(bone.name);
         if(from!=null && w>0) for(int i=0;i<6;i++) to[i]=i<3 ? angle(from[i],to[i],w) : to[i]+(from[i]-to[i])*w;
         bone.rotX=to[0];bone.rotY=to[1];bone.rotZ=to[2];bone.posX=to[3];bone.posY=to[4];bone.posZ=to[5];
         next.put(bone.name,to);
      }
      state.last=next;
   }
   static ReinhardFirstPersonPose.Offset firstPerson(Object player,boolean left,ReinhardFirstPersonPose.Offset target,int key,long now) {
      OffsetState[] pair=FP.computeIfAbsent(player,p->new OffsetState[]{new OffsetState(key),new OffsetState(key)});
      OffsetState state=pair[left?1:0];
      if(state.key!=key) {state.key=key;state.since=key<0?now:Long.MIN_VALUE/2;state.from=state.last;}
      float w=remaining(state.since,now);
      ReinhardFirstPersonPose.Offset from=state.from;
      state.last=new ReinhardFirstPersonPose.Offset(target.x()+(from.x()-target.x())*w,target.y()+(from.y()-target.y())*w,
         target.z()+(from.z()-target.z())*w,target.pitch()+(from.pitch()-target.pitch())*w,
         target.yaw()+(from.yaw()-target.yaw())*w,target.roll()+(from.roll()-target.roll())*w);
      return state.last;
   }
   /** First-person rig motion of a World Cut (ReinhardFirstPersonPose.cutView, null = none):
    * an interrupted cut eases back to the guard like the shoulder offsets, it never snaps. */
   static Matrix4f cutView(Object player,int key,@javax.annotation.Nullable Matrix4f target,long now) {
      Matrix4f to=target!=null?target:new Matrix4f();
      ViewState state=CUT_VIEW.computeIfAbsent(player,p->new ViewState(key));
      if(state.key!=key) {state.key=key;state.since=key<0?now:Long.MIN_VALUE/2;state.from=state.last;}
      float w=remaining(state.since,now);
      state.last=w>0?ReinhardSword.interpolate(to,state.from,w):new Matrix4f(to);
      return new Matrix4f(state.last);
   }
   static void reset(Object player) {
      CUT_VIEW.remove(player);
      BODY.remove(player);ARMS.remove(player);FP.remove(player);VISIBLE_UNTIL.remove(player);
      Object fp=FP_WEAPON_KEYS.remove(player);if(fp!=null) ReinhardMotion.reset(fp);
   }
   private static class Bones {
      int key;long since=Long.MIN_VALUE/2;Map<String,float[]> last=Map.of(),from=Map.of();
      Bones(int key){this.key=key;}
   }
   private static class OffsetState {
      int key;long since=Long.MIN_VALUE/2;
      ReinhardFirstPersonPose.Offset from=ReinhardFirstPersonPose.Offset.NONE,last=from;
      OffsetState(int key){this.key=key;}
   }
   private static class ViewState {
      int key;long since=Long.MIN_VALUE/2;Matrix4f from=new Matrix4f(),last=new Matrix4f();
      ViewState(int key){this.key=key;}
   }
}
