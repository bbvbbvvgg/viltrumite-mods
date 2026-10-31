package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Opens the Iron Legion menu: the suit selection (roster view and the
 * server's suggestion) or, while the Legion flies, the command page.
 */
public class LegionMenuS2CPacket {
   private final VeronicaView view;
   private final boolean active;
   private final int order;
   private final int priority;
   private final int[] suggested;

   public LegionMenuS2CPacket(VeronicaView view, boolean active, int order, int priority, int[] suggested) {
      this.view = view;
      this.active = active;
      this.order = order;
      this.priority = priority;
      this.suggested = suggested;
   }

   public LegionMenuS2CPacket(FriendlyByteBuf buffer) {
      this.view = VeronicaView.read(buffer);
      this.active = buffer.readBoolean();
      this.order = buffer.readVarInt();
      this.priority = buffer.readVarInt();
      int n = Math.max(0, Math.min(LegionRules.MAX_SUITS, buffer.readVarInt()));
      this.suggested = new int[n];
      for (int i = 0; i < n; i++) {
         this.suggested[i] = buffer.readVarInt();
      }
   }

   public void toBytes(FriendlyByteBuf buffer) {
      this.view.write(buffer);
      buffer.writeBoolean(this.active);
      buffer.writeVarInt(this.order);
      buffer.writeVarInt(this.priority);
      int n = Math.min(LegionRules.MAX_SUITS, this.suggested.length);
      buffer.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         buffer.writeVarInt(this.suggested[i]);
      }
   }

   public VeronicaView view() {
      return this.view;
   }

   public boolean active() {
      return this.active;
   }

   public int order() {
      return this.order;
   }

   public int priority() {
      return this.priority;
   }

   public int[] suggested() {
      return this.suggested;
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.openMenu(this)));
      context.setPacketHandled(true);
   }
}
