package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMovement;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Hulkbuster movement request (rework spec §5): dash direction, jet charge / release, hover on / off,
 * dive, the S key for the ground slam, and a held victim's struggle press. The server validates every
 * op with the same pure rules the client predicted with (HulkbusterMotion) and refuses otherwise.
 */
public class HulkbusterMotionC2SPacket {
   private final int op;
   private final int arg;

   public HulkbusterMotionC2SPacket(int op, int arg) {
      this.op = op;
      this.arg = arg;
   }

   public HulkbusterMotionC2SPacket(FriendlyByteBuf buf) {
      this.op = buf.readByte();
      this.arg = buf.readVarInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeByte(this.op);
      buf.writeVarInt(this.arg);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> {
         ServerPlayer player = context.get().getSender();
         if (player == null || !player.isAlive() || this.op < 0 || this.op > HulkbusterMovement.OP_STRUGGLE) {
            return;
         }

         if (this.op == HulkbusterMovement.OP_STRUGGLE) {
            // Any hero can be the held victim.
            HulkbusterMovement.handle(player, null, this.op, this.arg);
            return;
         }

         // IronManState.of is null for every other hero (the hero check lives in the hero package).
         IronManState state = IronManState.of(player);
         if (state != null) {
            HulkbusterMovement.handle(player, state, this.op, this.arg);
         }
      });
      context.get().setPacketHandled(true);
   }
}
