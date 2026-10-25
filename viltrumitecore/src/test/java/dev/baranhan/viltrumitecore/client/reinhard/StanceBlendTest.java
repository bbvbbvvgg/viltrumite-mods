package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import org.junit.jupiter.api.Test;

class StanceBlendTest {
   private static final long MS = 1_000_000L;

   @Test
   void blendReachesTarget() {
      Object player = new Object();
      StanceBlend.weights(player, ReinhardMode.RESTRAINED, 0L);
      float[] w = null;
      for (int i = 1; i <= 60; i++) {
         w = StanceBlend.weights(player, ReinhardMode.KNIGHT, i * 1000L / 60L * MS);
      }
      assertTrue(w[ReinhardMode.KNIGHT.ordinal()] > 0.999F, "after 1 s: " + w[ReinhardMode.KNIGHT.ordinal()]);
      StanceBlend.Mix mix = StanceBlend.mix(w, ReinhardMode.KNIGHT);
      assertEquals(ReinhardMode.KNIGHT, mix.to());
   }

   @Test
   void frameRateIndependent() {
      Object a = new Object();
      Object b = new Object();
      StanceBlend.weights(a, ReinhardMode.RESTRAINED, 0L);
      StanceBlend.weights(b, ReinhardMode.RESTRAINED, 0L);
      float[] wa = null;
      float[] wb = null;
      for (int i = 1; i <= 60; i++) {
         wa = StanceBlend.weights(a, ReinhardMode.HOLY_SWORD, i * 16L * MS);
      }
      for (int i = 1; i <= 240; i++) {
         wb = StanceBlend.weights(b, ReinhardMode.HOLY_SWORD, i * 4L * MS);
      }
      for (int m = 0; m < 3; m++) {
         assertEquals(wa[m], wb[m], 1.0E-3F);
      }
   }

   @Test
   void secondCallSameFrameDoesNotAdvance() {
      Object player = new Object();
      StanceBlend.weights(player, ReinhardMode.RESTRAINED, 0L);
      float[] first = StanceBlend.weights(player, ReinhardMode.KNIGHT, 50L * MS).clone();
      float[] second = StanceBlend.weights(player, ReinhardMode.KNIGHT, 50L * MS);
      assertArrayEquals(first, second, 0.0F);
      assertTrue(first[ReinhardMode.KNIGHT.ordinal()] > 0.0F && first[ReinhardMode.KNIGHT.ordinal()] < 1.0F);
   }

   @Test
   void dtClampedToTenthOfSecond() {
      Object player = new Object();
      StanceBlend.weights(player, ReinhardMode.RESTRAINED, 0L);
      float[] w = StanceBlend.weights(player, ReinhardMode.KNIGHT, 5000L * MS);
      assertEquals(1.0F - (float) Math.exp(-StanceBlend.K * 0.1), w[ReinhardMode.KNIGHT.ordinal()], 1.0E-5F);
   }

   @Test
   void resetOnHeroChange() {
      Object player = new Object();
      StanceBlend.weights(player, ReinhardMode.KNIGHT, 0L);
      StanceBlend.reset(player);
      float[] w = StanceBlend.weights(player, ReinhardMode.RESTRAINED, 10L * MS);
      assertEquals(1.0F, w[ReinhardMode.RESTRAINED.ordinal()], 1.0E-6F, "a fresh state starts on the target stance");
      assertEquals(0.0F, w[ReinhardMode.KNIGHT.ordinal()], 1.0E-6F);
   }

   @Test
   void mixPicksStrongestOther() {
      StanceBlend.Mix mix = StanceBlend.mix(new float[]{0.2F, 0.6F, 0.2F}, ReinhardMode.HOLY_SWORD);
      assertEquals(ReinhardMode.KNIGHT, mix.from());
      assertEquals(ReinhardMode.HOLY_SWORD, mix.to());
      assertEquals(0.25F, mix.weight(), 1.0E-6F);
   }

   private static BakedGeoModel model() {
      return BakedGeoModel.parse(JsonParser.parseString("""
         {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test","texture_width":16,"texture_height":16},
          "bones":[{"name":"a","pivot":[0,0,0]},{"name":"b","pivot":[0,0,0]}]}]}
         """).getAsJsonObject());
   }

   private static Animation clip(String bones) {
      return AnimationParser.parse(JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"c\":{\"loop\":true,\"animation_length\":1,\"bones\":{"
         + bones + "}}}}").getAsJsonObject()).get("c");
   }

   @Test
   void weightedOverlayLerpsFromCurrentPose() {
      BakedGeoModel model = model();
      AnimationController.seek(clip("\"a\":{\"rotation\":[0,0,10]},\"b\":{\"rotation\":[0,0,40]}"), model, 0.0);
      AnimationController.overlay(clip("\"a\":{\"rotation\":[0,0,30]}"), model, 0.0, 0.5F);
      GeoBone a = model.getBone("a");
      assertEquals(Math.toRadians(20.0), a.rotZ, 1.0E-5);
      assertEquals(Math.toRadians(40.0), model.getBone("b").rotZ, 1.0E-5, "bones outside the clip keep their pose");
   }

   @Test
   void additiveAddsToCurrentPose() {
      BakedGeoModel model = model();
      AnimationController.seek(clip("\"a\":{\"rotation\":[0,0,10],\"position\":[0,1,0]}"), model, 0.0);
      AnimationController.additive(clip("\"a\":{\"rotation\":[0,0,5],\"position\":[0,-3,2]}"), model, 0.0, 1.0F);
      GeoBone a = model.getBone("a");
      assertEquals(Math.toRadians(15.0), a.rotZ, 1.0E-5);
      assertEquals(-2.0F, a.posY, 1.0E-5F);
      assertEquals(2.0F, a.posZ, 1.0E-5F);
   }
}
