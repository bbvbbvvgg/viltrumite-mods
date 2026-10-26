package dev.baranhan.viltrumitecore.client.render.erasure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One The Hand erasure stroke: tube geometry and the material state of one frame.
 *
 * Port of Roundabout's TheHandErasureRenderer.Erasure (Hydraheads, Roundabout License v1.2, see CREDITS.md).
 * The timeline, taper, closing, ring construction and parallel transport are the original code. Two additions:
 * {@code scale} multiplies every size in blocks (1 = the original stroke; thehand_erasure.fsh receives it as the
 * Scale uniform, so vertex colours stay inside 0..1 at any size), and {@code offset} lets a stroke be a window that
 * slides along a longer path. The stand, palm, stopped time and sandbox clock of the original are not ported. */
public final class ErasureStroke {
   // Timeline in seconds after the cut starts, as in the original.
   public static final float AURA_START = -0.3F;
   public static final float CUT_END = 0.1F;
   public static final float AURA_END = 0.25F;
   public static final float DECAY_START = 0.42F;
   public static final float END = 0.75F;

   // Sizes in blocks at scale 1, as in the original.
   static final float BLADE_WIDTH = 0.34F;
   static final float AURA_RADIUS = 0.17F;
   /** Must match AURA_BOUND in thehand_erasure.fsh. */
   static final float AURA_BOUND = 1.8F;
   static final float TAIL_TAPER = 0.38F;
   static final float TAPER_START = 0.62F;
   /** Must match SHELL_MARGIN in thehand_erasure.fsh. */
   static final float SHELL_MARGIN = 0.13F;
   /** Radii go to the shader in vertex colour r divided by these times scale; must match the .vsh and .fsh. */
   static final float RADIUS_SCALE = 0.4F;
   static final float SHELL_SCALE = 0.5F;
   /** Must match the .fsh. */
   static final float CLOSE_SPAN = 1.3F;
   static final float ZIP = 0.6F;
   /** Fewest body rings, the original count; a long stroke gets more, see {@link #bodyRings()}. */
   static final int BODY_RINGS = 64;
   /** Most body rings of one stroke: bounds the vertices of a 200-block stroke to one draw per pass. */
   static final int MAX_BODY_RINGS = 192;
   /** Ring spacing of a long stroke, in tube widths: a quarter width keeps the silhouette round on curves. */
   static final float RING_SPACING = 0.25F;
   static final int CAP_RINGS = 8;
   static final int AROUND = 20;

   final ErasurePath path;
   final float offset;
   final float length;
   final float scale;
   final float seed;
   final ErasureStyle style;
   /** Width along the stroke; {@link Profile#ORIGINAL} unless the stroke is built with another one. */
   final Profile profile;
   final float auraStart;
   /** False: no aura before the cut starts (a stroke that continues another one); the original always has one. */
   final boolean preAura;
   // Derived from time once per frame by update().
   float time;
   /** Seconds since the cut started and the end of this stroke's timeline (END, or the end of a held stroke). */
   float clock;
   float end = END;
   float headS;
   /** The round head cap is a half sphere of radius capR centred at capS on the path. */
   float capS;
   float capR;
   float width;
   float decay;
   float flash;
   float halo;
   float aura;
   /** Centre line of this frame, shared by the shell and glass passes; dropped by every update. */
   private Frame frame;

   /** @param offset path length where this stroke's tail sits
    *  @param length stroke length along the path, blocks */
   public ErasureStroke(ErasurePath path, float offset, float length, float scale, float seed, ErasureStyle style) {
      this(path, offset, length, scale, seed, style, true);
   }

   public ErasureStroke(ErasurePath path, float offset, float length, float scale, float seed, ErasureStyle style,
                        boolean preAura) {
      this(path, offset, length, scale, seed, style, preAura, Profile.ORIGINAL);
   }

   /** A stroke with its own width profile (REINHARD OVERHAUL 3b: the World Cut wounds, tens of blocks long). */
   public ErasureStroke(ErasurePath path, float offset, float length, float scale, float seed, ErasureStyle style,
                        boolean preAura, Profile profile) {
      if (!(length > 0) || !(scale > 0) || profile == null) throw new IllegalArgumentException("Invalid erasure stroke");
      this.profile = profile;
      this.path = path;
      this.offset = offset;
      this.length = length;
      this.scale = scale;
      this.seed = seed;
      this.style = style;
      this.auraStart = AURA_START;
      this.preAura = preAura;
   }

   /** The whole path as one stroke, the original case. */
   public ErasureStroke(ErasurePath path, float seed, ErasureStyle style) {
      this(path, 0F, path.length(), 1F, seed, style);
   }

   /** Original timeline: the head follows the reveal curve over the whole stroke. */
   public ErasureStroke update(float t) {
      return update(t, reveal(t) * length);
   }

   /** Original timeline with the head position supplied by the caller (blocks from the stroke's tail). */
   public ErasureStroke update(float t, float head) {
      frame = null;
      time = t;
      clock = t;
      end = END;
      headS = Mth.clamp(head, 0F, length);
      decay = Mth.clamp((t - DECAY_START) / (END - DECAY_START), 0F, 1F);
      // The width breathes slightly while the tube holds; closing is done per point by radius().
      width = BLADE_WIDTH * scale * (t < CUT_END ? 1F : 1F + 0.04F * Mth.sin((t - CUT_END) * 18F));
      capS = Math.max(headS - radius(headS), 0F);
      capR = radius(capS);
      flash = t < CUT_END ? 1F : (float) Math.exp(-(t - CUT_END) / 0.025F);
      halo = 1F - smoothstep(0.85F, 1F, decay);
      aura = t < 0 ? (preAura ? easeOut((t - auraStart) / -auraStart) : 0F) : 1F - Mth.clamp((t - CUT_END) / (AURA_END - CUT_END), 0F, 1F);
      return this;
   }

   /**
    * A stroke that stays open (REINHARD OVERHAUL item 10, the diagonal World Cut). The original cut is played over
    * {@code reveal} seconds instead of {@link #CUT_END} (flash, aura and reveal keep their shape), then the original
    * clock runs on while the tube holds open, breathing as in the original, until {@code hold} seconds; from there it
    * zips shut from the tail to the head on the original closing curve, over {@code close} seconds instead of
    * END - DECAY_START. {@code swell} (0..1) widens it a little for a pulse. Before the cut (t &lt; 0) a stroke built
    * with a pre-aura shows it on the original clock. {@link #update} is untouched: the /erasuretest swipe keeps the
    * original timeline.
    *
    * @param t    seconds since the cut started
    * @param head blocks from the stroke's tail
    */
   public ErasureStroke updateHeld(float t, float head, float reveal, float hold, float close, float swell) {
      if (!(reveal > 0) || !(close > 0) || !(hold >= reveal)) throw new IllegalArgumentException("Invalid held timeline");
      // Before the cut (t < 0) the original pre-aura clock runs as it is: the flame gathers over 0.3 s.
      float original = t < 0 ? t : t < reveal ? t * (CUT_END / reveal) : CUT_END + (t - reveal);
      update(original, head);
      decay = Mth.clamp((t - hold) / close, 0F, 1F);
      width *= 1F + 0.1F * Mth.clamp(swell, 0F, 1F);
      capS = Math.max(headS - radius(headS), 0F);
      capR = radius(capS);
      halo = 1F - smoothstep(0.85F, 1F, decay);
      clock = t;
      end = hold + close;
      frame = null;
      return this;
   }

   /** Replaces the aura amount of this frame; a chain of strokes shares one aura on its front. */
   public ErasureStroke overrideAura(float amount) {
      aura = Mth.clamp(amount, 0F, 1F);
      return this;
   }

   /** Full tube width in blocks at a scale, before breathing and taper. */
   public static float widthAt(float scale) {
      return BLADE_WIDTH * scale;
   }

   /** Hands the aura over to the next stroke of a chain: both heads sit on the same point. */
   public ErasureStroke hideAura() {
      aura = 0F;
      return this;
   }

   public boolean finished() {
      return clock > end;
   }

   boolean tubeVisible() {
      return time >= 0 && capS > 0.01F * scale;
   }

   boolean auraVisible() {
      return aura > 0;
   }

   public float time() { return time; }
   /** Width of the tube this frame (blocks), breathing and swell included, before taper and closing. */
   public float width() { return width; }
   public ErasureStyle style() { return style; }
   public float headS() { return headS; }
   public float decay() { return decay; }
   public float aura() { return aura; }
   public float length() { return length; }
   public float scale() { return scale; }
   public Profile profile() { return profile; }
   public float seed() { return seed; }
   /** Tube radius at stroke length s this frame (blocks), profile, swell and closing included. */
   public float radiusAt(float s) { return radius(s); }

   /** Tube radius at stroke length s, closing included. */
   float radius(float s) {
      float u = s / length;
      return profile.halfWidth(u, width, seed) * openness(u, decay);
   }

   Vec3 pointAt(float s) {
      return path.pointAt(offset + s);
   }

   /** Averaged over 0.2 blocks at scale 1: a per-sample tangent jumps at polyline corners. */
   Vec3 tangentAt(float s) {
      float h = 0.1F * scale;
      return pointAt(s + h).subtract(pointAt(s - h)).normalize();
   }

   Vec3 auraCentre() {
      return pointAt(headS);
   }

   float auraRadius() {
      return AURA_RADIUS * scale * (0.4F + 0.6F * aura);
   }

   /** World direction the aura's flames lick: up, and back along the stroke while the head moves. */
   Vec3 auraDrift() {
      float motion = time < 0 ? 0F : 1F - Mth.clamp((time - CUT_END) / 0.1F, 0F, 1F);
      return new Vec3(0, 0.7, 0).add(tangentAt(headS).scale(-1.3 * motion)).normalize();
   }

   /** World box around everything this stroke can draw this frame. */
   public AABB bounds() {
      Vec3 a = pointAt(0);
      double minX = a.x, minY = a.y, minZ = a.z, maxX = minX, maxY = minY, maxZ = minZ;
      int steps = Math.max(2, (int) Math.ceil(headS / Math.max(0.5F, 0.1F * length)));
      for (int i = 1; i <= steps; i++) {
         Vec3 p = pointAt(headS * i / steps);
         minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
         maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
      }
      double pad = (BLADE_WIDTH + SHELL_MARGIN + AURA_RADIUS * AURA_BOUND) * scale + 0.5;
      return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(pad);
   }

   /** Body rings this frame: the original 64, or one every {@link #RING_SPACING} tube widths on a long stroke, at
    * most {@link #MAX_BODY_RINGS}. The original stroke (about eight widths long) keeps exactly 64. */
   int bodyRings() {
      float spacing = RING_SPACING * Math.max(width, 1.0E-3F);
      int wanted = (int) Math.ceil(capS / spacing);
      return Mth.clamp(wanted, BODY_RINGS, MAX_BODY_RINGS);
   }

   /** The centre line of this frame: points, tangents and transported normals of the body rings. Built once and
    * shared by the shell and glass passes (the renderer asks for both every frame). */
   private Frame frame() {
      if (frame != null) return frame;
      int n = bodyRings();
      Vec3[] centres = new Vec3[n + 1];
      Vec3[] tangents = new Vec3[n + 1];
      Vec3[] normals = new Vec3[n + 1];
      float[] ss = new float[n + 1];
      float[] rs = new float[n + 1];
      Vec3 previous = null;
      for (int i = 0; i <= n; i++) {
         float s = capS * i / n;
         Vec3 tangent = tangentAt(s);
         Vec3 normal = previous == null ? anyPerpendicular(tangent) : previous.subtract(tangent.scale(previous.dot(tangent)));
         if (normal.lengthSqr() < 1.0E-8) normal = anyPerpendicular(tangent);
         normal = normal.normalize();
         previous = normal;
         centres[i] = pointAt(s);
         tangents[i] = tangent;
         normals[i] = normal;
         ss[i] = s;
         rs[i] = radius(s);
      }
      frame = new Frame(n, centres, tangents, normals, ss, rs);
      return frame;
   }

   private record Frame(int n, Vec3[] centres, Vec3[] tangents, Vec3[] normals, float[] ss, float[] rs) {
   }

   /** Rings from the tail to the head. The glass: the tapered body, then a half sphere at the head. The shell:
    * SHELL_MARGIN wider, with round ends at both the tail point and the head. Frames are carried along the
    * path without twisting (parallel transport), so the pattern on the surface stays put. */
   List<Ring> rings(boolean shell) {
      float margin = shell ? SHELL_MARGIN * scale : 0F;
      float shellDiv = SHELL_SCALE * scale;
      float glassDiv = RADIUS_SCALE * scale;
      Frame f = frame();
      int n = f.n();
      Vec3[] centres = f.centres();
      Vec3[] tangents = f.tangents();
      Vec3[] normals = f.normals();
      float[] ss = f.ss();
      float[] rs = f.rs();

      List<Ring> rings = new ArrayList<>(n + 1 + 2 * CAP_RINGS);
      if (shell) {
         Vec3 tangent = tangents[0];
         Vec3 b = tangent.cross(normals[0]);
         for (int j = 0; j < CAP_RINGS; j++) {
            float phi = -Mth.HALF_PI + Mth.HALF_PI * j / CAP_RINGS;
            float sin = Mth.sin(phi);
            float cos = Mth.cos(phi);
            rings.add(new Ring(centres[0].add(tangent.scale(margin * sin)), normals[0], b, tangent,
               margin * cos, cos, sin, 0F, 0F, margin / shellDiv, false));
         }
      }
      for (int i = 0; i <= n; i++) {
         Vec3 b = tangents[i].cross(normals[i]);
         if (shell) {
            rings.add(new Ring(centres[i], normals[i], b, tangents[i], rs[i] + margin, 1F, 0F,
               ss[i], rs[i], (rs[i] + margin) / shellDiv, true));
         } else {
            int lo = Math.max(i - 1, 0);
            int hi = Math.min(i + 1, n);
            float slope = ss[hi] > ss[lo] ? (rs[hi] - rs[lo]) / (ss[hi] - ss[lo]) : 0F;
            rings.add(new Ring(centres[i], normals[i], b, tangents[i], rs[i], 1F, -slope,
               ss[i], 0F, rs[i] / glassDiv, true));
         }
      }
      Vec3 tangent = tangents[n];
      Vec3 b = tangent.cross(normals[n]);
      float capRadius = capR + margin;
      for (int j = 1; j <= CAP_RINGS; j++) {
         float phi = Mth.HALF_PI * j / CAP_RINGS;
         float sin = Mth.sin(phi);
         float cos = Mth.cos(phi);
         Vec3 centre = centres[n].add(tangent.scale(capRadius * sin));
         if (shell) {
            rings.add(new Ring(centre, normals[n], b, tangent, capRadius * cos, cos, sin,
               capS, capR, capRadius / shellDiv, false));
         } else {
            rings.add(new Ring(centre, normals[n], b, tangent, capR * cos, cos, sin,
               capS + capR * sin, 0F, capR / glassDiv, true));
         }
      }
      return rings;
   }

   /** Half width of the original tube at path fraction u, in blocks, before closing. */
   static float tubeHalfWidth(float u, float width, float seed) {
      return Profile.ORIGINAL.halfWidth(u, width, seed);
   }

   /** 1 open, 0 shut; space zips shut from the tail to the head. */
   static float openness(float u, float decay) {
      return 1F - smoothstep(0F, 1F, decay * (CLOSE_SPAN + ZIP) - ZIP * u);
   }

   static float reveal(float t) {
      return t <= 0 ? 0F : easeOut(Math.min(t / CUT_END, 1F));
   }

   static float easeOut(float x) {
      x = Mth.clamp(x, 0F, 1F);
      return 1F - (1F - x) * (1F - x);
   }

   static float smoothstep(float edge0, float edge1, float x) {
      float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0F, 1F);
      return t * t * (3F - 2F * t);
   }

   private static Vec3 anyPerpendicular(Vec3 tangent) {
      Vec3 axis = Math.abs(tangent.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
      return tangent.cross(axis).normalize();
   }

   /**
    * Width along a stroke, all in path fractions u (0 tail, 1 head). The tail opens on a quarter sine over
    * {@code tail}; the body grows from {@code grow} of the full width to all of it between {@code growStart} and
    * {@code growEnd}; a ripple of relative size {@code ripple} runs along it at {@code frequency} radians per unit of u,
    * phase-shifted by the seed. The head always ends in the round cap of the original.
    *
    * <p>{@link #ORIGINAL} is the /erasuretest horn (Roundabout): a thin tail over 38 % of the stroke and a body that
    * grows from 62 %. That is right for a stroke a few widths long; spread over a World Cut it leaves a thin thread
    * over tens of blocks, so the World Cut wounds use {@link #forLength}: the same quarter-sine tail and growth, sized
    * in blocks instead of fractions.
    */
   public record Profile(float tail, float grow, float growStart, float growEnd, float ripple, float frequency) {
      /** The original stroke: TAIL_TAPER, TAPER_START, smoothstep(0.2, 0.9) and a 4 % ripple at 7.5 rad. */
      public static final Profile ORIGINAL = new Profile(TAIL_TAPER, TAPER_START, 0.2F, 0.9F, 0.04F, 7.5F);

      public Profile {
         if (!(tail > 0) || !(tail <= 1) || !(grow > 0) || !(grow <= 1) || !(growEnd > growStart) || !(ripple >= 0)
            || !(ripple < 0.5F) || !(frequency >= 0)) throw new IllegalArgumentException("Invalid erasure profile");
      }

      /**
       * A long stroke: the tail opens over {@code tailBlocks}, the body reaches its full width {@code growBlocks}
       * after the tail point (from {@code grow} of it), a ripple every {@code waveBlocks}. Fractions are clamped so
       * a short path still gets a valid profile.
       */
      public static Profile forLength(float length, float tailBlocks, float grow, float growBlocks, float ripple,
                                      float waveBlocks) {
         if (!(length > 0)) throw new IllegalArgumentException("Invalid erasure profile length");
         float tail = Mth.clamp(tailBlocks / length, 0.01F, 1F);
         float growEnd = Mth.clamp(growBlocks / length, 0.02F, 1F);
         return new Profile(tail, grow, Math.min(tail * 0.5F, growEnd * 0.5F), growEnd, ripple,
            waveBlocks > 0 ? Mth.TWO_PI * length / waveBlocks : 0F);
      }

      /** Half width at path fraction u, in blocks, before closing. */
      public float halfWidth(float u, float width, float seed) {
         u = Mth.clamp(u, 0F, 1F);
         float open = Mth.sin(Mth.clamp(u / tail, 0F, 1F) * Mth.HALF_PI);
         float body = grow + (1F - grow) * smoothstep(growStart, growEnd, u);
         return 0.5F * width * open * body * (1F + ripple * Mth.sin(u * frequency + seed * Mth.TWO_PI));
      }
   }

   /** One ring of the tube or the shell. Vertex k sits at centre + r * (n cos a + b sin a), a = 2 pi k / AROUND;
    * its normal is (n cos a + b sin a) * normalRadial + tangent * normalAxial. */
   record Ring(Vec3 centre, Vec3 n, Vec3 b, Vec3 tangent, float r, float normalRadial, float normalAxial,
               float s, float tubeRadius, float colourRadius, boolean passTangent) {
   }
}
