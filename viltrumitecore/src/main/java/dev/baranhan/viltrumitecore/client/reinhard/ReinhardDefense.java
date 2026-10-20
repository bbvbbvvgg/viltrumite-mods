package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;

/** Automatic reactions use the existing clips/layer blender in both views.
 * Main action ownership wins over a defense arm. A sword grip is never torn apart.
 */
final class ReinhardDefense {
   private static final Map<Object, State> THIRD = new WeakHashMap<>(), FIRST = new WeakHashMap<>();
   private static final class State {
      long nanos;
      float weight, yaw, pitch;
      DefenseReaction.Kind kind = DefenseReaction.Kind.NONE;
   }
   private static State sample(Object player, HeroPublicSnapshot snapshot, boolean fp) {
      State s = (fp ? FIRST : THIRD).computeIfAbsent(player, p -> new State());
      long now = Util.getNanos();
      double dt = s.nanos == 0 ? 0 : Math.max(0, Math.min(.1, (now-s.nanos)/1E9));
      s.nanos = now;
      var next = DefenseReaction.kind(snapshot.heroFlags());
      // On a threat-type change, recover briefly before the next gesture. Do not snap.
      boolean active = next != DefenseReaction.Kind.NONE && (s.kind == next || s.weight < .04F);
      if (active) s.kind = next;
      float k = (float)(1-Math.exp(-(active ? 25 : 16)*dt));
      s.weight += ((active ? 1 : 0)-s.weight)*k;
      float aim = (float)(1-Math.exp(-12*dt));
      if (next != DefenseReaction.Kind.NONE) {
         s.yaw += (DefenseReaction.yaw(snapshot.heroFlags())-s.yaw)*aim;
         s.pitch += (DefenseReaction.pitch(snapshot.heroFlags())-s.pitch)*aim;
      }
      return s;
   }
   static void reset(Object player) { THIRD.remove(player); FIRST.remove(player); }
   static String clip(DefenseReaction.Kind kind) {
      return switch (kind) {
         case PALM -> "parry";
         case WEAPON -> ReinhardClips.REFLECT_READY;
         case ARROW, BEAM -> ReinhardClips.CATCH_READY;
         case PROJECTILE -> ReinhardClips.REFLECT;
         default -> "parry";
      };
   }
   private static boolean armOwned(HeroPublicSnapshot s) {
      return s.actionId() >= 0 || ReinhardFlags.has(s.heroFlags(), ReinhardFlags.REID_DRAWN)
         || ReinhardFlags.has(s.heroFlags(), ReinhardFlags.REID_DRAWING) || ReinhardFlags.has(s.heroFlags(), ReinhardFlags.REID_SHEATHING);
   }
   static void body(Object player, HeroPublicSnapshot snapshot, BakedGeoModel model) {
      State s = sample(player, snapshot, false);
      if (s.weight < .001F) return;
      if (armOwned(snapshot) || s.kind == DefenseReaction.Kind.EVADE) {
         var torso = model.getBone("body");
         if (torso != null) torso.rotZ += (float)Math.toRadians(6*s.weight*Math.copySign(1,s.yaw));
         return;
      }
      var clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, clip(s.kind));
      if (clip != null) ReinhardLayers.overlay(clip, model, s.kind == DefenseReaction.Kind.PROJECTILE ? .1 : .15, s.weight);
      var arm = model.getBone("right_arm");
      if (arm != null) { arm.rotY += (float)Math.toRadians(Math.max(-55,Math.min(55,s.yaw)))*s.weight;
         arm.rotX += (float)Math.toRadians(s.pitch*.35F)*s.weight; }
   }
   static ReinhardFirstPersonPose.Offset firstPerson(Object player, HeroPublicSnapshot snapshot, boolean left,
      ReinhardFirstPersonPose.Offset base) {
      if (left) return base;
      State s = sample(player, snapshot, true);
      if (s.weight < .001F || armOwned(snapshot)) return base;
      var ability = switch (s.kind) {
         case ARROW, BEAM -> dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.CATCH;
         case PROJECTILE, WEAPON -> dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.REFLECT;
         default -> null;
      };
      var o = ability == null ? new ReinhardFirstPersonPose.Offset(0,.08F,-.08F,-18,0,8)
         : ReinhardFirstPersonPose.of(ability,0,3,100);
      float w=s.weight;
      return new ReinhardFirstPersonPose.Offset(base.x()+o.x()*w,base.y()+o.y()*w,base.z()+o.z()*w,
         base.pitch()+(o.pitch()+s.pitch*.35F)*w,base.yaw()+(o.yaw()+s.yaw*.45F)*w,base.roll()+o.roll()*w);
   }
}
