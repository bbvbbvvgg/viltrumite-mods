package dev.baranhan.viltrumitecore.city.restore;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Puts journaled originals back, a bounded batch per tick: chunk by chunk (loading
 * unloaded chunks one or two at a time), bottom-up inside a chunk, without drops,
 * neighbour updates or journaling. Containers being replaced are emptied first so
 * nothing spills; debris (items, falling blocks, xp, primed TNT) in restored chunks is
 * removed and players inside restored blocks are lifted out. Side effects that still
 * happen (a torch popping, sand settling) are journaled normally and fixed by the next
 * pass; passes repeat until the journal is empty (at most {@link #MAX_PASSES}).
 */
public final class RestoreQueue {
   public static final int BLOCKS_PER_TICK = 6000;
   public static final int CHUNK_LOADS_PER_TICK = 2;
   public static final int MAX_PASSES = 4;
   private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

   private final ChangeJournal journal;
   private final ArrayDeque<Long> chunks = new ArrayDeque<>();
   private Long current;
   private List<Long2ObjectMap.Entry<ChangeJournal.Entry>> currentEntries;
   private Long2ObjectOpenHashMap<ChangeJournal.Entry> currentMap;
   private int cursor;
   private int pass;
   private boolean running;
   private long restoredBlocks;
   private int restoredChunks;
   private long startedAt;
   private final Consumer<String> report;
   private int waitTicks;

   public RestoreQueue(ChangeJournal journal, Consumer<String> report) {
      this.journal = journal;
      this.report = report;
   }

   public boolean running() {
      return this.running;
   }

   public long restoredBlocks() {
      return this.restoredBlocks;
   }

   public int pass() {
      return this.pass;
   }

   public int chunksLeft() {
      return this.chunks.size() + (this.current == null ? 0 : 1);
   }

   public void start(long now) {
      this.running = true;
      this.journal.restoring = true;
      this.journal.markDirty();
      this.pass = 0;
      this.restoredBlocks = 0;
      this.restoredChunks = 0;
      this.startedAt = now;
      this.nextPass();
   }

   private void nextPass() {
      this.pass++;
      this.chunks.clear();
      List<Long> keys = this.journal.chunkKeys();
      // a stable, spatially coherent order (row by row)
      keys.sort(Comparator.comparingInt(ChangeJournal::chunkZ).thenComparingInt(ChangeJournal::chunkX));
      this.chunks.addAll(keys);
   }

   /** Stops after the current chunk is written back; its remaining entries return to the journal. */
   public void abort() {
      if (this.current != null && this.currentMap != null) {
         Long2ObjectOpenHashMap<ChangeJournal.Entry> rest = new Long2ObjectOpenHashMap<>();
         for (int i = this.cursor; i < this.currentEntries.size(); i++) {
            rest.put(this.currentEntries.get(i).getLongKey(), this.currentEntries.get(i).getValue());
         }

         if (!rest.isEmpty()) {
            this.journal.putBack(this.current, rest);
         }
      }

      this.current = null;
      this.currentEntries = null;
      this.currentMap = null;
      this.chunks.clear();
      this.running = false;
   }

   public void tick(ServerLevel level, long now) {
      if (!this.running) {
         return;
      }

      if (this.waitTicks > 0) {
         this.waitTicks--;
         return;
      }

      int budget = BLOCKS_PER_TICK;
      int loads = 0;
      while (budget > 0) {
         if (this.current == null) {
            if (this.chunks.isEmpty()) {
               if (!this.journal.isEmpty() && this.pass < MAX_PASSES) {
                  this.nextPass();
                  this.waitTicks = 10; // let scheduled ticks (sand, liquids) settle first
                  return;
               }

               this.finish(now);
               return;
            }

            long key = this.chunks.peekFirst();
            int cx = ChangeJournal.chunkX(key);
            int cz = ChangeJournal.chunkZ(key);
            if (!level.hasChunk(cx, cz)) {
               if (loads >= CHUNK_LOADS_PER_TICK) {
                  return;
               }

               loads++;
            }

            this.chunks.pollFirst();
            level.getChunk(cx, cz); // loads (or generates nothing: the chunk exists) synchronously
            Long2ObjectOpenHashMap<ChangeJournal.Entry> m = this.journal.take(key);
            if (m == null || m.isEmpty()) {
               continue;
            }

            this.current = key;
            this.currentMap = m;
            this.currentEntries = new ArrayList<>(m.long2ObjectEntrySet());
            this.currentEntries.sort(Comparator.comparingInt(e -> BlockPos.getY(e.getLongKey())));
            this.cursor = 0;
         }

         int n = Math.min(budget, this.currentEntries.size() - this.cursor);
         ChangeJournal.suppressed = true;
         try {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < n; i++) {
               Long2ObjectMap.Entry<ChangeJournal.Entry> e = this.currentEntries.get(this.cursor + i);
               pos.set(e.getLongKey());
               restoreOne(level, pos.immutable(), e.getValue());
            }
         } finally {
            ChangeJournal.suppressed = false;
         }

         this.cursor += n;
         budget -= n;
         this.restoredBlocks += n;
         if (this.cursor >= this.currentEntries.size()) {
            this.afterChunk(level, ChangeJournal.chunkX(this.current), ChangeJournal.chunkZ(this.current));
            this.restoredChunks++;
            this.current = null;
            this.currentEntries = null;
            this.currentMap = null;
         }
      }
   }

   private static void restoreOne(ServerLevel level, BlockPos pos, ChangeJournal.Entry e) {
      BlockState now = level.getBlockState(pos);
      if (now == e.state() && e.blockEntity() == null) {
         return;
      }

      if (now.hasBlockEntity()) {
         BlockEntity old = level.getBlockEntity(pos);
         if (old != null) {
            Clearable.tryClear(old); // nothing spills out of a replaced container
         }
      }

      if (now != e.state()) {
         level.setBlock(pos, e.state(), FLAGS, 0);
      }

      if (e.blockEntity() != null) {
         BlockEntity be = level.getBlockEntity(pos);
         if (be != null) {
            be.load(e.blockEntity());
            be.setChanged();
            level.sendBlockUpdated(pos, e.state(), e.state(), Block.UPDATE_ALL);
         }
      }
   }

   private static void afterChunk(ServerLevel level, int cx, int cz) {
      AABB box = new AABB(cx << 4, level.getMinBuildHeight(), cz << 4, (cx << 4) + 16, level.getMaxBuildHeight(), (cz << 4) + 16);
      for (Entity en : level.getEntities((Entity)null, box, x -> x instanceof ItemEntity || x instanceof FallingBlockEntity || x instanceof ExperienceOrb || x instanceof PrimedTnt)) {
         en.discard();
      }

      for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
         if (!level.noCollision(p, p.getBoundingBox().deflate(0.05))) {
            BlockPos.MutableBlockPos m = p.blockPosition().mutable();
            for (int i = 0; i < 64 && !level.noCollision(p, p.getBoundingBox().move(0.0, m.getY() - p.getY(), 0.0).deflate(0.05)); i++) {
               m.move(0, 1, 0);
            }

            p.teleportTo(p.getX(), m.getY(), p.getZ());
         }
      }
   }

   private void finish(long now) {
      this.running = false;
      this.journal.restoring = false;
      this.journal.markDirty();
      long secs = Math.max(0, (now - this.startedAt) / 20);
      this.report.accept("done:" + this.restoredBlocks + ":" + this.restoredChunks + ":" + this.pass + ":" + secs + ":" + this.journal.size());
   }
}
