package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderView;
import dev.baranhan.viltrumitecore.client.ghostrider.HellfireDraw;
import dev.baranhan.viltrumitecore.client.hero.HeroLiftArms;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person on the Hell Cycle: the vanilla hands (and whatever they hold) give way to both arms reaching from the
 * rider's shoulders to the grips. The arms are drawn in the world pass in the bike's own frame, so they share the
 * world's projection, camera roll and depth with the bars: the shoulders stay on the body (which faces the bars), the
 * grips follow the bars as they steer and the frame as it leans, and looking round never drags the arms across the
 * view (playtest #3: hand-pass arms fixed to the camera crossed the screen). The Rider's fists burn on the grips.
 * Client only.
 */
public final class HellCycleCockpit {
   /** Grip centres in model pixels (tools/assets/ghost_rider_bike.py: grips +-8.4, 23.6, -3.9). */
   static final float GRIP_X = 8.4F;
   static final float GRIP_Y = 23.6F;
   static final float GRIP_Z = -3.9F;
   /** Where the raked steering axis crosses grip height (HEAD z -6.2 + 4.4 px * tan 32 deg). */
   static final float AXIS_Z = -3.45F;
   /** Shoulder pivots in bike pixels: the vanilla arm pivot (5 px out, 22 px over the feet) on the seat, leant forward. */
   static final float SHOULDER_X = 5.0F;
   static final float SHOULDER_Y = (float)(HellCycleRules.SEAT_Y * 16.0) + 22.0F;
   static final float SHOULDER_Z = (float)(HellCycleRules.SEAT_BACK * 16.0) - 1.5F;
   /** Pivot to the knuckles of the vanilla arm (its cube runs 10 px down from the pivot); the fist closes 1.5 px in. */
   static final float ARM_PX = 10.0F;
   static final float FIST_PX = 1.5F;
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Vector3f DOWN = new Vector3f(0.0F, -1.0F, 0.0F);

   private HellCycleCockpit() {
   }

   /** Riding: no vanilla hands or held items (the arms are drawn in the world pass). */
   public static void onRenderHand(RenderHandEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.getVehicle() instanceof HellCycleEntity) {
         event.setCanceled(true);
      }
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ShaderCompat.isShadowPass()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      Camera camera = event.getCamera();
      if (player == null || !(player.getVehicle() instanceof HellCycleEntity bike) || player.isInvisible() || camera.isDetached()
         || camera.getEntity() != player) {
         return;
      }
      EntityRenderer<? super LocalPlayer> r = mc.getEntityRenderDispatcher().getRenderer(player);
      if (!(r instanceof PlayerRenderer renderer)) {
         return;
      }
      float partial = event.getPartialTick();
      Matrix4f frame = bikeFrame(bike, camera.getPosition(), partial);
      float steer = Mth.lerp(partial, bike.steerO, bike.steer);
      float speedF = Mth.clamp((float)(bike.visualSpeed / HellCycleRules.MAX_SPEED), 0.0F, 1.6F);
      float steerYaw = HellCycleRenderer.steerDegrees(steer, speedF, bike.flag(HellCycleEntity.FLAG_DRIFT)) * HellCycleRenderer.COS_RAKE;
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ResourceLocation tex = HeroLiftArms.handTexture(player, player.getSkinTextureLocation());
      int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partial);
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      Vector3f[] grips = new Vector3f[2];
      stack.pushPose();
      stack.last().pose().mul(frame);
      stack.last().normal().mul(new Matrix3f(frame));
      for (int i = 0; i < 2; i++) {
         float side = i == 0 ? -1.0F : 1.0F;
         Vector3f grip = grip(side, steerYaw);
         // model -X is the rider's left (bike frame: front -Z, left -X), the player model's leftArm likewise
         arm(stack, buffers, light, tex, i == 0 ? model.leftArm : model.rightArm, i == 0 ? model.leftSleeve : model.rightSleeve,
            shoulder(side), grip);
         grips[i] = frame.transformPosition(new Vector3f(grip));
      }
      stack.popPose();
      HeroPublicSnapshot s = GhostRiderView.of(player);
      float fire = s == null ? 0.0F : GhostRiderView.fire(s, partial);
      if (fire > 0.0F) {
         VertexConsumer vc = buffers.getBuffer(HellfireDraw.type());
         Matrix4f m = stack.last().pose();
         float age = player.tickCount + partial;
         for (int side = 0; side < 2; side++) {
            Vector3f g = grips[side];
            for (int k = 0; k < 2; k++) {
               float h = (0.2F + 0.07F * k) * HellfireDraw.flicker(age, side * 7 + k * 3) * fire;
               HellfireDraw.flame(vc, m, g.x, g.y - 0.03F + k * 0.03F, g.z, UP, -g.x, -g.y, -g.z, 0.14F + 0.04F * k, h,
                  HellfireDraw.frame(age, side * 5 + k * 2), HellfireDraw.TONGUE, 255, 255, 255, (int)(210 * fire));
            }
         }
      }
      buffers.endBatch();
   }

   /** The bike's frame relative to the camera: position, yaw, ground pitch, lifts and lean (as HellCycleRenderer draws it). */
   static Matrix4f bikeFrame(HellCycleEntity bike, Vec3 cam, float partial) {
      Vec3 pos = bike.getPosition(partial);
      float park = Mth.lerp(partial, bike.parkO, bike.park);
      Matrix4f m = new Matrix4f().translate((float)(pos.x - cam.x), (float)(pos.y - cam.y), (float)(pos.z - cam.z));
      HellCycleRenderer.tilt(m, bike, partial, park, true);
      return m;
   }

   /** Pure: one grip (side -1 left / +1 right) in the bike frame (blocks), steered round the raked axis. */
   static Vector3f grip(float side, float steerYawDeg) {
      float a = steerYawDeg * Mth.DEG_TO_RAD;
      float x = side * GRIP_X;
      float z = GRIP_Z - AXIS_Z;
      float sx = x * Mth.cos(a) + z * Mth.sin(a);
      float sz = -x * Mth.sin(a) + z * Mth.cos(a) + AXIS_Z;
      return new Vector3f(sx / 16.0F, GRIP_Y / 16.0F, sz / 16.0F);
   }

   /** Pure: one shoulder pivot (side -1 left / +1 right) in the bike frame (blocks). */
   static Vector3f shoulder(float side) {
      return new Vector3f(side * SHOULDER_X / 16.0F, SHOULDER_Y / 16.0F, SHOULDER_Z / 16.0F);
   }

   /** Pure: how much the 10 px arm stretches so that its fist closes on a grip {@code distance} blocks from the shoulder. */
   static float stretch(float distance) {
      return Mth.clamp((distance * 16.0F + FIST_PX) / ARM_PX, 0.6F, 1.5F);
   }

   /**
    * One arm in the bike frame: the model arm hung from the shoulder (the renderer's x/y flip, so it hangs down -Y with its
    * front ahead like a standing arm), turned onto the shoulder-grip line and stretched to reach.
    */
   static void arm(PoseStack stack, MultiBufferSource buffers, int light, ResourceLocation tex, ModelPart arm, ModelPart sleeve, Vector3f shoulder,
         Vector3f grip) {
      Vector3f dir = new Vector3f(grip).sub(shoulder);
      float distance = dir.length();
      if (distance < 1.0E-3F) {
         return;
      }
      dir.div(distance);
      float ax = arm.xRot, ay = arm.yRot, az = arm.zRot;
      float sx = sleeve.xRot, sy = sleeve.yRot, sz = sleeve.zRot;
      boolean av = arm.visible, sv = sleeve.visible;
      arm.setRotation(0.0F, 0.0F, 0.0F);
      sleeve.setRotation(0.0F, 0.0F, 0.0F);
      arm.visible = true;
      stack.pushPose();
      stack.translate(shoulder.x, shoulder.y, shoulder.z);
      stack.mulPose(new Quaternionf().rotationTo(DOWN, dir));
      stack.scale(1.0F, stretch(distance), 1.0F);
      stack.scale(-1.0F, -1.0F, 1.0F);
      stack.translate(-arm.x / 16.0F, -arm.y / 16.0F, -arm.z / 16.0F);
      arm.render(stack, buffers.getBuffer(RenderType.entitySolid(tex)), light, OverlayTexture.NO_OVERLAY);
      if (sv) {
         sleeve.render(stack, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
      }
      stack.popPose();
      arm.setRotation(ax, ay, az);
      sleeve.setRotation(sx, sy, sz);
      arm.visible = av;
   }
}
