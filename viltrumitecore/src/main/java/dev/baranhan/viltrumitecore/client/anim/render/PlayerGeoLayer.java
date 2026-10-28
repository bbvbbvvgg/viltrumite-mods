package dev.baranhan.viltrumitecore.client.anim.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Geo parts on the player model (animation core extension, animation-system
 * skill "Geo parts on player parts"). A provider lists Blockbench geo models
 * for a player; every top-level bone with an armor-style name
 * ({@link PlayerBoneMap}) is drawn on the matching vanilla model part with the
 * part's final pose, so the geo follows every setupAnim TAIL pose mixin in
 * third person. Arm bones are also drawn on the first-person arm.
 */
public class PlayerGeoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/anim");
   private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
   private static final Set<String> WARNED = new HashSet<>();
   /** Geo space (y up, x mirrored, feet at 0) to vanilla model space: 24 px = 1.5 blocks. */
   private static final float MODEL_HEIGHT = 1.5F;

   public PlayerGeoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   public static void register(Provider provider) {
      PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
   }

   @Override
   public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
      float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
      if (player.isInvisible() || PROVIDERS.isEmpty()) {
         return;
      }

      List<Part> parts = collect(player, partialTick, false);
      if (parts.isEmpty()) {
         return;
      }

      PlayerModel<AbstractClientPlayer> model = this.getParentModel();
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      for (Part part : parts) {
         BakedGeoModel geo = model(part);
         if (geo != null) {
            for (GeoBone bone : geo.topLevelBones()) {
               PlayerBoneMap.Part target = map(part, bone);
               if (target != null) {
                  draw(poseStack, buffers, partOf(model, target), target, bone, part, light, overlay);
               }
            }
         }
      }
   }

   /** First-person arm (PlayerRenderer.renderHand TAIL): only bones on that arm. */
   public static void renderArm(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
      ModelPart arm, float partialTick) {
      if (PROVIDERS.isEmpty() || player.isInvisible()) {
         return;
      }

      PlayerBoneMap.Part side = arm == model.rightArm ? PlayerBoneMap.Part.RIGHT_ARM : arm == model.leftArm ? PlayerBoneMap.Part.LEFT_ARM : null;
      if (side == null) {
         return;
      }

      for (Part part : collect(player, partialTick, true)) {
         BakedGeoModel geo = model(part);
         if (geo != null) {
            for (GeoBone bone : geo.topLevelBones()) {
               if (map(part, bone) == side) {
                  draw(poseStack, buffers, arm, side, bone, part, light, OverlayTexture.NO_OVERLAY);
               }
            }
         }
      }
   }

   /** One part alone on a first-person arm (Hulkbuster arm): the other providers stay out. */
   public static void renderArmPart(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerModel<AbstractClientPlayer> model, ModelPart arm,
      Part part) {
      PlayerBoneMap.Part side = arm == model.rightArm ? PlayerBoneMap.Part.RIGHT_ARM : arm == model.leftArm ? PlayerBoneMap.Part.LEFT_ARM : null;
      BakedGeoModel geo = side == null ? null : model(part);
      if (geo == null) {
         return;
      }

      for (GeoBone bone : geo.topLevelBones()) {
         if (map(part, bone) == side) {
            draw(poseStack, buffers, arm, side, bone, part, light, OverlayTexture.NO_OVERLAY);
         }
      }
   }

   private static List<Part> collect(AbstractClientPlayer player, float partialTick, boolean firstPerson) {
      List<Part> parts = new ArrayList<>(4);
      for (Provider provider : PROVIDERS) {
         provider.collect(player, partialTick, firstPerson, parts);
      }

      return parts;
   }

   @Nullable
   private static BakedGeoModel model(Part part) {
      BakedGeoModel geo = AnimCache.model(part.geo());
      if (geo == null) {
         warnOnce("missing geo " + part.geo());
         return null;
      }

      // One shared instance per resource: reset, then let the provider pose it right before drawing.
      geo.resetBones();
      if (part.pose() != null) {
         part.pose().accept(geo);
      }

      return geo;
   }

   @Nullable
   private static PlayerBoneMap.Part map(Part part, GeoBone bone) {
      PlayerBoneMap.Part target = PlayerBoneMap.of(bone.name);
      if (target == null && !bone.cubes.isEmpty()) {
         warnOnce("bone " + bone.name + " in " + part.geo() + " has no player part, skipped");
      }

      return target;
   }

   private static void draw(PoseStack poseStack, MultiBufferSource buffers, ModelPart modelPart, PlayerBoneMap.Part target, GeoBone bone, Part part,
      int light, int overlay) {
      if (!modelPart.visible || bone.hidden) {
         return;
      }

      poseStack.pushPose();
      partFrame(poseStack,modelPart,target);
      boolean shadow = ShaderCompat.isShadowPass();
      if (!shadow && part.frame() != null) {
         part.frame().onFrame(poseStack, bone);
      }

      for (Pass pass : part.passes()) {
         boolean glow = pass.kind() == Pass.Kind.GLOW;
         boolean translucent = pass.kind() == Pass.Kind.TRANSLUCENT;
         if ((glow || translucent) && shadow) {
            // Emissive and ghost passes cast no shadow (animation-system §7).
            continue;
         }

         VertexConsumer buffer = buffers.getBuffer(glow ? RenderType.eyes(pass.texture())
            : translucent ? RenderType.entityTranslucent(pass.texture()) : RenderType.entityCutoutNoCull(pass.texture()));
         AnimRenderer.renderBone(bone, poseStack, buffer, glow ? LightTexture.FULL_BRIGHT : light, glow ? OverlayTexture.NO_OVERLAY : overlay,
            pass.r(), pass.g(), pass.b(), pass.a());
      }

      poseStack.popPose();
   }

   /** The actual draw frame, shared with final-palm contact sampling (no parallel approximation). */
   public static void partFrame(PoseStack stack,ModelPart part,PlayerBoneMap.Part target) {
      part.translateAndRotate(stack);
      stack.translate(-target.pivotX/16F,-target.pivotY/16F,-target.pivotZ/16F);
      stack.translate(0,MODEL_HEIGHT,0);stack.scale(-1,-1,1);
   }

   private static ModelPart partOf(PlayerModel<AbstractClientPlayer> model, PlayerBoneMap.Part part) {
      return switch (part) {
         case HEAD -> model.head;
         case BODY -> model.body;
         case RIGHT_ARM -> model.rightArm;
         case LEFT_ARM -> model.leftArm;
         case RIGHT_LEG -> model.rightLeg;
         case LEFT_LEG -> model.leftLeg;
      };
   }

   private static void warnOnce(String message) {
      if (WARNED.add(message)) {
         LOGGER.warn("PlayerGeoLayer: {}", message);
      }
   }

   /** Lists the geo parts a player wears this frame. Render thread only. */
   @FunctionalInterface
   public interface Provider {
      void collect(AbstractClientPlayer player, float partialTick, boolean firstPerson, List<Part> out);
   }

   /**
    * One geo model on the player. {@code pose} runs after the bones are reset
    * and may set bone scale/rotation/hidden (for example flame length).
    * {@code frame}, if set, receives the actual draw frame of each mapped
    * top-level bone (not in the shadow pass), for VFX anchors on the posed geo.
    */
   public record Part(ResourceLocation geo, List<Pass> passes, @Nullable Consumer<BakedGeoModel> pose, @Nullable FrameSink frame) {
      public Part(ResourceLocation geo, List<Pass> passes) {
         this(geo, passes, null, null);
      }

      public Part(ResourceLocation geo, List<Pass> passes, @Nullable Consumer<BakedGeoModel> pose) {
         this(geo, passes, pose, null);
      }
   }

   /** Draw frame of a part's top-level bone: the stack is in geo model space, the bone is posed. Do not keep the stack. */
   @FunctionalInterface
   public interface FrameSink {
      void onFrame(PoseStack frame, GeoBone topLevelBone);
   }

   /**
    * One draw of a part: cutout texture (lit), glow texture (additive, full
    * bright) or translucent (lit, alpha-blended: motion-blur ghost copies).
    */
   public record Pass(ResourceLocation texture, Kind kind, float r, float g, float b, float a) {
      public enum Kind {
         CUTOUT,
         GLOW,
         TRANSLUCENT
      }

      /** Alpha-blended pass (no shadow), for example motion-blur ghosts of a spinning part. */
      public static Pass translucent(ResourceLocation texture, float alpha) {
         return new Pass(texture, Kind.TRANSLUCENT, 1.0F, 1.0F, 1.0F, alpha);
      }

      public static Pass cutout(ResourceLocation texture) {
         return new Pass(texture, Kind.CUTOUT, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      /** Cutout pass multiplied by a colour (light-grayscale textures tinted to the suit). */
      public static Pass cutout(ResourceLocation texture, float r, float g, float b) {
         return new Pass(texture, Kind.CUTOUT, r, g, b, 1.0F);
      }

      public static Pass glow(ResourceLocation texture) {
         return new Pass(texture, Kind.GLOW, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      public static Pass glow(ResourceLocation texture, float r, float g, float b) {
         return new Pass(texture, Kind.GLOW, r, g, b, 1.0F);
      }
   }
}
