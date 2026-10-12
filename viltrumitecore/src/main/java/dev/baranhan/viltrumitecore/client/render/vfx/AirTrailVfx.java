package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Shared kick / throw FX (spec §9.3), pixel style, additive: a white sonic ring
 * that bursts out horizontally at the contact, and a white air trail that follows
 * a launched entity for {@link #TRAIL_TICKS} ticks (its last positions drawn as
 * fading pixel dots). Driven by {@code HeroFxS2CPacket} SONIC_RING / AIR_TRAIL.
 * Client only; the pure parts are unit tested.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class AirTrailVfx {
   static final int RING_LIFE_TICKS = 10;
   static final int RING_PIXELS = 56;
   static final float RING_START_RADIUS = 0.6F;
   static final float RING_END_RADIUS = 4.2F;
   static final int TRAIL_TICKS = 40;
   /** Positions kept per trail (one per tick). */
   static final int TRAIL_POINTS = 14;
   private static final float PIXEL_SIZE = 0.07F;
   private static final float TRAIL_DOT_SPACING = 0.35F;
   private static final int MAX_RINGS = 16;
   private static final int MAX_TRAILS = 16;
   private static final List<Ring> RINGS = new ArrayList<>();
   private static final Map<Integer, Trail> TRAILS = new HashMap<>();
   private static ClientLevel lastLevel;

   private AirTrailVfx() {
   }

   /** Pure: ring radius at a life fraction 0..1 (fast out, eases). */
   public static float ringRadius(float life) {
      float t = Math.max(0.0F, Math.min(1.0F, life));
      float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
      return RING_START_RADIUS + (RING_END_RADIUS - RING_START_RADIUS) * eased;
   }

   /** Pure: alpha of trail point {@code index} (0 = newest) of {@code count}, faded by the trail's age. */
   public static int trailAlpha(int index, int count, int age) {
      if (count <= 0 || index >= count || age >= TRAIL_TICKS) {
         return 0;
      }

      float along = 1.0F - (float) index / count;
      float life = 1.0F - (float) age / TRAIL_TICKS;
      return (int) (230.0F * along * Math.min(1.0F, life * 2.0F));
   }

   public static void ring(Vec3 at) {
      if (RINGS.size() >= MAX_RINGS) {
         RINGS.remove(0);
      }

      RINGS.add(new Ring(at));
   }

   public static void trail(int entityId) {
      if (TRAILS.size() >= MAX_TRAILS && !TRAILS.containsKey(entityId)) {
         return;
      }

      TRAILS.put(entityId, new Trail());
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         RINGS.clear();
         TRAILS.clear();
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      RINGS.removeIf(ring -> ++ring.age > RING_LIFE_TICKS);
      Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Trail> entry = it.next();
         Trail trail = entry.getValue();
         Entity entity = client.level.getEntity(entry.getKey());
         if (++trail.age > TRAIL_TICKS || entity == null || !entity.isAlive()) {
            it.remove();
            continue;
         }

         trail.points.addFirst(entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0));
         while (trail.points.size() > TRAIL_POINTS) {
            trail.points.removeLast();
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES || RINGS.isEmpty() && TRAILS.isEmpty()) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.rotateCamera(modelViewStack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Ring ring : RINGS) {
            float life = Math.min(1.0F, (ring.age + partialTick) / RING_LIFE_TICKS);
            int alpha = (int) (240.0F * (1.0F - life));
            if (alpha <= 0) {
               continue;
            }

            float r = ringRadius(life);
            for (int i = 0; i < RING_PIXELS; i++) {
               double angle = Math.PI * 2.0 * i / RING_PIXELS;
               PixelVfx.billboardPixel(buffer, cameraPos, camera, ring.center.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r), PIXEL_SIZE, 240, 248, 255, alpha);
            }
         }

         for (Trail trail : TRAILS.values()) {
            Vec3 previous = null;
            int index = 0;
            for (Vec3 point : trail.points) {
               if (previous != null) {
                  int alpha = trailAlpha(index, trail.points.size(), trail.age);
                  if (alpha > 0) {
                     PixelVfx.beamDots(buffer, cameraPos, camera, previous, point, TRAIL_DOT_SPACING, PIXEL_SIZE, 235, 245, 255, alpha);
                  }
               }

               previous = point;
               index++;
            }
         }

         tesselator.end();
      } finally {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static final class Ring {
      final Vec3 center;
      int age;

      Ring(Vec3 center) {
         this.center = center;
      }
   }

   private static final class Trail {
      final Deque<Vec3> points = new ArrayDeque<>();
      int age;
   }
}
