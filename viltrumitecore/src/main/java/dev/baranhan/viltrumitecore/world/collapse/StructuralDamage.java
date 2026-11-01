package dev.baranhan.viltrumitecore.world.collapse;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Where structural damage comes from. Destructive code runs inside a cause scope
 * ({@link #run(CollapseCause, Runnable)} / {@link #orDefault}); every solid block that turns into air inside a
 * scope is recorded in the level's {@link DamageLedger} by the existing {@code CityJournalMixin} hook
 * (LevelChunk.setBlockState HEAD). Outside a scope nothing is recorded, so mining, building, fluids, pistons
 * and world generation never feed a collapse. Collapse writes run {@link #suppressed()}: a collapse can never
 * feed another collapse (no recursion). Server thread only.
 */
public final class StructuralDamage {
   @Nullable
   private static CollapseCause scope;
   private static int suppress;
   private static final Map<ResourceKey<Level>, DamageLedger> LEDGERS = new HashMap<>();

   private StructuralDamage() {
   }

   /** Runs destructive code with this cause; an outer scope is replaced for the duration. */
   public static void run(CollapseCause cause, Runnable body) {
      CollapseCause before = scope;
      scope = cause;
      try {
         body.run();
      } finally {
         scope = before;
      }
   }

   public static <T> T call(CollapseCause cause, Supplier<T> body) {
      CollapseCause before = scope;
      scope = cause;
      try {
         return body.get();
      } finally {
         scope = before;
      }
   }

   /** Runs with {@code fallback} only when no scope is active (an outer, more specific cause wins). */
   public static <T> T orDefault(CollapseCause fallback, Supplier<T> body) {
      return scope != null ? body.get() : call(fallback, body);
   }

   public static void orDefault(CollapseCause fallback, Runnable body) {
      if (scope != null) {
         body.run();
      } else {
         run(fallback, body);
      }
   }

   /** The active cause, or null. Deferred work (CarveJobs) captures it at submit time. */
   @Nullable
   public static CollapseCause current() {
      return scope;
   }

   /** Collapse writes: nothing inside is recorded. */
   public static void suppressed(Runnable body) {
      suppress++;
      try {
         body.run();
      } finally {
         suppress--;
      }
   }

   public static boolean isSuppressed() {
      return suppress > 0;
   }

   public static DamageLedger ledger(ServerLevel level) {
      return LEDGERS.computeIfAbsent(level.dimension(), k -> new DamageLedger());
   }

   public static void forget(ResourceKey<Level> dimension) {
      LEDGERS.remove(dimension);
   }

   public static void clearAll() {
      LEDGERS.clear();
      scope = null;
      suppress = 0;
   }

   /** Called from CityJournalMixin before every block write of a loaded chunk. */
   public static void onSetBlock(Level level, LevelChunk chunk, BlockPos pos, BlockState next) {
      CollapseCause cause = scope;
      if (cause == null || suppress > 0 || !cause.feeds() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
         return;
      }
      BlockState old = chunk.getBlockState(pos);
      if (counts(old, next)) {
         ledger(server).record(pos.getX(), pos.getY(), pos.getZ(), cause.weight(), server.getGameTime(), cause);
         CollapseDirector.noteCause(server, cause);
      }
   }

   /** Blocks an explosion is about to remove (ExplosionEvent.Detonate). */
   public static void recordExplosion(ServerLevel level, List<BlockPos> toBlow, CollapseCause cause) {
      if (!cause.feeds() || suppress > 0) {
         return;
      }
      DamageLedger ledger = ledger(level);
      long now = level.getGameTime();
      for (BlockPos pos : toBlow) {
         if (level.isLoaded(pos) && counts(level.getBlockState(pos), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
            ledger.record(pos.getX(), pos.getY(), pos.getZ(), cause.weight(), now, cause);
         }
      }
   }

   /**
    * An explicit heavy impact at a point (meteor, Leviathan crash, a future Ultron strike). It does not remove
    * blocks by itself; it makes the damage around it settle faster, so phase 1 lands on the hit.
    */
   public static void impact(ServerLevel level, Vec3 at, CollapseCause cause) {
      if (cause.feeds()) {
         ledger(level).impact((int)Math.floor(at.x), (int)Math.floor(at.y), (int)Math.floor(at.z), level.getGameTime());
      }
   }

   /** A solid, structural-looking block turned into air, fluid or something replaceable. */
   static boolean counts(BlockState old, BlockState next) {
      if (old.isAir() || !old.getFluidState().isEmpty() || old.canBeReplaced()) {
         return false;
      }
      return next.isAir() || !next.getFluidState().isEmpty() || next.canBeReplaced();
   }

   // ------------------------------------------------------------------ classification for the analyzer

   /** Reads a box without loading chunks: unloaded columns are {@link CollapseAnalyzer#UNKNOWN}. */
   public static CollapseAnalyzer.Volume volume(ServerLevel level) {
      Map<BlockState, Byte> cache = new IdentityHashMap<>();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      return new CollapseAnalyzer.Volume() {
         int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
         LevelChunk chunk;

         @Override
         public byte at(int x, int y, int z) {
            int cx = x >> 4, cz = z >> 4;
            if (cx != this.lastCx || cz != this.lastCz) {
               this.lastCx = cx;
               this.lastCz = cz;
               this.chunk = level.getChunkSource().getChunkNow(cx, cz);
            }
            if (this.chunk == null) {
               return CollapseAnalyzer.UNKNOWN;
            }
            pos.set(x, y, z);
            BlockState state = this.chunk.getBlockState(pos);
            Byte b = cache.get(state);
            if (b == null) {
               b = classify(level, pos, state);
               cache.put(state, b);
            }
            return b;
         }
      };
   }

   static byte classify(ServerLevel level, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return CollapseAnalyzer.AIR;
      }
      if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
         return CollapseAnalyzer.FLUID;
      }
      if (state.getDestroySpeed(level, pos) < 0.0F) {
         return CollapseAnalyzer.FIXED;
      }
      if (state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty()) {
         return CollapseAnalyzer.AIR; // grass, flowers, snow layers: no support, never part of a section
      }
      if (state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SAND)
         || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.is(BlockTags.NYLIUM)
         || state.is(net.minecraftforge.common.Tags.Blocks.ORES) || state.is(net.minecraftforge.common.Tags.Blocks.GRAVEL)
         || state.getBlock() instanceof FallingBlock) {
         return CollapseAnalyzer.NATURAL;
      }
      return CollapseAnalyzer.STRUCTURE;
   }
}
