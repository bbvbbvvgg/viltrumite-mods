package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.combat.finisher.FinisherService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Finish request (wave1-22, task T6): the client asks the server to finish the entity
 * {@code targetId}. It carries the id only — the client's own pick is a prediction, and
 * {@link FinisherService} re-reads every fact (life, level, reach, line of sight, health, boss
 * threshold, the ally rule, PvP and the per-player cooldown) before it acts. Wire: varint targetId.
 * <p>
 * The id travels exactly as it is, with no clamp: entity ids keep growing on a long-lived server past
 * any ceiling we could pick, and a clamp would silently retarget the request at another body. An
 * unknown or negative id finds no entity in the level and the request is refused there.
 */
public final class FinisherRequestC2SPacket {
   public final int targetId;

   public FinisherRequestC2SPacket(int targetId) {
      this.targetId = targetId;
   }

   public FinisherRequestC2SPacket(FriendlyByteBuf buf) {
      this(buf.readVarInt());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.targetId);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      NetworkEvent.Context ctx = context.get();
      ctx.enqueueWork(() -> FinisherService.request(ctx.getSender(), this.targetId));
      ctx.setPacketHandled(true);
   }
}
