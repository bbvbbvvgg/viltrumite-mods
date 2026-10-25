package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ServerRules;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Server rules mirrored by the client's local input claims ({@link ServerRules}); sent at login. */
public record ServerRulesS2CPacket(boolean pvp) {
   public ServerRulesS2CPacket(FriendlyByteBuf buffer) {
      this(buffer.readBoolean());
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeBoolean(this.pvp);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      // ServerRules is common code: no client class is touched here.
      context.get().enqueueWork(() -> ServerRules.receive(this.pvp));
      context.get().setPacketHandled(true);
   }
}
