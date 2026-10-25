package dev.baranhan.viltrumitecore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraftforge.fml.loading.FMLPaths;

public class ViltrumitePostProcessingConfig {
   /** Local cinematic world-cut shake, 0 disables. Other heroes retain their old cap. */
   public float reinhardWorldCutShake = 1.0F;
   public float punchShakeMultiplier = 1.0F;
   public float punchEffectMultiplier = 1.0F;
   public float lockVignetteMultiplier = 1.0F;
   public static ViltrumitePostProcessingConfig INSTANCE = new ViltrumitePostProcessingConfig();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "viltrumitecore_post_processing.json");

   public static void load() {
      if (CONFIG_FILE.exists()) {
         try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ViltrumitePostProcessingConfig loaded = (ViltrumitePostProcessingConfig)GSON.fromJson(reader, ViltrumitePostProcessingConfig.class);
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
