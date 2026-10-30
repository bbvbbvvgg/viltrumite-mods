package dev.baranhan.viltrumitecore.hero.homelander;

import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Focus (spec §6.4): a toggle. Every 10 ticks the nearest living entities are
 * picked (FocusTargets) and frightened; the target ids go to the owner only
 * (HeroOwnerSnapshot) for the outlines, labels and routes.
 */
final class Focus {
   private Focus() {
   }

   static void toggle(ServerPlayer player, HomelanderState state, boolean allowed) {
      if (state.focusOn) {
         stop(player, state);
      } else if (!allowed) {
         HomelanderFeedback.refuse(player);
      } else {
         state.focusOn = true;
         state.focusRefresh = 0;
         dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.ability(player, HomelanderAbilities.FOCUS);
      }
   }

   static void stop(ServerPlayer player, HomelanderState state) {
      state.focusOn = false;
      // Outstanding fear lingers only FEAR_LINGER_TICKS after the focus ends.
      for (int id : state.focusTargets) {
         if (player.level().getEntity(id) instanceof LivingEntity target) {
            shortenFear(target);
         }
      }

      state.focusTargets.clear();
      HeroRegistry.pushOwnerSnapshot(player, HeroOwnerSnapshot.EMPTY);
   }

   static void tick(ServerPlayer player, HomelanderState state) {
      if (!state.focusOn) {
         return;
      }

      // Every tick: frightened mobs drop their target so chase/attack goals cannot override the flight.
      for (int id : state.focusTargets) {
         if (player.level().getEntity(id) instanceof net.minecraft.world.entity.Mob mob && mob.hasEffect(ViltrumiteEffects.FEAR.get())) {
            mob.setTarget(null);
            mob.setAggressive(false);
            if (mob instanceof PathfinderMob pathfinder && pathfinder.getNavigation().isDone()) {
               flee(player, pathfinder);
            }
         }
      }

      if (state.focusRefresh-- > 0) {
         return;
      }

      state.focusRefresh = HomelanderRules.FOCUS_REFRESH_TICKS - 1;
      double drop = HomelanderRules.FOCUS_DROP_RADIUS;
      List<FocusTargets.Candidate> candidates = new ArrayList<>();
      for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(drop), e -> e.isAlive() && !e.isSpectator() && !dev.baranhan.viltrumitecore.entity.Bystander.is(e)
         && !(e instanceof net.minecraft.world.entity.player.Player other && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(other).hiddenFromFocus(other)))) {
         candidates.add(new FocusTargets.Candidate(living.getId(), living.distanceTo(player), excluded(player, living)));
      }

      List<Integer> next = FocusTargets.refresh(state.focusTargets, candidates, HomelanderRules.FOCUS_MAX_TARGETS);
      state.focusTargets.clear();
      state.focusTargets.addAll(next);
      int[] ids = new int[next.size()];
      for (int i = 0; i < ids.length; i++) {
         ids[i] = next.get(i);
         if (player.level().getEntity(ids[i]) instanceof LivingEntity target) {
            frighten(player, target);
         }
      }

      HeroRegistry.pushOwnerSnapshot(player, new HeroOwnerSnapshot(ids));
   }

   static boolean excluded(ServerPlayer player, Entity entity) {
      if (entity == player || entity instanceof ArmorStand) {
         return true;
      }

      return entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID());
   }

   static boolean fearImmune(LivingEntity target) {
      return target instanceof WitherBoss || target instanceof EnderDragon;
   }

   private static void frighten(ServerPlayer player, LivingEntity target) {
      if (fearImmune(target)) {
         return;
      }

      int duration = HomelanderRules.FEAR_DURATION;
      target.addEffect(new MobEffectInstance(ViltrumiteEffects.FEAR.get(), duration, 0, false, false, true), player);
      target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, false, false, true), player);
      if (target instanceof PathfinderMob mob) {
         flee(player, mob);
      }
   }

   private static void flee(ServerPlayer player, PathfinderMob mob) {
      Vec3 away = DefaultRandomPos.getPosAway(mob, (int)HomelanderRules.FEAR_FLEE_DISTANCE, 7, player.position());
      if (away != null) {
         mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
      }
   }

   /** Cut the focus fear (and its Slowness I) down to the linger time; stronger slowness (roar) stays. */
   private static void shortenFear(LivingEntity target) {
      int linger = HomelanderRules.FEAR_LINGER_TICKS;
      MobEffectInstance fear = target.getEffect(ViltrumiteEffects.FEAR.get());
      if (fear != null && fear.getDuration() > linger) {
         target.removeEffect(ViltrumiteEffects.FEAR.get());
         target.addEffect(new MobEffectInstance(ViltrumiteEffects.FEAR.get(), linger, 0, false, false, true));
      }

      MobEffectInstance slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
      if (slow != null && slow.getAmplifier() == 0 && slow.getDuration() > linger && slow.getDuration() <= HomelanderRules.FEAR_DURATION) {
         target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
         target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, linger, 0, false, false, true));
      }
   }
}
