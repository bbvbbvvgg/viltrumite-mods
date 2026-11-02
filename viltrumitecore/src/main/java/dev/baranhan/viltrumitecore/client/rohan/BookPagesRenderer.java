package dev.baranhan.viltrumitecore.client.rohan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Heaven's Door in the world, for every viewer: the 3D pages of an opened body (they fan out of the face like a book
 * toward the reader, flutter, glow while a line is written, fold shut), loose sheets (the stand appearing, a torn page),
 * ink streaks (pen nibs, a reflected line), the air sketch hanging where Rohan drew it and the manuscript page he
 * raises. All state is client-side and fed by {@link RohanFx} and the public snapshots.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class BookPagesRenderer {
   static final ResourceLocation PAGE = HeavensDoorRenderer.rl("textures/entity/rohan/page.png");
   static final ResourceLocation PAGE_INK = HeavensDoorRenderer.rl("textures/entity/rohan/page_ink.png");
   static final ResourceLocation SKETCH = HeavensDoorRenderer.rl("textures/entity/rohan/sketch.png");
   static final ResourceLocation MANUSCRIPT = HeavensDoorRenderer.rl("textures/entity/rohan/manuscript.png");
   static final ResourceLocation STREAK = HeavensDoorRenderer.rl("textures/entity/rohan/streak.png");
   /** Pages of an opened body, ticks to fan out / fold shut. */
   public static final int PAGES = 7, OPEN_TICKS = 8, CLOSE_TICKS = 6;
   private static final Map<Integer, Book> BOOKS = new HashMap<>();
   private static final List<Sheet> SHEETS = new ArrayList<>();
   private static final List<Streak> STREAKS = new ArrayList<>();
   private static final Random RANDOM = new Random();
   @Nullable
   private static ClientLevel lastLevel;

   private BookPagesRenderer() {
   }

   static final class Book {
      double openedAt;
      double closedAt = -1.0;
      double writeAt = -1000.0;
      double resistAt = -1000.0;
      int window;
      int sourceId;
   }

   static final class Sheet {
      Vec3 pos, vel;
      float yaw, pitch, spinYaw, spinPitch, size;
      double bornAt;
      int life;
   }

   static final class Streak {
      Vec3 from, to;
      double bornAt;
      int life;
      float r, g, b, width;
   }

   static double now() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0 : mc.level.getGameTime() + mc.getFrameTime();
   }

   // --- events from RohanFx ---

   static void open(int targetId, int sourceId, int window) {
      Book b = BOOKS.get(targetId);
      if (b == null || b.closedAt >= 0.0) {
         b = new Book();
         b.openedAt = now();
         BOOKS.put(targetId, b);
      }
      b.window = Math.max(window, 20);
      b.sourceId = sourceId;
   }

   static void write(int targetId) {
      Book b = BOOKS.get(targetId);
      if (b != null) {
         b.writeAt = now();
      }
   }

   static void resist(int targetId) {
      Book b = BOOKS.get(targetId);
      if (b != null) {
         b.resistAt = now();
      }
   }

   static void close(int targetId) {
      Book b = BOOKS.get(targetId);
      if (b != null && b.closedAt < 0.0) {
         b.closedAt = now();
      }
   }

   public static boolean isOpen(int entityId) {
      Book b = BOOKS.get(entityId);
      return b != null && b.closedAt < 0.0;
   }

   /** Loose sheets bursting from a point. */
   public static void sheets(Vec3 at, int count, float speed) {
      double t = now();
      for (int i = 0; i < count && SHEETS.size() < 96; i++) {
         Sheet s = new Sheet();
         s.pos = at.add((RANDOM.nextDouble() - 0.5) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.4);
         s.vel = new Vec3((RANDOM.nextDouble() - 0.5) * speed, RANDOM.nextDouble() * speed * 0.6, (RANDOM.nextDouble() - 0.5) * speed).scale(0.25);
         s.yaw = RANDOM.nextFloat() * 360.0F;
         s.pitch = RANDOM.nextFloat() * 180.0F - 90.0F;
         s.spinYaw = (RANDOM.nextFloat() - 0.5F) * 30.0F;
         s.spinPitch = (RANDOM.nextFloat() - 0.5F) * 24.0F;
         s.size = 0.16F + RANDOM.nextFloat() * 0.08F;
         s.bornAt = t;
         s.life = 18 + RANDOM.nextInt(10);
         SHEETS.add(s);
      }
   }

   static void streak(Vec3 from, Vec3 to, int life, float r, float g, float b, float width) {
      if (STREAKS.size() > 48) {
         STREAKS.remove(0);
      }
      Streak s = new Streak();
      s.from = from;
      s.to = to;
      s.bornAt = now();
      s.life = Math.max(2, life);
      s.r = r;
      s.g = g;
      s.b = b;
      s.width = width;
      STREAKS.add(s);
   }

   /** Page angle (degrees from the face plane, 0 = flat on the right side) of page i at open progress p (pure). */
   public static float pageAngle(int i, float p, double ticks) {
      float target = 18.0F + 144.0F * i / (PAGES - 1);
      float flutter = (float)Math.sin(ticks * 0.31 + i * 1.7) * 3.5F * p;
      return target * p + flutter;
   }

   /** Open progress 0..1 of a book at {@code t} (pure): fans out over OPEN_TICKS, folds over CLOSE_TICKS. */
   public static float progress(double openedAt, double closedAt, double t) {
      double up = Math.min(1.0, Math.max(0.0, (t - openedAt) / OPEN_TICKS));
      float p = (float)(1.0 - Math.pow(1.0 - up, 3.0));
      if (closedAt >= 0.0) {
         double down = Math.min(1.0, Math.max(0.0, (t - closedAt) / CLOSE_TICKS));
         p *= (float)(1.0 - down * down);
      }
      return p;
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
         BOOKS.clear();
         SHEETS.clear();
         STREAKS.clear();
      }
      if (level == null) {
         return;
      }
      float partial = event.getPartialTick();
      double t = level.getGameTime() + partial;
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
      Matrix4f m = stack.last().pose();
      Matrix3f n = stack.last().normal();
      boolean drew = false;

      VertexConsumer paper = null;
      Iterator<Map.Entry<Integer, Book>> it = BOOKS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Book> e = it.next();
         Book b = e.getValue();
         Entity entity = level.getEntity(e.getKey());
         boolean expired = b.closedAt >= 0.0 ? t - b.closedAt > CLOSE_TICKS : t - b.openedAt > b.window + 60;
         if (entity == null || !entity.isAlive() || expired) {
            it.remove();
            continue;
         }
         // the opened player sees the victim overlay, not pages glued to the camera
         if (entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
            continue;
         }
         if (paper == null) {
            paper = buffers.getBuffer(RenderType.entityCutoutNoCull(PAGE));
         }
         drawBook(paper, buffers, m, n, cam, entity, b, t, partial, level);
         drew = true;
      }

      if (!SHEETS.isEmpty()) {
         VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(PAGE));
         Iterator<Sheet> si = SHEETS.iterator();
         while (si.hasNext()) {
            Sheet s = si.next();
            double age = t - s.bornAt;
            if (age > s.life || age < -2.0) {
               si.remove();
               continue;
            }
            Vec3 p = s.pos.add(s.vel.scale(age)).add(0.0, -0.004 * age * age * 0.2, 0.0);
            float alpha = (float)Math.min(1.0, (s.life - age) / 6.0);
            float yaw = (float)(s.yaw + s.spinYaw * age), pitch = (float)(s.pitch + s.spinPitch * age);
            Vec3[] q = card(p, yaw, pitch, s.size, s.size * 1.35F);
            quad(vc, m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, light(level, p), 1.0F, 1.0F, 1.0F, alpha);
         }
         drew = true;
      }

      if (!STREAKS.isEmpty()) {
         VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(STREAK));
         Iterator<Streak> st = STREAKS.iterator();
         while (st.hasNext()) {
            Streak s = st.next();
            double age = t - s.bornAt;
            if (age > s.life) {
               st.remove();
               continue;
            }
            float u = (float)(age / s.life);
            // the head races to the end in the first third, the tail follows
            Vec3 head = s.from.lerp(s.to, Math.min(1.0, u * 3.0));
            Vec3 tail = s.from.lerp(s.to, Math.max(0.0, u * 1.5 - 0.5));
            ribbon(vc, m, n, cam, tail, head, s.width * (1.0F - u * 0.5F), s.r, s.g, s.b, 1.0F - u * u);
         }
         drew = true;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot s = RohanView.of(player);
         if (s == null) {
            continue;
         }
         if (RohanView.flag(s, RohanFlags.SKETCH) && s.actionTarget() != null) {
            drawSketch(buffers, m, n, cam, s.actionTarget(), t, player.getId(), level);
            drew = true;
         }
         if (RohanView.move(s) == RohanMove.MANUSCRIPT) {
            drawManuscript(buffers, m, n, cam, player, s.actionElapsed() + partial, partial, level);
            drew = true;
         }
      }
      if (drew) {
         buffers.endBatch();
      }
   }

   private static void drawBook(VertexConsumer paper, MultiBufferSource buffers, Matrix4f m, Matrix3f n, Vec3 cam, Entity entity, Book b, double t,
      float partial, ClientLevel level) {
      float p = progress(b.openedAt, b.closedAt, t);
      if (p <= 0.001F) {
         return;
      }
      Vec3 pos = entity.getPosition(partial);
      Entity source = b.sourceId >= 0 ? level.getEntity(b.sourceId) : null;
      float yaw = entity instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : entity.getYRot();
      if (source != null && source != entity) {
         Vec3 d = source.getPosition(partial).subtract(pos);
         if (d.horizontalDistanceSqr() > 1.0E-3) {
            yaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
         }
      }
      double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
      Vec3 forward = new Vec3(fx, 0.0, fz);
      Vec3 right = new Vec3(-fz, 0.0, fx);
      float w = Mth.clamp(entity.getBbWidth() * 0.42F, 0.16F, 2.2F);
      float h = Mth.clamp(w * 1.35F, 0.22F, Math.max(0.3F, entity.getBbHeight() * 0.7F));
      double eye = entity instanceof LivingEntity l ? l.getEyeHeight() : entity.getBbHeight() * 0.8;
      Vec3 spine = pos.add(0.0, Math.max(h * 0.5, eye - h * 0.25), 0.0).add(forward.scale(entity.getBbWidth() * 0.5 + 0.03));
      int light = light(level, spine);
      double writeAge = t - b.writeAt, resistAge = t - b.resistAt;
      float glow = writeAge < 12.0 ? (float)(1.0 - writeAge / 12.0) : 0.0F;
      float resist = resistAge < 16.0 ? (float)(1.0 - resistAge / 16.0) : 0.0F;
      float shake = resist * (float)Math.sin(t * 2.7) * 4.0F;
      for (int i = 0; i < PAGES; i++) {
         float ang = (float)Math.toRadians(pageAngle(i, p, t) + shake);
         Vec3 dir = right.scale(Math.cos(ang)).add(forward.scale(Math.sin(ang)));
         Vec3 up = new Vec3(0.0, h * 0.5, 0.0);
         Vec3 a = spine.subtract(up), c = spine.add(up);
         Vec3 a2 = a.add(dir.scale(w)), c2 = c.add(dir.scale(w));
         // slight page curl: the outer edge lags a little lower
         Vec3 sag = new Vec3(0.0, -0.02 * w * (1.0 - p), 0.0);
         Vec3[] q = {a, a2.add(sag), c2.add(sag), c};
         float shade = 0.82F + 0.18F * (float)Math.sin(ang);
         float red = 1.0F, green = shade, blue = shade;
         if (resist > 0.0F) {
            green *= 1.0F - 0.45F * resist;
            blue *= 1.0F - 0.45F * resist;
         }
         quad(paper, m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, light, red * shade, green, blue, 1.0F);
         if (glow > 0.0F) {
            VertexConsumer ink = buffers.getBuffer(RenderType.eyes(PAGE_INK));
            Vec3 lift = dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize().scale(-0.004);
            Vec3[] g = {q[0].add(lift), q[1].add(lift), q[2].add(lift), q[3].add(lift)};
            quad(ink, m, n, cam, g, 0.0F, 0.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, glow, glow * 0.85F, glow * 0.4F, 1.0F);
         }
      }
   }

   private static void drawSketch(MultiBufferSource buffers, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 at, double t, int seed, ClientLevel level) {
      Vec3 toCam = cam.subtract(at);
      float yaw = (float)(Mth.atan2(toCam.z, toCam.x) * (180.0 / Math.PI)) - 90.0F;
      float wobble = (float)Math.sin(t * 0.4 + seed) * 2.0F;
      Vec3 c = at.add(0.0, Math.sin(t * 0.15 + seed) * 0.03, 0.0);
      Vec3[] q = card(c, yaw + wobble, 0.0F, 0.9F, 1.15F);
      quad(buffers.getBuffer(RenderType.entityTranslucent(SKETCH)), m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, 1.0F, 1.0F, 1.0F, 0.95F);
      float pulse = 0.35F + 0.25F * (float)Math.sin(t * 0.5);
      quad(buffers.getBuffer(RenderType.eyes(SKETCH)), m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, pulse * 0.6F, pulse, pulse * 0.8F, 1.0F);
   }

   private static void drawManuscript(MultiBufferSource buffers, Matrix4f m, Matrix3f n, Vec3 cam, Player player, double t, float partial, ClientLevel level) {
      float rise = RohanPoser.window(t, -1.0, RohanRules.MANUSCRIPT_SHOW + 6.0, 5.0);
      if (rise <= 0.01F) {
         return;
      }
      float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
      double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
      Vec3 at = player.getPosition(partial).add(fx * 0.35, 1.5 + 0.75 * rise, fz * 0.35);
      Vec3[] q = card(at, yaw, 0.0F, 0.55F, 0.75F);
      quad(buffers.getBuffer(RenderType.entityCutoutNoCull(MANUSCRIPT)), m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, light(level, at), 1.0F, 1.0F, 1.0F, 1.0F);
      float glow = rise * (0.6F + 0.4F * (float)Math.sin(t * 0.6));
      quad(buffers.getBuffer(RenderType.eyes(PAGE_INK)), m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, glow, glow * 0.9F, glow * 0.5F, 1.0F);
   }

   static int light(ClientLevel level, Vec3 p) {
      int packed = LevelRenderer.getLightColor(level, BlockPos.containing(p));
      return LightTexture.pack(Math.max(LightTexture.block(packed), 8), LightTexture.sky(packed));
   }

   /** A card centred at {@code c} facing yaw/pitch: corners bottom-left, bottom-right, top-right, top-left. */
   static Vec3[] card(Vec3 c, float yaw, float pitch, float w, float h) {
      double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
      Vec3 right = new Vec3(-Math.cos(y), 0.0, -Math.sin(y)).scale(w * 0.5);
      Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p)).scale(h * 0.5);
      return new Vec3[]{c.subtract(right).subtract(up), c.add(right).subtract(up), c.add(right).add(up), c.subtract(right).add(up)};
   }

   static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] q, float u0, float v0, float u1, float v1, int light, float r, float g,
      float b, float a) {
      Vec3 normal = q[1].subtract(q[0]).cross(q[3].subtract(q[0]));
      normal = normal.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : normal.normalize();
      float nx = (float)normal.x, ny = (float)normal.y, nz = (float)normal.z;
      vertex(vc, m, n, q[0].subtract(cam), u0, v1, light, r, g, b, a, nx, ny, nz);
      vertex(vc, m, n, q[1].subtract(cam), u1, v1, light, r, g, b, a, nx, ny, nz);
      vertex(vc, m, n, q[2].subtract(cam), u1, v0, light, r, g, b, a, nx, ny, nz);
      vertex(vc, m, n, q[3].subtract(cam), u0, v0, light, r, g, b, a, nx, ny, nz);
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int light, float r, float g, float b, float a,
      float nx, float ny, float nz) {
      vc.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz)
         .endVertex();
   }

   /** A camera-facing ribbon from a to b. */
   static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
      Vec3 along = b.subtract(a);
      if (along.lengthSqr() < 1.0E-6) {
         return;
      }
      Vec3 view = a.add(b).scale(0.5).subtract(cam);
      Vec3 side = along.cross(view);
      if (side.lengthSqr() < 1.0E-8) {
         return;
      }
      side = side.normalize().scale(width * 0.5);
      Vec3[] q = {a.subtract(side), b.subtract(side), b.add(side), a.add(side)};
      quad(vc, m, n, cam, q, 0.0F, 0.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, r, g, bl, alpha);
   }
}
