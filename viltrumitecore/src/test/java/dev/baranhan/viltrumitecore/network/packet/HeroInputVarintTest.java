package dev.baranhan.viltrumitecore.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * Wave 1 · session 01: the key packet carries the action ordinal as a varint. The old signed byte turned
 * ordinal 128+ into a negative id, {@code HeroAction.byId} returned null and the press was silently lost.
 */
class HeroInputVarintTest {
   @Test
   void everyRosterActionRoundTrips() {
      for (HeroAction action : HeroAction.values()) {
         for (boolean pressed : new boolean[]{true, false}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            new HeroInputC2SPacket(action, pressed).toBytes(buffer);
            HeroInputC2SPacket decoded = new HeroInputC2SPacket(buffer);
            assertEquals(action.ordinal(), decoded.actionOrdinal(), action.name());
            assertEquals(pressed, decoded.pressed());
            assertEquals(0, buffer.readableBytes());
            assertEquals(action, HeroAction.byId(decoded.actionOrdinal()));
         }
      }
   }

   @Test
   void syntheticOrdinalsPast127StayPositive() {
      for (int ordinal : new int[]{0, 63, 64, 127, 128, 200, 255, 256, 16_383, 16_384}) {
         FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
         buffer.writeVarInt(ordinal);
         buffer.writeBoolean(true);
         HeroInputC2SPacket decoded = new HeroInputC2SPacket(buffer);
         assertEquals(ordinal, decoded.actionOrdinal());
         assertTrue(decoded.pressed());
      }
   }

   @Test
   void smallOrdinalsKeepTheOldTwoByteSize() {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      new HeroInputC2SPacket(HeroAction.values()[0], true).toBytes(buffer);
      assertEquals(2, buffer.readableBytes(), "ordinal 0..127 is still one byte + the pressed flag");
   }
}
