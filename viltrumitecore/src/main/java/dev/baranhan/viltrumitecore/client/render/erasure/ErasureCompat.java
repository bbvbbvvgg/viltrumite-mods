package dev.baranhan.viltrumitecore.client.render.erasure;

import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;

/** Shader-pack detection through the public Iris/Oculus API, by reflection: no compile dependency. */
final class ErasureCompat {
   private static boolean initialized;
   private static Object api;
   private static Method inUse;
   private static boolean unknownPipeline;

   private ErasureCompat() {
   }

   static boolean shaderPackInUse() {
      if (!initialized) {
         initialized = true;
         ModList mods = ModList.get();
         if (mods != null && (mods.isLoaded("oculus") || mods.isLoaded("iris"))) {
            try {
               Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
               api = type.getMethod("getInstance").invoke(null);
               inUse = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
               // The mod is there but its API is not: assume a pack may be active and stay safe.
               unknownPipeline = true;
            }
         }
      }
      if (unknownPipeline) return true;
      if (inUse == null) return false;
      try {
         return (Boolean) inUse.invoke(api);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return true;
      }
   }
}
