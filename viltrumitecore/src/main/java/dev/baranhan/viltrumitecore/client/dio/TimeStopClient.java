package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Client side of DIO's stopped-time fields: the server packets keep the field
 * list (center, radius, clock, frozen ids); the cast before the capture comes
 * from the caster's snapshot. Drives the screen grade (TimeStopScreen), the
 * inverted capture/return sphere in the world, a cold dome seen from outside,
 * and blocks the local player's input while he is frozen. Everything clears on
 * logout and on a level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class TimeStopClient {
   private static final Map<UUID, Field> FIELDS = new HashMap<>();
   /** Ticks an ended field stays for the colour return. */
   private static final int LINGER = 12;
   private static ClientLevel lastLevel;

   private TimeStopClient() {
   }

   static final class Field {
      final UUID id;
      int casterId;
      Vec3 center;
      float radius;
      int duration;
      double age;
      boolean scripted;
      Set<Integer> frozen = new HashSet<>();
      double endedFor = -1.0;
      boolean abrupt;
      boolean flashed;

      Field(UUID id) {
         this.id = id;
      }

      double left() {
         return Math.max(0.0, this.duration - this.age);
      }

      boolean running() {
         return this.endedFor < 0.0;
      }
   }

   public static void accept(DioTimeS2CPacket packet) {
      if (!packet.active()) {
         Field field = FIELDS.get(packet.effectId());
         if (field != null && field.running()) {
            field.abrupt = field.scripted || field.left() > 2.0;
            field.endedFor = 0.0;
            field.frozen.clear();
         }

         return;
      }

      Field field = FIELDS.computeIfAbsent(packet.effectId(), Field::new);
      field.casterId = packet.casterId();
      field.center = new Vec3(packet.x(), packet.y(), packet.z());
      field.radius = packet.radius();
      field.duration = packet.duration();
      field.scripted = packet.scripted();
      if (Math.abs(field.age - packet.age()) > 2.0) {
         field.age = packet.age();
      }

      Set<Integer> ids = new HashSet<>();
      for (int id : packet.frozen()) {
         ids.add(id);
      }

      field.frozen = ids;
   }

   /** Any running field holds this entity (client knowledge, render and input only). */
   public static boolean isFrozen(int entityId) {
      for (Field field : FIELDS.values()) {
         if (field.running() && field.frozen.contains(entityId)) {
            return true;
         }
      }

      return false;
   }

   /** A running field covers this point (own particles hang there). */
   public static boolean stoppedAt(Vec3 at) {
      for (Field field : FIELDS.values()) {
         if (field.running() && field.age >= 0.0 && at.distanceToSqr(field.center) <= field.radius * (double)field.radius) {
            return true;
         }
      }

      return false;
   }

   public static boolean anyActive() {
      return !FIELDS.isEmpty();
   }

   /** {grey, invert, ripple} for the post pass, strongest over every field and cast. */
   public static float[] screen(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.gameRenderer == null) {
         return TimeStopScreen.NONE;
      }

      Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
      float[] out = {0.0F, 0.0F, 0.0F};
      boolean any = false;
      for (Field field : FIELDS.values()) {
         double age = field.age + (field.running() ? partialTick : 0.0);
         double ended = field.running() ? -1.0 : field.endedFor + partialTick;
         float[] s = TimeStopScreen.screen(age, field.left() - (field.running() ? partialTick : 0.0), field.scripted || field.abrupt, ended,
            eye.distanceTo(field.center), field.radius);
         any |= max(out, s);
      }

      for (Cast cast : casts(partialTick)) {
         if (FIELDS.values().stream().noneMatch(f -> f.casterId == cast.casterId)) {
            any |= max(out, TimeStopScreen.screen(cast.age, 1000.0, true, -1.0, eye.distanceTo(cast.center), cast.radius));
         }
      }

      return any ? out : TimeStopScreen.NONE;
   }

   private static boolean max(float[] out, float[] s) {
      if (s == TimeStopScreen.NONE) {
         return false;
      }

      for (int i = 0; i < 3; i++) {
         out[i] = Math.max(out[i], s[i]);
      }

      return true;
   }

   /** A cast in progress before its field exists: age is negative until the capture tick. */
   record Cast(int casterId, Vec3 center, float radius, double age) {
   }

   private static List<Cast> casts(float partialTick) {
      List<Cast> list = new ArrayList<>();
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         return list;
      }

      for (Player player : client.level.players()) {
         HeroPublicSnapshot snapshot = DioView.of(player);
         if (snapshot == null) {
            continue;
         }

         double t = snapshot.actionElapsed() + partialTick;
         Vec3 center = player.getPosition(partialTick).add(0.0, 1.0, 0.0);
         if (DioView.is(snapshot, HeroAction.TIME_STOP) && t >= DioRules.TIME_STOP_CAPTURE - TimeStopScreen.WAVE_LEAD && t < DioRules.TIME_STOP_CAPTURE + 2) {
            list.add(new Cast(player.getId(), center, (float)DioRules.TIME_STOP_RADIUS, t - DioRules.TIME_STOP_CAPTURE));
         } else if (DioView.is(snapshot, HeroAction.ROAD_ROLLER) && t < DioRules.ROLLER_CAPTURE + 2) {
            list.add(new Cast(player.getId(), center, (float)DioRules.ROLLER_RADIUS, t - DioRules.ROLLER_CAPTURE));
         }
      }

      return list;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         FIELDS.clear();
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
      Iterator<Field> it = FIELDS.values().iterator();
      while (it.hasNext()) {
         Field field = it.next();
         if (field.running()) {
            field.age += 1.0;
            if (!field.scripted && !field.flashed && field.left() <= TimeStopScreen.RESUME_TICKS * eye.distanceTo(field.center) / field.radius
               && eye.distanceTo(field.center) <= field.radius) {
               field.flashed = true;
               FlashOverlay.flash(0.28F, 5);
            }
         } else {
            if (!field.flashed && eye.distanceTo(field.center) <= field.radius) {
               field.flashed = true;
               FlashOverlay.flash(0.3F, 5);
            }

            field.endedFor += 1.0;
            if (field.endedFor > LINGER) {
               it.remove();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      FIELDS.clear();
   }

   private static boolean localFrozen() {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && isFrozen(client.player.getId());
   }

   @SubscribeEvent
   public static void onMovement(MovementInputUpdateEvent event) {
      if (!localFrozen()) {
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
      event.getEntity().setDeltaMovement(Vec3.ZERO);
   }

   @SubscribeEvent
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (localFrozen()) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   // --- world: inverted capture / return sphere and the cold dome ----------------------------

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FIELDS.isEmpty() && !anyCast() || ShaderCompat.isShadowPass()) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      List<double[]> inverted = new ArrayList<>();
      List<double[]> domes = new ArrayList<>();
      for (Field field : FIELDS.values()) {
         double age = field.age + (field.running() ? partialTick : 0.0);
         double r = -1.0;
         if (field.running() && age < 4.0) {
            r = TimeStopScreen.front(age, field.radius);
         } else if (field.running() && !field.scripted && field.left() <= TimeStopScreen.RESUME_TICKS) {
            r = TimeStopScreen.backFront(field.left() - partialTick, field.radius);
         } else if (!field.running() && field.abrupt) {
            r = field.radius * Math.max(0.0, 1.0 - (field.endedFor + partialTick) / TimeStopScreen.RETURN_TICKS);
         }

         if (r > 0.05) {
            inverted.add(new double[]{field.center.x, field.center.y, field.center.z, r});
         }

         if (field.running() && age >= 4.0) {
            domes.add(new double[]{field.center.x, field.center.y, field.center.z, field.radius, age});
         }
      }

      for (Cast cast : casts(partialTick)) {
         if (FIELDS.values().stream().noneMatch(f -> f.casterId == cast.casterId)) {
            double r = TimeStopScreen.front(cast.age, cast.radius);
            if (r > 0.05) {
               inverted.add(new double[]{cast.center.x, cast.center.y, cast.center.z, r});
            }
         }
      }

      if (inverted.isEmpty() && domes.isEmpty()) {
         return;
      }

      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.rotateCamera(modelView, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         if (!inverted.isEmpty()) {
            // Colour inversion of everything seen through the outer surface (one face per ray).
            RenderSystem.blendFunc(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO);
            buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (double[] s : inverted) {
               sphere(buffer, cam, s[0], s[1], s[2], s[3], 255, 255, 255, 255);
            }

            tesselator.end();
         }

         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         float seconds = (float)(System.nanoTime() / 1.0E9 % 1000.0);
         for (double[] s : inverted) {
            Vec3 c = new Vec3(s[0], s[1], s[2]);
            PixelVfx.sphereShell(buffer, cam, camera, c, (float)s[3], seconds, 150, 220, 255, 150);
         }

         for (double[] d : domes) {
            Vec3 c = new Vec3(d[0], d[1], d[2]);
            if (cam.distanceTo(c) > d[3] - 0.5) {
               PixelVfx.sphereShell(buffer, cam, camera, c, (float)d[3], 0.0F, 120, 150, 190, 26);
            }
         }

         tesselator.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static boolean anyCast() {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         return false;
      }

      for (Player player : client.level.players()) {
         HeroPublicSnapshot snapshot = DioView.of(player);
         if (snapshot != null && (DioView.is(snapshot, HeroAction.TIME_STOP) || DioView.is(snapshot, HeroAction.ROAD_ROLLER))) {
            return true;
         }
      }

      return false;
   }

   /** Lat-long sphere, only the faces turned toward the camera (manual back-face cull). */
   private static void sphere(BufferBuilder buffer, Vec3 cam, double cx, double cy, double cz, double r, int red, int green, int blue, int alpha) {
      int lat = 18, lon = 32;
      for (int i = 0; i < lat; i++) {
         double a0 = Math.PI * i / lat - Math.PI / 2, a1 = Math.PI * (i + 1) / lat - Math.PI / 2;
         for (int j = 0; j < lon; j++) {
            double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
            double am = (a0 + a1) / 2, bm = (b0 + b1) / 2;
            double nx = Math.cos(am) * Math.cos(bm), ny = Math.sin(am), nz = Math.cos(am) * Math.sin(bm);
            double px = cx + nx * r - cam.x, py = cy + ny * r - cam.y, pz = cz + nz * r - cam.z;
            if (nx * px + ny * py + nz * pz >= 0.0) {
               continue;
            }

            vertex(buffer, cam, cx, cy, cz, r, a0, b0, red, green, blue, alpha);
            vertex(buffer, cam, cx, cy, cz, r, a1, b0, red, green, blue, alpha);
            vertex(buffer, cam, cx, cy, cz, r, a1, b1, red, green, blue, alpha);
            vertex(buffer, cam, cx, cy, cz, r, a0, b1, red, green, blue, alpha);
         }
      }
   }

   private static void vertex(BufferBuilder buffer, Vec3 cam, double cx, double cy, double cz, double r, double a, double b, int red, int green, int blue,
      int alpha) {
      buffer.vertex((float)(cx + Math.cos(a) * Math.cos(b) * r - cam.x), (float)(cy + Math.sin(a) * r - cam.y), (float)(cz + Math.cos(a) * Math.sin(b) * r - cam.z))
         .color(red, green, blue, alpha).endVertex();
   }
}
