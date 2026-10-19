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
   private static final Map<Object, WeaponState> WEAPONS=new WeakHashMap<>();
   private static final Map<Object, Long> VISIBLE_UNTIL=new WeakHashMap<>();
   private static final Map<Object, Object> FP_WEAPON_KEYS=new WeakHashMap<>();
   private ReinhardRecovery() {}

   static boolean visible(Object player,boolean active,long now) {
      if(active) VISIBLE_UNTIL.put(player,now+300_000_000L);
      return active || now<VISIBLE_UNTIL.getOrDefault(player,Long.MIN_VALUE);
   }
   static Object firstPersonWeaponKey(Object player) { return FP_WEAPON_KEYS.computeIfAbsent(player,p->new Object()); }
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
      if(state.key!=key) {state.key=key;state.since=now;state.from=state.last;}
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
      if(state.key!=key) {state.key=key;state.since=now;state.from=state.last;}
      float w=remaining(state.since,now);
      ReinhardFirstPersonPose.Offset from=state.from;
      state.last=new ReinhardFirstPersonPose.Offset(target.x()+(from.x()-target.x())*w,target.y()+(from.y()-target.y())*w,
         target.z()+(from.z()-target.z())*w,target.pitch()+(from.pitch()-target.pitch())*w,
         target.yaw()+(from.yaw()-target.yaw())*w,target.roll()+(from.roll()-target.roll())*w);
      return state.last;
   }
   static Matrix4f[] weapons(Object player,int key,Matrix4f sheath,Matrix4f blade,long now) {
      WeaponState state=WEAPONS.computeIfAbsent(player,p->new WeaponState(key,sheath,blade));
      if(state.key!=key) {state.key=key;state.since=now;state.from=state.last;}
      float w=remaining(state.since,now);
      state.last=new Matrix4f[]{ReinhardSword.interpolate(sheath,state.from[0],w),ReinhardSword.interpolate(blade,state.from[1],w)};
      return state.last;
   }
   static void reset(Object player) {
      BODY.remove(player);ARMS.remove(player);FP.remove(player);WEAPONS.remove(player);VISIBLE_UNTIL.remove(player);
      Object fp=FP_WEAPON_KEYS.remove(player);if(fp!=null) WEAPONS.remove(fp);
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
   private static class WeaponState {
      int key;long since=Long.MIN_VALUE/2;Matrix4f[] from,last;
      WeaponState(int key,Matrix4f sheath,Matrix4f blade){this.key=key;last=new Matrix4f[]{new Matrix4f(sheath),new Matrix4f(blade)};from=last;}
   }
}
