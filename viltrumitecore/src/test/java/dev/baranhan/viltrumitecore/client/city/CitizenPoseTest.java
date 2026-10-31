package dev.baranhan.viltrumitecore.client.city;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenPainter;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

/**
 * Every citizen state poses the real model into the intended silhouette (arms over the head when
 * hiding, leaning into the run, arms thrown out when knocked down...), never NaN. Also writes a
 * software-rendered preview sheet to build/citizen-preview/ (front and 3/4 views per state) for
 * eyeballing the looks and poses without a game client.
 */
class CitizenPoseTest {
   private static final CitizenState[] STATES = CitizenState.values();

   private static CitizenModel posed(CitizenState s, int seed, float elapsed, float limbSwing, float limbAmount) {
      CitizenLook look = CitizenLook.of(seed);
      CitizenModel m = new CitizenModel(CitizenModel.create(look.slim()).bakeRoot(), look.slim());
      m.prepare(look);
      m.crouching = s == CitizenState.COVER;
      m.setAllVisible(true);
      m.prepare(look);
      PoseRig.restorePivots(m);
      resetVanilla(m, limbSwing, limbAmount);
      float[] w = new float[STATES.length];
      w[s.ordinal()] = 1.0F;
      CitizenPoser.apply(m, w, CitizenPoser.NONE, 0.0F, s, elapsed, seed, look, limbSwing, limbAmount, 40.0F);
      return m;
   }

   /** The vanilla humanoid walk the poser layers on (what HumanoidModel.setupAnim leaves). */
   private static void resetVanilla(CitizenModel m, float limbSwing, float limbAmount) {
      for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
         p.xRot = 0.0F;
         p.yRot = 0.0F;
         p.zRot = 0.0F;
      }
      m.rightArm.xRot = (float)Math.cos(limbSwing * 0.6662F + Math.PI) * limbAmount;
      m.leftArm.xRot = (float)Math.cos(limbSwing * 0.6662F) * limbAmount;
      m.rightLeg.xRot = (float)Math.cos(limbSwing * 0.6662F) * 1.4F * limbAmount;
      m.leftLeg.xRot = (float)Math.cos(limbSwing * 0.6662F + Math.PI) * 1.4F * limbAmount;
      if (m.crouching) {
         m.body.xRot = 0.5F;
         m.rightArm.xRot += 0.4F;
         m.leftArm.xRot += 0.4F;
         m.rightLeg.z = 4.0F;
         m.leftLeg.z = 4.0F;
         m.rightLeg.y = 12.2F;
         m.leftLeg.y = 12.2F;
         m.head.y = 4.2F;
         m.body.y = 3.2F;
         m.leftArm.y = 5.2F;
         m.rightArm.y = 5.2F;
      }
   }

   private static void finite(CitizenModel m, String what) {
      for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.ponytail}) {
         for (float v : new float[]{p.xRot, p.yRot, p.zRot, p.x, p.y, p.z}) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v), what);
         }
      }
   }

   @Test
   void statesPoseTheIntendedSilhouettes() {
      for (CitizenState s : STATES) {
         for (int seed : new int[]{0, 0x1800, 0x2400, -1, 12345}) {
            finite(posed(s, seed, 5.0F, 3.0F, 0.6F), s + " " + seed);
         }
      }

      CitizenModel cover = posed(CitizenState.COVER, 0, 10.0F, 0.0F, 0.0F);
      // the rig keeps crouch poses relative to the bent torso (crouch pitch added): -150° on a crouch reads ~ -127°
      assertTrue(cover.rightArm.xRot < -2.0F && cover.leftArm.xRot < -2.0F, "hiding: arms over the head");
      assertTrue(cover.head.xRot > 0.3F, "hiding: head down");
      CitizenModel run = posed(CitizenState.FLEEING, 0, 10.0F, 3.0F, 0.9F);
      assertTrue(run.body.xRot > 0.2F, "running: leaning forward");
      CitizenModel handsUp = posed(CitizenState.FLEEING, 0x1800, 10.0F, 3.0F, 0.9F);
      assertTrue(handsUp.rightArm.xRot < -2.4F, "running with the hands over the head");
      CitizenModel flinch = posed(CitizenState.STARTLED, 0, 3.0F, 0.0F, 0.0F);
      assertTrue(flinch.rightArm.xRot < -1.6F && flinch.leftArm.xRot < -1.6F, "startled: arms up in front of the face");
      assertTrue(flinch.body.xRot < 0.0F, "startled: leaning back");
      CitizenModel down = posed(CitizenState.FALLEN, 0, 10.0F, 0.0F, 0.0F);
      assertTrue(down.rightArm.zRot > 0.8F && down.leftArm.zRot < -0.8F, "knocked down: arms thrown out");
      CitizenModel shade = posed(CitizenState.ALERT, 0x400, 10.0F, 0.0F, 0.0F);
      assertTrue(shade.rightArm.xRot < -1.9F, "alert: shading the eyes");
      CitizenModel up = posed(CitizenState.GETTING_UP, 0, 6.0F, 0.0F, 0.0F);
      assertTrue(up.body.xRot > 0.35F, "getting up: pushing off, leaning over the knees");
   }

   // ------------------------------------------------------------------ preview sheet

   /** Collects the posed quads in model space (y down) with their texture coordinates. */
   static final class Quads implements VertexConsumer {
      final List<float[]> verts = new ArrayList<>();
      private float[] cur;

      @Override
      public VertexConsumer vertex(double x, double y, double z) {
         this.cur = new float[]{(float)x, (float)y, (float)z, 0.0F, 0.0F};
         return this;
      }

      @Override
      public VertexConsumer color(int r, int g, int b, int a) {
         return this;
      }

      @Override
      public VertexConsumer uv(float u, float v) {
         this.cur[3] = u;
         this.cur[4] = v;
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

   private static void draw(BufferedImage img, float[] zbuf, int ox, int oy, float scale, double yaw, List<float[]> v, int[] tex) {
      int w = img.getWidth();
      double cy = Math.cos(yaw);
      double sy = Math.sin(yaw);
      for (int q = 0; q + 3 < v.size(); q += 4) {
         float[][] p = new float[4][];
         for (int i = 0; i < 4; i++) {
            float[] a = v.get(q + i);
            double x = a[0] * cy + a[2] * sy;
            double z = -a[0] * sy + a[2] * cy;
            // model space: -x is the citizen's right (seen on the left from the front), y down, -z front
            p[i] = new float[]{(float)(ox + x * scale), (float)(oy + a[1] * scale), (float)z, a[3], a[4]};
         }
         tri(img, zbuf, w, p[0], p[1], p[2], tex);
         tri(img, zbuf, w, p[0], p[2], p[3], tex);
      }
   }

   private static void tri(BufferedImage img, float[] zbuf, int w, float[] a, float[] b, float[] c, int[] tex) {
      int minX = (int)Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
      int maxX = (int)Math.min(w - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
      int minY = (int)Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
      int maxY = (int)Math.min(img.getHeight() - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
      float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (Math.abs(area) < 1.0E-6F) {
         return;
      }
      for (int y = minY; y <= maxY; y++) {
         for (int x = minX; x <= maxX; x++) {
            float px = x + 0.5F;
            float py = y + 0.5F;
            float w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
            float w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
            float w2 = 1.0F - w0 - w1;
            if (w0 < 0 || w1 < 0 || w2 < 0) {
               continue;
            }
            float z = w0 * a[2] + w1 * b[2] + w2 * c[2];
            int idx = y * w + x;
            if (z >= zbuf[idx]) {
               continue;
            }
            float u = w0 * a[3] + w1 * b[3] + w2 * c[3];
            float vv = w0 * a[4] + w1 * b[4] + w2 * c[4];
            int tx = Math.min(CitizenPainter.W - 1, Math.max(0, (int)(u * CitizenPainter.W)));
            int ty = Math.min(CitizenPainter.H - 1, Math.max(0, (int)(vv * CitizenPainter.H)));
            int argb = tex[ty * CitizenPainter.W + tx];
            if (argb >>> 24 < 16) {
               continue;
            }
            zbuf[idx] = z;
            img.setRGB(x, y, argb | 0xFF000000);
         }
      }
   }

   @Test
   void previewSheet() throws Exception {
      int cell = 150;
      int[] seeds = {0, 0x1800, 0x2400, 0x7F3A1C55, 0x12345678, 0xCAFEBABE};
      BufferedImage img = new BufferedImage(cell * STATES.length, cell * 2 * seeds.length, BufferedImage.TYPE_INT_ARGB);
      float[] zbuf = new float[img.getWidth() * img.getHeight()];
      java.util.Arrays.fill(zbuf, Float.MAX_VALUE);
      for (int i = 0; i < img.getWidth(); i++) {
         for (int j = 0; j < img.getHeight(); j++) {
            img.setRGB(i, j, ((i / cell + j / cell) & 1) == 0 ? 0xFFD8DEE6 : 0xFFC8CED6);
         }
      }
      for (int r = 0; r < seeds.length; r++) {
         CitizenLook look = CitizenLook.of(seeds[r]);
         int[] tex = CitizenPainter.paint(look);
         for (int col = 0; col < STATES.length; col++) {
            CitizenState s = STATES[col];
            boolean moving = s == CitizenState.WALKING || s == CitizenState.FLEEING || s == CitizenState.RECOVERING;
            CitizenModel m = posed(s, seeds[r], 6.0F, moving ? 2.2F : 0.0F, moving ? 0.8F : 0.0F);
            for (int view = 0; view < 2; view++) {
               Quads qs = new Quads();
               m.renderToBuffer(new PoseStack(), qs, 0xF000F0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
               int ox = col * cell + cell / 2;
               int oy = (r * 2 + view) * cell + 4 + (int)(0.56F * 70.0F * look.height());
               draw(img, zbuf, ox, oy, 70.0F * look.height(), view == 0 ? 0.0 : Math.PI * 0.25, qs.verts, tex);
            }
         }
      }
      File dir = new File("build/citizen-preview");
      dir.mkdirs();
      ImageIO.write(img, "png", new File(dir, "states.png"));
      BufferedImage t = new BufferedImage(CitizenPainter.W, CitizenPainter.H * seeds.length, BufferedImage.TYPE_INT_ARGB);
      for (int r = 0; r < seeds.length; r++) {
         int[] tex = CitizenPainter.paint(CitizenLook.of(seeds[r]));
         for (int y = 0; y < CitizenPainter.H; y++) {
            for (int x = 0; x < CitizenPainter.W; x++) {
               t.setRGB(x, r * CitizenPainter.H + y, tex[y * CitizenPainter.W + x]);
            }
         }
      }
      ImageIO.write(t, "png", new File(dir, "textures.png"));
      assertTrue(new File(dir, "states.png").length() > 1000);
   }
}
