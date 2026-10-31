package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ironman.legion.IronManLegion;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** The suits picked in the Legion menu (mark ordinals, at most three). The server validates everything. */
public class LegionCallC2SPacket {
   private final int[] marks;

   public LegionCallC2SPacket(int[] marks) {
      this.marks = marks;
   }

   public LegionCallC2SPacket(FriendlyByteBuf buffer) {
      int n = Math.max(0, Math.min(LegionRules.MAX_SUITS, buffer.readVarInt()));
      this.marks = new int[n];
      for (int i = 0; i < n; i++) {
         this.marks[i] = buffer.readVarInt();
      }
   }

   public void toBytes(FriendlyByteBuf buffer) {
      int n = Math.min(LegionRules.MAX_SUITS, this.marks.length);
      buffer.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         buffer.writeVarInt(this.marks[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            IronManLegion.call(player, this.marks);
         }
      });
      context.setPacketHandled(true);
   }
}
