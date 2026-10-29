package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Jinwoo presentation event (type in {@code hero.jinwoo.JinwooFx}): a source entity,
 * an int argument, two points and up to 16 entity ids. Owner motion instructions
 * ride the same packet. Client only consumes it ({@code client.jinwoo.JinwooFxClient}).
 */
public record JinwooFxS2CPacket(int type, int entityId, int arg, float x, float y, float z, float x2, float y2, float z2, int[] ids) {
   public static final int MAX_IDS = 16;

   public JinwooFxS2CPacket(FriendlyByteBuf b) {
      this(b.readByte(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), readIds(b));
   }

   private static int[] readIds(FriendlyByteBuf b) {
      int n = Math.min(MAX_IDS, b.readVarInt());
      int[] ids = new int[n];
      for (int i = 0; i < n; i++) {
         ids[i] = b.readVarInt();
      }
      return ids;
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeByte(this.type);
      b.writeVarInt(this.entityId);
      b.writeVarInt(this.arg);
      b.writeFloat(this.x);
      b.writeFloat(this.y);
      b.writeFloat(this.z);
      b.writeFloat(this.x2);
      b.writeFloat(this.y2);
      b.writeFloat(this.z2);
      int n = this.ids == null ? 0 : Math.min(MAX_IDS, this.ids.length);
      b.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         b.writeVarInt(this.ids[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> c) {
      c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.jinwoo.JinwooFxClient.handle(this)));
      c.get().setPacketHandled(true);
   }
}
