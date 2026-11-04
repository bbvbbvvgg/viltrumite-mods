package dev.baranhan.viltrumitecore.client.city;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The phone in a citizen's hand (#151): a small 3D model (a box of 1.8 x 3.3 x 0.32 model pixels with its case,
 * camera back and a lit screen, {@link PhonePainter}) attached to the main arm, so it follows every pose of the
 * shared animation system ({@link CitizenPoser}). Drawn only while the current phone moment has it in the hand
 * (out of the pocket and until it is put away); the grip turns from reading (screen to the eyes) to the ear
 * (screen to the cheek) with {@link CitizenPoser#earGrip}. The screen glows while lit, as a real screen does
 * at night. Nothing is drawn beyond the poser's level of detail (far people never get a phone moment).
 */
public class CitizenPhoneLayer extends RenderLayer<CitizenEntity, CitizenModel> {
   /**
    * Phone size in model pixels: width, length, thickness (a touch larger than a real one, about 10 x 19 cm on a
    * 1.8 m body, so it still reads in the hand from a few blocks away; the playtest showed the true size vanishes).
    */
   static final float W = 1.8F;
   static final float L = 3.3F;
   static final float T = 0.32F;
   /** Reading grip in the right arm's space (model pixels): in the fingers, in front of the palm. */
   static final float READ_Y = 10.2F;
   static final float READ_Z = -2.3F;
   static final float READ_TILT = -45.0F;
   /** Ear grip: the phone centre at the ear (solved with the poser's EAR arm pose), screen turned to the head. */
   static final float EAR_X = 1.15F;
   static final float EAR_Y = 8.2F;
   private static ResourceLocation texture;

   public CitizenPhoneLayer(RenderLayerParent<CitizenEntity, CitizenModel> parent) {
      super(parent);
   }

   static ResourceLocation texture() {
      if (texture == null) {
         int[] argb = PhonePainter.paint();
         NativeImage img = new NativeImage(PhonePainter.W, PhonePainter.H, true);
         for (int y = 0; y < PhonePainter.H; y++) {
            for (int x = 0; x < PhonePainter.W; x++) {
               int c = argb[y * PhonePainter.W + x];
               img.setPixelRGBA(x, y, c & 0xFF00FF00 | (c & 0xFF) << 16 | c >> 16 & 0xFF);
            }
         }
         texture = new ResourceLocation("viltrumitecore", "citizen/phone");
         Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(img));
      }
      return texture;
   }

   /** Reading-grip x: a touch farther in for the slim arm (3 px wide instead of 4). */
   static float readX(boolean slim) {
      return slim ? -0.5F : -1.0F;
   }

   /**
    * Into the phone's own space (model pixels, centre of the phone, screen on -z, top at +y) from the model space:
    * the posed main arm, then the grip between reading ({@code k} 0) and the ear (1).
    */
   static void grip(PoseStack pose, CitizenModel model, float k) {
      model.rightArm.translateAndRotate(pose);
      pose.translate(Mth.lerp(k, readX(model.slimArms()), EAR_X) / 16.0F, Mth.lerp(k, READ_Y, EAR_Y) / 16.0F, Mth.lerp(k, READ_Z, 0.0F) / 16.0F);
      pose.mulPose(Axis.YP.rotationDegrees(-90.0F * k));
      pose.mulPose(Axis.XP.rotationDegrees(READ_TILT * (1.0F - k)));
      pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
   }

   @Override
   public void render(PoseStack pose, MultiBufferSource buffers, int light, CitizenEntity e, float limbSwing, float limbAmount, float partialTick,
      float age, float netHeadYaw, float headPitch) {
      if (e.isInvisible()) {
         return;
      }
      CitizenPoser.Anim a = CitizenPoser.anim(e);
      if (!CitizenPoser.phoneInHand(a)) {
         return;
      }

      PhoneRoutine.Moment m = a.phone;
      CitizenModel model = this.getParentModel();
      float k = CitizenPoser.earGrip(m);
      pose.pushPose();
      grip(pose, model, k);

      VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture()));
      Matrix4f mat = pose.last().pose();
      Matrix3f nrm = pose.last().normal();
      int ci = PhonePainter.caseOf(e.skin());
      float x = W / 2.0F;
      float y = L / 2.0F;
      float z = T / 2.0F;
      // case: the sides in the case colour, the back (+z) with the camera, the front (-z) a dark bezel
      float cu0 = (ci * 4 + 1) / 64.0F;
      float cu1 = (ci * 4 + 3) / 64.0F;
      float cv0 = 1.0F / 64.0F;
      float cv1 = 3.0F / 64.0F;
      quad(vc, mat, nrm, light, -x, -y, z, x, -y, z, x, y, z, -x, y, z, ci * 4 / 64.0F, 12.0F / 64.0F, (ci * 4 + 4) / 64.0F, 4.0F / 64.0F, 0, 0, 1);
      quad(vc, mat, nrm, light, x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 1.0F / 64.0F, 3.0F / 64.0F, 3.0F / 64.0F, 1.0F / 64.0F, 0, 0, -1);
      quad(vc, mat, nrm, light, x, -y, z, x, -y, -z, x, y, -z, x, y, z, cu0, cv1, cu1, cv0, 1, 0, 0);
      quad(vc, mat, nrm, light, -x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, cu0, cv1, cu1, cv0, -1, 0, 0);
      quad(vc, mat, nrm, light, -x, y, z, x, y, z, x, y, -z, -x, y, -z, cu0, cv1, cu1, cv0, 0, 1, 0);
      quad(vc, mat, nrm, light, -x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z, cu0, cv1, cu1, cv0, 0, -1, 0);

      // the screen just in front of the bezel; the top of the picture at the far end (+y, past the fingers)
      int screen = PhonePainter.screenFor(m);
      float off = PhonePainter.scroll(screen, m);
      float su0 = screen * PhonePainter.SCREEN_W / 64.0F;
      float su1 = (screen + 1) * PhonePainter.SCREEN_W / 64.0F;
      float sv0 = (PhonePainter.SCREENS_Y + off) / 64.0F;
      float sv1 = (PhonePainter.SCREENS_Y + off + PhonePainter.SCREEN_H) / 64.0F;
      float sx = x - 0.1F;
      float sy = y - 0.18F;
      float sz = -z - 0.01F;
      int lit = m.screenOn() ? LightTexture.FULL_BRIGHT : light;
      quad(vc, mat, nrm, lit, sx, sy, sz, -sx, sy, sz, -sx, -sy, sz, sx, -sy, sz, su0, sv0, su1, sv1, 0, 0, -1);
      pose.popPose();
   }

   /** Four corners in order with the texture rectangle (u0,v0) at the first corner and (u1,v1) at the third. */
   private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
      float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
      vertex(vc, m, n, light, x0, y0, z0, u0, v0, nx, ny, nz);
      vertex(vc, m, n, light, x1, y1, z1, u1, v0, nx, ny, nz);
      vertex(vc, m, n, light, x2, y2, z2, u1, v1, nx, ny, nz);
      vertex(vc, m, n, light, x3, y3, z3, u0, v1, nx, ny, nz);
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, float y, float z, float u, float v, float nx, float ny,
      float nz) {
      vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
   }
}
