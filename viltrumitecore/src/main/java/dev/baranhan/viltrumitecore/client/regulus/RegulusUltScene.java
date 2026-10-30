package dev.baranhan.viltrumitecore.client.regulus;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * «Королевство одного» on the client (spec §5.8): two directed cameras through a client-only marker
 * entity, one for the caster and one for the judged target, cut by phase (ACCUSE, SCOOP, SENTENCE,
 * FLICK, RESOLVE) with {@link RegulusRules#ULT_BLEND}-tick blends at every cut; letterbox for both
 * and a gentle lean for observers within {@link #OBSERVER_RANGE}. The scene clock is the synced move
 * clock, so DIO's stopped time pauses the film with the world. Any loss (ULT_END, the snapshot
 * leaving ULT, death, level change, timeout) releases the camera at once and restores the camera
 * type. Client only; nothing here touches the world.
 */
public final class RegulusUltScene {
   static final double OBSERVER_RANGE = 40.0;
   /** The target gets its own view back right after the flick lands. */
   static final int TARGET_RELEASE = RegulusRules.ULT_FLICK_CONTACT + 2;
   /** The caster's camera eases back into his eyes over the last ticks. */
   static final int RETURN = 12;
   private static final Map<Integer, Scene> SCENES = new HashMap<>();
   /** Casters whose scene just ended: a stale snapshot must not reopen it. */
   private static final Map<Integer, Long> ENDED = new HashMap<>();
   @Nullable
   private static Marker camera;
   @Nullable
   private static CameraType savedType;
   @Nullable
   private static ClientLevel cameraLevel;
   private static float letterbox;
   private static float letterboxO;
   private static float lean;
   private static float kick;

   enum Role {
      CASTER,
      TARGET,
      OBSERVER
   }

   static final class Scene {
      final int caster;
      int target;
      Vec3 anchor;
      double ticks;
      long seen;
      long lastWave = Long.MIN_VALUE;

      Scene(int caster, int target, Vec3 anchor) {
         this.caster = caster;
         this.target = target;
         this.anchor = anchor;
      }
   }

   private RegulusUltScene() {
   }

   public static boolean cameraActive() {
      Minecraft mc = Minecraft.getInstance();
      return camera != null && mc.getCameraEntity() == camera;
   }

   // ---------------------------------------------------------------- packet hooks

   static void start(int caster, int target, Vec3 anchor) {
      Scene scene = new Scene(caster, target, anchor);
      Minecraft mc = Minecraft.getInstance();
      scene.seen = mc.level == null ? 0L : mc.level.getGameTime();
      SCENES.put(caster, scene);
   }

   static void wave(int caster, int index) {
      Scene scene = SCENES.get(caster);
      Minecraft mc = Minecraft.getInstance();
      if (scene != null && mc.level != null) {
         scene.lastWave = mc.level.getGameTime();
         kick = Math.max(kick, 1.0F);
      }
   }

   static void flick(int caster) {
      if (SCENES.containsKey(caster)) {
         kick = Math.max(kick, 2.0F);
      }
   }

   static void end(int caster) {
      SCENES.remove(caster);
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         ENDED.put(caster, mc.level.getGameTime());
      }

      if (SCENES.isEmpty()) {
         releaseCamera(Minecraft.getInstance());
      }
   }

   // ---------------------------------------------------------------- tick

   static void tick(Minecraft mc) {
      ClientLevel level = mc.level;
      Player me = mc.player;
      if (level == null || me == null) {
         clear();
         return;
      }

      if (cameraLevel != null && cameraLevel != level) {
         // dimension change or reconnect: never keep a camera of another level
         clear();
      }

      long now = level.getGameTime();
      ENDED.values().removeIf(at -> now - at > 10L || now < at);
      for (Player p : level.players()) {
         HeroPublicSnapshot s = RegulusView.of(p);
         if (s == null || RegulusView.move(s) != RegulusMove.ULT) {
            continue;
         }

         Scene scene = SCENES.get(p.getId());
         if (scene == null && ENDED.containsKey(p.getId())) {
            continue;
         }

         int target = RegulusView.ultTargetId(s);
         Vec3 anchor = s.actionTarget();
         if (scene == null) {
            // joined mid-scene or missed the start packet: the snapshot is enough
            scene = new Scene(p.getId(), target, anchor == null ? p.position() : anchor);
            SCENES.put(p.getId(), scene);
         }

         if (target >= 0) {
            scene.target = target;
         }

         if (anchor != null) {
            scene.anchor = anchor;
         }

         scene.ticks = RegulusView.moveTicks(p, s, 0.0F);
         scene.seen = now;
      }

      // a scene whose caster left ULT (or left our sight) ends here even without ULT_END
      SCENES.values().removeIf(sc -> now - sc.seen > 5L || sc.ticks > RegulusRules.ULT_LENGTH + 5);
      Scene active = null;
      Role role = Role.OBSERVER;
      double best = OBSERVER_RANGE * OBSERVER_RANGE;
      for (Scene sc : SCENES.values()) {
         Role r = sc.caster == me.getId() ? Role.CASTER : sc.target == me.getId() ? Role.TARGET : Role.OBSERVER;
         if (r.ordinal() < role.ordinal() || active == null && r != Role.OBSERVER) {
            active = sc;
            role = r;
         } else if (r == Role.OBSERVER && role == Role.OBSERVER) {
            double d = me.position().distanceToSqr(sc.anchor);
            if (d < best) {
               best = d;
               active = sc;
            }
         }
      }

      letterboxO = letterbox;
      letterbox += ((active != null ? 1.0F : 0.0F) - letterbox) * 0.25F;
      kick *= 0.8F;
      lean = active == null || role != Role.OBSERVER ? 0.0F : envelope(active.ticks);
      Entity casterEntity = active == null ? null : level.getEntity(active.caster);
      boolean wantCamera = active != null && casterEntity != null && me.isAlive() && !me.isRemoved()
         && (role == Role.CASTER || role == Role.TARGET && active.ticks < TARGET_RELEASE);
      if (wantCamera) {
         updateCamera(mc, active, role, casterEntity);
      } else {
         releaseCamera(mc);
      }
   }

   /** Pure: observer lean envelope over the scene. */
   static float envelope(double t) {
      if (t < 0.0 || t > RegulusRules.ULT_LENGTH) {
         return 0.0F;
      }

      return (float) (Math.min(1.0, t / 10.0) * Math.min(1.0, (RegulusRules.ULT_LENGTH - t) / 10.0));
   }

   // ---------------------------------------------------------------- camera

   private static void updateCamera(Minecraft mc, Scene scene, Role role, Entity caster) {
      Entity target = mc.level.getEntity(scene.target);
      Vec3 c = caster.position();
      Vec3 t = target == null ? scene.anchor : target.position();
      Player me = mc.player;
      Vec3 eye = me.getEyePosition();
      Vec3 look = eye.add(me.getLookAngle().scale(6.0));
      if (camera == null || camera.level() != mc.level) {
         camera = new Marker(EntityType.MARKER, mc.level);
         cameraLevel = mc.level;
      }

      double tick = scene.ticks + 1.0;
      Vec3[] shot = role == Role.CASTER ? casterShot(c, t, tick, eye, look) : targetShot(c, t, tick);
      if (mc.getCameraEntity() != camera) {
         savedType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(camera);
         Vec3[] first = role == Role.CASTER ? casterShot(c, t, scene.ticks, eye, look) : targetShot(c, t, scene.ticks);
         place(mc, camera, first[0], first[1], true);
      }

      place(mc, camera, shot[0], shot[1], false);
   }

   /** Pure: the caster's shot [position, look-at] at scene tick {@code t}, blended at the cuts. */
   static Vec3[] casterShot(Vec3 c, Vec3 target, double t, Vec3 eye, Vec3 eyeLook) {
      Vec3[] cur = blended(c, target, t, true);
      double back = t - (RegulusRules.ULT_LENGTH - RETURN);
      if (back > 0.0) {
         // ease back into his own eyes: the release at the end is invisible
         float w = (float) smooth(Math.min(1.0, back / RETURN));
         return new Vec3[]{cur[0].lerp(eye, w), cur[1].lerp(eyeLook, w)};
      }

      return cur;
   }

   /** Pure: the target's shot [position, look-at]. */
   static Vec3[] targetShot(Vec3 c, Vec3 target, double t) {
      return blended(c, target, t, false);
   }

   private static Vec3[] blended(Vec3 c, Vec3 target, double t, boolean caster) {
      Vec3[] cur = raw(c, target, t, caster);
      for (int cut : new int[]{RegulusRules.ULT_SCOOP, RegulusRules.ULT_SENTENCE, RegulusRules.ULT_FLICK, RegulusRules.ULT_RESOLVE}) {
         if (t >= cut && t < cut + RegulusRules.ULT_BLEND) {
            Vec3[] prev = raw(c, target, cut - 1, caster);
            float w = (float) smooth((t - cut) / RegulusRules.ULT_BLEND);
            return new Vec3[]{prev[0].lerp(cur[0], w), prev[1].lerp(cur[1], w)};
         }
      }

      return cur;
   }

   /**
    * Raw shots. {@code f} points from Regulus to the target, {@code r} to its right. The caster sees
    * his own calm (frontal), the scooped dome (orbit), both of them (side two-shot), his finger
    * (close) and the result (high behind); the target sees him towering (low over its shoulder),
    * the dome closing, itself held, and the finger raised.
    */
   static Vec3[] raw(Vec3 c, Vec3 target, double t, boolean caster) {
      Vec3 flat = new Vec3(target.x - c.x, 0.0, target.z - c.z);
      Vec3 f = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
      Vec3 r = new Vec3(-f.z, 0.0, f.x);
      double d = Math.sqrt(flat.lengthSqr());
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      if (t < RegulusRules.ULT_SCOOP) {
         double u = t / RegulusRules.ULT_SCOOP;
         if (caster) {
            // low frontal on his face, a slow push-in while he accuses
            return new Vec3[]{c.add(f.scale(2.6 - 0.6 * u)).add(r.scale(0.7)).add(up.scale(1.25)), c.add(up.scale(1.62))};
         }

         // the target, low over its shoulder: he towers
         return new Vec3[]{target.add(f.scale(1.9 + 0.3 * u)).add(r.scale(0.75)).add(up.scale(1.05)), c.add(up.scale(1.7))};
      }

      if (t < RegulusRules.ULT_SENTENCE) {
         double u = (t - RegulusRules.ULT_SCOOP) / (RegulusRules.ULT_SENTENCE - RegulusRules.ULT_SCOOP);
         double a = Math.toRadians(caster ? 200.0 + 60.0 * u : 30.0 + 60.0 * u);
         double radius = caster ? 7.5 : 3.2;
         Vec3 around = f.scale(Math.cos(a)).add(r.scale(Math.sin(a)));
         return new Vec3[]{target.add(around.scale(radius)).add(up.scale(caster ? 2.5 + u : 1.0 + 1.2 * u)), target.add(up.scale(1.0))};
      }

      if (t < RegulusRules.ULT_FLICK) {
         double u = (t - RegulusRules.ULT_SENTENCE) / (RegulusRules.ULT_FLICK - RegulusRules.ULT_SENTENCE);
         if (caster) {
            // side two-shot that slowly tracks from him toward the held target
            Vec3 mid = c.add(f.scale(d * (0.4 + 0.2 * u)));
            double side = Math.max(4.5, d * 0.55);
            return new Vec3[]{mid.add(r.scale(side)).add(up.scale(1.6)), mid.add(up.scale(1.1))};
         }

         // between them, on the target's face: it sees itself held, him behind
         return new Vec3[]{target.subtract(f.scale(2.4 - 0.4 * u)).add(r.scale(0.5)).add(up.scale(0.95)), target.add(up.scale(1.35))};
      }

      if (t < RegulusRules.ULT_RESOLVE) {
         if (caster) {
            if (t < RegulusRules.ULT_FLICK_CONTACT) {
               // close on the finger that will end it
               return new Vec3[]{c.add(f.scale(1.1)).subtract(r.scale(1.0)).add(up.scale(1.5)), c.add(f.scale(0.45)).subtract(r.scale(0.3)).add(up.scale(1.35))};
            }

            // hard cut on the contact: high behind him, the target thrown
            double u = (t - RegulusRules.ULT_FLICK_CONTACT) / (RegulusRules.ULT_RESOLVE - RegulusRules.ULT_FLICK_CONTACT);
            return new Vec3[]{c.subtract(f.scale(3.0 + u)).add(up.scale(3.0 + 0.6 * u)), target.add(f.scale(2.0 * u)).add(up.scale(0.6))};
         }

         return new Vec3[]{target.add(f.scale(1.8)).add(up.scale(1.5)), c.add(up.scale(1.45))};
      }

      double u = Math.min(1.0, (t - RegulusRules.ULT_RESOLVE) / (RegulusRules.ULT_LENGTH - RegulusRules.ULT_RESOLVE));
      // the calm afterwards: low frontal pulling back
      return new Vec3[]{c.add(f.scale(2.6 + 1.2 * u)).add(r.scale(0.8)).add(up.scale(0.95)), c.add(up.scale(1.5))};
   }

   static double smooth(double x) {
      double k = Mth.clamp(x, 0.0, 1.0);
      return k * k * (3.0 - 2.0 * k);
   }

   private static void place(Minecraft mc, Marker cam, Vec3 pos, Vec3 look, boolean snap) {
      BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
      if (hit.getType() == HitResult.Type.BLOCK) {
         Vec3 dir = pos.subtract(look).normalize();
         pos = hit.getLocation().subtract(dir.scale(0.3));
      }

      Vec3 d = look.subtract(pos);
      float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI)));
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
         if (mc.player != null) {
            mc.setCameraEntity(mc.player);
         }

         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }
      }

      savedType = null;
      camera = null;
      cameraLevel = null;
   }

   // ---------------------------------------------------------------- render

   /** The local player is not drawn by the level while the marker is the camera: draw it here. */
   static void renderModels(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!cameraActive() || mc.player == null) {
         return;
      }

      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      AbstractClientPlayer p = mc.player;
      Vec3 at = p.getPosition(partial);
      var dispatcher = mc.getEntityRenderDispatcher();
      dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, stack, buffers,
         dispatcher.getPackedLightCoords(p, partial));
      buffers.endBatch();
   }

   public static void onRenderHand(RenderHandEvent event) {
      if (cameraActive()) {
         event.setCanceled(true);
      }
   }

   public static void onRenderGui(RenderGuiEvent.Pre event) {
      float k = Mth.lerp(event.getPartialTick(), letterboxO, letterbox);
      if (k < 0.01F) {
         return;
      }

      var g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      int bar = Math.round(h * 0.1F * k);
      g.fill(0, 0, w, bar, 0xFF000000);
      g.fill(0, h - bar, w, h, 0xFF000000);
      // a hairline of pale gold on the bars: his colour, not a flash
      int line = (Math.round(150 * k) << 24) | RegulusPalette.PALE_GOLD;
      g.fill(0, bar, w, bar + 1, line);
      g.fill(0, h - bar - 1, w, h - bar, line);
   }

   public static void onFov(ViewportEvent.ComputeFov event) {
      if (cameraActive()) {
         event.setFOV(62.0 - 3.0 * kick);
      } else if (lean > 0.01F) {
         event.setFOV(event.getFOV() * (1.0 - 0.07 * lean));
      }
   }

   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (lean > 0.01F && !cameraActive()) {
         event.setRoll(event.getRoll() + 2.0F * lean);
      }
   }

   static void clear() {
      SCENES.clear();
      ENDED.clear();
      releaseCamera(Minecraft.getInstance());
      letterbox = letterboxO = lean = kick = 0.0F;
   }
}
