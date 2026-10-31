package dev.baranhan.viltrumitecore.worldevent.chitauri.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Invasion state for clients in the level: portal centre, radius and tilt, the anchor (beam
 * source), the phase with its tick and length, a seed. Sent on start, every phase change, every
 * 2 s and on login / respawn / dimension change; {@code active=false} closes the portal at once
 * (emergency stop). The client advances the phase tick itself between packets.
 */
public record ChitauriPortalS2CPacket(boolean active, double cx, double cy, double cz, float radius, float tiltYaw, double ax, double ay, double az,
   int phase, int phaseTick, int phaseLength, long seed) {
   public ChitauriPortalS2CPacket(FriendlyByteBuf b) {
      this(b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readDouble(), b.readDouble(), b.readDouble(),
         b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong());
   }

   public static ChitauriPortalS2CPacket inactive() {
      return new ChitauriPortalS2CPacket(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeBoolean(this.active);
      buf.writeDouble(this.cx);
      buf.writeDouble(this.cy);
      buf.writeDouble(this.cz);
      buf.writeFloat(this.radius);
      buf.writeFloat(this.tiltYaw);
      buf.writeDouble(this.ax);
      buf.writeDouble(this.ay);
      buf.writeDouble(this.az);
      buf.writeVarInt(this.phase);
      buf.writeVarInt(this.phaseTick);
      buf.writeVarInt(this.phaseLength);
      buf.writeLong(this.seed);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.chitauri.ChitauriPortalClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
