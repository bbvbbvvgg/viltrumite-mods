package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.HeroKnockback;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code markHurt} makes the server resend the victim's velocity after a hit. A player's
 * server copy is stale (the client moves him), so for a knockback-immune hero that packet
 * is a pure stop: it would freeze his run or jump on every hit. Skipped for him only; the
 * hurt flash and damage events are separate packets.
 */
@Mixin(Entity.class)
public abstract class EntityHurtSyncMixin {
   @Inject(method = "markHurt", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$keepImmuneMomentum(CallbackInfo ci) {
      if (HeroKnockback.immune((Entity)(Object)this)) {
         ci.cancel();
      }
   }
}
