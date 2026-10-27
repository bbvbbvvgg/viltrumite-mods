package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The saved truth of the one logical hammer. */
class MjolnirRecordTest {
   private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
   private static final UUID THOR = UUID.fromString("00000000-0000-0000-0000-00000000b002");

   @Test
   void theLogicalIdIsChosenOnce() {
      MjolnirRecord record = new MjolnirRecord();
      assertFalse(record.exists());
      assertFalse(record.create(ID, MjolnirPhase.RESTING), "only the fall or the hand create it");
      assertTrue(record.create(ID, MjolnirPhase.WORLD_FALLING));
      assertTrue(record.exists());
      assertFalse(record.create(UUID.randomUUID(), MjolnirPhase.IN_HAND), "a second hammer is never created");
      assertEquals(ID, record.logicalId());
   }

   @Test
   void goOnlyFollowsTheTable() {
      MjolnirRecord record = new MjolnirRecord();
      record.create(ID, MjolnirPhase.IN_HAND);
      assertFalse(record.go(MjolnirPhase.RETURNING), "IN_HAND -> RETURNING is not a transition");
      assertSame(MjolnirPhase.IN_HAND, record.phase());
      assertTrue(record.go(MjolnirPhase.THROWN));
      assertTrue(record.go(MjolnirPhase.RESTING));
      record.embedded = true;
      assertTrue(record.go(MjolnirPhase.RETURNING));
      assertFalse(record.embedded(), "leaving the block clears the embedding");
   }

   @Test
   void nbtRoundTrip() {
      MjolnirRecord record = new MjolnirRecord();
      record.create(ID, MjolnirPhase.IN_HAND);
      record.go(MjolnirPhase.THROWN);
      record.go(MjolnirPhase.RESTING);
      record.place("minecraft:the_nether", 1.5, 64.0, -3.25);
      record.velocity(0.1, -0.2, 0.3);
      record.activeOwner = THOR;
      record.lastOwner = THOR;
      record.fallArmedAt = 1234L;
      record.fallCancelled = true;
      record.embedded = true;
      record.lying = false;
      record.charged = true;
      record.recallPending = true;
      record.yaw = 45.0F;
      record.pitch = 80.0F;
      record.death = new double[]{1.0, 2.0, 3.0};
      record.deathDimension = "minecraft:overworld";
      record.safe = new double[]{4.0, 5.0, 6.0};
      record.safeDimension = "minecraft:overworld";

      MjolnirRecord loaded = MjolnirRecord.load(record.save());
      assertEquals(ID, loaded.logicalId());
      assertSame(MjolnirPhase.RESTING, loaded.phase());
      assertEquals("minecraft:the_nether", loaded.dimension());
      assertEquals(1.5, loaded.x());
      assertEquals(64.0, loaded.y());
      assertEquals(-3.25, loaded.z());
      assertEquals(-0.2, loaded.vy);
      assertEquals(THOR, loaded.activeOwner());
      assertEquals(THOR, loaded.lastOwner());
      assertEquals(1234L, loaded.fallArmedAt());
      assertTrue(loaded.fallCancelled());
      assertTrue(loaded.embedded());
      assertTrue(loaded.charged());
      assertTrue(loaded.recallPending);
      assertEquals(45.0F, loaded.yaw);
      assertEquals(80.0F, loaded.pitch);
      assertArrayEquals(new double[]{1.0, 2.0, 3.0}, loaded.death);
      assertArrayEquals(new double[]{4.0, 5.0, 6.0}, loaded.safe);
   }

   @Test
   void brokenDataIsRecoveredNotDuplicated() {
      CompoundTag unknown = new CompoundTag();
      unknown.putUUID(MjolnirRecord.ID, ID);
      unknown.putString(MjolnirRecord.PHASE, "magnetized_to_hammer");
      MjolnirRecord recovered = MjolnirRecord.load(unknown);
      assertSame(MjolnirPhase.RESTING, recovered.phase(), "an unknown phase rests where it was saved");
      assertEquals("minecraft:overworld", recovered.dimension());
      assertEquals(ID, recovered.logicalId());

      MjolnirRecord empty = MjolnirRecord.load(new CompoundTag());
      assertSame(MjolnirPhase.NOT_CREATED, empty.phase());
      assertNull(empty.logicalId());
      assertEquals(-1L, empty.fallArmedAt(), "never armed");

      CompoundTag noId = new CompoundTag();
      noId.putString(MjolnirRecord.PHASE, "thrown");
      assertSame(MjolnirPhase.NOT_CREATED, MjolnirRecord.load(noId).phase(), "no id, no hammer");
   }

   @Test
   void distanceFromTheThrowOrigin() {
      MjolnirRecord record = new MjolnirRecord();
      record.originX = 0.0;
      record.originY = 0.0;
      record.originZ = 0.0;
      assertEquals(5.0, record.fromOrigin(3.0, 4.0, 0.0), 1.0E-9);
   }
}
