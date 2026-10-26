package dev.baranhan.viltrumitecore.client.reinhard;

import javax.annotation.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Render-thread memo of ONE first-person frame (REINHARD OVERHAUL 3b item 1). Both camera arms
 * hold one blade, but vanilla draws them in two RenderHandEvent passes, main hand first. Every
 * pass records the hand BASE (the pose stack RenderHandEvent receives, the same for both passes)
 * and the rig motion of the frame; the sword-arm pass publishes its blade in that base space and
 * the support pass reads it, at most one frame old (a left-handed main arm draws the support arm
 * first). Plain static fields: first-person rendering is single-threaded.
 */
final class ReinhardFirstPersonFrame {
   private static long frame, bladeFrame = Long.MIN_VALUE / 2;
   private static final Matrix4f BASE = new Matrix4f(), BASE_INVERSE = new Matrix4f(), BLADE = new Matrix4f(), CUT = new Matrix4f();
   private static float support;

   private ReinhardFirstPersonFrame() {}

   /** RenderHandEvent: the main hand opens a frame; {@code cut} is the rig motion
    * (ReinhardFirstPersonPose.cutView, identity outside the World Cuts), {@code support} the
    * weight of this pass's two-handed grip (0 for the sword arm). */
   static void pass(boolean mainHand, Matrix4f base, Matrix4f cut, float supportWeight) {
      if (mainHand) frame++;
      BASE.set(base);
      BASE_INVERSE.set(base).invert();
      CUT.set(cut);
      support = supportWeight;
   }

   /** A pose of the current pass in the hand base space. */
   static Matrix4f toBase(Matrix4f pose) {
      return new Matrix4f(BASE_INVERSE).mul(pose);
   }

   /** A base-space translation as seen from the pose stack root. */
   static Vector3f fromBase(Vector3f direction) {
      return BASE.transformDirection(new Vector3f(direction));
   }

   static Matrix4f cut() {
      return new Matrix4f(CUT);
   }

   static float support() {
      return support;
   }

   /** The sword pass: the blade frame (before its axial roll) in base space. */
   static void publishBlade(Matrix4f bladeInBase) {
      BLADE.set(bladeInBase);
      bladeFrame = frame;
   }

   /** The blade of this frame or the previous one; null when the sword arm was not drawn. */
   @Nullable
   static Matrix4f blade() {
      return frame - bladeFrame <= 1 ? new Matrix4f(BLADE) : null;
   }
}
