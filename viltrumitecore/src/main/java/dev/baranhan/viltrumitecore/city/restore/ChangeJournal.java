package dev.baranhan.viltrumitecore.city.restore;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Original states of every block changed inside the city arena since the prepared
 * baseline: the first write to a position stores what was there before (plus its block
 * entity data); later writes to the same position are ignored. /city reset puts the
 * originals back and empties the journal. Persisted (gzip NBT, atomic replace) next to
 * the profile so a restart, or a reset interrupted by a restart, loses nothing.
 *
 * <p>Restored chunks stay in a separate <i>settling</i> set until the world has written
 * them to disk ({@link #worldSaved()} twice): a crash right after a reset can leave the
 * damaged chunk on disk, and the settling originals let the next start put it back
 * ({@link #recovery()}) instead of losing them with the emptied journal.
 */
public final class ChangeJournal {
   public static final String FILE = "journal.dat";
   public static final int FORMAT = 1;

   public record Entry(BlockState state, @Nullable CompoundTag blockEntity) {
   }

   /** Set while the restore queue writes originals back (its own writes are not journaled). */
   public static boolean suppressed;

   private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks = new Long2ObjectOpenHashMap<>();
   private long size;
   private boolean dirty;
   /** Originals of restored chunks whose restored blocks may not be on disk yet. */
   private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> settling = new Long2ObjectOpenHashMap<>();
   /** World save count at the last restore of each settling chunk. */
   private final Long2IntOpenHashMap settledAt = new Long2IntOpenHashMap();
   private int worldSaves;
   /** A /city reset was running when the journal was last saved: resume it on load. */
   public boolean restoring;

   public static long chunkKey(int cx, int cz) {
      return (long)cx & 0xFFFFFFFFL | ((long)cz & 0xFFFFFFFFL) << 32;
   }

   public static int chunkX(long key) {
      return (int)key;
   }

   public static int chunkZ(long key) {
      return (int)(key >>> 32);
   }

   /** Records the original of a position unless it already has one. Returns true when new. */
   public boolean record(BlockPos pos, BlockState original, @Nullable CompoundTag blockEntity) {
      long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
      Long2ObjectOpenHashMap<Entry> m = this.chunks.get(ck);
      if (m == null) {
         m = new Long2ObjectOpenHashMap<>();
         this.chunks.put(ck, m);
      }

      long p = pos.asLong();
      if (m.containsKey(p)) {
         return false;
      }

      m.put(p, new Entry(original, blockEntity));
      this.size++;
      this.dirty = true;
      return true;
   }

   public boolean contains(BlockPos pos) {
      Long2ObjectOpenHashMap<Entry> m = this.chunks.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
      return m != null && m.containsKey(pos.asLong());
   }

   /** Removes and returns the entries of one chunk (null when none). */
   @Nullable
   public Long2ObjectOpenHashMap<Entry> take(long chunkKey) {
      Long2ObjectOpenHashMap<Entry> m = this.chunks.remove(chunkKey);
      if (m != null) {
         this.size -= m.size();
         this.dirty = true;
      }

      return m;
   }

   /** Puts entries back (a chunk that could not be restored yet); existing newer originals win. */
   public void putBack(long chunkKey, Long2ObjectOpenHashMap<Entry> entries) {
      Long2ObjectOpenHashMap<Entry> m = this.chunks.get(chunkKey);
      if (m == null) {
         this.chunks.put(chunkKey, entries);
         this.size += entries.size();
      } else {
         for (Long2ObjectMap.Entry<Entry> e : entries.long2ObjectEntrySet()) {
            if (!m.containsKey(e.getLongKey())) {
               m.put(e.getLongKey(), e.getValue());
               this.size++;
            }
         }
      }

      this.dirty = true;
   }

   /**
    * The restore queue wrote these originals back into one chunk: keep them until the
    * world has saved that chunk (a crash before that leaves the damaged chunk on disk).
    */
   public void settle(long chunkKey, Long2ObjectOpenHashMap<Entry> originals) {
      Long2ObjectOpenHashMap<Entry> m = this.settling.get(chunkKey);
      if (m == null) {
         this.settling.put(chunkKey, new Long2ObjectOpenHashMap<>(originals));
      } else {
         for (Long2ObjectMap.Entry<Entry> e : originals.long2ObjectEntrySet()) {
            m.putIfAbsent(e.getLongKey(), e.getValue()); // the first original wins, as in the journal
         }
      }

      this.settledAt.put(chunkKey, this.worldSaves);
      this.dirty = true;
   }

   /**
    * The world saved its chunks (Forge posts the save event after the chunk save). A settling
    * chunk is dropped at the second save after its restore: the first one wrote it, the
    * second one is late enough that the chunk IO of the first has finished.
    */
   public void worldSaved() {
      if (!this.settling.isEmpty()) {
         var it = this.settling.long2ObjectEntrySet().iterator();
         while (it.hasNext()) {
            long key = it.next().getLongKey();
            if (this.settledAt.get(key) < this.worldSaves) {
               it.remove();
               this.settledAt.remove(key);
               this.dirty = true;
            }
         }
      }

      this.worldSaves++;
   }

   public long settlingSize() {
      long n = 0;
      for (Long2ObjectOpenHashMap<Entry> m : this.settling.values()) {
         n += m.size();
      }

      return n;
   }

   /**
    * The settling originals of positions the journal has no newer record for, as a journal
    * of their own (after a load: a crash may have kept the damaged chunks on disk). Putting
    * them back is a no-op where the restored blocks did reach the disk. The settling set
    * itself is kept until the world saves again.
    */
   public ChangeJournal recovery() {
      ChangeJournal r = new ChangeJournal();
      for (Long2ObjectMap.Entry<Long2ObjectOpenHashMap<Entry>> ce : this.settling.long2ObjectEntrySet()) {
         Long2ObjectOpenHashMap<Entry> own = this.chunks.get(ce.getLongKey());
         for (Long2ObjectMap.Entry<Entry> e : ce.getValue().long2ObjectEntrySet()) {
            if (own == null || !own.containsKey(e.getLongKey())) {
               r.recordRaw(ce.getLongKey(), e.getLongKey(), e.getValue());
            }
         }
      }

      r.dirty = false;
      return r;
   }

   private void recordRaw(long chunkKey, long pos, Entry e) {
      Long2ObjectOpenHashMap<Entry> m = this.chunks.computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>());
      if (m.putIfAbsent(pos, e) == null) {
         this.size++;
      }
   }

   public boolean hasChunk(long chunkKey) {
      return this.chunks.containsKey(chunkKey);
   }

   public List<Long> chunkKeys() {
      return new ArrayList<>(this.chunks.keySet());
   }

   public long size() {
      return this.size;
   }

   public int chunkCount() {
      return this.chunks.size();
   }

   public boolean isEmpty() {
      return this.size == 0;
   }

   public boolean dirty() {
      return this.dirty;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void clear() {
      this.chunks.clear();
      this.size = 0;
      this.dirty = true;
   }

   /** Snapshot to NBT (main thread); writing it to disk can happen elsewhere. */
   public CompoundTag save() {
      CompoundTag root = new CompoundTag();
      root.putInt("format", FORMAT);
      root.putBoolean("restoring", this.restoring);
      root.put("chunks", writeChunks(this.chunks));
      if (!this.settling.isEmpty()) {
         root.put("settling", writeChunks(this.settling));
      }

      root.putLong("size", this.size);
      this.dirty = false;
      return root;
   }

   private static ListTag writeChunks(Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks) {
      ListTag list = new ListTag();
      for (Long2ObjectMap.Entry<Long2ObjectOpenHashMap<Entry>> ce : chunks.long2ObjectEntrySet()) {
         Long2ObjectOpenHashMap<Entry> m = ce.getValue();
         if (m.isEmpty()) {
            continue;
         }

         CompoundTag c = new CompoundTag();
         c.putInt("x", chunkX(ce.getLongKey()));
         c.putInt("z", chunkZ(ce.getLongKey()));
         Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
         ListTag palette = new ListTag();
         long[] pos = new long[m.size()];
         int[] st = new int[m.size()];
         ListTag bes = new ListTag();
         int i = 0;
         for (Long2ObjectMap.Entry<Entry> e : m.long2ObjectEntrySet()) {
            BlockState s = e.getValue().state();
            int id = ids.getOrDefault(s, -1);
            if (id < 0) {
               id = palette.size();
               ids.put(s, id);
               palette.add(NbtUtils.writeBlockState(s));
            }

            pos[i] = e.getLongKey();
            st[i] = id;
            if (e.getValue().blockEntity() != null) {
               CompoundTag be = new CompoundTag();
               be.putInt("i", i);
               be.put("tag", e.getValue().blockEntity());
               bes.add(be);
            }

            i++;
         }

         c.put("palette", palette);
         c.putLongArray("pos", pos);
         c.putIntArray("state", st);
         if (!bes.isEmpty()) {
            c.put("be", bes);
         }

         list.add(c);
      }

      return list;
   }

   public static void write(CompoundTag snapshot, Path file) throws IOException {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      NbtIo.writeCompressed(snapshot, tmp.toFile());
      try {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public static ChangeJournal read(Path file, HolderGetter<Block> blocks) throws IOException {
      ChangeJournal j = new ChangeJournal();
      if (!Files.isRegularFile(file)) {
         return j;
      }

      CompoundTag root = NbtIo.readCompressed(file.toFile());
      j.restoring = root.getBoolean("restoring");
      j.size = readChunks(root.getList("chunks", Tag.TAG_COMPOUND), blocks, j.chunks);
      readChunks(root.getList("settling", Tag.TAG_COMPOUND), blocks, j.settling);
      for (long key : j.settling.keySet()) {
         j.settledAt.put(key, 0);
      }

      j.dirty = false;
      return j;
   }

   private static long readChunks(ListTag list, HolderGetter<Block> blocks, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> into) {
      long size = 0;
      for (int k = 0; k < list.size(); k++) {
         CompoundTag c = list.getCompound(k);
         long ck = chunkKey(c.getInt("x"), c.getInt("z"));
         ListTag palette = c.getList("palette", Tag.TAG_COMPOUND);
         BlockState[] states = new BlockState[palette.size()];
         for (int i = 0; i < states.length; i++) {
            states[i] = NbtUtils.readBlockState(blocks, palette.getCompound(i));
         }

         long[] pos = c.getLongArray("pos");
         int[] st = c.getIntArray("state");
         CompoundTag[] be = new CompoundTag[pos.length];
         ListTag bes = c.getList("be", Tag.TAG_COMPOUND);
         for (int i = 0; i < bes.size(); i++) {
            CompoundTag b = bes.getCompound(i);
            int idx = b.getInt("i");
            if (idx >= 0 && idx < be.length) {
               be[idx] = b.getCompound("tag");
            }
         }

         Long2ObjectOpenHashMap<Entry> m = new Long2ObjectOpenHashMap<>(pos.length);
         for (int i = 0; i < pos.length && i < st.length; i++) {
            if (st[i] >= 0 && st[i] < states.length) {
               m.put(pos[i], new Entry(states[st[i]], be[i]));
            }
         }

         if (!m.isEmpty()) {
            into.put(ck, m);
            size += m.size();
         }
      }

      return size;
   }
}
