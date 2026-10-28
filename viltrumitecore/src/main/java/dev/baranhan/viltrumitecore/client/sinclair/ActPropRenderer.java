package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActPropEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The stage props of the acts (spec §6.1), each with its own motion on the act's tick table
 * ({@link ActScript}): NOVA's whirling top hat and its ring of cards, CUT's domino line of cards
 * and the sawing blade, AEGIS's ring of velvet curtains that rises, waves and bursts open, and
 * FINALE's falling chest with three swords and the opening lid. The scene tick is the synced
 * spawn tick plus the prop's own age.
 */
public final class ActPropRenderer extends EntityRenderer<ActPropEntity> {
   /** AEGIS: curtain panels around Sinclair and their ring radius (blocks). */
   static final int CURTAIN_PANELS = 10;
   static final float CURTAIN_RADIUS = 1.45F;
   /** NOVA: cards in the burst ring. */
   static final int NOVA_CARDS = 20;

   ActPropRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public boolean shouldRender(ActPropEntity entity, Frustum frustum, double x, double y, double z) {
      return entity.distanceToSqr(x, y, z) < 128.0 * 128.0;
   }

   /** Scene tick (ticks from the second B press) at this frame. */
   static float sceneTick(ActPropEntity prop, float partialTick) {
      return prop.sceneStart() + prop.tickCount + partialTick;
   }

   @Override
   public void render(ActPropEntity prop, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
      float s = sceneTick(prop, partialTick);
      int color = prop.color() == 0 ? SinclairPalette.GHOST : prop.color();
      float actYaw = prop.getYRot();
      switch (prop.kind()) {
         case ActPropEntity.NOVA_HAT -> nova(stack, buffers, s, color);
         case ActPropEntity.CUT_LINE -> cut(stack, buffers, light, s, actYaw, prop.reach(), color);
         case ActPropEntity.AEGIS_CURTAIN -> aegis(stack, buffers, light, s, color);
         case ActPropEntity.FINALE_CHEST -> finale(stack, buffers, light, s, actYaw, color);
         default -> {
         }
      }
   }

   // --- NOVA "Hat Whirl" -------------------------------------------------------------------------
   static float novaHatScale(float s) {
      if (s < ActScript.NOVA.reveal()) {
         return 1.2F + 2.4F * smooth((s - ActScript.NOVA_THROW) / (ActScript.NOVA.reveal() - ActScript.NOVA_THROW));
      }

      float k = (s - ActScript.NOVA.reveal()) / 4.0F;
      return k < 1.0F ? 3.6F + 0.8F * Mth.sin(k * Mth.PI) : 3.6F - 2.6F * smooth((s - ActScript.NOVA.reveal() - 4.0F) / 12.0F);
   }

   private void nova(PoseStack stack, MultiBufferSource buffers, float s, int color) {
      BakedGeoModel hat = AnimCache.model(SinclairAssets.TOP_HAT);
      if (hat == null) {
         return;
      }

      hat.resetBones();
      float scale = novaHatScale(s);
      float spin = s * (s < ActScript.NOVA.pauseEnd() ? 48.0F : 20.0F);
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(spin));
      stack.mulPose(Axis.XP.rotationDegrees(s < ActScript.NOVA.reveal() ? -18.0F : 0.0F));
      stack.scale(scale, scale, scale);
      stack.translate(0.0F, -3.1F / 16.0F, 0.0F);
      SinclairEntityRenderers.draw(hat, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      stack.popPose();
      float burst = s - ActScript.NOVA.reveal();
      BakedGeoModel card = AnimCache.model(SinclairAssets.CARD);
      if (card == null || burst < 0.0F || burst > 12.0F) {
         return;
      }

      card.resetBones();
      float radius = (float) (ActScript.NOVA_RADIUS * smooth(burst / 6.0F));
      float alpha = 1.0F - smooth((burst - 6.0F) / 6.0F);
      int tint = SinclairPalette.mix(0xFFFFFF, color, 0.4F);
      for (int i = 0; i < NOVA_CARDS; i++) {
         float angle = 360.0F * i / NOVA_CARDS + burst * 9.0F;
         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(angle));
         stack.translate(0.0F, Mth.sin(i * 1.7F + burst * 0.5F) * 0.3F, radius);
         stack.mulPose(Axis.ZP.rotationDegrees(burst * 50.0F + i * 30.0F));
         stack.scale(2.6F, 2.6F, 2.6F);
         SinclairEntityRenderers.draw(card, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, r(tint), g(tint), b(tint), Math.max(0.0F, alpha));
         stack.popPose();
      }
   }

   // --- CUT "Sawing" ----------------------------------------------------------------------------
   private void cut(PoseStack stack, MultiBufferSource buffers, int light, float s, float actYaw, float reach, int color) {
      BakedGeoModel card = AnimCache.model(SinclairAssets.CARD);
      BakedGeoModel sword = AnimCache.model(SinclairAssets.SWORD);
      if (card == null || sword == null) {
         return;
      }

      card.resetBones();
      sword.resetBones();
      float telegraph = ActScript.CUT_TELEGRAPH;
      float cutAt = ActScript.CUT.reveal();
      int count = Math.max(4, (int) (reach / 0.7F));
      float fade = 1.0F - smooth((s - ActScript.CUT.pauseEnd() + 2.0F) / 3.0F);
      int tint = SinclairPalette.mix(0xFFFFFF, color, 0.3F);
      stack.pushPose();
      // Local -z runs along the line (models face -z), +y up.
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - actYaw));
      for (int i = 0; i < count; i++) {
         float along = (i + 0.5F) * reach / count;
         float stand = smooth((s - telegraph - (cutAt - telegraph) * i / count) / 2.0F);
         float blade = cutAt + 4.0F * along / reach;
         float fall = smooth((s - blade) / 2.0F);
         if (stand <= 0.0F) {
            continue;
         }

         stack.pushPose();
         stack.translate(0.0F, 0.02F, -along);
         stack.mulPose(Axis.YP.rotationDegrees(90.0F));
         stack.mulPose(Axis.XP.rotationDegrees(90.0F * (1.0F - stand) + (i % 2 == 0 ? 80.0F : -80.0F) * fall));
         stack.translate(0.0F, 1.75F * 2.2F / 16.0F, 0.0F);
         stack.scale(2.2F, 2.2F, 2.2F);
         SinclairEntityRenderers.draw(card, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, light, OverlayTexture.NO_OVERLAY,
            r(tint), g(tint), b(tint), Math.max(0.0F, fade));
         stack.popPose();
      }

      // The card-blade: drawn from the wand at the telegraph, saws along the line on the cut.
      float pass = s < cutAt ? 0.0F : smooth((s - cutAt) / 4.0F);
      float bladeAlpha = s < telegraph + 4.0F ? smooth((s - telegraph) / 4.0F) : fade;
      if (bladeAlpha > 0.01F) {
         float saw = s >= cutAt && s < cutAt + 6.0F ? Mth.sin((s - cutAt) * 2.6F) * 0.35F : 0.0F;
         stack.pushPose();
         stack.translate(0.0F, 0.75F, -(reach - 1.5F) * pass - 0.2F + saw);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(90.0F));
         stack.scale(2.0F, 2.0F, 2.0F);
         SinclairEntityRenderers.draw(sword, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, r(tint), g(tint), b(tint), Math.max(0.0F, bladeAlpha));
         stack.popPose();
      }

      stack.popPose();
   }

   // --- AEGIS "Curtain" -------------------------------------------------------------------------
   private void aegis(PoseStack stack, MultiBufferSource buffers, int light, float s, int color) {
      BakedGeoModel curtain = AnimCache.model(SinclairAssets.CURTAIN);
      if (curtain == null) {
         return;
      }

      float reveal = ActScript.AEGIS.reveal();
      float rise = smooth(s / 6.0F);
      float open = s < reveal ? 0.0F : smooth((s - reveal) / 8.0F);
      float alpha = 1.0F - smooth((s - reveal - 2.0F) / 6.0F);
      if (alpha <= 0.01F) {
         return;
      }

      float width = (float) (2.0 * Math.PI * CURTAIN_RADIUS / CURTAIN_PANELS) / (17.0F / 16.0F);
      for (int i = 0; i < CURTAIN_PANELS; i++) {
         if (s < reveal) {
            SinclairPoser.overlayFresh(curtain, "curtain_wave", RealTime.seconds() + i * 0.21);
         } else {
            SinclairPoser.overlayFresh(curtain, "curtain_open", (s - reveal) / 20.0);
         }

         float angle = 360.0F * i / CURTAIN_PANELS;
         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(angle));
         stack.translate(0.0F, 0.0F, CURTAIN_RADIUS + 3.0F * open);
         stack.mulPose(Axis.XP.rotationDegrees(-35.0F * open));
         stack.scale(width, 1.35F * rise, 1.0F);
         SinclairEntityRenderers.draw(curtain, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, light, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, alpha < 0.999F ? alpha : 1.0F);
         stack.popPose();
      }
   }

   // --- FINALE "Metamorphosis" ------------------------------------------------------------------
   /** Offsets (x, z in chest px) and tilts (x, z degrees) of the three swords. */
   private static final float[][] SWORDS = {{-5.0F, 2.0F, 18.0F, 8.0F}, {5.0F, -2.0F, -14.0F, -20.0F}, {0.0F, 0.0F, 4.0F, 14.0F}};

   private void finale(PoseStack stack, MultiBufferSource buffers, int light, float s, float actYaw, int color) {
      BakedGeoModel chest = AnimCache.model(SinclairAssets.CHEST);
      BakedGeoModel sword = AnimCache.model(SinclairAssets.SWORD);
      if (chest == null || sword == null) {
         return;
      }

      float reveal = ActScript.FINALE.reveal();
      float vanish = smooth((s - ActScript.FINALE.pauseEnd()) / 10.0F);
      float scale = 1.5F * (1.0F - vanish);
      if (scale <= 0.02F) {
         return;
      }

      if (s >= reveal) {
         SinclairPoser.overlayFresh(chest, "chest_open", (s - reveal) / 20.0);
      } else {
         chest.resetBones();
      }

      float jolt = 0.0F;
      for (int hit : ActScript.FINALE_SWORDS) {
         float d = s - hit;
         if (d >= 0.0F && d < 4.0F) {
            jolt += Mth.sin(d * 5.0F) * (1.0F - d / 4.0F) * 3.0F;
         }
      }

      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - actYaw));
      stack.pushPose();
      stack.mulPose(Axis.ZP.rotationDegrees(jolt));
      stack.scale(scale, scale, scale);
      SinclairEntityRenderers.draw(chest, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, light, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F);
      stack.popPose();
      sword.resetBones();
      int tint = SinclairPalette.mix(0xFFFFFF, color, 0.25F);
      for (int i = 0; i < SWORDS.length; i++) {
         float start = ActScript.FINALE_SWORDS[i] - 3.0F;
         if (s < start) {
            continue;
         }

         float drop = smooth((s - start) / 3.0F);
         float out = s < reveal ? 0.0F : (s - reveal) / 8.0F;
         float alpha = 1.0F - smooth(out);
         if (alpha <= 0.01F) {
            continue;
         }

         float[] w = SWORDS[i];
         stack.pushPose();
         stack.translate(w[0] / 16.0F * 1.5F, (27.0F + 40.0F * (1.0F - drop)) / 16.0F + out * out * 3.0F, w[1] / 16.0F * 1.5F);
         stack.mulPose(Axis.XP.rotationDegrees(w[2]));
         stack.mulPose(Axis.ZP.rotationDegrees(180.0F + w[3] + out * 200.0F));
         SinclairEntityRenderers.draw(sword, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, light, OverlayTexture.NO_OVERLAY,
            r(tint), g(tint), b(tint), alpha);
         stack.popPose();
      }

      stack.popPose();
   }

   static float smooth(float t) {
      return SinclairEntityRenderers.smooth(t);
   }

   private static float r(int rgb) {
      return SinclairPalette.r(rgb) / 255.0F;
   }

   private static float g(int rgb) {
      return SinclairPalette.g(rgb) / 255.0F;
   }

   private static float b(int rgb) {
      return SinclairPalette.b(rgb) / 255.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(ActPropEntity entity) {
      return SinclairAssets.PROPS_TEXTURE;
   }
}
