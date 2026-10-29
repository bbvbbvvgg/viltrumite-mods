package dev.baranhan.viltrumitecore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraftforge.fml.loading.FMLPaths;

public class ViltrumiteClientConfig {
   public boolean alwaysRenderOffhand = true;
   /** Cinematic hero subtitles (spoken hero lines above the hotbar). */
   public boolean heroSubtitles = true;
   /** Reading-time factor of automatically timed hero subtitles, 0.5..2.5. */
   public double heroSubtitleDurationScale = 1.0;
   public static ViltrumiteClientConfig INSTANCE = new ViltrumiteClientConfig();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File CONFIG_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "viltrumitecore_client.json");

   public static void load() {
      if (CONFIG_FILE.exists()) {
         try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ViltrumiteClientConfig loaded = (ViltrumiteClientConfig)GSON.fromJson(reader, ViltrumiteClientConfig.class);
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
