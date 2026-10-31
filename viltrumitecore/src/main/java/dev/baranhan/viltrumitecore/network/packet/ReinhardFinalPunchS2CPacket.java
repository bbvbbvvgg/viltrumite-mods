package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * THE FINAL PUNCH beats for every tracking client: START (target, jaw kind, facing, jaw point),
 * IMPACT (the jaw leaves the head at {@code at} along {@code dir}) and CANCEL (restore now).
 * The timeline itself rides the caster's hero snapshot.
 */
public record ReinhardFinalPunchS2CPacket(int type, int casterId, int targetId, int jaw, int kind, float yaw, Vec3 at, Vec3 dir, boolean landed) {
   public static final int START = 0;
   public static final int IMPACT = 1;
   public static final int CANCEL = 2;

   public ReinhardFinalPunchS2CPacket(FriendlyByteBuf b) {
      this(b.readByte(), b.readVarInt(), b.readVarInt(), b.readByte(), b.readByte(), b.readFloat(),
         new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readBoolean());
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeByte(this.type);
      b.writeVarInt(this.casterId);
      b.writeVarInt(this.targetId);
      b.writeByte(this.jaw);
      b.writeByte(this.kind);
      b.writeFloat(this.yaw);
      b.writeDouble(this.at.x);
      b.writeDouble(this.at.y);
      b.writeDouble(this.at.z);
      b.writeFloat((float) this.dir.x);
      b.writeFloat((float) this.dir.y);
      b.writeFloat((float) this.dir.z);
      b.writeBoolean(this.landed);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchClient.receive(this)));
      ctx.get().setPacketHandled(true);
   }
}
