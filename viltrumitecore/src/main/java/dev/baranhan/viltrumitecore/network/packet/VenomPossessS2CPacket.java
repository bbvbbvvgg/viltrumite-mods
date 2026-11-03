package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Venom's host takeover for the clients. {@code op}: 0 ENGULF (the symbiote leaps on the host), 1 CONTROL (it rides
 * the host), 2 STATUS (remaining ticks and resistance), 3 END ({@code reason} = VenomPossession.End ordinal), 4 DRIVE
 * (to the host only: the possessor's movement this tick). Entity ids; {@code bits}: 1 = Spider abilities through the
 * adapter, 2 = the possessor is the boss, 4 = jump, 8 = sneak, 16 = sprint.
 */
public record VenomPossessS2CPacket(int op, int hostId, int possessorId, int remaining, int max, int resist, int bits, float forward, float strafe,
   float yaw, float pitch, int reason) {
   public static final int ENGULF = 0, CONTROL = 1, STATUS = 2, END = 3, DRIVE = 4;
   public static final int SPIDER = 1, BOSS = 2, JUMP = 4, SNEAK = 8, SPRINT = 16;

   public VenomPossessS2CPacket(FriendlyByteBuf buf) {
      this(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
         buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
   }

   public static VenomPossessS2CPacket state(int op, int hostId, int possessorId, int remaining, int max, int resist, int bits, int reason) {
      return new VenomPossessS2CPacket(op, hostId, possessorId, remaining, max, resist, bits, 0, 0, 0, 0, reason);
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.op);
      buf.writeVarInt(this.hostId);
      buf.writeVarInt(this.possessorId);
      buf.writeVarInt(this.remaining);
      buf.writeVarInt(this.max);
      buf.writeVarInt(this.resist);
      buf.writeVarInt(this.bits);
      buf.writeFloat(this.forward);
      buf.writeFloat(this.strafe);
      buf.writeFloat(this.yaw);
      buf.writeFloat(this.pitch);
      buf.writeVarInt(this.reason);
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.venom.VenomPossessionClient.receive(this)));
      context.setPacketHandled(true);
   }
}
