package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Air step ring (pixel style, additive): a flat white ring of pixels expands
 * under the feet where the air jump pushed off. The local player spawns it at
 * the prediction (AirJumpClient), observers on the server's step packet. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class AirStepVfx {
   static final int LIFE_TICKS = 9;
   static final int PIXELS = 28;
   static final float START_RADIUS = 0.25F;
   static final float END_RADIUS = 1.15F;
   private static final float PIXEL_SIZE = 0.05F;
   private static final int MAX_RINGS = 32;
   private static final List<Ring> RINGS = new ArrayList<>();
   private static ClientLevel lastLevel;

   private AirStepVfx() {
   }

   /** Pure: ring radius at a life fraction 0..1 (eases out). */
   public static float radius(float life) {
      float t = Math.max(0.0F, Math.min(1.0F, life));
      float eased = 1.0F - (1.0F - t) * (1.0F - t);
      return START_RADIUS + (END_RADIUS - START_RADIUS) * eased;
   }

   public static void spawn(Entity entity) {
      if (RINGS.size() >= MAX_RINGS) {
         RINGS.remove(0);
      }

      RINGS.add(new Ring(entity.position().add(0.0, 0.05, 0.0)));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         RINGS.clear();
      }

      if (!client.isPaused()) {
         RINGS.removeIf(ring -> ++ring.age > LIFE_TICKS);
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES || RINGS.isEmpty()) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.rotateCamera(modelViewStack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Ring ring : RINGS) {
            float life = Math.min(1.0F, (ring.age + partialTick) / LIFE_TICKS);
            int alpha = (int) (220.0F * (1.0F - life));
            if (alpha <= 0) {
               continue;
            }

            float r = radius(life);
            for (int i = 0; i < PIXELS; i++) {
               double angle = Math.PI * 2.0 * i / PIXELS + ring.age * 0.05;
               Vec3 at = ring.center.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, at, PIXEL_SIZE, 235, 248, 255, alpha);
            }
         }

         tesselator.end();
      } finally {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static final class Ring {
      final Vec3 center;
      int age;

      Ring(Vec3 center) {
         this.center = center;
      }
   }
}
