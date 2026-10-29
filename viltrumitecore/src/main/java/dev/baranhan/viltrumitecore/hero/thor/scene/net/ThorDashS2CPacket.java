package dev.baranhan.viltrumitecore.hero.thor.scene.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The server refused a double-WASD dash the client already predicted
 * (integration of session C with the server checks): the client stops the
 * rest of the burst. The server also snaps the player back to its own
 * (pre-dash) position, so a refused dash moves nothing. No payload.
 */
public class ThorDashS2CPacket {
   public ThorDashS2CPacket() {
   }

   public ThorDashS2CPacket(FriendlyByteBuf buf) {
   }

   public void toBytes(FriendlyByteBuf buf) {
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> dev.baranhan.viltrumitecore.client.thor.scene.ThorSceneClient::dashRefused));
      context.get().setPacketHandled(true);
   }
}
