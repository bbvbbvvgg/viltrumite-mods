package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * SCP-106 and one player (protocol 30): {@link #STATE} — the instance you are in (slot, its seed for the client's
 * ambience, sigils and key; slot -1 when you are out), {@link #CAPTURE} — you are being pulled into the floor,
 * {@link #NOTE} — a note or a diary to read, {@link #SCARE} — something happened to you (abyss, decoy, drag),
 * {@link #ESCAPE} — you got out (route id).
 */
public class Scp106S2CPacket {
   public static final byte STATE = 0;
   public static final byte CAPTURE = 1;
   public static final byte NOTE = 2;
   public static final byte SCARE = 3;
   public static final byte ESCAPE = 4;

   public final byte kind;
   public final int a;
   public final int b;
   public final long seed;

   public Scp106S2CPacket(byte kind, int a, int b, long seed) {
      this.kind = kind;
      this.a = a;
      this.b = b;
      this.seed = seed;
   }

   public Scp106S2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.a = buf.readVarInt();
      this.b = buf.readVarInt();
      this.seed = buf.readLong();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeVarInt(this.a);
      buf.writeVarInt(this.b);
      buf.writeLong(this.seed);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.scp106.PocketClient.onPacket(this.kind, this.a, this.b, this.seed)));
      context.setPacketHandled(true);
   }
}
