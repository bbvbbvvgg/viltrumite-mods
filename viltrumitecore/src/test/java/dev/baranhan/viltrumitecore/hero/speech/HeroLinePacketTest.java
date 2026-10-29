package dev.baranhan.viltrumitecore.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class HeroLinePacketTest {
   private static HeroLineS2CPacket roundTrip(HeroLineS2CPacket packet) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         packet.toBytes(buf);
         HeroLineS2CPacket copy = new HeroLineS2CPacket(buf);
         assertEquals(0, buf.readableBytes());
         return copy;
      } finally {
         buf.release();
      }
   }

   @Test
   void everyOpRoundTrips() {
      HeroLineS2CPacket play = HeroLineS2CPacket.play(7, UUID.randomUUID(), 42, "Steve", "reinhard", "reinhard:world_cut",
         "subtitle.viltrumitecore.speaker.reinhard", List.of("subtitle.viltrumitecore.reinhard.world_cut", "Мой меч"), false, 2, 0);
      assertEquals(play, roundTrip(play));
      assertEquals(HeroLineS2CPacket.stop(9), roundTrip(HeroLineS2CPacket.stop(9)));
      assertEquals(HeroLineS2CPacket.clear(), roundTrip(HeroLineS2CPacket.clear()));
   }

   @Test
   void badPacketsAreRefused() {
      UUID id = UUID.randomUUID();
      assertThrows(IllegalArgumentException.class, () -> HeroLineS2CPacket.play(1, id, 1, "a", "h", "h:l", "", List.of(), false, 1, 0));
      assertThrows(IllegalArgumentException.class, () -> HeroLineS2CPacket.play(1, id, 1, "a", "h", "h:l", "", List.of("k"), false, 3, 0));
      assertThrows(IllegalArgumentException.class,
         () -> HeroLineS2CPacket.play(1, id, 1, "a", "h", "h:l", "", java.util.Collections.nCopies(HeroLineS2CPacket.MAX_FRAGMENTS + 1, "k"), false, 1, 0));
      FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
      try {
         forged.writeVarInt(HeroLineS2CPacket.PLAY);
         forged.writeVarInt(1);
         forged.writeUUID(id);
         forged.writeVarInt(1);
         forged.writeUtf("a");
         forged.writeUtf("h");
         forged.writeUtf("h:l");
         forged.writeUtf("");
         forged.writeVarInt(100000);
         assertThrows(IllegalArgumentException.class, () -> new HeroLineS2CPacket(forged), "a forged count is refused before reading");
      } finally {
         forged.release();
      }
   }
}
