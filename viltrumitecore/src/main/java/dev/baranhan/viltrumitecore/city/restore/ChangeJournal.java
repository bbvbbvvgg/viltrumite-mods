package dev.baranhan.viltrumitecore.city.restore;

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
      ListTag list = new ListTag();
      for (Long2ObjectMap.Entry<Long2ObjectOpenHashMap<Entry>> ce : this.chunks.long2ObjectEntrySet()) {
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

      root.put("chunks", list);
      root.putLong("size", this.size);
      this.dirty = false;
      return root;
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
      ListTag list = root.getList("chunks", Tag.TAG_COMPOUND);
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
            j.chunks.put(ck, m);
            j.size += m.size();
         }
      }

      j.dirty = false;
      return j;
   }
}
