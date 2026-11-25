package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.combat.feel.CombatFeel;
import dev.baranhan.viltrumitecore.combat.feel.HitWeight;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sonic ram (spec §7.4): while SONIC every body in this tick's swept box takes
 * damage + knockback, each at most once per {@link IronManRules#RAM_REHIT_TICKS}.
 * No extra block destruction.
 */
public final class SonicRam {
   private SonicRam() {
   }

   /** Pure rehit gate; records the hit when due. */
   static boolean due(Map<UUID, Long> lastHit, UUID target, long now) {
      Long last = lastHit.get(target);
      if (last != null && now - last < IronManRules.RAM_REHIT_TICKS) {
         return false;
      }

      lastHit.put(target, now);
      return true;
   }

   public static void tick(ServerPlayer player, IronManState state) {
      Vec3 velocity = FlyBy.velocityOf(player);
      if (velocity.lengthSqr() < 1.0E-4) {
         return;
      }

      AABB swept = player.getBoundingBox().expandTowards(velocity).inflate(0.5);
      long now = player.level().getGameTime();
      Vec3 push = velocity.normalize().scale(IronManRules.RAM_KNOCKBACK).add(0.0, 0.2, 0.0);
      for (Entity entity : player.level().getEntities(player, swept, e -> FlyBy.valid(player, e))) {
         if (!due(state.ramHits, entity.getUUID(), now)) {
            continue;
         }

         if (entity.hurt(player.damageSources().playerAttack(player), IronManRules.RAM_DAMAGE)) {
            CombatFeel.hit(player, entity, HitWeight.forDamage(IronManRules.RAM_DAMAGE));
            FlyBy.push((LivingEntity)entity, push);
            IronManSounds.play(player, dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_FLYBY_HIT.get(), 1.4F, 0.7F);
         } else {
            // i-frames ate the hit: try again next tick instead of waiting the rehit window
            state.ramHits.remove(entity.getUUID());
         }
      }
   }
}
