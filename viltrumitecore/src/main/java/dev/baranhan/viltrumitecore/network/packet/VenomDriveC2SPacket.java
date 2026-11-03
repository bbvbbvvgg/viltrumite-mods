package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

/**
 * Input of a takeover. From the possessor ({@code resist} false): movement, look and the edges of attack / use /
 * a Spider ability (1..4, 0 none; negative = released). From the host ({@code resist} true): one resistance press.
 * The server checks who sent it against the session (VenomPossession.input); values are clamped here.
 */
public record VenomDriveC2SPacket(boolean resist, float forward, float strafe, float yaw, float pitch, int bits, int ability) {
   public static final int JUMP = 1, SNEAK = 2, SPRINT = 4, ATTACK = 8, USE = 16, ATTACK_HELD = 32;

   public VenomDriveC2SPacket(FriendlyByteBuf buf) {
      this(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readByte());
   }

   public static VenomDriveC2SPacket resistPress() {
      return new VenomDriveC2SPacket(true, 0, 0, 0, 0, 0, 0);
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeBoolean(this.resist);
      buf.writeFloat(this.forward);
      buf.writeFloat(this.strafe);
      buf.writeFloat(this.yaw);
      buf.writeFloat(this.pitch);
      buf.writeVarInt(this.bits);
      buf.writeByte(this.ability);
   }

   /** Pure: finite, bounded values (a hostile client cannot send NaN or a speed hack). */
   public VenomDriveC2SPacket clamped() {
      float f = Float.isFinite(this.forward) ? Mth.clamp(this.forward, -1.0F, 1.0F) : 0.0F;
      float s = Float.isFinite(this.strafe) ? Mth.clamp(this.strafe, -1.0F, 1.0F) : 0.0F;
      float y = Float.isFinite(this.yaw) ? Mth.wrapDegrees(this.yaw) : 0.0F;
      float p = Float.isFinite(this.pitch) ? Mth.clamp(this.pitch, -90.0F, 90.0F) : 0.0F;
      return new VenomDriveC2SPacket(this.resist, f, s, y, p, this.bits & 63, Mth.clamp(this.ability, -4, 4));
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null) {
            dev.baranhan.viltrumitecore.hero.venom.VenomPossession.input(sender, this.clamped());
         }
      });
      context.setPacketHandled(true);
   }
}
