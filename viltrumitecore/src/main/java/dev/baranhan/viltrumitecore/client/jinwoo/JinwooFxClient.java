package dev.baranhan.viltrumitecore.client.jinwoo;

import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFx;
import dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket;
import net.minecraft.world.phys.Vec3;

/** Client consumer of {@link JinwooFxS2CPacket}: owner motion plus the Jinwoo VFX events. */
public final class JinwooFxClient {
   private JinwooFxClient() {
   }

   public static void handle(JinwooFxS2CPacket p) {
      Vec3 a = new Vec3(p.x(), p.y(), p.z());
      Vec3 b = new Vec3(p.x2(), p.y2(), p.z2());
      if (p.type() == JinwooFx.MOTION) {
         JinwooMotion.start(a, p.arg(), b.x > 0.5);
         return;
      }
      JinwooVfx.onEvent(p.type(), p.entityId(), p.arg(), a, b, p.ids());
   }
}
