package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.Set;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerGamePacketListenerImpl.class})
public class AntiCheatBypassMixin {
   @Shadow
   public ServerPlayer player;

   @Shadow
   private int aboveGroundTickCount;

   /**
    * Unlimited air steps (HeroDefinition.sustainsAirborne) are legitimate movement: the vanilla
    * floating counter (kick after 80 airborne ticks when allow-flight is off) never counts them.
    */
   @Inject(method = "tick", at = @At("HEAD"))
   private void viltrumitecore$airStepsAreNotFlight(CallbackInfo ci) {
      if (this.player != null && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(this.player).sustainsAirborne(this.player)) {
         this.aboveGroundTickCount = 0;
      }
   }

   /**
    * The server keeps {@code flying} only with its own {@code mayfly}, but vanilla never tells the
    * client it refused: a client that believes it may fly (a stale local mayfly) keeps flying.
    * A refused takeoff now resends the server's abilities, which clears the client's flags.
    */
   @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
   private void viltrumitecore$refuseUnauthorisedFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
      if (this.player != null && packet.isFlying() && !this.player.getAbilities().flying) {
         this.player.onUpdateAbilities();
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bypassGrabRubberband(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      if (this.player != null && this.player.server != null && this.player.server.isSameThread()) {
         boolean isBeingGrabbed = dev.baranhan.viltrumitecore.hero.HeroHeldTargets.holder(this.player) != null;

         for (ServerPlayer otherPlayer : this.player.server.getPlayerList().getPlayers()) {
            ViltrumiteCorePlayer coreOther = (ViltrumiteCorePlayer)otherPlayer;
            if (coreOther.getGrabbedTarget() == this.player) {
               isBeingGrabbed = true;
               break;
            }
         }

         if (isBeingGrabbed && packet.hasPosition()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"teleport(DDDFFLjava/util/Set;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bypassFlightRubberband(double x, double y, double z, float yaw, float pitch, Set<?> relativeArguments, CallbackInfo ci) {
      if (this.player != null) {
         ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)(Object)this.player;
         if (corePlayer.isSuperSpeed() || corePlayer.isDashing()) {
            ci.cancel();
         }
      }
   }
}
