package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.baranhan.viltrumitecore.scp.scp106.block.PocketBlocks;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Chunk generator {@code viltrumitecore:pocket}: every chunk inside an instance is painted column by column from the
 * pure {@link PocketPainter} (no noise, no structures, no features, no mobs). The space between instances is empty.
 */
public final class PocketChunkGenerator extends ChunkGenerator {
   public static final Codec<PocketChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
      BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(PocketChunkGenerator::new)));

   public PocketChunkGenerator(BiomeSource biomes) {
      super(biomes);
   }

   @Override
   protected Codec<? extends ChunkGenerator> codec() {
      return CODEC;
   }

   @Override
   public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
      ChunkPos cp = chunk.getPos();
      int x0 = cp.getMinBlockX();
      int z0 = cp.getMinBlockZ();
      int slot = PocketLayout.slotAt(x0, z0);
      if (slot < 0) {
         return CompletableFuture.completedFuture(chunk);
      }
      PocketPainter painter = PocketWorld.painter(slot);
      int lx0 = x0 - PocketLayout.slotOriginX(slot);
      int lz0 = z0 - PocketLayout.slotOriginZ(slot);
      Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
      Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
      byte[] col = new byte[PocketPainter.HEIGHT];
      int minY = chunk.getMinBuildHeight();
      int maxY = Math.min(chunk.getMaxBuildHeight(), PocketPainter.HEIGHT);
      for (LevelChunkSection section : chunk.getSections()) {
         section.acquire();
      }
      try {
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               painter.column(lx0 + x, lz0 + z, col);
               for (int y = Math.max(0, minY); y < maxY; y++) {
                  byte id = col[y];
                  if (id == PocketPalette.AIR) {
                     continue;
                  }
                  BlockState state = PocketBlocks.state(id);
                  LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                  section.setBlockState(x, y & 15, z, state, false);
                  ocean.update(x, y, z, state);
                  surface.update(x, y, z, state);
                  if (needsShape(id)) {
                     chunk.markPosForPostprocessing(pos.set(x0 + x, y, z0 + z));
                  }
               }
            }
         }
      } finally {
         for (LevelChunkSection section : chunk.getSections()) {
            section.release();
         }
      }
      return CompletableFuture.completedFuture(chunk);
   }

   /** Bars, fences and stairs connect to their neighbours once the chunk is complete. */
   private static boolean needsShape(byte id) {
      return id == PocketPalette.BARS || id == PocketPalette.FENCE || id >= PocketPalette.STAIR && id < PocketPalette.CHAIR + 4;
   }

   @Override
   public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
      GenerationStep.Carving step) {
   }

   @Override
   public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
   }

   @Override
   public void spawnOriginalMobs(WorldGenRegion region) {
   }

   @Override
   public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
   }

   @Override
   public void createStructures(RegistryAccess access, net.minecraft.world.level.chunk.ChunkGeneratorStructureState state, StructureManager structures,
      ChunkAccess chunk, StructureTemplateManager templates) {
   }

   @Override
   public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
   }

   @Override
   public int getGenDepth() {
      return PocketPainter.HEIGHT;
   }

   @Override
   public int getSeaLevel() {
      return 0;
   }

   @Override
   public int getMinY() {
      return 0;
   }

   @Override
   public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
      int slot = PocketLayout.slotAt(x, z);
      if (slot < 0) {
         return level.getMinBuildHeight();
      }
      byte[] col = new byte[PocketPainter.HEIGHT];
      PocketWorld.painter(slot).column(x - PocketLayout.slotOriginX(slot), z - PocketLayout.slotOriginZ(slot), col);
      for (int y = col.length - 1; y >= 0; y--) {
         if (col[y] != PocketPalette.AIR && type.isOpaque().test(PocketBlocks.state(col[y]))) {
            return y + 1;
         }
      }
      return level.getMinBuildHeight();
   }

   @Override
   public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
      BlockState[] states = new BlockState[level.getHeight()];
      int slot = PocketLayout.slotAt(x, z);
      byte[] col = new byte[PocketPainter.HEIGHT];
      if (slot >= 0) {
         PocketWorld.painter(slot).column(x - PocketLayout.slotOriginX(slot), z - PocketLayout.slotOriginZ(slot), col);
      }
      for (int i = 0; i < states.length; i++) {
         int y = level.getMinBuildHeight() + i;
         states[i] = y >= 0 && y < col.length && col[y] != PocketPalette.AIR ? PocketBlocks.state(col[y]) : Blocks.AIR.defaultBlockState();
      }
      return new NoiseColumn(level.getMinBuildHeight(), states);
   }

   @Override
   public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
      int slot = PocketLayout.slotAt(pos.getX(), pos.getZ());
      if (slot >= 0) {
         PocketLayout.Room room = PocketWorld.layout(slot).roomAtBlock(pos.getX() - PocketLayout.slotOriginX(slot), pos.getZ() - PocketLayout.slotOriginZ(slot));
         info.add("SCP-106 pocket: slot " + slot + (room == null ? "" : " " + room));
      }
   }

}
