package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.hero.TimeFreezeView;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stopped-time camera lock: while the local player is frozen the mouse cannot
 * turn him, and the motion collected meanwhile is dropped so nothing snaps on
 * release. The same lock holds the caster's mouse while his Road Roller cinema
 * owns the view. Priority below the default so it runs before the target-lock and
 * scene mouse hooks.
 */
@Mixin(value = MouseHandler.class, priority = 500)
public abstract class TimeFreezeMouseMixin {
   @Shadow
   private double accumulatedDX;
   @Shadow
   private double accumulatedDY;

   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$frozenView(CallbackInfo ci) {
      if (TimeFreezeView.locked() || dev.baranhan.viltrumitecore.client.dio.RollerCinema.active()) {
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         ci.cancel();
      }
   }
}
