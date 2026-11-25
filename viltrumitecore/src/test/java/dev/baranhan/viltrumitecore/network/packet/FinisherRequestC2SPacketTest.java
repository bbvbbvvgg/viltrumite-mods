package dev.baranhan.viltrumitecore.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Wire round trip and the encode/decode clamps of the finisher request (wave1-22 T6). */
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
   void roundTripKeepsTheTargetId() {
      assertEquals(42, roundTrip(42).targetId);
      assertEquals(0, roundTrip(0).targetId);
      assertEquals(FinisherRequestC2SPacket.MAX_ENTITY_ID, roundTrip(FinisherRequestC2SPacket.MAX_ENTITY_ID).targetId);
   }

   @Test
   void hostileIdsAreClampedOnEncode() {
      assertEquals(0, new FinisherRequestC2SPacket(-7).targetId);
      assertEquals(FinisherRequestC2SPacket.MAX_ENTITY_ID, new FinisherRequestC2SPacket(Integer.MAX_VALUE).targetId);
      assertEquals(0, roundTrip(-7).targetId);
   }

   @Test
   void hostileIdsAreClampedOnDecode() {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         buf.writeVarInt(-9);
         assertEquals(0, new FinisherRequestC2SPacket(buf).targetId);
      } finally {
         buf.release();
      }

      buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         buf.writeVarInt(Integer.MAX_VALUE);
         assertEquals(FinisherRequestC2SPacket.MAX_ENTITY_ID, new FinisherRequestC2SPacket(buf).targetId);
      } finally {
         buf.release();
      }
   }
}
