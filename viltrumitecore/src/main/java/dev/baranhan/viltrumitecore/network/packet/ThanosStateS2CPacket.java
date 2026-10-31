package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The Thanos event as one player should see it: the stage, the countdown (participants), the
 * scene clock (everyone during the snap), Thanos's entity id and where he stands (his feet and
 * body yaw; during the snap the place of the snap, kept after he is gone). Sent every few ticks
 * while it runs; {@link #NONE} ends everything on the client (HUD, music, cameras, scene).
 * {@code hud} is false for players who only see the universe scene; {@code here} tells whether
 * the player is in his dimension (the scene is staged around Thanos or around the player).
 */
public record ThanosStateS2CPacket(int stage, int thanosId, int ticksLeft, int duration, int phase, int stageTick, boolean hud, double x, double y,
   double z, float yaw, boolean here) {
   public static final int NONE = 0, INTRO = 1, FIGHT = 2, VICTORY = 3, SNAP = 4, SNAP_WAIT = 5;

   public ThanosStateS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readDouble(),
         b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean());
   }

   public static ThanosStateS2CPacket none() {
      return new ThanosStateS2CPacket(NONE, -1, 0, 0, 0, 0, false, 0.0, 0.0, 0.0, 0.0F, false);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeVarInt(this.stage);
      buf.writeVarInt(this.thanosId);
      buf.writeVarInt(this.ticksLeft);
      buf.writeVarInt(this.duration);
      buf.writeVarInt(this.phase);
      buf.writeVarInt(this.stageTick);
      buf.writeBoolean(this.hud);
      buf.writeDouble(this.x);
      buf.writeDouble(this.y);
      buf.writeDouble(this.z);
      buf.writeFloat(this.yaw);
      buf.writeBoolean(this.here);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.thanos.ThanosClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
