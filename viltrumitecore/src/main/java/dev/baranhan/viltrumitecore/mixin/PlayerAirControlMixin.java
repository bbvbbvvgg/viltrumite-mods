package dev.baranhan.viltrumitecore.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Air acceleration of a hero with its own air control ({@code HeroDefinition.airControl}):
 * steering between air steps follows the input smoothly instead of vanilla's 0.02. Never
 * while flying (vanilla or mod flight keeps its own speed). Both sides: the client predicts.
 */
@Mixin(Player.class)
public abstract class PlayerAirControlMixin {
   @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
   private void viltrumitecore$heroAirControl(CallbackInfoReturnable<Float> cir) {
      Player player = (Player)(Object)this;
      if (player.getAbilities().flying || player.isPassenger()) {
         return;
      }
      float control = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).airControl(player);
      if (control > cir.getReturnValueF()) {
         cir.setReturnValue(player.isSprinting() ? control * 1.3F : control);
      }
   }
}
