package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
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
 * Phoenix revive flames (Reinhard spec §4.2, pixel style, additive): gold-red pixel flames rise
 * around the body for {@link #BURN_TICKS} ticks. State-driven: a revive is the tick on which the
 * synced Phoenix cooldown (snapshot extraCooldowns[EXTRA_PHOENIX]) jumps up; the first value seen
 * for a player is only a baseline. The server adds the flash and the fire ring (HeroFx). Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class PhoenixFlameVfx {
   static final int BURN_TICKS = 30;
   private static final int FLAME_LIFE_TICKS = 14;
   private static final int FLAMES_PER_TICK = 6;
   private static final float FLAME_SIZE = 0.06F;
   private static final double RISE_PER_TICK = 0.07;
   private static final Map<Player, Integer> LAST_SECONDS = new WeakHashMap<>();
   private static final Map<Player, Integer> BURNING = new WeakHashMap<>();
   private static final List<Flame> FLAMES = new ArrayList<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static ClientLevel lastLevel;

   private PhoenixFlameVfx() {
   }

   /** Pure: a revive happened when the cooldown rose since the last seen value (null = first sight). */
   public static boolean revived(Integer lastSeconds, int seconds) {
      return lastSeconds != null && seconds > lastSeconds;
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         FLAMES.clear();
         LAST_SECONDS.clear();
         BURNING.clear();
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      FLAMES.removeIf(flame -> ++flame.age > FLAME_LIFE_TICKS);
      for (Player player : client.level.players()) {
         HeroPublicSnapshot snapshot = ReinhardView.of(player);
         if (snapshot == null) {
            LAST_SECONDS.remove(player);
            continue;
         }

         int[] extra = snapshot.extraCooldowns();
         int seconds = extra.length > ReinhardFlags.EXTRA_PHOENIX ? extra[ReinhardFlags.EXTRA_PHOENIX] : 0;
         if (revived(LAST_SECONDS.get(player), seconds)) {
            BURNING.put(player, BURN_TICKS);
         }

         LAST_SECONDS.put(player, seconds);
      }

      BURNING.entrySet().removeIf(entry -> {
         Player player = entry.getKey();
         if (player.isRemoved() || player.isInvisible()) {
            return true;
         }

         boolean firstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
         int count = firstPerson ? FLAMES_PER_TICK / 3 : FLAMES_PER_TICK;
         double half = player.getBbWidth() * 0.7;
         for (int i = 0; i < count; i++) {
            Vec3 at = player.position().add((RANDOM.nextDouble() * 2.0 - 1.0) * half, RANDOM.nextDouble() * player.getBbHeight() * 0.8,
               (RANDOM.nextDouble() * 2.0 - 1.0) * half);
            FLAMES.add(new Flame(at, RANDOM.nextFloat()));
         }

         entry.setValue(entry.getValue() - 1);
         return entry.getValue() <= 0;
      });
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES || FLAMES.isEmpty()) {
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
         for (Flame flame : FLAMES) {
            float life = Math.min(1.0F, (flame.age + partialTick) / FLAME_LIFE_TICKS);
            // Gold at birth, red as it rises and fades.
            int g = (int) (210.0F - 150.0F * life * (0.6F + 0.4F * flame.tint));
            int alpha = (int) (230.0F * (1.0F - life));
            if (alpha > 0) {
               Vec3 at = flame.pos.add(0.0, (flame.age + partialTick) * RISE_PER_TICK, 0.0);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, at, FLAME_SIZE * (1.0F - 0.5F * life), 255, g, 40, alpha);
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

   private static final class Flame {
      final Vec3 pos;
      final float tint;
      int age;

      Flame(Vec3 pos, float tint) {
         this.pos = pos;
         this.tint = tint;
      }
   }
}
