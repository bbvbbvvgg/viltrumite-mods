package dev.baranhan.viltrumitecore.client.cinematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.combat.feel.FeelMath;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.annotation.Nullable;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Player comfort settings of the cinematic combat (wave1-22), file
 * {@code config/viltrumitecore_cinematic.json}. Client only; the server never sees them.
 */
public final class CinematicSettings {
   public static final String FILE_NAME = "viltrumitecore_cinematic.json";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   @Nullable
   private static CinematicSettings instance;

   /** Shake at 35 %, no roll, no FOV kick, no air rings. */
   public boolean reduceCameraMotion = false;
   /** Ultimates and finishers keep the player's own camera (letterbox and slow-mo stay). */
   public boolean disableCinematicCamera = false;
   /** Show other players' cinematic camera shots when standing next to them (opt-in). */
   public boolean watchOthersCinematics = false;
   /** Inverted manga frames on crushing hits; off = a soft white flash instead. */
   public boolean impactFrames = true;
   /** 0..1 multiplier of every flash of the cinematic combat. */
   public float flashIntensity = 1.0F;

   public static CinematicSettings get() {
      CinematicSettings current = instance;
      if (current == null) {
         load();
         current = instance;
      }
      return current;
   }

   public static synchronized void load() {
      File file = file();
      CinematicSettings loaded = null;
      if (file.exists()) {
         try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            loaded = GSON.fromJson(reader, CinematicSettings.class);
         } catch (Exception e) {
            LOGGER.warn("Could not read {}, using defaults", FILE_NAME, e);
         }
      }
      instance = loaded != null ? loaded : new CinematicSettings();
      if (loaded == null) {
         save();
      }
   }

   public static synchronized void save() {
      CinematicSettings current = instance == null ? new CinematicSettings() : instance;
      try (Writer writer = Files.newBufferedWriter(file().toPath(), StandardCharsets.UTF_8)) {
         GSON.toJson(current, writer);
      } catch (Exception e) {
         LOGGER.warn("Could not save {}", FILE_NAME, e);
      }
   }

   private static File file() {
      return new File(FMLPaths.CONFIGDIR.get().toFile(), FILE_NAME);
   }

   /** Camera motion multiplier: 1, or 0.35 with reduced camera motion. */
   public float motionScale() {
      return this.reduceCameraMotion ? FeelMath.REDUCED_MOTION : 1.0F;
   }

   /** Sanitised flash multiplier in 0..1. */
   public float flash() {
      return FeelMath.sane(this.flashIntensity, 1.0F);
   }
}
