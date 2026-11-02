package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Visual event of Rohan / Heaven's Door for every client tracking it: a book opening on a body (3D pages), a written
 * line, closing, a torn page, a pen nib streak, the sketch, the manuscript, the counter, resisted / reflected lines.
 */
public record RohanFxS2CPacket(int kind, int sourceId, int targetId, int ticks, Vec3 at) {
   public static final int BOOK_OPEN = 0;
   public static final int BOOK_WRITE = 1;
   public static final int BOOK_CLOSE = 2;
   public static final int PAGE_RIP = 3;
   public static final int NIB = 4;
   public static final int SKETCH = 5;
   public static final int MANUSCRIPT = 6;
   public static final int COUNTER = 7;
   public static final int RESIST = 8;
   public static final int REFLECT = 9;
   public static final int SELF_EDIT = 10;
   public static final int GUARDED = 11;

   public RohanFxS2CPacket(FriendlyByteBuf buf) {
      this(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeVarInt(this.sourceId);
      buf.writeVarInt(this.targetId);
      buf.writeVarInt(this.ticks);
      buf.writeDouble(this.at.x);
      buf.writeDouble(this.at.y);
      buf.writeDouble(this.at.z);
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.rohan.RohanClientNet.receiveFx(this)));
      context.setPacketHandled(true);
   }
}
