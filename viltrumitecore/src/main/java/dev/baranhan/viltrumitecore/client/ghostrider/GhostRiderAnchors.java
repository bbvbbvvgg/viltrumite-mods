package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * World points of the drawn Rider recorded while his gear renders (PlayerGeoLayer frame hook): both hands (the chain
 * leaves from the drawn fist), both eye sockets (the Penance Stare) and the flame roots on the skull. Without a fresh
 * sample (first person, culled, inventory preview) an eye-relative estimate is used.
 */
public final class GhostRiderAnchors {
   private static final long FRESH_NANOS = 150_000_000L;
   static final int RIGHT_HAND = 0;
   static final int LEFT_HAND = 1;
   static final int RIGHT_EYE = 2;
   static final int LEFT_EYE = 3;
   /** Flame roots on the cranium (geo px, JSON coordinates; the skull's crown, back and temples). */
   static final float[][] FLAME_ROOTS = {
      {0.0F, 33.0F, -0.6F}, {-2.4F, 32.7F, -1.6F}, {2.4F, 32.7F, -1.6F}, {-2.9F, 32.2F, 1.4F}, {2.9F, 32.2F, 1.4F},
      {0.0F, 32.8F, 2.0F}, {-1.6F, 32.0F, 3.6F}, {1.6F, 32.0F, 3.6F}, {0.0F, 30.6F, 4.4F}, {-4.0F, 30.4F, 1.0F},
      {4.0F, 30.4F, 1.0F}, {-3.4F, 31.6F, -2.2F}, {3.4F, 31.6F, -2.2F}, {0.0F, 32.2F, -2.6F}
   };
   private static final int POINTS = 4 + FLAME_ROOTS.length;
   private static final Map<Integer, Sample> SAMPLES = new HashMap<>();

   private GhostRiderAnchors() {
   }

   private static final class Sample {
      final Vec3[] points = new Vec3[POINTS];
      long handsAt;
      long headAt;
   }

   /** Frame hook of the gear part: records the head and hand points of this entity. */
   static void record(Entity entity, PoseStack frame, String topBone) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || entity.level() != mc.level) {
         return;
      }
      Camera camera = mc.gameRenderer.getMainCamera();
      Matrix4f m = frame.last().pose();
      Sample sample = SAMPLES.computeIfAbsent(entity.getId(), k -> new Sample());
      switch (topBone) {
         case "armorrightarm" -> {
            if (put(sample, RIGHT_HAND, world(camera, m, -6.0F, 11.6F, 0.0F), entity)) {
               sample.handsAt = Util.getNanos();
            }
         }
         case "armorleftarm" -> {
            if (put(sample, LEFT_HAND, world(camera, m, 6.0F, 11.6F, 0.0F), entity)) {
               sample.handsAt = Util.getNanos();
            }
         }
         case "armorhead" -> {
            boolean ok = put(sample, RIGHT_EYE, world(camera, m, -1.8F, 27.9F, -2.4F), entity);
            ok &= put(sample, LEFT_EYE, world(camera, m, 1.8F, 27.9F, -2.4F), entity);
            for (int i = 0; i < FLAME_ROOTS.length; i++) {
               float[] r = FLAME_ROOTS[i];
               ok &= put(sample, 4 + i, world(camera, m, r[0], r[1], r[2]), entity);
            }
            if (ok) {
               sample.headAt = Util.getNanos();
            }
         }
         default -> {
         }
      }
   }

   /** Rejects frames that are not the world render (inventory and portrait previews draw the same layer). */
   private static boolean put(Sample sample, int slot, Vec3 p, Entity entity) {
      if (p.distanceToSqr(entity.position()) > 16.0) {
         return false;
      }
      sample.points[slot] = p;
      return true;
   }

   /** JSON geo point (px) through the part's draw frame to the world (the loader mirrors x). */
   private static Vec3 world(Camera camera, Matrix4f m, float x, float y, float z) {
      Vector3f v = m.transformPosition(-x / 16.0F, y / 16.0F, z / 16.0F, new Vector3f());
      return camera.getPosition().add(HeroDefenseContacts.viewToWorld(new Vec3(v.x, v.y, v.z), camera.rotation()));
   }

   private static boolean firstPerson(Entity entity) {
      Minecraft mc = Minecraft.getInstance();
      return entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
   }

   @Nullable
   private static Sample fresh(Entity entity, boolean head) {
      if (firstPerson(entity)) {
         return null;
      }
      Sample s = SAMPLES.get(entity.getId());
      if (s == null) {
         return null;
      }
      long at = head ? s.headAt : s.handsAt;
      return Util.getNanos() - at < FRESH_NANOS ? s : null;
   }

   public static Vec3 hand(Entity entity, boolean right, float partial) {
      Sample s = fresh(entity, false);
      Vec3 p = s != null ? s.points[right ? RIGHT_HAND : LEFT_HAND] : null;
      return p != null ? p : estimate(entity, partial, 0.45, right ? 0.36 : -0.36, -0.55);
   }

   public static Vec3 eye(Entity entity, boolean right, float partial) {
      Sample s = fresh(entity, true);
      Vec3 p = s != null ? s.points[right ? RIGHT_EYE : LEFT_EYE] : null;
      return p != null ? p : estimate(entity, partial, 0.25, right ? 0.11 : -0.11, 0.0);
   }

   /** Flame roots of the skull this frame, or null without a fresh head sample (then the caller estimates). */
   @Nullable
   public static Vec3[] flameRoots(Entity entity) {
      Sample s = fresh(entity, true);
      if (s == null) {
         return null;
      }
      Vec3[] out = new Vec3[FLAME_ROOTS.length];
      for (int i = 0; i < out.length; i++) {
         out[i] = s.points[4 + i];
         if (out[i] == null) {
            return null;
         }
      }
      return out;
   }

   /** Eye + look * forward + right * side + up. */
   static Vec3 estimate(Entity entity, float partial, double forward, double side, double up) {
      Vec3 eye = entity.getEyePosition(partial);
      Vec3 look = entity.getViewVector(partial);
      Vec3 right = new Vec3(-look.z, 0.0, look.x);
      right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
      return eye.add(look.scale(forward)).add(right.scale(side)).add(0.0, up, 0.0);
   }

   public static void forget(int entityId) {
      SAMPLES.remove(entityId);
   }

   public static void clear() {
      SAMPLES.clear();
   }
}
