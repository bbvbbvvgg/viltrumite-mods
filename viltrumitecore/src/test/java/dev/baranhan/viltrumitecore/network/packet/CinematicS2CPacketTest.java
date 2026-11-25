package dev.baranhan.viltrumitecore.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Wire round trip and decode clamping of the cinematic camera cue (wave1-22). */
class CinematicS2CPacketTest {
   @Test
   void roundTripKeepsEveryField() {
      ResourceLocation scene = new ResourceLocation("viltrumitecore", "orbit");
      CinematicS2CPacket sent = new CinematicS2CPacket(42, scene, 77);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         sent.encode(buf);
         CinematicS2CPacket got = new CinematicS2CPacket(buf);
         assertEquals(0, buf.readableBytes(), "decoder must consume exactly what the encoder wrote");
         assertEquals(42, got.casterId);
         assertEquals(scene, got.sceneId);
         assertEquals(77, got.focusId);
      } finally {
         buf.release();
      }
   }

   @Test
   void hostileIdsAreClampedOnEncode() {
      CinematicS2CPacket bad = new CinematicS2CPacket(-5, null, Integer.MIN_VALUE);
      assertEquals(0, bad.casterId);
      assertEquals(0, bad.focusId);
      assertEquals(CinematicS2CPacket.DEFAULT_SCENE_ID, bad.sceneId);
   }

   @Test
   void hostileIdsAndSceneAreClampedOnDecode() {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      try {
         buf.writeVarInt(-9);
         buf.writeUtf("not a valid id!!", CinematicS2CPacket.MAX_SCENE_ID_LENGTH);
         buf.writeVarInt(Integer.MAX_VALUE);
         CinematicS2CPacket got = new CinematicS2CPacket(buf);
         assertEquals(0, got.casterId);
         assertEquals(CinematicS2CPacket.DEFAULT_SCENE_ID, got.sceneId);
         assertEquals(CinematicS2CPacket.MAX_ENTITY_ID, got.focusId);
      } finally {
         buf.release();
      }
   }
}
