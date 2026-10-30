package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player in stopped time does not live through the tick: Player.tick (and with
 * it the hero runtime at its TAIL, Forge PlayerTickEvent users, cooldowns, effects,
 * food, item use) is skipped on the server, so abilities started before the stop
 * resume exactly where they were. ServerPlayer.tick/doTick housekeeping (menus,
 * connection, health sync) still runs.
 */
@Mixin(value = Player.class, priority = 900)
public abstract class TimeFreezePlayerMixin {
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozen(CallbackInfo ci) {
      Player self = (Player)(Object)this;
      if (self instanceof ServerPlayer && TimeFreeze.isFrozen(self)) {
         ci.cancel();
      }
   }
}
