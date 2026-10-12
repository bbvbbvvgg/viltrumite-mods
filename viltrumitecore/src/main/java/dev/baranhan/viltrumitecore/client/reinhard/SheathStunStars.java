package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SheathStrike;
import dev.baranhan.viltrumitecore.network.packet.HeroControlS2CPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Sheath Strike stun (spec §7): three white pixel stars circling over the head of
 * every target held by a freeze a Reinhard cast (from the control sync), until the
 * control expires. Not drawn over the local player in first person.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class SheathStunStars {
   private static final int STARS = 3;
   private static final double ORBIT = 0.38;

   private SheathStunStars() {
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || ClientHeroData.controls().isEmpty()) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = null;
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      try {
         for (HeroControlS2CPacket.ControlInfo control : ClientHeroData.controls()) {
            Player caster = control.caster() == null ? null : level.getPlayerByUUID(control.caster());
            if (!SheathStrike.showsStars(control.kindOrdinal(), caster != null && ReinhardView.of(caster) != null)) {
               continue;
            }

            Entity target = level.getEntity(control.entityId());
            if (target == null || target.isInvisible() || (target == client.player && client.options.getCameraType().isFirstPerson())) {
               continue;
            }

            if (buffer == null) {
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
               buffer = tesselator.getBuilder();
               buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }

            Vec3 head = target.getPosition(partialTick).add(0.0, target.getBbHeight() + 0.3, 0.0);
            double spin = ((level.getGameTime() % 24000L) + partialTick) * 0.25 + (target.getId() & 15);
            for (int i = 0; i < STARS; i++) {
               double a = spin + i * Math.PI * 2.0 / STARS;
               Vec3 pos = head.add(Math.cos(a) * ORBIT, Math.sin(a * 2.0) * 0.05, Math.sin(a) * ORBIT);
               PixelVfx.crossGlow(buffer, cameraPos, camera, pos, 0.14F, 255, 255, 255, 230);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, pos, 0.05F, 255, 250, 210, 255);
            }
         }

         if (buffer != null) {
            tesselator.end();
         }
      } finally {
         if (buffer != null) {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
         }
      }
   }
}
