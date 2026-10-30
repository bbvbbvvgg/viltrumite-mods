package dev.baranhan.viltrumitecore.client.wish;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes;
import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import dev.baranhan.viltrumitecore.hero.wish.WishSounds;
import dev.baranhan.viltrumitecore.hero.wish.WishTimeline;
import dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Client side of the wish ritual (spec: the 2-4 s scene). Every client in range draws the scene of every
 * wisher from the server's beats (WishSceneS2CPacket); only the wisher gets the cinematic camera, the
 * input lock, the letterbox and «твоё желание услышано.». Nothing here changes game state: the hero
 * itself changes on the server at {@link WishTimeline#SWAP}.
 */
public final class WishSceneClient {
   static final Map<Integer, WishScene> SCENES = new HashMap<>();
   private static final double RANGE = 96;
   private static final double TINT_RANGE = 40;
   @Nullable
   private static Marker camera;
   @Nullable
   private static CameraType savedType;
   @Nullable
   private static WishScene cameraScene;
   private static float weight;
   private static float weightO;
   /** The wisher's title clock (ticks since the start), -1 when no title is due. */
   private static int titleAge = -1;
   private static int titleAgeO = -1;

   private WishSceneClient() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onMovement);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onInteraction);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onRenderOverlay);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onFov);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(WishSceneClient::onLoggingOut);
   }

   // ---------------------------------------------------------------- beats from the server

   public static void handle(WishSceneS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      boolean own = mc.player != null && packet.entityId() == mc.player.getId();
      switch (packet.phase()) {
         case WishSceneS2CPacket.START -> start(mc, packet, own);
         case WishSceneS2CPacket.SWAP -> {
            WishScene scene = SCENES.get(packet.entityId());
            if (scene != null && !scene.swapped) {
               scene.ageO = Math.min(scene.ageO, WishTimeline.SWAP - 1);
               scene.age = WishTimeline.SWAP;
               scene.swapped = true;
               WishSceneFx.onSwap(mc, scene);
            }
         }
         case WishSceneS2CPacket.END -> finish(packet.entityId(), true);
         default -> finish(packet.entityId(), false);
      }
   }

   private static void start(Minecraft mc, WishSceneS2CPacket packet, boolean own) {
      int elapsed = Mth.clamp(packet.elapsed(), 0, WishTimeline.LENGTH);
      WishScene old = SCENES.remove(packet.entityId());
      if (old != null) {
         drop(old);
      }
      WishScene scene = new WishScene(packet.entityId(), OdLagnaWishes.byId(packet.wishId()), packet.entityId() * 31L + packet.wishId().hashCode(), own,
         elapsed);
      scene.swapped = elapsed >= WishTimeline.SWAP;
      locate(mc, scene);
      SCENES.put(scene.entityId, scene);
      WishSceneView.set(scene.entityId, true);
      if (own) {
         if (mc.screen instanceof WishScreen screen) {
            screen.closeNow();
         }
         titleAge = titleAgeO = elapsed;
      }
      if (elapsed == 0) {
         WishSceneFx.play(mc, scene, WishSounds.CALL, 1.0F, 1.0F);
      }
   }

   private static void finish(int entityId, boolean completed) {
      WishScene scene = SCENES.remove(entityId);
      if (scene == null) {
         return;
      }
      drop(scene);
      if (scene.own && !completed) {
         titleAge = titleAgeO = -1;
      }
   }

   private static void drop(WishScene scene) {
      WishSceneView.set(scene.entityId, false);
      SkyTint.INSTANCE.remove(scene);
      if (cameraScene == scene) {
         cameraScene = null;
      }
   }

   private static void locate(Minecraft mc, WishScene scene) {
      if (scene.origin == null && mc.level != null) {
         Entity entity = mc.level.getEntity(scene.entityId);
         if (entity != null) {
            scene.origin = entity.position();
            scene.yaw = entity instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : entity.getYRot();
         }
      }
   }

   // ---------------------------------------------------------------- ticking

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         if (!SCENES.isEmpty() || camera != null) {
            clear();
         }
         return;
      }
      WishScreens.tick(mc);
      if (mc.isPaused()) {
         return;
      }
      var it = SCENES.values().iterator();
      while (it.hasNext()) {
         WishScene scene = it.next();
         scene.ageO = scene.age;
         scene.age++;
         locate(mc, scene);
         if (scene.age > WishTimeline.LENGTH + 40) {
            // the END beat never came (the wisher left our tracking range): stop quietly
            it.remove();
            drop(scene);
            continue;
         }
         WishSceneFx.tick(mc, scene);
         tint(mc, scene);
      }
      titleAgeO = titleAge;
      if (titleAge >= 0 && ++titleAge > WishTimeline.LENGTH + WishTimeline.TITLE_TAIL) {
         titleAge = titleAgeO = -1;
      }
      updateCamera(mc);
   }

   private static void tint(Minecraft mc, WishScene scene) {
      if (scene.style.darken <= 0 && scene.style.tintR >= 1 && scene.style.tintG >= 1 && scene.style.tintB >= 1 || scene.origin == null || mc.player == null) {
         return;
      }
      if (scene.origin.distanceTo(mc.player.position()) > TINT_RANGE) {
         SkyTint.INSTANCE.remove(scene);
         return;
      }
      WishStyle s = scene.style;
      SkyTint.INSTANCE.push(scene, 112, () -> SkyTint.mix(SkyTint.NONE, new SkyTint.Tint(s.tintR, s.tintG, s.tintB, s.darken, 1.0F),
         WishTimeline.energy(scene.age)));
   }

   // ---------------------------------------------------------------- the wisher's camera

   @Nullable
   static WishScene ownScene() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? null : SCENES.get(mc.player.getId());
   }

   static boolean cameraActive() {
      Minecraft mc = Minecraft.getInstance();
      return camera != null && mc.getCameraEntity() == camera;
   }

   private static void updateCamera(Minecraft mc) {
      WishScene scene = ownScene();
      weightO = weight;
      if (scene == null || scene.origin == null || mc.player == null || scene.age >= WishTimeline.LENGTH) {
         weight = 0;
         releaseCamera(mc);
         return;
      }
      weight = WishTimeline.cameraWeight(scene.age);
      if (camera == null || camera.level() != mc.level) {
         if (mc.getCameraEntity() != mc.player) {
            // another cinematic owns the view (a hero ult, spectating): the scene plays without our camera
            return;
         }
         camera = new Marker(EntityType.MARKER, mc.level);
      }
      if (mc.getCameraEntity() != camera) {
         if (mc.getCameraEntity() != mc.player) {
            return;
         }
         savedType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(camera);
         cameraScene = scene;
         place(mc, camera, shot(mc, scene, scene.age), true);
      }
      place(mc, camera, shot(mc, scene, scene.age + 1), false);
   }

   private static Vec3[] shot(Minecraft mc, WishScene scene, float t) {
      return WishCamera.blended(scene.origin, scene.yaw, t, mc.player.getEyePosition(), mc.player.getViewVector(1.0F));
   }

   private static void place(Minecraft mc, Marker cam, Vec3[] shot, boolean snap) {
      Vec3 pos = shot[0];
      Vec3 look = shot[1];
      if (pos.distanceToSqr(look) > 1.0E-4) {
         BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
         if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 dir = pos.subtract(look).normalize();
            pos = hit.getLocation().subtract(dir.scale(0.25));
         }
      }
      Vec3 d = look.subtract(pos);
      float yaw = d.horizontalDistanceSqr() < 1.0E-6 ? cam.getYRot() : (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI)));
      if (snap) {
         cam.setPos(pos);
         cam.xo = pos.x;
         cam.yo = pos.y;
         cam.zo = pos.z;
         cam.setYRot(yaw);
         cam.setXRot(pitch);
         cam.yRotO = yaw;
         cam.xRotO = pitch;
         return;
      }
      cam.xo = cam.getX();
      cam.yo = cam.getY();
      cam.zo = cam.getZ();
      cam.yRotO = cam.getYRot();
      cam.xRotO = cam.getXRot();
      cam.setPos(pos);
      cam.setYRot(cam.yRotO + Mth.wrapDegrees(yaw - cam.yRotO));
      cam.setXRot(pitch);
   }

   static void releaseCamera(Minecraft mc) {
      if (camera != null && mc.getCameraEntity() == camera) {
         mc.setCameraEntity(mc.player);
         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }
      }
      savedType = null;
      camera = null;
      cameraScene = null;
   }

   // ---------------------------------------------------------------- input lock of the wisher

   public static void onMovement(MovementInputUpdateEvent event) {
      if (ownScene() == null) {
         return;
      }
      var input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (ownScene() != null) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   // ---------------------------------------------------------------- render

   public static void onRenderHand(RenderHandEvent event) {
      if (cameraActive()) {
         event.setCanceled(true);
      }
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (SCENES.isEmpty() || ShaderCompat.isShadowPass()) {
         return;
      }
      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         renderOwnModel(event);
      } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
         renderFx(event);
      }
   }

   /** The level skips the local player while another camera entity is active: draw it once the camera left the head. */
   private static void renderOwnModel(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!cameraActive() || mc.player == null) {
         return;
      }
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      AbstractClientPlayer p = mc.player;
      if (cam.distanceToSqr(p.getEyePosition(partial)) < 0.6 * 0.6) {
         return;
      }
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      Vec3 at = p.getPosition(partial);
      var dispatcher = mc.getEntityRenderDispatcher();
      dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, stack, buffers,
         dispatcher.getPackedLightCoords(p, partial));
      buffers.endBatch();
   }

   private static void renderFx(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      float partial = event.getPartialTick();
      PoseStack mv = RenderSystem.getModelViewStack();
      mv.pushPose();
      mv.setIdentity();
      PixelVfx.rotateCamera(mv, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buffer = tess.getBuilder();
      try {
         RenderSystem.defaultBlendFunc();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (WishScene scene : SCENES.values()) {
            Vec3 c = center(level, scene, partial);
            if (c != null && c.distanceToSqr(cam) < RANGE * RANGE) {
               WishSceneFx.render(buffer, cam, camera, level, scene, c, scene.time(partial), false);
            }
         }
         tess.end();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (WishScene scene : SCENES.values()) {
            Vec3 c = center(level, scene, partial);
            if (c != null && c.distanceToSqr(cam) < RANGE * RANGE) {
               WishSceneFx.render(buffer, cam, camera, level, scene, c, scene.time(partial), true);
            }
         }
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         mv.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   @Nullable
   private static Vec3 center(ClientLevel level, WishScene scene, float partial) {
      Entity entity = level.getEntity(scene.entityId);
      return entity != null ? entity.getPosition(partial) : scene.origin;
   }

   // ---------------------------------------------------------------- HUD: letterbox and the title

   public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
      if (cameraActive() && weight > 0.35F && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
         event.setCanceled(true);
      }
   }

   public static void onRenderGui(RenderGuiEvent.Post event) {
      float partial = event.getPartialTick();
      var g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float k = cameraActive() ? Mth.lerp(partial, weightO, weight) : 0;
      int bar = Math.round(h * 0.1F * k);
      if (bar > 0) {
         g.fill(0, 0, w, bar, 0xFF000000);
         g.fill(0, h - bar, w, h, 0xFF000000);
      }
      if (titleAge < 0) {
         return;
      }
      float t = titleAgeO + (titleAge - titleAgeO) * partial;
      float alpha = WishTimeline.titleAlpha(t);
      if (alpha * 255 < 6) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String full = Component.translatable("wish.viltrumitecore.granted").getString();
      // the words appear one by one, like a whisper
      float reveal = Mth.clamp((t - (WishTimeline.FULFIL_END - 2)) / 14.0F, 0, 1);
      int chars = full.codePointCount(0, full.length());
      String shown = full.substring(0, full.offsetByCodePoints(0, Math.max(1, Math.round(chars * reveal))));
      float scale = h < 280 ? 1.25F : 1.6F;
      int fullWidth = mc.font.width(full);
      float y = h * 0.7F;
      g.pose().pushPose();
      g.pose().translate(w / 2.0F, y, 0);
      g.pose().scale(scale, scale, 1);
      int glow = WishScreen.argb(80 * alpha, WishStyle.GOLD);
      for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
         g.drawString(mc.font, shown, -fullWidth / 2 + o[0], o[1], glow, false);
      }
      g.drawString(mc.font, shown, -fullWidth / 2, 0, WishScreen.argb(255 * alpha, 0xFFF1D2), false);
      g.pose().popPose();
   }

   public static void onFov(ViewportEvent.ComputeFov event) {
      if (cameraActive()) {
         float k = (float)Mth.lerp(event.getPartialTick(), weightO, weight);
         event.setFOV(Mth.lerp(k, event.getFOV(), 62.0));
      }
   }

   // ---------------------------------------------------------------- cleanup

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         clear();
      }
   }

   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      WishScreens.clear();
      clear();
   }

   static void clear() {
      for (WishScene scene : SCENES.values()) {
         SkyTint.INSTANCE.remove(scene);
      }
      SCENES.clear();
      WishSceneView.clear();
      releaseCamera(Minecraft.getInstance());
      weight = weightO = 0;
      titleAge = titleAgeO = -1;
   }

   static SoundEvent heroSound(String key) {
      return SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", key));
   }

   static void flashOwn(float strength, int ticks) {
      FlashOverlay.flash(strength, ticks);
   }
}
