package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame.Offset;
import javax.annotation.Nullable;

/**
 * Where a grabbed body hangs in Thor's frame (his position and body yaw),
 * the same on the server (authoritative setPos) and on every client (the
 * shared held-target anchor). Continuous over the stages: an action starts
 * at the hold point. Pure.
 */
public final class GrabPose {
   private GrabPose() {
   }

   /** Lifted by the throat, in front of the chest. */
   public static Offset hold(float width, float height) {
      return new Offset(0.75 + width * 0.5, Math.max(0.25, 1.75 - height * 0.85), 0.12);
   }

   /** The body's offset {@code tick} (may be fractional) ticks into the stage. */
   public static Offset at(GrabFlow.Stage stage, @Nullable GrabAction action, double tick, float width, float height) {
      Offset hold = hold(width, height);
      if (stage != GrabFlow.Stage.ACTION || action == null) {
         return hold;
      }

      double release = Math.max(1, action.releaseTick());
      double p = Math.max(0.0, Math.min(1.0, tick / release));
      return switch (action) {
         case SLAM -> {
            // Over the head, then down into the ground in front.
            Offset overhead = new Offset(0.2, 2.0, 0.0);
            Offset ground = new Offset(1.3 + width * 0.5, 0.0, 0.0);
            yield p < 0.6 ? hold.lerp(overhead, SceneFrame.smooth(p / 0.6)) : overhead.lerp(ground, SceneFrame.easeIn((p - 0.6) / 0.4));
         }
         case THROW -> hold.lerp(new Offset(0.25, hold.up() + 0.35, 0.6), SceneFrame.smooth(p));
         case BARRAGE -> {
            int interval = Math.max(1, ThorSceneConfig.barrageInterval);
            double since = tick < interval ? interval : tick % interval;
            double recoil = tick < interval ? 0.0 : Math.max(0.0, 1.0 - since / 2.0);
            yield hold.plus(0.18 * recoil, -0.05 * recoil, 0.0);
         }
      };
   }
}
