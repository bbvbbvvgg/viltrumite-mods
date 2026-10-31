package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ironman.legion.IronManLegion;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A squad order (LegionOrder ordinal) and/or a priority (LegionPriority ordinal); -1 = unchanged. */
public class LegionOrderC2SPacket {
   private final int order;
   private final int priority;

   public LegionOrderC2SPacket(int order, int priority) {
      this.order = order;
      this.priority = priority;
   }

   public LegionOrderC2SPacket(FriendlyByteBuf buffer) {
      this.order = buffer.readVarInt() - 1;
      this.priority = buffer.readVarInt() - 1;
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(Math.max(-1, this.order) + 1);
      buffer.writeVarInt(Math.max(-1, this.priority) + 1);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            IronManLegion.order(player, this.order, this.priority);
         }
      });
      context.setPacketHandled(true);
   }
}
