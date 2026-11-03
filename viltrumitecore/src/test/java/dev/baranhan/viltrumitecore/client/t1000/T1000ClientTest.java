package dev.baranhan.viltrumitecore.client.t1000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.client.city.CitizenModel;
import dev.baranhan.viltrumitecore.t1000.T1000Action;
import dev.baranhan.viltrumitecore.t1000.T1000Arm;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import dev.baranhan.viltrumitecore.t1000.T1000Rules;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * The T-1000 client without a game: the PoseRig poses of every action (finite, the strikes reach where they
 * should, the disguise adds nothing), the liquid-metal mesh of every form (closed, outward-facing, human-sized,
 * blades longer than hands, the puddle flat, the reveal partial) and the droplet blobs. Also writes a
 * software-rendered preview sheet (matcap shaded, front, 3/4 and side view) to build/t1000-preview/ for eyeballing.
 */
class T1000ClientTest {
   static T1000Model model(boolean slim) {
      T1000Model m = new T1000Model(CitizenModel.create(slim).bakeRoot(), slim);
      m.setAllVisible(true);
      return m;
   }

   /** What HumanoidModel.setupAnim leaves (the vanilla walk) before the posers layer on top. */
   static void vanilla(CitizenModel m, float limbSwing, float limbAmount) {
      PoseRig.restorePivots(m);
      for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
         p.xRot = 0.0F;
         p.yRot = 0.0F;
         p.zRot = 0.0F;
      }
      m.head.x = 0.0F;
      m.head.y = 0.0F;
      m.head.z = 0.0F;
      m.body.x = 0.0F;
      m.body.y = 0.0F;
      m.body.z = 0.0F;
      m.rightArm.x = -5.0F;
      m.rightArm.y = 2.0F;
      m.rightArm.z = 0.0F;
      m.leftArm.x = 5.0F;
      m.leftArm.y = 2.0F;
      m.leftArm.z = 0.0F;
      m.rightLeg.x = -1.9F;
      m.rightLeg.y = 12.0F;
      m.rightLeg.z = 0.0F;
      m.leftLeg.x = 1.9F;
      m.leftLeg.y = 12.0F;
      m.leftLeg.z = 0.0F;
      m.rightArm.xRot = (float)Math.cos(limbSwing * 0.6662F + Math.PI) * limbAmount;
      m.leftArm.xRot = (float)Math.cos(limbSwing * 0.6662F) * limbAmount;
      m.rightLeg.xRot = (float)Math.cos(limbSwing * 0.6662F) * 1.4F * limbAmount;
      m.leftLeg.xRot = (float)Math.cos(limbSwing * 0.6662F + Math.PI) * 1.4F * limbAmount;
   }

   static T1000Model posed(T1000Form form, float formAge, T1000Action action, float t, T1000Arm right, T1000Arm left, float stance, float stiff,
      boolean heated, float limbSwing, float limbAmount) {
      T1000Model m = model(false);
      vanilla(m, limbSwing, limbAmount);
      T1000Poser.apply(m, form, formAge, stance, stiff, action, t, right, left, heated, 1.0F, 0.0F, 1.0F, limbSwing, limbAmount, 40.0F, 7);
      return m;
   }

   static void assertFinite(CitizenModel m, String what) {
      for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
         for (float v : new float[]{p.xRot, p.yRot, p.zRot, p.x, p.y, p.z}) {
            assertTrue(Float.isFinite(v), what + " has a non-finite part value");
         }
      }
   }

   // ------------------------------------------------------------------ poses

   @Test
   void everyActionAndFormPosesFinite() {
      for (T1000Action a : T1000Action.values()) {
         for (float t = 0.0F; t <= Math.max(1, a.ticks); t += 0.5F) {
            for (T1000Form f : new T1000Form[]{T1000Form.LIQUID, T1000Form.REVEALING, T1000Form.REFORMING, T1000Form.DYING}) {
               assertFinite(posed(f, t * 2.0F, a, t, T1000Arm.BLADE, T1000Arm.HOOK, 1.0F, 0.0F, false, t * 0.7F, 0.8F), a + "@" + t + " " + f);
            }
            assertFinite(posed(T1000Form.LIQUID, 10.0F, a, t, T1000Arm.HAND, T1000Arm.HAND, 0.5F, 1.0F, true, t, 1.0F), a + " frozen+heated");
         }
      }
   }

   @Test
   void disguiseAddsNothingToTheCitizen() {
      T1000Model plain = model(false);
      vanilla(plain, 1.3F, 0.6F);
      float[] before = {plain.rightArm.xRot, plain.leftArm.xRot, plain.body.xRot, plain.head.zRot, plain.rightLeg.xRot};
      T1000Poser.apply(plain, T1000Form.HUMAN, 100.0F, 0.0F, 0.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, false, 1.0F, 0.0F, 0.0F, 1.3F, 0.6F,
         40.0F, 7);
      float[] after = {plain.rightArm.xRot, plain.leftArm.xRot, plain.body.xRot, plain.head.zRot, plain.rightLeg.xRot};
      for (int i = 0; i < before.length; i++) {
         assertEquals(before[i], after[i], 1.0E-6F, "disguised pose changed channel " + i);
      }
      assertEquals(1.0F, T1000Model.skinVisibility(T1000Form.HUMAN, 0.0F));
      assertEquals(0.0F, T1000Model.skinVisibility(T1000Form.LIQUID, 1.0F));
      assertEquals(1.0F, T1000Model.extraScale(0.0F), 1.0E-6F);
      assertTrue(T1000Model.extraScale(0.7F) < 0.01F);
   }

   @Test
   void strikesReachWhereTheyShould() {
      float deg = (float)(Math.PI / 180.0);
      T1000Model stab = posed(T1000Form.LIQUID, 50.0F, T1000Action.STAB, T1000Action.STAB.hitTick, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(stab.rightArm.xRot < -80.0F * deg, "stab: blade arm thrust forward, was " + stab.rightArm.xRot / deg);
      T1000Model hook = posed(T1000Form.LIQUID, 50.0F, T1000Action.HOOK, T1000Action.HOOK.hitTick, T1000Arm.BLADE, T1000Arm.HOOK, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(hook.leftArm.xRot < -80.0F * deg, "hook: thrown with the left arm, was " + hook.leftArm.xRot / deg);
      T1000Model grab = posed(T1000Form.LIQUID, 50.0F, T1000Action.GRAB, 20.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(grab.leftArm.xRot < -115.0F * deg, "grab: the victim lifted high, was " + grab.leftArm.xRot / deg);
      T1000Model slash = posed(T1000Form.LIQUID, 50.0F, T1000Action.SLASH, 5.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(slash.rightArm.xRot < -140.0F * deg, "slash: wound up overhead, was " + slash.rightArm.xRot / deg);
      T1000Model tilt = posed(T1000Form.LIQUID, 50.0F, T1000Action.TILT, 15.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(Math.abs(tilt.head.zRot) > 15.0F * deg, "tilt: the head rolls, was " + tilt.head.zRot / deg);
      T1000Model leap = posed(T1000Form.LIQUID, 50.0F, T1000Action.LEAP, 4.0F, T1000Arm.BLADE, T1000Arm.BLADE, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertTrue(leap.body.xRot > 20.0F * deg, "leap: pitched into the jump");
      // every action returns to the pose underneath at its end
      T1000Model end = posed(T1000Form.LIQUID, 50.0F, T1000Action.STAB, T1000Action.STAB.ticks, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      T1000Model none = posed(T1000Form.LIQUID, 50.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      assertEquals(none.rightArm.xRot, end.rightArm.xRot, 1.0E-3F);
   }

   @Test
   void frostLocksTheStride() {
      T1000Model walking = posed(T1000Form.LIQUID, 50.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, false, 2.0F, 1.0F);
      T1000Model frozen = posed(T1000Form.LIQUID, 50.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 1.0F, false, 2.0F, 1.0F);
      assertTrue(Math.abs(walking.rightLeg.xRot) > 0.2F);
      assertEquals(0.0F, frozen.rightLeg.xRot, 1.0E-4F);
   }

   // ------------------------------------------------------------------ mesh

   /** Collects the written vertices: x, y, z, r, g, b, a, u, v, nx, ny, nz. */
   static final class Sink implements VertexConsumer {
      final List<float[]> verts = new ArrayList<>();
      private float[] cur;

      @Override
      public VertexConsumer vertex(double x, double y, double z) {
         this.cur = new float[12];
         this.cur[0] = (float)x;
         this.cur[1] = (float)y;
         this.cur[2] = (float)z;
         return this;
      }

      @Override
      public VertexConsumer color(int r, int g, int b, int a) {
         this.cur[3] = r / 255.0F;
         this.cur[4] = g / 255.0F;
         this.cur[5] = b / 255.0F;
         this.cur[6] = a / 255.0F;
         return this;
      }

      @Override
      public VertexConsumer uv(float u, float v) {
         this.cur[7] = u;
         this.cur[8] = v;
         return this;
      }

      @Override
      public VertexConsumer overlayCoords(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer uv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         this.cur[9] = x;
         this.cur[10] = y;
         this.cur[11] = z;
         return this;
      }

      @Override
      public void endVertex() {
         this.verts.add(this.cur);
      }

      @Override
      public void defaultColor(int r, int g, int b, int a) {
      }

      @Override
      public void unsetDefaultColor() {
      }
   }

   static T1000Body.Frame frame(T1000Form form, float formAge, T1000Action action, float t, T1000Arm right, T1000Arm left) {
      T1000Body.Frame f = new T1000Body.Frame();
      f.time = 200.0F + formAge;
      f.form = form;
      f.formAge = formAge;
      T1000Body.derive(f);
      f.action = action;
      f.actionAge = t;
      f.hitDx = 0.0F;
      f.hitDz = -1.0F;
      f.hitStrength = 1.0F;
      f.hitAge = action == T1000Action.HEAVY || action == T1000Action.HIT ? t : 100.0F;
      f.arms = T1000Arm.pack(right, left, right, left);
      f.morphR = 1.0F;
      f.morphL = 1.0F;
      f.seed = 12345;
      f.originX = 0.0F;
      f.originY = 4.0F;
      f.originZ = -2.2F;
      return f;
   }

   /** Model space (pixels / 16, y down), like the layer gets it before the entity transform. */
   static List<float[]> mesh(T1000Body.Frame f, T1000Model m, int skip) {
      Sink s = new Sink();
      T1000Body.draw(f, m, new PoseStack(), s, 0xF000F0, skip);
      return s.verts;
   }

   /** Farthest vertex from the left shoulder pivot (blocks). */
   static float reach(List<float[]> v) {
      float best = 0.0F;
      for (float[] p : v) {
         if (p[6] < 0.05F) {
            continue;
         }
         float dx = p[0] - 5.0F / 16.0F;
         float dy = p[1] - 2.0F / 16.0F;
         float dz = p[2];
         best = Math.max(best, (float)Math.sqrt(dx * dx + dy * dy + dz * dz));
      }
      return best;
   }

   static float[] bounds(List<float[]> v) {
      float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (float[] p : v) {
         if (p[6] < 0.05F) {
            continue;
         }
         for (int i = 0; i < 3; i++) {
            b[i] = Math.min(b[i], p[i]);
            b[i + 3] = Math.max(b[i + 3], p[i]);
         }
      }
      return b;
   }

   @Test
   void theLiquidBodyIsAClosedHumanSizedShell() {
      T1000Model m = posed(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      List<float[]> v = mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND), m, -1);
      assertTrue(v.size() > 2000 && v.size() % 4 == 0, "quads: " + v.size());
      for (float[] p : v) {
         for (float c : p) {
            assertTrue(Float.isFinite(c), "non-finite vertex");
         }
         float len = (float)Math.sqrt(p[9] * p[9] + p[10] * p[10] + p[11] * p[11]);
         assertEquals(1.0F, len, 0.02F, "normal not unit");
         assertTrue(p[7] >= 0.0F && p[7] <= 1.0F && p[8] >= 0.0F && p[8] <= 1.0F, "matcap uv out of range");
      }
      float[] b = bounds(v);
      float height = b[4] - b[1];
      float width = b[3] - b[0];
      assertTrue(height > 1.8F && height < 2.15F, "height " + height);
      assertTrue(width > 0.9F && width < 1.25F, "width " + width);
   }

   @Test
   void quadsFaceOutward() {
      // the winding must be counter-clockwise from outside (back faces are culled) and agree with the normals
      T1000Model m = posed(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HOOK, 1.0F, 0.0F, false, 0.0F, 0.0F);
      List<float[]> v = mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HOOK), m, -1);
      int agree = 0;
      int total = 0;
      for (int q = 0; q + 3 < v.size(); q += 4) {
         float[] n = faceNormal(v.get(q), v.get(q + 1), v.get(q + 2), v.get(q + 3));
         if (n == null) {
            continue;
         }
         float nx = 0.0F;
         float ny = 0.0F;
         float nz = 0.0F;
         for (int k = 0; k < 4; k++) {
            nx += v.get(q + k)[9];
            ny += v.get(q + k)[10];
            nz += v.get(q + k)[11];
         }
         total++;
         if (n[0] * nx + n[1] * ny + n[2] * nz > 0.0F) {
            agree++;
         }
      }
      for (int part = 0; part < 6; part++) {
         List<float[]> pv = mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HOOK), m, -2 - part);
         int pa = 0;
         int pt = 0;
         for (int q = 0; q + 3 < pv.size(); q += 4) {
            float[] n = faceNormal(pv.get(q), pv.get(q + 1), pv.get(q + 2), pv.get(q + 3));
            if (n == null) {
               continue;
            }
            float nx = 0.0F;
            float ny = 0.0F;
            float nz = 0.0F;
            for (int k = 0; k < 4; k++) {
               nx += pv.get(q + k)[9];
               ny += pv.get(q + k)[10];
               nz += pv.get(q + k)[11];
            }
            pt++;
            if (n[0] * nx + n[1] * ny + n[2] * nz > 0.0F) {
               pa++;
            }
         }
         // every part, including the end caps (poles) of the arms, legs and head
         assertTrue(pa >= pt * 0.99F, "part " + part + ": " + pa + " of " + pt + " quads face their normal");
      }
      assertTrue(agree >= total * 0.99F, agree + " of " + total + " quads face their normal");
   }

   static float[] faceNormal(float[] a, float[] b, float[] c, float[] d) {
      // Newell's method over the quad
      float[][] p = {a, b, c, d};
      float nx = 0.0F;
      float ny = 0.0F;
      float nz = 0.0F;
      for (int i = 0; i < 4; i++) {
         float[] u = p[i];
         float[] w = p[(i + 1) % 4];
         nx += (u[1] - w[1]) * (u[2] + w[2]);
         ny += (u[2] - w[2]) * (u[0] + w[0]);
         nz += (u[0] - w[0]) * (u[1] + w[1]);
      }
      float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      return len < 1.0E-9F ? null : new float[]{nx / len, ny / len, nz / len};
   }

   @Test
   void bladesAndHooksAreLongerThanHands() {
      T1000Model m = posed(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 0.0F, 0.0F, false, 0.0F, 0.0F);
      float hand = bounds(mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND), m, -2 - T1000Body.R_ARM))[4];
      float blade = bounds(mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HAND), m, -2 - T1000Body.R_ARM))[4];
      float spike = bounds(mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.SPIKE, T1000Arm.HAND), m, -2 - T1000Body.R_ARM))[4];
      assertTrue(blade > hand + 0.25F, "blade tip " + blade + " vs hand " + hand);
      assertTrue(spike > hand + 0.15F, "spike tip " + spike + " vs hand " + hand);
      // the hook throw stretches the left arm out
      float rest = reach(mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HOOK), m, -2 - T1000Body.L_ARM));
      T1000Model throwing = posed(T1000Form.LIQUID, 60.0F, T1000Action.HOOK, 9.0F, T1000Arm.HAND, T1000Arm.HOOK, 1.0F, 0.0F, false, 0.0F, 0.0F);
      List<float[]> thrownMesh = mesh(frame(T1000Form.LIQUID, 60.0F, T1000Action.HOOK, 9.0F, T1000Arm.HAND, T1000Arm.HOOK), throwing, -2 - T1000Body.L_ARM);
      float thrown = reach(thrownMesh);
      assertTrue(thrown > rest + 1.5F, "hook reach " + thrown + " vs " + rest);
      // thrown forward (the model faces -z) and the sickle tip does not swing back past the shoulder
      float[] tb = bounds(thrownMesh);
      assertTrue(tb[2] < -1.5F, "hook flies forward, front " + tb[2]);
      assertTrue(tb[5] < 0.4F, "hook does not point backwards, back " + tb[5]);
   }

   @Test
   void thePuddleIsFlatAndWide() {
      T1000Model m = posed(T1000Form.PUDDLE, 40.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, false, 0.0F, 0.0F);
      float[] b = bounds(mesh(frame(T1000Form.PUDDLE, 40.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND), m, -1));
      assertTrue(b[4] - b[1] < 0.35F, "puddle height " + (b[4] - b[1]));
      assertTrue(b[3] - b[0] > 0.9F && b[5] - b[2] > 0.9F, "puddle spread " + (b[3] - b[0]) + " x " + (b[5] - b[2]));
      assertTrue(b[4] > 1.4F, "puddle lies on the ground (y down), lowest " + b[4]);
   }

   @Test
   void theRevealSpreadsAndTheDisguiseHidesTheMetal() {
      T1000Model m = posed(T1000Form.REVEALING, 13.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 0.5F, 0.0F, false, 0.0F, 0.0F);
      List<float[]> v = mesh(frame(T1000Form.REVEALING, T1000Rules.REVEAL_TICKS * 0.5F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND), m, -1);
      int clear = 0;
      int solid = 0;
      for (float[] p : v) {
         if (p[6] < 0.1F) {
            clear++;
         } else if (p[6] > 0.9F) {
            solid++;
         }
      }
      assertTrue(clear > 100 && solid > 100, "mid reveal: " + clear + " clear, " + solid + " covered");
      T1000Body.Frame human = frame(T1000Form.HUMAN, 100.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND);
      assertTrue(T1000Body.invisible(human));
   }

   @Test
   void deathEndsInNothing() {
      T1000Model m = posed(T1000Form.DYING, 79.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.BLADE, 1.0F, 0.0F, false, 0.0F, 0.0F);
      List<float[]> v = mesh(frame(T1000Form.DYING, 79.9F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.BLADE), m, -1);
      float[] b = bounds(v);
      assertTrue(v.isEmpty() || b[3] - b[0] < 0.4F || b[0] == Float.MAX_VALUE, "the last frame of death still shows a body " + (b[3] - b[0]));
   }

   @Test
   void dropletBlobsFaceOutward() {
      for (boolean landed : new boolean[]{false, true}) {
         for (boolean shard : new boolean[]{false, true}) {
            T1000Droplets.Drop d = new T1000Droplets.Drop();
            d.size = 0.05F;
            d.landed = landed;
            d.shard = shard;
            d.vx = 0.1;
            d.vy = -0.2;
            d.age = 20;
            Sink s = new Sink();
            T1000Droplets.blob(s, new Matrix4f(), new Matrix3f(), d, 0.0F, 0.0F, -3.0F, 0xF000F0, 0.0F);
            assertTrue(s.verts.size() >= 32);
            int out = 0;
            int total = 0;
            for (int q = 0; q + 3 < s.verts.size(); q += 4) {
               float[] n = faceNormal(s.verts.get(q), s.verts.get(q + 1), s.verts.get(q + 2), s.verts.get(q + 3));
               if (n == null) {
                  continue;
               }
               float cx = 0.0F;
               float cy = 0.0F;
               float cz = 0.0F;
               for (int k = 0; k < 4; k++) {
                  cx += s.verts.get(q + k)[0] / 4.0F;
                  cy += s.verts.get(q + k)[1] / 4.0F;
                  cz += s.verts.get(q + k)[2] / 4.0F + 3.0F / 4.0F;
               }
               total++;
               if (n[0] * cx + n[1] * cy + n[2] * cz > 0.0F) {
                  out++;
               }
            }
            assertTrue(out > total * 0.9F, "landed=" + landed + " shard=" + shard + ": " + out + "/" + total + " outward");
         }
      }
   }

   @Test
   void matcapIsOpaqueAndHasContrast() {
      int lo = 255;
      int hi = 0;
      for (int y = 8; y < 256; y += 16) {
         for (int x = 8; x < 256; x += 16) {
            int c = T1000Matcap.paint(x, y, 256);
            float dx = (x - 128) / 128.0F;
            float dy = (y - 128) / 128.0F;
            if (dx * dx + dy * dy > 0.95F) {
               continue;
            }
            assertEquals(255, c >>> 24 & 255, "matcap alpha");
            int l = ((c >> 16 & 255) * 3 + (c >> 8 & 255) * 6 + (c & 255)) / 10;
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
         }
      }
      assertTrue(hi - lo > 90, "chrome needs contrast: " + lo + ".." + hi);
   }

   // ------------------------------------------------------------------ preview sheet

   record Shot(String name, T1000Form form, float formAge, T1000Action action, float t, T1000Arm right, T1000Arm left, float stance, float swing,
      float amount) {
   }

   @Test
   void previewSheet() throws Exception {
      Shot[] shots = {
         new Shot("reveal_mid", T1000Form.REVEALING, 15.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 0.6F, 0.0F, 0.0F),
         new Shot("liquid", T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("run", T1000Form.LIQUID, 60.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.BLADE, 1.0F, 2.3F, 1.0F),
         new Shot("stab", T1000Form.LIQUID, 60.0F, T1000Action.STAB, 5.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("slash", T1000Form.LIQUID, 60.0F, T1000Action.SLASH, 7.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("hook", T1000Form.LIQUID, 60.0F, T1000Action.HOOK, 9.0F, T1000Arm.BLADE, T1000Arm.HOOK, 1.0F, 0.0F, 0.0F),
         new Shot("grab", T1000Form.LIQUID, 60.0F, T1000Action.GRAB, 16.0F, T1000Arm.BLADE, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("split", T1000Form.LIQUID, 60.0F, T1000Action.FACE_SPLIT, 8.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("heavy", T1000Form.LIQUID, 60.0F, T1000Action.HEAVY, 3.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("melting", T1000Form.MELTING, 8.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("puddle", T1000Form.PUDDLE, 30.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("reform", T1000Form.REFORMING, 15.0F, T1000Action.NONE, 0.0F, T1000Arm.HAND, T1000Arm.HAND, 1.0F, 0.0F, 0.0F),
         new Shot("dying20", T1000Form.DYING, 20.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.BLADE, 1.0F, 0.0F, 0.0F),
         new Shot("dying50", T1000Form.DYING, 50.0F, T1000Action.NONE, 0.0F, T1000Arm.BLADE, T1000Arm.BLADE, 1.0F, 0.0F, 0.0F),
      };
      int cw = 130;
      int ch = 210;
      float[] views = {0.0F, 38.0F, 90.0F};
      BufferedImage sheet = new BufferedImage(cw * views.length * 7, ch * 2, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < shots.length; i++) {
         Shot s = shots[i];
         for (int view = 0; view < views.length; view++) {
            T1000Model m = posed(s.form, s.formAge, s.action, s.t, s.right, s.left, s.stance, 0.0F, false, s.swing, s.amount);
            T1000Body.Frame f = frame(s.form, s.formAge, s.action, s.t, s.right, s.left);
            PoseStack pose = new PoseStack();
            pose.translate(0.0F, -0.95F, -5.0F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F + views[view]));
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0.0F, -1.501F, 0.0F);
            Sink sink = new Sink();
            T1000Body.draw(f, m, pose, sink, 0xF000F0, -1);
            int col = i % 7;
            int row = i / 7;
            raster(sheet, (col * views.length + view) * cw, row * ch, cw, ch, sink.verts);
         }
      }
      File dir = new File("build/t1000-preview");
      dir.mkdirs();
      ImageIO.write(sheet, "png", new File(dir, "t1000_sheet.png"));
   }

   private static void raster(BufferedImage img, int ox, int oy, int w, int h, List<float[]> v) {
      float[] z = new float[w * h];
      java.util.Arrays.fill(z, -Float.MAX_VALUE);
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            img.setRGB(ox + x, oy + y, (x + y) / 6 % 2 == 0 ? 0x5A6B4A : 0x637452);
         }
      }
      float scale = 72.0F;
      for (int q = 0; q + 3 < v.size(); q += 4) {
         tri(img, z, ox, oy, w, h, scale, v.get(q), v.get(q + 1), v.get(q + 2));
         tri(img, z, ox, oy, w, h, scale, v.get(q + 2), v.get(q + 3), v.get(q));
      }
   }

   private static void tri(BufferedImage img, float[] zb, int ox, int oy, int w, int h, float scale, float[] a, float[] b, float[] c) {
      float ax = w * 0.5F + a[0] * scale;
      float ay = h * 0.5F - a[1] * scale;
      float bx = w * 0.5F + b[0] * scale;
      float by = h * 0.5F - b[1] * scale;
      float cx = w * 0.5F + c[0] * scale;
      float cy = h * 0.5F - c[1] * scale;
      float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
      if (area >= 0.0F) {
         return; // back face (screen y is flipped: counter-clockwise in view space is negative here)
      }
      int minX = Math.max(0, (int)Math.floor(Math.min(ax, Math.min(bx, cx))));
      int maxX = Math.min(w - 1, (int)Math.ceil(Math.max(ax, Math.max(bx, cx))));
      int minY = Math.max(0, (int)Math.floor(Math.min(ay, Math.min(by, cy))));
      int maxY = Math.min(h - 1, (int)Math.ceil(Math.max(ay, Math.max(by, cy))));
      for (int y = minY; y <= maxY; y++) {
         for (int x = minX; x <= maxX; x++) {
            float px = x + 0.5F;
            float py = y + 0.5F;
            float w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
            float w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
            float w2 = 1.0F - w0 - w1;
            if (w0 < 0.0F || w1 < 0.0F || w2 < 0.0F) {
               continue;
            }
            float depth = w0 * a[2] + w1 * b[2] + w2 * c[2];
            float alpha = w0 * a[6] + w1 * b[6] + w2 * c[6];
            int idx = y * w + x;
            if (alpha < 0.35F || depth <= zb[idx]) {
               continue;
            }
            zb[idx] = depth;
            float u = w0 * a[7] + w1 * b[7] + w2 * c[7];
            float vv = w0 * a[8] + w1 * b[8] + w2 * c[8];
            int t = T1000Matcap.paint(Math.min(255, (int)(u * 256)), Math.min(255, (int)(vv * 256)), 256);
            float r = (t >> 16 & 255) * (w0 * a[3] + w1 * b[3] + w2 * c[3]);
            float g = (t >> 8 & 255) * (w0 * a[4] + w1 * b[4] + w2 * c[4]);
            float bl = (t & 255) * (w0 * a[5] + w1 * b[5] + w2 * c[5]);
            int under = img.getRGB(ox + x, oy + y);
            float k = Math.min(1.0F, alpha);
            int rr = (int)(r * k + (under >> 16 & 255) * (1.0F - k));
            int gg = (int)(g * k + (under >> 8 & 255) * (1.0F - k));
            int bb = (int)(bl * k + (under & 255) * (1.0F - k));
            img.setRGB(ox + x, oy + y, Math.min(255, rr) << 16 | Math.min(255, gg) << 8 | Math.min(255, bb));
         }
      }
   }
}
