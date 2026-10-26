package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.HeroKnockback;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions push players regardless of knockback resistance (1.20.1). For a
 * knockback-immune hero the push recorded in {@code hitPlayers} (exactly the vector
 * added to his velocity) is taken back and removed, so the explosion packet carries
 * no knockback either. Damage is untouched.
 */
@Mixin(Explosion.class)
public abstract class ExplosionKnockbackMixin {
   @Shadow
   public abstract Map<Player, Vec3> getHitPlayers();

   @Inject(method = "explode", at = @At("TAIL"))
   private void viltrumitecore$cancelImmunePush(CallbackInfo ci) {
      Iterator<Map.Entry<Player, Vec3>> it = this.getHitPlayers().entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Player, Vec3> hit = it.next();
         Player player = hit.getKey();
         if (HeroKnockback.immune(player)) {
            player.setDeltaMovement(player.getDeltaMovement().subtract(hit.getValue()));
            it.remove();
         }
      }
   }
}
