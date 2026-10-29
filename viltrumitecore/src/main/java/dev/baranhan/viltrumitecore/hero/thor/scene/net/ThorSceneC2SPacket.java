package dev.baranhan.viltrumitecore.hero.thor.scene.net;

import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Scene input from a client (session C). Every op is re-validated against
 * the sender's live scene on the server: a resist press counts only for the
 * victim of a resistible hold and only with the shown key; a teleport
 * confirm only for the selecting Thor and only on a point the server judges
 * safe and visible; a dash only for a Thor allowed to dash now.
 */
public class ThorSceneC2SPacket {
   public enum Op {
      RESIST,
      TELEPORT_CONFIRM,
      TELEPORT_CANCEL,
      DASH
   }

   private final Op op;
   private final int arg;
   private final BlockPos pos;

   public ThorSceneC2SPacket(Op op, int arg, BlockPos pos) {
      this.op = op;
      this.arg = arg;
      this.pos = pos;
   }

   public ThorSceneC2SPacket(FriendlyByteBuf buf) {
      Op[] ops = Op.values();
      int id = buf.readByte();
      this.op = id >= 0 && id < ops.length ? ops[id] : null;
      this.arg = buf.readByte();
      this.pos = buf.readBlockPos();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeByte(this.op == null ? -1 : this.op.ordinal());
      buf.writeByte(this.arg);
      buf.writeBlockPos(this.pos);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      NetworkEvent.Context ctx = context.get();
      ctx.enqueueWork(() -> {
         ServerPlayer sender = ctx.getSender();
         if (sender != null && this.op != null) {
            ThorScenes.onClientInput(sender, this.op, this.arg, this.pos);
         }
      });
      ctx.setPacketHandled(true);
   }
}
