package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client side of {@link CaptainFxS2CPacket}: routes each event to the VFX, shake and motion code. */
public final class CaptainFxClient {
   private CaptainFxClient() {
   }

   public static void handle(CaptainFxS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      Vec3 a = new Vec3(p.x(), p.y(), p.z());
      Vec3 b = new Vec3(p.x2(), p.y2(), p.z2());
      if (p.type() == CaptainFx.MOTION) {
         if (mc.player != null && mc.player.getId() == p.entityId()) {
            CaptainMotion.start(a, p.arg(), b.x > 0.5);
         }
         return;
      }
      CaptainVfx.play(mc.level, p.type(), p.entityId(), p.arg(), a, b, p.ids());
   }
}
