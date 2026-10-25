package dev.baranhan.viltrumitecore.client.reinhard.reid;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * Visuals of the Reid draw scene (spec §7.3, pixel style): every tick a strong
 * shake from the origin (falloff over the scene range), a dark sky with the
 * weather gone ({@link SkyTint}), the world hushed to 25% except the scene's own
 * sounds ({@link SoundDuck}); in the world a ring of dark ground cracks
 * (2 → 14 blocks), a white pillar of light to the clouds + 40 and a grey cloud
 * funnel opening to r 40. The final flash and ring are the server's
 * {@code HeroFx}. Outside the scene range: nothing (chat + far sound only).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidDrawSceneVfx {
   public static final int TINT_PRIORITY = 100;
   public static final SkyTint.Tint DARK = new SkyTint.Tint(0.55F, 0.55F, 0.65F, 0.75F, 0.0F);
   public static final float DUCK_TARGET = 0.25F;
   public static final int DUCK_FADE_TICKS = 20;
   private static final Set<ResourceLocation> OWN_SOUNDS = Set.of(
      new ResourceLocation("viltrumitecore", "reinhard.reid_rumble"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_choir"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_unsheathe"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_far"));
   private static final int CRACK_LINES = 14;
   private static final float CRACK_STEP = 0.5F;
   private static final float PILLAR_STEP = 0.75F;
   private static final float FUNNEL_RADIUS = 40.0F;
   private static final float SKY_ABOVE_CLOUDS = 40.0F;
   private static final Set<Integer> PUSHED = new HashSet<>();
   private static ClientLevel lastLevel;

   private ReidDrawSceneVfx() {
   }

   private static Object owner(int casterId) {
      return "reid_draw:" + casterId;
   }

   static boolean inRange(Minecraft client, ReidDrawScene.Active scene) {
      return client.player != null && (client.player.getId() == scene.casterId()
         || client.player.position().distanceTo(Vec3.atBottomCenterOf(scene.origin())) <= ReinhardRules.REID_SCENE_RANGE);
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         // Logout or dimension change: scenes of the old level end (SkyTint / SoundDuck clear themselves).
         lastLevel = level;
         ReidDrawScene.clear();
         PUSHED.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      long now = level.getGameTime();
      ReidDrawScene.prune(now);
      Set<Integer> live = new HashSet<>();
      for (ReidDrawScene.Active scene : ReidDrawScene.active()) {
         if (!inRange(client, scene)) {
            continue;
         }

         live.add(scene.casterId());
         float p = ReidDrawScene.progress(scene.startGameTime(), now, 0.0F);
         CameraShake.addAt(Vec3.atBottomCenterOf(scene.origin()), Mth.lerp(p, 2.5F, 4.0F), ReinhardRules.REID_SCENE_RANGE);
         if (PUSHED.add(scene.casterId())) {
            SkyTint.INSTANCE.push(owner(scene.casterId()), TINT_PRIORITY, () -> DARK);
            SoundDuck.INSTANCE.duck(owner(scene.casterId()), SoundSource.MASTER, DUCK_TARGET, DUCK_FADE_TICKS, OWN_SOUNDS);
         }
      }

      PUSHED.removeIf(id -> {
         if (live.contains(id)) {
            return false;
         }

         SkyTint.INSTANCE.remove(owner(id));
         SoundDuck.INSTANCE.release(owner(id));
         return true;
      });
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES || dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      List<ReidDrawScene.Active> scenes = new ArrayList<>();
      for (ReidDrawScene.Active scene : ReidDrawScene.active()) {
         if (inRange(client, scene)) {
            scenes.add(scene);
         }
      }

      if (scenes.isEmpty()) {
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
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         // Cracks: dark, normal alpha blend (additive would hide them).
         RenderSystem.defaultBlendFunc();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (ReidDrawScene.Active scene : scenes) {
            float t = (float)(level.getGameTime() - scene.startGameTime()) + partialTick;
            cracks(buffer, cameraPos, level, scene, ReidDrawScene.crackRadius(t));
         }
         tesselator.end();

         // Pillar and funnel: light, additive.
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (ReidDrawScene.Active scene : scenes) {
            float t = (float)(level.getGameTime() - scene.startGameTime()) + partialTick;
            if (t >= ReinhardRules.REID_CRACK_START) {
               float grow = Math.min(1.0F, (t - ReinhardRules.REID_CRACK_START) / (ReinhardRules.REID_DRAW_TICKS - ReinhardRules.REID_CRACK_START));
               float cloudY = cloudHeight(level, scene);
               pillar(buffer, cameraPos, camera, scene, cloudY + SKY_ABOVE_CLOUDS, t, grow);
               funnel(buffer, cameraPos, camera, scene, cloudY, t, grow);
               coronas(buffer,cameraPos,camera,scene,t,grow);
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

   /** Three rising halos contract around the blade at the final beat, keeping the silhouette readable. */
   private static void coronas(BufferBuilder buffer,Vec3 cameraPos,Camera camera,ReidDrawScene.Active scene,float ticks,float grow) {
      Vec3 origin=Vec3.atBottomCenterOf(scene.origin());
      float finish=Mth.clamp((ticks-96)/24,0,1);
      for(int band=0;band<3;band++) {
         float radius=(1.3F+band*.7F)*(1-.55F*finish);
         double height=.35+band*.8+grow*1.2;
         for(int point=0;point<48;point++) {
            double angle=point*Math.PI*2/48+ticks*.035*(band%2==0?1:-1);
            Vec3 at=origin.add(Math.cos(angle)*radius,height,Math.sin(angle)*radius);
            boolean gold=point%4==0;
            PixelVfx.billboardPixel(buffer,cameraPos,camera,at,gold?.065F:.04F,
               gold?255:185,gold?218:225,gold?135:255,(int)(grow*(90+110*finish)));
         }
      }
   }

   private static float cloudHeight(ClientLevel level, ReidDrawScene.Active scene) {
      float cloud = level.effects().getCloudHeight();
      return Float.isNaN(cloud) ? scene.origin().getY() + 100.0F : Math.max(cloud, scene.origin().getY() + 30.0F);
   }

   /** Jagged dark lines radiating from the origin, flat on the top of the ground. */
   private static void cracks(BufferBuilder buffer, Vec3 cameraPos, ClientLevel level, ReidDrawScene.Active scene, float radius) {
      if (radius <= 0.0F) {
         return;
      }

      double ox = scene.origin().getX() + 0.5;
      double oz = scene.origin().getZ() + 0.5;
      int seed = scene.origin().hashCode() * 31 + scene.casterId();
      for (int line = 0; line < CRACK_LINES; line++) {
         double base = (Math.PI * 2.0 * line) / CRACK_LINES + hash(seed, line) * 0.35;
         for (float d = 1.0F; d <= radius; d += CRACK_STEP) {
            double angle = base + Math.sin(d * 0.9 + line) * 0.12 + (hash(seed + line, (int)(d * 2.0F)) - 0.5) * 0.08;
            double x = ox + Math.cos(angle) * d;
            double z = oz + Math.sin(angle) * d;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            int alpha = (int)(210.0F * Math.min(1.0F, (radius - d + 1.0F) / 1.5F));
            float size = 0.09F + 0.05F * (1.0F - d / ReidDrawScene.CRACK_RADIUS_MAX);
            groundPixel(buffer, cameraPos, x, y + 0.02, z, size, 22, 14, 12, alpha);
         }
      }
   }

   /** A white column of light, its width pulsing. */
   private static void pillar(BufferBuilder buffer, Vec3 cameraPos, Camera camera, ReidDrawScene.Active scene, float topY, float t, float grow) {
      Vec3 base = Vec3.atBottomCenterOf(scene.origin());
      float height = (float)(topY - base.y) * Math.min(1.0F, grow * 3.0F);
      float width = (0.5F + 0.6F * grow) * (1.0F + 0.25F * Mth.sin(t * 0.6F));
      int alpha = (int)(90.0F + 120.0F * grow);
      for (float y = 0.0F; y <= height; y += PILLAR_STEP) {
         for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0 + y * 0.15 + t * 0.05;
            Vec3 at = base.add(Math.cos(a) * width, y, Math.sin(a) * width);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at, 0.22F, 255, 255, 245, alpha);
         }
         PixelVfx.billboardPixel(buffer, cameraPos, camera, base.add(0.0, y, 0.0), 0.35F, 255, 255, 255, alpha);
      }
   }

   /** A ring of grey cloud pixels opening around the pillar at cloud height. */
   private static void funnel(BufferBuilder buffer, Vec3 cameraPos, Camera camera, ReidDrawScene.Active scene, float cloudY, float t, float grow) {
      float radius = FUNNEL_RADIUS * grow;
      if (radius < 1.0F) {
         return;
      }

      Vec3 center = new Vec3(scene.origin().getX() + 0.5, cloudY, scene.origin().getZ() + 0.5);
      for (int ring = 0; ring < 3; ring++) {
         float r = radius * (0.7F + 0.15F * ring);
         int count = Math.max(12, (int)(r * 3.0F));
         for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0 * i) / count + t * 0.01 * (ring + 1);
            Vec3 at = center.add(Math.cos(a) * r, ring * 1.5 - Math.sin(a * 3.0 + t * 0.1) * 0.8, Math.sin(a) * r);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at, 1.1F, 150, 152, 160, 70);
         }
      }
   }

   private static void groundPixel(BufferBuilder buffer, Vec3 cameraPos, double x, double y, double z, float size, int r, int g, int b, int alpha) {
      float cx = (float)(x - cameraPos.x);
      float cy = (float)(y - cameraPos.y);
      float cz = (float)(z - cameraPos.z);
      buffer.vertex(cx - size, cy, cz - size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + size, cy, cz - size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + size, cy, cz + size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx - size, cy, cz + size).color(r, g, b, alpha).endVertex();
   }

   private static double hash(int seed, int i) {
      int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      return (h & 0xFFFF) / 65535.0;
   }
}