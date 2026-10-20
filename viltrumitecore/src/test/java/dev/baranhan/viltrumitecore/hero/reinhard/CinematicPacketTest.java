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
   @Test void twelveRainContactsRoundTripAndRejectBadIndex() {
      for(int i=0;i<12;i++) {
         var p=new CutRainS2CPacket(new Vec3(1,2,3),new Vec3(0,0,2),i,7+i*3);
         var b=new FriendlyByteBuf(Unpooled.buffer());try{p.toBytes(b);assertEquals(p,new CutRainS2CPacket(b));assertEquals(0,b.readableBytes());}finally{b.release();}
      }
      assertThrows(IllegalArgumentException.class,()->new CutRainS2CPacket(Vec3.ZERO,Vec3.ZERO,12,0));
      assertThrows(IllegalArgumentException.class,()->new CutRainS2CPacket(new Vec3(Double.NaN,0,0),Vec3.ZERO,0,0));
   }
}
