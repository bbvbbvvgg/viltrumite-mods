package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.CarveBudget;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutCarve;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutGeometry;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WorldCutCarveTest {
   /** Finite fake world: real job/cursor logic, no Minecraft level or mocks. */
   static WorldCutGeometry.Wave wave(int total) {
      return new WorldCutGeometry.Wave() {
         int used;
         public boolean hasNext() { return used < total; }
         public WorldCutGeometry.Cell next() {
            if (!hasNext()) throw new NoSuchElementException();
            int i = used++;
            return new WorldCutGeometry.Cell(i % 17, 80, i / 17, i / 800, 0);
         }
         public int remainingWork() { return total - used; }
         public int frontAlong() { return used == 0 ? 0 : (used - 1) / 800 + 1; }
      };
   }

   static final class Probe implements WorldCutCarve.Probe {
      int visited, clears, launches;
      boolean protectedBlocks;
      final Set<BlockPos> changed = new HashSet<>();
      public boolean loaded(BlockPos pos) { visited++; return !protectedBlocks || index(pos) != 1; }
      public boolean canDestroy(BlockPos pos) {
         assertFalse(protectedBlocks && index(pos) == 1, "never read an unloaded chunk");
         return !protectedBlocks || index(pos) != 2;
      }
      public boolean solid(BlockPos pos) { return true; }
      public boolean canLaunch(BlockPos pos) { return true; }
      public boolean clear(BlockPos pos) { assertTrue(changed.add(pos)); clears++; return true; }
      public boolean launch(BlockPos pos, Vec3 velocity) {
         assertTrue(changed.add(pos), "a launched source is not cleared again");
         assertTrue(velocity.y > 0);
         launches++; return true;
      }
      static int index(BlockPos pos) { return pos.getX() + pos.getZ() * 17; }
   }

   static WorldCutCarve job(int count, Probe probe) {
      return new WorldCutCarve(wave(count), probe, Vec3.ZERO, new Vec3(0, 0, 1));
   }

   @Test
   void neverAboveBudget() {
      Probe probe = new Probe(); WorldCutCarve job = job(10001, probe);
      for (int budget : new int[] {1, 17, 1999, 2000, 31}) {
         int before = probe.visited;
         job.step(null, budget);
         assertEquals(budget, probe.visited - before);
         assertEquals(10001 - probe.visited, job.remainingWork());
      }
   }

   @Test
   void skipsUnbreakable() {
      Probe probe = new Probe(); probe.protectedBlocks = true;
      assertTrue(job(20, probe).step(null, 20));
      assertEquals(20, probe.visited);
      assertEquals(18, probe.changed.size());
      assertFalse(probe.changed.contains(new BlockPos(1, 80, 0)));
      assertFalse(probe.changed.contains(new BlockPos(2, 80, 0)));
   }

   @Test
   void debrisCapped() {
      Probe probe = new Probe(); WorldCutCarve job = job(160000, probe);
      while (!job.step(null, 2000)) { }
      assertEquals(300, probe.launches);
      assertEquals(160000, probe.clears + probe.launches);
      assertEquals(300, job.debrisCount());
   }

   @Test
   void finishes() {
      Probe probe = new Probe(); WorldCutCarve job = job(160000, probe);
      int steps = 0;
      do { steps++; } while (!job.step(null, 2000));
      assertEquals(80, steps);
      assertEquals(0, job.remainingWork());
      assertEquals(200, job.frontAlong());
      assertTrue(job.step(null, 2000));
      assertEquals(160000, probe.visited);
   }

   @Test
   void slowerUnderLoad() {
      assertTrue(steps(CarveBudget.perTick(2000, 55)) > steps(CarveBudget.perTick(2000, 40)));
   }

   private int steps(int budget) {
      Probe probe = new Probe(); WorldCutCarve job = job(160000, probe);
      int steps = 0;
      do { steps++; } while (!job.step(null, budget));
      assertEquals(160000, probe.visited);
      return steps;
   }

   @Test
   void zeroBudgetDoesNoWork() {
      Probe probe = new Probe(); WorldCutCarve job = job(100, probe);
      assertFalse(job.step(null, 0));
      assertFalse(job.step(null, -1));
      assertEquals(0, probe.visited);
      assertEquals(100, job.remainingWork());
   }
}
