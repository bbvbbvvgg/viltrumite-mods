package dev.baranhan.viltrumitecore.city.restore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Journal persistence and the crash safety of restored chunks (settling set). */
class ChangeJournalTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   private static long key(BlockPos p) {
      return ChangeJournal.chunkKey(p.getX() >> 4, p.getZ() >> 4);
   }

   @Test
   void firstOriginalWinsAndBlockEntitiesRoundTrip(@TempDir Path dir) throws Exception {
      ChangeJournal j = new ChangeJournal();
      BlockPos a = new BlockPos(3, 70, -5);
      CompoundTag chest = new CompoundTag();
      chest.putString("id", "minecraft:chest");
      chest.putString("CustomName", "{\"text\":\"Shop\"}");
      assertTrue(j.record(a, Blocks.CHEST.defaultBlockState(), chest));
      assertFalse(j.record(a, Blocks.AIR.defaultBlockState(), null), "a later change keeps the first original");
      assertTrue(j.record(new BlockPos(-20, 64, 40), Blocks.STONE_BRICKS.defaultBlockState(), null));
      Path f = dir.resolve(ChangeJournal.FILE);
      ChangeJournal.write(j.save(), f);
      ChangeJournal r = ChangeJournal.read(f, BuiltInRegistries.BLOCK.asLookup());
      assertEquals(2, r.size());
      assertEquals(2, r.chunkCount());
      ChangeJournal.Entry e = r.take(key(a)).get(a.asLong());
      assertEquals(Blocks.CHEST.defaultBlockState(), e.state());
      assertNotNull(e.blockEntity());
      assertEquals("{\"text\":\"Shop\"}", e.blockEntity().getString("CustomName"));
      assertFalse(Files.exists(dir.resolve(ChangeJournal.FILE + ".tmp")), "atomic replace leaves no temp file");
   }

   @Test
   void restoredChunksSurviveACrashUntilTheWorldSavedThemTwice(@TempDir Path dir) throws Exception {
      ChangeJournal j = new ChangeJournal();
      BlockPos a = new BlockPos(1, 65, 1);
      BlockPos b = new BlockPos(2, 66, 1);
      j.record(a, Blocks.BRICKS.defaultBlockState(), null);
      j.record(b, Blocks.GLASS.defaultBlockState(), null);
      // the reset takes the chunk and writes it back
      Long2ObjectOpenHashMap<ChangeJournal.Entry> taken = j.take(key(a));
      j.settle(key(a), taken);
      assertTrue(j.isEmpty(), "the reset emptied the journal");
      assertEquals(2, j.settlingSize());
      // the journal is written before the world saved the chunk, then the server crashes
      Path f = dir.resolve(ChangeJournal.FILE);
      ChangeJournal.write(j.save(), f);
      ChangeJournal r = ChangeJournal.read(f, BuiltInRegistries.BLOCK.asLookup());
      assertTrue(r.isEmpty());
      ChangeJournal lost = r.recovery();
      assertEquals(2, lost.size(), "the next start can put the damaged chunk back");
      assertEquals(Blocks.GLASS.defaultBlockState(), lost.take(key(b)).get(b.asLong()).state());

      // without a crash: the first save writes the chunk, the second drops the originals
      j.worldSaved();
      assertEquals(2, j.settlingSize(), "the chunk IO of the first save may still run");
      j.worldSaved();
      assertEquals(0, j.settlingSize());
      assertTrue(j.recovery().isEmpty());
   }

   @Test
   void recoveryNeverOverridesANewerRecordAndResettlingRestartsTheClock() {
      ChangeJournal j = new ChangeJournal();
      BlockPos a = new BlockPos(17, 70, 17);
      BlockPos b = new BlockPos(18, 70, 17);
      j.record(a, Blocks.OAK_PLANKS.defaultBlockState(), null);
      j.record(b, Blocks.OAK_PLANKS.defaultBlockState(), null);
      j.settle(key(a), j.take(key(a)));
      // after the reset a player breaks one restored block again: the journal records it anew
      j.record(a, Blocks.OAK_PLANKS.defaultBlockState(), null);
      ChangeJournal lost = j.recovery();
      assertEquals(1, lost.size(), "only positions without a newer record are recovered");
      assertNull(lost.take(key(a)).get(a.asLong()));

      j.worldSaved();
      // a second reset restores the chunk again before the next save: it must wait two saves again
      j.settle(key(a), j.take(key(a)));
      j.worldSaved();
      assertEquals(2, j.settlingSize());
      j.worldSaved();
      assertEquals(0, j.settlingSize());
   }

   @Test
   void putBackKeepsNewerOriginals() {
      ChangeJournal j = new ChangeJournal();
      BlockPos a = new BlockPos(0, 64, 0);
      Long2ObjectOpenHashMap<ChangeJournal.Entry> rest = new Long2ObjectOpenHashMap<>();
      rest.put(a.asLong(), new ChangeJournal.Entry(Blocks.STONE.defaultBlockState(), null));
      rest.put(a.above().asLong(), new ChangeJournal.Entry(Blocks.DIRT.defaultBlockState(), null));
      j.record(a, Blocks.GOLD_BLOCK.defaultBlockState(), null);
      j.putBack(key(a), rest);
      assertEquals(2, j.size(), "no duplicate per position");
      assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), j.take(key(a)).get(a.asLong()).state());
   }
}
