package dev.baranhan.viltrumitecore.client.meteor;

import dev.baranhan.viltrumitecore.client.render.vfx.MeteorImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
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
 * Client side of {@link MeteorRainFxS2CPacket}. The red apocalyptic sky: while the server says a rain is
 * active around this player, a low-priority {@link SkyTint} ({@link #PRIORITY}, under every hero scene)
 * turns sky, clouds and fog to a smoky red that keeps the day / night brightness, and the fog draws in a
 * little (haze of smoke). It eases in and out over a few seconds; a missed "off" packet cannot strand it
 * because the server refreshes the state every five seconds and the tint lapses without that. Fire zones,
 * brands, far impacts and flares are handed to {@link CityFireClient}, {@link MeteorImpactFx} and
 * {@link MeteorRainAudio}.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class MeteorRainClient {
   /** Below Thanos (4), Thor (5), Chitauri (90), Reinhard (110+), Wish (112), Jinwoo (120): any scene wins. */
   public static final int PRIORITY = 3;
   /** The sky lapses when no refresh came for this long (the server refreshes every 100 ticks). */
   public static final int SKY_STALE = 260;
   /** Share by which the fog's far plane closes in at full haze. */
   public static final float FOG_PULL = 0.22F;
   /** The red sky: a warm multiplier, a little darker, and a smoky red cover over the vanilla hue. */
   public static final SkyTint.Tint TINT = new SkyTint.Tint(1.0F, 0.66F, 0.56F, 0.1F, 1.0F, 0.92F, 0.3F, 0.16F, 0.68F);

   private static final Object OWNER = new Object();
   private static boolean sky;
   private static int skyAt;
   private static float haze;
   private static int clock;
   private static ClientLevel lastLevel;

   private MeteorRainClient() {
   }

   public static void handle(MeteorRainFxS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      switch (packet.kind) {
         case MeteorRainFxS2CPacket.SKY -> setSky(packet.power > 0.0F);
         case MeteorRainFxS2CPacket.FIRE_ZONES -> CityFireClient.zones(packet.data);
         case MeteorRainFxS2CPacket.BRAND -> {
            if (packet.data.length >= 4) {
               CityFireClient.brand(packet.origin, new Vec3(packet.data[0], packet.data[1], packet.data[2]), Math.round(packet.data[3]));
            }
         }
         case MeteorRainFxS2CPacket.FAR_IMPACT -> {
            double d = packet.origin.distanceTo(mc.gameRenderer.getMainCamera().getPosition());
            MeteorRainAudio.impact(packet.origin, d);
            MeteorImpactFx.far(packet.origin);
         }
         case MeteorRainFxS2CPacket.FLARE -> CityFireClient.flare(packet.origin, packet.power);
         default -> {
         }
      }
   }

   private static void setSky(boolean on) {
      sky = on;
      skyAt = clock;
      if (on) {
         SkyTint.INSTANCE.push(OWNER, PRIORITY, MeteorRainClient::tint);
      } else {
         SkyTint.INSTANCE.remove(OWNER);
      }
   }

   /** The tint with a faint slow flicker, as if the light came from fires below. */
   static SkyTint.Tint tint() {
      float f = 0.03F * Mth.sin(clock * 0.07F) + 0.015F * Mth.sin(clock * 0.23F + 1.3F);
      return new SkyTint.Tint(TINT.r(), TINT.g() + f * 0.5F, TINT.b(), TINT.darken(), TINT.rain(), TINT.cr(), TINT.cg() + f, TINT.cb(), TINT.cover());
   }

   /** Pure: the fog far plane under a haze 0..1. */
   public static float fogFar(float far, float haze) {
      return far * (1.0F - FOG_PULL * Mth.clamp(haze, 0.0F, 1.0F));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         // SkyTintEvents clears every tint on a level change; the server re-sends the state in the new level
         lastLevel = mc.level;
         sky = false;
         haze = 0.0F;
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      clock++;
      if (sky && clock - skyAt > SKY_STALE) {
         setSky(false);
      }
      haze += ((sky ? 1.0F : 0.0F) - haze) * 0.02F;
      if (!sky && haze < 0.002F) {
         haze = 0.0F;
      }
   }

   @SubscribeEvent
   public static void onRenderFog(ViewportEvent.RenderFog event) {
      if (haze <= 0.0F || event.getType() != FogType.NONE) {
         return;
      }
      event.setFarPlaneDistance(fogFar(event.getFarPlaneDistance(), haze));
      event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), event.getFarPlaneDistance() * 0.55F));
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      sky = false;
      haze = 0.0F;
      SkyTint.INSTANCE.remove(OWNER);
   }

   /** The red sky is wanted now (debug and QA). */
   public static boolean skyActive() {
      return sky;
   }
}
