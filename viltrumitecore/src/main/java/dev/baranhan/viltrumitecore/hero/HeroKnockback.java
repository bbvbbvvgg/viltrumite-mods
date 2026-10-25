package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Full knockback immunity for heroes that ask for it ({@link HeroDefinition#knockbackImmune}).
 * Every vanilla way a hit moves a player is closed, while the hero's own motion stays free:
 * <ul>
 * <li>{@code LivingEntity.knockback} (melee, mobs, players, arrows, sweeps): the Forge event is
 * cancelled here (on top of the chassis attribute, which other modifiers could lower);</li>
 * <li>explosions (1.20.1 ignores the attribute): {@code ExplosionKnockbackMixin} takes the push
 * back and drops the player from the explosion packet's knockback;</li>
 * <li>the hurt velocity resync ({@code markHurt}): {@code EntityHurtSyncMixin} skips it, so a hit
 * never overwrites the client's running/jumping momentum with the server's stale copy;</li>
 * <li>hero pushes: the hero denies {@code ControlKind.IMPULSE} in allowsExternalControl.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroKnockback {
   private HeroKnockback() {
   }

   /** Both sides: is this entity a knockback-immune hero right now. */
   public static boolean immune(Entity entity) {
      return entity instanceof Player player && HeroRegistry.get(player).knockbackImmune(player);
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onKnockback(LivingKnockBackEvent event) {
      if (immune(event.getEntity())) {
         event.setCanceled(true);
      }
   }
}
