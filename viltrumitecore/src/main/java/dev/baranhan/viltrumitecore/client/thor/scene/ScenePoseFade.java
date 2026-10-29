package dev.baranhan.viltrumitecore.client.thor.scene;

import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.thor.scene.PoseBlend;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cross-fade when the pose source of a body switches between a C scene /
 * flight pose and the normal pose (decisions 90, 119: a lethal hit mid-combo
 * flows into the fatality without the combo clip snapping off; the end of a
 * scene and a grab flow back the same way). The last shown pose is kept per
 * body; on a switch the new pose is blended in from it over a few ticks.
 * Runs after every other Thor pose (ThorPoser), client render thread.
 * Fade lengths PLACEHOLDER.
 */
public final class ScenePoseFade {
   /** PLACEHOLDER: ticks to blend into a scene pose / back to the normal pose. */
   static final float FADE_IN = 6.0F;
   static final float FADE_OUT = 8.0F;

   private static final class Track {
      boolean scene;
      float[] last;
      float lastAt = Float.NEGATIVE_INFINITY;
      float[] from;
      float fadeAt;
      float fadeLength;
   }

   private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

   private ScenePoseFade() {
   }

   /** {@code scene}: this frame's pose came from the scene poser. {@code now}: tickCount + partial tick. */
   public static void apply(PlayerModel<?> model, LivingEntity entity, boolean scene, float now) {
      Track track = TRACKS.computeIfAbsent(entity, e -> new Track());
      float[] current = capture(model);
      if (track.last != null && track.scene != scene && now - track.lastAt <= 3.0F) {
         track.from = track.last;
         track.fadeAt = now;
         track.fadeLength = scene ? FADE_IN : FADE_OUT;
      }

      float[] shown = current;
      if (track.from != null) {
         float w = PoseBlend.weight(now - track.fadeAt, track.fadeLength);
         if (w >= 1.0F) {
            track.from = null;
         } else {
            shown = PoseBlend.blend(track.from, current, w);
            write(model, shown);
            PoseRig.copyLayers(model);
         }
      }

      track.scene = scene;
      track.last = shown;
      track.lastAt = now;
   }

   private static ModelPart[] parts(PlayerModel<?> model) {
      return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
   }

   private static float[] capture(PlayerModel<?> model) {
      ModelPart[] parts = parts(model);
      float[] out = new float[parts.length * PoseBlend.STRIDE];
      for (int i = 0; i < parts.length; i++) {
         ModelPart p = parts[i];
         int o = i * PoseBlend.STRIDE;
         out[o] = p.xRot;
         out[o + 1] = p.yRot;
         out[o + 2] = p.zRot;
         out[o + 3] = p.x;
         out[o + 4] = p.y;
         out[o + 5] = p.z;
      }
      return out;
   }

   private static void write(PlayerModel<?> model, float[] pose) {
      ModelPart[] parts = parts(model);
      for (int i = 0; i < parts.length; i++) {
         ModelPart p = parts[i];
         int o = i * PoseBlend.STRIDE;
         p.xRot = pose[o];
         p.yRot = pose[o + 1];
         p.zRot = pose[o + 2];
         p.x = pose[o + 3];
         p.y = pose[o + 4];
         p.z = pose[o + 5];
      }
      PoseRig.markDirty(model);
   }
}
