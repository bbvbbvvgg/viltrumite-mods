package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Forge bus hooks of the Iron Legion: the one ally gate every suit hit passes
 * (melee, projectiles, beams, splash), kill credit for Tony and the server
 * stop.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class LegionEvents {
   private LegionEvents() {
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttack(LivingAttackEvent event) {
      LivingEntity target = event.getEntity();
      if (target.level().isClientSide) {
         return;
      }

      LegionSuitEntity suit = LegionAllies.suitOf(event.getSource().getEntity(), event.getSource().getDirectEntity());
      if (suit == null || target == suit) {
         return;
      }

      if (!LegionAllies.mayHit(suit, target)) {
         event.setCanceled(true);
         return;
      }

      // Tony gets the kill (drops, XP); the victim still answers the suit (its last attacker).
      ServerPlayer owner = suit.owner();
      if (owner != null && target != owner) {
         target.setLastHurtByPlayer(owner);
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      IronManLegion.clear();
   }
}
