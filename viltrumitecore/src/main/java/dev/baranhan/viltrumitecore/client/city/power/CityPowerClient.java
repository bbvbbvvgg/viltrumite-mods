package dev.baranhan.viltrumitecore.client.city.power;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.city.power.PowerRules;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.ElectricVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client side of the city grid (Living City 3.0): the last state the server sent (cells without full power,
 * blown transformers, burning emergency lights, the alarm), the electric cues (impact flash and crackle, a
 * transformer blowing, a substation cascade) through the shared {@link ElectricVfx} / {@link FlashOverlay} /
 * {@link CameraShake} toolkit, ambient sparks at blown transformers, and the red emergency beacons drawn on the
 * lights that stay on. Real lamps are switched by the server; this only adds what blocks cannot show. Every list
 * is capped; everything clears on logout and level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public final class CityPowerClient {
   static final double BEACON_RANGE = 224.0;
   static final double TRANSFORMER_RANGE = 56.0;
   private static final Random RANDOM = new Random();

   static boolean known;
   static int originX;
   static int originZ;
   static final Long2IntOpenHashMap CELLS = new Long2IntOpenHashMap();
   static List<Vec3> transformers = List.of();
   static List<Vec3> emergency = List.of();
   static List<Vec3> sites = List.of();
   static List<Vec3> foci = List.of();
   static byte[] fociStage = new byte[0];
   static AlarmStage stage = AlarmStage.NONE;
   static int stageStart;
   static int cause;
   static float darkFraction;
   static int clock;
   private static ClientLevel lastLevel;
   private static int sparkBudgetClock;
   private static int nextArc;

   private CityPowerClient() {
   }

   public static AlarmStage stage() {
      return stage;
   }

   /** State of the grid cell at a block position (POWERED = 0 when unknown). */
   static int stateAt(double x, double z) {
      if (!known) {
         return 0;
      }
      long key = PowerRules.key(PowerRules.cellIndex(Mth.floor(x), originX), PowerRules.cellIndex(Mth.floor(z), originZ));
      return CELLS.getOrDefault(key, 0);
   }

   static boolean dark(int state) {
      return state == 3 || state == 4; // DARK, DAMAGED
   }

   static boolean flickering(int state) {
      return state == 1 || state == 2 || state == 5; // SURGE, FAILING, RESTORING
   }

   public static void handle(CityPowerS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }
      if (p.kind == CityPowerS2CPacket.STATE) {
         applyState(mc.player, p);
      } else {
         fx(mc, p);
      }
   }

   private static void applyState(LocalPlayer player, CityPowerS2CPacket p) {
      int before = stateAt(player.getX(), player.getZ());
      Long2IntOpenHashMap old = new Long2IntOpenHashMap(CELLS);
      known = true;
      originX = p.originX;
      originZ = p.originZ;
      CELLS.clear();
      for (long packed : p.cells) {
         CELLS.put(PowerRules.key(CityPowerS2CPacket.cellX(packed), CityPowerS2CPacket.cellZ(packed)), CityPowerS2CPacket.cellState(packed));
      }
      transformers = toVecs(p.transformers, 0.8);
      emergency = toVecs(p.emergency, 0.5);
      sites = toVecs(p.sites, 0.5);
      foci = toVecs(p.foci, 0.5);
      fociStage = p.fociStage;
      darkFraction = p.darkFraction;
      cause = p.cause;
      AlarmStage next = AlarmStage.byId(p.stage);
      if (next != stage) {
         AlarmStage prev = stage;
         stage = next;
         CityPowerAudio.onStage(prev, next);
      }
      stageStart = clock - p.stageAge;
      int after = stateAt(player.getX(), player.getZ());
      if (after != before) {
         CityPowerAudio.onOwnCell(before, after);
      }
      // nearby cells going dark or coming back: the breakers and the hum in the next street
      List<Vec3> down = new ArrayList<>(), up = new ArrayList<>();
      for (var e : CELLS.long2IntEntrySet()) {
         int was = old.getOrDefault(e.getLongKey(), 0);
         if (!dark(was) && dark(e.getIntValue())) {
            down.add(cellCentre(e.getLongKey(), player.getY()));
         }
      }
      for (var e : old.long2IntEntrySet()) {
         if (dark(e.getIntValue()) && !dark(CELLS.getOrDefault(e.getLongKey(), 0))) {
            up.add(cellCentre(e.getLongKey(), player.getY()));
         }
      }
      CityPowerAudio.onCells(player.position(), down, up);
   }

   static Vec3 cellCentre(long key, double y) {
      return new Vec3(originX + (PowerRules.keyX(key) + 0.5) * PowerRules.CELL, y, originZ + (PowerRules.keyZ(key) + 0.5) * PowerRules.CELL);
   }

   private static List<Vec3> toVecs(long[] a, double dy) {
      List<Vec3> out = new ArrayList<>(a.length);
      for (long l : a) {
         BlockPos b = BlockPos.of(l);
         out.add(new Vec3(b.getX() + 0.5, b.getY() + dy, b.getZ() + 0.5));
      }
      return List.copyOf(out);
   }

   // ------------------------------------------------------------------ cues

   private static void fx(Minecraft mc, CityPowerS2CPacket p) {
      Vec3 at = new Vec3(p.x, p.y, p.z);
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      double d = eye.distanceTo(at);
      long seed = p.seed;
      float power = Mth.clamp(p.power, 0.0F, 1.0F);
      switch (p.fx) {
         case CityPowerS2CPacket.FX_IMPACT -> {
            // the grid answers the hit: a blue-white flash in the sky and arcs crawling out of the crater
            ElectricVfx.skyFlash(at, 26.0F + 40.0F * power, 12, seed);
            ElectricVfx.lightPool(at, 9.0F + 12.0F * power, 1.0F, 16, seed);
            ElectricVfx.crackle(at.add(0, 1.0, 0), 3.0F + 5.0F * power, 5 + (int)(8 * power), 14, seed);
            if (d < 90.0) {
               FlashOverlay.flash(0.22F * power * (float)(1.0 - d / 90.0), 6);
            }
            CityPowerAudio.impact(at, d, power, seed);
         }
         case CityPowerS2CPacket.FX_BLOW -> {
            ElectricVfx.sparks(at, 60 + (int)(40 * power), 0.55F, 30, seed);
            ElectricVfx.crackle(at, 2.5F, 6, 10, seed + 1);
            ElectricVfx.arc(at, at.add(randomUnit(seed).scale(3.5)).add(0, 2.0, 0), 0.07F, 9, 3, seed + 2);
            ElectricVfx.lightPool(at, 9.0F, 1.2F, 14, seed + 3);
            CameraShake.addAt(at, 0.35F + 0.3F * power, 42.0);
            if (d < 32.0) {
               FlashOverlay.flash(0.2F * (float)(1.0 - d / 32.0), 5);
            }
            CityPowerAudio.blow(at, d, seed);
         }
         case CityPowerS2CPacket.FX_CASCADE -> {
            ElectricVfx.skyFlash(at, 70.0F, 16, seed);
            ElectricVfx.crackle(at.add(0, 1.0, 0), 7.0F, 12, 18, seed);
            ElectricVfx.sparks(at.add(0, 1.0, 0), 110, 0.7F, 34, seed + 1);
            ElectricVfx.lightPool(at, 16.0F, 1.4F, 20, seed + 2);
            for (int i = 0; i < 5; i++) {
               Vec3 end = at.add(randomUnit(seed + 10 + i).multiply(9, 0, 9)).add(0, 0.3, 0);
               ElectricVfx.arc(at.add(0, 2.5, 0), end, 0.09F, 12, 4, seed + 20 + i);
            }
            CameraShake.addAt(at, 0.6F, 90.0);
            if (d < 70.0) {
               FlashOverlay.flash(0.3F * (float)(1.0 - d / 70.0), 7);
            }
            CityPowerAudio.cascade(at, d, seed);
         }
         default -> {
            ElectricVfx.arc(at, at.add(randomUnit(seed).scale(3.0)), 0.06F, 8, 2, seed);
            CityPowerAudio.arc(at, d, seed);
         }
      }
   }

   private static Vec3 randomUnit(long seed) {
      Random r = new Random(seed);
      return new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.5, r.nextGaussian()).normalize();
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         clear();
      }
      if (mc.level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      clock++;
      CityPowerAudio.tick(mc, mc.player.position());
      if (transformers.isEmpty()) {
         return;
      }
      // blown transformers keep sparking: a few pixel sparks, now and then an arc (capped, near the player only)
      Vec3 me = mc.player.position();
      int sparks = 0;
      for (Vec3 t : transformers) {
         double d = t.distanceTo(me);
         if (d > TRANSFORMER_RANGE || sparks >= 2) {
            continue;
         }
         if (RANDOM.nextFloat() < 0.22F) {
            ElectricVfx.sparks(t.add(0, 0.6, 0), 2 + RANDOM.nextInt(4), 0.28F, 14, RANDOM.nextLong());
            sparks++;
         }
         if (clock >= nextArc && RANDOM.nextFloat() < 0.04F) {
            nextArc = clock + 22 + RANDOM.nextInt(30);
            long seed = RANDOM.nextLong();
            ElectricVfx.arc(t.add(0, 0.6, 0), t.add(randomUnit(seed).scale(2.0 + RANDOM.nextDouble() * 2.0)), 0.05F, 7, 2, seed);
            ElectricVfx.lightPool(t, 5.0F, 0.8F, 8, seed);
            CityPowerAudio.arc(t, d, seed);
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   static void clear() {
      known = false;
      CELLS.clear();
      transformers = List.of();
      emergency = List.of();
      sites = List.of();
      foci = List.of();
      fociStage = new byte[0];
      stage = AlarmStage.NONE;
      darkFraction = 0.0F;
      CityPowerAudio.clear();
   }

   // ------------------------------------------------------------------ emergency beacons

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || emergency.isEmpty() && transformers.isEmpty()) {
         return;
      }
      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      float time = clock + event.getPartialTick();
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.levelView(modelView, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      Matrix4f m = new Matrix4f();
      Vector3f c = new Vector3f();
      try {
         buf.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         int drawn = 0;
         double r2 = BEACON_RANGE * BEACON_RANGE;
         for (Vec3 e : emergency) {
            double dsq = e.distanceToSqr(eye);
            if (dsq > r2 || drawn >= 96) {
               continue;
            }
            drawn++;
            float dist = (float)Math.sqrt(dsq);
            long h = PowerRules.mix(Double.doubleToLongBits(e.x * 31 + e.z));
            // a rotating red beacon: the halo swells each time the reflector sweeps past the camera
            float phase = time * 0.21F + (h & 0xFF) / 40.0F;
            float sweep = (float)Math.pow(Math.max(0.0, Math.cos(phase)), 3.0);
            float size = 0.55F + dist * 0.0065F;
            c.set((float)(e.x - eye.x), (float)(e.y - eye.y), (float)(e.z - eye.z));
            disc(buf, m, c, left, up, size * (1.0F + sweep * 1.6F), 0.25F, 255, 38, 24, (int)(70 + 150 * sweep));
            disc(buf, m, c, left, up, size * 0.45F, 0.5F, 255, 120, 90, (int)(120 + 100 * sweep));
            // the reflector beam itself, a short horizontal fan turning around the light
            Vector3f beam = new Vector3f((float)Math.cos(phase) * 3.2F, 0.0F, (float)Math.sin(phase) * 3.2F);
            ribbon(buf, m, c, new Vector3f(c).add(beam), c, 0.25F, 0.9F, 255, 40, 25, (int)(90 * (0.4F + sweep)), 0);
         }
         for (Vec3 t : transformers) {
            double dsq = t.distanceToSqr(eye);
            if (dsq > 120 * 120) {
               continue;
            }
            // a blown transformer glows orange and stutters
            float flick = 0.4F + 0.6F * (float)Math.abs(Math.sin(time * 1.7F + t.x)) * (RANDOM.nextFloat() < 0.15F ? 0.2F : 1.0F);
            c.set((float)(t.x - eye.x), (float)(t.y - eye.y), (float)(t.z - eye.z));
            disc(buf, m, c, left, up, 1.3F, 0.2F, 255, 140, 40, (int)(90 * flick));
            disc(buf, m, c, left, up, 0.4F, 0.5F, 200, 220, 255, (int)(160 * flick));
         }
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static final float[] COS = new float[17];
   private static final float[] SIN = new float[17];

   static {
      for (int i = 0; i <= 16; i++) {
         COS[i] = (float)Math.cos(i / 16.0 * Math.PI * 2.0);
         SIN[i] = (float)Math.sin(i / 16.0 * Math.PI * 2.0);
      }
   }

   /** Camera-facing soft disc (solid core to {@code core} of the radius, fading to the rim), 16 sectors. */
   private static void disc(BufferBuilder vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float radius, float core, int r, int g, int b, int a) {
      if (a <= 0) {
         return;
      }
      float rc = radius * core;
      for (int i = 0; i < 16; i++) {
         float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
         put(vc, m, c, ax, bx, 0, 0, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * rc, s0 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, c1 * rc, s1 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, 0, 0, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * rc, s0 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * radius, s0 * radius, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * radius, s1 * radius, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * rc, s1 * rc, r, g, b, a);
      }
   }

   private static void ribbon(BufferBuilder vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f toCam, float w0, float w1, int r, int g, int b, int a0,
      int a1) {
      float dx = p1.x - p0.x, dy = p1.y - p0.y, dz = p1.z - p0.z;
      float sx = dy * toCam.z - dz * toCam.y, sy = dz * toCam.x - dx * toCam.z, sz = dx * toCam.y - dy * toCam.x;
      float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
      if (len < 1.0E-6F) {
         return;
      }
      sx /= len;
      sy /= len;
      sz /= len;
      vertex(vc, m, p0.x - sx * w0, p0.y - sy * w0, p0.z - sz * w0, r, g, b, 0);
      vertex(vc, m, p0.x, p0.y, p0.z, r, g, b, a0);
      vertex(vc, m, p1.x, p1.y, p1.z, r, g, b, a1);
      vertex(vc, m, p1.x - sx * w1, p1.y - sy * w1, p1.z - sz * w1, r, g, b, 0);
      vertex(vc, m, p0.x, p0.y, p0.z, r, g, b, a0);
      vertex(vc, m, p0.x + sx * w0, p0.y + sy * w0, p0.z + sz * w0, r, g, b, 0);
      vertex(vc, m, p1.x + sx * w1, p1.y + sy * w1, p1.z + sz * w1, r, g, b, 0);
      vertex(vc, m, p1.x, p1.y, p1.z, r, g, b, a1);
   }

   private static void put(BufferBuilder vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float u, float v, int r, int g, int b, int a) {
      vertex(vc, m, c.x + ax.x * u + bx.x * v, c.y + ax.y * u + bx.y * v, c.z + ax.z * u + bx.z * v, r, g, b, a);
   }

   private static void vertex(BufferBuilder vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
      vc.vertex(m, x, y, z).color(Mth.clamp(r, 0, 255), Mth.clamp(g, 0, 255), Mth.clamp(b, 0, 255), Mth.clamp(a, 0, 255)).endVertex();
   }
}
