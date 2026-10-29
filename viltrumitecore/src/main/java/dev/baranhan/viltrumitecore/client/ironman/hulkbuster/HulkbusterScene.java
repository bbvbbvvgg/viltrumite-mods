package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkTextures;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterSuitUp;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The suit-up scene on every client (rework spec §4), always at full scale with the real Mark 48 plates.
 * Veronica's capsule (the pod model, enlarged to hold the machine) falls with a fire trail onto the frame point
 * (in the air variant it hangs there), opens, and shows the open Mark 48 frame (clip suit_assemble at 0). The
 * player backs in (HulkbusterSuitUp.path, predicted on the owner's client) and the plates close one by one on
 * the server's assembly clock (suit_assemble, plate lock ticks = HulkbusterLayer.PART_LOCK). When the torso
 * closes the frame becomes the player's body ({@link #bodyClosed}: same model, same scale, same place: no jump)
 * and the capsule flies off. Client only; the scene word comes from the snapshot (HulkbusterWire.SCENE).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterScene {
   static final ResourceLocation POD_GEO = new ResourceLocation("viltrumitecore", "geo/ironman/veronica/veronica_pod.geo.json");
   static final ResourceLocation POD_ANIMS = new ResourceLocation("viltrumitecore", "animations/ironman/veronica_pod.animation.json");
   /** The pod model is a 2.8-block man-sized capsule: enlarged to carry the 3.4-block open frame. */
   static final float POD_SCALE = 1.7F;
   /** Capsule fall: from this height above the frame, over the first ticks of the scene (air / ground). */
   static final double POD_FALL_HEIGHT = 70.0;
   static final int POD_FALL_AIR = 20;
   static final int POD_FALL_GROUND = 12;
   /** After the torso closes the empty capsule climbs away over these assemble ticks. */
   static final int POD_LEAVE = 22;
   /** Owner start positions of running scenes (entity id → feet when the scene was first seen). */
   private static final Map<Integer, Vec3> STARTS = new HashMap<>();

   private HulkbusterScene() {
   }

   /** Assemble tick of a scene snapshot, -1 before the assembly. */
   static int assemble(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.sceneAssemble(HulkbusterView.sceneWord(snapshot));
   }

   /** The torso closed in the suit-up: the machine is already the player's body (drawn by HulkbusterRenderer). */
   static boolean bodyClosed(@Nullable HeroPublicSnapshot snapshot, HulkbusterLayer.Phase phase) {
      return snapshot != null && phase == HulkbusterLayer.Phase.ASSEMBLING && assemble(snapshot) >= HulkbusterLayer.TORSO_TICK;
   }

   /** Scene tick (drop + assembly) with the partial tick. */
   static double sceneTicks(HeroPublicSnapshot snapshot, HulkbusterLayer.Phase phase, float partialTick) {
      int drop = HulkbusterView.sceneAir(snapshot) ? HulkbusterLayer.DROP_TICKS_AIR : HulkbusterLayer.DROP_TICKS_GROUND;
      return (phase == HulkbusterLayer.Phase.ASSEMBLING ? drop : 0) + snapshot.actionElapsed() + partialTick;
   }

   /** The frame's pose: open until the assembly starts, then the plates close on the server clock. */
   static void poseFrame(BakedGeoModel model, HeroPublicSnapshot snapshot, float partialTick) {
      Animation clip = AnimCache.animation(HulkbusterAssets.ANIMATIONS, "suit_assemble");
      if (clip == null) {
         model.resetBones();
         return;
      }

      HulkbusterLayer.Phase phase = HulkbusterView.phase(snapshot);
      double ticks = phase == HulkbusterLayer.Phase.ASSEMBLING ? snapshot.actionElapsed() + partialTick : 0.0;
      AnimationController.seek(clip, model, Math.min(clip.lengthSeconds(), ticks / 20.0));
   }

   // ------------------------------------------------------------------ owner path

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      HeroPublicSnapshot snapshot = player == null ? null : IronManView.of(player);
      HulkbusterLayer.Phase phase = snapshot == null ? HulkbusterLayer.Phase.NONE : HulkbusterView.phase(snapshot);
      if (player == null || !HulkbusterView.scene(phase) || snapshot.actionTarget() == null) {
         STARTS.clear();
         return;
      }

      // The scripted path (HulkbusterSuitUp.path), the same pure function the server follows; it corrects > 1 block.
      Vec3 start = STARTS.computeIfAbsent(player.getId(), id -> player.position());
      boolean air = HulkbusterView.sceneAir(snapshot);
      int drop = air ? HulkbusterLayer.DROP_TICKS_AIR : HulkbusterLayer.DROP_TICKS_GROUND;
      int tick = (int)sceneTicks(snapshot, phase, 0.0F);
      Vec3 at = HulkbusterSuitUp.path(start, snapshot.actionTarget(), HulkbusterWire.sceneYaw(HulkbusterView.sceneWord(snapshot)), air, tick + 1, drop);
      player.setPos(at.x, at.y, at.z);
      player.setDeltaMovement(Vec3.ZERO);
      player.fallDistance = 0.0F;
   }

   // ------------------------------------------------------------------ world render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      boolean drew = false;
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         HulkbusterLayer.Phase phase = snapshot == null ? HulkbusterLayer.Phase.NONE : HulkbusterView.phase(snapshot);
         if (!HulkbusterView.scene(phase) || snapshot.actionTarget() == null) {
            continue;
         }

         draw(player, snapshot, phase, partialTick, camera.getPosition(), stack, buffers, level);
         drew = true;
      }

      if (drew) {
         buffers.endBatch();
      }
   }

   private static void draw(Player player, HeroPublicSnapshot snapshot, HulkbusterLayer.Phase phase, float partialTick, Vec3 cameraPos, PoseStack stack,
      MultiBufferSource buffers, ClientLevel level) {
      Vec3 frame = snapshot.actionTarget();
      float yaw = HulkbusterWire.sceneYaw(HulkbusterView.sceneWord(snapshot));
      boolean air = HulkbusterView.sceneAir(snapshot);
      double tick = sceneTicks(snapshot, phase, partialTick);
      int fall = air ? POD_FALL_AIR : POD_FALL_GROUND;
      double drop = tick < fall ? POD_FALL_HEIGHT * Math.pow(1.0 - tick / fall, 2.0) : 0.0;
      int assemble = assemble(snapshot);
      double leave = assemble >= HulkbusterLayer.TORSO_TICK + 2 ? Math.min(1.0, (assemble + partialTick - HulkbusterLayer.TORSO_TICK - 2) / POD_LEAVE) : 0.0;
      Vec3 podAt = frame.add(0.0, drop + 40.0 * leave * leave, 0.0);
      int light = LevelRenderer.getLightColor(level, BlockPos.containing(frame.add(0.0, 1.5, 0.0)));

      // Capsule: falls, opens (pod "open", 0.9 s) once it arrived, then climbs away empty.
      BakedGeoModel pod = AnimCache.model(POD_GEO);
      if (pod != null && leave < 1.0) {
         Animation open = AnimCache.animation(POD_ANIMS, "open");
         if (open != null) {
            AnimationController.seek(open, pod, Mth.clamp((tick - fall) / 20.0, 0.0, open.lengthSeconds()));
         } else {
            pod.resetBones();
         }

         stack.pushPose();
         stack.translate(podAt.x - cameraPos.x, podAt.y - cameraPos.y, podAt.z - cameraPos.z);
         stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
         stack.scale(POD_SCALE, POD_SCALE, POD_SCALE);
         AnimRenderer.render(pod, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(MarkTextures.POD)), light, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F, null);
         AnimRenderer.render(pod, stack, null, buffers.getBuffer(RenderType.eyes(MarkTextures.POD_GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F, null);
         stack.popPose();
      }

      // The open frame rides in the capsule until the torso closes; then the player's body takes over.
      BakedGeoModel body = AnimCache.model(HulkbusterAssets.BODY);
      if (body == null || bodyClosed(snapshot, phase)) {
         return;
      }

      poseFrame(body, snapshot, partialTick);
      Vec3 at = frame.add(0.0, drop, 0.0);
      stack.pushPose();
      stack.translate(at.x - cameraPos.x, at.y - cameraPos.y, at.z - cameraPos.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      stack.scale(HulkbusterRenderer.SCALE, HulkbusterRenderer.SCALE, HulkbusterRenderer.SCALE);
      HulkbusterRenderer.drawModel(body, stack, buffers, light, OverlayTexture.NO_OVERLAY, ReinhardPoses.passes(false, true, false),
         Mth.clamp((float)(assemble - HulkbusterLayer.TORSO_TICK) / 10.0F, 0.15F, 1.0F), 0.0F, player.tickCount, 0);
      stack.popPose();
   }
}
