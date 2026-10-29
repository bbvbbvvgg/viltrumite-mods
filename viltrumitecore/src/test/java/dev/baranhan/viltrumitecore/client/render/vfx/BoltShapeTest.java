package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R4: the bolt channel is seeded (same look on every client), hits its endpoints and stays a bolt, not a scribble. */
class BoltShapeTest {
   private static final Vec3 TOP = new Vec3(0.0, 100.0, 0.0);
   private static final Vec3 GROUND = new Vec3(2.0, 60.0, -1.0);

   @Test
   void sameSeedSameChannel() {
      List<BoltShape.Strand> a = BoltShape.channel(42L, TOP, GROUND, 0.42, 5);
      List<BoltShape.Strand> b = BoltShape.channel(42L, TOP, GROUND, 0.42, 5);
      assertEquals(a.size(), b.size());
      for (int i = 0; i < a.size(); i++) {
         assertArrayEquals(a.get(i).points(), b.get(i).points());
      }
   }

   @Test
   void mainStrandRunsSkyToGroundWithForks() {
      List<BoltShape.Strand> strands = BoltShape.channel(7L, TOP, GROUND, 0.42, 5);
      BoltShape.Strand main = strands.get(0);
      assertEquals(0, main.level());
      assertEquals(TOP, main.points()[0]);
      assertEquals(GROUND, main.points()[main.points().length - 1]);
      assertTrue(strands.stream().filter(s -> s.level() == 1).count() == 5, "five forks");
      assertTrue(strands.stream().allMatch(s -> s.level() == 0 || s.width() < main.width()), "forks are thinner than the core");
      double length = TOP.distanceTo(GROUND);
      Vec3 dir = GROUND.subtract(TOP).normalize();
      for (Vec3 p : main.points()) {
         Vec3 rel = p.subtract(TOP);
         double side = rel.subtract(dir.scale(rel.dot(dir))).length();
         assertTrue(side < length * 0.5, "the core stays near its line: " + side);
      }
   }

   @Test
   void segmentsFollowLengthAndAreCapped() {
      assertEquals(2, BoltShape.depthFor(0.5));
      assertTrue(BoltShape.depthFor(40.0) >= 6);
      assertEquals(BoltShape.MAX_DEPTH, BoltShape.depthFor(1000.0));
      Vec3 far = TOP.add(0.0, -1000.0, 0.0);
      BoltShape.Strand main = BoltShape.channel(1L, TOP, far, 0.4, 0).get(0);
      assertTrue(main.points()[main.points().length - 1].distanceTo(TOP) <= BoltShape.MAX_LENGTH + 1.0E-6);
   }

   @Test
   void strokeIsBrightThenRestrikesThenFades() {
      int life = 40;
      assertEquals(0.45F, BoltShape.boltBrightness(0.5F, life, 3L), 1.0E-6, "dim stepped leader first");
      assertTrue(BoltShape.boltBrightness(BoltShape.LEADER_TICKS + 0.5F, life, 3L) > 0.9F, "full return stroke");
      assertEquals(0.0F, BoltShape.boltBrightness(life, life, 3L));
      float late = BoltShape.boltBrightness(life - 1.0F, life, 3L);
      assertTrue(late >= 0.0F && late < 0.1F, "only a faint afterglow at the end");
      for (int age = 0; age < life; age++) {
         float b = BoltShape.boltBrightness(age, life, 9L);
         assertTrue(b >= 0.0F && b <= 1.0F);
      }
   }
}
