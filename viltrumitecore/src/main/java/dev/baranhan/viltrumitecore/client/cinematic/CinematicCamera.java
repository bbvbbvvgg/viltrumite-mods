package dev.baranhan.viltrumitecore.client.cinematic;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.network.packet.CinematicS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Client driver of the cinematic camera (wave1-22). It takes the view through a client-only
 * {@link Marker} (the same trick as {@code JinwooUltScene}), plays one built-in one-shot path and
 * always gives the camera back: on the end of the path, the skip key (Backspace), death, a
 * dimension change, opening a menu or a disconnect. {@link CinematicVisibility} decides whether the
 * scene may run at all — comfort setting, opt-in and "never fight another hero's own camera".
 * Client thread only.
 */
public final class CinematicCamera {
   @Nullable
   private static Marker camera;
   @Nullable
   private static CinematicPath path;
   @Nullable
   private static CameraType savedType;
   private static long startTick;

   private CinematicCamera() {
   }

   /** Registers the client listeners (called from the client setup, like the other hero clients). */
   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onLoggingOut);
   }

   /** Packet entry: start the scene, unless the player's comfort settings or another camera say no. */
   public static void play(CinematicS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.screen != null) {
         return;
      }
      boolean isCaster = packet.casterId == mc.player.getId();
      boolean otherOwner = otherCameraOwnerActive(mc);
      if (!CinematicVisibility.shouldPlay(isCaster, CinematicSettings.get().watchOthers(),
            CinematicSettings.get().cinematicCameraEnabled(), otherOwner)) {
         return;
      }
      Entity focus = mc.level.getEntity(packet.focusId);
      Vec3 centre = focus != null ? focus.position() : mc.player.position();
      start(mc, CinematicPath.around(centre, CinematicSettings.get().reducedMotion()));
   }

   // ---------------------------------------------------------------- lifecycle

   private static void start(Minecraft mc, CinematicPath scene) {
      // The player's own camera type is captured once per scene: a second packet mid-scene must not
      // overwrite it with the FIRST_PERSON we forced, or the return would leave the player in first person.
      boolean alreadyHolding = camera != null && mc.getCameraEntity() == camera;
      if (CinematicVisibility.capturesCameraType(alreadyHolding, savedType != null)) {
         savedType = mc.options.getCameraType();
      }
      if (camera == null || camera.level() != mc.level) {
         camera = new Marker(EntityType.MARKER, mc.level);
      }
      mc.options.setCameraType(CameraType.FIRST_PERSON);
      mc.setCameraEntity(camera);
      path = scene;
      startTick = mc.level.getGameTime();
      place(mc, camera, scene.position(0.0F), scene.look(), true);
   }

   /**
    * Gives the camera back to the player and drops all scene state (the try/finally of the scene:
    * it runs on every exit path, so a player can never stay stuck in another camera). Safe twice.
    */
   public static void release(Minecraft mc) {
      path = null;
      // The camera entity goes back to the player only while it is still ours: if another owner
      // replaced it, that camera is not ours to touch.
      if (camera != null && mc.getCameraEntity() == camera && mc.player != null) {
         mc.setCameraEntity(mc.player);
      }
      // A non-null savedType is the flag "we forced FIRST_PERSON when we took the view", so the
      // player's own type always comes back — even when another owner has meanwhile replaced the
      // camera entity. Otherwise the player would silently stay in first person for good.
      if (savedType != null) {
         mc.options.setCameraType(savedType);
      }
      savedType = null;
      camera = null;
   }

   static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (path == null) {
         // No scene: the skip key must not build up a queue that would kill the next scene at once.
         drainSkipKey();
         return;
      }
      if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.screen != null
            || camera == null || camera.level() != mc.level || mc.getCameraEntity() != camera) {
         release(mc);
         return;
      }
      // A skip eats the whole queue, so repeated presses never leak into the next scene.
      if (drainSkipKey()) {
         release(mc);
         return;
      }
      float t = (float)(mc.level.getGameTime() - startTick);
      if (t >= path.ticks()) {
         release(mc);
         return;
      }
      place(mc, camera, path.position(t), path.look(), false);
   }

   /** Drains every queued click of the skip key; true when there was at least one. */
   private static boolean drainSkipKey() {
      KeyMapping skip = AbilityInputManager.skipCinematicKey;
      if (skip == null) {
         return false;
      }
      boolean any = false;
      while (skip.consumeClick()) {
         any = true;
      }
      return any;
   }

   static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         release(Minecraft.getInstance());
      }
   }

   static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      release(Minecraft.getInstance());
   }

   /**
    * True when another hero's own camera already holds the view. Our own marker is never counted as
    * "another" owner; everything else is {@link CameraOwners}' job (the single place that knows the
    * hero cameras). See it for which owners exist and how each is detected.
    */
   private static boolean otherCameraOwnerActive(Minecraft mc) {
      if (camera != null && mc.getCameraEntity() == camera) {
         return false;
      }
      return CameraOwners.anyActive(mc);
   }

   // ---------------------------------------------------------------- placement

   private static void place(Minecraft mc, Marker cam, Vec3 pos, Vec3 look, boolean snap) {
      BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
      if (hit.getType() == HitResult.Type.BLOCK) {
         Vec3 dir = pos.subtract(look).normalize();
         pos = hit.getLocation().subtract(dir.scale(0.3));
      }
      Vec3 d = look.subtract(pos);
      float yaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
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
}
