package dev.baranhan.viltrumitecore.entity.chitauri;

/**
 * Pure: the path the Leviathan's head flew, as a ring buffer of points (oldest to newest). Body
 * segments sample it by arc length behind the head, so every segment passes exactly where the
 * head passed and the head's vertical undulation becomes a travelling wave along the body. Used
 * the same way on the server (hitboxes) and on the client (rendering).
 */
public final class LeviathanTrail {
   /** Minimum spacing of stored points; the head itself is always the newest sample. */
   public static final double MIN_STEP = 0.25;
   public static final int SEGMENTS = 10;
   /** Segment scales from the neck to the tail root; the tail piece follows at {@link #TAIL_SCALE}. */
   public static final float[] SCALE = {1.0F, 0.98F, 0.95F, 0.9F, 0.85F, 0.79F, 0.72F, 0.65F, 0.58F, 0.51F};
   public static final float TAIL_SCALE = 0.46F;
   /** Head centre to the first segment centre (blocks). */
   public static final double HEAD_GAP = 4.4;
   /** Segment spacing at scale 1 (blocks). */
   public static final double SPACING = 2.55;
   public static final double TAIL_GAP = 2.9;

   private final int cap;
   private final double[] x;
   private final double[] y;
   private final double[] z;
   private int start;
   private int count;

   public LeviathanTrail(int capacity) {
      this.cap = capacity;
      this.x = new double[capacity];
      this.y = new double[capacity];
      this.z = new double[capacity];
   }

   public int size() {
      return this.count;
   }

   public void clear() {
      this.start = 0;
      this.count = 0;
   }

   private int idx(int i) {
      return (this.start + i) % this.cap;
   }

   /** Records the head position (kept only when it moved at least {@link #MIN_STEP} from the newest stored point). */
   public void push(double px, double py, double pz) {
      if (this.count > 0) {
         int n = this.idx(this.count - 1);
         double dx = px - this.x[n], dy = py - this.y[n], dz = pz - this.z[n];
         if (dx * dx + dy * dy + dz * dz < MIN_STEP * MIN_STEP) {
            return;
         }
      }
      if (this.count == this.cap) {
         this.start = (this.start + 1) % this.cap;
         this.count--;
      }
      int i = this.idx(this.count);
      this.x[i] = px;
      this.y[i] = py;
      this.z[i] = pz;
      this.count++;
   }

   /** Fills a straight trail of {@code length} blocks ending at the head, coming from direction -d (unit). */
   public void prefill(double hx, double hy, double hz, double dx, double dy, double dz, double length) {
      this.clear();
      int n = (int)Math.ceil(length / 0.5);
      for (int i = n; i >= 1; i--) {
         double s = i * 0.5;
         this.push(hx - dx * s, hy - dy * s, hz - dz * s);
      }
   }

   /**
    * Point {@code dist} blocks of path behind the head at (hx, hy, hz), written to out[0..2]. Past the
    * oldest point the trail is extended straight along its last direction.
    */
   public void sample(double hx, double hy, double hz, double dist, double[] out) {
      double px = hx, py = hy, pz = hz;
      double left = dist;
      for (int i = this.count - 1; i >= 0; i--) {
         int k = this.idx(i);
         double dx = this.x[k] - px, dy = this.y[k] - py, dz = this.z[k] - pz;
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len >= left && len > 1.0E-9) {
            double f = left / len;
            out[0] = px + dx * f;
            out[1] = py + dy * f;
            out[2] = pz + dz * f;
            return;
         }
         left -= len;
         if (len > 1.0E-9) {
            px = this.x[k];
            py = this.y[k];
            pz = this.z[k];
         }
      }
      // extend along the oldest direction (or straight back when there is no history)
      double ex = 0, ey = 0, ez = -1;
      if (this.count >= 2) {
         int a = this.idx(0), b = this.idx(1);
         double dx = this.x[a] - this.x[b], dy = this.y[a] - this.y[b], dz = this.z[a] - this.z[b];
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len > 1.0E-9) {
            ex = dx / len;
            ey = dy / len;
            ez = dz / len;
         }
      }
      out[0] = px + ex * left;
      out[1] = py + ey * left;
      out[2] = pz + ez * left;
   }

   /** Arc distance of body piece {@code i} (0..SEGMENTS-1 segments, SEGMENTS = tail) behind the head centre. */
   public static double offset(int i) {
      double d = HEAD_GAP;
      int last = Math.min(i, SEGMENTS - 1);
      for (int k = 0; k < last; k++) {
         d += SPACING * (SCALE[k] + SCALE[k + 1]) * 0.5;
      }
      if (i >= SEGMENTS) {
         d += TAIL_GAP;
      }
      return d;
   }

   public static float scale(int i) {
      return i >= SEGMENTS ? TAIL_SCALE : SCALE[i];
   }

   /** Total body length behind the head centre (blocks). */
   public static double bodyLength() {
      return offset(SEGMENTS) + 2.5;
   }
}
