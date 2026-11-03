package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Runtime state of the pocket dimension shared with the chunk generator threads: the dimension key, the base seed
 * of every instance (derived from the world seed at server start) and the current maze variants per slot.
 */
public final class PocketWorld {
   public static final String DIMENSION_ID = "viltrumitecore:pocket_dimension";
   private static final long SALT = 0x106_106_106_106L;
   private static volatile long baseSeed = SALT;
   private static final Map<Integer, int[]> VARIANTS = new ConcurrentHashMap<>();

   private PocketWorld() {
   }

   public static ResourceKey<Level> dimension() {
      return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DIMENSION_ID));
   }

   public static boolean is(Level level) {
      return level != null && level.dimension().location().toString().equals(DIMENSION_ID);
   }

   public static void start(long worldSeed) {
      baseSeed = PocketLayout.mix(worldSeed ^ SALT);
      VARIANTS.clear();
   }

   public static long baseSeed() {
      return baseSeed;
   }

   public static PocketLayout layout(int slot) {
      return PocketLayout.of(PocketLayout.instanceSeed(baseSeed, slot), slot);
   }

   /** Per-room variants of a slot (null: all as generated). */
   public static int[] variants(int slot) {
      return VARIANTS.get(slot);
   }

   public static void setVariants(int slot, int[] variants) {
      if (variants == null) {
         VARIANTS.remove(slot);
      } else {
         VARIANTS.put(slot, variants.clone());
      }
   }

   public static PocketPainter painter(int slot) {
      return new PocketPainter(layout(slot), variants(slot));
   }
}
