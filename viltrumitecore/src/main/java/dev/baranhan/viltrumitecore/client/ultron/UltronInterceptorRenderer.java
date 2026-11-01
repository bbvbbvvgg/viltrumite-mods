package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.entity.ultron.UltronInterceptor;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Interceptor: the air machine. Hovers and flies on two thrust nacelles whose flames stretch with the thrust (speed,
 * acceleration, the boost and the dive), banks hard into turns and noses down as it accelerates. The left nacelle is
 * shot off at critical damage (its flame dies with it); dying, it spirals down.
 */
public class UltronInterceptorRenderer extends UltronUnitRenderer<UltronInterceptor> {
   public UltronInterceptorRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.INTERCEPTOR, 0.5F, new String[]{"gun_r", "gun_l", "body", "bay_r"}, new String[]{"flame_r", "flame_l"},
         new String[]{"flame_r", "flame_l"}, new float[][]{{0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 1.0F}}, "nacelle_l",
         List.of("body", "wing_r", "wing_l", "nacelle_r", "nacelle_l", "tail_r", "tail_l"));
   }

   @Override
   protected void base(BakedGeoModel model, UltronInterceptor e, float partial, float age, float speed, float combat) {
      this.hover(model, e, partial, age, speed);
   }

   /** Thrust 0..1.6 from the speed, the acceleration and the boost / dive moves. */
   static float thrust(UltronInterceptor e, float partial) {
      if (!e.isAlive()) {
         return 0.0F;
      }
      float speed = Mth.lerp(partial, e.clientSpeedO, e.clientSpeed);
      float accel = Mth.lerp(partial, e.clientAccelO, e.clientAccel);
      float t = 0.35F + speed * 1.3F + Math.max(0.0F, accel) * 1.6F;
      float move = e.clientMoveSeconds(partial);
      if (move >= 0.0F && e.clientMove == UltronMove.INTERCEPTOR_BOOST) {
         t += 0.9F * (1.0F - move / (e.clientMove.length / 20.0F));
      } else if (move >= 0.0F && e.clientMove == UltronMove.INTERCEPTOR_DIVE) {
         t += 0.6F;
      }
      return Mth.clamp(t, 0.2F, 1.6F);
   }

   @Override
   protected void glowPose(BakedGeoModel model, UltronInterceptor e, float partial, float age) {
      float thrust = thrust(e, partial);
      for (String name : new String[]{"flame_r", "flame_l"}) {
         GeoBone flame = model.getBone(name);
         if (flame != null) {
            flame.scaleZ = 0.4F + thrust * (0.9F + 0.12F * Mth.sin(age * 2.3F + name.length()));
            flame.scaleX = flame.scaleY = 0.9F + 0.1F * thrust;
         }
      }
      if (e.clientPartLost) {
         hideTree(model.getBone("nacelle_l"), true);
      }
   }

   @Override
   protected void transform(PoseStack stack, UltronInterceptor e, float partial, float age) {
      super.transform(stack, e, partial, age);
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      float c = 7.0F / 16.0F;
      if (dying >= 0.0F) {
         stack.translate(0.0F, c, 0.0F);
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 14.0F));
         stack.mulPose(Axis.XP.rotationDegrees(-Math.min(45.0F, dying * 2.5F)));
         stack.translate(0.0F, -c, 0.0F);
      } else {
         this.attitude(stack, e, partial, 7.0F, 1.4F, 1.0F);
      }
   }

   @Override
   protected void extras(UltronInterceptor e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      if (dying >= 0.0F) {
         return;
      }
      Matrix4f m = stack.last().pose();
      float thrust = thrust(e, partial);
      for (int i = 0; i < 2; i++) {
         if (this.nozzleFound[i] && !(i == 1 && e.clientPartLost)) {
            this.flame(e, partial, vc, m, this.nozzleAt[i], this.nozzleDir[i], 0.25F + 0.75F * thrust, 0.11F + 0.04F * thrust, Math.min(1.0F, 0.5F + thrust * 0.5F),
               age);
         }
      }
   }
}
