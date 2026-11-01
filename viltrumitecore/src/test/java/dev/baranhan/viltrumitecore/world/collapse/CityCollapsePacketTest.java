package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CityCollapsePacketTest {
   @Test
   void startRoundTripKeepsTheSection() {
      int sx = 5, sy = 23, sz = 5;
      long[] bits = new long[(sx * sy * sz + 63) >>> 6];
      int[][] set = {{0, 0, 0}, {4, 22, 4}, {2, 10, 3}};
      for (int[] p : set) {
         int i = CityCollapseS2CPacket.bitIndex(p[0], p[1], p[2], sx, sz);
         bits[i >>> 6] |= 1L << (i & 63);
      }
      CityCollapseS2CPacket out = CityCollapseS2CPacket.start(77L, new Vec3(2.5, 79.5, 2.5), 1, 0, 1234L, 0.6F, -0.8F, 0, 68, 0, sx, sy, sz, bits, 32, 38,
         40, 63, false);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      out.encode(buf);
      assertTrue(buf.readableBytes() < 200, "a 575-block section fits in " + buf.readableBytes() + " bytes");
      CityCollapseS2CPacket in = new CityCollapseS2CPacket(buf);
      assertEquals(77L, in.id);
      assertEquals(68, in.minY);
      assertEquals(23, in.sizeY);
      assertEquals(38, in.collapseAt);
      assertEquals(40, in.fallTicks);
      assertEquals(63, in.groundY);
      assertEquals(0.6F, in.fallX);
      for (int[] p : set) {
         assertTrue(in.has(p[0], p[1], p[2]));
      }
      assertFalse(in.has(1, 1, 1));
   }

   @Test
   void phasePacketIsSmall() {
      CityCollapseS2CPacket out = CityCollapseS2CPacket.phase(CityCollapseS2CPacket.IMPACT, 5L, new Vec3(1, 2, 3), 2, 63, true);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      out.encode(buf);
      CityCollapseS2CPacket in = new CityCollapseS2CPacket(buf);
      assertEquals(CityCollapseS2CPacket.IMPACT, in.kind);
      assertTrue(in.far);
      assertEquals(2, in.severity);
      assertEquals(0, buf.readableBytes());
   }
}
