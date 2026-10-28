package dev.baranhan.viltrumitecore.hero.dio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Wire round trips of both DIO client packets, including the frozen-id bound. */
class DioPacketsTest {
   @Test
   void fxRoundTrip() {
      DioFxS2CPacket p = new DioFxS2CPacket(DioFxS2CPacket.ROLLER_PUNCH, 42, 1.5, -64.25, 1.0E6, 0.1F, -0.2F, 0.3F, 2.5F, 77);
      FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
      try {
         p.toBytes(b);
         assertEquals(p, new DioFxS2CPacket(b));
         assertEquals(0, b.readableBytes());
      } finally {
         b.release();
      }
   }

   @Test
   void fxKindsAreDistinct() {
      int[] kinds = {DioFxS2CPacket.STAND_HIT, DioFxS2CPacket.STAND_MISS, DioFxS2CPacket.BARRAGE_HIT, DioFxS2CPacket.FINISHER, DioFxS2CPacket.KNIFE_HIT,
         DioFxS2CPacket.LEDGER_BURST, DioFxS2CPacket.DRAIN_PULSE, DioFxS2CPacket.ROLLER_SLAM, DioFxS2CPacket.ROLLER_PUNCH, DioFxS2CPacket.ROLLER_EXPLODE,
         DioFxS2CPacket.GUARD_BLOCK, DioFxS2CPacket.DEFLECT, DioFxS2CPacket.FROZEN_HIT, DioFxS2CPacket.SUMMON, DioFxS2CPacket.KNIFE_STOP};
      assertEquals(kinds.length, java.util.Arrays.stream(kinds).distinct().count());
   }

   @Test
   void timeRoundTrip() {
      UUID id = UUID.randomUUID();
      DioTimeS2CPacket p = new DioTimeS2CPacket(true, id, 7, 10.5, 70.0, -3.25, 40.0F, 180, 33, false, new int[]{3, 9, 1200});
      FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
      try {
         p.toBytes(b);
         DioTimeS2CPacket q = new DioTimeS2CPacket(b);
         assertEquals(0, b.readableBytes());
         assertEquals(p.active(), q.active());
         assertEquals(id, q.effectId());
         assertEquals(7, q.casterId());
         assertEquals(70.0, q.y());
         assertEquals(40.0F, q.radius());
         assertEquals(180, q.duration());
         assertEquals(33, q.age());
         assertEquals(false, q.scripted());
         assertArrayEquals(new int[]{3, 9, 1200}, q.frozen());
      } finally {
         b.release();
      }
   }

   @Test
   void frozenIdsAreBounded() {
      int[] many = new int[DioTimeS2CPacket.MAX_IDS + 100];
      for (int i = 0; i < many.length; i++) {
         many[i] = i;
      }

      DioTimeS2CPacket p = new DioTimeS2CPacket(false, UUID.randomUUID(), 1, 0, 0, 0, 32.0F, 0, 0, true, many);
      FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
      try {
         p.toBytes(b);
         DioTimeS2CPacket q = new DioTimeS2CPacket(b);
         assertEquals(DioTimeS2CPacket.MAX_IDS, q.frozen().length);
         assertEquals(0, b.readableBytes());
      } finally {
         b.release();
      }
   }
}
