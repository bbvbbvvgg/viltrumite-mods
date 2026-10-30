package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stopped time on the server: a frozen non-player entity skips its whole tick
 * (AI, brain, physics, lifetime, fuse), keeping velocity and timers for the
 * resume. Riders still get their own tick. Players are frozen in
 * {@link TimeFreezePlayerMixin} (ServerPlayer.tick keeps the connection-side work).
 */
@Mixin(ServerLevel.class)
public abstract class TimeFreezeLevelMixin {
   @Shadow
   protected abstract void tickPassenger(Entity vehicle, Entity passenger);

   @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozen(Entity entity, CallbackInfo ci) {
      if (!(entity instanceof Player) && TimeFreeze.isFrozen(entity)) {
         ci.cancel();
         for (Entity passenger : entity.getPassengers()) {
            this.tickPassenger(entity, passenger);
         }
      }
   }

   @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozenPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
      if (!(passenger instanceof Player) && passenger.getVehicle() == vehicle && TimeFreeze.isFrozen(passenger)) {
         ci.cancel();
      }
   }
}
