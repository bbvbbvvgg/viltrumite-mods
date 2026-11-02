package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderView;
import dev.baranhan.viltrumitecore.client.ghostrider.HellfireDraw;
import dev.baranhan.viltrumitecore.client.hero.HeroLiftArms;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person on the Hell Cycle: the vanilla hands (and whatever they hold) give way to both arms reaching from the
 * shoulders to the grips, which follow the bars as they steer and the frame as it leans; the Rider's fists burn on the
 * grips. Look down to see the bars. RenderHandEvent, client only.
 */
public final class HellCycleCockpit {
   /** Grip centres in model pixels (tools/assets/ghost_rider_bike.py: grips +-8.4, 23.6, -3.9). */
   static final float GRIP_X = 8.4F;
   static final float GRIP_Y = 23.6F;
   static final float GRIP_Z = -3.9F;
   /** Where the raked steering axis crosses grip height (HEAD z -6.2 + 4.4 px * tan 32 deg). */
   static final float AXIS_Z = -3.45F;
   /** Shoulder to the grip's centre along the arm (the 12 px arm closes its fist round the grip). */
   static final float REACH = 9.0F / 16.0F;
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

   private HellCycleCockpit() {
   }

   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || !(player.getVehicle() instanceof HellCycleEntity bike)) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }
      float partial = event.getPartialTick();
      Camera camera = mc.gameRenderer.getMainCamera();
      Vector3f[] grips = grips(bike, camera, partial);
      EntityRenderer<? super LocalPlayer> r = mc.getEntityRenderDispatcher().getRenderer(player);
      if (!(r instanceof PlayerRenderer renderer)) {
         return;
      }
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      ResourceLocation tex = HeroLiftArms.handTexture(player, player.getSkinTextureLocation());
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      // grips[0] is the one further left on screen
      arm(stack, buffers, light, tex, model.leftArm, model.leftSleeve, grips[0], -1.0F);
      arm(stack, buffers, light, tex, model.rightArm, model.rightSleeve, grips[1], 1.0F);
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
   }

   /** The two grips in view space (camera at the origin, -z ahead), sorted left to right on screen. */
   static Vector3f[] grips(HellCycleEntity bike, Camera camera, float partial) {
      Vec3 cam = camera.getPosition();
      Vec3 pos = bike.getPosition(partial);
      float park = Mth.lerp(partial, bike.parkO, bike.park);
      Matrix4f m = new Matrix4f().translate((float)(pos.x - cam.x), (float)(pos.y - cam.y), (float)(pos.z - cam.z));
      HellCycleRenderer.tilt(m, bike, partial, park, true);
      float steer = Mth.lerp(partial, bike.steerO, bike.steer);
      float speedF = Mth.clamp((float)(bike.visualSpeed / HellCycleRules.MAX_SPEED), 0.0F, 1.6F);
      float steerYaw = HellCycleRenderer.steerDegrees(steer, speedF, bike.flag(HellCycleEntity.FLAG_DRIFT)) * HellCycleRenderer.COS_RAKE;
      Quaternionf view = new Quaternionf().rotateX(camera.getXRot() * Mth.DEG_TO_RAD).rotateY((camera.getYRot() + 180.0F) * Mth.DEG_TO_RAD);
      Vector3f[] out = new Vector3f[2];
      for (int i = 0; i < 2; i++) {
         out[i] = grip(i == 0 ? -1.0F : 1.0F, steerYaw, m, view);
      }
      if (out[0].x > out[1].x) {
         Vector3f t = out[0];
         out[0] = out[1];
         out[1] = t;
      }
      return out;
   }

   /** Pure: one grip (side -1/+1 in model x) steered round the axis, through the bike matrix, into view space. */
   static Vector3f grip(float side, float steerYawDeg, Matrix4f bike, Quaternionf view) {
      float a = steerYawDeg * Mth.DEG_TO_RAD;
      float x = side * GRIP_X;
      float z = GRIP_Z - AXIS_Z;
      float sx = x * Mth.cos(a) + z * Mth.sin(a);
      float sz = -x * Mth.sin(a) + z * Mth.cos(a) + AXIS_Z;
      Vector3f p = bike.transformPosition(new Vector3f(sx / 16.0F, GRIP_Y / 16.0F, sz / 16.0F));
      return view.transform(p);
   }

   /** One arm from its shoulder to the grip: the model arm (flipped into model space) turned onto the shoulder-grip line. */
   static void arm(PoseStack stack, MultiBufferSource buffers, int light, ResourceLocation tex, ModelPart arm, ModelPart sleeve, Vector3f grip,
         float side) {
      Vector3f shoulder = new Vector3f(side * 0.34F, -0.36F, 0.1F);
      Vector3f dir = new Vector3f(grip).sub(shoulder);
      if (dir.lengthSquared() < 1.0E-4F) {
         return;
      }
      dir.normalize();
      Vector3f root = new Vector3f(grip).sub(new Vector3f(dir).mul(REACH));
      float ax = arm.xRot, ay = arm.yRot, az = arm.zRot;
      float sx = sleeve.xRot, sy = sleeve.yRot, sz = sleeve.zRot;
      boolean av = arm.visible, sv = sleeve.visible;
      arm.setRotation(0.0F, 0.0F, 0.0F);
      sleeve.setRotation(0.0F, 0.0F, 0.0F);
      arm.visible = true;
      stack.pushPose();
      stack.translate(root.x, root.y, root.z);
      stack.mulPose(new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, dir.x, dir.y, dir.z));
      stack.mulPose(Axis.ZP.rotationDegrees(180.0F));
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
