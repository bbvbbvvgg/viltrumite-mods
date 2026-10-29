package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The volumetric Road Roller of DIO's ult, staged by RollerChoreo on the
 * synced ult clock: it rides under DIO down his arc, lands on the slam tick
 * (squash + bounce), sinks a little, dents with every pulse of the barrage,
 * shakes through WRYYY and is gone on the resume tick (the server explosion
 * packet paints the blast). Drums spin in the air. Render only: the server
 * owns every hit.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class RoadRollerScene {
   public static final ResourceLocation GEO = new ResourceLocation("viltrumitecore", "geo/dio/road_roller.geo.json");
   public static final ResourceLocation TEXTURE = new ResourceLocation("viltrumitecore", "textures/entity/dio/road_roller.png");
   public static final ResourceLocation GLOW = new ResourceLocation("viltrumitecore", "textures/entity/dio/road_roller_glow.png");
   private static final Map<Integer, Scene> SCENES = new HashMap<>();
   private static ClientLevel lastLevel;

   private RoadRollerScene() {
   }

   static final class Scene {
      final float yaw;
      boolean appeared;

      Scene(float yaw) {
         this.yaw = yaw;
      }
   }

   /** Roller heading of a running ult (degrees), or NaN. */
   public static float yaw(int playerId) {
      Scene scene = SCENES.get(playerId);
      return scene == null ? Float.NaN : scene.yaw;
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ShaderCompat.isShadowPass()) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         lastLevel = level;
         SCENES.clear();
      }

      if (level == null) {
         return;
      }

      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
      boolean drew = false;
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = DioView.of(player);
         if (snapshot == null || !DioView.is(snapshot, HeroAction.ROAD_ROLLER) || snapshot.actionTarget() == null) {
            SCENES.remove(player.getId());
            continue;
         }

         Scene scene = SCENES.computeIfAbsent(player.getId(), id -> new Scene(player.getYRot()));
         double t = snapshot.actionElapsed() + (double)partial;
         RollerChoreo.Pose pose = RollerChoreo.at(t);
         if (!pose.visible()) {
            continue;
         }

         Vec3 land = snapshot.actionTarget();
         if (!scene.appeared) {
            scene.appeared = true;
            Vec3 feet = player.getPosition(partial);
            DioFxClient.burst(feet.add(0, -1.0, 0), new Vec3(0, -1, 0), 26, 0.4, 0.07F, 255, 220, 120);
            DioFxClient.ring(feet.add(0, -1.0, 0), new Vec3(0, 1, 0), 0.5, 32, 0.06F, 255, 230, 160, 220, 8);
            CameraShake.addAt(feet, 0.5F, 32.0);
            if (player == client.player) {
               FlashOverlay.flash(0.25F, 4);
            }
         }

         drew |= draw(stack, buffers, cam, player, scene, pose, land, t, partial);
      }

      if (drew) {
         buffers.endBatch();
      }
   }

   private static boolean draw(PoseStack stack, MultiBufferSource.BufferSource buffers, Vec3 cam, Player player, Scene scene, RollerChoreo.Pose pose,
      Vec3 land, double t, float partial) {
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return false;
      }

      double yawRad = Math.toRadians(scene.yaw);
      Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Vec3 origin;
      if (pose.landed()) {
         origin = land.subtract(forward.scale(RollerChoreo.DIO_FORWARD)).add(0.0, -pose.sink(), 0.0);
      } else {
         Vec3 feet = player.getPosition(partial);
         origin = new Vec3(feet.x, feet.y - DioRules.ROLLER_SEAT * pose.grow(), feet.z).subtract(forward.scale(RollerChoreo.DIO_FORWARD));
      }

      if (pose.tremble() > 0.0F) {
         Random random = new Random((long)Math.floor(t * 3.0));
         double a = 0.07 * pose.tremble();
         origin = origin.add((random.nextDouble() - 0.5) * a, (random.nextDouble() - 0.5) * a * 0.6, (random.nextDouble() - 0.5) * a);
      }

      pose(model, pose, t);
      stack.pushPose();
      stack.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - scene.yaw));
      float s = RollerChoreo.SCALE * pose.grow();
      stack.scale(s, s, s);
      // Pitch around the middle of the roller; squash from the ground up.
      float mid = 14.0F / 16.0F;
      stack.translate(0.0F, mid, 0.0F);
      stack.mulPose(Axis.XP.rotationDegrees(pose.pitchDeg()));
      stack.translate(0.0F, -mid, 0.0F);
      float bulge = 1.0F + (1.0F - pose.squash()) * 0.5F;
      stack.scale(bulge, pose.squash(), bulge);
      BlockPos at = BlockPos.containing(origin.add(0.0, 1.5, 0.0));
      int light = LevelRenderer.getLightColor(player.level(), at);
      float alpha = pose.alpha();
      RenderType type = alpha >= 0.995F ? RenderType.entityCutoutNoCull(TEXTURE) : RenderType.entityTranslucent(TEXTURE);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha, null);
      float heat = 0.6F + 0.4F * pose.tremble();
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, heat * alpha,
         heat * alpha, heat * alpha, alpha, null);
      stack.popPose();
      return true;
   }

   /** Bone layer: spinning drums, dents in the hood and roof, a wobbling body under the barrage. */
   private static void pose(BakedGeoModel model, RollerChoreo.Pose pose, double t) {
      model.resetBones();
      float spin = (float)Math.toRadians(pose.drumSpinDeg());
      rot(model, "front_drum", spin, 0, 0);
      rot(model, "rear_drum", spin * 1.15F, 0, 0);
      float dent = pose.dent();
      GeoBone hood = model.getBone("hood");
      if (hood != null) {
         hood.posY -= 3.2F * dent;
         hood.rotX += 0.12F * dent;
         hood.scaleY = 1.0F - 0.3F * dent;
      }

      GeoBone roof = model.getBone("roof");
      if (roof != null) {
         roof.posY -= 4.0F * dent;
         roof.rotZ += 0.08F * dent;
         roof.scaleY = 1.0F - 0.4F * dent;
         roof.scaleX = 1.0F + 0.08F * dent;
      }

      GeoBone body = model.getBone("body");
      if (body != null && t > DioRules.ROLLER_SLAM && t <= DioRules.ROLLER_BARRAGE_END) {
         // Every pulse shoves the body down for a tick, then it springs back.
         double phase = ((t - DioRules.ROLLER_SLAM) % DioRules.ROLLER_PULSE) / DioRules.ROLLER_PULSE;
         float kick = (float)Math.exp(-phase * 4.0);
         body.posY -= 0.9F * kick;
         body.rotZ += 0.03F * kick * (((int)((t - DioRules.ROLLER_SLAM) / DioRules.ROLLER_PULSE) & 1) == 0 ? 1 : -1);
      }

      GeoBone yoke = model.getBone("yoke");
      if (yoke != null) {
         yoke.rotY += (float)Math.toRadians(4.0 * pose.tremble() * Math.sin(t * 2.3));
      }
   }

   private static void rot(BakedGeoModel model, String name, float x, float y, float z) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.rotX += x;
         bone.rotY += y;
         bone.rotZ += z;
      }
   }

   @Nullable
   static Vec3 landing(Player player) {
      HeroPublicSnapshot snapshot = DioView.of(player);
      return snapshot != null && DioView.is(snapshot, HeroAction.ROAD_ROLLER) ? snapshot.actionTarget() : null;
   }
}
