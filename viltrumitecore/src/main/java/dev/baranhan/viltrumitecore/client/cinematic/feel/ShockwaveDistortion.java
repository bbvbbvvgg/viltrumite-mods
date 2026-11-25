package dev.baranhan.viltrumitecore.client.cinematic.feel;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicSettings;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.combat.feel.FeelMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Air distortion rings of the cinematic combat (wave1-22). No own post chain: the two strongest
 * live rings feed the {@code FeelWaveA}/{@code FeelWaveB} uniforms of the shared
 * {@code dash_impact} pass (GameRendererDashMixin), which includes them in its early-out.
 * At most {@link #MAX_WAVES} rings live at once, only within {@link #MAX_DISTANCE} blocks, and
 * reduced camera motion turns them off.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ShockwaveDistortion {
   public static final int MAX_WAVES = 4;
   public static final double MAX_DISTANCE = 64.0;
   /** Final ring radius in screen heights. */
   public static final float MAX_RADIUS = 0.9F;
   private static final List<Wave> WAVES = new ArrayList<>();
   private static final float[] OUT = new float[8];
   @Nullable
   private static ClientLevel lastLevel;

   private record Wave(Vec3 at, float strength, int duration, long start) {
   }

   private ShockwaveDistortion() {
   }

   public static void spawn(@Nullable Vec3 at, float strength, int durationTicks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || at == null || !(strength > 0.0F) || CinematicSettings.get().reduceCameraMotion) {
         return;
      }
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      if (eye.distanceToSqr(at) > MAX_DISTANCE * MAX_DISTANCE) {
         return;
      }
      if (WAVES.size() >= MAX_WAVES) {
         WAVES.remove(0);
      }
      WAVES.add(new Wave(at, Math.min(1.5F, strength), Mth.clamp(durationTicks, 4, 60), mc.level.getGameTime()));
   }

   public static int live() {
      return WAVES.size();
   }

   /**
    * Uniform values for this frame: {cx, cy, radius, strength} of ring A then ring B, centre in
    * post texture coordinates (y up). All zero when nothing is visible. Shared array: read only.
    */
   public static float[] post(float partialTick) {
      Arrays.fill(OUT, 0.0F);
      Minecraft mc = Minecraft.getInstance();
      if (WAVES.isEmpty() || mc.level == null || mc.level != lastLevel) {
         return OUT;
      }
      int w = mc.getWindow().getGuiScaledWidth();
      int h = mc.getWindow().getGuiScaledHeight();
      if (w <= 0 || h <= 0) {
         return OUT;
      }
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      long now = mc.level.getGameTime();
      for (Wave wave : WAVES) {
         float progress = ((now - wave.start) + partialTick) / wave.duration;
         float s = FeelMath.waveStrength(progress) * wave.strength * FeelMath.falloff(eye.distanceTo(wave.at), MAX_DISTANCE);
         if (s <= 0.001F) {
            continue;
         }
         ScreenProjector.Point p = ScreenProjector.project(wave.at, w, h);
         if (p == null || p.behind()) {
            continue;
         }
         float radius = FeelMath.waveRadius(progress, MAX_RADIUS);
         if (s > OUT[3]) {
            System.arraycopy(OUT, 0, OUT, 4, 4);
            write(0, p.x() / w, 1.0F - p.y() / h, radius, s);
         } else if (s > OUT[7]) {
            write(4, p.x() / w, 1.0F - p.y() / h, radius, s);
         }
      }
      return OUT;
   }

   private static void write(int base, float x, float y, float radius, float strength) {
      OUT[base] = x;
      OUT[base + 1] = y;
      OUT[base + 2] = radius;
      OUT[base + 3] = strength;
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         WAVES.clear();
         lastLevel = mc.level;
      }
      if (mc.level == null || WAVES.isEmpty()) {
         return;
      }
      long now = mc.level.getGameTime();
      WAVES.removeIf(wave -> now - wave.start >= wave.duration || now < wave.start);
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      WAVES.clear();
      lastLevel = null;
   }
}
