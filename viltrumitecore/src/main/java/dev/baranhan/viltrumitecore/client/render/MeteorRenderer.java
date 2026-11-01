package dev.baranhan.viltrumitecore.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Meteor Rain 2.0 meteor: a tumbling, full-bright magma core inside a pulsing additive heat shell (its
 * flicker stands in for heat shimmer), a camera-facing glow halo (white-yellow centre, red-orange rim)
 * that reads by day and by night, and a tapered plasma tail along the flight path whose texture streams
 * backward. The old chain of eight translucent magma copies is gone; the particle trail
 * ({@code MeteorTrailFx}) adds flame, sparks and smoke behind. Hitbox and physics are unchanged; only the
 * culling box grows with the tail so a meteor whose core is off screen still shows its streak.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
   public static final ResourceLocation GLOW = new ResourceLocation("viltrumitecore", "textures/entity/meteor_glow.png");
   public static final ResourceLocation TAIL = new ResourceLocation("viltrumitecore", "textures/entity/meteor_tail.png");
   /** Rendered core size in blocks (the magma model scaled up). */
   public static final float CORE = 4.0F;
   /** Plasma tail length in blocks and its width at the core. */
   public static final float TAIL_LENGTH = 30.0F;
   public static final float TAIL_WIDTH = 5.2F;
   private static final int FULL_BRIGHT = 15728880;
   private static final int TAIL_SEGMENTS = 12;
   private static final Vector3f TUMBLE = new Vector3f(1.0F, 0.6F, 0.3F).normalize();

   private final BlockRenderDispatcher blockRenderer;

   public MeteorRenderer(Context context) {
      super(context);
      this.blockRenderer = context.getBlockRenderDispatcher();
      this.shadowRadius = 0.0F;
   }

   /** Pure: tail half-width at {@code t} 0 (core) .. 1 (tip). */
   public static float tailWidth(float t) {
      float k = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
      return TAIL_WIDTH * 0.5F * (float)Math.pow(k, 0.75);
   }

   /** Pure: heat-shell pulse 0.6..1 at a time in ticks (two incommensurate waves and a fast jitter). */
   public static float pulse(float time) {
      float p = 0.8F + 0.12F * Mth.sin(time * 0.9F) + 0.06F * Mth.sin(time * 2.3F + 1.1F) + 0.02F * Mth.sin(time * 7.7F);
      return Mth.clamp(p, 0.6F, 1.0F);
   }

   @Override
   public boolean shouldRender(MeteorEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      if (!entity.shouldRender(camX, camY, camZ)) {
         return false;
      }
      Vec3 v = entity.getDeltaMovement();
      double speed = v.length();
      AABB box = entity.getBoundingBox().inflate(CORE);
      if (speed > 1.0E-3) {
         box = box.expandTowards(v.scale(-TAIL_LENGTH / speed));
      }
      return frustum.isVisible(box);
   }

   @Override
   public void render(MeteorEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      float time = entity.tickCount + partialTick;
      Vec3 velocity = entity.getDeltaMovement();
      double speed = velocity.length();
      Vec3 dir = speed < 1.0E-3 ? new Vec3(0.0, -1.0, 0.0) : velocity.scale(1.0 / speed);
      BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
      float yaw = (float)(Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI));
      float spin = (float)(time * speed * 10.0);
      float pulse = pulse(time + entity.getId() * 3.1F);

      poseStack.pushPose();
      poseStack.translate(0.0, 0.5, 0.0);

      // 1. the core: tumbling magma, full bright
      poseStack.pushPose();
      poseStack.scale(CORE, CORE, CORE);
      poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
      poseStack.mulPose(Axis.of(TUMBLE).rotationDegrees(spin));
      poseStack.translate(-0.5, -0.5, -0.5);
      this.blockRenderer.renderSingleBlock(magma, poseStack, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
      poseStack.popPose();

      // 2. the heat shell: the same block a little larger, additive, pulsing orange
      poseStack.pushPose();
      float shell = CORE * (1.12F + 0.03F * pulse);
      poseStack.scale(shell, shell, shell);
      poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
      poseStack.mulPose(Axis.of(TUMBLE).rotationDegrees(spin * 0.85F + 20.0F));
      poseStack.translate(-0.5, -0.5, -0.5);
      this.shell(magma, poseStack, buffer, 0.95F * pulse, 0.42F * pulse, 0.12F * pulse);
      poseStack.popPose();

      // camera-facing parts are built in the entity's frame, oriented to the camera here
      Camera camera = this.entityRenderDispatcher.camera;
      Vec3 centre = entity.getPosition(partialTick).add(0.0, 0.5, 0.0);
      Vec3 toCamera = camera.getPosition().subtract(centre);

      // 3. plasma tail along the flight path, two layers: wide orange, narrow white-yellow
      this.tail(poseStack, buffer, dir, toCamera, time, 1.0F, 0.42F, 0.12F, 0.85F, 1.0F);
      this.tail(poseStack, buffer, dir, toCamera, time * 1.6F, 1.0F, 0.86F, 0.55F, 0.75F, 0.45F);

      // 4. glow halo: a wide red-orange rim and a hot centre
      poseStack.pushPose();
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      VertexConsumer glow = buffer.getBuffer(Types.GLOW);
      float rim = 15.0F + 1.5F * pulse;
      quad(glow, poseStack.last(), rim, 0.95F * pulse, 0.32F * pulse, 0.08F * pulse);
      quad(glow, poseStack.last(), 7.5F, 1.0F, 0.85F * pulse, 0.55F * pulse);
      poseStack.popPose();

      poseStack.popPose();
      super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
   }

   private void shell(BlockState state, PoseStack poseStack, MultiBufferSource buffer, float r, float g, float b) {
      BakedModel model = this.blockRenderer.getBlockModel(state);
      VertexConsumer consumer = buffer.getBuffer(Types.SHELL);
      PoseStack.Pose pose = poseStack.last();
      RandomSource random = RandomSource.create();
      for (Direction direction : Direction.values()) {
         random.setSeed(42L);
         for (BakedQuad quad : model.getQuads(state, direction, random, ModelData.EMPTY, null)) {
            consumer.putBulkData(pose, quad, r, g, b, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
         }
      }
      random.setSeed(42L);
      for (BakedQuad quad : model.getQuads(state, null, random, ModelData.EMPTY, null)) {
         consumer.putBulkData(pose, quad, r, g, b, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      }
   }

   /** A camera-facing ribbon from the core backward along {@code -dir}, tapering to the tip. */
   private void tail(PoseStack poseStack, MultiBufferSource buffer, Vec3 dir, Vec3 toCamera, float time, float r, float g, float b, float a, float scale) {
      Vec3 side = dir.cross(toCamera);
      if (side.lengthSqr() < 1.0E-6) {
         return;
      }
      side = side.normalize();
      VertexConsumer consumer = buffer.getBuffer(Types.TAIL);
      PoseStack.Pose pose = poseStack.last();
      Matrix4f m = pose.pose();
      Matrix3f n = pose.normal();
      float length = TAIL_LENGTH * (0.75F + 0.25F * scale);
      float scroll = -time * 0.06F;
      for (int i = 0; i < TAIL_SEGMENTS; i++) {
         float t0 = i / (float)TAIL_SEGMENTS;
         float t1 = (i + 1) / (float)TAIL_SEGMENTS;
         Vec3 c0 = dir.scale(-length * t0 + 0.6);
         Vec3 c1 = dir.scale(-length * t1 + 0.6);
         float w0 = tailWidth(t0) * scale;
         float w1 = tailWidth(t1) * scale;
         // fading toward the tip
         float f0 = a * (1.0F - t0) * (1.0F - t0);
         float f1 = a * (1.0F - t1) * (1.0F - t1);
         float u0 = scroll + t0 * 2.0F;
         float u1 = scroll + t1 * 2.0F;
         Vec3 p0 = c0.add(side.scale(w0));
         Vec3 p1 = c0.subtract(side.scale(w0));
         Vec3 p2 = c1.subtract(side.scale(w1));
         Vec3 p3 = c1.add(side.scale(w1));
         vertex(consumer, m, n, p0, u0, 0.0F, r * f0, g * f0, b * f0);
         vertex(consumer, m, n, p1, u0, 1.0F, r * f0, g * f0, b * f0);
         vertex(consumer, m, n, p2, u1, 1.0F, r * f1, g * f1, b * f1);
         vertex(consumer, m, n, p3, u1, 0.0F, r * f1, g * f1, b * f1);
      }
   }

   private static void vertex(VertexConsumer consumer, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, float r, float g, float b) {
      consumer.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
   }

   private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float size, float r, float g, float b) {
      Matrix4f m = pose.pose();
      Matrix3f n = pose.normal();
      float h = size * 0.5F;
      vertex(consumer, m, n, new Vec3(-h, -h, 0.0), 0.0F, 1.0F, r, g, b);
      vertex(consumer, m, n, new Vec3(h, -h, 0.0), 1.0F, 1.0F, r, g, b);
      vertex(consumer, m, n, new Vec3(h, h, 0.0), 1.0F, 0.0F, r, g, b);
      vertex(consumer, m, n, new Vec3(-h, h, 0.0), 0.0F, 0.0F, r, g, b);
   }

   /**
    * Additive, double sided, full bright and free of distance fog: vanilla eyes fade additive colour to
    * nothing toward the fog end, which hid a meteor 200 blocks up; these keep the glow to the render range.
    */
   private static final class Types extends RenderType {
      private static float savedFogStart;
      private static final OutputStateShard NO_FOG = new OutputStateShard("viltrumitecore_meteor_no_fog", () -> {
         savedFogStart = RenderSystem.getShaderFogStart();
         RenderSystem.setShaderFogStart(Float.MAX_VALUE);
      }, () -> RenderSystem.setShaderFogStart(savedFogStart));
      static final RenderType GLOW = glow("meteor_glow", MeteorRenderer.GLOW);
      static final RenderType TAIL = glow("meteor_tail", MeteorRenderer.TAIL);
      static final RenderType SHELL = glow("meteor_shell", InventoryMenu.BLOCK_ATLAS);

      private Types(String name, VertexFormat format, Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
         super(name, format, mode, size, crumbling, sort, setup, clear);
      }

      private static RenderType glow(String name, ResourceLocation texture) {
         return create("viltrumitecore_" + name, DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, false, true,
            CompositeState.builder()
               .setShaderState(RENDERTYPE_EYES_SHADER)
               .setTextureState(new TextureStateShard(texture, false, false))
               .setTransparencyState(ADDITIVE_TRANSPARENCY)
               .setCullState(NO_CULL)
               .setWriteMaskState(COLOR_WRITE)
               .setOutputState(NO_FOG)
               .createCompositeState(false));
      }
   }

   @Override
   public ResourceLocation getTextureLocation(MeteorEntity entity) {
      return InventoryMenu.BLOCK_ATLAS;
   }
}
