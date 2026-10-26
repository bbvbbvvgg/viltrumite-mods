package dev.baranhan.viltrumitecore.hero.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import dev.baranhan.viltrumitecore.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class CinematicPacketTest {
   @Test void diagonalAllLifecyclePhasesRoundTrip() {
      for(int phase=0;phase<=3;phase++) {
         var p=new DiagonalCutS2CPacket(UUID.randomUUID(),UUID.randomUUID(),new DiagonalCutGeometry(new Vec3(20,100,-30),new Vec3(.7,.2,.7),123),500,phase);
         var b=new FriendlyByteBuf(Unpooled.buffer());try {
            p.toBytes(b);var copy=new DiagonalCutS2CPacket(b);
            assertEquals(p.castId(),copy.castId());assertEquals(p.casterId(),copy.casterId());assertEquals(p.phase(),copy.phase());assertEquals(500,copy.startTime());
            assertEquals(p.shape().origin(),copy.shape().origin());assertTrue(p.shape().forward().distanceTo(copy.shape().forward())<1E-12);assertEquals(0,b.readableBytes());
         } finally {b.release();}
      }
      assertThrows(IllegalArgumentException.class,()->new DiagonalCutS2CPacket(UUID.randomUUID(),UUID.randomUUID(),new DiagonalCutGeometry(Vec3.ZERO,new Vec3(0,0,1),0),0,4));
   }
   @Test void sawSphereStartRoundTripsAndRejectsBadScenes() {
      long[] mask={0x5L,-1L,0L};
      var p=new SawSphereS2CPacket(42,10.5,64,-3.25,90F,17,180,mask);
      var b=new FriendlyByteBuf(Unpooled.buffer());try{p.toBytes(b);assertEquals(p,new SawSphereS2CPacket(b));assertEquals(0,b.readableBytes());}finally{b.release();}
      mask[0]=0;assertEquals(0x5L,p.mask()[0],"the packet owns a copy of the mask");
      assertEquals(260,p.withAge(999).age());assertEquals(0,p.withAge(-4).age());assertEquals(p.mask().length,p.withAge(30).mask().length);
      assertThrows(IllegalArgumentException.class,()->new SawSphereS2CPacket(1,Double.NaN,0,0,0,0,0,new long[0]));
      assertThrows(IllegalArgumentException.class,()->new SawSphereS2CPacket(1,0,0,0,0,-1,0,new long[0]));
      assertThrows(IllegalArgumentException.class,()->new SawSphereS2CPacket(1,0,0,0,0,261,0,new long[0]));
      assertThrows(IllegalArgumentException.class,()->new SawSphereS2CPacket(1,0,0,0,0,0,0,new long[SawSphereS2CPacket.MAX_MASK+1]));
      var huge=new FriendlyByteBuf(Unpooled.buffer());try{
         huge.writeVarInt(1);huge.writeDouble(0);huge.writeDouble(0);huge.writeDouble(0);huge.writeFloat(0);huge.writeVarInt(0);huge.writeVarInt(0);
         huge.writeVarInt(SawSphereS2CPacket.MAX_MASK+1);
         assertThrows(IllegalArgumentException.class,()->new SawSphereS2CPacket(huge),"a forged length is refused before allocation");
      }finally{huge.release();}
   }
   @Test void sawSphereEndRoundTrips() {
      for(boolean interrupted:new boolean[]{false,true}) {
         var p=new SawSphereEndS2CPacket(7,interrupted);
         var b=new FriendlyByteBuf(Unpooled.buffer());try{p.toBytes(b);assertEquals(p,new SawSphereEndS2CPacket(b));assertEquals(0,b.readableBytes());}finally{b.release();}
      }
   }
}
