package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "viltrumitecore",
   value = {Dist.CLIENT},
   bus = Bus.FORGE
)
public class ChopSweepVFXManager {
   private static final Map<UUID, Float> initialYMap = new HashMap<>();

   @SubscribeEvent
   public static void onRenderWorld(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
               if (player instanceof ViltrumiteCorePlayer) {
                  ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)player;
                  int chopTicks = corePlayer.getChopTicks();
                  if (chopTicks <= 0) {
                     initialYMap.remove(player.getUUID());
                  } else {
                     float partialTick = event.getPartialTick();
                     float exactTicks = (float)chopTicks - partialTick;
                     float time = (20.0F - exactTicks) / 20.0F;
                     if (!(time < 0.25F) && !(time > 1.0F)) {
                        int chopType = corePlayer.getChopType();
                        boolean isLeft = corePlayer.isLeftChop();
                        float sweepProgress = 0.0F;
                        float tailLength = 0.6F;
                        if (chopType == 1) {
                           sweepProgress = (time - 0.25F) / 0.19F;
                        } else {
                           sweepProgress = (time - 0.25F) / 0.1F;
                        }

                        if (!(sweepProgress > 1.0F + tailLength)) {
                           double lerpedY = Mth.lerp((double)partialTick, player.yo, player.getY());
                           float planeY = (float)(lerpedY + (double)(player.getBbHeight() * 0.75F));
                           Camera camera = event.getCamera();
                           PoseStack poseStack = new PoseStack();
                           poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
                           poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
                           poseStack.translate(
                              player.getX() - camera.getPosition().x,
                              (double)planeY - camera.getPosition().y,
                              player.getZ() - camera.getPosition().z
                           );
                           poseStack.mulPose(Axis.YP.rotationDegrees(-player.getViewYRot(partialTick)));
                           boolean isFirstPerson = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
                           boolean isGrounded = player.onGround();
                           if (player instanceof ViltrumiteFlightPlayer flightPlayer) {
                              isGrounded = flightPlayer.getFlightState() == FlightState.NONE;
                           }

                           float lookPitch = player.getViewXRot(partialTick);
                           float tiltPitch;
                           if (isFirstPerson) {
                              tiltPitch = lookPitch;
                           } else {
                              tiltPitch = lookPitch * 0.45F;
                           }

                           float tiltRoll = 0.0F;
                           if (chopType == 1) {
                              if (!isFirstPerson && isGrounded) {
                                 tiltRoll = 0.0F;
                              } else {
                                 tiltRoll = isLeft ? -15.0F : 15.0F;
                              }
                           } else {
                              tiltPitch -= 15.0F;
                              tiltRoll = isLeft ? 25.0F : -25.0F;
                           }

                           poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch));
                           poseStack.mulPose(Axis.ZP.rotationDegrees(tiltRoll));
                           poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                           dev.baranhan.viltrumitecore.hero.ChopStyle style = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).chopStyle(player);
                           if (style.arcScale() != 1.0F) {
                              poseStack.scale(style.arcScale(), style.arcScale(), style.arcScale());
                           }
                           PoseStack modelViewStack = RenderSystem.getModelViewStack();
                           modelViewStack.pushPose();
                           modelViewStack.setIdentity();
                           RenderSystem.applyModelViewMatrix();
                           RenderSystem.enableBlend();
                           RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
                           RenderSystem.disableCull();
                           RenderSystem.enableDepthTest();
                           RenderSystem.depthMask(false);
                           RenderSystem.setShader(GameRenderer::getPositionColorShader);
                           Tesselator tesselator = Tesselator.getInstance();
                           BufferBuilder buffer = tesselator.getBuilder();
                           buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                           Matrix4f matrix = poseStack.last().pose();
                           int outerR = 36;
                           int innerR = 30;
                           float pixelScale = 0.0625F;
                           float startAngle;
                           float sweepAngle;
                           if (chopType == 1) {
                              startAngle = isLeft ? 45.0F : 135.0F;
                              sweepAngle = isLeft ? 795.0F : -795.0F;
                           } else {
                              startAngle = isLeft ? 190.0F : -10.0F;
                              sweepAngle = isLeft ? -180.0F : 180.0F;
                           }

                           // Air ripple (style.ripple): a faint ring just outside the arc, same sweep.
                           int extent = style.ripple() ? outerR + 4 : outerR;
                           int innerRgb = style.trailInner();
                           int outerRgb = style.trailOuter();
                           for (int x = -extent; x <= extent; x++) {
                              for (int y = -extent; y <= extent; y++) {
                                 float dist = (float)Math.sqrt((double)(x * x + y * y));
                                 boolean rippleRing = style.ripple() && dist >= (float)outerR + 2.5F && dist <= (float)outerR + 3.5F;
                                 if (dist >= (float)innerR && dist <= (float)outerR || rippleRing) {
                                    float angle = (float)Math.toDegrees(Math.atan2((double)(-y), (double)(-x)));
                                    float relAngle = angle - startAngle;
                                    if (sweepAngle > 0.0F) {
                                       while (relAngle < 0.0F) {
                                          relAngle += 360.0F;
                                       }

                                       while (relAngle >= 360.0F) {
                                          relAngle -= 360.0F;
                                       }
                                    } else {
                                       while (relAngle > 0.0F) {
                                          relAngle -= 360.0F;
                                       }

                                       while (relAngle <= -360.0F) {
                                          relAngle += 360.0F;
                                       }
                                    }

                                    float angleProgress = relAngle / sweepAngle;
                                    if (!(angleProgress > 1.0F)) {
                                       float age = sweepProgress - angleProgress;
                                       if (!(age < 0.0F) && !(age > tailLength)) {
                                          float localAlpha = 1.0F - age / tailLength;
                                          int finalAlpha = (int)(255.0F * localAlpha * (rippleRing ? 0.3F : 1.0F));
                                          if (finalAlpha > 5) {
                                             int rgb = !rippleRing && (dist > (float)outerR - 1.5F || dist < (float)innerR + 1.5F) ? outerRgb : innerRgb;
                                             int r = rgb >> 16 & 0xFF;
                                             int g = rgb >> 8 & 0xFF;
                                             int b = rgb & 0xFF;

                                             drawPaintPixel(matrix, buffer, x, y, pixelScale, r, g, b, finalAlpha);
                                          }
                                       }
                                    }
                                 }
                              }
                           }

                           tesselator.end();
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
            }
         }
      }
   }

   private static void drawPaintPixel(Matrix4f matrix, BufferBuilder buffer, int gridX, int gridY, float pixelScale, int r, int g, int b, int alpha) {
      float cx = (float)gridX * pixelScale;
      float cy = (float)gridY * pixelScale;
      float half = pixelScale * 0.5F;
      buffer.vertex(matrix, cx - half, cy - half, 0.0F).color(r, g, b, alpha).endVertex();
      buffer.vertex(matrix, cx + half, cy - half, 0.0F).color(r, g, b, alpha).endVertex();
      buffer.vertex(matrix, cx + half, cy + half, 0.0F).color(r, g, b, alpha).endVertex();
      buffer.vertex(matrix, cx - half, cy + half, 0.0F).color(r, g, b, alpha).endVertex();
   }
}
