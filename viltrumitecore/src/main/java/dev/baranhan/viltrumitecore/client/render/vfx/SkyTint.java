package dev.baranhan.viltrumitecore.client.render.vfx;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared sky / cloud / fog tint and weather fade with prioritised sources.
 * The strongest live source wins and is blended over {@link #NONE} by its
 * weight, which eases in after {@link #push} and fades out after
 * {@link #remove} (real time, skill rule 6). Read by {@code SkyTintMixin}
 * (sky, clouds), {@code LevelRainMixin} (rain, thunder on the client level)
 * and {@link SkyTintEvents} (fog). Under a shader pack only the fog part may
 * show. Render thread only; the instance form is for tests.
 */
public final class SkyTint {
   /**
    * Colour multipliers, extra darkening (0 none, 1 black) and the rain / thunder multiplier. The optional
    * cover colour ({@code cr, cg, cb}) replaces the hue by {@code cover} (0 none, 1 full) while it keeps the
    * brightness of the vanilla colour, so a cover tint is bright by day and dark at night. A multiplier alone
    * cannot turn a blue sky red; old five-value tints have cover 0 and work as before.
    */
   public record Tint(float r, float g, float b, float darken, float rain, float cr, float cg, float cb, float cover) {
      public Tint(float r, float g, float b, float darken, float rain) {
         this(r, g, b, darken, rain, 1.0F, 1.0F, 1.0F, 0.0F);
      }
   }

   public static final Tint NONE = new Tint(1.0F, 1.0F, 1.0F, 0.0F, 1.0F);
   public static final SkyTint INSTANCE = new SkyTint();
   private static final float K_IN = 3.0F;
   private static final float K_OUT = 3.0F;
   private static final float GONE = 0.001F;

   private static final class Source {
      int priority;
      Supplier<Tint> tint;
      float weight;
      boolean removing;
   }

   private final Map<Object, Source> sources = new LinkedHashMap<>();

   /** Starts (or retargets) the tint of {@code owner}; the same owner again replaces its supplier and priority. */
   public void push(Object owner, int priority, Supplier<Tint> tint) {
      Source source = this.sources.computeIfAbsent(owner, key -> new Source());
      source.priority = priority;
      source.tint = tint;
      source.removing = false;
   }

   /** Fades the owner's tint out; it is dropped once invisible. */
   public void remove(Object owner) {
      Source source = this.sources.get(owner);
      if (source != null) {
         source.removing = true;
      }
   }

   public void clear() {
      this.sources.clear();
   }

   public boolean active() {
      return !this.sources.isEmpty();
   }

   /** Eases every weight by {@code dtSeconds} of real time. */
   public void advance(float dtSeconds) {
      Iterator<Source> it = this.sources.values().iterator();
      while (it.hasNext()) {
         Source source = it.next();
         float target = source.removing ? 0.0F : 1.0F;
         float k = source.removing ? K_OUT : K_IN;
         source.weight += (target - source.weight) * (1.0F - (float)Math.exp(-k * dtSeconds));
         if (source.removing && source.weight < GONE) {
            it.remove();
         }
      }
   }

   /** The tint to apply now: the highest priority source (the later push wins a tie) over NONE by its weight. */
   public Tint current() {
      Source best = null;
      for (Source source : this.sources.values()) {
         if (best == null || source.priority >= best.priority) {
            best = source;
         }
      }

      if (best == null) {
         return NONE;
      }

      Tint tint = best.tint.get();
      return tint == null ? NONE : mix(NONE, tint, best.weight);
   }

   /** Pure: linear blend of every field. */
   public static Tint mix(Tint base, Tint over, float w) {
      // the cover hue is the stronger side's hue (NONE has no hue), its amount blends like every other field
      Tint hue = over.cover >= base.cover ? over : base;
      return new Tint(lerp(base.r, over.r, w), lerp(base.g, over.g, w), lerp(base.b, over.b, w),
         lerp(base.darken, over.darken, w), lerp(base.rain, over.rain, w), hue.cr, hue.cg, hue.cb, lerp(base.cover, over.cover, w));
   }

   /** Pure: a colour under a tint. */
   public static double[] apply(double r, double g, double b, Tint tint) {
      double keep = 1.0 - tint.darken;
      double or = r * tint.r * keep;
      double og = g * tint.g * keep;
      double ob = b * tint.b * keep;
      if (tint.cover <= 0.0F) {
         return new double[]{or, og, ob};
      }

      // the cover colour at the brightness of the original colour (Rec. 601 luma), so night stays night
      double luma = 0.299 * r + 0.587 * g + 0.114 * b;
      double scale = keep * (0.18 + 1.25 * luma);
      double c = tint.cover;
      return new double[]{or + (Math.min(1.0, tint.cr * scale) - or) * c, og + (Math.min(1.0, tint.cg * scale) - og) * c,
         ob + (Math.min(1.0, tint.cb * scale) - ob) * c};
   }

   private static float lerp(float a, float b, float w) {
      return a + (b - a) * w;
   }
}
