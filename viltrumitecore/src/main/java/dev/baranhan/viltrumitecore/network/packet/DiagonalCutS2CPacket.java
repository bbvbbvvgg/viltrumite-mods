package dev.baranhan.viltrumitecore.network.packet;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
public record DiagonalCutS2CPacket(UUID castId,UUID casterId,DiagonalCutGeometry shape,long startTime,int phase) {
   public DiagonalCutS2CPacket {
      if(phase<0||phase>3) throw new IllegalArgumentException("diagonal phase");
   }
   public DiagonalCutS2CPacket(FriendlyByteBuf b) {
      this(b.readUUID(),b.readUUID(),new DiagonalCutGeometry(vec(b),vec(b),b.readLong()),b.readLong(),b.readUnsignedByte());
   }
   private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
   private static void vec(FriendlyByteBuf b,Vec3 v) { b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z); }
   public void toBytes(FriendlyByteBuf b) {
      b.writeUUID(castId);b.writeUUID(casterId);vec(b,shape.origin());vec(b,shape.forward());
      b.writeLong(shape.seed());b.writeLong(startTime);b.writeByte(phase);
   }
   public void handle(Supplier<NetworkEvent.Context> c) {
      c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
         dev.baranhan.viltrumitecore.client.reinhard.cut.DiagonalCutScene.onPacket(this)));
      c.get().setPacketHandled(true);
   }
}
