package dev.baranhan.viltrumitecore.hero.ghostrider;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Server events of the Ghost Rider: the hellfire burn tick and the deeds journal of the Penance Stare. */
@EventBusSubscriber(modid = "viltrumitecore")
public final class GhostRiderEvents {
   private GhostRiderEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         Hellfire.tick();
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDamage(LivingDamageEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0F) {
         return;
      }
      Entity source = event.getSource().getEntity();
      if (source instanceof LivingEntity attacker) {
         PenanceLedger ledger = PenanceLedger.get(victim.getServer());
         if (ledger != null) {
            ledger.recordHarm(attacker, victim, event.getAmount());
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || event.isCanceled()) {
         return;
      }
      PenanceLedger ledger = PenanceLedger.get(victim.getServer());
      if (ledger != null) {
         LivingEntity killer = event.getSource().getEntity() instanceof LivingEntity l ? l : victim.getKillCredit();
         ledger.recordDeath(killer, victim);
      }
      Hellfire.extinguish(victim);
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      Hellfire.clear();
   }
}
