package dev.baranhan.viltrumitecore.hero.dio.net;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * State of one DIO stopped-time field for nearby clients: identity, center,
 * radius, clock and the frozen entity ids (bounded). {@code active=false} ends it.
 */
public record DioTimeS2CPacket(boolean active, UUID effectId, int casterId, double x, double y, double z, float radius,
   int duration, int age, boolean scripted, int[] frozen) {
   public static final int MAX_IDS = 512;

   public DioTimeS2CPacket(FriendlyByteBuf b) {
      this(b.readBoolean(), b.readUUID(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(),
         b.readVarInt(), b.readVarInt(), b.readBoolean(), readIds(b));
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
      b.writeBoolean(this.active);
      b.writeUUID(this.effectId);
      b.writeVarInt(this.casterId);
      b.writeDouble(this.x);
      b.writeDouble(this.y);
      b.writeDouble(this.z);
      b.writeFloat(this.radius);
      b.writeVarInt(this.duration);
      b.writeVarInt(this.age);
      b.writeBoolean(this.scripted);
      int n = Math.min(MAX_IDS, this.frozen.length);
      b.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         b.writeVarInt(this.frozen[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.dio.TimeStopClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
