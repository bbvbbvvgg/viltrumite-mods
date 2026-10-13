package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.client.ViltrumiteCoreClient;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroSuperJump;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.network.packet.HeroControlS2CPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Super jump, client side, for every hero with
 * HeroDefinition.superJumpVelocity > 0: one press of the super-jump key (G)
 * launches instantly. Player movement is client-authoritative, so the launch
 * velocity is applied here; the server (HeroSuperJump) only validates and
 * plays the launch for everyone. The space bar stays a normal vanilla jump.
 */
public final class SuperJumpClient {
   private SuperJumpClient() {
   }

   /** Launch if allowed; true when the server should be told (for FX). */
   public static boolean tryLaunch(LocalPlayer player) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      float velocity = HeroRegistry.get(player).superJumpVelocity(player);
      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (velocity <= 0.0F || !player.onGround() || !canJump(player, snapshot)) {
         return false;
      }

      Vec3 delta = player.getDeltaMovement();
      Vec3 look = player.getLookAngle();
      // A little of the facing goes into the launch, so it can travel.
      player.setDeltaMovement(
         delta.x + look.x * HeroSuperJump.FORWARD,
         velocity,
         delta.z + look.z * HeroSuperJump.FORWARD
      );
      player.hasImpulse = true;
      return true;
   }

   private static boolean canJump(LocalPlayer player, HeroPublicSnapshot snapshot) {
      if (player.isSpectator() || player.getAbilities().flying || player.isInWater() || player.isInLava() || player.isPassenger()) {
         return false;
      }

      return !anchored(player, snapshot);
   }

   /** Held by someone's control (channel target, grab, freeze, stasis): no self-propelled jump. Shared with AirJumpClient. */
   public static boolean anchored(LocalPlayer player, HeroPublicSnapshot snapshot) {
      if (WorldPauseClient.isPaused()) return true;
      if ((snapshot != null && snapshot.controlTargetId() >= 0) || ViltrumiteCoreClient.iAmBeingGrabbedBy != null) {
         return true;
      }

      for (HeroControlS2CPacket.ControlInfo control : ClientHeroData.controls()) {
         if (control.entityId() == player.getId()) {
            int kind = control.kindOrdinal();
            if (kind == ControlKind.FREEZE.ordinal() || kind == ControlKind.STASIS.ordinal()) {
               return true;
            }
         }
      }

      return false;
   }
}
