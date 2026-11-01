package dev.baranhan.viltrumitecore.worldevent.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.meteor.fire.FireRules;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure rules of the Meteor Rain 2.0 city fire and the red-sky range. */
class CityFireRulesTest {
   @Test
   void inertNeverBurnsAndMaterialsAreOrdered() {
      assertEquals(0.0F, FireRules.maxHeat(FireRules.INERT));
      assertEquals(0.0F, FireRules.catchFactor(FireRules.INERT));
      assertEquals(0, FireRules.fuelTicks(FireRules.INERT, new Random(1)));
      assertTrue(FireRules.catchFactor(FireRules.FLAMMABLE) > FireRules.catchFactor(FireRules.MINERAL));
      assertTrue(FireRules.catchFactor(FireRules.MINERAL) > FireRules.catchFactor(FireRules.GROUND));
      // concrete and stone facades still carry the fire (their contents burn)
      assertTrue(FireRules.maxHeat(FireRules.MINERAL) > FireRules.BRAND_HEAT);
   }

   @Test
   void mergingFiresBurnHotterButCapped() {
      float alone = FireRules.targetHeat(FireRules.MINERAL, 0);
      float merged = FireRules.targetHeat(FireRules.MINERAL, 4);
      assertTrue(merged > alone);
      assertEquals(1.0F, FireRules.targetHeat(FireRules.FLAMMABLE, 50), 1.0E-6F);
   }

   @Test
   void heatEasesWithoutOvershoot() {
      float h = 0.0F;
      for (int i = 0; i < 2000; i++) {
         h = FireRules.heatStep(h, 0.8F, 20);
         assertTrue(h <= 0.8F + 1.0E-6F);
      }
      assertEquals(0.8F, h, 0.01F);
      assertEquals(0.3F, FireRules.heatStep(0.3F, 0.9F, 0), 1.0E-6F);
   }

   @Test
   void chancesStayInRangeAndBrandsNeedHeat() {
      for (float heat = 0.0F; heat <= 1.0F; heat += 0.05F) {
         for (int dt : new int[]{0, 1, 20, 200, 100000}) {
            float s = FireRules.spreadChance(heat, dt);
            float b = FireRules.brandChance(heat, dt);
            float g = FireRules.shatterChance(heat, dt);
            assertTrue(s >= 0.0F && s <= 1.0F);
            assertTrue(b >= 0.0F && b <= 1.0F);
            assertTrue(g >= 0.0F && g <= 1.0F);
         }
      }
      assertEquals(0.0F, FireRules.brandChance(FireRules.BRAND_HEAT - 0.01F, 1000));
      assertEquals(0.0F, FireRules.shatterChance(0.4F, 1000));
   }

   @Test
   void spreadClimbsMoreThanItFalls() {
      Random random = new Random(42);
      int up = 0;
      int down = 0;
      for (int i = 0; i < 20000; i++) {
         int[] o = FireRules.spreadOffset(random);
         assertFalse(o[0] == 0 && o[1] == 0 && o[2] == 0);
         assertTrue(Math.abs(o[0]) <= 2 && Math.abs(o[2]) <= 2 && o[1] >= -1 && o[1] <= 3);
         if (o[1] > 0) {
            up++;
         } else if (o[1] < 0) {
            down++;
         }
      }
      assertTrue(up > down * 5, up + " up vs " + down + " down");
   }

   @Test
   void brandsFlyAcrossAStreetButNotAcrossTheCity() {
      Random random = new Random(7);
      for (int i = 0; i < 1000; i++) {
         double d = FireRules.brandDistance(random.nextDouble() * 2.0 - 1.0, random);
         assertTrue(d >= FireRules.BRAND_MIN && d <= FireRules.BRAND_MAX * 1.3);
         double a = FireRules.brandAngle(1.0, random);
         assertTrue(Math.abs(a - 1.0) <= Math.toRadians(75.0) + 1.0E-9);
      }
      assertTrue(FireRules.brandFlightTicks(16.0) > FireRules.brandFlightTicks(5.0));
   }

   @Test
   void zonesAndDangerAreBounded() {
      assertEquals(0.0F, FireRules.zoneIntensity(0, 1.0F));
      assertTrue(FireRules.zoneIntensity(40, 1.0F) <= 1.0F);
      assertTrue(FireRules.zoneIntensity(30, 0.9F) > FireRules.zoneIntensity(5, 0.9F));
      assertEquals(4.0F, FireRules.dangerRadius(0));
      assertEquals(20.0F, FireRules.dangerRadius(100000));
   }

   @Test
   void budgetsAreSane() {
      assertTrue(FireRules.WRITES_PER_TICK <= 64);
      assertTrue(FireRules.VISITS_PER_TICK <= 64);
      assertTrue(FireRules.MAX_SPOTS <= 4096);
      assertTrue(FireRules.IMPACT_SPOTS_MAX <= FireRules.IMPACT_WRITES);
   }

   @Test
   void redSkyRangeIsHorizontal() {
      Vec3 owner = new Vec3(0.0, 70.0, 0.0);
      assertTrue(MeteorRainSky.inRange(owner, new Vec3(100.0, 300.0, 100.0)));
      assertTrue(MeteorRainSky.inRange(owner, new Vec3(MeteorRainSky.RANGE, 0.0, 0.0)));
      assertFalse(MeteorRainSky.inRange(owner, new Vec3(MeteorRainSky.RANGE + 1.0, 70.0, 0.0)));
   }
}
