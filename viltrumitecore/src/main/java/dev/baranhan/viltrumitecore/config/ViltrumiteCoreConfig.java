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
   /**
    * Automatic world events (Viltrumite conquest, Thula/Vidor/Lucan trio). Off by
    * default: these characters appear only by {@code /viltrumite event start}.
    * A config file without this field keeps the default (Gson field defaults).
    */
   public boolean worldEventsAutoStart = false;
   public float worldEventChancePercent = 0.004F;
   public int worldEventCooldownMinutes = 40;
   public boolean bloodEnabled = true;
   public boolean shouldHumansBleed = true;
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
