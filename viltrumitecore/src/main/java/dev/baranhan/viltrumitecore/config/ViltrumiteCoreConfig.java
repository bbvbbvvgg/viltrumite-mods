package dev.baranhan.viltrumitecore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraftforge.fml.loading.FMLPaths;

public class ViltrumiteCoreConfig {
   public float damageReductionPercent = 97.0F;
   public float damageIgnoreThreshold = 0.5F;
   public boolean shouldAskRace = false;
   public boolean isViltrumiteByDefault = false;
   public float punchBlockDropChance = 40.0F;
   public float dashBlockDropChance = 40.0F;
   public double spaceLimitY = 1500.0;
   public float meteorSpawnChancePercent = 0.02F;
   public double meteorMinDistance = 500.0;
   // Viltrumite squad events (Conquest, Thula/Vidor/Lucan trio) start only by {@code /viltrumite event start}.
   // There is no automatic start any more; an old config's "worldEventsAutoStart" is ignored by Gson.
   public float worldEventChancePercent = 0.004F;
   public int worldEventCooldownMinutes = 40;
   /** Living city: citizens per player = profile perPlayer x /city density x this crowd scale. */
   public double cityCrowdScale = 2.25;
   /** Living city: hard cap of citizens in the city level (any density, any number of players). */
   public int cityMaxCitizens = 480;
   public boolean bloodEnabled = true;
   public boolean shouldHumansBleed = true;
   // Reinhard's secret Final Punch (Shift + N): balance knobs, clamped by FinalPunchRules.
   public float reinhardFinalPunchDamage = 40.0F;
   public double reinhardFinalPunchLaunchSpeed = 4.2;
   public double reinhardFinalPunchLaunchUp = 0.55;
   public int reinhardFinalPunchLaunchTicks = 10;
   public int reinhardFinalPunchCooldownSeconds = 300;
   /** City disaster aftermath: ruins keep smoking, dusting and sounding after destruction (docs/city-aftermath-integration.md). */
   public boolean aftermathEnabled = true;
   /** City disaster aftermath: stage lengths x this (0.1..10; 1 = 45 s fresh, 6 min smouldering, 16 min late at severity 0.5). */
   public double aftermathDurationScale = 1.0;
   public static ViltrumiteCoreConfig INSTANCE = new ViltrumiteCoreConfig();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "viltrumitecore.json");

   public static void load() {
      if (CONFIG_FILE.exists()) {
         try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ViltrumiteCoreConfig loaded = (ViltrumiteCoreConfig)GSON.fromJson(reader, ViltrumiteCoreConfig.class);
            if (loaded != null) {
               INSTANCE = loaded;
            }
         } catch (Exception var5) {
            var5.printStackTrace();
         }
         // Files written before a knob existed get it added once (defaults; nothing else changes).
         try {
            if (!java.nio.file.Files.readString(CONFIG_FILE.toPath()).contains("aftermathDurationScale")) {
               save();
            }
         } catch (IOException ignored) {
         }
      } else {
         save();
      }
   }

   public static void save() {
      try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
         GSON.toJson(INSTANCE, writer);
      } catch (IOException var5) {
         var5.printStackTrace();
      }
   }
}
