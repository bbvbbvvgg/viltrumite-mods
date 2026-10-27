package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shared hero effects for a hammer that may have no caster around (HeroFxS2CPacket to nearby players). */
final class MjolnirFx {
   private MjolnirFx() {
   }

   static void slam(ServerLevel level, Vec3 at, @Nullable BlockState material, float radius) {
      send(level, HeroFxS2CPacket.SLAM, at, 1.0F, material, new float[]{radius, 0.0F, 0.0F});
   }

   static void shockwave(ServerLevel level, Vec3 at, float power, @Nullable BlockState material, float radius) {
      send(level, HeroFxS2CPacket.SHOCKWAVE, at, power, material, new float[]{radius, 0.0F, 0.0F});
   }

   /** The catch: electric flash and a small camera push (§5.2). */
   static void catchFlash(ServerLevel level, Vec3 at) {
      send(level, HeroFxS2CPacket.FLASH, at, 0.35F, null, null);
   }

   private static void send(ServerLevel level, byte kind, Vec3 at, float power, @Nullable BlockState material, @Nullable float[] points) {
      HeroFxS2CPacket packet = new HeroFxS2CPacket(kind, at, power, material == null ? 0 : Block.getId(material), points, -1);
      double range = MjolnirTuning.FX_RANGE * MjolnirTuning.FX_RANGE;
      for (ServerPlayer viewer : level.players()) {
         if (viewer.position().distanceToSqr(at) <= range) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }
}
