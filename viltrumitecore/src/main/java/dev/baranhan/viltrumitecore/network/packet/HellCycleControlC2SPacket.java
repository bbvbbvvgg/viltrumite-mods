package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/**
 * The rider's client reports the Hell Cycle's visual controls (throttle, steering, lean, lifts, engine speed, flags and
 * one-tick events) so every other client draws and hears the same bike. Purely cosmetic: the motion itself travels
 * through the vanilla vehicle packet and its server check. Accepted only from the bike's controlling passenger.
 */
public record HellCycleControlC2SPacket(int bikeId, int throttle, int steer, int lean, int lift, int rpm, int flags, int events) {
   public HellCycleControlC2SPacket(FriendlyByteBuf buf) {
      this(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
         buf.readUnsignedByte());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.bikeId);
      buf.writeByte(this.throttle);
      buf.writeByte(this.steer);
      buf.writeByte(this.lean);
      buf.writeByte(this.lift);
      buf.writeByte(this.rpm);
      buf.writeByte(this.flags);
      buf.writeByte(this.events);
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer sender = context.getSender();
         if (sender == null) {
            return;
         }
         Entity vehicle = sender.getVehicle();
         if (vehicle instanceof HellCycleEntity bike && bike.getId() == this.bikeId && bike.getControllingPassenger() == sender) {
            bike.applyRiderControl(this.throttle, this.steer, this.lean, this.lift, this.rpm, this.flags, this.events);
         }
      });
      context.setPacketHandled(true);
   }
}
