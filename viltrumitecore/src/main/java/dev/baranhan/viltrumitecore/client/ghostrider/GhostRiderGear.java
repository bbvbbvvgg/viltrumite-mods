package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderMove;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * The Rider's 3D parts on the vanilla body (PlayerGeoLayer): the skull with its jaw, collar, belt buckle and boot
 * straps (both forms), the spiked pads, chest chain and spurs (the Rider), and the emissive crack overlay of the
 * burn frames. The pose hook shows the right bones for the frame and works the jaw; the frame hook records anchors.
 */
public final class GhostRiderGear implements PlayerGeoLayer.Provider {
   public static final GhostRiderGear INSTANCE = new GhostRiderGear();
   private static final List<PlayerGeoLayer.Pass> GEAR = List.of(PlayerGeoLayer.Pass.cutout(GhostRiderView.GEAR_TEX),
      PlayerGeoLayer.Pass.glow(GhostRiderView.GEAR_GLOW));
   @SuppressWarnings("unchecked")
   private static final List<PlayerGeoLayer.Pass>[] GLOW = new List[GhostRiderRules.BURN_FRAMES + 1];

   static {
      for (int i = 1; i < GLOW.length; i++) {
         GLOW[i] = List.of(PlayerGeoLayer.Pass.glow(GhostRiderView.SKIN_GLOWS[i]));
      }
   }

   private GhostRiderGear() {
   }

   @Override
   public void collect(AbstractClientPlayer player, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      HeroPublicSnapshot s = GhostRiderView.of(player);
      if (s == null) {
         return;
      }
      int frame = GhostRiderView.burnFrame(s);
      float jaw = jawOpen(s, GhostRiderView.elapsed(s, partialTick), player.tickCount + partialTick);
      boolean chainOut = GhostRiderView.chainOut(s);
      float pop = skullPop(s, partialTick);
      out.add(new PlayerGeoLayer.Part(GhostRiderView.GEAR_GEO, GEAR, geo -> pose(geo, frame, jaw, chainOut, pop),
         (stack, bone) -> GhostRiderAnchors.record(player, stack, bone.name)));
      if (frame > 0) {
         out.add(new PlayerGeoLayer.Part(GhostRiderView.SKIN_GLOW_GEO, GLOW[frame]));
      }
   }

   static void pose(BakedGeoModel geo, int frame, float jaw, boolean chainOut, float pop) {
      geo.resetBones();
      boolean skull = frame >= GhostRiderView.SKULL_FRAME;
      boolean riderGear = frame >= GhostRiderView.SKULL_FRAME;
      hide(geo.getBone("skull"), !skull);
      hide(geo.getBone("jaw"), !skull);
      for (String name : new String[]{"r_pad", "l_pad", "r_spur", "l_spur"}) {
         hide(geo.getBone(name), !riderGear);
      }
      hide(geo.getBone("chest_chain"), !riderGear || chainOut);
      GeoBone jawBone = geo.getBone("jaw");
      if (jawBone != null) {
         // Blockbench +X drops the chin (see HellCycleRenderer.deg: rotX = -degrees)
         jawBone.rotX -= jaw;
      }
      GeoBone skullBone = geo.getBone("skull");
      if (skullBone != null && pop != 1.0F) {
         skullBone.scaleX = pop;
         skullBone.scaleY = pop;
         skullBone.scaleZ = pop;
      }
   }

   private static void hide(@Nullable GeoBone bone, boolean hidden) {
      if (bone != null) {
         bone.hidden = hidden;
      }
   }

   /** The skull swells out of the burning head and settles (transformation only). */
   static float skullPop(HeroPublicSnapshot s, float partial) {
      if (GhostRiderView.move(s) != GhostRiderMove.TRANSFORM) {
         return 1.0F;
      }
      float t = GhostRiderView.elapsed(s, partial);
      float flare = GhostRiderRules.TRANSFORM_FLARE_TICK;
      if (t < flare - 6.0F) {
         return 0.9F;
      }
      if (t < flare) {
         return 0.9F + 0.18F * (t - (flare - 6.0F)) / 6.0F;
      }
      return 1.08F - 0.08F * GhostRiderView.clamp01((t - flare) / 6.0F);
   }

   /** Jaw opening (radians): a slow chatter, the roar of the transformation, the stare, the hellfire wave. */
   static float jawOpen(HeroPublicSnapshot s, float t, float age) {
      float open = 0.04F + 0.03F * Mth.sin(age * 0.21F);
      GhostRiderMove move = GhostRiderView.move(s);
      if (move == null) {
         return open;
      }
      switch (move) {
         case TRANSFORM -> open = Math.max(open, 0.62F * bump(t, GhostRiderRules.TRANSFORM_FLARE_TICK - 3, GhostRiderRules.TRANSFORM_TICKS, 3.0F)
            * (0.9F + 0.1F * Mth.sin(age * 2.1F)));
         case STARE -> open = Math.max(open, 0.30F * bump(t, 4.0F, GhostRiderRules.STARE_VERDICT, 4.0F)
            + 0.45F * bump(t, GhostRiderRules.STARE_VERDICT - 1, GhostRiderRules.STARE_TICKS, 2.0F));
         case WAVE -> open = Math.max(open, 0.55F * bump(t, 3.0F, GhostRiderRules.WAVE_TICKS, 3.0F));
         case WHIRL -> open = Math.max(open, 0.28F);
         case SLAM -> open = Math.max(open, 0.45F * bump(t, GhostRiderRules.SLAM_CONTACT - 3, GhostRiderRules.SLAM_TICKS, 2.0F));
         case DASH -> open = Math.max(open, 0.32F);
         default -> {
         }
      }
      return open;
   }

   /** Smooth 0 - 1 - 0 window over [a, b] with ramps of {@code ramp} ticks. */
   static float bump(float t, float a, float b, float ramp) {
      if (t <= a || t >= b) {
         return 0.0F;
      }
      float w = Math.min(1.0F, Math.min((t - a) / ramp, (b - t) / ramp));
      return w * w * (3.0F - 2.0F * w);
   }
}
