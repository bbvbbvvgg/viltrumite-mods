package dev.baranhan.viltrumitecore.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Wire round trip of the finisher request (wave1-22 T6): the target id is never rewritten. */
class FinisherRequestC2SPacketTest {
   private static FinisherRequestC2SPacket roundTrip(int targetId) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         new FinisherRequestC2SPacket(targetId).encode(buf);
         FinisherRequestC2SPacket got = new FinisherRequestC2SPacket(buf);
         assertEquals(0, buf.readableBytes(), "decoder must consume exactly what the encoder wrote");
         return got;
      } finally {
         buf.release();
      }
   }

   @Test
   void everyIdDozensOutExactlyAsItCameIn() {
      for (int id : new int[] {0, 42, 0xFFFFF, 0x100000, 1_000_000_000, Integer.MAX_VALUE, -7, Integer.MIN_VALUE}) {
         assertEquals(id, roundTrip(id).targetId, "id " + id + " must not be clamped or rewritten");
      }
   }

   @Test
   void anIdPastTheOldCeilingStillReachesTheServer() {
      // A long-lived server outgrows any ceiling we could pick; clamping here would retarget the
      // request at whatever body owns the clamped id. The level, not the packet, decides what exists.
      assertEquals(4_000_000, roundTrip(4_000_000).targetId);
   }

   @Test
   void aHugeIdFromTheWireIsKeptAsItIs() {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         buf.writeVarInt(Integer.MAX_VALUE);
         assertEquals(Integer.MAX_VALUE, new FinisherRequestC2SPacket(buf).targetId);
      } finally {
         buf.release();
      }
   }

   @Test
   void aNegativeIdFromTheWireIsKeptAsItIs() {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         buf.writeVarInt(-9);
         assertEquals(-9, new FinisherRequestC2SPacket(buf).targetId);
      } finally {
         buf.release();
      }
   }
}
