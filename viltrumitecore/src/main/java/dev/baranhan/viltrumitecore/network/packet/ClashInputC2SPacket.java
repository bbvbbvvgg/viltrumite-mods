package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.combat.clash.BeamStruggles;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * One mash pulse (attack key) of a struggling player. The server checks the sender owns that struggle, applies the
 * window cap and the beat with its own clock; the client only says "pressed now".
 */
public class ClashInputC2SPacket {
   private final int struggleId;

   public ClashInputC2SPacket(int struggleId) {
      this.struggleId = struggleId;
   }

   public ClashInputC2SPacket(FriendlyByteBuf buf) {
      this.struggleId = buf.readVarInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.struggleId);
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null && player.isAlive() && !player.isSpectator()) {
            BeamStruggles.pulse(player, this.struggleId);
         }
      });
      context.setPacketHandled(true);
   }
}
