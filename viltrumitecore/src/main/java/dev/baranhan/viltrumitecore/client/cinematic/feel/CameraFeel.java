package dev.baranhan.viltrumitecore.client.cinematic.feel;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicSettings;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.combat.feel.FeelMath;
import dev.baranhan.viltrumitecore.combat.feel.ShakeMixer;
import dev.baranhan.viltrumitecore.combat.feel.ShakeProfile;
import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The single owner of camera shake in {@code ComputeCameraAngles} (wave1-22).
 *
 * <p>It adds the legacy {@link CameraShake} offsets (unchanged math, every old caller keeps its
 * exact look) and the profiled {@link ShakeMixer} layers, then applies the player's comfort
 * settings once: reduced motion scales both, drops roll and the FOV kick.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class CameraFeel {
   private static final ShakeMixer MIXER = new ShakeMixer();
   private static final float[] SAMPLE = new float[4];
   private static final float[] LEGACY = new float[3];
   @Nullable
   private static ClientLevel lastLevel;

   private CameraFeel() {
   }

   /** Personal shake (no distance falloff). */
   public static void shake(ShakeProfile profile, float trauma) {
      MIXER.add(profile, trauma);
   }

   /** Shake from a world point; {@code range <= 0} means personal (no falloff). */
   public static void shakeAt(@Nullable Vec3 at, ShakeProfile profile, float trauma, double range) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      if (at == null || !(range > 0.0)) {
         MIXER.add(profile, trauma);
         return;
      }
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      float k = FeelMath.falloff(eye.distanceTo(at), range);
      if (k > 0.0F) {
         MIXER.add(profile, trauma * k);
      }
   }

   public static int layers() {
      return MIXER.layers();
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         MIXER.clear();
         lastLevel = mc.level;
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      MIXER.tick();
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      MIXER.clear();
      lastLevel = null;
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      boolean legacy = CameraShake.legacyOffsets(event.getPartialTick(), LEGACY);
      if (!legacy && MIXER.layers() == 0) {
         return;
      }
      CinematicSettings settings = CinematicSettings.get();
      boolean reduced = settings.reduceCameraMotion;
      float motion = settings.motionScale();
      float pitch = LEGACY[0] * motion;
      float yaw = LEGACY[1] * motion;
      float roll = reduced ? 0.0F : LEGACY[2];
      if (MIXER.layers() > 0) {
         MIXER.sample(time(event.getPartialTick()), SAMPLE);
         float k = motion * FeelMath.sane(ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier, 3.0F);
         pitch += SAMPLE[0] * k;
         yaw += SAMPLE[1] * k;
         if (!reduced) {
            roll += SAMPLE[2] * k;
         }
      }
      event.setPitch(event.getPitch() + pitch);
      event.setYaw(event.getYaw() + yaw);
      event.setRoll(event.getRoll() + roll);
   }

   @SubscribeEvent
   public static void onFov(ViewportEvent.ComputeFov event) {
      if (!event.usedConfiguredFov() || MIXER.layers() == 0 || CinematicSettings.get().reduceCameraMotion) {
         return;
      }
      MIXER.sample(time(event.getPartialTick()), SAMPLE);
      if (SAMPLE[3] > 0.0F) {
         event.setFOV(event.getFOV() + SAMPLE[3]);
      }
   }

   private static float time(double partialTick) {
      return (float)(partialTick + (lastLevel == null ? 0L : lastLevel.getGameTime() % 100000L));
   }
}
