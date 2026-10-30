package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * R2b wiring of the posed checks to Thor's assets. The body model and its grip
 * bones belong to R1 (rework spec §1/§9: R1 blocks R2): until R1's skeleton doc
 * lands, {@link #BODY} and the bone names are the only placeholders here and the
 * checks on real assets abort instead of guessing.
 */
final class ThorClipChecks {
   /** R1's Thor body (set from R1's skeleton doc). */
   static final Path BODY = ThorClipRig.GEO.resolve("thor/thor.geo.json");
   static final Path HAMMER = ThorClipRig.GEO.resolve("thor/mjolnir_hand.geo.json");
   /** R1's right-hand grip bone; the hand model is drawn in its frame. */
   static final String GRIP_BONE = "right_hand";
   /** R1's left hand: it may hold the handle in two-handed clips (lift, overhead, slam). */
   static final String OFF_HAND_BONE = "left_hand";
   static final String HAMMER_LABEL = "mjolnir";

   private ThorClipChecks() {
   }

   /** Clips drawn with Mjölnir in the right hand at some tick (fists and hammerless loops are not). */
   static boolean hammerInHand(String clip) {
      return place(clip, 0) == Place.HAND || place(clip, Integer.MAX_VALUE) == Place.HAND;
   }

   /** Where Mjölnir is during a third-person clip (tools/assets/make_thor_clips.py: Clip.hammer / hand_span / away). */
   enum Place {
      HAND,
      BELT,
      /** Thrown, flying back, or planted in the ground (R1's lift squat): neither in the fist nor on the belt. */
      AWAY
   }

   /** Draw / holster: the server switches the hammer place on the contact tick (ThorAttacks ENTER_MAIN, tick 14). */
   /** ThorActions: the hammer leaves the belt on the draw's contact tick, reaches it on the holster's. */
   static final int DRAW_SWITCH_TICK = dev.baranhan.viltrumitecore.hero.thor.ThorRules.DRAW_INTRO;
   static final int HOLSTER_SWITCH_TICK = dev.baranhan.viltrumitecore.hero.thor.ThorRules.HOLSTER_INTRO;

   /**
    * Mjölnir's place at a tick of a clip, as the clip generator bakes it: draw = belt 0–13, hand 14+;
    * holster = hand 0–13, belt 14+; pull / throw_release = away; fists and hammerless loops = belt.
    * R1's crouch / sit keep the belt hammer, lift_squat has it planted in front. An unknown clip throws:
    * a new clip must be classified here before the body check can judge it.
    */
   static Place place(String clip, int tick) {
      String tp = clip.endsWith("_fp") ? clip.substring(0, clip.length() - 3) : clip;
      switch (tp) {
         case "draw":
            return tick >= DRAW_SWITCH_TICK ? Place.HAND : Place.BELT;
         case "holster":
            return tick < HOLSTER_SWITCH_TICK ? Place.HAND : Place.BELT;
         case "pull", "throw_release", "lift_squat":
            return Place.AWAY;
         case "catch", "wrath", "lift_thor", "throw_hold", "throw_windup":
            return Place.HAND;
         case "idle", "walk", "run", "stance", "crouch", "sit", "guard_fist":
            return Place.BELT;
         default:
            break;
      }

      if (tp.startsWith("hammer_") || tp.startsWith("electric_") || tp.startsWith("flight_") || tp.endsWith("_hammer")) {
         return Place.HAND;
      }

      if (tp.startsWith("fist_")) {
         return Place.BELT;
      }

      throw new IllegalArgumentException("clip " + clip + " has no Mjölnir place in ThorClipChecks.place");
   }

   /** Posed boxes of the body (+ the hammer in the grip bone) at a clip second. */
   static List<ThorClipRig.Box> pose(BakedGeoModel body, BakedGeoModel hammer, String gripBone, Animation clip, double seconds, boolean withHammer) {
      AnimationController.seek(clip, body, seconds);
      List<ThorClipRig.Box> boxes = new ArrayList<>(ThorClipRig.boxes(ThorIntersection.BODY, body, new org.joml.Matrix4f()));
      if (withHammer && hammer != null) {
         boxes.addAll(ThorClipRig.attached(HAMMER_LABEL, hammer, body, gripBone));
      }

      return boxes;
   }

   /** Bones of the hand model a second palm may close on (the handle, not the head). */
   static final String[] HANDLE_BONES = {"handle", "handle_plains"};

   /** The rules of the body check: the grip bone holds the hammer, the off hand may share only the handle. */
   static ThorIntersection rules(String gripBone, String offHandBone) {
      ThorIntersection rules = new ThorIntersection().allow(ThorIntersection.BODY + ":" + gripBone, HAMMER_LABEL + ":*");
      for (String handle : HANDLE_BONES) {
         rules.allow(ThorIntersection.BODY + ":" + offHandBone, HAMMER_LABEL + ":" + handle);
      }

      return rules;
   }
}
