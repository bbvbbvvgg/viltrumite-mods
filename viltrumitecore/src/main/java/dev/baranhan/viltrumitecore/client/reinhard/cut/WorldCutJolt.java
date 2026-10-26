package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The BLAST jolt of the normal World Cut: one damped camera lurch, started on the same tick and by the same
 * admission as the strike voice and the explosion ({@link WorldCutScene}).
 *
 * It rides on top of the atmosphere rumble ({@link CutAtmosphere}, a fast 9-17 Hz jitter through
 * {@link CameraShake}) and is deliberately different from it: a slow 4 Hz swing that rises within one tick,
 * peaks in the first three ticks and is gone after {@link #LENGTH} ticks, so it reads as the hit of the
 * explosion and not as more rumble. It falls off linearly with the distance to the cut over the view range
 * and follows the same settings as the rumble: {@code reinhardWorldCutShake} (0 disables) and
 * {@code punchShakeMultiplier}. Caster and nearby players get it alike. One lurch at a time: a second kick
 * only replaces a live one when it is stronger. No allocation per frame.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class WorldCutJolt {
   /** Degrees at the source for a full envelope. */
   static final float PITCH = 6.0F, ROLL = 3.5F, YAW = 2.0F;
   static final double RANGE = ReinhardRules.WORLD_CUT_VIEW_RANGE;
   /** Ticks: rise time, decay time, hard end (with a short fade before it). */
   static final double RISE = .35, DECAY = 5.5, LENGTH = 24, FADE = 6;
   /** 4 Hz in radians per tick. */
   static final double OMEGA = 2 * Math.PI * 4 / 20.0;
   private static double start = Double.NaN;
   private static float gain;
   private static ClientLevel level;

   private WorldCutJolt() {
   }

   /** Starts the lurch for the camera's distance to {@code at}, on game tick {@code now}. */
   static void kick(Vec3 at, long now) {
      Minecraft mc = Minecraft.getInstance();
      float setting = ViltrumitePostProcessingConfig.INSTANCE.reinhardWorldCutShake;
      float strength = strength(mc.gameRenderer.getMainCamera().getPosition().distanceTo(at), setting);
      if (strength <= 0 || mc.level == null) {
         return;
      }
      if (!Double.isNaN(start) && mc.level == level && gain * envelope(now - start) >= strength) {
         return;
      }
      start = now;
      gain = strength;
      level = mc.level;
   }

   /** Pure: the distance falloff times the World Cut shake setting (clamped to 0..2, NaN counts as 1). */
   static float strength(double distance, float setting) {
      float s = Float.isFinite(setting) ? Mth.clamp(setting, 0.0F, 2.0F) : 1.0F;
      return CameraShake.falloff(distance, RANGE) * s;
   }

   /** Pure: the lurch envelope {@code age} ticks after the kick; 0 before it, after LENGTH and for NaN. */
   static float envelope(double age) {
      if (!(age >= 0) || age >= LENGTH) {
         return 0.0F;
      }
      double rise = 1 - Math.exp(-age / RISE);
      double end = Math.min(1, (LENGTH - age) / FADE);
      return (float)(rise * Math.exp(-age / DECAY) * end);
   }

   /** Pure: pitch offset in degrees for a full-strength kick (positive looks down first: the blow presses down). */
   static float pitch(double age) {
      return PITCH * envelope(age) * (float)Math.cos(OMEGA * age);
   }

   static float roll(double age) {
      return ROLL * envelope(age) * (float)Math.sin(OMEGA * 1.37 * age + .6);
   }

   static float yaw(double age) {
      return YAW * envelope(age) * (float)Math.sin(OMEGA * .83 * age + 1.9);
   }

   static void clear() {
      start = Double.NaN;
      level = null;
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (Double.isNaN(start)) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.level != level) {
         clear();
         return;
      }
      double age = mc.level.getGameTime() - start + event.getPartialTick();
      if (age >= LENGTH) {
         clear();
         return;
      }
      float multiplier = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier;
      float k = gain * (Float.isFinite(multiplier) ? Math.max(0.0F, multiplier) : 1.0F);
      event.setPitch(event.getPitch() + k * pitch(age));
      event.setYaw(event.getYaw() + k * yaw(age));
      event.setRoll(event.getRoll() + k * roll(age));
   }
}
