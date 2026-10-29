package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeldInputs;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Claimed mouse button edge: button ordinal + pressed. The server gates presses in HeldInputs. */
public class HeroMouseC2SPacket {
   private final int buttonOrdinal;
   private final boolean pressed;

   public HeroMouseC2SPacket(MouseButton button, boolean pressed) {
      this.buttonOrdinal = button.ordinal();
      this.pressed = pressed;
   }

   public HeroMouseC2SPacket(FriendlyByteBuf buffer) {
      this.buttonOrdinal = buffer.readByte();
      this.pressed = buffer.readBoolean();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeByte(this.buttonOrdinal);
      buffer.writeBoolean(this.pressed);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         MouseButton button = MouseButton.byId(this.buttonOrdinal);
         if (player != null && button != null && !(this.pressed && dev.baranhan.viltrumitecore.hero.HeroHeldTargets.anchored(player))) {
            HeldInputs.onPacket(player, button, this.pressed);
         }
      });
      context.setPacketHandled(true);
   }
}
