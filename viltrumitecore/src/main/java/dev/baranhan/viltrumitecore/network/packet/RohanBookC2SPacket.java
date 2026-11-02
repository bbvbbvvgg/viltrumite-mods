package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * A reader's or an opened player's input on a Heaven's Door book: WRITE a registered line with a parameter index,
 * RIP a page, CLOSE the book, FLIP (page turn sound for the people around) or STRUGGLE (the opened player fights the
 * book). Everything is re-validated against the sender's live session (RohanBooks).
 */
public record RohanBookC2SPacket(int op, int session, String instruction, int param) {
   public static final int WRITE = 0;
   public static final int RIP = 1;
   public static final int CLOSE = 2;
   public static final int FLIP = 3;
   public static final int STRUGGLE = 4;

   public RohanBookC2SPacket(FriendlyByteBuf buf) {
      this(buf.readByte(), buf.readVarInt(), buf.readUtf(48), buf.readVarInt());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.op);
      buf.writeVarInt(this.session);
      buf.writeUtf(this.instruction == null ? "" : this.instruction, 48);
      buf.writeVarInt(this.param);
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer sender = context.getSender();
         if (sender != null) {
            dev.baranhan.viltrumitecore.hero.rohan.RohanBooks.onClient(sender, this.op, this.session, this.instruction, this.param);
         }
      });
      context.setPacketHandled(true);
   }
}
