package dev.baranhan.viltrumitecore.client.scp106;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sky and light of the pocket dimension: no sky, no clouds, the fog colour and a lightmap tint of the zone the
 * player stands in (brown decay, black voids, green water, a pale false calm, the red domain, warm sanctuaries).
 */
public final class PocketEffects extends DimensionSpecialEffects {
   public PocketEffects() {
      super(Float.NaN, false, SkyType.NONE, false, false);
   }

   @Override
   public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
      return PocketClient.fogColor();
   }

   @Override
   public boolean isFoggyAt(int x, int z) {
      return true;
   }

   @Override
   public boolean renderClouds(ClientLevel level, int ticks, float partial, com.mojang.blaze3d.vertex.PoseStack stack, double camX, double camY,
      double camZ, org.joml.Matrix4f projection) {
      return true;
   }

   @Override
   public boolean renderSky(ClientLevel level, int ticks, float partial, com.mojang.blaze3d.vertex.PoseStack stack, net.minecraft.client.Camera camera,
      org.joml.Matrix4f projection, boolean foggy, Runnable setupFog) {
      return true;
   }

   @Override
   public boolean renderSnowAndRain(ClientLevel level, int ticks, float partial, net.minecraft.client.renderer.LightTexture light, double camX,
      double camY, double camZ) {
      return true;
   }

   @Override
   public void adjustLightmapColors(ClientLevel level, float partial, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX,
      int pixelY, Vector3f colors) {
      float[] t = PocketClient.tint();
      colors.set(colors.x() * t[0], colors.y() * t[1], colors.z() * t[2]);
   }
}
