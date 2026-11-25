package dev.baranhan.viltrumitecore.client.cinematic;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

/**
 * Loads every {@code assets/<namespace>/cinematics/<id>.json} scene (wave1-22, task T4), data-driven
 * so sessions 23/24 and new heroes add scenes without code. Registered like the other client reload
 * listeners; a file that cannot be read or has no usable path is logged and skipped, never thrown.
 */
public final class CinematicScenes implements ResourceManagerReloadListener {
   public static final CinematicScenes INSTANCE = new CinematicScenes();
   /** Resource directory of the scenes, under {@code assets/<ns>/}. */
   public static final String DIR = "cinematics";
   private static final String SUFFIX = ".json";
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<ResourceLocation, CinematicScene> scenes = new ConcurrentHashMap<>();

   private CinematicScenes() {
   }

   @Override
   public void onResourceManagerReload(ResourceManager manager) {
      Map<ResourceLocation, CinematicScene> loaded = new HashMap<>();
      for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(DIR, CinematicScenes::isSceneFile).entrySet()) {
         ResourceLocation id = idOf(entry.getKey());
         if (id == null) {
            continue;
         }
         try (var reader = entry.getValue().openAsReader()) {
            CinematicScene scene = CinematicScene.parse(com.google.gson.JsonParser.parseReader(reader));
            if (scene == null) {
               LOGGER.warn("Cinematic scene {} has no usable keyframes, ignored", id);
               continue;
            }
            loaded.put(id, scene);
         } catch (Exception error) {
            LOGGER.warn("Could not read cinematic scene {}, ignored", id, error);
         }
      }
      this.scenes.clear();
      this.scenes.putAll(loaded);
      LOGGER.info("Loaded {} cinematic scene(s)", loaded.size());
   }

   /** The scene of a packet's id, or {@code null} (the camera then plays the built-in orbit). */
   @Nullable
   public static CinematicScene get(@Nullable ResourceLocation id) {
      return id == null ? null : INSTANCE.scenes.get(id);
   }

   static boolean isSceneFile(ResourceLocation file) {
      return file.getPath().startsWith(DIR + "/") && file.getPath().endsWith(SUFFIX);
   }

   /** {@code <ns>:cinematics/orbit.json} to the scene id {@code <ns>:orbit}; null for another directory. */
   @Nullable
   static ResourceLocation idOf(ResourceLocation file) {
      String path = file.getPath();
      if (!isSceneFile(file)) {
         return null;
      }
      String name = path.substring(DIR.length() + 1, path.length() - SUFFIX.length());
      return name.isEmpty() ? null : ResourceLocation.tryParse(file.getNamespace() + ":" + name);
   }
}
