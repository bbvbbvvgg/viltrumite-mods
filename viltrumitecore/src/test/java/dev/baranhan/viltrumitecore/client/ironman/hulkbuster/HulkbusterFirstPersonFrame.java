package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Test-only replica of the first-person arm path of HulkbusterRenderer: RenderHandEvent stack (no bob,
 * no sway) → {@link HulkbusterFirstPerson#place} → vanilla ItemInHandRenderer.renderPlayerArm (1.20.1,
 * equip 0, swing 0) → arm ModelPart.translateAndRotate (the rest pose of onRenderArm + the thrust) →
 * PlayerGeoLayer.partFrame → AnimRenderer bone chain. Gives camera-space vertices and their screen
 * position under the hand projection (GameRenderer.renderItemInHand: fixed 70° FOV, near 0.05).
 */
final class HulkbusterFirstPersonFrame {
   /** GameRenderer.renderItemInHand always uses getFov(camera, partial, false) = 70 degrees. */
   static final float HAND_FOV = 70.0F;
   static final float NEAR = 0.05F;
   static final float ASPECT = 16.0F / 9.0F;

   private HulkbusterFirstPersonFrame() {
   }

   /** Camera-space points of every cube vertex of one arm chain (bones already posed). */
   static List<Vector3f> arm(BakedGeoModel geo, boolean right, float push) {
      return arm(geo, right, push, s -> HulkbusterFirstPerson.place(s, right));
   }

   static List<Vector3f> arm(BakedGeoModel geo, boolean right, float push, java.util.function.Consumer<PoseStack> place) {
      PoseStack stack = new PoseStack();
      place.accept(stack);
      vanillaRenderPlayerArm(stack, right);
      HulkbusterFirstPerson.Rest rest = HulkbusterFirstPerson.rest(right);
      float x = rest.x(), y = rest.y(), z = rest.z();
      if (push != 0.0F) {
         Vector3f along = new Quaternionf().rotationZYX(rest.zRot(), rest.yRot(), rest.xRot()).transform(new Vector3f(0.0F, push, 0.0F));
         x += along.x;
         y += along.y;
         z += along.z;
      }

      // ModelPart.translateAndRotate
      stack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
      stack.mulPose(new Quaternionf().rotationZYX(rest.zRot(), rest.yRot(), rest.xRot()));
      // PlayerGeoLayer.partFrame (arm pivots -5/5, 2, 0; MODEL_HEIGHT 1.5)
      stack.translate((right ? 5.0F : -5.0F) / 16.0F, -2.0F / 16.0F, 0.0F);
      stack.translate(0.0F, 1.5F, 0.0F);
      stack.scale(-1.0F, -1.0F, 1.0F);
      List<Vector3f> out = new ArrayList<>();
      GeoBone root = geo.getBone(right ? HulkbusterAssets.SHOULDER_R : HulkbusterAssets.SHOULDER_L);
      collect(root, stack, out);
      return out;
   }

   /** ItemInHandRenderer.renderPlayerArm, 1.20.1, swing 0 and equip 0. */
   static void vanillaRenderPlayerArm(PoseStack ps, boolean right) {
      float f = right ? 1.0F : -1.0F;
      ps.translate(f * 0.64000005F, -0.6F, -0.71999997F);
      ps.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
      ps.translate(f * -1.0F, 3.6F, 3.5F);
      ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
      ps.mulPose(Axis.XP.rotationDegrees(200.0F));
      ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
      ps.translate(f * 5.6F, 0.0F, 0.0F);
   }

   /** When set, collect() also appends each quad's four camera-space corners (CPU preview export). */
   static List<Vector3f[]> quadSink;

   private static void collect(GeoBone bone, PoseStack stack, List<Vector3f> out) {
      if (bone == null) {
         return;
      }

      stack.pushPose();
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      stack.mulPose(Axis.YP.rotation(bone.rotY));
      stack.mulPose(Axis.XP.rotation(bone.rotX));
      stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
      stack.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
      boolean flame = bone.name.startsWith(HulkbusterAssets.FLAME_PREFIX);
      if (!bone.hidden && !flame) {
         for (GeoBone.Cube cube : bone.cubes) {
            Matrix4f m = new Matrix4f(stack.last().pose());
            if (cube.hasRotation()) {
               m.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x)
                  .translate(new Vector3f(cube.pivot()).negate());
            }

            for (GeoBone.Quad quad : cube.quads()) {
               if (quad == null) {
                  continue;
               }

               GeoBone.Vertex[] v = quad.vertices();
               if (quadSink != null) {
                  Vector3f[] corners = new Vector3f[v.length];
                  for (int c = 0; c < v.length; c++) {
                     corners[c] = m.transformPosition(new Vector3f(v[c].x(), v[c].y(), v[c].z()));
                  }

                  quadSink.add(corners);
               }

               // 5 x 5 bilinear samples: a quad spanning the centre band counts even with its corners outside.
               for (int i = 0; i <= 4; i++) {
                  for (int j = 0; j <= 4; j++) {
                     float a = i / 4.0F, b = j / 4.0F;
                     float px = lerp(b, lerp(a, v[0].x(), v[1].x()), lerp(a, v[3].x(), v[2].x()));
                     float py = lerp(b, lerp(a, v[0].y(), v[1].y()), lerp(a, v[3].y(), v[2].y()));
                     float pz = lerp(b, lerp(a, v[0].z(), v[1].z()), lerp(a, v[3].z(), v[2].z()));
                     out.add(m.transformPosition(new Vector3f(px, py, pz)));
                  }
               }
            }
         }
      }

      if (!flame) {
         for (GeoBone child : bone.children) {
            collect(child, stack, out);
         }
      }

      stack.popPose();
   }

   private static float lerp(float t, float a, float b) {
      return a + (b - a) * t;
   }

   /** Screen position in NDC (-1..1, y up) under the hand projection; null behind the near plane. */
   static float[] ndc(Vector3f p) {
      if (-p.z < NEAR) {
         return null;
      }

      float t = (float)Math.tan(Math.toRadians(HAND_FOV / 2.0F));
      return new float[]{p.x / -p.z / (t * ASPECT), p.y / -p.z / t};
   }

   /** Metrics of one frame: highest covered NDC y outside / inside the centre band, nearest depth. */
   record Metrics(float edgeTop, float centreTop, float nearest, float centreMinX) {
   }

   static Metrics metrics(List<Vector3f> points) {
      float edgeTop = -1.0F, centreTop = -1.0F, nearest = Float.MAX_VALUE, centreMinX = Float.MAX_VALUE;
      for (Vector3f p : points) {
         nearest = Math.min(nearest, -p.z);
         float[] s = ndc(p);
         if (s == null) {
            continue;
         }

         float y = Math.max(-1.0F, Math.min(1.0F, s[1]));
         if (Math.abs(s[0]) < 0.5F) {
            centreTop = Math.max(centreTop, y);
            centreMinX = Math.min(centreMinX, Math.abs(s[0]));
         } else if (Math.abs(s[0]) <= 1.0F) {
            edgeTop = Math.max(edgeTop, y);
         }
      }

      return new Metrics(edgeTop, centreTop, nearest, centreMinX);
   }
}
