package dev.baranhan.viltrumitecore.worldevent.aftermath;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.restore.ChangeJournal;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Finds destruction without knowing who did it: the block hook ({@code mixin.CityJournalMixin}) reports every
 * solid block that turns into air, fire, fluid or a magma crater, counted per chunk. A chunk that loses
 * {@link #BURST_MIN} blocks within {@link #WINDOW} ticks becomes (or feeds) a ruin zone. The cause comes from
 * the latest {@link CityAftermath#hint} nearby; without one only the living city counts it (as plain
 * destruction), so a miner or a creeper in the wild never leaves a smoking ruin. Restores of the city journal
 * ({@link ChangeJournal#suppressed}) and blocks broken by players by hand are ignored.
 */
public final class AftermathTracker {
   public static final int BURST_MIN = 8;
   public static final int WINDOW = 20;
   static final int SEEDS_PER_BURST = 8;
   private static final int MAX_HINTS = 24;
   private static final int MAX_BURSTS = 512;
   private static final Map<Level, Long2ObjectOpenHashMap<Burst>> BURSTS = new IdentityHashMap<>();
   private static final Map<Level, ArrayDeque<Hint>> HINTS = new IdentityHashMap<>();
   private static final LongOpenHashSet HAND_BROKEN = new LongOpenHashSet();
   private static long handTick = Long.MIN_VALUE;

   private AftermathTracker() {
   }

   static final class Burst {
      final long first;
      int count;
      double sx;
      double sy;
      double sz;
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      final long[] seeds = new long[SEEDS_PER_BURST];
      int seedCount;

      Burst(long first) {
         this.first = first;
      }

      void add(BlockPos pos, Level level) {
         this.count++;
         this.sx += pos.getX() + 0.5;
         this.sy += pos.getY() + 0.5;
         this.sz += pos.getZ() + 0.5;
         this.minX = Math.min(this.minX, pos.getX());
         this.minY = Math.min(this.minY, pos.getY());
         this.minZ = Math.min(this.minZ, pos.getZ());
         this.maxX = Math.max(this.maxX, pos.getX());
         this.maxY = Math.max(this.maxY, pos.getY());
         this.maxZ = Math.max(this.maxZ, pos.getZ());
         if (this.seedCount < SEEDS_PER_BURST) {
            this.seeds[this.seedCount++] = pos.asLong();
         } else {
            int j = level.random.nextInt(this.count);
            if (j < SEEDS_PER_BURST) {
               this.seeds[j] = pos.asLong();
            }
         }
      }
   }

   record Hint(int causes, double x, double y, double z, float radius, long tick) {
   }

   /** True for a real destruction: a solid, non-replaceable block turned into air, fire, a fluid or a magma crater. */
   public static boolean destroyed(BlockState old, BlockState next) {
      if (old == next || old.isAir() || old.getBlock() instanceof BaseFireBlock || old.getBlock() instanceof LiquidBlock || old.canBeReplaced()) {
         return false;
      }

      if (next.isAir() || next.getBlock() instanceof BaseFireBlock || next.getBlock() instanceof LiquidBlock) {
         return true;
      }

      return next.is(Blocks.MAGMA_BLOCK) && !old.is(Blocks.MAGMA_BLOCK);
   }

   /** The block hook: called before every block write of a loaded chunk. */
   public static void onSetBlock(Level level, LevelChunk chunk, BlockPos pos, BlockState next) {
      if (level.isClientSide || ChangeJournal.suppressed || !(level instanceof ServerLevel sl) || !CityAftermath.enabled()) {
         return;
      }

      BlockState old = chunk.getBlockState(pos);
      if (!destroyed(old, next) || !sl.getServer().isSameThread()) {
         return;
      }

      if (handTick == sl.getGameTime() && HAND_BROKEN.contains(pos.asLong())) {
         return;
      }

      Long2ObjectOpenHashMap<Burst> map = BURSTS.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
      long key = ChangeJournal.chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
      Burst b = map.get(key);
      if (b == null) {
         if (map.size() >= MAX_BURSTS) {
            return;
         }

         b = new Burst(sl.getGameTime());
         map.put(key, b);
      }

      b.add(pos, level);
   }

   /** A player is breaking this block by hand (Forge BreakEvent): not a disaster. */
   public static void handBroken(ServerLevel level, BlockPos pos) {
      long now = level.getGameTime();
      if (handTick != now) {
         HAND_BROKEN.clear();
         handTick = now;
      }

      if (HAND_BROKEN.size() < 4096) {
         HAND_BROKEN.add(pos.asLong());
      }
   }

   static void hint(ServerLevel level, AftermathCause cause, double x, double y, double z, float radius) {
      ArrayDeque<Hint> hints = HINTS.computeIfAbsent(level, l -> new ArrayDeque<>());
      long now = level.getGameTime();
      Hint last = hints.peekLast();
      if (last != null && last.tick() == now && last.causes() == cause.bit()) {
         double dx = last.x() - x;
         double dz = last.z() - z;
         if (dx * dx + dz * dz < 64.0 && Math.abs(last.y() - y) < 8.0) {
            return; // the same blow, block after block
         }
      }

      hints.addLast(new Hint(cause.bit(), x, y, z, Math.max(1.0F, radius), now));
      while (hints.size() > MAX_HINTS) {
         hints.removeFirst();
      }
   }

   /** Generic causes only name a burst when no specific one (meteor, Chitauri, collapse...) covers it. */
   static final int WEAK = AftermathCause.HERO.bit() | AftermathCause.EXPLOSION.bit() | AftermathCause.DESTRUCTION.bit() | AftermathCause.OTHER.bit();

   /** Cause bits of the latest specific hint covering a point, else of the latest generic one, 0 when none. */
   static int causeAt(ServerLevel level, double x, double y, double z, long now) {
      ArrayDeque<Hint> hints = HINTS.get(level);
      if (hints == null || hints.isEmpty()) {
         return 0;
      }

      int weak = 0;
      Iterator<Hint> it = hints.descendingIterator();
      while (it.hasNext()) {
         Hint h = it.next();
         if (now - h.tick() > CityAftermath.HINT_TICKS) {
            break;
         }

         double r = h.radius() + 24.0;
         double dx = h.x() - x;
         double dy = h.y() - y;
         double dz = h.z() - z;
         if (dx * dx + dy * dy + dz * dz <= r * r) {
            if ((h.causes() & ~WEAK) != 0) {
               return h.causes();
            }

            if (weak == 0) {
               weak = h.causes();
            }
         }
      }

      return weak;
   }

   /** End of a level tick: bursts big enough become zones, stale small ones are dropped. */
   static void flush(ServerLevel level, long now) {
      ArrayDeque<Hint> hints = HINTS.get(level);
      if (hints != null) {
         while (!hints.isEmpty() && now - hints.peekFirst().tick() > CityAftermath.HINT_TICKS) {
            hints.removeFirst();
         }
      }

      Long2ObjectOpenHashMap<Burst> map = BURSTS.get(level);
      if (map == null || map.isEmpty()) {
         return;
      }

      boolean city = CityManager.of(level) != null;
      var it = map.long2ObjectEntrySet().fastIterator();
      while (it.hasNext()) {
         Burst b = it.next().getValue();
         if (b.count >= BURST_MIN) {
            double cx = b.sx / b.count;
            double cy = b.sy / b.count;
            double cz = b.sz / b.count;
            int causes = causeAt(level, cx, cy, cz, now);
            if (causes == 0 && city && CityManager.of(level).profile().inArena((int)Math.floor(cx), (int)Math.floor(cy), (int)Math.floor(cz))) {
               causes = AftermathCause.DESTRUCTION.bit();
            }

            if (causes != 0) {
               float r = AftermathRules.burstRadius(b.maxX - b.minX + 1, b.maxY - b.minY + 1, b.maxZ - b.minZ + 1);
               CityAftermath.burst(level, causes, cx, cy, cz, r, b.count, b.seeds, b.seedCount);
            }

            it.remove();
         } else if (now - b.first > WINDOW) {
            it.remove();
         }
      }
   }

   static void forget(Level level) {
      Long2ObjectOpenHashMap<Burst> map = BURSTS.get(level);
      if (map != null) {
         map.clear();
      }
   }

   static void unload(Level level) {
      BURSTS.remove(level);
      HINTS.remove(level);
   }
}
