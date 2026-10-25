package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Fixed cast frame and terrain profile: no entity lookup needed to keep the scene alive. */
public record WorldCutS2CPacket(UUID castId, UUID casterId, WorldCutShape shape, int[] surface, long startTime, int phase) {
   public static final int START=0, BLAST=1, WORK=2, DONE=3;
   public WorldCutS2CPacket {
      if(surface.length != ReinhardRules.CUT_LENGTH || (phase < START || phase > DONE)) throw new IllegalArgumentException("Invalid world cut packet");
      surface=surface.clone();
   }
   public WorldCutS2CPacket(FriendlyByteBuf b) {
      this(b.readUUID(),b.readUUID(),new WorldCutShape(vec(b),vec(b),b.readLong()),
         b.readVarIntArray(ReinhardRules.CUT_LENGTH),b.readLong(),b.readUnsignedByte());
   }
   private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
   private static void vec(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
   public void toBytes(FriendlyByteBuf b) {
      b.writeUUID(castId); b.writeUUID(casterId); vec(b,shape.origin()); vec(b,shape.dirXZ());
      b.writeLong(shape.seed()); b.writeVarIntArray(surface); b.writeLong(startTime); b.writeByte(phase);
   }
   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutScene.onPacket(this)));
      context.get().setPacketHandled(true);
   }
}
