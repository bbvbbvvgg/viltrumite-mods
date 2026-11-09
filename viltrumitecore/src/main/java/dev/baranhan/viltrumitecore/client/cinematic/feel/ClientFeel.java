package dev.baranhan.viltrumitecore.client.cinematic.feel;

import dev.baranhan.viltrumitecore.combat.feel.ShakeProfile;
import dev.baranhan.viltrumitecore.network.packet.FeelS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client side of {@link FeelS2CPacket}: routes a cue to the camera, impact frame or ring. */
public final class ClientFeel {
   private ClientFeel() {
   }

   public static void handle(FeelS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      switch (packet.kind) {
         case FeelS2CPacket.SHAKE -> CameraFeel.shakeAt(packet.at, ShakeProfile.byId(packet.profile), packet.power, packet.range);
         case FeelS2CPacket.IMPACT -> {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            if (packet.range <= 0.0F || eye.distanceTo(packet.at) <= packet.range) {
               ImpactFrame.trigger(packet.at, packet.power);
            }
         }
         case FeelS2CPacket.WAVE -> ShockwaveDistortion.spawn(packet.at, packet.power, packet.duration);
         default -> {
         }
      }
   }
}
