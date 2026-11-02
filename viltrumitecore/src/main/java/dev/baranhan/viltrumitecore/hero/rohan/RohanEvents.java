package dev.baranhan.viltrumitecore.hero.rohan;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Server events of Rohan: book sessions, Faster than a Punch, the chronicle of deeds. */
@EventBusSubscriber(modid = "viltrumitecore")
public final class RohanEvents {
   private RohanEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         RohanBooks.tick(event.getServer());
      }
   }

   /** Faster than a Punch: a melee hit on Rohan inside the stance never lands. */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttack(LivingAttackEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer rohan) || event.isCanceled()) {
         return;
      }
      RohanState state = RohanState.of(rohan);
      Entity source = event.getSource().getEntity();
      if (state != null && state.counterTicks > 0 && source instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker
         && RohanSkills.counter(rohan, state, attacker)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDamage(LivingDamageEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0F) {
         return;
      }
      if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim && victim instanceof Player) {
         RohanChronicle.record(attacker.getUUID(), "book.viltrumitecore.line.hurt_player", victim.getName().getString());
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || event.isCanceled()) {
         return;
      }
      LivingEntity killer = event.getSource().getEntity() instanceof LivingEntity l ? l : victim.getKillCredit();
      if (killer != null && killer != victim) {
         RohanChronicle.record(killer.getUUID(), "book.viltrumitecore.line.killed",
            victim instanceof Player ? victim.getName().getString() : "@" + victim.getType().getDescriptionId());
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      RohanBooks.clearAll();
      RohanChronicle.clear();
   }
}
