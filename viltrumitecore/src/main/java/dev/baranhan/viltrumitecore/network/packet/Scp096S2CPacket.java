package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * SCP-096 and one player (protocol 28): {@link #TRIGGERED} — you saw its face (the sting, the camera pull, the
 * chase music from now on); {@link #TARGET} — you are still hunted (rejoin, resync; no sting); {@link #RELEASED} —
 * you are no longer hunted. Only the player concerned receives it; everything else comes from the entity state.
 */
public class Scp096S2CPacket {
   public static final byte TRIGGERED = 0;
   public static final byte TARGET = 1;
   public static final byte RELEASED = 2;

   public final byte kind;
   public final int entityId;

   public Scp096S2CPacket(byte kind, int entityId) {
      this.kind = kind;
      this.entityId = entityId;
   }

   public Scp096S2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.entityId = buf.readVarInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeVarInt(this.entityId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.scp096.Scp096ClientSounds.onPacket(this.kind, this.entityId)));
      context.setPacketHandled(true);
   }
}
