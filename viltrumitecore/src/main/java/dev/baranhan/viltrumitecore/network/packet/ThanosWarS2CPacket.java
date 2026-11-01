package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * THANOS 2.0 war state for a client (protocol 28). {@link #STATE}: the phase, the Sanctuary II's path
 * state (its start and hover points and the ticks into the state, so the client follows the same
 * {@code ShipPath}) and the war flags; {@link #NONE}: the war is over, drop everything;
 * {@link #HOLD}: the receiving player is held for {@code a} ticks ({@code b} the hold kind), his own
 * movement input is off meanwhile (0 ticks = released).
 */
public record ThanosWarS2CPacket(int op, int phase, int ship, int shipTick, double sx, double sy, double sz, double hx, double hy, double hz, int flags,
   int a, int b) {
   public static final int STATE = 0, NONE = 1, HOLD = 2;
   /** Flags of {@link #STATE}. */
   public static final int ALARM = 1, SMOKE = 2, GAUNTLET = 4, ORDER = 8, ARMY = 16, HIDE_SHIP = 32, BOMBARD = 64;

   public ThanosWarS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
         b.readDouble(), b.readVarInt(), b.readVarInt(), b.readVarInt());
   }

   public static ThanosWarS2CPacket none() {
      return new ThanosWarS2CPacket(NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
   }

   public static ThanosWarS2CPacket hold(int ticks, int kind) {
      return new ThanosWarS2CPacket(HOLD, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Math.max(0, ticks), kind);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeVarInt(this.op);
      buf.writeVarInt(this.phase);
      buf.writeVarInt(this.ship);
      buf.writeVarInt(this.shipTick);
      buf.writeDouble(this.sx);
      buf.writeDouble(this.sy);
      buf.writeDouble(this.sz);
      buf.writeDouble(this.hx);
      buf.writeDouble(this.hy);
      buf.writeDouble(this.hz);
      buf.writeVarInt(this.flags);
      buf.writeVarInt(this.a);
      buf.writeVarInt(this.b);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.thanos.war.ThanosWarClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
