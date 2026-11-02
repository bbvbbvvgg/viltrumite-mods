package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityFireMixin {
   @Inject(
      method = {"isOnFire"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void preventViltrumiteFireAnimation(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof Player player && (player instanceof ViltrumiteCorePlayer corePlayer && corePlayer.isViltrumite()
         || dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).fireImmune(player))) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"fireImmune"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void makeViltrumiteFireImmune(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof Player player && (player instanceof ViltrumiteCorePlayer corePlayer && corePlayer.isViltrumite()
         || dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).fireImmune(player))) {
         cir.setReturnValue(true);
      }
   }
}
