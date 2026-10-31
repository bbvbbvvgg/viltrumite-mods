package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * «Avengers, assemble!» as a film on the caster's own client: letterbox bars, the vanilla HUD hidden, and a
 * directed camera through a client-only marker ({@link CaptainSceneShots}) for the ceremony moves only
 * (call, arrival, circle). Other players keep their own view. Walls between the subject and the lens pull the
 * lens in. Any loss (the ceremony ends, death, spectator, level change, logout) gives the view back at once and
 * restores the camera type. Nothing here touches the world.
 */
public final class CaptainUltScene {
   static final float FOV = 58.0F;
   static final float BAR = 0.11F;
   private static final Set<String> HIDDEN = Set.of("hotbar", "player_health", "armor_level", "food_level", "air_level",
      "mount_health", "jump_bar", "experience_bar", "item_name", "crosshair", "potion_icons", "vignette");
   @Nullable
   private static Marker camera;
   @Nullable
   private static CameraType savedType;
   @Nullable
   private static ClientLevel cameraLevel;
   @Nullable
   private static Vec3 centre;
   @Nullable
   private static Vec3 facing;
   @Nullable
   private static CaptainMove move;
   private static int age;
   private static double wideEnd = CaptainSceneShots.WIDE_START;
   @Nullable
   private static Vec3[] blendFrom;
   private static float bars;
   private static float barsO;
   /** Arrival close-up state: who the lens is on (0 = crane), ticks since that cut, the lens it blends from. */
   private static int closeWho;
   private static Vec3 closeFacing = new Vec3(0.0, 0.0, 1.0);
   private static int closeAge;
   @Nullable
   private static Vec3[] closeFrom;

   private CaptainUltScene() {
   }

   public static boolean active() {
      return camera != null && Minecraft.getInstance().getCameraEntity() == camera;
   }

   /** The ASSEMBLE cue of {@code caster}: the planned circle centre. */
   static void assemble(int caster, Vec3 steve, Vec3 circleCentre) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.getId() == caster) {
         centre = circleCentre;
         // Steve stands on the rim facing out, the centre behind him
         Vec3 d = new Vec3(steve.x - circleCentre.x, 0.0, steve.z - circleCentre.z);
         if (d.lengthSqr() > 0.25) {
            facing = d.normalize();
         }
      }
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.isPaused()) {
         return;
      }
      barsO = bars;
      LocalPlayer me = mc.player;
      ClientLevel level = mc.level;
      HeroPublicSnapshot s = me == null ? null : CaptainView.of(me);
      CaptainMove m = s == null ? null : CaptainView.move(s);
      boolean film = level != null && m != null && m.ceremony() && me.isAlive() && !me.isRemoved() && !me.isSpectator()
         && (cameraLevel == null || cameraLevel == level);
      // another hero's film (or the spectator camera) owns the view: stay out of it
      if (film && mc.getCameraEntity() != me && !active()) {
         film = false;
      }
      bars += ((film ? 1.0F : 0.0F) - bars) * 0.25F;
      if (!film) {
         reset(mc);
         return;
      }
      if (m != move) {
         if (move == CaptainMove.ASSEMBLE_WAIT && m == CaptainMove.ASSEMBLE_RAISE) {
            wideEnd = CaptainSceneShots.wideAngle(age);
            blendFrom = current();
         } else {
            blendFrom = null; // a hard cut
         }
         if (move == null) {
            // joined mid-scene or the first tick: the snapshot's clock is enough
            age = Math.max(0, s.actionElapsed());
            if (m == CaptainMove.ASSEMBLE_RAISE) {
               wideEnd = CaptainSceneShots.wideAngle(CaptainRules.FORM_START - CaptainRules.CALL_TICKS);
            }
         } else {
            age = 0;
         }
         move = m;
      } else {
         age++;
      }
      Vec3 steve = me.position();
      Vec3 f = facing != null ? facing : flat(me.getYRot());
      Vec3 c = centre != null && centre.distanceToSqr(steve) < 16.0 * 16.0 ? centre : steve.subtract(f.scale(CaptainRules.CIRCLE_RADIUS));
      if (facing == null) {
         facing = f;
      }
      if (centre == null) {
         centre = c;
      }
      Vec3[] shot = CaptainSceneShots.shot(m, age, steve, c, f, wideEnd);
      if (blendFrom != null && age < CaptainSceneShots.BLEND) {
         float w = (float)CaptainSceneShots.smooth((age + 1.0) / CaptainSceneShots.BLEND);
         shot = new Vec3[]{blendFrom[0].lerp(shot[0], w), blendFrom[1].lerp(shot[1], w)};
      }
      if (m == CaptainMove.ASSEMBLE_WAIT) {
         shot = arrivalCloseUp(level, me, shot, c, f);
      }
      if (m == CaptainMove.ASSEMBLE_RAISE) {
         double w = CaptainSceneShots.returnWeight(age);
         if (w > 0.0) {
            Vec3 eye = me.getEyePosition();
            shot = new Vec3[]{shot[0].lerp(eye, w), shot[1].lerp(eye.add(me.getLookAngle().scale(6.0)), w)};
         }
      }
      boolean snap = false;
      if (camera == null || camera.level() != level) {
         camera = new Marker(EntityType.MARKER, level);
         cameraLevel = level;
         snap = true;
      }
      if (mc.getCameraEntity() != camera) {
         savedType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(camera);
         snap = true;
      }
      place(level, camera, shot[0], shot[1], snap);
   }

   /** Cuts the crane to Natasha's, then Clint's close-up while they drop from the ramp (cross-faded both ways). */
   private static Vec3[] arrivalCloseUp(ClientLevel level, LocalPlayer me, Vec3[] wide, Vec3 c, Vec3 f) {
      int sceneT = CaptainRules.CALL_TICKS + age;
      int who = CaptainSceneShots.closeUp(sceneT);
      AvengerEntity subject = who == 0 ? null : dropper(level, me, who == 1 ? AvengerKind.BLACK_WIDOW : AvengerKind.HAWKEYE, c);
      int now = subject == null ? 0 : who;
      if (now != closeWho) {
         closeFrom = current();
         closeWho = now;
         closeAge = 0;
         closeFacing = subject == null ? f : Vec3.directionFromRotation(0.0F, subject.getYHeadRot());
      } else {
         closeAge++;
      }
      Vec3[] shot = wide;
      if (subject != null) {
         int start = who == 1 ? CaptainSceneShots.WIDOW_CLOSE : CaptainSceneShots.CLINT_CLOSE;
         shot = CaptainSceneShots.closeShot(subject.position(), closeFacing, c.y, who, (sceneT - start) / (double)CaptainSceneShots.CLOSE_TICKS);
      }
      if (closeFrom != null && closeAge < CaptainSceneShots.CLOSE_BLEND) {
         float w = (float)CaptainSceneShots.smooth((closeAge + 1.0) / CaptainSceneShots.CLOSE_BLEND);
         shot = new Vec3[]{closeFrom[0].lerp(shot[0], w), closeFrom[1].lerp(shot[1], w)};
      }
      return shot;
   }

   /** The caster's own Natasha / Clint near the circle (none: the crane stays). */
   @Nullable
   private static AvengerEntity dropper(ClientLevel level, LocalPlayer me, AvengerKind kind, Vec3 c) {
      AvengerEntity best = null;
      for (AvengerEntity e : level.getEntitiesOfClass(AvengerEntity.class, new AABB(c, c).inflate(48.0, 40.0, 48.0),
            e -> e.isAlive() && e.kind() == kind && e.ownerId() == me.getId())) {
         if (best == null || e.distanceToSqr(c) < best.distanceToSqr(c)) {
            best = e;
         }
      }
      return best;
   }

   @Nullable
   private static Vec3[] current() {
      return camera == null ? null : new Vec3[]{camera.position(), camera.position().add(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).scale(6.0))};
   }

   private static Vec3 flat(float yaw) {
      double a = Math.toRadians(yaw);
      return new Vec3(-Math.sin(a), 0.0, Math.cos(a));
   }

   /** Puts the lens at {@code pos} looking at {@code look}; a wall in between pulls it in front of the wall. */
   private static void place(ClientLevel level, Marker cam, Vec3 pos, Vec3 look, boolean snap) {
      BlockHitResult hit = level.clip(new ClipContext(look, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
      if (hit.getType() == HitResult.Type.BLOCK) {
         Vec3 dir = pos.subtract(look);
         double len = dir.length();
         double keep = Math.max(0.4, look.distanceTo(hit.getLocation()) - 0.35);
         pos = len < 1.0E-4 ? look : look.add(dir.scale(keep / len));
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

   private static void reset(Minecraft mc) {
      if (camera != null && mc.getCameraEntity() == camera) {
         if (mc.player != null) {
            mc.setCameraEntity(mc.player);
         }
         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }
      }
      camera = null;
      cameraLevel = null;
      savedType = null;
      move = null;
      age = 0;
      blendFrom = null;
      closeWho = 0;
      closeAge = 0;
      closeFrom = null;
      wideEnd = CaptainSceneShots.WIDE_START;
      if (mc.player == null || CaptainView.of(mc.player) == null || !ceremony(mc.player)) {
         centre = null;
         facing = null;
      }
   }

   private static boolean ceremony(LocalPlayer p) {
      HeroPublicSnapshot s = CaptainView.of(p);
      CaptainMove m = s == null ? null : CaptainView.move(s);
      return m != null && m.ceremony();
   }

   // ---------------------------------------------------------------- render

   /** The level does not draw the local player while the marker is the camera: draw him here. */
   public static void onRenderLevel(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !active() || mc.player == null) {
         return;
      }
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      LocalPlayer p = mc.player;
      Vec3 at = p.getPosition(partial);
      var dispatcher = mc.getEntityRenderDispatcher();
      dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, stack, buffers,
         dispatcher.getPackedLightCoords(p, partial));
      buffers.endBatch();
   }

   /** His own name tag would float in front of the lens. */
   public static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
      if (active() && event.getEntity() == Minecraft.getInstance().player) {
         event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
      }
   }

   public static void onRenderHand(RenderHandEvent event) {
      if (active()) {
         event.setCanceled(true);
      }
   }

   public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
      if (bars > 0.05F && event.getOverlay().id().getNamespace().equals("minecraft") && HIDDEN.contains(event.getOverlay().id().getPath())) {
         event.setCanceled(true);
      }
   }

   public static void onRenderGui(RenderGuiEvent.Post event) {
      float k = Mth.lerp(event.getPartialTick(), barsO, bars);
      if (k < 0.01F) {
         return;
      }
      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      int bar = Math.round(h * BAR * k);
      g.fill(0, 0, w, bar, 0xFF000000);
      g.fill(0, h - bar, w, h, 0xFF000000);
      // a thin red-white-blue rule on the inner edges
      int a = Math.round(170 * k) << 24;
      g.fill(0, bar, w / 3, bar + 1, a | 0xC8102E);
      g.fill(w / 3, bar, 2 * w / 3, bar + 1, a | 0xF2F2F2);
      g.fill(2 * w / 3, bar, w, bar + 1, a | 0x1F3A93);
      g.fill(0, h - bar - 1, w / 3, h - bar, a | 0x1F3A93);
      g.fill(w / 3, h - bar - 1, 2 * w / 3, h - bar, a | 0xF2F2F2);
      g.fill(2 * w / 3, h - bar - 1, w, h - bar, a | 0xC8102E);
   }

   public static void onFov(ViewportEvent.ComputeFov event) {
      if (active()) {
         event.setFOV(FOV);
      }
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         bars = barsO = 0.0F;
         reset(Minecraft.getInstance());
         centre = null;
         facing = null;
      }
   }
}
