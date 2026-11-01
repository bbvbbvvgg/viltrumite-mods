package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Wire format of the two uprising packets (PROTOCOL 23): encode → decode gives the same state, nothing left over. */
class UltronPacketsTest {
   private static UltronStateS2CPacket roundTrip(UltronStateS2CPacket p) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      p.toBytes(buf);
      UltronStateS2CPacket back = new UltronStateS2CPacket(buf);
      assertEquals(0, buf.readableBytes(), "nothing left over");
      return back;
   }

   private static void assertSame(UltronStateS2CPacket a, UltronStateS2CPacket b) {
      assertEquals(a.active(), b.active());
      assertEquals(a.phase(), b.phase());
      assertEquals(a.progress(), b.progress());
      assertEquals(a.variant(), b.variant());
      assertEquals(a.doctrine(), b.doctrine());
      assertEquals(a.outcome(), b.outcome());
      assertEquals(a.ax(), b.ax());
      assertEquals(a.az(), b.az());
      assertEquals(a.radius(), b.radius());
      assertArrayEquals(a.protocolSeconds(), b.protocolSeconds());
      assertArrayEquals(a.protocolLevel(), b.protocolLevel());
      assertEquals(a.nodes().size(), b.nodes().size());
      for (int i = 0; i < a.nodes().size(); i++) {
         UltronStateS2CPacket.Node x = a.nodes().get(i);
         UltronStateS2CPacket.Node y = b.nodes().get(i);
         assertEquals(List.of(x.id(), x.kind(), x.state(), x.flags(), x.x(), x.y(), x.z()), List.of(y.id(), y.kind(), y.state(), y.flags(), y.x(), y.y(), y.z()));
         assertEquals(List.of(x.hull(), x.shield(), x.build()), List.of(y.hull(), y.shield(), y.build()));
         assertArrayEquals(x.links(), y.links());
      }
      assertEquals(a.bossId(), b.bossId());
      assertEquals(a.bossStage(), b.bossStage());
      assertEquals(a.bossHealth(), b.bossHealth());
      assertEquals(a.upload(), b.upload());
      assertEquals(a.uploading(), b.uploading());
      assertArrayEquals(a.captured(), b.captured());
      assertEquals(a.units(), b.units());
      assertEquals(a.hack(), b.hack());
      assertEquals(a.hackNode(), b.hackNode());
   }

   @Test
   void fullStateRoundTrips() {
      List<UltronStateS2CPacket.Node> nodes = new ArrayList<>();
      for (UltronNodeKind k : UltronNodeKind.values()) {
         int i = k.ordinal();
         int flags = UltronStateS2CPacket.FLAG_POWERED | (i % 2 == 0 ? UltronStateS2CPacket.FLAG_DISCOVERED : 0) | (i == 3 ? UltronStateS2CPacket.FLAG_FORTIFIED : 0);
         nodes.add(new UltronStateS2CPacket.Node(i + 1, i, i % UltronNodeState.values().length, flags, 120.5F - i, 40.25F, 1.0F, -1200 + i * 37, 64 + i, 845 - i,
            i == 0 ? new int[]{2, 3, 4, 5, 6} : new int[]{1}));
      }
      UltronStateS2CPacket p = new UltronStateS2CPacket(true, UltronPhase.FINALE.ordinal(), 0.625F, UltronScenario.Variant.SINGULARITY.ordinal(),
         UltronDoctrine.HUNT.ordinal(), UltronStateS2CPacket.OUTCOME_NONE, -1180.5, 830.25, 96.0F, new int[]{0, 75, 0, 12, 0}, new int[]{0, 2, 0, 1, 0}, nodes,
         4711, UltronBossRules.Stage.AERIAL.ordinal(), 0.42F, 0.73F, true, new int[]{101, 102, 2_000_000}, 37, 0.5F, 3);
      UltronStateS2CPacket back = roundTrip(p);
      assertSame(p, back);
      assertTrue(back.nodes().get(0).discovered() && back.nodes().get(0).powered());
      assertFalse(back.nodes().get(1).discovered());
      assertTrue(back.nodes().get(3).fortified());
   }

   @Test
   void inactiveStateAndNoHackRoundTrip() {
      UltronStateS2CPacket off = UltronStateS2CPacket.inactive();
      assertFalse(off.active());
      UltronStateS2CPacket back = roundTrip(off);
      assertSame(off, back);
      assertEquals(-1, back.hackNode(), "no hack target survives the -1 offset");
      assertEquals(-1, back.bossId());
      UltronStateS2CPacket hacked = roundTrip(off.withHack(0.9F, 0));
      assertEquals(0, hacked.hackNode());
      assertEquals(0.9F, hacked.hack());
   }

   @Test
   void listsAreCappedOnTheWireAndRejectedWhenForged() {
      List<UltronStateS2CPacket.Node> many = new ArrayList<>();
      for (int i = 0; i < UltronStateS2CPacket.MAX_NODES + 5; i++) {
         many.add(new UltronStateS2CPacket.Node(i, 0, 0, 0, 1.0F, 0.0F, 1.0F, i, 70, -i, new int[12]));
      }
      int[] captured = new int[UltronStateS2CPacket.MAX_CAPTURED + 4];
      UltronStateS2CPacket big = new UltronStateS2CPacket(true, 1, 0.0F, 0, 0, 0, 0.0, 0.0, 64.0F, new int[20], new int[20], many, -1, 0, 0.0F, 0.0F, false,
         captured, 0, 0.0F, -1);
      UltronStateS2CPacket back = roundTrip(big);
      assertEquals(UltronStateS2CPacket.MAX_NODES, back.nodes().size());
      assertEquals(8, back.nodes().get(0).links().length);
      assertEquals(UltronStateS2CPacket.MAX_CAPTURED, back.captured().length);
      assertEquals(8, back.protocolSeconds().length);
      // a forged list length is refused instead of allocating whatever it says
      FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
      forged.writeBoolean(true);
      forged.writeVarInt(1);
      forged.writeFloat(0.0F);
      forged.writeVarInt(0);
      forged.writeVarInt(0);
      forged.writeVarInt(0);
      forged.writeDouble(0.0);
      forged.writeDouble(0.0);
      forged.writeFloat(1.0F);
      forged.writeVarInt(1_000_000);
      assertThrows(IllegalArgumentException.class, () -> new UltronStateS2CPacket(forged));
   }

   @Test
   void fxRoundTripsEveryKind() {
      for (int kind = 0; kind <= UltronFxS2CPacket.LAST; kind++) {
         UltronFxS2CPacket p = new UltronFxS2CPacket(kind, new Vec3(1.5 * kind, 70.25, -3.0), new Vec3(0.5, -1.0, 0.25), 2.5F, -7 * kind);
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         p.toBytes(buf);
         UltronFxS2CPacket back = new UltronFxS2CPacket(buf);
         assertEquals(p, back);
         assertEquals(0, buf.readableBytes());
         assertEquals(p.at(), back.at());
      }
      assertEquals(31, UltronFxS2CPacket.LAST, "fx kinds are contiguous ids 0..LAST");
   }
}
