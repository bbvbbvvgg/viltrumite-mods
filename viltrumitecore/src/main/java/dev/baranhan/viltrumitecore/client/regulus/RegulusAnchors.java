package dev.baranhan.viltrumitecore.client.regulus;

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
 * World positions of both palms and the heart, recorded while the body renders (bone hook), so the
 * stilled dust, the sand and the cut leave from the drawn hand. Without a fresh sample (first
 * person, culled) an eye-relative estimate is used.
 */
public final class RegulusAnchors {
   private static final long FRESH_NANOS = 150_000_000L;
   private static final Map<Integer, Vec3[]> POINTS = new HashMap<>();
   private static final Map<Integer, Long> AT = new HashMap<>();
   static final int RIGHT = 0;
   static final int LEFT = 1;
   static final int HEART = 2;

   private RegulusAnchors() {
   }

   static AnimRenderer.BoneHook recorder(Entity entity) {
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      return (bone, stack, buffers, light, overlay) -> {
         int slot = switch (bone.name) {
            case "right_palm" -> RIGHT;
            case "left_palm" -> LEFT;
            case "heart" -> HEART;
            default -> -1;
         };
         if (slot >= 0) {
            Vec3[] points = POINTS.computeIfAbsent(entity.getId(), k -> new Vec3[3]);
            points[slot] = world(camera, stack, bone);
            AT.put(entity.getId(), Util.getNanos());
         }
      };
   }

   private static Vec3 world(Camera camera, PoseStack stack, GeoBone bone) {
      Vector3f p = stack.last().pose().transformPosition(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, new Vector3f());
      return camera.getPosition().add(HeroDefenseContacts.viewToWorld(new Vec3(p.x, p.y, p.z), camera.rotation()));
   }

   public static Vec3 rightHand(Entity entity, float partialTick) {
      Vec3 p = fresh(entity, RIGHT);
      return p != null ? p : estimate(entity, partialTick, 0.5, 0.32, -0.45);
   }

   public static Vec3 leftHand(Entity entity, float partialTick) {
      Vec3 p = fresh(entity, LEFT);
      return p != null ? p : estimate(entity, partialTick, 0.5, -0.32, -0.45);
   }

   public static Vec3 heart(Entity entity, float partialTick) {
      Vec3 p = fresh(entity, HEART);
      return p != null ? p : entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.68, 0.0);
   }

   @Nullable
   private static Vec3 fresh(Entity entity, int slot) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
         return null;
      }

      Long at = AT.get(entity.getId());
      Vec3[] points = POINTS.get(entity.getId());
      return at != null && points != null && Util.getNanos() - at < FRESH_NANOS ? points[slot] : null;
   }

   /** Eye + look·forward + right·side + up. */
   static Vec3 estimate(Entity entity, float partialTick, double forward, double side, double up) {
      Vec3 eye = entity.getEyePosition(partialTick);
      Vec3 look = entity.getViewVector(partialTick);
      Vec3 right = new Vec3(-look.z, 0.0, look.x);
      right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
      return eye.add(look.scale(forward)).add(right.scale(side)).add(0.0, up, 0.0);
   }

   public static void clear() {
      POINTS.clear();
      AT.clear();
   }
}
