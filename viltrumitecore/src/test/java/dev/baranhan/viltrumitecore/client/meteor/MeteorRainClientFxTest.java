package dev.baranhan.viltrumitecore.client.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.particle.MeteorFlameParticle;
import dev.baranhan.viltrumitecore.client.render.MeteorRenderer;
import dev.baranhan.viltrumitecore.client.render.vfx.MeteorImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.MeteorTrailFx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure parts of the Meteor Rain 2.0 client: audio curves and voice limits, LOD, tail shape, sky and fog. */
class MeteorRainClientFxTest {
   @Test
   void meteorLoopsFadeWithDistance() {
      assertTrue(MeteorRainAudio.roarVolume(10.0) > MeteorRainAudio.roarVolume(100.0));
      assertEquals(0.0F, MeteorRainAudio.roarVolume(MeteorRainAudio.ROAR_RANGE));
      assertTrue(MeteorRainAudio.approachVolume(250.0) > 0.0F, "a meteor 250 blocks up is heard");
      assertEquals(0.0F, MeteorRainAudio.approachVolume(MeteorRainAudio.METEOR_RANGE));
      for (double d = 0.0; d < 400.0; d += 7.0) {
         assertTrue(MeteorRainAudio.roarVolume(d) <= 1.0F && MeteorRainAudio.approachVolume(d) <= 1.0F);
      }
   }

   @Test
   void dopplerRisesWhenClosing() {
      assertEquals(1.0F, MeteorRainAudio.doppler(0.0), 1.0E-6F);
      assertTrue(MeteorRainAudio.doppler(1.7) > 1.05F);
      assertTrue(MeteorRainAudio.doppler(-1.7) < 0.95F);
      assertTrue(MeteorRainAudio.doppler(100.0) <= 1.25F);
   }

   @Test
   void impactLayersFollowDistance() {
      float[] near = MeteorRainAudio.impactMix(10.0, 0);
      float[] far = MeteorRainAudio.impactMix(300.0, 0);
      assertTrue(near[0] > 0.9F && near[3] > 0.0F && near[4] > 0.0F, "crack, debris and rubble close by");
      assertEquals(0.0F, far[0]);
      assertEquals(0.0F, far[3]);
      assertEquals(0.0F, far[4]);
      assertTrue(far[1] > 0.0F, "far boom is heard");
      assertTrue(MeteorRainAudio.soundDelay(300.0) > MeteorRainAudio.soundDelay(20.0));
      assertTrue(MeteorRainAudio.soundDelay(10000.0) <= 28);
   }

   @Test
   void denseRainThinsTheImpacts() {
      float[] alone = MeteorRainAudio.impactMix(20.0, 0);
      float[] busy = MeteorRainAudio.impactMix(20.0, 3);
      float[] storm = MeteorRainAudio.impactMix(20.0, 8);
      assertEquals(0.0F, busy[4], "rubble tails drop first");
      assertTrue(busy[3] < alone[3]);
      assertTrue(storm[0] < alone[0] && storm[2] < alone[2]);
      assertEquals(0.0F, storm[3]);
   }

   @Test
   void fireRoarNeedsANearZone() {
      assertTrue(MeteorRainAudio.fireRoarVolume(1.0F, 12.0F, 5.0) > 0.5F);
      assertEquals(0.0F, MeteorRainAudio.fireRoarVolume(1.0F, 12.0F, 12.0 + MeteorRainAudio.FIRE_RANGE));
      assertEquals(0.0F, MeteorRainAudio.fireRoarVolume(0.0F, 12.0F, 5.0));
      assertTrue(MeteorRainAudio.fireRoarVolume(1.0F, 20.0F, 30.0) > MeteorRainAudio.fireRoarVolume(1.0F, 4.0F, 30.0));
   }

   @Test
   void whooshOnlyForANearComingMeteor() {
      assertTrue(MeteorRainAudio.whooshDue(10.0, 18.0, 1.6));
      assertFalse(MeteorRainAudio.whooshDue(60.0, 18.0, 1.6));
      assertFalse(MeteorRainAudio.whooshDue(10.0, -5.0, 1.6));
      assertFalse(MeteorRainAudio.whooshDue(10.0, 18.0, 0.1));
   }

   @Test
   void trailAndImpactDetailFallWithDistance() {
      assertEquals(1.0F, MeteorTrailFx.lod(10.0));
      assertTrue(MeteorTrailFx.lod(120.0) < 1.0F && MeteorTrailFx.lod(120.0) > 0.0F);
      assertEquals(0.0F, MeteorTrailFx.lod(MeteorTrailFx.MAX + 1.0));
      assertTrue(MeteorTrailFx.farScale(250.0) > MeteorTrailFx.farScale(20.0));
      assertTrue(MeteorTrailFx.farScale(10000.0) <= 3.0F);
      assertEquals(1.0F, MeteorImpactFx.detail(30.0));
      assertEquals(0.0F, MeteorImpactFx.detail(MeteorImpactFx.DRAW_RANGE + 1.0));
   }

   @Test
   void tailTapersAndShellPulses() {
      assertEquals(MeteorRenderer.TAIL_WIDTH * 0.5F, MeteorRenderer.tailWidth(0.0F), 1.0E-5F);
      assertEquals(0.0F, MeteorRenderer.tailWidth(1.0F), 1.0E-5F);
      float last = Float.MAX_VALUE;
      for (float t = 0.0F; t <= 1.0F; t += 0.1F) {
         float w = MeteorRenderer.tailWidth(t);
         assertTrue(w <= last);
         last = w;
      }
      for (float time = 0.0F; time < 400.0F; time += 0.37F) {
         float p = MeteorRenderer.pulse(time);
         assertTrue(p >= 0.6F && p <= 1.0F);
      }
   }

   @Test
   void flameCoolsFromWhiteToRed() {
      float[] hot = MeteorFlameParticle.ramp(0.0F);
      float[] cold = MeteorFlameParticle.ramp(1.0F);
      assertTrue(hot[2] > 0.7F, "white-hot start");
      assertTrue(cold[0] > cold[1] && cold[1] > cold[2], "dark red end");
   }

   @Test
   void zoneSmokeAndBrandArc() {
      assertTrue(CityFireClient.zoneRate(1.0F, 20.0F, 30.0) > CityFireClient.zoneRate(0.2F, 4.0F, 30.0));
      assertTrue(CityFireClient.zoneRate(1.0F, 48.0F, 10.0) <= 4.0F);
      Vec3 a = new Vec3(0.0, 70.0, 0.0);
      Vec3 b = new Vec3(12.0, 70.0, 0.0);
      assertEquals(a, CityFireClient.brandPos(a, b, 0.0F));
      assertEquals(b.x, CityFireClient.brandPos(a, b, 1.0F).x, 1.0E-9);
      assertTrue(CityFireClient.brandPos(a, b, 0.5F).y > 72.0);
   }

   @Test
   void redSkyKeepsDayAndNight() {
      SkyTint.Tint tint = MeteorRainClient.TINT;
      double[] day = SkyTint.apply(0.47, 0.65, 1.0, tint);
      double[] night = SkyTint.apply(0.02, 0.02, 0.05, tint);
      assertTrue(day[0] > day[2], "the blue day sky turns red");
      assertTrue(day[0] > night[0] * 4.0, "night stays dark");
      assertTrue(MeteorRainClient.PRIORITY < 4, "every hero scene wins over the rain");
      assertEquals(100.0F * (1.0F - MeteorRainClient.FOG_PULL), MeteorRainClient.fogFar(100.0F, 1.0F), 1.0E-4F);
      assertEquals(100.0F, MeteorRainClient.fogFar(100.0F, 0.0F), 1.0E-4F);
   }

   @Test
   void haloFadesOutAroundAPassingCamera() {
      assertEquals(0.0F, MeteorRenderer.haloFade(0.5F, 15.0F), 1.0E-6F);
      assertEquals(1.0F, MeteorRenderer.haloFade(40.0F, 15.0F), 1.0E-6F);
      assertTrue(MeteorRenderer.haloFade(6.0F, 15.0F) > 0.0F && MeteorRenderer.haloFade(6.0F, 15.0F) < 1.0F);
      assertTrue(MeteorRenderer.haloFade(8.0F, 15.0F) > MeteorRenderer.haloFade(6.0F, 15.0F));
   }
}
