package dev.baranhan.viltrumitecore.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Fixed target metadata and verdict; server age lets every dimension and a late joiner catch up. */
public record ErasureS2CPacket(UUID pauseId,long startGameTime,ResourceKey<Level> targetLevel,int targetId,Vec3 targetPos,
      float bbWidth,float bbHeight,boolean humanoid,boolean erase,int age) {
   public ErasureS2CPacket(FriendlyByteBuf b) {
      this(b.readUUID(),b.readLong(),ResourceKey.create(Registries.DIMENSION,b.readResourceLocation()),b.readVarInt(),
         new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readVarInt());
   }
   public ErasureS2CPacket withAge(int ticks) {return new ErasureS2CPacket(pauseId,startGameTime,targetLevel,targetId,targetPos,bbWidth,bbHeight,humanoid,erase,Math.max(0,ticks));}
   public void toBytes(FriendlyByteBuf b) {
      b.writeUUID(pauseId);b.writeLong(startGameTime);b.writeResourceLocation(targetLevel.location());b.writeVarInt(targetId);
      b.writeDouble(targetPos.x);b.writeDouble(targetPos.y);b.writeDouble(targetPos.z);b.writeFloat(bbWidth);b.writeFloat(bbHeight);
      b.writeBoolean(humanoid);b.writeBoolean(erase);b.writeVarInt(age);
   }
   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.baranhan.viltrumitecore.client.reinhard.ReinhardClient.receiveErasure(this)));
      ctx.get().setPacketHandled(true);
   }
}
