package dev.baranhan.viltrumitecore.network.packet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
public record CutRainS2CPacket(Vec3 origin,Vec3 direction,int index,long contactTime) {
   public CutRainS2CPacket {
      if(index<0||index>=12||!Double.isFinite(origin.lengthSqr())||!Double.isFinite(direction.lengthSqr()))throw new IllegalArgumentException("rain wave");
      direction=direction.normalize();
   }
   public CutRainS2CPacket(FriendlyByteBuf b) {this(vec(b),vec(b),b.readUnsignedByte(),b.readLong());}
   private static Vec3 vec(FriendlyByteBuf b) {return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
   private static void vec(FriendlyByteBuf b,Vec3 v) {b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
   public void toBytes(FriendlyByteBuf b) {vec(b,origin);vec(b,direction);b.writeByte(index);b.writeLong(contactTime);}
   public void handle(Supplier<NetworkEvent.Context> c) {
      c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.baranhan.viltrumitecore.client.reinhard.cut.CutRainWaves.receive(this)));
      c.get().setPacketHandled(true);
   }
}
