package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;

/**
 * Pure camera plan of the snap and universe scene (no Minecraft classes, unit tested): for every
 * scene tick a camera position, the point it looks at, the field of view and whether the shot is
 * a hard cut. Two plans share one timeline ({@link ThanosSceneTimeline}):
 * <ul>
 * <li><b>near</b> - the player watches Thanos: wide shot of the raise, the gauntlet while the six
 * stones light, his face, the fingers folding and the click itself, the pull-back over the
 * dissolving world, the void, Od Lagna forming, six framed shots for the six lines, the golden
 * restore and the way back into the player's eyes;</li>
 * <li><b>far</b> - Thanos is out of sight (another place or dimension): the stop holds the
 * player's own view, the camera rises over the player while the world comes apart, Od Lagna forms
 * in front of the player and speaks, and the camera comes back.</li>
 * </ul>
 * The first and the last shots blend with the player's own view, so the scene starts and ends
 * without a jump.
 */
public final class ThanosSceneShots {
   /** Ticks the camera takes to leave the player's eyes at the start. */
   public static final float LEAVE = 16.0F;
   /** The way back into the player's eyes, ending just before the pause ends. */
   public static final float BACK_START = ThanosSceneTimeline.RESTORE_END - 40.0F;
   public static final float BACK_END = ThanosSceneTimeline.LENGTH - 8.0F;
   /** The click is seen on the fingers; the cut to the shock wave comes right after it. */
   public static final float SNAP_CUT = ThanosSceneTimeline.SNAP + 3.0F;
   public static final float NORMAL_FOV = 70.0F;

   private ThanosSceneShots() {
   }

   /** A small immutable vector (world coordinates). */
   public record V(double x, double y, double z) {
      public static final V ZERO = new V(0.0, 0.0, 0.0);
      public static final V UP = new V(0.0, 1.0, 0.0);

      public V add(V o) {
         return new V(this.x + o.x, this.y + o.y, this.z + o.z);
      }

      public V sub(V o) {
         return new V(this.x - o.x, this.y - o.y, this.z - o.z);
      }

      public V scale(double k) {
         return new V(this.x * k, this.y * k, this.z * k);
      }

      public V up(double k) {
         return new V(this.x, this.y + k, this.z);
      }

      public double length() {
         return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
      }

      public V normalize() {
         double l = this.length();
         return l < 1.0E-6 ? ZERO : this.scale(1.0 / l);
      }

      public V flat() {
         return new V(this.x, 0.0, this.z);
      }

      public V lerp(V o, double k) {
         return new V(this.x + (o.x - this.x) * k, this.y + (o.y - this.y) * k, this.z + (o.z - this.z) * k);
      }

      public boolean finite() {
         return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
      }
   }

   /**
    * Everything the plan needs about the scene: Thanos's feet and facing (forward, left), the
    * posed gauntlet, the centre of its stones and his head (world points), Od Lagna's feet, and the
    * player's eye and view direction at the start. In the far plan the Thanos points are unused.
    */
   public record Frame(boolean near, V origin, V fwd, V left, V gauntlet, V stones, V head, V od, V eye, V look) {
      /** Unit vector from Od Lagna toward Thanos (flat); the far plan: toward the player. */
      public V toThanos() {
         V d = (this.near ? this.origin : this.eye).sub(this.od).flat().normalize();
         return d.length() < 0.5 ? this.fwd.scale(-1.0) : d;
      }

      /** Flat right-hand side of the Od Lagna - Thanos line. */
      public V side() {
         V d = this.toThanos();
         return new V(-d.z, 0.0, d.x);
      }
   }

   public record Shot(V pos, V target, float fov, boolean cut) {
   }

   /** Od Lagna's height in blocks (Reinhard's rig at {@link #OD_SCALE}). */
   public static final double OD_SCALE = 1.9;
   public static final double OD_HEIGHT = 1.95 * OD_SCALE;

   /** Od Lagna's head and chest (world). */
   public static V odHead(Frame f) {
      return f.od.up(OD_HEIGHT * 0.86);
   }

   public static V odChest(Frame f) {
      return f.od.up(OD_HEIGHT * 0.66);
   }

   /** Ticks at which the plan cuts (no interpolation across them). */
   public static final float[] NEAR_CUTS = cuts(ThanosSceneTimeline.RAISE_END, ThanosSceneTimeline.STONES_END, ThanosSceneTimeline.FACE_END, SNAP_CUT,
      ThanosSceneTimeline.VOID_END);
   public static final float[] FAR_CUTS = cuts(ThanosSceneTimeline.VOID_END);

   /** The given cuts, then the first line (Od Lagna has formed), every line change and the end of the speech. */
   private static float[] cuts(float... before) {
      float[] out = new float[before.length + ThanosSceneTimeline.LINES + 1];
      System.arraycopy(before, 0, out, 0, before.length);
      for (int i = 0; i <= ThanosSceneTimeline.LINES; i++) {
         out[before.length + i] = ThanosSceneTimeline.SPEECH_START + i * ThanosSceneTimeline.LINE_TICKS;
      }

      return out;
   }

   /** Is there a cut in (t0, t1]? */
   public static boolean cutBetween(boolean near, float t0, float t1) {
      for (float c : near ? NEAR_CUTS : FAR_CUTS) {
         if (t0 < c && t1 >= c) {
            return true;
         }
      }

      return false;
   }

   /** The camera at scene time t. */
   public static Shot at(Frame f, float t) {
      Shot s = f.near ? near(f, t) : far(f, t);
      V eyeTarget = f.eye.add(f.look.scale(8.0));
      // leave the player's eyes, come back into them
      if (t < LEAVE) {
         double k = smooth(t / LEAVE);
         s = new Shot(f.eye.lerp(s.pos, k), eyeTarget.lerp(s.target, k), (float)(NORMAL_FOV + (s.fov - NORMAL_FOV) * k), false);
      } else if (t > BACK_START) {
         double k = smooth((t - BACK_START) / (BACK_END - BACK_START));
         s = new Shot(s.pos.lerp(f.eye, k), s.target.lerp(eyeTarget, k), (float)(s.fov + (NORMAL_FOV - s.fov) * k), false);
      }

      if (!s.pos.finite() || !s.target.finite()) {
         return new Shot(f.eye, eyeTarget, NORMAL_FOV, false);
      }

      return s;
   }

   /** Letterbox and FOV weight 0..1: in over the first ticks, out on the way back. */
   public static float weight(float t) {
      if (t < 0.0F || t >= ThanosSceneTimeline.LENGTH) {
         return 0.0F;
      }

      float in = (float)smooth(t / 12.0F);
      float out = 1.0F - (float)smooth((t - BACK_START) / (BACK_END - BACK_START));
      return Math.min(in, out);
   }

   // ---- near: around Thanos -------------------------------------------------------------------

   private static Shot near(Frame f, float t) {
      V o = f.origin;
      V fw = f.fwd;
      V l = f.left;
      V r = l.scale(-1.0);
      if (t < ThanosSceneTimeline.RAISE_END) {
         // wide: he straightens and raises the gauntlet; a slow dolly in from the front-left
         double k = smooth(t / ThanosSceneTimeline.RAISE_END);
         V from = o.add(fw.scale(9.0)).add(l.scale(3.6)).up(1.9);
         V to = o.add(fw.scale(7.2)).add(l.scale(3.0)).up(2.4);
         V aim = o.up(2.5).lerp(f.gauntlet.lerp(o.up(3.0), 0.5), k);
         return new Shot(from.lerp(to, k), aim, 62.0F, false);
      }

      if (t < ThanosSceneTimeline.STONES_END) {
         // the gauntlet: the stones light one by one, a slow orbit and push in
         double k = smooth((t - ThanosSceneTimeline.RAISE_END) / (ThanosSceneTimeline.STONES_END - ThanosSceneTimeline.RAISE_END));
         V n = stoneSide(f);
         V around = rotateY(n, Math.toRadians(-18.0 + 30.0 * k));
         double dist = 2.7 - 0.8 * k;
         return new Shot(f.stones.add(around.scale(dist)).up(0.25 - 0.15 * k), f.stones, 46.0F, true);
      }

      if (t < ThanosSceneTimeline.FACE_END) {
         // his face: calm, certain
         double k = smooth((t - ThanosSceneTimeline.STONES_END) / (ThanosSceneTimeline.FACE_END - ThanosSceneTimeline.STONES_END));
         V face = f.head.up(0.05);
         V pos = face.add(fw.scale(2.9 - 0.5 * k)).add(r.scale(0.55)).up(-0.25);
         return new Shot(pos, face, 44.0F, true);
      }

      if (t < SNAP_CUT) {
         // the fingers fold slowly - and the click is seen on them
         double k = smooth((t - ThanosSceneTimeline.FACE_END) / (ThanosSceneTimeline.SNAP - ThanosSceneTimeline.FACE_END));
         V n = stoneSide(f);
         V aside = rotateY(n, Math.toRadians(52.0)).add(l.scale(0.25)).normalize();
         V hand = f.gauntlet.lerp(f.stones, 0.35);
         return new Shot(hand.add(aside.scale(1.9 - 0.45 * k)).up(0.12), hand, 40.0F, true);
      }

      if (t < ThanosSceneTimeline.DISSOLVE_START) {
         // the shock wave: medium-wide, the ring leaves the gauntlet
         double k = smooth((t - SNAP_CUT) / (ThanosSceneTimeline.DISSOLVE_START - SNAP_CUT));
         V pos = o.add(fw.scale(7.0 + 0.8 * k)).add(l.scale(2.4)).up(2.3 + 0.3 * k);
         return new Shot(pos, f.gauntlet.lerp(o.up(2.8), 0.4), 64.0F, true);
      }

      if (t < ThanosSceneTimeline.DISSOLVE_END) {
         // pull back and up over the world coming apart from the gauntlet outward
         double k = ease((t - ThanosSceneTimeline.DISSOLVE_START) / (ThanosSceneTimeline.DISSOLVE_END - ThanosSceneTimeline.DISSOLVE_START));
         V from = o.add(fw.scale(7.8)).add(l.scale(2.4)).up(2.6);
         V to = o.add(fw.scale(24.0)).add(l.scale(11.0)).up(13.0);
         V aim = f.gauntlet.lerp(o.up(2.4), 0.4).lerp(o.up(3.0), k);
         return new Shot(from.lerp(to, k), aim, (float)(64.0 + 6.0 * k), false);
      }

      if (t < ThanosSceneTimeline.VOID_END) {
         // the void: he stands alone in it, a slow drift down and around
         double k = smooth((t - ThanosSceneTimeline.DISSOLVE_END) / (ThanosSceneTimeline.VOID_END - ThanosSceneTimeline.DISSOLVE_END));
         V a = o.add(fw.scale(24.0)).add(l.scale(11.0)).up(13.0);
         V b = o.add(fw.scale(15.0)).add(l.scale(6.0)).up(5.5);
         return new Shot(a.lerp(b, k), o.up(3.0), 70.0F, false);
      }

      V od = odChest(f);
      V oh = odHead(f);
      V d = f.toThanos();
      V side = f.side();
      if (t < ThanosSceneTimeline.ARRIVAL_END) {
         // over his shoulder: Od Lagna forms out of white-gold light in front of him
         double k = smooth((t - ThanosSceneTimeline.VOID_END) / (ThanosSceneTimeline.ARRIVAL_END - ThanosSceneTimeline.VOID_END));
         V pos = o.add(d.scale(3.6 + 0.6 * k)).add(side.scale(-2.3)).up(4.1 - 0.2 * k);
         return new Shot(pos, od.lerp(oh, 0.3), 58.0F, true);
      }

      int line = ThanosSceneTimeline.line(t);
      if (line >= 0) {
         double k = smooth(ThanosSceneTimeline.lineTime(t) / ThanosSceneTimeline.LINE_TICKS);
         V th = f.head;
         V mid = o.lerp(f.od, 0.5);
         return switch (line) {
            // 1: Od Lagna, medium close-up from his side
            case 0 -> new Shot(oh.add(d.scale(6.2 - 0.9 * k)).add(side.scale(1.4)).up(-0.4), oh.up(-0.15), 48.0F, true);
            // 2: Thanos hears it - the reverse shot
            case 1 -> new Shot(th.add(fw.scale(3.3 - 0.4 * k)).add(r.scale(0.9)).up(-0.2), th, 46.0F, true);
            // 3: the two of them in the empty universe
            case 2 -> new Shot(mid.add(side.scale(15.0 - 2.0 * k)).up(4.0 + 1.0 * k), mid.up(2.6), 62.0F, true);
            // 4: Od Lagna from below, the judgement
            case 3 -> new Shot(oh.add(d.scale(3.4 - 0.4 * k)).add(side.scale(-0.8)).up(-1.6), oh.up(0.1), 44.0F, true);
            // 5: over Od Lagna's shoulder onto Thanos
            case 4 -> new Shot(oh.add(d.scale(-1.9)).add(side.scale(1.5)).up(0.5 - 0.3 * k), th.lerp(o.up(2.4), 0.3), 50.0F, true);
            // 6: Od Lagna rises, the camera pulls back
            default -> new Shot(od.add(d.scale(6.5 + 6.0 * k)).add(side.scale(2.0 + 2.0 * k)).up(0.8 + 3.0 * k), od.up(0.6 * k), (float)(54.0 + 10.0 * k),
               true);
         };
      }

      // the restore: wide from Od Lagna's side, the golden front runs out over the world
      double k = smooth((t - ThanosSceneTimeline.SPEECH_END) / (ThanosSceneTimeline.RESTORE_END - ThanosSceneTimeline.SPEECH_END));
      V pos = f.od.add(d.scale(13.0 + 4.0 * k)).add(side.scale(4.0 + 3.0 * k)).up(4.4 + 4.0 * k);
      return new Shot(pos, f.od.up(2.0).lerp(o.up(1.0), 0.5), 68.0F, true);
   }

   /** Direction from the hand to its stones (where the back of the gauntlet faces), flat-ish. */
   static V stoneSide(Frame f) {
      V n = f.stones.sub(f.gauntlet);
      n = new V(n.x, n.y * 0.35, n.z).normalize();
      if (n.length() < 0.5) {
         n = f.fwd;
      }

      // never from behind him: keep the side facing his front
      double front = n.x * f.fwd.x + n.z * f.fwd.z;
      if (front < -0.2) {
         n = n.add(f.fwd.scale(0.8)).normalize();
      }

      return n;
   }

   // ---- far: around the player ----------------------------------------------------------------

   private static Shot far(Frame f, float t) {
      V eye = f.eye;
      V look = f.look.flat().normalize();
      if (look.length() < 0.5) {
         look = f.fwd;
      }

      V side = new V(-look.z, 0.0, look.x);
      V ahead = eye.add(f.look.scale(8.0));
      if (t < ThanosSceneTimeline.DISSOLVE_START) {
         // the world stops in the player's own view; the gaze drifts up a little at the snap
         double k = smooth(t / ThanosSceneTimeline.DISSOLVE_START);
         return new Shot(eye, ahead.up(1.5 * k), (float)(70.0 - 10.0 * k), false);
      }

      if (t < ThanosSceneTimeline.VOID_END) {
         // rise over the player while everything comes apart
         double k = ease((t - ThanosSceneTimeline.DISSOLVE_START) / (ThanosSceneTimeline.VOID_END - ThanosSceneTimeline.DISSOLVE_START));
         V from = eye;
         V to = eye.add(look.scale(-7.5)).add(side.scale(3.0)).up(5.0);
         V aim = ahead.up(1.5).lerp(eye.up(-0.6), k);
         return new Shot(from.lerp(to, k), aim, (float)(60.0 + 8.0 * k), false);
      }

      V od = odChest(f);
      V oh = odHead(f);
      V d = f.toThanos();
      V s = f.side();
      if (t < ThanosSceneTimeline.ARRIVAL_END) {
         double k = smooth((t - ThanosSceneTimeline.VOID_END) / (ThanosSceneTimeline.ARRIVAL_END - ThanosSceneTimeline.VOID_END));
         return new Shot(eye.add(look.scale(-2.5 + 0.8 * k)).add(side.scale(1.8)).up(1.6), od.lerp(oh, 0.3), 60.0F, true);
      }

      int line = ThanosSceneTimeline.line(t);
      if (line >= 0) {
         double k = smooth(ThanosSceneTimeline.lineTime(t) / ThanosSceneTimeline.LINE_TICKS);
         return switch (line % 3) {
            case 0 -> new Shot(oh.add(d.scale(5.8 - 0.8 * k)).add(s.scale(1.2)).up(-0.4), oh.up(-0.15), 48.0F, true);
            case 1 -> new Shot(eye.lerp(od, 0.5).add(s.scale(11.0 - 1.5 * k)).up(3.0 + 0.8 * k), eye.lerp(od, 0.5).up(1.6), 62.0F, true);
            default -> new Shot(oh.add(d.scale(3.4 - 0.4 * k)).add(s.scale(-0.8)).up(-1.5), oh.up(0.1), 44.0F, true);
         };
      }

      double k = smooth((t - ThanosSceneTimeline.SPEECH_END) / (ThanosSceneTimeline.RESTORE_END - ThanosSceneTimeline.SPEECH_END));
      return new Shot(f.od.add(d.scale(11.0 + 3.0 * k)).add(s.scale(3.0)).up(3.5 + 3.0 * k), f.od.up(2.0).lerp(eye, 0.5), 68.0F, true);
   }

   // ---- helpers ---------------------------------------------------------------------------------

   static V rotateY(V v, double radians) {
      double c = Math.cos(radians);
      double s = Math.sin(radians);
      return new V(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
   }

   static double clamp(double v) {
      return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.0;
   }

   public static double smooth(double v) {
      double t = clamp(v);
      return t * t * (3.0 - 2.0 * t);
   }

   public static double ease(double v) {
      double t = clamp(v);
      return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
   }
}
