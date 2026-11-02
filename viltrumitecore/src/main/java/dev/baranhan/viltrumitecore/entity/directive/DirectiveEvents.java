package dev.baranhan.viltrumitecore.entity.directive;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Forge hooks of the behaviour-control layer (server side only). */
@EventBusSubscriber(modid = "viltrumitecore")
public final class DirectiveEvents {
   /** Above everything a vanilla mob registers (0 is the float goal; directives share it). */
   public static final int PRIORITY = 0;

   private DirectiveEvents() {
   }

   static void ensureGoal(Mob mob) {
      for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
         if (wrapped.getGoal() instanceof DirectiveGoal) {
            return;
         }
      }
      mob.goalSelector.addGoal(PRIORITY, new DirectiveGoal(mob));
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
         return;
      }
      if (living.getPersistentData().contains(MobDirectives.NBT_KEY)) {
         MobDirectives.loadFromSave(living);
         if (living instanceof Mob mob && MobDirectives.any(mob)) {
            ensureGoal(mob);
         }
      }
   }

   @SubscribeEvent
   public static void onTick(LivingEvent.LivingTickEvent event) {
      LivingEntity e = event.getEntity();
      if (!e.level().isClientSide && MobDirectives.cached(e)) {
         MobDirectives.tick(e);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onTarget(LivingChangeTargetEvent event) {
      LivingEntity e = event.getEntity();
      if (!e.level().isClientSide && event.getNewTarget() != null && MobDirectives.cached(e) && MobDirectives.forbidsTarget(e, event.getNewTarget())) {
         event.setNewTarget(null);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttack(LivingAttackEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide) {
         return;
      }
      Entity source = event.getSource().getEntity();
      if (source instanceof LivingEntity attacker && attacker != victim && MobDirectives.cached(attacker)
         && MobDirectives.forbidsDamage(attacker, victim, event.getSource())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onHurt(LivingHurtEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || event.isCanceled() || !MobDirectives.cached(victim)) {
         return;
      }
      List<Directive> list = MobDirectives.active(victim);
      Entity source = event.getSource().getEntity();
      float after = (victim.getHealth() - event.getAmount()) / Math.max(1.0F, victim.getMaxHealth());
      List<Directive> kept = DirectiveRules.afterHurt(list, source == null ? null : source.getUUID(), event.getAmount(), after,
         MobDirectives.HEAVY_HIT, MobDirectives.LOW_HEALTH);
      if (kept != list) {
         MobDirectives.store(victim, kept);
      }
   }

   @SubscribeEvent
   public static void onKnockback(LivingKnockBackEvent event) {
      LivingEntity e = event.getEntity();
      if (!e.level().isClientSide && MobDirectives.cached(e) && MobDirectives.has(e, DirectiveEffect.RIPPED)) {
         event.setStrength(event.getStrength() * MobDirectives.RIPPED_KNOCKBACK);
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity e = event.getEntity();
      if (!e.level().isClientSide && !event.isCanceled()) {
         MobDirectives.clearAll(e);
      }
   }
}
