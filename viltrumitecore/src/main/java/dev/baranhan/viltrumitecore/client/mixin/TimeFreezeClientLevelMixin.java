package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client half of stopped time: entities the server froze stop being simulated
 * here too (no prediction drift, no lerp, no walk/idle animation, tickCount
 * holds so GeckoLib/vanilla clocks hold). Old position = position, so the
 * renderer draws them perfectly still. The local player keeps ticking (his
 * input and camera are locked instead, see TimeStopClient/TimeFreezeMouseMixin).
 */
@Mixin(ClientLevel.class)
public abstract class TimeFreezeClientLevelMixin {
   @Shadow
   protected abstract void tickPassenger(Entity vehicle, Entity passenger);

   @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozen(Entity entity, CallbackInfo ci) {
      if (!(entity instanceof LocalPlayer) && TimeFreeze.isFrozen(entity)) {
         ci.cancel();
         entity.setOldPosAndRot();
         for (Entity passenger : entity.getPassengers()) {
            this.tickPassenger(entity, passenger);
         }
      }
   }

   @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozenPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
      if (!(passenger instanceof LocalPlayer) && passenger.getVehicle() == vehicle && TimeFreeze.isFrozen(passenger)) {
         ci.cancel();
         passenger.setOldPosAndRot();
      }
   }
}
