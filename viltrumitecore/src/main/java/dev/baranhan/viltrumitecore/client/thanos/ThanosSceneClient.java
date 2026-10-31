package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.Frame;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.Shot;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.V;
import dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

/**
 * The snap and universe scene on this client, driven by the server's scene clock
 * ({@link ThanosStateS2CPacket#SNAP}, every 5 ticks): a local clock that runs every client tick
 * and is pulled toward the server's (a jump past {@link #SYNC_JUMP} ticks re-syncs at once), the
 * cinematic camera of {@link ThanosSceneShots} (a {@link Marker} camera entity, cuts, field of
 * view, the snap's shake), the clean screen with its letterbox and Od Lagna's subtitles
 * ({@link ThanosSceneHud}), the world passes and figures ({@link ThanosSceneDraw}), the sound cues
 * ({@link ThanosSceneCues}) and the void's loop. Everything is dropped on the scene's end, the
 * server's {@link ThanosStateS2CPacket#NONE}, the 3-second watchdog of {@link ThanosClient}, a
 * logout or a level unload: the camera, the field of view, the input and the HUD are the player's
 * own again on the next frame.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosSceneClient {
   /** Share of the clock error corrected per tick; past {@link #SYNC_JUMP} the clock jumps. */
   static final float SYNC_RATE = 0.15F;
   static final float SYNC_JUMP = 12.0F;
   /** Od Lagna's distance in front of Thanos (near) or the player (far). */
   static final double OD_NEAR = 11.0;
   static final double OD_FAR = 9.0;
   private static final float[] STONE_PITCH = {1.0F, 1.12F, 0.94F, 1.26F, 0.89F, 1.06F};
   private static boolean running;
   /** The scene played to its end here: further scene packets of this run are ignored. */
   private static boolean done;
   private static float clock;
   private static float clockO;
   private static float cueClock;
   private static int thanosId = -1;
   @Nullable
   private static Frame frame;
   @Nullable
   private static Marker camera;
   @Nullable
   private static CameraType savedType;
   @Nullable
   private static Loop voidLoop;

   static {
      CinematicHud.hideWhile(ThanosSceneClient::running, ThanosSceneHud::render);
   }

   private ThanosSceneClient() {
   }

   // ---- state -------------------------------------------------------------------------------

   /** Every state packet (and null on a clear): starts the scene on the first SNAP packet. */
   public static void accept(int before, @Nullable ThanosStateS2CPacket p) {
      if (p == null || p.stage() != ThanosStateS2CPacket.SNAP) {
         done = false;
         if (running) {
            finish();
         }

         return;
      }

      if (p.thanosId() >= 0) {
         thanosId = p.thanosId();
      }

      if (!running && !done) {
         start(p);
      }
   }

   private static void start(ThanosStateS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }

      float t = Math.max(0.0F, ThanosClient.stageTime(0.0F));
      if (t >= ThanosSceneTimeline.LENGTH - 2) {
         done = true;
         return;
      }

      Vec3 origin = new Vec3(p.x(), p.y(), p.z());
      double near = Math.max(32.0, Math.min(120.0, mc.options.getEffectiveRenderDistance() * 16.0 - 24.0));
      boolean here = p.here() && origin.lengthSqr() > 0.0 && mc.player.position().distanceTo(origin) <= near;
      frame = buildFrame(mc, origin, p.yaw(), here);
      running = true;
      clock = t;
      clockO = t;
      cueClock = t < 1.0F ? -1.0F : t;
      long seed = Double.doubleToLongBits(origin.x * 31.0 + origin.z) ^ (long)p.thanosId() * 0x9E3779B97F4A7C15L;
      float reach = (float)Math.max(96.0, mc.options.getEffectiveRenderDistance() * 16.0 + 32.0);
      ThanosSceneDraw.start(frame, seed, reach);
   }

   /** Ends the scene on this client (idempotent): camera, input, loops and passes released. */
   static void finish() {
      running = false;
      Minecraft mc = Minecraft.getInstance();
      releaseCamera(mc);
      if (voidLoop != null) {
         voidLoop.cut();
         voidLoop = null;
      }

      ThanosSceneDraw.clear();
      frame = null;
   }

   public static boolean running() {
      return running;
   }

   /** Scene time at this frame (interpolated between client ticks), -1 when no scene runs. */
   public static float snapTime(float partial) {
      return running ? Mth.lerp(Mth.clamp(partial, 0.0F, 1.0F), clockO, clock) : -1.0F;
   }

   @Nullable
   static Frame frame() {
      return frame;
   }

   /** Does the scene draw this Thanos itself (after the dissolve pass) instead of his renderer? */
   public static boolean ownsThanos(Entity e) {
      return running && frame != null && frame.near() && e.getId() == thanosId;
   }

   static boolean cameraActive() {
      Minecraft mc = Minecraft.getInstance();
      return camera != null && mc.getCameraEntity() == camera;
   }

   // ---- frame -------------------------------------------------------------------------------

   static V v(Vec3 p) {
      return new V(p.x, p.y, p.z);
   }

   static Vec3 vec(V p) {
      return new Vec3(p.x(), p.y(), p.z());
   }

   private static Frame buildFrame(Minecraft mc, Vec3 origin, float yaw, boolean here) {
      V eye = v(mc.player.getEyePosition(1.0F));
      V look = v(mc.player.getViewVector(1.0F));
      if (!here) {
         V fwd = look.flat().normalize();
         if (fwd.length() < 0.5) {
            fwd = new V(0.0, 0.0, 1.0);
         }

         V left = new V(fwd.z(), 0.0, -fwd.x());
         V feet = v(mc.player.position());
         V od = feet.add(fwd.scale(OD_FAR));
         V chest = feet.up(1.0);
         return new Frame(false, feet, fwd, left, chest, chest, eye, od, eye, look);
      }

      double r = Math.toRadians(yaw);
      V fwd = new V(-Math.sin(r), 0.0, Math.cos(r));
      V left = new V(Math.cos(r), 0.0, Math.sin(r));
      V o = v(origin);
      V gauntlet = o.add(left.scale(1.1)).add(fwd.scale(0.5)).up(3.0);
      V stones = gauntlet.up(0.1);
      V head = o.up(3.45);
      BakedGeoModel model = AnimCache.model(ThanosView.GEO);
      if (model != null) {
         try {
            Matrix4f base = new Matrix4f().rotateY((float)Math.toRadians(180.0F - yaw));
            ThanosRenderer.poseSnap(model, 70.0F);
            GeoBone hand = model.getBone("left_hand");
            if (hand != null) {
               gauntlet = o.add(v(ThanosRenderer.point(hand, base)));
            }

            V sum = V.ZERO;
            int n = 0;
            for (String name : ThanosView.STONE_BONES) {
               GeoBone bone = model.getBone(name);
               if (bone != null) {
                  sum = sum.add(v(ThanosRenderer.point(bone, base)));
                  n++;
               }
            }

            if (n > 0) {
               stones = o.add(sum.scale(1.0 / n));
            }

            ThanosRenderer.poseSnap(model, 95.0F);
            GeoBone headBone = model.getBone("head");
            if (headBone != null) {
               head = o.add(v(ThanosRenderer.point(headBone, base))).up(0.33);
            }
         } finally {
            model.resetBones();
         }
      }

      V od = o.add(fwd.scale(OD_NEAR));
      return new Frame(true, o, fwd, left, gauntlet, stones, head, od, eye, look);
   }

   // ---- tick --------------------------------------------------------------------------------

   /** Client tick (from {@link ThanosClient}, unpaused ticks only). */
   static void tick(Minecraft mc) {
      if (!running) {
         return;
      }

      if (mc.level == null || mc.player == null || frame == null) {
         finish();
         return;
      }

      float before = clock;
      float target = ThanosClient.stageTime(0.0F);
      float next = clock + 1.0F;
      float error = target - next;
      boolean jump = Math.abs(error) > SYNC_JUMP;
      clockO = clock;
      clock = jump ? Math.max(0.0F, target) : Math.max(clock, next + error * SYNC_RATE);
      if (jump) {
         clockO = clock;
      }

      if (clock >= ThanosSceneTimeline.LENGTH) {
         done = true;
         finish();
         return;
      }

      cues(mc, Math.min(cueClock, before), clock);
      cueClock = clock;
      mc.getMusicManager().stopPlaying();
      updateCamera(mc, jump);
      ThanosSceneDraw.tick(mc, frame, clockO, clock);
   }

   private static void cues(Minecraft mc, float from, float to) {
      for (ThanosSceneCues.Cue c : ThanosSceneCues.between(from, to, SYNC_JUMP)) {
         switch (c.kind()) {
            case WORLD_STOP -> play(mc, ThanosSounds.WORLD_STOP.get(), 1.0F, 1.0F);
            case GAUNTLET_RAISE -> play(mc, ThanosSounds.GAUNTLET_RAISE.get(), 0.9F, 1.0F);
            case STONE_LIGHT -> play(mc, ThanosSounds.STONE_LIGHT.get(), 0.85F, STONE_PITCH[Mth.clamp(c.index(), 0, STONE_PITCH.length - 1)]);
            case FINGERS -> play(mc, ThanosSounds.FINGERS.get(), 0.9F, 1.0F);
            case SNAP -> play(mc, ThanosSounds.SNAP_CLICK.get(), 1.0F, 1.0F);
            case DISSOLVE -> play(mc, ThanosSounds.DISSOLVE.get(), 1.0F, 1.0F);
            case OD_LAGNA_ARRIVE -> play(mc, ThanosSounds.OD_LAGNA_ARRIVE.get(), 1.0F, 1.0F);
            case LINE -> play(mc, ThanosSounds.OD_LAGNA_LINE.get(), 0.75F, 1.0F - 0.015F * c.index());
            case RESTORE -> play(mc, ThanosSounds.RESTORE.get(), 1.0F, 1.0F);
            default -> {
            }
         }
      }

      boolean wanted = ThanosSceneCues.voidWanted(to);
      if (wanted && (voidLoop == null || voidLoop.isStopped())) {
         voidLoop = new Loop(ThanosSounds.VOID.get());
         mc.getSoundManager().play(voidLoop);
      }

      if (voidLoop != null) {
         voidLoop.target = wanted ? 0.8F : 0.0F;
         if (voidLoop.isStopped()) {
            voidLoop = null;
         }
      }
   }

   private static void play(Minecraft mc, SoundEvent sound, float volume, float pitch) {
      mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.MASTER, volume, pitch, RandomSource.create(), false, 0,
         SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
   }

   // ---- camera ------------------------------------------------------------------------------

   private static void updateCamera(Minecraft mc, boolean jump) {
      if (frame == null) {
         return;
      }

      if (camera == null || camera.level() != mc.level) {
         if (mc.getCameraEntity() != mc.player) {
            // another cinematic owns the view: the scene plays on without its camera
            return;
         }

         camera = new Marker(EntityType.MARKER, mc.level);
      }

      boolean snap = jump || ThanosSceneShots.cutBetween(frame.near(), clockO, clock);
      if (mc.getCameraEntity() != camera) {
         if (mc.getCameraEntity() != mc.player) {
            return;
         }

         savedType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(camera);
         place(mc, camera, ThanosSceneShots.at(frame, clockO), clockO, true);
      }

      place(mc, camera, ThanosSceneShots.at(frame, clock), clock, snap);
   }

   /** The world is solid for the camera while it stands (before the dissolve took it, after the restore). */
   static boolean clipsWorld(float t) {
      return t < ThanosSceneTimeline.DISSOLVE_START + 30 || t > ThanosSceneTimeline.SPEECH_END + 40;
   }

   private static void place(Minecraft mc, Marker cam, Shot shot, float t, boolean snap) {
      Vec3 pos = vec(shot.pos());
      Vec3 look = vec(shot.target());
      if (clipsWorld(t) && pos.distanceToSqr(look) > 1.0E-4 && t > ThanosSceneShots.LEAVE * 0.5F && t < ThanosSceneShots.BACK_END - 4.0F) {
         BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
         if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 dir = pos.subtract(look).normalize();
            pos = hit.getLocation().subtract(dir.scale(0.3));
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

   /** How close the scene camera may come to the hero's own body before it is no longer drawn. */
   static final double SELF_CLEARANCE = 0.45;

   /**
    * The way back ends in the hero's own eyes: the last ticks of it would fly through his head
    * (the scene camera draws his body). Once the camera is that close the body is not drawn, as
    * in first person.
    */
   static boolean hidesSelf(Vec3 cam, AABB body) {
      return body.inflate(SELF_CLEARANCE).contains(cam);
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (!running || camera == null || mc.getCameraEntity() != camera || event.getEntity() != mc.player) {
         return;
      }

      if (hidesSelf(mc.gameRenderer.getMainCamera().getPosition(), mc.player.getBoundingBox())) {
         event.setCanceled(true);
      }
   }

   static void releaseCamera(Minecraft mc) {
      if (camera != null && mc.getCameraEntity() == camera && mc.player != null) {
         mc.setCameraEntity(mc.player);
         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }
      }

      savedType = null;
      camera = null;
   }

   /** Camera shake 0..1: the snap, the world coming apart, the restore's arrival. */
   static float shake(float t) {
      float snap = t >= ThanosSceneTimeline.SNAP && t < ThanosSceneTimeline.SNAP + 24 ? (float)Math.pow(1.0F - (t - ThanosSceneTimeline.SNAP) / 24.0F, 2.0) : 0.0F;
      float rumble = t >= ThanosSceneTimeline.DISSOLVE_START && t < ThanosSceneTimeline.DISSOLVE_END
         ? 0.16F * (float)Math.sin(Math.PI * (t - ThanosSceneTimeline.DISSOLVE_START) / (ThanosSceneTimeline.DISSOLVE_END - ThanosSceneTimeline.DISSOLVE_START))
         : 0.0F;
      float restore = t >= ThanosSceneTimeline.THANOS_GONE && t < ThanosSceneTimeline.THANOS_GONE + 30
         ? 0.3F * (1.0F - (t - ThanosSceneTimeline.THANOS_GONE) / 30.0F)
         : 0.0F;
      return Math.max(snap, Math.max(rumble, restore));
   }

   @SubscribeEvent
   public static void onFov(ViewportEvent.ComputeFov event) {
      if (!running || frame == null || !cameraActive() || !event.usedConfiguredFov()) {
         return;
      }

      float t = snapTime((float)event.getPartialTick());
      float w = ThanosSceneShots.weight(t);
      Shot s = ThanosSceneShots.at(frame, t);
      event.setFOV(Mth.lerp(w, event.getFOV(), s.fov()));
   }

   @SubscribeEvent
   public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
      if (!running || !cameraActive()) {
         return;
      }

      float t = snapTime((float)event.getPartialTick());
      float k = shake(t);
      if (k <= 0.0F) {
         return;
      }

      event.setYaw(event.getYaw() + k * 0.9F * (float)Math.sin(t * 2.7F));
      event.setPitch(event.getPitch() + k * 0.7F * (float)Math.sin(t * 3.4F + 1.3F));
      event.setRoll(event.getRoll() + k * 1.4F * (float)Math.sin(t * 1.9F + 0.4F));
   }

   /**
    * Screen grade of the scene for the dash_impact post pass: flash, shock ring radius, the
    * ring's centre on screen (texture coordinates), golden light, the void's vignette.
    */
   public static float[] screen(float partial) {
      float[] out = {0.0F, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F};
      if (!running || frame == null) {
         return out;
      }

      float t = snapTime(partial);
      float s = ThanosSceneTimeline.SNAP;
      if (t >= s - 0.5F && t < s + 10.0F) {
         out[0] = t < s ? (t - (s - 0.5F)) * 2.0F : (float)Math.pow(1.0F - (t - s) / 10.0F, 2.0);
      } else if (t >= ThanosSceneTimeline.THANOS_GONE && t < ThanosSceneTimeline.THANOS_GONE + 20) {
         out[0] = 0.35F * (1.0F - (t - ThanosSceneTimeline.THANOS_GONE) / 20.0F);
      }

      if (t >= s && t < s + 25.0F) {
         out[1] = Math.max(0.001F, (t - s) / 25.0F * 1.5F);
      }

      if (frame.near()) {
         Minecraft mc = Minecraft.getInstance();
         int w = mc.getWindow().getGuiScaledWidth();
         int h = mc.getWindow().getGuiScaledHeight();
         ScreenProjector.Point p = ScreenProjector.project(vec(frame.gauntlet()), w, h);
         if (p != null && !p.behind() && w > 0 && h > 0) {
            out[2] = p.x() / w;
            out[3] = 1.0F - p.y() / h;
         }
      }

      out[4] = 0.6F * ThanosSceneTimeline.gold(t);
      float dissolve = t < ThanosSceneTimeline.SPEECH_END ? ThanosSceneTimeline.dissolve(t) : 1.0F - ThanosSceneTimeline.restore(t);
      out[5] = 0.7F * ThanosSceneTimeline.smooth((dissolve - 0.55F) / 0.45F);
      return out;
   }

   // ---- input and view ----------------------------------------------------------------------

   @SubscribeEvent
   public static void onMovement(MovementInputUpdateEvent event) {
      if (!running) {
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

   @SubscribeEvent
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (running) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void onHighlight(RenderHighlightEvent.Block event) {
      if (running) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (!running || frame == null || ShaderCompat.isShadowPass()) {
         return;
      }

      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         renderOwnModel(event);
      } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         ThanosSceneDraw.render(event, frame, snapTime(event.getPartialTick()));
      }
   }

   /** The level skips the local player while another camera entity is active: drawn once the camera left the head. */
   private static void renderOwnModel(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!cameraActive() || mc.player == null) {
         return;
      }

      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      AbstractClientPlayer p = mc.player;
      if (cam.distanceToSqr(p.getEyePosition(partial)) < 0.8 * 0.8) {
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

   // ---- cleanup -----------------------------------------------------------------------------

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      done = false;
      finish();
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide() && running) {
         finish();
      }
   }

   /** The void's loop: fades in and out on the master channel, never outlives the scene. */
   static final class Loop extends AbstractTickableSoundInstance {
      float target = 0.8F;
      private float level;

      Loop(SoundEvent event) {
         super(event, SoundSource.MASTER, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
         this.volume = 0.001F;
      }

      @Override
      public void tick() {
         if (!running) {
            this.stop();
            return;
         }

         float step = 1.0F / 30.0F;
         this.level += Math.max(-step, Math.min(step, this.target - this.level));
         this.volume = Math.max(0.001F, this.level);
         if (this.level <= 0.0F && this.target <= 0.0F) {
            this.stop();
         }
      }

      void cut() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
