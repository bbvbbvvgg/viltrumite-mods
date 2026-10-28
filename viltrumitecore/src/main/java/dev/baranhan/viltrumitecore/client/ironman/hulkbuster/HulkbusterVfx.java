package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Heavy Mark 48 steps: pixel dust at the feet, once per stride (the step sound is the
 * server's HulkbusterSounds.Cue.STEP: no client duplicate). The stride comes from the walk
 * distance (HulkbusterClips.STRIDE_UNITS), so it stays in step with the walk clip.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterVfx {
   private static final int MAX_MOTES = 240;
   private static final int DUST_PER_STEP = 8;
   private static final Random RANDOM = new Random();
   private static final List<Mote> MOTES = new ArrayList<>();
   private static final Map<Integer, Integer> STRIDE = new HashMap<>();

   private HulkbusterVfx() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.isPaused()) {
         return;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null) {
            STRIDE.remove(player.getId());
            continue;
         }

         int cycle = (int)Math.floor(player.walkAnimation.position() / (HulkbusterClips.STRIDE_UNITS / 2.0));
         Integer last = STRIDE.put(player.getId(), cycle);
         boolean big = HulkbusterView.phase(snapshot) == HulkbusterLayer.Phase.ACTIVE;
         if (last != null && last != cycle && big && player.onGround() && player.walkAnimation.speed() > 0.15F) {
            step(level, player.position());
         }
      }

      MOTES.removeIf(mote -> ++mote.age >= mote.life);
      for (Mote mote : MOTES) {
         mote.x += mote.vx;
         mote.y += mote.vy;
         mote.z += mote.vz;
         mote.vx *= 0.92;
         mote.vz *= 0.92;
         mote.vy *= 0.9;
      }
   }

   private static void step(ClientLevel level, Vec3 feet) {
      for (int i = 0; i < DUST_PER_STEP; i++) {
         double angle = RANDOM.nextDouble() * Math.PI * 2.0;
         double speed = 0.04 + RANDOM.nextDouble() * 0.05;
         if (MOTES.size() >= MAX_MOTES) {
            MOTES.remove(0);
         }

         MOTES.add(new Mote(feet.x + Math.cos(angle) * 0.4, feet.y + 0.05, feet.z + Math.sin(angle) * 0.4,
            Math.cos(angle) * speed, 0.02 + RANDOM.nextDouble() * 0.03, Math.sin(angle) * speed, 14 + RANDOM.nextInt(10)));
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MOTES.isEmpty()) {
         return;
      }

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
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Mote mote : MOTES) {
            float fade = 1.0F - (float)mote.age / (float)mote.life;
            PixelVfx.billboardPixel(buffer, cameraPos, camera, new Vec3(mote.x, mote.y, mote.z), 0.05F + 0.03F * fade, 200, 170, 130, (int)(200 * fade));
         }

         tessellator.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      MOTES.clear();
      STRIDE.clear();
   }

   private static final class Mote {
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      final int life;
      int age;

      Mote(double x, double y, double z, double vx, double vy, double vz, int life) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.life = life;
      }
   }
}
