package dev.baranhan.viltrumitecore.client.t1000;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.city.CitizenModel;
import dev.baranhan.viltrumitecore.t1000.T1000Action;
import dev.baranhan.viltrumitecore.t1000.T1000Arm;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import dev.baranhan.viltrumitecore.t1000.T1000Rules;
import dev.baranhan.viltrumitecore.t1000.T1000WoundMath;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The procedural liquid-metal body (docs/design/2026-11-06-t1000-design.md §1). Every frame it builds one closed
 * tube mesh per humanoid part from {@link T1000Shapes}, in the part's rest space, deforms it there (wounds, face
 * relief, arm weapons, the head split, the reveal wave), moves it into the model root with the part's posed
 * transform — so the chrome follows exactly the citizen pose of {@link T1000Poser} — deforms it again in the root
 * (hit ripples and dents, the squeeze through bars, melting into the puddle, the death throes) and writes it with
 * matcap UVs ({@link T1000Matcap}). Everything derives from synced state and the game time, so all players see the
 * same frame. Render thread only (shared scratch buffers).
 */
final class T1000Body {
   static final int HEAD = T1000Entity.PART_HEAD;
   static final int BODY = T1000Entity.PART_BODY;
   static final int R_ARM = T1000Entity.PART_RIGHT_ARM;
   static final int L_ARM = T1000Entity.PART_LEFT_ARM;
   static final int R_LEG = T1000Entity.PART_RIGHT_LEG;
   static final int L_LEG = T1000Entity.PART_LEFT_LEG;
   private static final int MAX = (T1000Shapes.ARM_RINGS + 1) * T1000Shapes.HEAD_COLS + 64;

   // per-vertex scratch: root-space position (px), normal, alpha, shade, rest distance
   private static final float[] PX = new float[MAX];
   private static final float[] PY = new float[MAX];
   private static final float[] PZ = new float[MAX];
   private static final float[] NX = new float[MAX];
   private static final float[] NY = new float[MAX];
   private static final float[] NZ = new float[MAX];
   private static final float[] AL = new float[MAX];
   private static final float[] SH = new float[MAX];
   private static final float[] HH = new float[MAX];
   private static final float[] SEC = new float[2];
   private static final float[] SLUMP = new float[2];
   private static final float[] RING = new float[6];
   private static final float[] RING_B = new float[6];
   private static final float[] UV = new float[2];
   private static final Vector4f V4 = new Vector4f();
   private static final Vector3f V3 = new Vector3f();
   private static final PoseStack PART_STACK = new PoseStack();
   private static final Matrix4f PART = new Matrix4f();

   private T1000Body() {
   }

   /** Everything one frame of the body depends on (synced state sampled at the partial tick). */
   static final class Frame {
      T1000Entity e;
      float time;
      T1000Form form;
      float formAge;
      float metal;
      float settle;
      float melt;
      boolean human;
      T1000Action action;
      float actionAge;
      float hitDx;
      float hitDz;
      float hitStrength;
      float hitAge;
      boolean frozen;
      boolean heated;
      boolean flowing;
      boolean slim;
      int seed;
      float originX;
      float originY;
      float originZ;
      float deathShrink = 1.0F;
      /** Packed arms byte and the morph progress of each arm (0 before .. 1 now). */
      int arms;
      float morphR = 1.0F;
      float morphL = 1.0F;
      final float[][] wounds = new float[T1000Rules.MAX_WOUNDS][];
      int woundCount;
   }

   private static final Frame FRAME = new Frame();

   static Frame frame(T1000Entity e, float partialTick, boolean slim) {
      Frame f = FRAME;
      f.e = e;
      f.time = e.tickCount + partialTick;
      f.form = e.form();
      f.formAge = e.formAge(partialTick);
      derive(f);
      f.action = e.acting() ? e.action() : T1000Action.NONE;
      f.actionAge = e.actionAge(partialTick);
      float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
      float alpha = (float)Math.toRadians(e.lastHitYaw() - bodyYaw);
      f.hitDx = -Mth.sin(alpha);
      f.hitDz = -Mth.cos(alpha);
      f.hitStrength = e.lastHitStrength();
      f.hitAge = e.hitAge(partialTick);
      int flags = e.flags();
      f.frozen = (flags & T1000Entity.FLAG_FROZEN) != 0;
      f.heated = (flags & T1000Entity.FLAG_HEATED) != 0;
      f.flowing = (flags & T1000Entity.FLAG_FLOWING) != 0 || f.action == T1000Action.FLOW;
      f.slim = slim;
      f.arms = e.arms();
      f.morphR = e.armMorph(true, partialTick);
      f.morphL = e.armMorph(false, partialTick);
      f.seed = e.getId() * 0x9E3779B1;
      // wounds: rest-model position, size, open fraction, kind
      List<T1000Entity.Wound> list = e.wounds();
      int now = (int)e.level().getGameTime();
      f.woundCount = 0;
      T1000Entity.Wound newest = null;
      for (T1000Entity.Wound w : list) {
         float age = now - w.start() + partialTick;
         float open = T1000Shapes.woundOpen(age, T1000Rules.WOUND_CLOSE_TICKS);
         if (open <= 0.001F || f.woundCount >= f.wounds.length) {
            continue;
         }
         f.wounds[f.woundCount++] = new float[]{w.part(), w.x(), w.y(), w.z(), Math.max(0.6F, w.size()), open, w.kind(), w.start()};
         if (newest == null || w.start() > newest.start()) {
            newest = w;
         }
      }
      // the chrome spreads from the newest wound (the hit that made it show itself) or from the chest
      if (newest != null && f.form == T1000Form.REVEALING) {
         f.originX = newest.x();
         f.originY = newest.y();
         f.originZ = newest.z();
      } else {
         f.originX = 0.0F;
         f.originY = 4.0F;
         f.originZ = -2.2F;
      }
      return f;
   }

   /** The blends that follow from the form and its age alone (metal, melt, settle, death shrink). Pure. */
   static void derive(Frame f) {
      f.metal = f.form.metal(f.formAge);
      f.melt = f.form.melt(f.formAge);
      f.human = f.form == T1000Form.HUMAN;
      f.settle = switch (f.form) {
         case HUMAN, REVEALING -> 0.0F;
         case LIQUID -> T1000Shapes.smooth(f.formAge / 14.0F);
         case CONCEALING -> 1.0F - T1000Shapes.smooth(f.formAge / 8.0F);
         default -> 1.0F;
      };
      f.deathShrink = 1.0F;
      if (f.form == T1000Form.DYING) {
         f.deathShrink = 1.0F - T1000Shapes.smooth((f.formAge - (T1000Rules.DEATH_TICKS - 22)) / 20.0F);
      }
   }

   /** True when nothing of the metal is visible this frame (plain citizen). */
   static boolean invisible(Frame f) {
      return f.human && f.woundCount == 0;
   }

   static void render(Frame f, CitizenModel model, PoseStack pose, MultiBufferSource buffers, int light) {
      if (invisible(f)) {
         return;
      }
      boolean split = f.action == T1000Action.FACE_SPLIT;
      // the split head shows its inside: no back-face culling for that part only (fetched last: one batch switch)
      VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucentCull(T1000Matcap.texture()));
      draw(f, model, pose, solid, light, split ? HEAD : -1);
      if (split) {
         VertexConsumer open = buffers.getBuffer(RenderType.entityTranslucent(T1000Matcap.texture()));
         draw(f, model, pose, open, light, -2 - HEAD);
      }
   }

   /**
    * Writes the body into {@code vc}. {@code skip} >= 0 leaves that part out, {@code skip} <= -2 draws only part
    * {@code -2 - skip}; -1 draws everything. Also the test entry point (any vertex sink).
    */
   static void draw(Frame f, CitizenModel model, PoseStack pose, VertexConsumer vc, int light, int skip) {
      ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
      float heat = f.heated ? 0.7F + 0.3F * Mth.sin(f.time * 0.4F) : 0.0F;
      int lit = light;
      if (heat > 0.0F) {
         int block = Math.max(LightTexture.block(light), 12);
         lit = LightTexture.pack(block, LightTexture.sky(light));
      }
      for (int p = 0; p < parts.length; p++) {
         if (skip >= 0 && p == skip || skip <= -2 && p != -2 - skip) {
            continue;
         }
         int rings;
         int cols;
         if (p == HEAD) {
            rings = T1000Shapes.HEAD_RINGS;
            cols = T1000Shapes.HEAD_COLS;
         } else if (p == BODY) {
            rings = T1000Shapes.BODY_RINGS;
            cols = T1000Shapes.BODY_COLS;
         } else if (p == R_ARM || p == L_ARM) {
            rings = T1000Shapes.ARM_RINGS;
            cols = T1000Shapes.LIMB_COLS;
         } else {
            rings = T1000Shapes.LEG_RINGS;
            cols = T1000Shapes.LIMB_COLS;
         }
         PART_STACK.setIdentity();
         parts[p].translateAndRotate(PART_STACK);
         PART.set(PART_STACK.last().pose());
         build(f, p, rings, cols);
         rootDeform(f, rings, cols, p == R_ARM || p == L_ARM ? 0.9F : 1.0F);
         normals(rings, cols);
         emit(f, pose, vc, rings, cols, lit, heat);
      }
   }

   // --- rest-space construction --------------------------------------------------------------------------------

   private static void build(Frame f, int part, int rings, int cols) {
      float boxy = 1.0F - f.settle;
      float n = T1000Shapes.lerp(f.settle, T1000Shapes.N_BOXY, T1000Shapes.N_SMOOTH);
      float out = T1000Shapes.SHELL_OUTSET * boxy;
      float[] pv = T1000WoundMath.PIVOTS[part];
      boolean arm = part == R_ARM || part == L_ARM;
      boolean right = part == R_ARM;
      T1000Arm now = T1000Arm.HAND;
      T1000Arm before = T1000Arm.HAND;
      float morph = 1.0F;
      float reach = 0.0F;
      float lenNow = 0.0F;
      float lenBefore = 0.0F;
      float hx0 = f.slim ? 1.5F : 2.0F;
      float cxArm = (f.slim ? 0.5F : 1.0F) * (right ? -1.0F : 1.0F);
      if (arm) {
         int a = f.arms;
         now = right ? T1000Arm.right(a) : T1000Arm.left(a);
         before = right ? T1000Arm.rightBefore(a) : T1000Arm.leftBefore(a);
         morph = right ? f.morphR : f.morphL;
         if (f.human) {
            // in disguise the arms are always hands (a wound patch is all the metal there is)
            now = T1000Arm.HAND;
            before = T1000Arm.HAND;
         }
         reach = reach(f, right);
         lenNow = T1000Shapes.armLength(now, reach);
         lenBefore = T1000Shapes.armLength(before, reach);
      }
      for (int j = 0; j <= rings; j++) {
         float t = T1000Shapes.ringT(j, rings);
         float y;
         float cz = 0.0F;
         float cx = 0.0F;
         float bend = 0.0F;
         float hx;
         float hz;
         float nn = n;
         float scale;
         if (arm) {
            float pm = T1000Shapes.ringMorph(morph, t);
            T1000Shapes.armRing(before, t * lenBefore, lenBefore, hx0, RING_B);
            T1000Shapes.armRing(now, t * lenNow, lenNow, hx0, RING);
            y = T1000Shapes.lerp(pm, RING_B[0], RING[0]);
            cz = T1000Shapes.lerp(pm, RING_B[1], RING[1]);
            bend = T1000Shapes.lerp(pm, RING_B[2], RING[2]);
            hx = T1000Shapes.lerp(pm, RING_B[3], RING[3]);
            hz = T1000Shapes.lerp(pm, RING_B[4], RING[4]);
            float sn = T1000Shapes.lerp(pm, RING_B[5], RING[5]);
            nn = T1000Shapes.lerp(f.settle, n, sn);
            cx = cxArm;
            float len = T1000Shapes.lerp(pm, lenBefore, lenNow);
            float s = t * len;
            float d = Math.min(s, len - s);
            boolean pointed = (pm > 0.5F ? now : before).weapon();
            scale = s < len * 0.5F ? T1000Shapes.capBlend(d, 1.8F, boxy) : pointed ? T1000Shapes.cap(d, 0.5F) : T1000Shapes.capBlend(d, 1.6F, boxy);
            if (y < -2.0F + 0.01F) {
               y = -2.0F;
            }
            y -= out * (1.0F - t) * 2.0F - out * (s >= len * 0.5F && !pointed ? 1.0F : 0.0F) * 0.0F;
         } else if (part == HEAD) {
            float y0 = -8.0F - out;
            float y1 = 0.0F + out * 0.3F;
            y = T1000Shapes.lerp(t, y0, y1);
            float d0 = y - y0;
            float d1 = y1 - y;
            scale = d0 < d1 ? T1000Shapes.capBlend(d0, 3.0F, boxy) : T1000Shapes.capBlend(d1, 2.2F, boxy);
            hx = 4.0F;
            hz = 4.0F;
         } else if (part == BODY) {
            float y0 = 0.0F - out * 0.3F;
            float y1 = 12.0F + out * 0.3F;
            y = T1000Shapes.lerp(t, y0, y1);
            float d0 = y - y0;
            float d1 = y1 - y;
            scale = d0 < d1 ? T1000Shapes.capBlend(d0, 1.7F, boxy) : T1000Shapes.capBlend(d1, 1.3F, boxy);
            float waist = 1.0F - 0.07F * f.settle * (float)Math.exp(-(y - 9.0F) * (y - 9.0F) / 5.0F);
            hx = 4.0F * waist;
            hz = 2.0F * (1.0F + 0.08F * f.settle * (float)Math.exp(-(y - 3.5F) * (y - 3.5F) / 5.0F));
         } else {
            float y0 = 0.0F - out * 0.3F;
            float y1 = 12.0F + out;
            y = T1000Shapes.lerp(t, y0, y1);
            float d0 = y - y0;
            float d1 = y1 - y;
            scale = d0 < d1 ? T1000Shapes.capBlend(d0, 1.2F, boxy) : T1000Shapes.capBlend(d1, 1.1F, boxy);
            hx = 2.0F;
            hz = 2.0F;
         }
         float shx = (hx + out) * scale;
         float shz = (hz + out) * scale;
         float sinB = (float)Math.sin(bend);
         float cosB = (float)Math.cos(bend);
         for (int i = 0; i < cols; i++) {
            int v = j * cols + i;
            float a = (float)(Math.PI * 2.0 * i / cols);
            T1000Shapes.section(a, shx, shz, nn, SEC);
            float lx = cx + SEC[0];
            float lz;
            float ly;
            if (arm) {
               // section across the bent centreline: x stays across, z goes along N = (0, sin, cos)
               ly = y + SEC[1] * sinB;
               lz = cz + SEC[1] * cosB;
            } else {
               ly = y;
               lz = cz + SEC[1];
            }
            // rest normal (approximate) for displacements along the surface
            float rnx = (float)Math.cos(a);
            float rnz = (float)Math.sin(a);
            float rny = 0.0F;
            float capW = 1.0F - scale;
            if (capW > 0.0F) {
               float sign = t < 0.5F ? -1.0F : 1.0F;
               rnx *= 1.0F - capW;
               rnz *= 1.0F - capW;
               rny = sign * capW;
            }
            float shade = 1.0F;
            float disp = 0.0F;
            if (part == HEAD && f.settle > 0.0F) {
               float front = Math.max(0.0F, -rnz);
               disp += T1000Shapes.face(lx, ly) * front * front * f.settle;
            }
            // rest-model position (for wounds and the reveal front)
            float mx = lx + pv[0];
            float my = ly + pv[1];
            float mz = lz + pv[2];
            float cover = f.human ? 0.0F : f.metal >= 0.999F ? 1.0F : 0.0F;
            if (!f.human && f.metal < 0.999F) {
               float dx = mx - f.originX;
               float dy = my - f.originY;
               float dz = mz - f.originZ;
               float noise = 1.6F * Mth.sin(mx * 1.3F + my * 0.7F + f.seed) * Mth.sin(mz * 1.1F - my * 0.9F);
               float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) + noise;
               cover = T1000Shapes.coverage(d, f.metal);
               disp += T1000Shapes.frontBulge(d, f.metal);
            }
            for (int w = 0; w < f.woundCount; w++) {
               float[] wd = f.wounds[w];
               if ((int)wd[0] != part) {
                  continue;
               }
               float ox = mx - wd[1];
               float oy = my - wd[2];
               float oz = mz - wd[3];
               float r = wd[4];
               int kind = (int)wd[6];
               float dist;
               if (kind == 1) {
                  float horiz = (float)Math.sqrt(ox * ox + oz * oz);
                  dist = (float)Math.sqrt(horiz * horiz / 5.5F + oy * oy * 2.4F);
               } else {
                  dist = (float)Math.sqrt(ox * ox + oy * oy + oz * oz);
               }
               if (dist > r * 2.4F) {
                  continue;
               }
               float phi = (float)Math.atan2(oy, ox + oz);
               float c = T1000Shapes.crater(dist, r, phi, kind, wd[5], (int)wd[7]);
               if (f.human) {
                  c = Math.max(c, -out + 0.08F);
               }
               disp += c;
               float inside = 1.0F - T1000Shapes.smooth(dist / r);
               shade -= 0.22F * inside * wd[5] * (kind == 2 ? 1.6F : 1.0F);
               cover = Math.max(cover, T1000Shapes.woundCover(dist, r, wd[5]));
            }
            if (!f.human && f.metal >= 0.999F && f.form != T1000Form.DYING) {
               // the surface is never quite still
               disp += 0.07F * Mth.sin(f.time * 0.13F + mx * 0.7F + my * 0.45F + mz * 0.6F);
            }
            lx += rnx * disp;
            ly += rny * disp;
            lz += rnz * disp;
            if (part == HEAD && f.action == T1000Action.FACE_SPLIT) {
               float th = split(f.actionAge);
               if (th > 0.0F) {
                  float side = Math.abs(lx) < 0.05F ? (i % 2 == 0 ? 1.0F : -1.0F) : Math.signum(lx);
                  float ang = -side * th;
                  float c = (float)Math.cos(ang);
                  float s = (float)Math.sin(ang);
                  float gap = side * 0.6F * th;
                  float nxp = lx * c - ly * s + gap;
                  float nyp = lx * s + ly * c;
                  lx = nxp;
                  ly = nyp;
               }
            }
            if (part == R_LEG || part == L_LEG) {
               // feet: the toes reach forward like the citizen's shoes
               float foot = T1000Shapes.smooth((ly - 9.5F) / 2.0F) * Math.max(0.0F, -rnz);
               lz -= 1.0F * foot;
            }
            // into the model root (pixels)
            V4.set(lx / 16.0F, ly / 16.0F, lz / 16.0F, 1.0F);
            PART.transform(V4);
            PX[v] = V4.x * 16.0F;
            PY[v] = V4.y * 16.0F;
            PZ[v] = V4.z * 16.0F;
            AL[v] = cover;
            SH[v] = Math.max(0.45F, shade);
            HH[v] = Mth.clamp((24.0F - my) / 32.0F, 0.0F, 1.0F);
         }
      }
   }

   /** Head split opening angle (radians) at {@code t} ticks into FACE_SPLIT: bursts open, hangs, flows shut. */
   static float split(float t) {
      float max = (float)Math.toRadians(68.0);
      if (t < 4.0F) {
         return max * T1000Shapes.smooth(t / 4.0F);
      }
      if (t < 18.0F) {
         return max * (1.0F - 0.06F * Mth.sin((t - 4.0F) * 0.9F));
      }
      return max * (1.0F - T1000Shapes.smooth((t - 18.0F) / 22.0F));
   }

   /** Extra length of an arm (pixels) during the attacks: the hook shoots out, the blade lunges. */
   static float reach(Frame f, boolean right) {
      float t = f.actionAge;
      return switch (f.action) {
         case HOOK -> right ? 0.0F : 44.0F * T1000Shapes.smooth((t - 4.0F) / 5.0F) * (1.0F - T1000Shapes.smooth((t - 11.0F) / 8.0F));
         case STAB -> right ? 7.0F * T1000Shapes.smooth((t - 3.0F) / 2.0F) * (1.0F - T1000Shapes.smooth((t - 7.0F) / 4.0F)) : 0.0F;
         case COUNTER -> right ? 5.0F * T1000Shapes.smooth((t - 4.5F) / 1.5F) * (1.0F - T1000Shapes.smooth((t - 8.0F) / 5.0F)) : 0.0F;
         case GRAB -> right ? 0.0F : 6.0F * T1000Shapes.smooth((t - 2.0F) / 4.0F) * (1.0F - T1000Shapes.smooth((t - 22.0F) / 6.0F));
         default -> 0.0F;
      };
   }

   // --- root-space deformation ---------------------------------------------------------------------------------

   /** {@code inner}: where this part ends up across the slumping column (arms sink just inside the trunk). */
   private static void rootDeform(Frame f, int rings, int cols, float inner) {
      int count = (rings + 1) * cols;
      boolean ripple = f.hitAge >= 0.0F && f.hitAge < 14.0F && !f.human;
      boolean heavy = f.action == T1000Action.HEAVY;
      float ix = f.hitDx * 3.2F;
      float iy = 6.0F;
      float iz = f.hitDz * 3.2F;
      float dent = heavy ? 3.4F * Math.max(0.35F, f.hitStrength) * T1000Shapes.dentSpring(f.actionAge, 7.0F) : 0.0F;
      float squeeze = 0.0F;
      if (f.flowing) {
         squeeze = Mth.sin((float)Math.PI * Mth.clamp(f.actionAge / T1000Action.FLOW.ticks, 0.0F, 1.0F));
      }
      boolean dying = f.form == T1000Form.DYING;
      float throes = dying ? 1.0F - T1000Shapes.smooth((f.formAge - T1000Rules.DEATH_MELT_START) / 10.0F) : 0.0F;
      float melt = f.melt;
      for (int v = 0; v < count; v++) {
         float x = PX[v];
         float y = PY[v];
         float z = PZ[v];
         float r = (float)Math.sqrt(x * x + z * z);
         float rx = r > 1.0E-3F ? x / r : 0.0F;
         float rz = r > 1.0E-3F ? z / r : 0.0F;
         if (ripple) {
            float dx = x - ix;
            float dy = y - iy;
            float dz = z - iz;
            float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            float w = 0.55F * f.hitStrength * T1000Shapes.ripple(d, f.hitAge);
            x += rx * w;
            z += rz * w;
         }
         if (dent != 0.0F) {
            float dx = x - ix;
            float dy = y - iy;
            float dz = z - iz;
            float g = (float)Math.exp(-(dx * dx + dy * dy + dz * dz) / 50.0F);
            x -= f.hitDx * dent * g;
            z -= f.hitDz * dent * g;
            y += 0.4F * dent * g;
         }
         if (squeeze > 0.0F) {
            x *= 1.0F - 0.72F * squeeze;
            z *= 1.0F - 0.38F * squeeze;
            x += 0.35F * squeeze * Mth.sin(y * 0.9F + f.time * 0.8F);
         }
         if (throes > 0.0F) {
            float w = 0.9F * throes * Mth.sin(f.time * 1.7F + y * 0.8F) * Mth.sin(f.time * 0.9F + rx * 3.0F + rz * 2.0F);
            x += rx * w;
            z += rz * w;
         }
         if (melt > 0.0F) {
            float h = HH[v];
            float phi = r > 0.2F ? (float)Math.atan2(z, x) : (v * 2.399963F);
            int hash = v * 0x27D4EB2D ^ f.seed;
            float jitter = ((hash >>> 9 & 255) / 255.0F - 0.5F) * 0.12F;
            float outline = T1000Shapes.puddleOutline(phi, f.time, f.seed) * f.deathShrink;
            // the settled puddle
            float q = Mth.clamp(0.2F + 0.35F * Math.min(r, 8.0F) / 8.0F + 0.45F * (1.0F - h) + jitter, 0.0F, 1.0F);
            float radius = T1000Shapes.PUDDLE_RADIUS * outline;
            float tx = Mth.cos(phi) * q * radius;
            float tz = Mth.sin(phi) * q * radius;
            float ty = T1000Shapes.GROUND_Y - T1000Shapes.puddleHeight(q) * (0.25F + 0.75F * h) * (0.4F + 0.6F * f.deathShrink);
            ty -= 0.28F * Mth.sin(phi * 4.0F + f.time * 0.25F + q * 6.0F) * (1.0F - q);
            if (dying) {
               // the puddle boils away
               float bub = Mth.sin((hash & 1023) * 0.37F + f.time * 0.6F);
               ty -= 0.7F * Math.max(0.0F, bub) * (1.0F - q) * f.deathShrink;
            }
            // on the way there (or back up) a slumping column: round, sagging, with a skirt that spreads
            T1000Shapes.slump(melt, h, inner, phi, f.time, outline, SLUMP);
            float[] col = SLUMP;
            float sx = Mth.cos(phi) * col[0];
            float sz = Mth.sin(phi) * col[0];
            float sy = col[1];
            float settle = T1000Shapes.smooth((melt - 0.55F) / 0.45F);
            sx = Mth.lerp(settle, sx, tx);
            sy = Mth.lerp(settle, sy, ty);
            sz = Mth.lerp(settle, sz, tz);
            // the top gives way first (and on the way back up the feet stand first)
            float w = T1000Shapes.smooth(melt * 2.7F - (1.0F - h) * 0.55F);
            x = Mth.lerp(w, x, sx);
            y = Mth.lerp(w, y, sy);
            z = Mth.lerp(w, z, sz);
         }
         PX[v] = x;
         PY[v] = Math.min(y, T1000Shapes.GROUND_Y - 0.05F + (melt > 0.0F ? 0.0F : 2.0F));
         PZ[v] = z;
      }
   }

   // --- normals and output -------------------------------------------------------------------------------------

   private static void normals(int rings, int cols) {
      for (int j = 0; j <= rings; j++) {
         int jp = Math.min(rings, j + 1);
         int jm = Math.max(0, j - 1);
         for (int i = 0; i < cols; i++) {
            int ip = (i + 1) % cols;
            int im = (i + cols - 1) % cols;
            int v = j * cols + i;
            float ux = PX[j * cols + ip] - PX[j * cols + im];
            float uy = PY[j * cols + ip] - PY[j * cols + im];
            float uz = PZ[j * cols + ip] - PZ[j * cols + im];
            float tx = PX[jp * cols + i] - PX[jm * cols + i];
            float ty = PY[jp * cols + i] - PY[jm * cols + i];
            float tz = PZ[jp * cols + i] - PZ[jm * cols + i];
            if (ux * ux + uy * uy + uz * uz < 1.0E-8F) {
               // a pole: use the next ring around it
               int jr = j == 0 ? 1 : rings - 1;
               ux = PX[jr * cols + ip] - PX[jr * cols + im];
               uy = PY[jr * cols + ip] - PY[jr * cols + im];
               uz = PZ[jr * cols + ip] - PZ[jr * cols + im];
            }
            // outward = t x u (rings go down the part, columns turn from +x to +z)
            float nx = ty * uz - tz * uy;
            float ny = tz * ux - tx * uz;
            float nz = tx * uy - ty * ux;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6F) {
               NX[v] = 0.0F;
               NY[v] = j == 0 ? -1.0F : 1.0F;
               NZ[v] = 0.0F;
            } else {
               NX[v] = nx / len;
               NY[v] = ny / len;
               NZ[v] = nz / len;
            }
         }
      }
   }

   private static void emit(Frame f, PoseStack pose, VertexConsumer vc, int rings, int cols, int light, float heat) {
      Matrix4f m = pose.last().pose();
      Matrix3f nm = pose.last().normal();
      float tr = 0.93F;
      float tg = 0.96F;
      float tb = 1.0F;
      if (f.frozen) {
         tr = 0.82F;
         tg = 0.93F;
         tb = 1.0F;
      }
      if (heat > 0.0F) {
         tr = Mth.lerp(heat, tr, 1.0F);
         tg = Mth.lerp(heat, tg, 0.52F);
         tb = Mth.lerp(heat, tb, 0.28F);
      }
      if (f.form == T1000Form.DYING) {
         float dark = 0.35F * T1000Shapes.smooth(f.formAge / 30.0F);
         tr *= 1.0F - dark;
         tg *= 1.0F - dark;
         tb *= 1.0F - dark * 0.8F;
      }
      for (int j = 0; j < rings; j++) {
         for (int i = 0; i < cols; i++) {
            int ip = (i + 1) % cols;
            int a = j * cols + i;
            int b = (j + 1) * cols + i;
            int c = (j + 1) * cols + ip;
            int d = j * cols + ip;
            if (Math.max(Math.max(AL[a], AL[b]), Math.max(AL[c], AL[d])) < 0.004F) {
               continue;
            }
            vertex(vc, m, nm, a, light, tr, tg, tb);
            vertex(vc, m, nm, b, light, tr, tg, tb);
            vertex(vc, m, nm, c, light, tr, tg, tb);
            vertex(vc, m, nm, d, light, tr, tg, tb);
         }
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, int v, int light, float tr, float tg, float tb) {
      V4.set(PX[v] / 16.0F, PY[v] / 16.0F, PZ[v] / 16.0F, 1.0F);
      m.transform(V4);
      V3.set(NX[v], NY[v], NZ[v]);
      nm.transform(V3);
      V3.normalize();
      T1000Matcap.uv(V4.x, V4.y, V4.z, V3.x, V3.y, V3.z, UV);
      float s = SH[v];
      vc.vertex(V4.x, V4.y, V4.z, tr * s, tg * s, tb * s, AL[v], UV[0], UV[1], OverlayTexture.NO_OVERLAY, light, V3.x, V3.y, V3.z);
   }
}
