package dev.baranhan.viltrumitecore.client.ironman.mark;

import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.ironman.IronManCombatParts;
import dev.baranhan.viltrumitecore.client.ironman.veronica.PartWrapAnimator;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.mark.EquipTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SuitPart;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client entry of the Stage 4 mark visuals (animation-system §7.1, §7.2):
 * the mark skin plus the mark's own Satsu parts ({@link MarkExtras}) when the
 * whole suit is on; otherwise Tony's skin with the suit pieces
 * ({@link MarkParts}) on the parts that are on or wrapping; parts still
 * flying are drawn in the world by {@link PartFlightVisuals}. Pods, empty suits and debris use
 * their own whole-entity renderers.
 */
public final class MarkVisuals {
   private MarkVisuals() {
   }

   public static void init() {
   }

   public static void addLayers(PlayerRenderer renderer) {
   }

   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ViltrumiteEntities.VERONICA_POD.get(), dev.baranhan.viltrumitecore.client.ironman.veronica.VeronicaPodRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.EMPTY_SUIT.get(), EmptySuitRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.SUIT_DEBRIS.get(), SuitDebrisRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.LEGION_SUIT.get(), dev.baranhan.viltrumitecore.client.ironman.legion.LegionSuitRenderer::new);
   }

   /** Whole-suit skin: the mark (helmet open = Tony's head). Empty when Tony's skin or the plates show. */
   public static Optional<ResourceLocation> skin(AbstractClientPlayer player, HeroPublicSnapshot snapshot) {
      MarkState state = MarkState.of(snapshot);
      if (!state.full()) {
         return Optional.empty();
      }

      float closed = dev.baranhan.viltrumitecore.client.ironman.HelmetAnim.progress(player, snapshot, net.minecraft.client.Minecraft.getInstance().getFrameTime());
      return Optional.of(MarkSkins.INSTANCE.skin(state.mark(), closed < 0.999F));
   }

   public static void collectParts(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      MarkState state = MarkState.of(snapshot);
      if (!state.markOn()) {
         return;
      }

      if (state.full()) {
         PlayerGeoLayer.Part extras = MarkExtras.part(state.mark(), IronManCombatParts.flapOpen(player, snapshot, partialTick));
         if (extras != null) {
            out.add(extras);
         }

         if (!firstPerson) {
            PlayerGeoLayer.Part plate = MarkFaceplate.part(state.mark(), dev.baranhan.viltrumitecore.client.ironman.HelmetAnim.progress(player, snapshot, partialTick));
            if (plate != null) {
               out.add(plate);
            }
         }

         return;
      }

      // Exit: the empty suit opens around Tony, who walks out without any plates.
      if (state.equipPhase() == IronManFlags.EQUIP_EXITING) {
         return;
      }

      List<SuitPart> parts = SuitPart.of(state.mark());
      int count = parts.size();
      float elapsed = state.elapsed() + partialTick;
      boolean delivery = state.delivery();
      for (int i = 0; i < count; i++) {
         SuitPart part = parts.get(i);
         boolean helmet = part.bone() == SuitPart.Bone.HEAD;
         List<PlayerGeoLayer.Pass> passes = MarkParts.passes(state.mark(), part);
         if (state.equipping() && EquipTimeline.phase(i, count, delivery, elapsed) == EquipTimeline.Phase.WRAPPING) {
            float progress = EquipTimeline.phaseProgress(i, count, delivery, elapsed);
            List<PlayerGeoLayer.Pass> wrapPasses = PartWrapAnimator.flash(progress) > 0.02F ? withFlash(passes) : passes;
            out.add(new PlayerGeoLayer.Part(MarkParts.geo(state.mark(), part), wrapPasses, geo -> PartWrapAnimator.pose(geo, progress)));
            continue;
         }

         if (!state.partPresent(i)) {
            continue;
         }

         if (helmet && !state.helmetClosed() && state.equipPhase() == 0) {
            continue;
         }

         out.add(new PlayerGeoLayer.Part(MarkParts.geo(state.mark(), part), passes));
      }
   }

   /** Click flash: the cutout textures drawn once more as glow. */
   private static List<PlayerGeoLayer.Pass> withFlash(List<PlayerGeoLayer.Pass> passes) {
      List<PlayerGeoLayer.Pass> out = new java.util.ArrayList<>(passes);
      for (PlayerGeoLayer.Pass pass : passes) {
         if (pass.kind() == PlayerGeoLayer.Pass.Kind.CUTOUT) {
            out.add(PlayerGeoLayer.Pass.glow(pass.texture()));
         }
      }

      return out;
   }
}
