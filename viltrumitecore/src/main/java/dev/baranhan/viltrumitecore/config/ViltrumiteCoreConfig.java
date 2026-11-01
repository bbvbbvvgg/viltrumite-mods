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
   // Structural collapse of damaged buildings (world/collapse, docs/city-collapse-integration.md).
   // Off: damage still breaks blocks as before, nothing ever collapses. Clamped by CollapseDirector.
   public boolean collapseEnabled = true;
   /** Collapses running at the same time (0..8). */
   public int collapseMaxActive = 3;
   /** Blocks one collapse may move (24..4000); bigger sections are cut at the top. */
   public int collapseMaxBlocks = 1600;
   /** Block removals per server tick across all collapses (64..4000). */
   public int collapseBlocksPerTick = 900;
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
            if (!java.nio.file.Files.readString(CONFIG_FILE.toPath()).contains("reinhardFinalPunchDamage")
               || !java.nio.file.Files.readString(CONFIG_FILE.toPath()).contains("collapseBlocksPerTick")) {
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
