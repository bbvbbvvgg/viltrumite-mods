package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Wire contracts of the Legion: status, menu and the two C2S requests survive a round trip and clamp hostile input. */
class LegionWireTest {
   private static byte[] bytes(Consumer<FriendlyByteBuf> writer) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      writer.accept(buffer);
      byte[] out = new byte[buffer.readableBytes()];
      buffer.readBytes(out);
      return out;
   }

   private static FriendlyByteBuf buffer(byte[] data) {
      return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
   }

   private static LegionStatus.Suit suit(MarkId mark, float fraction, LegionPhase phase, LegionTactics.Stance stance, LegionRole role, boolean engaged) {
      return new LegionStatus.Suit(mark.ordinal(), fraction, phase.ordinal(), stance.ordinal(), role.ordinal(), engaged);
   }

   @Test
   void statusRoundTrip() {
      LegionStatus status = new LegionStatus(true, LegionOrder.ATTACK.ordinal(), LegionPriority.STRONGEST.ordinal(), 1234, 0b101, List.of(
         suit(MarkId.MARK_7, 0.8F, LegionPhase.ACTIVE, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, true),
         suit(MarkId.WAR_MACHINE_MK2, 0.25F, LegionPhase.BRAKE, LegionTactics.Stance.GUARD, LegionRole.OVERWATCH, false),
         suit(MarkId.IRON_HEART_MK3, 0.0F, LegionPhase.CRASH, LegionTactics.Stance.GUARD, LegionRole.FLANKER, false)));
      LegionStatus read = LegionStatus.read(buffer(bytes(status::write)));
      assertEquals(status, read);
      assertEquals(LegionOrder.ATTACK, read.orderValue());
      assertEquals(LegionPriority.STRONGEST, read.priorityValue());
      assertEquals(MarkId.WAR_MACHINE_MK2, read.suits().get(1).markId());
      assertEquals(LegionPhase.CRASH, read.suits().get(2).phaseValue());
      assertEquals(LegionStatus.NONE, LegionStatus.read(buffer(bytes(LegionStatus.NONE::write))));
   }

   @Test
   void statusCapsSuitsAndClampsDurability() {
      LegionStatus status = new LegionStatus(true, 0, 0, -5, 0, List.of(
         suit(MarkId.MARK_7, Float.NaN, LegionPhase.ACTIVE, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, false),
         suit(MarkId.MARK_42, 7.0F, LegionPhase.ACTIVE, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, false),
         suit(MarkId.MARK_15, -1.0F, LegionPhase.ACTIVE, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, false),
         suit(MarkId.MARK_39, 0.5F, LegionPhase.ACTIVE, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, false)));
      LegionStatus read = LegionStatus.read(buffer(bytes(status::write)));
      assertEquals(LegionRules.MAX_SUITS, read.suits().size());
      assertEquals(0, read.cooldown());
      assertEquals(0.0F, read.suits().get(0).fraction());
      assertEquals(1.0F, read.suits().get(1).fraction());
      assertEquals(0.0F, read.suits().get(2).fraction());
   }

   @Test
   void statusPacketRoundTripIsByteExact() {
      LegionStatus status = new LegionStatus(true, LegionOrder.HOLD.ordinal(), LegionPriority.TONY_TARGET.ordinal(), 60, 3, List.of(
         suit(MarkId.MARK_17, 0.6F, LegionPhase.READY, LegionTactics.Stance.GUARD, LegionRole.VANGUARD, false)));
      byte[] once = bytes(new LegionStatusS2CPacket(status)::toBytes);
      byte[] twice = bytes(new LegionStatusS2CPacket(buffer(once))::toBytes);
      assertArrayEquals(once, twice);
   }

   @Test
   void callPacketKeepsAtMostThreeMarks() {
      byte[] four = bytes(new LegionCallC2SPacket(new int[]{0, 3, 5, 6})::toBytes);
      byte[] three = bytes(new LegionCallC2SPacket(new int[]{0, 3, 5})::toBytes);
      assertArrayEquals(three, four);
      assertArrayEquals(three, bytes(new LegionCallC2SPacket(buffer(four))::toBytes));
      // A forged count larger than the cap is clamped on read.
      FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
      forged.writeVarInt(40);
      for (int i = 0; i < 40; i++) {
         forged.writeVarInt(i % 7);
      }

      assertEquals(three.length, bytes(new LegionCallC2SPacket(forged)::toBytes).length);
   }

   @Test
   void orderPacketCarriesNoChangeAsMinusOne() {
      for (int[] pair : new int[][]{{-1, 2}, {LegionOrder.RETURN.ordinal(), -1}, {0, 0}}) {
         byte[] once = bytes(new LegionOrderC2SPacket(pair[0], pair[1])::toBytes);
         assertArrayEquals(once, bytes(new LegionOrderC2SPacket(buffer(once))::toBytes));
      }

      // Below -1 collapses to "no change" on the wire.
      assertArrayEquals(bytes(new LegionOrderC2SPacket(-1, -1)::toBytes), bytes(new LegionOrderC2SPacket(-9, -30)::toBytes));
   }

   @Test
   void menuPacketRoundTrip() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION);
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      roster.breakMark(MarkId.MARK_17);
      VeronicaView view = VeronicaView.of(roster, VeronicaView.POD_STAYS, 0);
      LegionMenuS2CPacket packet = new LegionMenuS2CPacket(view, true, LegionOrder.FOLLOW.ordinal(), LegionPriority.NEAREST.ordinal(),
         new int[]{MarkId.MARK_15.ordinal(), MarkId.MARK_39.ordinal()});
      LegionMenuS2CPacket read = new LegionMenuS2CPacket(buffer(bytes(packet::toBytes)));
      assertTrue(read.active());
      assertEquals(LegionOrder.FOLLOW.ordinal(), read.order());
      assertEquals(LegionPriority.NEAREST.ordinal(), read.priority());
      assertArrayEquals(new int[]{MarkId.MARK_15.ordinal(), MarkId.MARK_39.ordinal()}, read.suggested());
      for (MarkId id : MarkId.values()) {
         assertEquals(roster.legionCallable(id), read.view().legionCallable(id), id.name());
      }
   }

   @Test
   void viewCallableMatchesRoster() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      roster.move(MarkId.MARK_15, MarkLocation.STORED, MarkLocation.LEGION);
      roster.move(MarkId.MARK_39, MarkLocation.STORED, MarkLocation.IN_DELIVERY);
      roster.breakMark(MarkId.MARK_17);
      roster.setDurability(MarkId.IRON_HEART_MK3, 0.0F);
      VeronicaView view = VeronicaView.read(buffer(bytes(VeronicaView.of(roster, VeronicaView.POD_STAYS, 0)::write)));
      int callable = 0;
      for (MarkId id : MarkId.values()) {
         assertEquals(roster.legionCallable(id), view.legionCallable(id), id.name());
         callable += view.legionCallable(id) ? 1 : 0;
      }

      assertEquals(2, callable, "Mark 7 and War Machine stay callable");
      assertFalse(view.legionCallable(MarkId.MARK_15), "already flying");
   }
}
