package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.power.PowerRules;
import dev.baranhan.viltrumitecore.city.restore.ChangeJournal;
import dev.baranhan.viltrumitecore.city.restore.RestoreQueue;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathStage;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * One lifecycle of a collapsed building on pure classes (no Minecraft world, no running server), in the
 * order the real systems run: settled {@link DamageLedger} damage → {@link CollapseAnalyzer} plan → the
 * collapse phases remove the section → the {@code CityCollapseEvent.Finished} record (the contract of
 * {@code docs/city-collapse-integration.md}) → an aftermath zone and a grid hit built from the same info
 * (the {@code CityDisasterLinks} shape) → every removed block journaled → {@link RestoreQueue} writes every
 * original back, the journal ends empty and the city shows 0 differences.
 *
 * <p>Production runs this through a real {@code ServerLevel}: the strike clears blocks in a damage scope,
 * the {@code CityJournalMixin} hook journals each first original, the director posts the events, the
 * {@code RestoreQueue} walks real chunks. On pure classes those seams are the fake {@link City} volume,
 * the real {@link CollapseAnalyzer}, {@link CollapseRules}, {@link AftermathRules}, {@link PowerRules},
 * {@link ChangeJournal} and {@link RestoreQueue} through its {@link RestoreQueue.Access} adapter — the
 * same production code paths, only the world is a map.
 *
 * <p>The test falls when the chain breaks: damage that never settles, an analyzer that rejects a severed
 * tower or accepts terrain, a plan without blocks, an aftermath/power pipeline that ignores a real
 * collapse, a journal that loses originals or records them twice, a restore that leaves the journal dirty,
 * blocks still gone after the reset, or a settling set that never drains.
 */
class CollapseLifecycleTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   /** A sparse city as in {@code CollapseAnalyzerTest}: NATURAL street at y <= 63, set cells override, else air. */
   static final class City implements CollapseAnalyzer.Volume {
      final Map<Long, Byte> cells = new HashMap<>();
      /** First recorded original per position (the journal keeps the first write of a position). */
      final Map<Long, ChangeJournal.Entry> journal = new HashMap<>();
      /** Positions that lost their block (the hole of the strike + the removed section). */
      final List<Long> damaged = new ArrayList<>();

      void building(int x0, int z0, int size, int rows) {
         this.box(x0, 64, z0, x0 + size - 1, 63 + rows, z0 + size - 1, CollapseAnalyzer.STRUCTURE);
      }

      void box(int x0, int y0, int z0, int x1, int y1, int z1, byte m) {
         for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
               for (int z = z0; z <= z1; z++) {
                  this.cells.put(BlockKey.of(x, y, z), m);
               }
            }
         }
      }

      /**
       * The hero strike: removes the box (solid blocks only), journals each first original, returns the
       * removed positions — exactly what the damage scope plus the journal hook produce in production.
       */
      List<Long> strike(int x0, int y0, int z0, int x1, int y1, int z1) {
         LongArrayList out = new LongArrayList();
         for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
               for (int z = z0; z <= z1; z++) {
                  long k = BlockKey.of(x, y, z);
                  byte m = this.cells.getOrDefault(k, y <= 63 ? CollapseAnalyzer.NATURAL : CollapseAnalyzer.AIR);
                  if (m == CollapseAnalyzer.AIR) {
                     continue;
                  }
                  this.journal.putIfAbsent(k, new ChangeJournal.Entry(Blocks.STONE_BRICKS.defaultBlockState(), (CompoundTag) null));
                  this.cells.put(k, CollapseAnalyzer.AIR);
                  this.damaged.add(k);
                  out.add(k);
               }
            }
         }
         List<Long> list = new ArrayList<>(out.size());
         for (int i = 0; i < out.size(); i++) {
            list.add(out.getLong(i));
         }
         return list;
      }

      /** The collapse phases 2-3: the plan's section comes down (same journaling as the strike). */
      List<Long> removeSection(long[] section) {
         LongArrayList out = new LongArrayList();
         for (long k : section) {
            if (this.cells.containsKey(k) && this.cells.get(k) != CollapseAnalyzer.AIR) {
               this.journal.putIfAbsent(k, new ChangeJournal.Entry(Blocks.STONE_BRICKS.defaultBlockState(), (CompoundTag) null));
               this.cells.put(k, CollapseAnalyzer.AIR);
               this.damaged.add(k);
               out.add(k);
            }
         }
         List<Long> list = new ArrayList<>(out.size());
         for (int i = 0; i < out.size(); i++) {
            list.add(out.getLong(i));
         }
         return list;
      }

      @Override
      public byte at(int x, int y, int z) {
         long k = BlockKey.of(x, y, z);
         if (this.cells.containsKey(k)) {
            return this.cells.get(k);
         }
         return y <= 63 ? CollapseAnalyzer.NATURAL : CollapseAnalyzer.AIR;
      }

      /** One tick of {@code /city reset}: the queue writes its originals back into this fake world. */
      void tickRestore(RestoreQueue queue, long now) {
         queue.tick(new RestoreQueue.Access() {
            @Override
            public boolean hasChunk(int cx, int cz) {
               return true; // every chunk of the fake city is loaded
            }

            @Override
            public void loadChunk(int cx, int cz) {
               // nothing to load
            }

            @Override
            public void restore(BlockPos pos, ChangeJournal.Entry original) {
               City.this.cells.put(BlockKey.of(pos.getX(), pos.getY(), pos.getZ()), CollapseAnalyzer.STRUCTURE);
            }

            @Override
            public void chunkRestored(int cx, int cz) {
               // no debris or players in the fake world
            }
         }, now);
      }

      /** Block class at a position (for the 0-difference check after the reset). */
      byte state(int x, int y, int z) {
         return this.at(x, y, z);
      }
   }

   @Test
   void collapsedTowerIsJournaledAndFullyRestored() {
      // ---------------------------------------------------------------- setup: a tower, a hero strike at its base
      City city = new City();
      city.building(0, 0, 6, 20); // 6 x 6 footprint, y 64..83
      long now = 1000L;
      List<Long> hole = city.strike(0, 64, 0, 5, 66, 5); // the bottom three storeys are gone
      assertEquals(108, hole.size());

      DamageLedger ledger = new DamageLedger();
      for (long k : hole) {
         ledger.record(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k), CollapseCause.HERO.weight(), now, CollapseCause.HERO);
      }
      assertTrue(ledger.size() == hole.size(), "every damaged position is in the ledger once");

      // ---------------------------------------------------------------- settle: the quiet cluster asks for analysis
      List<DamageLedger.Candidate> found = ledger.settled(now + CollapseRules.SETTLE_TICKS, 1);
      assertEquals(1, found.size(), "one settled building");
      DamageLedger.Candidate c = found.get(0);
      assertTrue(c.score() >= CollapseRules.SCORE_THRESHOLD, "score " + c.score());
      assertTrue(ledger.settled(now + CollapseRules.SETTLE_TICKS + 1, 1).isEmpty(), "the candidate is consumed once");

      // ---------------------------------------------------------------- analyze: the storeys above the cut hang in the air
      LongArrayList near = ledger.around(c.x(), c.y(), c.z(), CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP);
      CollapseAnalyzer.Request req = new CollapseAnalyzer.Request(c.x(), c.y(), c.z(), near.toLongArray(), c.score(), CollapseCause.HERO,
         42L, CollapseRules.DEFAULT_MAX_BLOCKS, -64, 319);
      CollapseAnalyzer.Plan plan = CollapseAnalyzer.analyze(city, req);
      assertTrue(plan.accepted(), plan.reason());
      assertEquals(CollapseAnalyzer.Rule.SEVERED, plan.rule());
      assertNotEquals(0, plan.blocks().length);
      assertTrue(plan.blocks().length <= CollapseRules.DEFAULT_MAX_BLOCKS);
      assertTrue(plan.groundY() < plan.minY(), "the section lands on something below it");

      // ---------------------------------------------------------------- timeline numbers come from CollapseRules
      int creak = CollapseRules.creakTicks(plan.variant(), plan.severity(), 42L);
      int collapseAt = CollapseRules.IMPACT_TICKS + creak;
      int fall = CollapseRules.impactTicks(plan.variant(), plan.minY() - plan.groundY() - 1, plan.maxY() - plan.minY() + 1);
      assertTrue(creak > 0);
      assertTrue(collapseAt > CollapseRules.IMPACT_TICKS);
      assertTrue(fall > 0, "a severed section always has a fall time");

      // ---------------------------------------------------------------- phases 2-3: the section comes down
      List<Long> fell = city.removeSection(plan.blocks());
      assertTrue(fell.size() >= CollapseRules.MIN_SECTION, "a whole section was removed: " + fell.size());

      // ---------------------------------------------------------------- Finished: the events carry the real record
      List<BlockPos> affected = new ArrayList<>();
      Map<BlockPos, BlockState> originals = new HashMap<>();
      for (long k : city.damaged) { // the strike hole + the section: every block the disaster changed
         affected.add(BlockPos.of(k));
         originals.put(BlockPos.of(k), city.journal.get(k).state());
      }
      assertEquals(city.damaged.size(), affected.size());
      assertEquals(new ArrayList<>(affected.stream().map(BlockPos::asLong).distinct().toList()).size(), affected.size(), "no duplicates");
      for (BlockPos p : affected) {
         assertEquals(Blocks.STONE_BRICKS.defaultBlockState(), originals.get(p));
      }

      // ---------------------------------------------------------------- aftermath: the links build one zone from the impact
      String zoneId = "collapse:1";
      AftermathEvent zone = new AftermathEvent(zoneId, "minecraft:overworld", planCenter(plan), plan.radius(), plan.severity().strength(),
         AftermathCause.HERO.id, affected, now);
      assertEquals(zoneId, zone.eventId());
      assertEquals(AftermathCause.HERO, zone.causeType(), "a hero collapse is a hero ruin, not OTHER");
      float f = AftermathRules.durationFactor(zone.severity(), 1.0);
      assertEquals(AftermathStage.IMPACT, AftermathRules.stage(0, f), "the zone starts fresh");
      float[] mid = AftermathRules.mix(60, f, zone.severity(), zone.causeType().bit(), 0); // a minute in: full dust and smoke
      assertTrue(mid[AftermathRules.SMOKE] > 0.0F, "a fresh ruin smokes");
      assertTrue(mid[AftermathRules.DUST] > 0.0F, "a fresh ruin is dusty");
      assertTrue(mid[AftermathRules.SIREN] == 0.0F, "no sirens in the first minute");

      // ---------------------------------------------------------------- power: the grid reads the same impact info
      double reach = PowerRules.reach(plan.radius(), plan.severity().strength());
      BlockPos center = planCenter(plan);
      double own = PowerRules.distanceToCell(center.getX(), center.getZ(), 0, 0, 0, 0);
      assertTrue(own < reach, "the grid cell of the collapse is inside the reach");
      assertEquals(PowerRules.Effect.DAMAGED, PowerRules.effect(PowerRules.falloff(own, reach)), "the own cell goes DAMAGED");
      double far = PowerRules.distanceToCell(center.getX(), center.getZ(), 4, 0, 0, 0);
      assertTrue(PowerRules.effect(PowerRules.falloff(far, reach)).ordinal() < PowerRules.Effect.DAMAGED.ordinal(), "far cells stay lighter");

      // ---------------------------------------------------------------- journal: every changed block has its original
      ChangeJournal journal = new ChangeJournal();
      for (long k : city.damaged) {
         ChangeJournal.Entry e = city.journal.get(k);
         journal.record(BlockPos.of(k), e.state(), e.blockEntity());
      }
      assertEquals(city.damaged.size(), journal.size(), "each position once: the first original wins");
      assertEquals(1, journal.chunkCount(), "the tower is inside one chunk");

      // ---------------------------------------------------------------- reset: RestoreQueue writes everything back
      RestoreQueue queue = new RestoreQueue(journal, journal, msg -> { });
      queue.start(now + collapseAt + fall);
      int ticks = 0;
      while (queue.running() && ticks++ < 100) {
         city.tickRestore(queue, now + collapseAt + fall + ticks);
      }
      assertTrue(!queue.running(), "the reset finished");
      assertEquals(0, journal.size(), "the journal is empty after the reset");
      assertEquals(journal.settlingSize(), city.damaged.size(), "the restored originals wait for two world saves");

      // ---------------------------------------------------------------- 0 differences: every block is back as it was
      for (long k : city.damaged) {
         assertEquals(CollapseAnalyzer.STRUCTURE, city.state(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k)),
            "restored block class at " + BlockKey.x(k) + "," + BlockKey.y(k) + "," + BlockKey.z(k));
      }
      journal.worldSaved();
      journal.worldSaved();
      assertEquals(0, journal.settlingSize(), "after two world saves the crash-safety settling set is drained");
   }

   @Test
   void aTowerOverTwoChunksRestoresWhole() {
      City city = new City();
      city.building(40, 0, 10, 10); // x 40..49 crosses the chunk border 47/48
      long now = 500L;
      List<Long> hole = city.strike(40, 64, 0, 49, 65, 9); // the leviathan cut the whole footprint, two storeys deep
      assertEquals(200, hole.size());

      DamageLedger ledger = new DamageLedger();
      for (long k : hole) {
         ledger.record(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k), CollapseCause.LEVIATHAN.weight(), now, CollapseCause.LEVIATHAN);
      }
      List<DamageLedger.Candidate> found = ledger.settled(now + CollapseRules.SETTLE_TICKS, 2);
      assertEquals(1, found.size());
      DamageLedger.Candidate c = found.get(0);

      LongArrayList near = ledger.around(c.x(), c.y(), c.z(), CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP);
      CollapseAnalyzer.Request req = new CollapseAnalyzer.Request(c.x(), c.y(), c.z(), near.toLongArray(), c.score(), CollapseCause.LEVIATHAN,
         7L, CollapseRules.DEFAULT_MAX_BLOCKS, -64, 319);
      CollapseAnalyzer.Plan plan = CollapseAnalyzer.analyze(city, req);
      assertTrue(plan.accepted(), plan.reason());
      assertEquals(CollapseAnalyzer.Rule.SEVERED, plan.rule());

      List<Long> fell = city.removeSection(plan.blocks());
      assertTrue(fell.size() >= CollapseRules.MIN_SECTION);

      ChangeJournal journal = new ChangeJournal();
      for (long k : city.damaged) {
         ChangeJournal.Entry e = city.journal.get(k);
         journal.record(BlockPos.of(k), e.state(), e.blockEntity());
      }
      assertEquals(2, journal.chunkCount(), "the tower spans two chunks");
      assertEquals(city.damaged.size(), journal.size());

      RestoreQueue queue = new RestoreQueue(journal, journal, msg -> { });
      queue.start(now);
      int ticks = 0;
      while (queue.running() && ticks++ < 100) {
         city.tickRestore(queue, now + ticks);
      }
      assertTrue(!queue.running());
      assertEquals(0, journal.size());
      for (long k : city.damaged) {
         assertEquals(CollapseAnalyzer.STRUCTURE, city.state(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k)));
      }
   }

   private static BlockPos planCenter(CollapseAnalyzer.Plan plan) {
      return new BlockPos((plan.minX() + plan.maxX()) / 2, (plan.minY() + plan.maxY()) / 2, (plan.minZ() + plan.maxZ()) / 2);
   }
}
