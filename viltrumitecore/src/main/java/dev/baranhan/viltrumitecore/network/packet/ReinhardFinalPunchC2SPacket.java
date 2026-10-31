package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * The secret Shift + N chord of Reinhard (THE FINAL PUNCH): only the entity id the client saw
 * in its look cone (−1 = none). The server re-picks and re-validates everything; a press
 * without a target is refused (refuse sound, nothing spent).
 */
public record ReinhardFinalPunchC2SPacket(int targetHint) {
   public ReinhardFinalPunchC2SPacket(FriendlyByteBuf buffer) {
      this(buffer.readVarInt());
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.targetHint);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null && HeroRegistry.get(player) == ReinhardHero.INSTANCE) {
            ReinhardActions.handleFinalPunch(player, ReinhardState.ensure(player), this.targetHint);
         }
      });
      context.setPacketHandled(true);
   }
}
