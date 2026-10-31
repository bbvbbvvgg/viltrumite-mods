package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Chitauri chariot: hull banked by the synced roll and pitched with the flight, the idle flutter of fins and engines,
 * the cannon recoil, engine glow and twin exhaust flames that grow with the speed (sputtering while it goes down).
 * The crew are separate soldier entities placed on the seats by the server. Client only.
 */
public class ChitauriChariotRenderer extends EntityRenderer<ChitauriChariot> {
   /** Engine nozzle centres in model space (blocks; baked axes: +X is the chariot's right). */
   static final float NOZZLE_X = 12.5F / 16.0F;
   static final float NOZZLE_Y = 2.0F / 16.0F;
   static final float NOZZLE_Z = 23.0F / 16.0F;
   private final Vector3f a = new Vector3f();
   private final Vector3f b = new Vector3f();

   public ChitauriChariotRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 1.1F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriChariot entity) {
      return ChitauriAssets.CHARIOT_TEX;
   }

   @Override
   public void render(ChitauriChariot e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.CHARIOT_GEO);
      if (model == null) {
         return;
      }
      // a chariot still inside the portal tube is hidden until its nose crosses the opening
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      if (portal != null && !portal.inFront(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + 0.55,
            Mth.lerp(partial, e.zo, e.getZ()), 1.6)) {
         return;
      }
      float age = e.tickCount + partial;
      float roll = Mth.lerp(partial, e.clientRollO, e.clientRoll);
      float speed = (float)e.clientSpeed;
      boolean dying = e.dying() || !e.isAlive();
      pose(model, e, age, roll);
      stack.pushPose();
      stack.translate(0.0, 0.55, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
      stack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
      stack.mulPose(Axis.ZP.rotationDegrees(-roll));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(ChitauriAssets.CHARIOT_TEX)), light, overlay,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      if (!ShaderCompat.isShadowPass()) {
         float g = engineGlow(speed, dying, age, e.getId());
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ChitauriAssets.CHARIOT_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
         VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
         Matrix4f m = stack.last().pose();
         for (int side = -1; side <= 1; side += 2) {
            float sputter = dying ? (((int)(age * 3.1F) + side) % 3 == 0 ? 0.15F : 0.8F) : 1.0F;
            this.flame(vc, m, side * NOZZLE_X, flameLength(speed, age, side) * sputter, age, side, sputter);
         }
      }
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }

   static void pose(BakedGeoModel model, ChitauriChariot e, float age, float roll) {
      model.resetBones();
      Animation idle = AnimCache.animation(ChitauriAssets.CHARIOT_ANIM, "idle");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0 + e.getId() * 0.37) % idle.lengthSeconds(), 1.0F);
      }
      Animation cannon = AnimCache.animation(ChitauriAssets.CHARIOT_ANIM, "cannon");
      float since = age - e.clientCannonStart;
      if (cannon != null && since >= 0.0F && since < cannon.lengthSeconds() * 20.0F) {
         AnimationController.overlay(cannon, model, since / 20.0, 1.0F);
      }
      // the fins work as ailerons against the bank
      float flap = Mth.clamp(roll, -40.0F, 40.0F) * 0.35F;
      ChitauriSoldierRenderer.deg(model.getBone("fin_l"), 0.0F, 0.0F, flap);
      ChitauriSoldierRenderer.deg(model.getBone("fin_r"), 0.0F, 0.0F, flap);
   }

   /** Pure: glow of engines and lights: idle hum, brighter with speed, failing while it goes down. */
   static float engineGlow(float speed, boolean dying, float age, int id) {
      float g = 0.72F + Mth.clamp(speed / 0.7F, 0.0F, 1.0F) * 0.28F + 0.03F * Mth.sin(age * 0.8F + id);
      if (dying) {
         g *= ((int)(age * 2.3F + id) & 3) == 0 ? 0.25F : 0.7F;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   /** Pure: exhaust flame length in blocks (idle flicker, longer at cruise speed). */
   static float flameLength(float speed, float age, int side) {
      float s = Mth.clamp(speed / 0.7F, 0.0F, 1.0F);
      return (0.45F + 1.55F * s) * (0.86F + 0.14F * Mth.sin(age * 2.7F + side * 1.3F));
   }

   /** Three crossed tapering planes (outer blue, inner white-hot) and the nozzle glow, pointing backwards (+Z). */
   private void flame(VertexConsumer vc, Matrix4f m, float x, float length, float age, int side, float strength) {
      if (length <= 0.05F) {
         return;
      }
      for (int layer = 0; layer < 2; layer++) {
         float radius = layer == 0 ? 0.2F : 0.09F;
         float len = layer == 0 ? length : length * 0.55F;
         int r = layer == 0 ? 70 : 200;
         int g = layer == 0 ? 140 : 230;
         int alpha = (int)((layer == 0 ? 150 : 235) * strength);
         for (int k = 0; k < 3; k++) {
            double ang = k * Math.PI / 3.0 + age * 0.05 * side;
            float cx = (float)Math.cos(ang) * radius;
            float cy = (float)Math.sin(ang) * radius;
            GlowDraw.vertex(vc, m, x - cx, NOZZLE_Y - cy, NOZZLE_Z, r, g, 255, alpha);
            GlowDraw.vertex(vc, m, x + cx, NOZZLE_Y + cy, NOZZLE_Z, r, g, 255, alpha);
            GlowDraw.vertex(vc, m, x, NOZZLE_Y, NOZZLE_Z + len, r, g, 255, 0);
            GlowDraw.vertex(vc, m, x, NOZZLE_Y, NOZZLE_Z + len, r, g, 255, 0);
         }
      }
      this.a.set(x, NOZZLE_Y, NOZZLE_Z + 0.01F);
      GlowDraw.disc(vc, m, this.a, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 0.36F, 0.35F, 120, 180, 255, (int)(190 * strength));
      this.b.set(x, NOZZLE_Y, NOZZLE_Z - 0.01F);
      GlowDraw.disc(vc, m, this.b, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 0.24F, 0.4F, 170, 215, 255, (int)(150 * strength));
   }
}
