package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.restore.ChangeJournal;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Switches the real light blocks of grid cells off and on, within {@link PowerRules#WRITES_PER_TICK} writes a tick.
 * Every fixture it turns off goes into a ledger (exact original and dark state, saved with the world as
 * {@code viltrumite_city/power_ledger.dat}) and, once, into the city change journal as a changed block, so power
 * coming back, a restart and {@code /city reset} all put the original state back. Fixtures are written with
 * client updates only (no neighbour updates, nothing drops) and with the journal hook suppressed. A ledger entry
 * whose block changed since (destroyed, replaced, reset) is dropped instead of overwriting the new block.
 */
final class FixtureSwitch {
   static final String FILE = "power_ledger.dat";
   private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

   record Entry(BlockState original, BlockState dark) {
   }

   /** Work on one cell: bring every fixture of it to {@code lit}. */
   static final class Sweep {
      boolean lit;
      int chunk;
      int pos;

      Sweep(boolean lit) {
         this.lit = lit;
      }
   }

   private final PowerGrid grid;
   private final ServerLevel level;
   private final FixtureIndex index;
   private final Path file;
   /** chunk key -> (block pos -> entry) of every fixture this grid turned off. */
   private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> ledger = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectLinkedOpenHashMap<Sweep> sweeps = new Long2ObjectLinkedOpenHashMap<>();
   private int size;
   private boolean dirty;
   private long writes;
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

   FixtureSwitch(PowerGrid grid, ServerLevel level, FixtureIndex index, Path folder) {
      this.grid = grid;
      this.level = level;
      this.index = index;
      this.file = folder.resolve(FILE);
   }

   int size() {
      return this.size;
   }

   int sweeps() {
      return this.sweeps.size();
   }

   long writes() {
      return this.writes;
   }

   boolean hasChunk(long chunkKey) {
      Long2ObjectOpenHashMap<Entry> m = this.ledger.get(chunkKey);
      return m != null && !m.isEmpty();
   }

   long[] ledgerChunks() {
      return this.ledger.keySet().toLongArray();
   }

   /** Asks for every fixture of the cell to be {@code lit}; a newer wish replaces an unfinished one. */
   void want(long cellKey, boolean lit) {
      Sweep s = this.sweeps.get(cellKey);
      if (s == null) {
         this.sweeps.put(cellKey, new Sweep(lit));
      } else if (s.lit != lit) {
         s.lit = lit;
         s.chunk = 0;
         s.pos = 0;
      } else {
         s.chunk = 0; // same wish again (a chunk loaded): go over the cell once more
         s.pos = 0;
      }
   }

   void tick() {
      if (this.sweeps.isEmpty()) {
         return;
      }
      int[] budget = {PowerRules.WRITES_PER_TICK, PowerRules.READS_PER_TICK};
      LongArrayList done = new LongArrayList();
      for (Long2ObjectMap.Entry<Sweep> e : this.sweeps.long2ObjectEntrySet()) {
         if (budget[0] <= 0 || budget[1] <= 0) {
            break;
         }
         if (this.process(e.getLongKey(), e.getValue(), budget)) {
            done.add(e.getLongKey());
         }
      }
      for (int i = 0; i < done.size(); i++) {
         this.sweeps.remove(done.getLong(i));
      }
   }

   /** True when the cell is finished (or has to wait for a chunk load, which asks again). */
   private boolean process(long cellKey, Sweep s, int[] budget) {
      long[] chunkKeys = this.grid.chunksOf(cellKey);
      boolean waiting = false;
      for (; s.chunk < chunkKeys.length; s.chunk++, s.pos = 0) {
         long ck = chunkKeys[s.chunk];
         LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(ck), ChunkPos.getZ(ck));
         if (chunk == null) {
            continue; // handled when the chunk loads
         }
         if (s.lit) {
            Long2ObjectOpenHashMap<Entry> m = this.ledger.get(ck);
            if (m == null || m.isEmpty()) {
               continue;
            }
            long[] keys = m.keySet().toLongArray();
            for (long p : keys) {
               if (budget[0] <= 0 || budget[1] <= 0) {
                  return false;
               }
               budget[1]--;
               Entry entry = m.get(p);
               this.cursor.set(p);
               BlockState now = chunk.getBlockState(this.cursor);
               if (now == entry.dark()) {
                  this.write(this.cursor, entry.original());
                  budget[0]--;
               }
               m.remove(p);
               this.size--;
               this.dirty = true;
            }
            this.ledger.remove(ck);
         } else {
            long[] fx = this.index.fixtures(ck);
            if (fx == null) {
               this.index.request(ck);
               waiting = true;
               continue;
            }
            for (; s.pos < fx.length; s.pos++) {
               if (budget[0] <= 0 || budget[1] <= 0) {
                  return false;
               }
               long p = fx[s.pos];
               if (PowerRules.emergency(p)) {
                  continue;
               }
               budget[1]--;
               this.cursor.set(p);
               BlockState now = chunk.getBlockState(this.cursor);
               BlockState dark = Fixtures.dark(now);
               if (dark == null) {
                  continue;
               }
               this.journal(this.cursor, now);
               this.write(this.cursor, dark);
               budget[0]--;
               if (this.ledger.computeIfAbsent(ck, k -> new Long2ObjectOpenHashMap<>()).put(p, new Entry(now, dark)) == null) {
                  this.size++;
               }
               this.dirty = true;
            }
         }
      }
      if (waiting) {
         // the scans are queued: come back to this cell (from the start, it is cheap) next tick
         s.chunk = 0;
         s.pos = 0;
         return false;
      }
      return true;
   }

   private void journal(BlockPos pos, BlockState original) {
      CityManager city = this.grid.city;
      if (!city.journal().contains(pos) && !city.restoreQueue().pending(pos)) {
         city.journal().record(pos.immutable(), original, null);
      }
   }

   private void write(BlockPos pos, BlockState state) {
      boolean was = ChangeJournal.suppressed;
      ChangeJournal.suppressed = true;
      try {
         this.level.setBlock(pos, state, FLAGS);
         this.writes++;
      } finally {
         ChangeJournal.suppressed = was;
      }
   }

   /** Up to {@code max} burning emergency fixtures of a cell (for the client's emergency light glows). */
   long[] emergencyLights(long cellKey, int max) {
      LongArrayList out = new LongArrayList();
      for (long ck : this.grid.chunksOf(cellKey)) {
         long[] fx = this.index.fixtures(ck);
         LevelChunk chunk = this.level.getChunkSource().getChunkNow(ChunkPos.getX(ck), ChunkPos.getZ(ck));
         if (fx == null || chunk == null) {
            continue;
         }
         // one per chunk, so the beacons spread over the cell instead of crowding one building
         for (long p : fx) {
            if (PowerRules.emergency(p) && Fixtures.isLit(chunk.getBlockState(this.cursor.set(p)))) {
               out.add(p);
               if (out.size() >= max) {
                  return out.toLongArray();
               }
               break;
            }
         }
      }
      return out.toLongArray();
   }

   /** Forgets everything (the city reset puts the journaled originals back itself). */
   void clear() {
      this.ledger.clear();
      this.sweeps.clear();
      this.size = 0;
      this.dirty = true;
   }

   // ------------------------------------------------------------------ persistence

   void load() {
      if (!Files.isRegularFile(this.file)) {
         return;
      }
      try {
         CompoundTag root = NbtIo.readCompressed(this.file.toFile());
         ListTag list = root.getList("e", Tag.TAG_COMPOUND);
         var blocks = this.level.holderLookup(Registries.BLOCK);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            long p = t.getLong("p");
            Entry e = new Entry(NbtUtils.readBlockState(blocks, t.getCompound("o")), NbtUtils.readBlockState(blocks, t.getCompound("d")));
            long ck = ChunkPos.asLong(BlockPos.getX(p) >> 4, BlockPos.getZ(p) >> 4);
            if (this.ledger.computeIfAbsent(ck, k -> new Long2ObjectOpenHashMap<>()).put(p, e) == null) {
               this.size++;
            }
         }
      } catch (IOException | RuntimeException e) {
         PowerGrid.LOG.error("City power ledger unreadable ({}); dark fixtures of the last session stay until /city reset", e.toString());
      }
   }

   void save(boolean force) {
      if (!this.dirty && !force) {
         return;
      }
      this.dirty = false;
      try {
         if (this.size == 0) {
            Files.deleteIfExists(this.file);
            return;
         }
         ListTag list = new ListTag();
         for (Long2ObjectOpenHashMap<Entry> m : this.ledger.values()) {
            for (Long2ObjectMap.Entry<Entry> e : m.long2ObjectEntrySet()) {
               CompoundTag t = new CompoundTag();
               t.putLong("p", e.getLongKey());
               t.put("o", NbtUtils.writeBlockState(e.getValue().original()));
               t.put("d", NbtUtils.writeBlockState(e.getValue().dark()));
               list.add(t);
            }
         }
         CompoundTag root = new CompoundTag();
         root.putInt("format", 1);
         root.put("e", list);
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(FILE + ".tmp");
         NbtIo.writeCompressed(root, tmp.toFile());
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         this.dirty = true;
         PowerGrid.LOG.error("City power ledger save failed: {}", e.toString());
      }
   }
}
