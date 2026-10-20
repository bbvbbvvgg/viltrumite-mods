package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Independent response layer. Weapon transforms remain owned by the existing sword solver. */
final class ReinhardDefense {
   private static final Map<Object,State> THIRD=new WeakHashMap<>(),FIRST=new WeakHashMap<>();
   static final class State {
      long nanos,start; float weight; boolean left;
      Vec3 target; DefenseReaction.Kind kind=DefenseReaction.Kind.NONE;
      void advance(int flags,Vec3 point,long now) {
         double dt=nanos==0?0:Math.max(0,Math.min(.1,(now-nanos)/1E9));nanos=now;
         var next=DefenseReaction.kind(flags); boolean nextLeft=DefenseReaction.leftHand(flags);
         boolean active=next!=DefenseReaction.Kind.NONE && (kind==next && left==nextLeft || weight<.02F);
         if(active) {
            if(kind!=next || left!=nextLeft || weight<.001F)start=now;
            kind=next;left=nextLeft;
            if(point!=null)target=target==null || weight<.02F ? point : target.lerp(point,1-Math.exp(-12*dt));
         }
         weight+=((active?1:0)-weight)*(float)(1-Math.exp(-(active?25:16)*dt));
      }
   }
   private static State sample(Object player,HeroPublicSnapshot s,boolean fp) {
      var state=(fp?FIRST:THIRD).computeIfAbsent(player,p->new State());state.advance(s.heroFlags(),s.actionTarget(),Util.getNanos());return state;
   }
   static void reset(Object player) {THIRD.remove(player);FIRST.remove(player);}
   static boolean active(HeroPublicSnapshot s,boolean left) {
      return DefenseReaction.kind(s.heroFlags())!=DefenseReaction.Kind.NONE && DefenseReaction.leftHand(s.heroFlags())==left;
   }
   static boolean visible(Object player,HeroPublicSnapshot snapshot,boolean left) {
      var state=sample(player,snapshot,true);
      return active(snapshot,left) || state.left==left && state.weight>.01F;
   }
   /** Called AFTER equipment/recovery. Only the selected free hand is constrained. */
   static void apply(BakedGeoModel model,DefenseReaction.Kind kind,float weight,Vec3 target,boolean left) {
      if(weight<.001F)return;
      if(kind==DefenseReaction.Kind.EVADE)return;
      if(target==null)return;
      float w=weight*weight*(3-2*weight);
      var wrist=model.getBone(left?"left_hand":"right_hand");
      if(wrist!=null) {
         // Modest authored contact orientations, not one universal block and not wrist flips.
         float roll=switch(kind) {case WEAPON -> 18;case PROJECTILE -> -18;case ARROW -> -5;case BRACE -> 10;default -> 0;};
         float pitch=kind==DefenseReaction.Kind.BEAM?-12:kind==DefenseReaction.Kind.ARROW?8:0;
         wrist.rotZ+=(float)Math.toRadians(roll*w*(left?-1:1));wrist.rotX+=(float)Math.toRadians(pitch*w);
      }
      ReinhardGrip.guard(model,new Vector3f((float)target.x,(float)target.y,(float)target.z),w,left);
   }
   static void torso(Object player,HeroPublicSnapshot snapshot,BakedGeoModel model) {
      var s=sample(player,snapshot,false);var torso=model.getBone("body");
      if(torso!=null && (s.kind==DefenseReaction.Kind.EVADE || s.kind==DefenseReaction.Kind.BRACE))
         torso.rotZ+=(float)Math.toRadians((s.kind==DefenseReaction.Kind.EVADE?9:3)*s.weight*(s.left?-1:1));
   }
   static void body(Object player,HeroPublicSnapshot snapshot,BakedGeoModel model) {
      var s=sample(player,snapshot,false);apply(model,s.kind,s.weight,s.target,s.left);
   }
   static void elbows(Object player,HeroPublicSnapshot snapshot,BakedGeoModel model) {
      var s=sample(player,snapshot,true);
      if(s.kind==DefenseReaction.Kind.EVADE)return;
      var lower=model.getBone(s.left?"left_forearm":"right_forearm");
      if(lower!=null)lower.rotX+=(float)Math.toRadians(35*s.weight);
   }
   static ReinhardFirstPersonPose.Offset offset(DefenseReaction.Kind kind,boolean left,float w,
      ReinhardFirstPersonPose.Offset base,boolean selected) {
      if(w<.001F)return base;
      w=w*w*(3-2*w);
      if(kind==DefenseReaction.Kind.EVADE)
         return new ReinhardFirstPersonPose.Offset(base.x()+.12F*w,base.y()-.12F*w,base.z()+.08F*w,base.pitch()+4*w,base.yaw(),base.roll()+9*w);
      if(!selected)return base;
      float pitch=switch(kind) {case BEAM -> -40;case ARROW -> -35;case WEAPON -> -22;case PROJECTILE -> -12;case BRACE -> -18;default -> -30;};
      float yaw=kind==DefenseReaction.Kind.PROJECTILE?28:kind==DefenseReaction.Kind.WEAPON?-12:10;
      float roll=kind==DefenseReaction.Kind.WEAPON?24:kind==DefenseReaction.Kind.PROJECTILE?-25:-8;
      // Absolute view-space guard blended from the action, not another rotation added to it.
      return new ReinhardFirstPersonPose.Offset(base.x()*(1-w),base.y()*(1-w)+(kind==DefenseReaction.Kind.ARROW?.52F:.16F)*w,base.z()*(1-w)-.22F*w,
         base.pitch()*(1-w)+pitch*w,base.yaw()*(1-w)+yaw*w,base.roll()*(1-w)+roll*w);
   }
   static ReinhardFirstPersonPose.Offset firstPerson(Object player,HeroPublicSnapshot snapshot,boolean left,ReinhardFirstPersonPose.Offset base) {
      var s=sample(player,snapshot,true);return offset(s.kind,left,s.weight,base,left==s.left);
   }
}
