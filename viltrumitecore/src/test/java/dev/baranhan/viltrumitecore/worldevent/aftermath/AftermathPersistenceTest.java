package dev.baranhan.viltrumitecore.worldevent.aftermath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Zones survive save/load (relog, restart), the wire and the shared event tag. */
class AftermathPersistenceTest {
   private static AftermathZone zone() {
      AftermathZone z = new AftermathZone(7, 100.5, 64.0, -230.25, 14.0F, 5000L);
      z.severity = 0.72F;
      z.declared = 0.6F;
      z.blocks = 321;
      z.causes = AftermathCause.METEOR.bit() | AftermathCause.DESTRUCTION.bit();
      z.flags = AftermathRules.FLAG_CITY | AftermathRules.FLAG_POWER_OUT;
      z.lastHit = 6200L;
      z.focus = new int[]{90, 60, -240, 110, 80, -220};
      RandomSource r = RandomSource.create(3);
      for (int i = 0; i < 70; i++) {
         z.addSeed(new BlockPos(90 + i % 20, 64 + i % 7, -240 + i % 13).asLong(), r);
      }
      z.addEventId("meteor:1");
      z.addEventId("collapse:9");
      return z;
   }

   @Test
   void zoneRoundTripsThroughNbt() {
      AftermathZone a = zone();
      AftermathZone b = AftermathZone.load(a.save());
      assertEquals(a.id, b.id);
      assertEquals(a.x, b.x, 1.0E-9);
      assertEquals(a.y, b.y, 1.0E-9);
      assertEquals(a.z, b.z, 1.0E-9);
      assertEquals(a.radius, b.radius, 1.0E-6);
      assertEquals(a.severity, b.severity, 1.0E-6);
      assertEquals(a.declared, b.declared, 1.0E-6);
      assertEquals(a.blocks, b.blocks);
      assertEquals(a.causes, b.causes);
      assertEquals(a.flags, b.flags);
      assertEquals(a.created, b.created);
      assertEquals(a.lastHit, b.lastHit);
      assertArrayEquals(a.seeds, b.seeds);
      assertEquals(AftermathRules.MAX_SEEDS, b.seeds.length, "seed reservoir is capped");
      assertArrayEquals(a.focus, b.focus);
      assertTrue(b.hasEvent("meteor:1") && b.hasEvent("collapse:9"));
      assertFalse(b.addEventId("meteor:1"), "a repeated event id is recognised after a restart");
      assertEquals(a.stage(30000L, 1.0), b.stage(30000L, 1.0));
   }

   @Test
   void dataKeepsZonesOutagesAndIds() {
      AftermathData d = new AftermathData();
      d.zones.add(zone());
      d.outages.put("grid:north", new AftermathData.Outage("grid:north", 1.0, 2.0, 3.0, 40.0F));
      d.nextId = 8;
      AftermathData e = AftermathData.load(d.save(new CompoundTag()));
      assertEquals(1, e.zones.size());
      assertEquals(8, e.nextId);
      assertEquals(new AftermathData.Outage("grid:north", 1.0, 2.0, 3.0, 40.0F), e.outages.get("grid:north"));
      CompoundTag stale = new CompoundTag();
      stale.putInt("nextId", 1);
      AftermathData f = AftermathData.load(d.save(new CompoundTag()).merge(stale));
      assertEquals(8, f.nextId, "ids never go back below a loaded zone");
      assertTrue(AftermathData.load(new CompoundTag()).zones.isEmpty(), "an empty world loads clean");
   }

   @Test
   void snapshotAndPacketRoundTrip() {
      AftermathZone.Snapshot s = zone().snapshot(9000L, 1.0);
      AftermathZone plain = new AftermathZone(8, 0.0, 70.0, 0.0, 6.0F, 0L);
      AftermathS2CPacket p = new AftermathS2CPacket(AftermathS2CPacket.SYNC, List.of(s, plain.snapshot(100L, 2.0)), new int[]{3, 4});
      FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
      try {
         p.encode(b);
         AftermathS2CPacket q = new AftermathS2CPacket(b);
         assertEquals(0, b.readableBytes());
         assertEquals(AftermathS2CPacket.SYNC, q.kind);
         assertArrayEquals(new int[]{3, 4}, q.ids);
         AftermathZone.Snapshot r = q.zones.get(0);
         assertEquals(s.id(), r.id());
         assertEquals(s.x(), r.x(), 1.0E-9);
         assertEquals(s.radius(), r.radius(), 1.0E-6);
         assertEquals(s.factor(), r.factor(), 1.0E-6);
         assertEquals(2800L, r.age());
         assertEquals(s.causes(), r.causes());
         assertEquals(s.flags(), r.flags());
         assertArrayEquals(s.focus(), r.focus());
         assertArrayEquals(s.seeds(), r.seeds());
         assertNull(q.zones.get(1).focus());
         assertEquals(0, q.zones.get(1).seeds().length);
      } finally {
         b.release();
      }
   }

   @Test
   void sharedEventTagRoundTripsAndClamps() {
      AftermathEvent e = new AftermathEvent("collapse:42", "minecraft:overworld", new BlockPos(10, 70, -5), 22.0F, 0.8F, "collapse",
         List.of(new BlockPos(10, 70, -5), new BlockPos(11, 71, -5)), 1234L);
      CompoundTag t = e.toTag();
      for (String key : new String[]{"eventId", "dimension", "center", "radius", "severity", "cause", "affectedBlocks", "timestamp"}) {
         assertTrue(t.contains(key), key);
      }
      assertEquals(e, AftermathEvent.fromTag(t));
      AftermathEvent odd = new AftermathEvent(null, null, null, 900.0F, Float.NaN, "lava-flood", null, 0L);
      assertEquals(AftermathRules.MAX_RADIUS, odd.radius(), 1.0E-6);
      assertEquals(0.3F, odd.severity(), 1.0E-6);
      assertEquals("other", odd.cause());
      assertTrue(odd.eventId().startsWith("anon:"));
      assertTrue(odd.affectedBlocks().isEmpty());
      CompoundTag xyz = new CompoundTag();
      xyz.putInt("x", 5);
      xyz.putInt("y", 6);
      xyz.putInt("z", 7);
      assertEquals(new BlockPos(5, 6, 7), AftermathEvent.fromTag(xyz).center(), "x/y/z ints are accepted too");
   }
}
