package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.combat.finisher.FinisherService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

/**
 * Finish request (wave1-22, task T6): the client asks the server to finish the entity
 * {@code targetId}. It carries the id only — the client's own pick is a prediction, and
 * {@link FinisherService} re-reads every fact (life, level, reach, line of sight, health, boss
 * threshold, PvP and ally rule, per-player cooldown) before it acts. Values are clamped on both ends.
 * Wire: varint targetId.
 */
public final class FinisherRequestC2SPacket {
   /** Largest entity id the wire accepts (vanilla ids stay well below this). */
   public static final int MAX_ENTITY_ID = 0xFFFFF;

   public final int targetId;

   public FinisherRequestC2SPacket(int targetId) {
      this.targetId = clamp(targetId);
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

   private static int clamp(int id) {
      return Mth.clamp(id, 0, MAX_ENTITY_ID);
   }
}
