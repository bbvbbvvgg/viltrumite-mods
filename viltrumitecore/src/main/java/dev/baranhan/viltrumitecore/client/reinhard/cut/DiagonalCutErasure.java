package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureTestSwipe;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * REINHARD OVERHAUL item 10: the wound of the diagonal World Cut is the existing The Hand erasure of
 * {@code /erasuretest} (the friend's shader, PR #43), in place of the narrow aperture rift.
 *
 * <p>The stroke is the original sandbox swipe of {@link ErasureTestSwipe} at {@link #SCALE}: the same path, tube,
 * glass, shell and aura in the original proportions, so its volume and depth are the original ones, only large
 * enough to span the cut. Its chord is laid on the cut's span (it is cut from the upper right to the lower left, the
 * way the blade comes down) and its bow on the cut's forward axis, so it lies in the diagonal plane of
 * {@link DiagonalCutGeometry}, 30 to 36 blocks ahead of the caster. It starts on the BLAST of the shared server clock
 * (the impact, not the swing), is cut in {@link #REVEAL} seconds, stays open {@link #HOLD} seconds with slow pulses
 * and a soft cyan-green shimmer of its colours, then zips shut from the tail over {@link #CLOSE} seconds: gone ten
 * seconds after the impact.
 *
 * <p>Client only and cosmetic: no packet, damage, hitbox or audio change (the diagonal cut keeps its own sounds).
 * A wound is kept from any packet of its cast (late trackers included) independently of the scene's own pruning,
 * dropped once shut, on a level change, on unload or disconnect; at most {@link #MAX}. The renderer culls it by
 * distance and frustum like every stroke.
 */
public final class DiagonalCutErasure implements ErasureRenderer.Provider {
   public static final DiagonalCutErasure INSTANCE = new DiagonalCutErasure();
   /**
    * The /erasuretest swipe at this scale spans the cut: about 42 blocks end to end (its chord ends 20.9 blocks
    * either side of the axis, inside the narrowest width the cut can have, 28 * 0.76), a tube about 5.6 blocks thick.
    */
   static final float SCALE = 16.5F;
   /** Seconds the head takes from the upper right to the lower left. */
   static final float REVEAL = 0.25F;
   /** Seconds open after the impact, then seconds zipping shut. */
   static final float HOLD = 7.5F, CLOSE = 2.5F;
   static final int MAX = 16;
   private final Map<UUID, Wound> wounds = new LinkedHashMap<>();

   /** One cast: the client level it belongs to, the server clock of its start and its stroke path. */
   record Wound(@Nullable Object level, long startTime, ErasurePath path, float seed) {
   }

   private DiagonalCutErasure() {
   }

   /** Any packet of a cast starts tracking it (once). */
   public void track(DiagonalCutS2CPacket packet, @Nullable Object level) {
      if (wounds.containsKey(packet.castId())) return;
      if (wounds.size() >= MAX) {
         Iterator<UUID> oldest = wounds.keySet().iterator();
         oldest.next();
         oldest.remove();
      }
      DiagonalCutGeometry shape = packet.shape();
      wounds.put(packet.castId(), new Wound(level, packet.startTime(), ErasurePath.smooth(List.of(path(shape))),
         (shape.seed() & 0xFFFF) / 65536F));
   }

   public void clear() {
      wounds.clear();
   }

   int size() {
      return wounds.size();
   }

   @Override
   public void collect(List<ErasureStroke> out, float partialTick, long gameTime) {
      if (wounds.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      Object level = mc == null ? null : mc.level;
      prune(level, gameTime);
      for (Wound wound : wounds.values()) {
         ErasureStroke stroke = stroke(wound, sinceImpact(wound.startTime(), gameTime, partialTick));
         if (stroke != null) out.add(stroke);
      }
   }

   /** Drops wounds of another level and wounds already shut. */
   void prune(@Nullable Object level, long gameTime) {
      wounds.values().removeIf(w -> w.level() != level || sinceImpact(w.startTime(), gameTime, 0F) > HOLD + CLOSE);
   }

   /** Seconds after the impact: the BLAST tick of the cast on the shared server clock. */
   static float sinceImpact(long startTime, long gameTime, float partialTick) {
      return (float)((gameTime - startTime - ReinhardRules.WORLD_CUT_BLAST + (double)partialTick) / 20.0);
   }

   /** The stroke of a wound {@code since} seconds after the impact; null before the impact and once shut. */
   static @Nullable ErasureStroke stroke(Wound wound, float since) {
      if (!(since >= 0) || since > HOLD + CLOSE) return null;
      float length = wound.path().length();
      return new ErasureStroke(wound.path(), 0F, length, SCALE, wound.seed(), style(since, wound.seed()), false)
         .updateHeld(since, length * reveal(since), REVEAL, HOLD, CLOSE, swell(since, wound.seed()));
   }

   /** Head position over the stroke, the original ease-out reveal stretched to {@link #REVEAL}. */
   static float reveal(float since) {
      float x = Math.max(0F, Math.min(1F, since / REVEAL));
      return 1F - (1F - x) * (1F - x);
   }

   /** Slow pulses while it holds open (0..1): a soft swell every 1.7 s, none during the cut, gone as it shuts. */
   static float swell(float since, float seed) {
      float in = smoothstep(REVEAL, REVEAL + 0.8F, since);
      float out = 1F - smoothstep(HOLD, HOLD + CLOSE * 0.6F, since);
      double wave = 0.5 + 0.5 * Math.sin(2 * Math.PI * (since / 1.7 + seed));
      return (float)(in * out * wave * wave * wave);
   }

   /** The original colours with a slow cyan-green shimmer and a brightening on every pulse. */
   static ErasureStyle style(float since, float seed) {
      float in = smoothstep(REVEAL, REVEAL + 0.8F, since);
      float shift = (float)(0.16 + 0.14 * Math.sin(2 * Math.PI * (since / 3.1 + seed * 2)));
      float lift = 0.12F * swell(since, seed) + (float)(0.05 * Math.sin(2 * Math.PI * (since / 2.3 + seed * 3)));
      return ErasureStyle.shimmer(lift * in, shift * in);
   }

   /** The original swipe, its chord rotated onto the cut's span and its bow along the cut, in the cut plane. */
   static Vec3[] path(DiagonalCutGeometry shape) {
      Vec3[] swipe = ErasureTestSwipe.originalSwipe();
      Vec3 first = swipe[0], last = swipe[swipe.length - 1];
      double angle = Math.atan2(first.y - last.y, first.x - last.x), cos = Math.cos(angle), sin = Math.sin(angle);
      double middle = 0, chord = 0;
      for (Vec3 p : new Vec3[]{first, last}) {
         middle += 0.5 * (p.x * cos + p.y * sin);
         chord += 0.5 * (-p.x * sin + p.y * cos);
      }
      List<Vec3> points = new ArrayList<>(swipe.length);
      for (Vec3 p : swipe) {
         double along = p.x * cos + p.y * sin - middle, off = -p.x * sin + p.y * cos - chord;
         // View right -> the span (upper right); view up -> the plane normal's opposite (upper left); view forward
         // -> the cut's forward axis. The original sits about two blocks ahead of the eyes: here 30 to 36 of the cut.
         points.add(shape.point(p.z * SCALE, along * SCALE, -off * SCALE));
      }
      return points.toArray(Vec3[]::new);
   }

   /** Middle of the stroke along the cut, blocks: the cut's wave sets off from here. */
   static double along(DiagonalCutGeometry shape) {
      Vec3[] points = path(shape);
      return points[points.length / 2].subtract(shape.origin()).dot(shape.forward());
   }

   private static float smoothstep(float edge0, float edge1, float x) {
      float t = Math.max(0F, Math.min(1F, (x - edge0) / (edge1 - edge0)));
      return t * t * (3F - 2F * t);
   }
}
