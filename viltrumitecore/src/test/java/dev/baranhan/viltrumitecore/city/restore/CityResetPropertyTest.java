package dev.baranhan.viltrumitecore.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.restore.ChangeJournal;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Property test of the restore loop (T22): populate (the ambient life of the city) -> a catastrophe
 * (deterministic random hits on the blocks) -> /city reset = 0 differing blocks against the
 * reference map, over many fixed seeds. It runs the pure classes and rules the server path uses -
 * the journal hook of {@link CityManager#onSetBlock} (first original wins, ambient-only changes are
 * not journaled), {@link ChangeJournal} (take/settle/worldSaved, crash recovery) and the chunk
 * chunk-by-chunk, originals-back loop of RestoreQueue - as a flat simulation: a map of position ->
 * state instead of a level. No Minecraft world is started, like the other city tests; on real world
 * files the same check is tools/atheria/analysis/compare.py.
 */
class CityResetPropertyTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   /** The flat world of the simulation plus the journal the LevelChunk hook would fill. */
   private static final class Sim {
      final Map<Long, BlockState> blocks = new HashMap<>();
      final ChangeJournal journal = new ChangeJournal();

      /** CityManager.onSetBlock in miniature: true when the original was journaled. */
      boolean set(BlockPos pos, BlockState next) {
         BlockState old = this.blocks.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
         if (old == next) {
            return false;
         }

         if (old.getBlock() == next.getBlock() && CityManager.ambientOnly(old, next)) {
            this.blocks.put(pos.asLong(), next); // the map living on its own: day/night, growth, redstone
            return false;
         }

         boolean fresh = !this.journal.contains(pos); // the first original wins
         if (fresh) {
            CompoundTag be = old.getBlock() == Blocks.CHEST ? chestTag(pos) : null;
            assertTrue(this.journal.record(pos, old, be), "a change of a not yet journaled position is recorded");
         }

         this.blocks.put(pos.asLong(), next);
         return fresh;
      }

      void put(BlockPos pos, BlockState state) {
         this.blocks.put(pos.asLong(), state); // the prepared city, before the journal watches it
      }
   }

   private static CompoundTag chestTag(BlockPos pos) {
      CompoundTag t = new CompoundTag();
      t.putString("id", "minecraft:chest");
      t.putInt("x", pos.getX());
      t.putInt("y", pos.getY());
      t.putInt("z", pos.getZ());
      return t;
   }

   /** The prepared city: pavement, towers, a chest, candles, redstone, wheat, a detector, stairs. */
   private static Sim city() {
      Sim s = new Sim();
      Random r = new Random(100);
      for (int x = 0; x < 64; x++) {
         for (int z = 0; z < 64; z++) {
            s.put(new BlockPos(x, 60, z), Blocks.STONE.defaultBlockState());
            if (r.nextInt(10) == 0) {
               int h = 4 + r.nextInt(8);
               for (int y = 61; y <= 60 + h; y++) {
                  s.put(new BlockPos(x, y, z), y == 60 + h ? Blocks.BRICKS.defaultBlockState() : Blocks.GLASS.defaultBlockState());
               }
            }
         }
      }

      s.put(new BlockPos(10, 61, 10), Blocks.CHEST.defaultBlockState());
      s.put(new BlockPos(12, 61, 12), Blocks.CANDLE.defaultBlockState());
      s.put(new BlockPos(20, 61, 20), Blocks.REDSTONE_WIRE.defaultBlockState());
      s.put(new BlockPos(24, 61, 24), Blocks.OAK_STAIRS.defaultBlockState());
      s.put(new BlockPos(28, 61, 28), Blocks.WHEAT.defaultBlockState());
      s.put(new BlockPos(32, 61, 32), Blocks.LEVER.defaultBlockState());
      return s;
   }

   /** The city is alive: ambient properties change by themselves and are never journaled. */
   private static void populate(Sim s, Random r) {
      for (int i = 0; i < 400; i++) {
         switch (r.nextInt(4)) {
            case 0 -> s.set(new BlockPos(12, 61, 12), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, r.nextBoolean()));
            case 1 -> s.set(new BlockPos(20, 61, 20), Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, r.nextInt(16)));
            case 2 -> s.set(new BlockPos(28, 61, 28), Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, r.nextInt(8)));
            default -> s.set(new BlockPos(32, 61, 32), Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, r.nextBoolean()));
         }
      }
   }

   /** The catastrophe: deterministic random hits on the blocks. Returns how many were journaled. */
   private static int catastrophe(Sim s, Random r, int hits) {
      int journaled = 0;
      for (int i = 0; i < hits; i++) {
         BlockPos p = new BlockPos(r.nextInt(64), 60 + r.nextInt(21), r.nextInt(64));
         BlockState next = switch (r.nextInt(4)) {
            case 0 -> Blocks.AIR.defaultBlockState();
            case 1 -> Blocks.STONE.defaultBlockState();
            case 2 -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.OAK_PLANKS.defaultBlockState();
         };
         if (s.set(p, next)) {
            journaled++;
         }
      }

      return journaled;
   }

   /** /city reset in miniature (RestoreQueue.tick): originals back chunk by chunk, then two world saves. */
   private static void reset(Sim s) {
      for (long key : s.journal.chunkKeys()) {
         Long2ObjectOpenHashMap<ChangeJournal.Entry> taken = s.journal.take(key);
         if (taken == null) {
            continue;
         }

         for (Long2ObjectMap.Entry<ChangeJournal.Entry> e : taken.long2ObjectEntrySet()) {
            s.blocks.put(e.getLongKey(), e.getValue().state());
         }

         s.journal.settle(key, taken);
      }

      s.journal.worldSaved();
      s.journal.worldSaved();
   }

   /** Every position where the two maps differ (missing = air), like tools/atheria/analysis/compare.py. */
   private static long differences(Map<Long, BlockState> after, Map<Long, BlockState> reference) {
      Set<Long> keys = new HashSet<>(after.keySet());
      keys.addAll(reference.keySet());
      BlockState air = Blocks.AIR.defaultBlockState();
      long n = 0;
      for (long key : keys) {
         if (after.getOrDefault(key, air) != reference.getOrDefault(key, air)) {
            n++;
         }
      }

      return n;
   }

   @Test
   void catastropheThenResetLeavesZeroDifferences() {
      for (int seed = 1; seed <= 16; seed++) {
         Sim city = city();
         populate(city, new Random(1000 + seed));
         assertTrue(city.journal.isEmpty(), "the ambient life of the city journals nothing (seed " + seed + ")");
         Map<Long, BlockState> reference = new HashMap<>(city.blocks);
         int journaled = catastrophe(city, new Random(seed), 300);
         assertTrue(journaled > 0, "a catastrophe journals something (seed " + seed + ")");
         assertEquals(journaled, city.journal.size(), "exactly the real changes are journaled, the first original wins (seed " + seed + ")");
         reset(city);
         assertEquals(0L, differences(city.blocks, reference), "populate -> catastrophe -> reset = 0 differences (seed " + seed + ")");
         assertTrue(city.journal.isEmpty(), "the reset emptied the journal (seed " + seed + ")");
         assertEquals(0L, city.journal.settlingSize(), "and emptied the settling set (seed " + seed + ")");
      }
   }

   @Test
   void repeatedCatastrophesAndResetsAlwaysReturnToTheReference() {
      Sim city = city();
      populate(city, new Random(7));
      Map<Long, BlockState> reference = new HashMap<>(city.blocks);
      Random r = new Random(42);
      for (int round = 0; round < 5; round++) {
         int journaled = catastrophe(city, r, 120);
         assertEquals(journaled, city.journal.size(), "round " + round);
         reset(city);
         assertEquals(0L, differences(city.blocks, reference), "round " + round);
         assertTrue(city.journal.isEmpty());
      }
   }

   @Test
   void aCrashRightAfterTheResetStillRestoresEverything() {
      for (int seed = 1; seed <= 8; seed++) {
         Sim city = city();
         populate(city, new Random(500 + seed));
         Map<Long, BlockState> reference = new HashMap<>(city.blocks);
         int journaled = catastrophe(city, new Random(seed), 200);
         assertTrue(journaled > 0, "a catastrophe journals something (seed " + seed + ")");
         // the reset, killed after the chunks were written back but before the world saved them twice
         for (long key : city.journal.chunkKeys()) {
            Long2ObjectOpenHashMap<ChangeJournal.Entry> taken = city.journal.take(key);
            if (taken == null) {
               continue;
            }

            for (Long2ObjectMap.Entry<ChangeJournal.Entry> e : taken.long2ObjectEntrySet()) {
               city.blocks.put(e.getLongKey(), e.getValue().state());
            }

            city.journal.settle(key, taken);
         }

         city.journal.worldSaved(); // the first save lands, then the server dies
         ChangeJournal lost = city.journal.recovery();
         assertTrue(lost.size() > 0, "the crash left something to recover (seed " + seed + ")");
         // the next start puts the lost chunks back (a RestoreQueue over the recovery journal)
         for (long key : lost.chunkKeys()) {
            Long2ObjectOpenHashMap<ChangeJournal.Entry> taken = lost.take(key);
            if (taken == null) {
               continue;
            }

            for (Long2ObjectMap.Entry<ChangeJournal.Entry> e : taken.long2ObjectEntrySet()) {
               city.blocks.put(e.getLongKey(), e.getValue().state());
            }
         }

         assertEquals(0L, differences(city.blocks, reference), "the recovery returns everything (seed " + seed + ")");
         city.journal.worldSaved(); // the second save drops the settling set
         assertEquals(0L, city.journal.settlingSize());
      }
   }

   @Test
   void ambientChurnNeverEntersTheJournalAndNeverSpoilsTheReset() {
      Sim city = city();
      populate(city, new Random(11));
      assertTrue(city.journal.isEmpty(), "day/night, redstone and growth are not journaled");
      Map<Long, BlockState> reference = new HashMap<>(city.blocks);
      BlockPos stairs = new BlockPos(24, 61, 24);
      assertFalse(city.set(stairs, Blocks.OAK_STAIRS.defaultBlockState()), "nothing changed");
      assertTrue(city.set(stairs, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP)),
         "the same block with a non-ambient property is a real change");
      assertEquals(1L, city.journal.size());
      assertFalse(city.set(stairs, Blocks.OAK_PLANKS.defaultBlockState()), "a second change keeps the first original");
      assertEquals(1L, city.journal.size());
      reset(city);
      assertEquals(0L, differences(city.blocks, reference), "the reset returns the first original");
      assertTrue(city.journal.isEmpty());
      // an empty journal: a reset without a catastrophe is a no-op
      reset(city);
      assertEquals(0L, differences(city.blocks, reference));
   }
}
