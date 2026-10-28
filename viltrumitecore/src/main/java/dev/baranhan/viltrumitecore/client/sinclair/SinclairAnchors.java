package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * World positions of the wand tip and the left palm, recorded while the body renders (bone hook),
 * so sparks, the bolt glow, the BEAM ray and the floating card start at the drawn hand. Without a
 * fresh sample (first person, culled) the server's Stagecraft offsets are the fallback.
 */
public final class SinclairAnchors {
   private static final long FRESH_NANOS = 150_000_000L;
   private static final Map<Integer, Sample> SAMPLES = new HashMap<>();

   private SinclairAnchors() {
   }

   record Sample(Vec3 wandTip, Vec3 leftHand, long at) {
   }

   /** Bone hook recording the anchors of {@code entity} from the level pose stack (camera-rotated). */
   static AnimRenderer.BoneHook recorder(Entity entity) {
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      Vec3[] found = new Vec3[2];
      return (bone, stack, buffers, light, overlay) -> {
         int slot = "wand_tip".equals(bone.name) ? 0 : "left_hand".equals(bone.name) ? 1 : -1;
         if (slot < 0) {
            return;
         }

         found[slot] = world(camera, stack, bone, slot == 1 ? -1.6F : 0.0F);
         Sample old = SAMPLES.get(entity.getId());
         Vec3 wand = found[0] != null ? found[0] : old == null ? null : old.wandTip();
         Vec3 hand = found[1] != null ? found[1] : old == null ? null : old.leftHand();
         if (wand != null && hand != null) {
            SAMPLES.put(entity.getId(), new Sample(wand, hand, Util.getNanos()));
         }
      };
   }

   private static Vec3 world(Camera camera, PoseStack stack, GeoBone bone, float downPx) {
      Vector3f p = stack.last().pose().transformPosition(bone.pivotX / 16.0F, (bone.pivotY + downPx) / 16.0F, bone.pivotZ / 16.0F, new Vector3f());
      return camera.getPosition().add(HeroDefenseContacts.viewToWorld(new Vec3(p.x, p.y, p.z), camera.rotation()));
   }

   /** Wand tip in the world: the drawn bone when fresh, else the Stagecraft estimate. */
   public static Vec3 wandTip(Entity entity, float partialTick) {
      Sample s = fresh(entity);
      return s != null ? s.wandTip() : estimate(entity, partialTick, 0.55, 0.32, -0.22);
   }

   /** Left palm in the world. */
   public static Vec3 leftHand(Entity entity, float partialTick) {
      Sample s = fresh(entity);
      return s != null ? s.leftHand() : estimate(entity, partialTick, 0.45, -0.34, -0.4);
   }

   @Nullable
   private static Sample fresh(Entity entity) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
         return null;
      }

      Sample s = SAMPLES.get(entity.getId());
      return s != null && Util.getNanos() - s.at() < FRESH_NANOS ? s : null;
   }

   /** Same offsets as the server's Stagecraft (eye + look·forward + right·side + up). */
   static Vec3 estimate(Entity entity, float partialTick, double forward, double side, double up) {
      Vec3 eye = entity.getEyePosition(partialTick);
      Vec3 look = entity.getViewVector(partialTick);
      Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
      return eye.add(look.scale(forward)).add(right.scale(side)).add(0.0, up, 0.0);
   }

   public static void clear() {
      SAMPLES.clear();
   }
}
