package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Ultimate Hero Expansion: start (or, with scene 0, cancel) a short ultimate scene on clients. */
public class BudgetSceneS2CPacket {
   private final int entityId;
   private final int scene;
   private final int ticks;

   public BudgetSceneS2CPacket(int entityId, int scene, int ticks) {
      this.entityId = entityId;
      this.scene = scene;
      this.ticks = ticks;
   }

   public BudgetSceneS2CPacket(FriendlyByteBuf buffer) {
      this.entityId = buffer.readVarInt();
      this.scene = buffer.readVarInt();
      this.ticks = Math.max(0, Math.min(100, buffer.readVarInt()));
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.entityId);
      buffer.writeVarInt(this.scene);
      buffer.writeVarInt(this.ticks);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.budget.BudgetSceneClient.receive(this.entityId, this.scene, this.ticks)));
      context.setPacketHandled(true);
   }
}
