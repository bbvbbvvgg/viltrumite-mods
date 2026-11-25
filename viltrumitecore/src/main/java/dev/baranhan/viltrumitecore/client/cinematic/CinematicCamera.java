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
import net.minecraftforge.client.event.ViewportEvent;
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
   /** The JSON scene being played, or {@code null} when the built-in orbit is (see {@link #orbit}). */
   @Nullable
   private static CinematicScene scene;
   @Nullable
   private static CinematicPath orbit;
   @Nullable
   private static CameraType savedType;
   private static Vec3 focus = Vec3.ZERO;
   private static float frameYaw;
   private static float duration;
   private static float clock;
   private static boolean reduced;

   private CinematicCamera() {
   }

   /** Registers the client listeners (called from the client setup, like the other hero clients). */
   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onLoggingOut);
      MinecraftForge.EVENT_BUS.addListener(CinematicCamera::onFov);
   }

   /** Is a shot holding the view right now (the HUD layer and the skip key ask this). */
   public static boolean active() {
      return scene != null || orbit != null;
   }

   /** Does the running scene letterbox the screen (the built-in orbit does not). */
   public static boolean letterbox() {
      return scene != null && scene.letterbox();
   }

   /** Scene-clock ticks per client tick, 1 when nothing is slowing down (the sound bed ducks to it). */
   public static float clockRate() {
      return active() && scene != null ? scene.clockRate(reduced) : 1.0F;
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
      Entity focusEntity = mc.level.getEntity(packet.focusId);
      reduced = CinematicSettings.get().reducedMotion();
      focus = focusEntity != null ? focusEntity.position() : mc.player.position();
      // The offsets of a scene are taken in the focus's frame, so every viewer of the same scene sees
      // the same shot and a late-joining watcher lands on it too.
      frameYaw = focusEntity != null ? focusEntity.getYRot() : mc.player.getYRot();
      CinematicScene json = CinematicScenes.get(packet.sceneId);
      if (json != null) {
         scene = json;
         orbit = null;
         duration = json.duration(reduced);
      } else {
         scene = null;
         orbit = CinematicPath.around(focus, reduced);
         duration = orbit.ticks();
      }
      clock = 0.0F;
      start(mc);
   }

   // ---------------------------------------------------------------- lifecycle

   private static void start(Minecraft mc) {
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
      place(mc, camera, positionAt(0.0F), lookAt(0.0F), true);
   }

   /** Camera position of the shot at {@code progress} 0..1. */
   private static Vec3 positionAt(float progress) {
      if (scene != null) {
         return CameraPath.toWorld(scene.path().sample(progress, scene.amplitude(reduced)).offset(), focus, frameYaw);
      }
      return orbit.position(progress * orbit.ticks());
   }

   /** What the shot looks at, at {@code progress} 0..1. */
   private static Vec3 lookAt(float progress) {
      if (scene != null) {
         return CameraPath.toWorld(scene.path().sample(progress, scene.amplitude(reduced)).look(), focus, frameYaw);
      }
      return orbit.look();
   }

   private static float progress() {
      return duration <= 0.0F ? 1.0F : (float)CameraPath.clamp01(clock / duration);
   }

   /** The lens of the running scene, when it names one (a scene without fov keeps the player's). */
   static void onFov(ViewportEvent.ComputeFov event) {
      if (scene == null) {
         return;
      }
      float fov = scene.path().sample(progress(), scene.amplitude(reduced)).fov();
      if (fov > 0.0F) {
         event.setFOV(fov);
      }
   }

   /**
    * Gives the camera back to the player and drops all scene state (the try/finally of the scene:
    * it runs on every exit path, so a player can never stay stuck in another camera). Safe twice.
    */
   public static void release(Minecraft mc) {
      scene = null;
      orbit = null;
      clock = 0.0F;
      duration = 0.0F;
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
      if (!active()) {
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
      // The scene clock runs at the scene's own rate: a slow-mo scene lasts longer in client ticks
      // while the world and the server keep their own time.
      clock += 1.0F;
      if (clock >= duration) {
         release(mc);
         return;
      }
      place(mc, camera, positionAt(progress()), lookAt(progress()), false);
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
