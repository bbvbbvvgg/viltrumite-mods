package dev.baranhan.viltrumitecore.city.power;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Where the lights of the city are, found lazily: a chunk is scanned only when its grid cell is about to go dark,
 * only while it is loaded, at most {@link PowerRules#SCANS_PER_TICK} chunks per tick, and a section only when its
 * block palette may hold a lit fixture (most sections are skipped without reading a block). Never a scan of the
 * whole city.
 */
final class FixtureIndex {
   private static final long[] NONE = new long[0];
   private static final int MAX_QUEUE = 4096;
   private final ServerLevel level;
   private final int minY;
   private final int maxY;
   private final Long2ObjectOpenHashMap<long[]> chunks = new Long2ObjectOpenHashMap<>();
   private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
   private long scannedTotal;

   FixtureIndex(ServerLevel level, int minY, int maxY) {
      this.level = level;
      this.minY = minY;
      this.maxY = maxY;
   }

   boolean scanned(long chunkKey) {
      return this.chunks.containsKey(chunkKey);
   }

   /** Lit fixtures of a scanned chunk at scan time (an empty array when the chunk has none), or null when unscanned. */
   @Nullable
   long[] fixtures(long chunkKey) {
      return this.chunks.get(chunkKey);
   }

   void request(long chunkKey) {
      if (!this.chunks.containsKey(chunkKey) && this.queue.size() < MAX_QUEUE) {
         this.queue.add(chunkKey);
      }
   }

   /** The chunk was (re)loaded: its blocks may differ from the last scan. */
   void forget(long chunkKey) {
      this.chunks.remove(chunkKey);
   }

   void clear() {
      this.chunks.clear();
      this.queue.clear();
   }

   int pending() {
      return this.queue.size();
   }

   int chunkCount() {
      return this.chunks.size();
   }

   long fixtureCount() {
      long n = 0;
      for (long[] f : this.chunks.values()) {
         n += f.length;
      }
      return n;
   }

   long scannedTotal() {
      return this.scannedTotal;
   }

   void tick() {
      int budget = PowerRules.SCANS_PER_TICK;
      while (budget > 0 && !this.queue.isEmpty()) {
         long key = this.queue.removeFirstLong();
         LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
         if (chunk == null) {
            continue; // scanned when it loads (PowerGrid.onChunkLoad asks again)
         }
         this.chunks.put(key, this.scan(chunk));
         this.scannedTotal++;
         budget--;
      }
   }

   private long[] scan(LevelChunk chunk) {
      LongArrayList out = null;
      int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
      LevelChunkSection[] sections = chunk.getSections();
      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         int by = chunk.getSectionYFromSectionIndex(i) << 4;
         if (section == null || section.hasOnlyAir() || by + 15 < this.minY || by > this.maxY || !section.maybeHas(Fixtures::isLit)) {
            continue;
         }
         for (int y = 0; y < 16; y++) {
            int wy = by + y;
            if (wy < this.minY || wy > this.maxY) {
               continue;
            }
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  if (Fixtures.isLit(section.getBlockState(x, y, z))) {
                     if (out == null) {
                        out = new LongArrayList();
                     }
                     out.add(BlockPos.asLong(bx + x, wy, bz + z));
                     if (out.size() >= PowerRules.MAX_FIXTURES_PER_CHUNK) {
                        return out.toLongArray();
                     }
                  }
               }
            }
         }
      }
      return out == null ? NONE : out.toLongArray();
   }
}
