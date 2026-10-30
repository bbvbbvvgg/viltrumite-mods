package dev.baranhan.viltrumitecore.hero.dio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** DIO state lifecycle: what persists, what a death keeps, what a cleared action leaves behind. */
class DioStateTest {
   @Test
   void saveLoadKeepsBloodAndCooldownsOnly() {
      DioState state = new DioState();
      state.blood = 73;
      state.cooldowns[DioRules.CD_TIME_STOP] = 400;
      state.standPhase = DioRules.STAND_OUT;
      state.startAction(HeroAction.TIME_STOP, DioRules.TIME_STOP_CAST);
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      DioState loaded = new DioState();
      loaded.load(nbt);
      assertEquals(73, loaded.blood);
      assertArrayEquals(state.cooldowns, loaded.cooldowns);
      assertEquals(DioRules.STAND_NONE, loaded.standPhase, "the stand is put away after a login");
      assertNull(loaded.action, "channels never persist");
   }

   @Test
   void loadClampsBadValues() {
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("DioBlood", 999);
      nbt.putIntArray("DioCooldowns", new int[]{-5, 10, 20, 30, 40, 50, 60, 70});
      DioState state = new DioState();
      state.load(nbt);
      assertEquals(DioRules.BLOOD_MAX, state.blood);
      assertEquals(0, state.cooldowns[0]);
      assertEquals(50, state.cooldowns[5]);
   }

   @Test
   void deathKeepsCooldownsAndHalfTheBlood() {
      DioState state = new DioState();
      state.blood = 81;
      state.cooldowns[DioRules.CD_ROLLER] = 2000;
      state.standPhase = DioRules.STAND_OUT;
      state.startAction(HeroAction.ROAD_ROLLER, DioRules.ROLLER_LENGTH);
      state.drainTargetId = 12;
      DioState copy = state.respawnCopy();
      assertEquals(40, copy.blood);
      assertEquals(2000, copy.cooldowns[DioRules.CD_ROLLER]);
      assertNull(copy.action);
      assertEquals(DioRules.STAND_NONE, copy.standPhase);
      assertEquals(-1, copy.drainTargetId);
      assertFalse(copy.standOut());
   }

   @Test
   void clearActionDropsChannelFlags() {
      DioState state = new DioState();
      state.startAction(HeroAction.MUDA_BARRAGE, DioRules.BARRAGE_MAX);
      state.barrageHeld = true;
      state.finisher = true;
      state.comboBuffered = true;
      assertTrue(state.busy());
      state.clearAction();
      assertFalse(state.busy());
      assertFalse(state.barrageHeld);
      assertFalse(state.finisher);
      assertFalse(state.comboBuffered);
      assertEquals(0, state.length);
   }

   @Test
   void heroIsRegisteredWithoutFlightOrLegacyKit() {
      HeroRegistry.registerDefaults();
      assertSame(DioHero.INSTANCE, HeroRegistry.get(HeroId.DIO));
      assertSame(HeroId.DIO, HeroId.fromKey("dio"));
      assertFalse(DioHero.INSTANCE.allowsFlight(null));
      assertFalse(DioHero.INSTANCE.allowsLegacyAbilities(null));
      assertTrue(DioHero.INSTANCE.cancelsFallDamage(null));
      assertEquals(6, DioHero.INSTANCE.panelAbilities().size());
   }

   @Test
   void everyLocaleCarriesDioText() throws Exception {
      java.nio.file.Path lang = java.nio.file.Path.of("src/main/resources/assets/viltrumitecore/lang");
      java.util.List<String> keys = new java.util.ArrayList<>();
      DioLines.LINES.all().forEach(line -> keys.addAll(line.fragments()));
      keys.add("subtitle.viltrumitecore.speaker.dio");
      keys.add("hero.viltrumitecore.dio");
      keys.add("gui.viltrumitecore.race_selection.become_dio");
      keys.add("entity.viltrumitecore.dio_knife");
      for (String id : new String[]{"the_world", "muda_barrage", "knives", "time_stop", "vampire_drain", "road_roller"}) {
         keys.add("ability.viltrumitecore.dio_" + id + ".name");
         keys.add("ability.viltrumitecore.dio_" + id + ".desc");
      }

      com.google.gson.JsonObject sounds = dev.baranhan.viltrumitecore.integration.ShippedSounds.merged();
      for (String name : sounds.keySet()) {
         if (name.startsWith("dio.")) {
            keys.add(sounds.getAsJsonObject(name).get("subtitle").getAsString());
         }
      }

      assertEquals(36 + 25, keys.size(), "36 text keys (15 line fragments since the global subtitles expansion) + 25 dio sound subtitles");
      java.util.List<java.nio.file.Path> files;
      try (var listing = java.nio.file.Files.list(lang)) {
         files = listing.filter(p -> p.toString().endsWith(".json")).toList();
      }

      assertEquals(12, files.size());
      for (java.nio.file.Path file : files) {
         com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
         // Overlays (src/main/lang-overlays/<part>/<locale>.json) are merged into the locale by processResources.
         java.nio.file.Path overlays = java.nio.file.Path.of("src/main/lang-overlays");
         if (java.nio.file.Files.isDirectory(overlays)) {
            try (var parts = java.nio.file.Files.list(overlays)) {
               for (java.nio.file.Path part : parts.toList()) {
                  java.nio.file.Path extra = part.resolve(file.getFileName().toString());
                  if (java.nio.file.Files.isRegularFile(extra)) {
                     com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(extra)).getAsJsonObject().entrySet()
                        .forEach(e -> json.add(e.getKey(), e.getValue()));
                  }
               }
            }
         }

         for (String key : keys) {
            assertTrue(json.has(key) && !json.get(key).getAsString().isBlank(), file.getFileName() + " " + key);
         }
      }
   }
}
