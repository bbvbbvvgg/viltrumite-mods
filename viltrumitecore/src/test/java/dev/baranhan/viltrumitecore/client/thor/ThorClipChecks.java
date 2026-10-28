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

   /** Clips drawn with Mjölnir in the right hand (fists and hammerless loops are not). */
   static boolean hammerInHand(String clip) {
      String tp = clip.endsWith("_fp") ? clip.substring(0, clip.length() - 3) : clip;
      return tp.startsWith("hammer_") || tp.startsWith("electric_") || tp.equals("wrath") || tp.equals("draw") || tp.equals("holster")
         || tp.endsWith("_hammer") || tp.startsWith("throw") || tp.startsWith("lift_thor") || tp.startsWith("flight");
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
