package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActCardEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.DecoyEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexHatEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Renderers of Sinclair's eight entity types (plan step 7). Sparks, Vexing Bolts and hex marks
 * are pixel effects drawn by {@link SinclairVfx}; the rest are geo models: Savannah, the decoy
 * (Sinclair himself as the running magician), the hex hat, the act props and the act cards.
 * Every type must have a renderer or the client crashes on spawn.
 */
public final class SinclairEntityRenderers {
   private SinclairEntityRenderers() {
   }

   public static void register(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(SinclairEntities.SPARK.get(), NoopRenderer::new);
      event.registerEntityRenderer(SinclairEntities.VEXING_BOLT.get(), NoopRenderer::new);
      event.registerEntityRenderer(SinclairEntities.HEX_MARK.get(), NoopRenderer::new);
      event.registerEntityRenderer(SinclairEntities.ASSISTANT.get(), AssistantRenderer::new);
      event.registerEntityRenderer(SinclairEntities.DECOY.get(), DecoyRenderer::new);
      event.registerEntityRenderer(SinclairEntities.HEX_HAT.get(), HexHatRenderer::new);
      event.registerEntityRenderer(SinclairEntities.ACT_PROP.get(), ActPropRenderer::new);
      event.registerEntityRenderer(SinclairEntities.ACT_CARD.get(), ActCardRenderer::new);
   }

   /** Renders a model in one pass plus its glow, with a colour multiplier and alpha. */
   static void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, ResourceLocation texture, ResourceLocation glow, int light, int overlay,
                    float r, float g, float b, float alpha) {
      RenderType type = alpha >= 0.999F ? RenderType.entityCutoutNoCull(texture) : RenderType.entityTranslucent(texture);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, r, g, b, alpha, null);
      if (!ShaderCompat.isShadowPass() && alpha > 0.02F) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(glow)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            r * alpha, g * alpha, b * alpha, 1.0F, null);
      }
   }

   static float ticks(Entity entity, int start, float partialTick) {
      return Math.max(0.0F, entity.tickCount - start + partialTick);
   }

   static float smooth(float t) {
      float k = Mth.clamp(t, 0.0F, 1.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   /** Savannah, the spectral assistant (spec §5.3): every state has its own clip; idle and act loop in real time. */
   static final class AssistantRenderer extends EntityRenderer<AssistantEntity> {
      static final float SCALE = 1.1F;
      static final float ALPHA = 0.8F;

      AssistantRenderer(EntityRendererProvider.Context context) {
         super(context);
         this.shadowRadius = 0.0F;
      }

      static String clip(int state) {
         return switch (state) {
            case AssistantEntity.APPEAR -> "sav_appear";
            case AssistantEntity.ATTACK -> "sav_attack";
            case AssistantEntity.CAST -> "sav_cast";
            case AssistantEntity.SWAP_OUT -> "sav_swap_out";
            case AssistantEntity.SWAP_IN -> "sav_swap_in";
            case AssistantEntity.ACT -> "sav_act";
            case AssistantEntity.HURT -> "sav_hurt";
            case AssistantEntity.LEAVE -> "sav_leave";
            case AssistantEntity.DEATH -> "sav_death";
            case AssistantEntity.BOW -> "sav_bow";
            default -> "sav_idle";
         };
      }

      /** Fade of the ghost: rises out of the ribbons, dissolves on leave and death. */
      static float alpha(int state, float t) {
         return switch (state) {
            case AssistantEntity.APPEAR -> ALPHA * smooth(t / 10.0F);
            case AssistantEntity.LEAVE, AssistantEntity.DEATH -> ALPHA * (1.0F - smooth((t - 6.0F) / 14.0F));
            default -> ALPHA;
         };
      }

      @Override
      public void render(AssistantEntity entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
         BakedGeoModel model = AnimCache.model(SinclairAssets.SAVANNAH);
         if (model == null) {
            return;
         }

         int state = entity.state();
         float t = ticks(entity, entity.stateStart(), partialTick);
         Animation idle = SinclairAssets.clip("sav_idle");
         double real = RealTime.seconds() + entity.getId() * 0.37;
         if (idle != null) {
            AnimationController.seek(idle, model, SinclairPoser.loop(idle, real));
         } else {
            model.resetBones();
         }

         if (state != AssistantEntity.IDLE) {
            Animation clip = SinclairAssets.clip(clip(state));
            if (clip != null) {
               double seconds = clip.loopType() == Animation.LoopType.LOOP ? real : t / 20.0;
               AnimationController.overlay(clip, model, SinclairPoser.loop(clip, seconds), 1.0F);
            }
         }

         float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
         GeoBone head = model.getBone("head");
         if (head != null) {
            float look = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw);
            head.rotY += (float) Math.toRadians(-Mth.clamp(look, -60.0F, 60.0F));
            head.rotX += (float) Math.toRadians(-entity.getViewXRot(partialTick) * 0.6F);
         }

         CoatPhysics.savannah(model, entity, bodyYaw, partialTick);
         float alpha = alpha(state, t);
         if (alpha <= 0.01F) {
            return;
         }

         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
         stack.scale(SCALE, SCALE, SCALE);
         int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
         draw(model, stack, buffers, SinclairAssets.SAVANNAH_TEXTURE, SinclairAssets.SAVANNAH_GLOW, LightTexture.FULL_BRIGHT, overlay, 1.0F, 1.0F, 1.0F, alpha);
         stack.popPose();
      }

      @Override
      public ResourceLocation getTextureLocation(AssistantEntity entity) {
         return SinclairAssets.SAVANNAH_TEXTURE;
      }
   }

   /** The decoy (spec §5.5): Sinclair's own model running on; the reveal spins it away into cards. */
   static final class DecoyRenderer extends EntityRenderer<DecoyEntity> {
      DecoyRenderer(EntityRendererProvider.Context context) {
         super(context);
         this.shadowRadius = 0.45F;
      }

      @Override
      public void render(DecoyEntity entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
         BakedGeoModel model = AnimCache.model(SinclairAssets.BODY);
         if (model == null) {
            return;
         }

         float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
         SinclairPoser.poseDecoy(model, entity, partialTick, bodyYaw);
         float shrink = 1.0F;
         float spin = 0.0F;
         if (entity.revealed()) {
            float t = ticks(entity, entity.revealTick(), partialTick);
            shrink = 1.0F - smooth(t / (DecoyEntity.REVEAL_TICKS * 0.6F));
            spin = t * t * 4.0F;
            if (shrink <= 0.02F) {
               return;
            }
         }

         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw + spin));
         float s = SinclairAssets.PLAYER_SCALE;
         stack.translate(0.0F, (1.0F - shrink) * 0.9F, 0.0F);
         stack.scale(s * shrink, s * shrink, s * shrink);
         int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
         draw(model, stack, buffers, SinclairAssets.TEXTURE, SinclairAssets.GLOW, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
         stack.popPose();
      }

      @Override
      public ResourceLocation getTextureLocation(DecoyEntity entity) {
         return SinclairAssets.TEXTURE;
      }
   }

   /** Rabbit Hex telegraph (spec §5.4): a ghostly top hat spins 3 blocks over the point, then tips over and bursts. */
   static final class HexHatRenderer extends EntityRenderer<HexHatEntity> {
      static final float SCALE = 2.0F;

      HexHatRenderer(EntityRendererProvider.Context context) {
         super(context);
         this.shadowRadius = 0.0F;
      }

      @Override
      public boolean shouldRender(HexHatEntity entity, Frustum frustum, double x, double y, double z) {
         return entity.distanceToSqr(x, y, z) < 96.0 * 96.0;
      }

      @Override
      public void render(HexHatEntity entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
         BakedGeoModel model = AnimCache.model(SinclairAssets.TOP_HAT);
         if (model == null) {
            return;
         }

         model.resetBones();
         float age = entity.tickCount + partialTick;
         float appear = smooth(age / 5.0F);
         float scale = SCALE * (0.4F + 0.6F * appear);
         float alpha = 0.85F * appear;
         float tip = 0.0F;
         if (entity.poofTick() >= 0) {
            float p = ticks(entity, entity.poofTick(), partialTick) / HexHatEntity.AFTER_POOF;
            scale *= 1.0F + 0.5F * smooth(p);
            alpha *= 1.0F - smooth(p);
            tip = 180.0F * smooth(p * 1.5F);
         }

         if (alpha <= 0.01F) {
            return;
         }

         stack.pushPose();
         stack.translate(0.0, SinclairRules.HEX_HAT_HEIGHT + Mth.sin(age * 0.3F) * 0.12F, 0.0);
         stack.mulPose(Axis.YP.rotationDegrees(age * 24.0F));
         stack.mulPose(Axis.XP.rotationDegrees(14.0F + tip));
         stack.scale(scale, scale, scale);
         stack.translate(0.0F, -3.1F / 16.0F, 0.0F);
         draw(model, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.85F, 0.72F, 1.0F, alpha);
         stack.popPose();
      }

      @Override
      public ResourceLocation getTextureLocation(HexHatEntity entity) {
         return SinclairAssets.PROPS_TEXTURE;
      }
   }

   /** Card Fan cards (BARRAGE): a spinning playing card flying nose-first, tinted by the act colour. */
   static final class ActCardRenderer extends EntityRenderer<ActCardEntity> {
      static final float SCALE = 2.4F;

      ActCardRenderer(EntityRendererProvider.Context context) {
         super(context);
         this.shadowRadius = 0.0F;
      }

      @Override
      public void render(ActCardEntity entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
         BakedGeoModel model = AnimCache.model(SinclairAssets.CARD);
         if (model == null) {
            return;
         }

         model.resetBones();
         double dx = entity.getX() - entity.xo;
         double dy = entity.getY() - entity.yo;
         double dz = entity.getZ() - entity.zo;
         float heading = (float) Math.toDegrees(Mth.atan2(dx, dz));
         float pitch = (float) Math.toDegrees(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
         float age = entity.tickCount + partialTick;
         int color = entity.color() == 0 ? SinclairPalette.GHOST : entity.color();
         int tint = SinclairPalette.mix(0xFFFFFF, color, 0.35F);
         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(heading));
         stack.mulPose(Axis.XP.rotationDegrees(-pitch + 90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(age * 40.0F));
         stack.scale(SCALE, SCALE, SCALE);
         draw(model, stack, buffers, SinclairAssets.PROPS_TEXTURE, SinclairAssets.PROPS_GLOW, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            SinclairPalette.r(tint) / 255.0F, SinclairPalette.g(tint) / 255.0F, SinclairPalette.b(tint) / 255.0F, 1.0F);
         stack.popPose();
      }

      @Override
      public ResourceLocation getTextureLocation(ActCardEntity entity) {
         return SinclairAssets.PROPS_TEXTURE;
      }
   }
}
