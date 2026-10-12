package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Shared ref-counted noGravity leases (Reinhard stage 4 Task 3, review focus 2). */
class LiftLeasesTest {
   static final class FakeBody implements LiftLeases.Body {
      final UUID id = UUID.randomUUID();
      boolean noGravity;
      double y;
      final CompoundTag tag = new CompoundTag();

      FakeBody(boolean noGravity) {
         this.noGravity = noGravity;
      }

      @Override
      public UUID id() {
         return this.id;
      }

      @Override
      public boolean noGravity() {
         return this.noGravity;
      }

      @Override
      public void setNoGravity(boolean value) {
         this.noGravity = value;
      }

      @Override
      public double y() {
         return this.y;
      }

      @Override
      public CompoundTag persistentData() {
         return this.tag;
      }
   }

   private static final UUID A = UUID.randomUUID();
   private static final UUID B = UUID.randomUUID();

   @Test
   void overlappingOwnersRestoreOriginal() {
      LiftLeases leases = new LiftLeases();
      FakeBody cow = new FakeBody(false);
      assertTrue(leases.acquire(cow, A));
      assertTrue(cow.noGravity);
      assertTrue(leases.acquire(cow, B));
      assertFalse(leases.release(cow, A), "B still holds it");
      assertTrue(cow.noGravity, "still floating while B holds the lease");
      assertTrue(leases.release(cow, B));
      assertFalse(cow.noGravity, "the original false comes back, never B's saved true");

      // Mirror order: B leaves first.
      FakeBody sheep = new FakeBody(false);
      leases.acquire(sheep, A);
      leases.acquire(sheep, B);
      leases.release(sheep, B);
      assertTrue(sheep.noGravity);
      leases.release(sheep, A);
      assertFalse(sheep.noGravity);
      assertFalse(leases.holds(sheep.id()));
   }

   @Test
   void originalTrueStaysTrue() {
      LiftLeases leases = new LiftLeases();
      FakeBody vex = new FakeBody(true);
      leases.acquire(vex, A);
      leases.acquire(vex, B);
      leases.release(vex, A);
      leases.release(vex, B);
      assertTrue(vex.noGravity, "a naturally floating mob is never set to false");
   }

   @Test
   void secondAcquireBySameOwnerIsNoop() {
      LiftLeases leases = new LiftLeases();
      FakeBody cow = new FakeBody(false);
      assertTrue(leases.acquire(cow, A));
      assertFalse(leases.acquire(cow, A));
      assertTrue(leases.release(cow, A));
      assertFalse(cow.noGravity);
      assertFalse(leases.release(cow, A), "releasing twice changes nothing");
   }

   @Test
   void releaseAllOnlyOwn() {
      LiftLeases leases = new LiftLeases();
      FakeBody one = new FakeBody(false);
      FakeBody two = new FakeBody(false);
      FakeBody three = new FakeBody(false);
      leases.acquire(one, A);
      leases.acquire(two, A);
      leases.acquire(two, B);
      leases.acquire(three, B);
      Map<UUID, LiftLeases.Body> world = new HashMap<>();
      for (FakeBody body : List.of(one, two, three)) {
         world.put(body.id, body);
      }

      List<LiftLeases.Body> ended = leases.releaseAll(A, world::get);
      assertEquals(List.of(one), ended);
      assertFalse(one.noGravity);
      assertTrue(two.noGravity);
      assertTrue(three.noGravity);
      assertEquals(Set.of(B), leases.owners(two.id()));
   }

   @Test
   void releaseAllWithAnUnloadedBodyDropsTheLease() {
      LiftLeases leases = new LiftLeases();
      FakeBody away = new FakeBody(false);
      leases.acquire(away, A);
      assertTrue(leases.releaseAll(A, id -> null).isEmpty());
      assertFalse(leases.holds(away.id()), "the tag restores it on its next load");
      assertTrue(away.noGravity, "unloaded: unchanged until it joins again");
      leases.onJoin(away);
      assertFalse(away.noGravity);
      assertNull(LiftLeases.readTag(away.tag));
   }

   @Test
   void tagRoundTrip() {
      LiftLeases leases = new LiftLeases();
      FakeBody cow = new FakeBody(false);
      cow.y = 64.0;
      leases.acquire(cow, A);
      leases.acquire(cow, B);
      LiftLeases.Mirror mirror = LiftLeases.readTag(cow.tag);
      assertNotNull(mirror);
      assertFalse(mirror.original());
      assertEquals(Set.of(A, B), mirror.owners());
      assertEquals(64.0, mirror.baseY(), 1.0E-9);
      leases.release(cow, A);
      assertEquals(Set.of(B), LiftLeases.readTag(cow.tag).owners());
      leases.release(cow, B);
      assertNull(LiftLeases.readTag(cow.tag), "the tag goes with the lease");
   }

   @Test
   void joinRestoresAfterRestartAndKeepsLiveLeases() {
      FakeBody cow = new FakeBody(false);
      LiftLeases before = new LiftLeases();
      before.acquire(cow, A);
      // Restart: a fresh registry, the entity loads with noGravity true and the tag.
      LiftLeases after = new LiftLeases();
      after.onJoin(cow);
      assertFalse(cow.noGravity);
      assertNull(LiftLeases.readTag(cow.tag));

      FakeBody live = new FakeBody(false);
      after.acquire(live, B);
      live.noGravity = false; // a reload lost the flag
      after.onJoin(live);
      assertTrue(live.noGravity, "a live lease lifts it again");
   }

   @Test
   void baseYIsTheFirstOwnersStart() {
      LiftLeases leases = new LiftLeases();
      FakeBody cow = new FakeBody(false);
      cow.y = 10.0;
      leases.acquire(cow, A);
      cow.y = 11.5;
      leases.acquire(cow, B);
      assertEquals(10.0, leases.baseY(cow.id()), 1.0E-9);
   }
}
