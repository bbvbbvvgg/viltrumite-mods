package dev.baranhan.viltrumitecore.worldevent.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Wire format of the two invasion packets (PROTOCOL 17): encode → decode gives the same record, nothing left over. */
class ChitauriPacketsTest {
   @Test
   void portalStateRoundTrips() {
      ChitauriPortalS2CPacket p = new ChitauriPortalS2CPacket(true, 12.5, 98.25, -40.75, ChitauriRules.PORTAL_RADIUS, 137.5F, 12.5, 36.0, -40.75,
         ChitauriPhase.LEVIATHAN.id(), 1234, ChitauriRules.LEVIATHAN_TICKS, 0x1234_5678_9ABC_DEF0L);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      p.toBytes(buf);
      ChitauriPortalS2CPacket back = new ChitauriPortalS2CPacket(buf);
      assertEquals(p, back);
      assertEquals(0, buf.readableBytes());
      assertEquals(ChitauriPhase.LEVIATHAN, ChitauriPhase.byId(back.phase()));
   }

   @Test
   void inactivePortalClosesEverything() {
      ChitauriPortalS2CPacket off = ChitauriPortalS2CPacket.inactive();
      assertFalse(off.active());
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      off.toBytes(buf);
      assertEquals(off, new ChitauriPortalS2CPacket(buf));
   }

   @Test
   void fxRoundTripsEveryKind() {
      int[] kinds = {ChitauriFxS2CPacket.BOLT_IMPACT, ChitauriFxS2CPacket.CANNON_IMPACT, ChitauriFxS2CPacket.EXPLOSION, ChitauriFxS2CPacket.CHARIOT_BREAKUP,
         ChitauriFxS2CPacket.LEVIATHAN_HIT, ChitauriFxS2CPacket.LEVIATHAN_ROAR, ChitauriFxS2CPacket.LEVIATHAN_CRASH, ChitauriFxS2CPacket.LEVIATHAN_SMASH,
         ChitauriFxS2CPacket.DROP_LANDING, ChitauriFxS2CPacket.PORTAL_STRIKE, ChitauriFxS2CPacket.SEVER, ChitauriFxS2CPacket.MUZZLE,
         ChitauriFxS2CPacket.SEGMENT_LAND, ChitauriFxS2CPacket.VANISH, ChitauriFxS2CPacket.TETHER, ChitauriFxS2CPacket.SLAM, ChitauriFxS2CPacket.SIEGE_CHARGE,
         ChitauriFxS2CPacket.SIEGE_IMPACT, ChitauriFxS2CPacket.BEACON_DEPLOY, ChitauriFxS2CPacket.BEACON_DOWN, ChitauriFxS2CPacket.SIGNAL, ChitauriFxS2CPacket.RELEASE,
         ChitauriFxS2CPacket.ARMOR_BREAK, ChitauriFxS2CPacket.THRUST};
      Set<Integer> seen = new HashSet<>();
      for (int i = 0; i < kinds.length; i++) {
         assertEquals(i, kinds[i], "kinds are contiguous network ids");
         seen.add(kinds[i]);
         ChitauriFxS2CPacket p = new ChitauriFxS2CPacket(kinds[i], new Vec3(1.5 * i, 70.25, -3.0), new Vec3(0.5, -1.0, 0.25), 2.5F, -7 * i);
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         p.toBytes(buf);
         ChitauriFxS2CPacket back = new ChitauriFxS2CPacket(buf);
         assertEquals(p, back);
         assertEquals(0, buf.readableBytes());
         assertEquals(p.at(), back.at());
         assertEquals(p.d(), back.d());
      }
      assertEquals(kinds.length, seen.size());
   }
}
