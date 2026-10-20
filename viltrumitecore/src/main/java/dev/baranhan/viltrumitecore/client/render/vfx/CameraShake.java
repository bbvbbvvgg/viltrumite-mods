package dev.baranhan.viltrumitecore.client.render.vfx;

import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Shared camera shake for hero impacts. Works through ComputeCameraAngles, so
 * it also runs with post-processing off or under a shader pack (the
 * dash_impact post shader is a separate, Viltrumite-only path). Scaled by the
 * existing {@code punchShakeMultiplier} config. The strongest live shake wins;
 * it decays by 14% per tick.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class CameraShake {
   private static final float MAX = 2.5F;
   private static float shake;
   private static ClientLevel lastLevel;

   private CameraShake() {
   }

   /** Shake with a linear falloff from {@code at} to the camera over {@code range} blocks. */
   public static void addAt(Vec3 at, float power, double range) {
      Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      add(falloff(listener.distanceTo(at), range) * power);
   }

   /** Shake that ignores distance (the local player's own action). */
   public static void add(float power) {
      shake = Math.max(shake, Math.min(MAX,power));
   }

   /** Opt-in cinematic contribution; does not raise the shared cap for other heroes. */
   public static void cinematicAt(Vec3 at,float power,double range) {
      float setting=ViltrumitePostProcessingConfig.INSTANCE.reinhardWorldCutShake;
      if(!Float.isFinite(setting)) setting=1;
      float value=power*falloff(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(at),range)
         *Math.max(0,Math.min(2,setting));
      shake=Math.max(shake,Math.min(8,value));
   }

   /** Pure: linear distance falloff, 1 at the source, 0 at range and beyond. */
   public static float falloff(double distance, double range) {
      return (float)Math.max(0.0, 1.0 - distance / range);
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         shake = 0.0F;
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      shake *= 0.86F;
      if (shake < 0.01F) {
         shake = 0.0F;
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (shake <= 0.001F) {
         return;
      }

      float multiplier = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier;
      float t = (float)(event.getPartialTick() + (lastLevel == null ? 0L : lastLevel.getGameTime() % 100000L));
      float amp = shake * multiplier;
      // Two detuned sines per axis: cheap, smooth, never periodic-looking.
      event.setPitch(event.getPitch() + amp * 1.6F * (Mth.sin(t * 2.9F) * 0.6F + Mth.sin(t * 5.3F + 1.3F) * 0.4F));
      event.setYaw(event.getYaw() + amp * 1.3F * (Mth.sin(t * 3.7F + 0.7F) * 0.6F + Mth.sin(t * 6.1F + 2.1F) * 0.4F));
      event.setRoll(event.getRoll() + amp * 1.1F * Mth.sin(t * 4.3F + 0.4F));
   }
}
