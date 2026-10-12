package dev.baranhan.viltrumitecore.client.reinhard.reid;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
 * Reid in hand (spec §7.3): a gold body shell shimmer (alpha only, skill §8) with rising gold
 * motes around every Reinhard whose Reid is drawn, and a warm gold sky over him for players
 * within {@link #TINT_RANGE} blocks (weight falls with distance, low priority so the draw scene
 * wins). Gone when the flag clears. Pure rules + client handlers.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidAura {
   public static final double TINT_RANGE = 64.0;
   public static final int TINT_PRIORITY = 1;
   public static final SkyTint.Tint WARM = new SkyTint.Tint(1.12F, 0.98F, 0.7F, 0.05F, 1.0F);
   private static final Object OWNER = new Object();
   private static final int MOTES = 14;
   private static float weight;
   private static boolean pushed;
   private static ClientLevel lastLevel;

   private ReidAura() {
   }

   /** Pure: the aura shows only while Reid is out and settled in the hand (not drawing / sheathing). */
   public static boolean active(int flags) {
      return ReinhardFlags.has(flags, ReinhardFlags.REID_DRAWN);
   }

   /** Pure: sky tint weight at a distance from the drawn Reinhard, 1 at him, 0 at {@link #TINT_RANGE}. */
   public static float tintWeight(double distance) {
      return (float)Math.max(0.0, Math.min(1.0, 1.0 - distance / TINT_RANGE));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         // SkyTintEvents clears every source on a level change.
         lastLevel = client.level;
         pushed = false;
      }

      float best = 0.0F;
      if (client.level != null && client.player != null) {
         for (Player player : client.level.players()) {
            HeroPublicSnapshot snapshot = ReinhardView.of(player);
            if (snapshot != null && active(snapshot.heroFlags())) {
               best = Math.max(best, tintWeight(player.distanceTo(client.player)));
            }
         }
      }

      weight = best;
      if (best > 0.0F && !pushed) {
         SkyTint.INSTANCE.push(OWNER, TINT_PRIORITY, () -> SkyTint.mix(SkyTint.NONE, WARM, weight));
         pushed = true;
      } else if (best <= 0.0F && pushed) {
         SkyTint.INSTANCE.remove(OWNER);
         pushed = false;
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      List<Player> drawn = new ArrayList<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = ReinhardView.of(player);
         boolean ownFirstPerson = player == client.player && client.options.getCameraType() == CameraType.FIRST_PERSON;
         if (snapshot != null && !player.isInvisible() && !ownFirstPerson && active(snapshot.heroFlags())) {
            drawn.add(player);
         }
      }

      if (drawn.isEmpty()) {
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
         for (Player player : drawn) {
            float seconds = (player.tickCount + partialTick) / 20.0F;
            Vec3 feet = player.getPosition(partialTick);
            Vec3 center = feet.add(0.0, player.getBbHeight() * 0.5, 0.0);
            PixelVfx.bodyShell(buffer, cameraPos, camera, center, player.getBbWidth() * 0.9F, player.getBbHeight() * 0.62F, seconds, 255, 205, 80, 70);
            int seed = player.getUUID().hashCode();
            for (int i = 0; i < MOTES; i++) {
               double phase = ((seconds * 0.4 + i / (double)MOTES + (seed & 0xFF) / 255.0) % 1.0);
               double angle = i * 2.399963 + seconds * 0.8;
               double r = player.getBbWidth() * (0.55 + 0.25 * Math.sin(i * 1.7));
               Vec3 at = feet.add(Math.cos(angle) * r, phase * player.getBbHeight() * 1.2, Math.sin(angle) * r);
               int alpha = (int)(200.0 * Math.sin(phase * Math.PI));
               PixelVfx.billboardPixel(buffer, cameraPos, camera, at, 0.045F, 255, 214, 90, alpha);
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
}