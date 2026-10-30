package dev.baranhan.viltrumitecore.client.ironman;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.anim.render.RevealMask;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Pixel VFX of the suit (animation-system §8, PixelVfx vocabulary):
 * <ul>
 *    <li>nano wave — cyan scale pixels on the reveal front (same distance
 *    field as the skin frames), a stream of nanites between the reactor and
 *    the front, a reactor flash at the start of deploy / end of retract;</li>
 *    <li>hover ground light — a soft repulsor glow and a dust ring on the
 *    ground when it is within 4 blocks below a hovering suit.</li>
 * </ul>
 * Pure function of the synced snapshot: a relog mid-wave shows the current
 * wave state, nothing is cached per player.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class NanoWaveVfx {
   private static final float BAND = 0.06F;
   private static final double GROUND_RANGE = 4.0;
   private static float[][] samples;

   private NanoWaveVfx() {
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      List<AbstractClientPlayer> wave = new ArrayList<>();
      List<AbstractClientPlayer> hover = new ArrayList<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null || player.isInvisible() || !(player instanceof AbstractClientPlayer clientPlayer)) {
            continue;
         }

         if (IronManView.transitioning(snapshot)) {
            boolean localFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
            if (!localFirstPerson) {
               wave.add(clientPlayer);
            }
         } else if (ThrusterFlames.mode(clientPlayer, snapshot) == ThrusterFlames.Mode.HOVER) {
            hover.add(clientPlayer);
         }
      }

      if (wave.isEmpty() && hover.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.levelView(modelViewStack, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (AbstractClientPlayer player : wave) {
            drawWave(buffer, cameraPos, camera, player, IronManView.of(player), partialTick);
         }

         for (AbstractClientPlayer player : hover) {
            drawGround(buffer, cameraPos, level, player, partialTick);
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

   private static void drawWave(BufferBuilder buffer, Vec3 cameraPos, Camera camera, AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      if (snapshot == null) {
         return;
      }

      float progress = IronManView.reveal(snapshot, partialTick);
      boolean deploying = IronManView.deploying(snapshot);
      Vec3 base = player.getPosition(partialTick);
      float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
      float time = player.tickCount + partialTick;
      float[][] points = samples();
      Vec3 reactor = toWorld(base, yaw, RevealMask.REACTOR[0], RevealMask.REACTOR[1], RevealMask.REACTOR[2] - 0.6F);
      for (int i = 0; i < points.length; i++) {
         float[] p = points[i];
         float d = p[3];
         float off = Math.abs(d - progress);
         if (off > BAND) {
            continue;
         }

         float k = 1.0F - off / BAND;
         // Scales lift off the surface a little and shimmer.
         float lift = 0.6F + 0.5F * (float)Math.sin(time * 0.9F + i);
         float len = (float)Math.sqrt(p[0] * p[0] + p[2] * p[2]) + 0.001F;
         float push = 0.3F + 0.5F * lift;
         Vec3 pos = toWorld(base, yaw, p[0] + p[0] / len * push, p[1], p[2] + p[2] / len * push);
         boolean bright = (i & 3) == 0;
         PixelVfx.billboardPixel(buffer, cameraPos, camera, pos, bright ? 0.022F : 0.016F, bright ? 200 : 90, bright ? 250 : 215, 255, (int)(220 * k));
         // Nanites streaming between the reactor and the front.
         if (i % 5 == 0) {
            float f = (time * 0.15F + i * 0.618F) % 1.0F;
            float along = deploying ? f : 1.0F - f;
            Vec3 stream = reactor.lerp(pos, along);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, stream, 0.012F, 120, 230, 255, (int)(150 * k));
         }
      }

      // Deploy starts and retract ends at the reactor: flash while the front is near it.
      float flash = 1.0F - progress / 0.15F;
      if (flash > 0.0F) {
         PixelVfx.crossGlow(buffer, cameraPos, camera, reactor, 0.05F + 0.05F * flash, 170, 240, 255, (int)(255 * Math.min(1.0F, flash)));
      }
   }

   /** Repulsor light and an expanding dust ring on the ground under a hovering suit. */
   private static void drawGround(BufferBuilder buffer, Vec3 cameraPos, ClientLevel level, AbstractClientPlayer player, float partialTick) {
      Vec3 feet = player.getPosition(partialTick);
      BlockHitResult hit = level.clip(new ClipContext(feet, feet.subtract(0.0, GROUND_RANGE, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return;
      }

      double height = feet.y - hit.getLocation().y;
      float strength = (float)(1.0 - height / GROUND_RANGE);
      if (strength <= 0.0F) {
         return;
      }

      Vec3 centre = hit.getLocation().add(0.0, 0.02, 0.0);
      float time = player.tickCount + partialTick;
      float pixel = 0.0625F;
      // Glow disc: pixel grid with a radial falloff.
      for (int gx = -12; gx <= 12; gx++) {
         for (int gz = -12; gz <= 12; gz++) {
            float r = (float)Math.sqrt(gx * gx + gz * gz) / 12.0F;
            if (r > 1.0F) {
               continue;
            }

            int alpha = (int)(90 * strength * (1.0F - r) * (1.0F - r));
            if (alpha > 2) {
               flatPixel(buffer, cameraPos, centre.add(gx * pixel, 0.0, gz * pixel), pixel * 0.5F, 90, 200, 255, alpha);
            }
         }
      }

      // Dust ring rolling outwards.
      float phase = (time * 0.06F) % 1.0F;
      float radius = 0.4F + phase * 1.4F;
      int dots = 28;
      for (int i = 0; i < dots; i++) {
         double a = i * Math.PI * 2.0 / dots + time * 0.01;
         Vec3 pos = centre.add(Math.cos(a) * radius, 0.01, Math.sin(a) * radius);
         flatPixel(buffer, cameraPos, pos, pixel * 0.6F, 150, 140, 120, (int)(110 * strength * (1.0F - phase)));
      }
   }

   private static void flatPixel(BufferBuilder buffer, Vec3 cameraPos, Vec3 pos, float half, int r, int g, int b, int alpha) {
      float x = (float)(pos.x - cameraPos.x);
      float y = (float)(pos.y - cameraPos.y);
      float z = (float)(pos.z - cameraPos.z);
      buffer.vertex(x - half, y, z - half).color(r, g, b, alpha).endVertex();
      buffer.vertex(x - half, y, z + half).color(r, g, b, alpha).endVertex();
      buffer.vertex(x + half, y, z + half).color(r, g, b, alpha).endVertex();
      buffer.vertex(x + half, y, z - half).color(r, g, b, alpha).endVertex();
   }

   /** Model pixel point (y up from the feet, -z front) to world space with the body yaw. */
   private static Vec3 toWorld(Vec3 base, float bodyYaw, float mx, float my, float mz) {
      double x = -mx / 16.0;
      double z = mz / 16.0;
      double theta = Math.toRadians(180.0F - bodyYaw);
      double cos = Math.cos(theta);
      double sin = Math.sin(theta);
      return base.add(x * cos + z * sin, my / 16.0, -x * sin + z * cos);
   }

   /** One surface point per skin texel (base layer), with its reveal distance. */
   private static float[][] samples() {
      if (samples == null) {
         RevealMask.Field field = RevealMask.field();
         List<float[]> out = new ArrayList<>();
         float[] p = new float[3];
         for (int y = 0; y < RevealMask.SIZE; y++) {
            for (int x = 0; x < RevealMask.SIZE; x++) {
               float d = field.at(x, y);
               // Base layer only (overlay rows 32-47 and the 48+ overlay quadrants repeat it).
               boolean overlay = y >= 32 && y < 48 || x >= 32 && y < 16 || y >= 48 && (x < 16 || x >= 48);
               if (!Float.isNaN(d) && !overlay) {
                  field.point(x, y, p);
                  out.add(new float[]{p[0], p[1], p[2], d});
               }
            }
         }

         samples = out.toArray(new float[0][]);
      }

      return samples;
   }
}
