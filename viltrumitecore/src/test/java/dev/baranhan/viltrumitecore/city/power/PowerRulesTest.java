package dev.baranhan.viltrumitecore.city.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.city.power.PowerRules.Effect;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PowerRulesTest {
   @Test
   void geometryKeysRoundTrip() {
      for (int ix : new int[]{-70000, -1, 0, 1, 3, 123456}) {
         for (int iz : new int[]{-5, 0, 7, 99999}) {
            long k = PowerRules.key(ix, iz);
            assertEquals(ix, PowerRules.keyX(k));
            assertEquals(iz, PowerRules.keyZ(k));
         }
      }
      assertEquals(-1, PowerRules.cellIndex(-1, 0));
      assertEquals(0, PowerRules.cellIndex(47, 0));
      assertEquals(1, PowerRules.cellIndex(48, 0));
      assertEquals(-1, PowerRules.stationIndex(-1));
      assertEquals(0, PowerRules.stationIndex(2));
      assertEquals(1, PowerRules.stationIndex(3));
      assertEquals(0.0, PowerRules.distanceToCell(10, 10, 0, 0, 0, 0));
      assertEquals(2.0, PowerRules.distanceToCell(50, 10, 0, 0, 0, 0), 1e-9);
   }

   @Test
   void impactFallsOffAndIsCapped() {
      double reach = PowerRules.reach(12, 0.9);
      assertTrue(reach > 40 && reach <= (PowerRules.MAX_CELL_REACH + 0.5) * PowerRules.CELL);
      assertEquals((PowerRules.MAX_CELL_REACH + 0.5) * PowerRules.CELL, PowerRules.reach(10000, 1), 1e-9);
      assertEquals(1.0, PowerRules.falloff(0, reach), 1e-9);
      assertEquals(0.0, PowerRules.falloff(reach, reach));
      assertEquals(0.0, PowerRules.falloff(5, 0));
      double prev = 2;
      for (int d = 0; d < reach; d += 4) {
         double f = PowerRules.falloff(d, reach);
         assertTrue(f <= prev);
         prev = f;
      }
      assertEquals(Effect.DAMAGED, PowerRules.effect(0.9));
      assertEquals(Effect.DARK, PowerRules.effect(0.4));
      assertEquals(Effect.SURGE, PowerRules.effect(0.1));
      assertEquals(Effect.NONE, PowerRules.effect(0.01));
      // a meteor darkens its own cell and stutters lights further away: the full ladder is reachable
      assertEquals(Effect.DAMAGED, PowerRules.effect(0.9 * PowerRules.falloff(0, reach)));
      assertEquals(Effect.SURGE, PowerRules.effect(0.9 * PowerRules.falloff(reach * 0.75, reach)));
      assertTrue(PowerRules.waveDelay(0, 1) < 4);
      assertTrue(PowerRules.waveDelay(70, 1) >= 20);
   }

   @Test
   void burstsAndExplosionsAreBounded() {
      assertEquals(0.0, PowerRules.burstSeverity(3));
      assertTrue(PowerRules.burstSeverity(6) > 0);
      assertTrue(PowerRules.burstSeverity(100000) <= 0.75);
      assertTrue(PowerRules.explosionSeverity(1000000) <= 0.8);
      assertTrue(PowerRules.burstRadius(1000000) <= 16);
      assertTrue(PowerRules.explosionRadius(1000000) <= 14);
   }

   @Test
   void cascadeIsBoundedAndDeterministic() {
      for (long seed = 0; seed < 2000; seed++) {
         assertFalse(PowerRules.cascades(1.0, PowerRules.MAX_CASCADE_DEPTH + 1, seed));
         assertFalse(PowerRules.cascades(0.2, 1, seed));
         assertEquals(PowerRules.cascades(0.7, 1, seed), PowerRules.cascades(0.7, 1, seed));
         int d = PowerRules.cascadeDelay(seed);
         assertTrue(d >= 45 && d < 95);
      }
      assertTrue(PowerRules.cascadeLoad(1, 2) < PowerRules.cascadeLoad(1, 1));
      // a fresh grid mostly holds after one meteor-sized hit: well under half of the first neighbours trip
      int first = 0;
      for (long seed = 0; seed < 4000; seed++) {
         if (PowerRules.cascades(PowerRules.cascadeLoad(0.9, 1), 1, seed)) {
            first++;
         }
      }
      assertTrue(first > 800 && first < 1800, "first ring " + first);
      // worn neighbours (the caller adds their weakness) give way far more often
      int worn = 0;
      for (long seed = 0; seed < 4000; seed++) {
         if (PowerRules.cascades(PowerRules.cascadeLoad(0.9, 1) + 0.3, 1, seed)) {
            worn++;
         }
      }
      assertTrue(worn > first * 1.6, "worn " + worn);
      assertTrue(PowerRules.stationTrips(0.4, 0, 9));
      assertTrue(PowerRules.stationTrips(1.0, 6, 9));
      assertFalse(PowerRules.stationTrips(1.0, 5, 9));
      assertTrue(PowerRules.restoreDelay(Effect.DAMAGED, 1, 3) > PowerRules.restoreDelay(Effect.DARK, 1, 3));
      assertEquals(0, PowerRules.restoreDelay(Effect.SURGE, 1, 3));
      assertTrue(PowerRules.stationRestoreDelay(2, 5) > PowerRules.stationRestoreDelay(0, 5));
   }

   @Test
   void emergencyCircuitIsAboutOneInNine() {
      int n = 90000, hits = 0;
      for (int i = 0; i < n; i++) {
         if (PowerRules.emergency(i * 7919L + 31)) {
            hits++;
         }
      }
      double ratio = hits / (double)n;
      assertTrue(Math.abs(ratio - 1.0 / 9.0) < 0.01, "ratio " + ratio);
   }

   @Test
   void flickerEndsInTheRequestedStateAndActuallyFlickers() {
      for (long seed = 0; seed < 300; seed++) {
         for (boolean ends : new boolean[]{true, false}) {
            assertEquals(ends, PowerRules.flickerLit(PowerRules.FLICKER_TICKS, seed, ends));
            assertEquals(ends, PowerRules.flickerLit(PowerRules.FLICKER_TICKS + 50, seed, ends));
            assertEquals(!ends, PowerRules.flickerLit(0, seed, ends));
            int changes = 0;
            boolean last = PowerRules.flickerLit(0, seed, ends);
            for (int a = 1; a <= PowerRules.FLICKER_TICKS; a++) {
               boolean now = PowerRules.flickerLit(a, seed, ends);
               if (now != last) {
                  changes++;
               }
               last = now;
            }
            assertTrue(changes >= 3, "flicker too calm for seed " + seed);
         }
      }
   }

   @Test
   void surgeDipsAndRecovers() {
      for (long seed = 0; seed < 200; seed++) {
         int dark = 0;
         for (int a = 0; a < PowerRules.SURGE_TICKS; a++) {
            if (!PowerRules.surgeLit(a, seed)) {
               dark++;
            }
         }
         assertTrue(dark >= 4 && dark <= 8);
         assertTrue(PowerRules.surgeLit(PowerRules.SURGE_TICKS, seed));
      }
   }

   @Test
   void alarmNeverStepsDownSilently() {
      assertEquals(AlarmStage.CRITICAL, PowerRules.resolve(AlarmStage.CRITICAL, AlarmStage.WARNING, 0));
      assertEquals(AlarmStage.ALL_CLEAR, PowerRules.resolve(AlarmStage.NONE, AlarmStage.ACTIVE, 900));
      assertEquals(AlarmStage.ALL_CLEAR, PowerRules.resolve(AlarmStage.NONE, AlarmStage.CRITICAL, 5));
      assertEquals(AlarmStage.ALL_CLEAR, PowerRules.resolve(AlarmStage.NONE, AlarmStage.ALL_CLEAR, 10));
      assertEquals(AlarmStage.NONE, PowerRules.resolve(AlarmStage.NONE, AlarmStage.ALL_CLEAR, PowerRules.ALL_CLEAR_TICKS));
      assertEquals(AlarmStage.NONE, PowerRules.resolve(AlarmStage.NONE, AlarmStage.WARNING, 0));
   }

   @Test
   void sirenCurvesFadeWithDistance() {
      assertEquals(1.0F, PowerRules.sirenLevel(0), 1e-6);
      assertEquals(0.0F, PowerRules.sirenLevel(900));
      float prev = 2;
      for (int d = 0; d <= 900; d += 25) {
         float l = PowerRules.sirenLevel(d);
         assertTrue(l <= prev && l >= 0);
         prev = l;
      }
      assertEquals(1.0F, PowerRules.sirenNear(20));
      assertEquals(0.0F, PowerRules.sirenNear(400));
      assertEquals(0, PowerRules.soundDelay(0));
      assertEquals(60, PowerRules.soundDelay(5000));
   }

   @Test
   void packedCellsRoundTrip() {
      for (int ix : new int[]{-8388608, -300, -1, 0, 5, 8388607}) {
         for (int iz : new int[]{-8388608, -2, 0, 77, 8388607}) {
            for (int s = 0; s < 16; s += 5) {
               long p = CityPowerS2CPacket.packCell(ix, iz, s);
               assertEquals(ix, CityPowerS2CPacket.cellX(p));
               assertEquals(iz, CityPowerS2CPacket.cellZ(p));
               assertEquals(s, CityPowerS2CPacket.cellState(p));
            }
         }
      }
   }

   @Test
   void everySoundEventHasFilesAndSubtitlesInEveryLocale() throws IOException {
      Path main = Path.of("src/main");
      JsonObject events = JsonParser.parseString(Files.readString(main.resolve("sound-overlays/city_power.json"), StandardCharsets.UTF_8)).getAsJsonObject();
      Path sounds = main.resolve("binassets/assets/viltrumitecore/sounds");
      Path langs = main.resolve("lang-overlays/city_power");
      String[] locales;
      try (var s = Files.list(langs)) {
         locales = s.map(p -> p.getFileName().toString()).sorted().toArray(String[]::new);
      }
      assertEquals(12, locales.length);
      JsonObject en = JsonParser.parseString(Files.readString(langs.resolve("en_us.json"))).getAsJsonObject();
      for (Map.Entry<String, com.google.gson.JsonElement> e : events.entrySet()) {
         assertTrue(e.getKey().startsWith("city_power."), e.getKey());
         JsonObject ev = e.getValue().getAsJsonObject();
         String subtitle = ev.get("subtitle").getAsString();
         for (String loc : locales) {
            JsonObject lang = JsonParser.parseString(Files.readString(langs.resolve(loc))).getAsJsonObject();
            assertTrue(lang.has(subtitle), loc + " misses " + subtitle);
         }
         for (var snd : ev.getAsJsonArray("sounds")) {
            String name = snd.isJsonObject() ? snd.getAsJsonObject().get("name").getAsString() : snd.getAsString();
            assertTrue(name.startsWith("viltrumitecore:city_power/"), name);
            String file = name.substring("viltrumitecore:".length()) + ".ogg";
            boolean whole = Files.exists(sounds.resolve(file + ".b64"));
            boolean parts = Files.exists(sounds.resolve(file + ".b64.part0")) || Files.exists(sounds.resolve(file + ".b64.part1"));
            assertTrue(whole || parts, "missing sound file " + file);
         }
      }
      for (String loc : locales) {
         JsonObject lang = JsonParser.parseString(Files.readString(langs.resolve(loc))).getAsJsonObject();
         assertEquals(en.keySet(), lang.keySet(), loc + " key set differs from en_us");
      }
   }

   @Test
   void plainDestructionNeedsCollapseSeverityForAnActiveAlarmAndNeverWarns() {
      // tuned on Atheria (memory: city-infrastructure.md): hero smashing walls at spawn must not raise the alarm
      assertEquals(AlarmStage.NONE, PowerRules.alarmStage("destruction", 0.0));
      assertEquals(AlarmStage.NONE, PowerRules.alarmStage("destruction", 0.29));
      assertEquals(AlarmStage.NONE, PowerRules.alarmStage("destruction", 0.59));
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("destruction", 0.6), "active exactly at 0.6");
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("destruction", 0.9));
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("destruction", 1.5), "severity above 1 clamps to 1 and fires");
      // every other cause follows the general ladder and can warn before going active
      assertEquals(AlarmStage.NONE, PowerRules.alarmStage("meteor", 0.29));
      assertEquals(AlarmStage.WARNING, PowerRules.alarmStage("meteor", 0.3), "warning exactly at 0.3");
      assertEquals(AlarmStage.WARNING, PowerRules.alarmStage("explosion", 0.44));
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("explosion", 0.45), "other causes go active at 0.45");
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("godzilla", 0.9));
      assertEquals(AlarmStage.ACTIVE, PowerRules.alarmStage("", 0.9), "unknown causes are not plain destruction");
      assertEquals(AlarmStage.NONE, PowerRules.alarmStage(null, 0.1), "no cause, no alarm");
      // the constants are the documented tuning, pinned so a rebalance is a conscious change
      assertEquals(0.6, PowerRules.ACTIVE_SEVERITY, 1e-9);
      assertEquals(0.45, PowerRules.ACTIVE_OTHER_SEVERITY, 1e-9);
      assertEquals(0.3, PowerRules.WARNING_SEVERITY, 1e-9);
   }

   @Test
   void cascadeFloorChanceAndCriticalTripsAreTheTunedOnes() {
      // tuned on Atheria (memory: city-infrastructure.md): a neighbour under 0.30 load never gives way
      for (long seed = 0; seed < 4000; seed++) {
         assertFalse(PowerRules.cascades(0.29, 1, seed), "floor 0.30: below it nothing trips, seed " + seed);
         assertFalse(PowerRules.cascades(0.30, 1, seed), "at the floor the chance is 0, seed " + seed);
      }
      // chance = min(0.85, (load - 0.30) * 1.2), deterministic per seed: one spot check above the floor
      assertTrue(PowerRules.cascades(0.31, 1, 0));
      assertFalse(PowerRules.cascades(0.31, 1, 1));
      assertFalse(PowerRules.cascades(0.31, 1, 2));
      assertFalse(PowerRules.cascades(0.31, 1, 3));
      assertFalse(PowerRules.cascades(0.31, 1, 4));
      assertFalse(PowerRules.cascades(0.31, 1, 5));
      assertFalse(PowerRules.cascades(0.31, 1, 6));
      // 0.5 load: (0.5-0.3)*1.2 = 0.24 chance — about a quarter of the neighbours over 4000 seeds
      int quarter = 0;
      for (long seed = 0; seed < 4000; seed++) {
         if (PowerRules.cascades(0.5, 1, seed)) quarter++;
      }
      assertTrue(quarter > 700 && quarter < 1400, "0.5 load trips " + quarter + "/4000");
      // a worn chain far above the cap stays at the 0.85 ceiling, whatever the load
      int capped = 0, more = 0;
      for (long seed = 0; seed < 4000; seed++) {
         if (PowerRules.cascades(1.5, 1, seed)) capped++;
         if (PowerRules.cascades(3.0, 1, seed)) more++;
      }
      assertEquals(capped, more, "the 0.85 chance cap does not move with load");
      assertTrue(capped > 3100 && capped < 3700, "capped trips " + capped + "/4000");
      assertEquals(3, PowerRules.CRITICAL_TRIPS, "three substations down in one incident = critical");
      assertEquals(0.594, PowerRules.cascadeLoad(0.9, 1), 1e-9, "0.66^1 decay from a 0.9 severity");
      assertEquals(1.0, PowerRules.cascadeLoad(2.0, 0), 1e-9, "severity is clamped into the load");
   }

   @Test
   void darkFractionCountsUrbanCellsNotTheWholeArena() {
      // tuned on Atheria (memory: city-infrastructure.md): districts are the denominator, outskirts hide nothing
      assertEquals(0.5221543F, PowerRules.darkFraction(7000, 13406, 160000), 1e-6, "half of downtown dark");
      assertEquals(1.0F, PowerRules.darkFraction(13406, 13406, 160000), 1e-6);
      assertEquals(1.0F, PowerRules.darkFraction(30000, 13406, 160000), 1e-6, "clamped at 1");
      assertEquals(0.0F, PowerRules.darkFraction(0, 13406, 160000), 1e-9);
      // without mapped districts the whole grid is the fallback denominator, never a zero division
      assertEquals(5.0F / 160000.0F, PowerRules.darkFraction(5, 0, 160000), 1e-9);
      assertEquals(0.0F, PowerRules.darkFraction(0, 0, 0), 1e-9);
   }

   @Test
   void sirenLevelsAreThePinnedCurveNumbers() {
      // 1 at the mast, half at the 110-block reference distance, silence from 900 blocks (from PowerRules source)
      assertEquals(1.0F, PowerRules.sirenLevel(0), 1e-6);
      assertEquals(0.5F, PowerRules.sirenLevel(110), 1e-6);
      assertEquals(0.2612039F, PowerRules.sirenLevel(220), 1e-5);
      assertEquals(0.1111111F, PowerRules.sirenLevel(440), 1e-5);
      assertEquals(0.0020821F, PowerRules.sirenLevel(890), 1e-5, "the edge fade before the cutoff");
      assertEquals(0.0F, PowerRules.sirenLevel(900), 1e-9);
      assertEquals(0.0F, PowerRules.sirenLevel(1500), 1e-9);
      assertEquals(0.0F, PowerRules.sirenNear(220), 1e-9);
      assertEquals(0.5F, PowerRules.sirenNear(145), 1e-6);
      assertEquals(1.0F, PowerRules.sirenNear(70), 1e-6);
      // sound is delayed by distance at 343 m/s, capped at three seconds
      assertEquals(10, PowerRules.soundDelay(171.5));
      assertEquals(60, PowerRules.soundDelay(5000));
      assertEquals(0, PowerRules.soundDelay(0));
   }
}
