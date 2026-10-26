package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
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
 * <p>Playtest item 4 (3b): the wound spans the cut's whole diagonal sweep. PR #49 laid the /erasuretest swipe at
 * scale 16.5 on the cut, 30 to 36 blocks ahead: scaling the swipe about the eyes keeps its angular size, so it
 * covered a third of the diagonal the blade cuts (42 blocks of a 56-block-wide cut, a short horn from most angles).
 * Now it is one arc in the cut plane ({@link #path}): its tips at {@link #REACH} of the cut's local half width on
 * either side (from the upper right, where the blade starts, to the lower left), {@link #TIP_ALONG} blocks ahead where
 * the cut has its full width, its middle bowed {@link #BOW} blocks further along the cut like the sweep of a blade.
 * The transverse sweep is what grew (tip to tip about 50 blocks); the forward reach stays in the first fifth of the
 * cut. From the caster's eyes the plane is seen edge on, so the wound crosses the whole screen diagonally; from any
 * other point it is a crescent about 75 blocks long and {@link #SCALE} makes it about 8.8 blocks thick. The tail opens
 * and the body grows over a short share of the arc ({@link #PROFILE}), so nearly all of it is at full width; the head
 * keeps the original round cap.
 *
 * <p>It starts on the BLAST of the shared server clock (the impact, not the swing), is cut in {@link #REVEAL}
 * seconds, stays open {@link #HOLD} seconds with slow pulses and a soft cyan-green shimmer of its colours, then zips
 * shut from the tail over {@link #CLOSE} seconds: gone ten seconds after the impact.
 *
 * <p>Client only and cosmetic: no packet, damage, hitbox or audio change (the diagonal cut keeps its own sounds).
 * A wound is kept from any packet of its cast (late trackers included) independently of the scene's own pruning,
 * dropped once shut, on a level change, on unload or disconnect; at most {@link #MAX}. The renderer culls it by
 * distance and frustum like every stroke; it is one stroke, one draw per pass.
 */
public final class DiagonalCutErasure implements ErasureRenderer.Provider {
   public static final DiagonalCutErasure INSTANCE = new DiagonalCutErasure();
   /** About 8.8 blocks thick: the original proportions (about 8.5 widths long) for a 75-block arc. */
   static final float SCALE = 26F;
   /** Tips of the arc this far along the cut: past the cut's opening taper (full width from 16 blocks on). */
   static final double TIP_ALONG = 18;
   /** The middle of the arc leads its tips by this much along the cut. */
   static final double BOW = 22;
   /** Tips at this share of the cut's local half width: inside the cut, nearly edge to edge. */
   static final double REACH = 0.93;
   /** Control points of the arc, smoothed by Catmull-Rom. */
   static final int ARC_POINTS = 24;
   /** Width along the arc: the tail opens over 17 %, the body grows from 70 % to full by 65 %; the original ripple. */
   static final ErasureStroke.Profile PROFILE = new ErasureStroke.Profile(0.17F, 0.7F, 0.08F, 0.65F, 0.04F, 7.5F);
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
      return new ErasureStroke(wound.path(), 0F, length, SCALE, wound.seed(), style(since, wound.seed()), false, PROFILE)
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

   /**
    * The arc in the cut plane, from the upper right (+span, where the blade starts) to the lower left: at angle
    * theta from 0 to pi it is {@link #TIP_ALONG} + {@link #BOW} sin(theta) along the cut and {@link #REACH} of the
    * local half width times cos(theta) across it, so every point stays inside the cut whatever its wiggle.
    */
   static Vec3[] path(DiagonalCutGeometry shape) {
      Vec3[] points = new Vec3[ARC_POINTS + 1];
      for (int i = 0; i <= ARC_POINTS; i++) {
         double theta = Math.PI * i / ARC_POINTS;
         double along = TIP_ALONG + BOW * Math.sin(theta);
         points[i] = shape.point(along, REACH * shape.width(along) * Math.cos(theta), 0);
      }
      return points;
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
