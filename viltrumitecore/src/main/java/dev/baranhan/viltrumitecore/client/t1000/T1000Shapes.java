package dev.baranhan.viltrumitecore.client.t1000;

import dev.baranhan.viltrumitecore.t1000.T1000Arm;

/**
 * Pure geometry of the liquid-metal body (docs/design/2026-11-06-t1000-design.md §1): cross-sections, cap domes,
 * the arm weapon shapes, the face relief, wound craters, the reveal front and the puddle. Units are model pixels in
 * part space (y down the limb, -z the face, -x the right side) unless a method says otherwise. No Minecraft types,
 * so {@code T1000ShapesTest} covers it.
 */
public final class T1000Shapes {
   /** Grid sizes: rings along the part axis, columns around it. */
   public static final int HEAD_RINGS = 16;
   public static final int HEAD_COLS = 32;
   public static final int BODY_RINGS = 16;
   public static final int BODY_COLS = 28;
   public static final int LEG_RINGS = 14;
   public static final int LIMB_COLS = 16;
   public static final int ARM_RINGS = 30;
   /** Exponent of the settled liquid body (rounded) and of the shell that still covers the citizen cubes. */
   public static final float N_SMOOTH = 3.6F;
   public static final float N_BOXY = 14.0F;
   /** Outset of the shell over the skin cubes while it spreads over them (pixels). */
   public static final float SHELL_OUTSET = 0.55F;
   /** Arm length (shoulder top to hand end) and the length of each weapon beyond the forearm. */
   public static final float ARM_LENGTH = 12.0F;
   public static final float BLADE_LENGTH = 19.0F;
   public static final float HOOK_LENGTH = 15.0F;
   public static final float SPIKE_LENGTH = 22.0F;
   /** Where the forearm turns into the weapon (pixels from the shoulder top). */
   public static final float WEAPON_ROOT = 7.0F;
   /** Puddle radius / height in pixels (matches T1000Rules.PUDDLE_WIDTH 1.4 blocks). */
   public static final float PUDDLE_RADIUS = 11.0F;
   public static final float PUDDLE_HEIGHT = 2.2F;
   /** Ground level of the model root (feet), pixels. */
   public static final float GROUND_Y = 24.0F;

   private T1000Shapes() {
   }

   public static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   public static float smooth(float t) {
      t = clamp01(t);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float lerp(float t, float a, float b) {
      return a + (b - a) * t;
   }

   /** Superellipse cross-section point at angle {@code a}: out[0] = x, out[1] = z. n = 2 ellipse, large n box. */
   public static void section(float a, float hx, float hz, float n, float[] out) {
      float c = (float)Math.cos(a);
      float s = (float)Math.sin(a);
      float e = 2.0F / Math.max(1.0F, n);
      out[0] = hx * Math.signum(c) * (float)Math.pow(Math.abs(c), e);
      out[1] = hz * Math.signum(s) * (float)Math.pow(Math.abs(s), e);
   }

   /** Ring parameter 0..1 of ring {@code j}: denser near both caps (Chebyshev nodes). */
   public static float ringT(int j, int rings) {
      return 0.5F - 0.5F * (float)Math.cos(Math.PI * j / rings);
   }

   /** Section scale of a dome cap: {@code d} pixels from the nearest end, cap length {@code c}. */
   public static float cap(float d, float c) {
      if (c <= 1.0E-4F || d >= c) {
         return 1.0F;
      }
      float q = 1.0F - Math.max(0.0F, d) / c;
      return (float)Math.sqrt(Math.max(0.0F, 1.0F - q * q));
   }

   /** Cap scale with the boxy shell blend: a box keeps (almost) its full section up to the end. */
   public static float capBlend(float d, float c, float boxy) {
      float dome = cap(d, c);
      float box = cap(d, c * 0.18F);
      return lerp(boxy, dome, box);
   }

   // --- arms ---------------------------------------------------------------------------------------------------

   /** Total centreline length of an arm shape (pixels), {@code reach} stretches the weapon (hook shot, lunge). */
   public static float armLength(T1000Arm shape, float reach) {
      return switch (shape) {
         case HAND -> ARM_LENGTH + reach * 0.5F;
         case BLADE -> WEAPON_ROOT + BLADE_LENGTH + reach;
         case HOOK -> WEAPON_ROOT + HOOK_LENGTH + reach;
         case SPIKE -> WEAPON_ROOT + SPIKE_LENGTH + reach;
      };
   }

   /**
    * One ring of an arm shape at arc position {@code s} (pixels from the shoulder top). Output: {y, z, bend, hx,
    * hz, n} — centre (part space, x stays on the arm axis), bend angle of the tangent towards -z (radians),
    * half extents of the section across (x) and along the bend plane (z) and the exponent.
    */
   public static void armRing(T1000Arm shape, float s, float length, float hx0, float[] out) {
      float y = -2.0F + s;
      float z = 0.0F;
      float bend = 0.0F;
      float hx = hx0;
      float hz = 2.0F;
      float n = N_SMOOTH;
      float root = WEAPON_ROOT;
      switch (shape) {
         case HAND -> {
            // a slightly narrower wrist, the hand a touch flatter
            float w = s / Math.max(1.0F, length);
            hx = hx0 * (0.92F + 0.08F * (float)Math.cos(w * Math.PI * 2.0F));
            hz = 2.0F * (w > 0.78F ? 0.9F : 1.0F);
         }
         case BLADE -> {
            if (s > root) {
               float q = clamp01((s - root) / Math.max(1.0F, length - root));
               float base = smooth(clamp01((s - root) / 2.5F));
               hx = lerp(base, hx0, 0.38F + 0.12F * (1.0F - q));
               // a long leaf: widest a third of the way, then a fine point
               float leaf = (float)Math.pow(Math.sin(Math.PI * Math.min(1.0F, 0.18F + q * 0.82F)), 0.7);
               hz = lerp(base, 2.0F, 2.6F * leaf * (1.0F - q * q * 0.35F) + 0.04F);
               z = 0.55F * q * q;
               n = lerp(base, N_SMOOTH, 2.15F);
            }
         }
         case HOOK -> {
            if (s > root) {
               float q = clamp01((s - root) / Math.max(1.0F, length - root));
               float base = smooth(clamp01((s - root) / 2.0F));
               float arc = (s - root);
               float radius = 4.2F;
               float phi = Math.min(arc / radius, 2.7F) * smooth(clamp01(q * 1.4F));
               // the centreline curls forward and back up (a sickle)
               float straight = Math.max(0.0F, arc - phi * radius);
               y = -2.0F + root + radius * (float)Math.sin(phi) + straight * (float)Math.cos(phi);
               z = -radius * (1.0F - (float)Math.cos(phi)) - straight * (float)Math.sin(phi);
               bend = phi;
               hx = lerp(base, hx0, 0.55F);
               hz = lerp(base, 2.0F, 1.7F * (1.0F - q) + 0.06F);
               n = lerp(base, N_SMOOTH, 2.3F);
            }
         }
         case SPIKE -> {
            if (s > root) {
               float q = clamp01((s - root) / Math.max(1.0F, length - root));
               float base = smooth(clamp01((s - root) / 3.0F));
               float r = 1.45F * (float)Math.pow(1.0F - q, 1.15F) + 0.03F;
               hx = lerp(base, hx0, r);
               hz = lerp(base, 2.0F, r);
               n = lerp(base, N_SMOOTH, 2.0F);
            }
         }
      }
      out[0] = y;
      out[1] = z;
      out[2] = bend;
      out[3] = hx;
      out[4] = hz;
      out[5] = n;
   }

   /** Morph progress of one ring: the metal flows from the elbow to the tip (t 0 shoulder .. 1 tip). */
   public static float ringMorph(float morph, float t) {
      return smooth(morph * 1.6F - t * 0.6F);
   }

   // --- face ---------------------------------------------------------------------------------------------------

   private static float bump(float dx, float dy, float r2) {
      return (float)Math.exp(-(dx * dx + dy * dy) / r2);
   }

   /**
    * Relief of the chrome face (pixels outward) at head-space (x, y), y from -8 (crown) to 0 (chin): brow, eye
    * sockets, nose, cheekbones, lips and chin. Multiply by the frontness of the vertex.
    */
   public static float face(float x, float y) {
      float ax = Math.abs(x);
      float r = 0.0F;
      r -= 0.5F * bump(ax - 1.85F, y + 4.3F, 0.9F);
      r += 0.28F * (float)Math.exp(-(y + 5.25F) * (y + 5.25F) / 0.35F) * (ax < 3.4F ? 1.0F : 0.0F);
      float nose = (float)Math.exp(-x * x / 0.45F) * smooth((y + 4.6F) / 0.8F) * (1.0F - smooth((y + 2.3F) / 0.5F));
      r += 0.75F * nose * (0.55F + 0.45F * clamp01((y + 4.6F) / 2.0F));
      r += 0.22F * bump(ax - 2.55F, y + 3.1F, 0.8F);
      r -= 0.16F * (float)Math.exp(-(y + 1.55F) * (y + 1.55F) / 0.05F) * (ax < 1.5F ? 1.0F : 0.0F);
      r += 0.12F * (float)Math.exp(-(y + 1.15F) * (y + 1.15F) / 0.12F) * (ax < 1.3F ? 1.0F : 0.0F);
      r += 0.22F * bump(x, y + 0.45F, 0.9F);
      return r;
   }

   // --- wounds -------------------------------------------------------------------------------------------------

   /** Visible fraction of a wound {@code age} ticks old that closes over {@code closeTicks}: snaps open, flows shut. */
   public static float woundOpen(float age, float closeTicks) {
      if (age < 0.0F) {
         return 0.0F;
      }
      float open = smooth(age / 2.5F);
      float shut = smooth((age - 8.0F) / Math.max(1.0F, closeTicks - 8.0F));
      return open * (1.0F - shut);
   }

   /**
    * Displacement along the surface normal of a wound at distance {@code d} from its centre (pixels), angle
    * {@code phi} around it. Kind 0: bullet hole with a petal crown (the film's flower-shaped hits), 1: a cut
    * (call with the anisotropic distance), 2: heat blisters. Scaled by {@code open}.
    */
   public static float crater(float d, float r, float phi, int kind, float open, int seed) {
      if (open <= 0.0F || r <= 0.0F) {
         return 0.0F;
      }
      float q = d / r;
      if (q > 2.2F) {
         return 0.0F;
      }
      return switch (kind) {
         case 1 -> {
            float groove = q < 1.0F ? -0.55F * r * (1.0F - q * q) : 0.0F;
            float lip = 0.22F * r * (float)Math.exp(-(q - 1.15F) * (q - 1.15F) / 0.06F);
            yield open * (groove + lip);
         }
         case 2 -> {
            float n = (float)Math.sin(phi * 3.0F + seed) * (float)Math.sin(q * 6.0F + seed * 0.7F);
            yield open * 0.25F * r * Math.max(0.0F, 1.0F - q * 0.55F) * (0.6F + 0.4F * n);
         }
         default -> {
            float hole = q < 1.0F ? -0.95F * r * (1.0F - q * q) : 0.0F;
            float petals = 0.62F + 0.38F * (float)Math.cos(phi * 5.0F + seed * 1.7F);
            float rim = 0.55F * r * petals * (float)Math.exp(-(q - 1.12F) * (q - 1.12F) / 0.05F);
            yield open * (hole + rim);
         }
      };
   }

   /** How much a wound shows metal through the skin of the disguise (0..1) at distance {@code d}. */
   public static float woundCover(float d, float r, float open) {
      return open * (1.0F - smooth((d - r * 1.25F) / Math.max(0.3F, r * 0.45F)));
   }

   // --- reveal -------------------------------------------------------------------------------------------------

   /** Farthest rest-model distance the reveal front has to travel (chest to crown / toes / fingertips). */
   public static final float REVEAL_REACH = 30.0F;
   /** Width of the soft edge of the spreading chrome (pixels). */
   public static final float REVEAL_BAND = 3.5F;

   /** Coverage 0..1 of a vertex {@code d} pixels from the origin when the metal amount is {@code metal}. */
   public static float coverage(float d, float metal) {
      if (metal >= 0.999F) {
         return 1.0F;
      }
      if (metal <= 0.0F) {
         return 0.0F;
      }
      float front = metal * (REVEAL_REACH + REVEAL_BAND);
      return clamp01((front - d) / REVEAL_BAND);
   }

   /** Outward bulge of the liquid wave riding the reveal front (pixels). */
   public static float frontBulge(float d, float metal) {
      if (metal <= 0.0F || metal >= 0.999F) {
         return 0.0F;
      }
      float front = metal * (REVEAL_REACH + REVEAL_BAND) - REVEAL_BAND * 0.5F;
      float x = (d - front) / 1.6F;
      return 0.45F * (float)Math.exp(-x * x);
   }

   // --- springs ------------------------------------------------------------------------------------------------

   /** Damped spring of a dent: 1 at the hit, overshoots once and settles ({@code t} ticks since the hit). */
   public static float dentSpring(float t, float tau) {
      if (t < 0.0F) {
         return 0.0F;
      }
      float attack = smooth(t / 1.5F);
      return attack * (float)Math.exp(-t / tau) * (float)Math.cos(t * 0.42F);
   }

   /** Ripple of an ordinary hit: a ring wave running out from the impact ({@code d} px, {@code t} ticks). */
   public static float ripple(float d, float t) {
      if (t < 0.0F || t > 14.0F) {
         return 0.0F;
      }
      return (float)Math.sin(d * 1.1F - t * 1.25F) * (float)Math.exp(-t / 4.5F) * (float)Math.exp(-d / 9.0F) * smooth((t * 1.4F - d * 0.25F) + 1.0F);
   }

   // --- puddle -------------------------------------------------------------------------------------------------

   /** Per-vertex melt progress: the upper body slumps first; {@code h} is 0 at the feet, 1 at the crown. */
   public static float vertexMelt(float melt, float h) {
      return smooth(melt * 1.35F - (1.0F - h) * 0.35F);
   }

   /** Outline radius factor of the puddle at angle {@code a} and time {@code t} (ticks): a slow living blob. */
   public static float puddleOutline(float a, float t, int seed) {
      return 1.0F + 0.11F * (float)Math.sin(a * 3.0F + t * 0.045F + seed) + 0.07F * (float)Math.sin(a * 5.0F - t * 0.07F + seed * 0.37F)
         + 0.04F * (float)Math.sin(a * 9.0F + t * 0.11F);
   }

   /** Height (pixels) of the puddle surface at radius fraction {@code q} (0 centre .. 1 rim). */
   public static float puddleHeight(float q) {
      q = clamp01(q);
      return PUDDLE_HEIGHT * (float)Math.sqrt(Math.max(0.0F, 1.0F - q * q));
   }
}
