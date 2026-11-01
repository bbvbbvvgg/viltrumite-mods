package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Client effects of the Thanos war (kinds 29..58 of {@link ThanosFxS2CPacket}). */
final class ThanosWarFx {
   private ThanosWarFx() {
   }

   static void accept(ThanosFxS2CPacket p, ClientLevel level, Vec3 at, Vec3 dir) {
   }

   static void tick(ClientLevel level) {
   }

   static void draw(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, float partial, boolean glow) {
   }

   static boolean idle() {
      return true;
   }

   static void clear() {
   }
}
