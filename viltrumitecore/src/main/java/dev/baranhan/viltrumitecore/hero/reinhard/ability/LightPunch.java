package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.DestructionLevel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Light Punch (spec §6.1, Restrained slot 1): a right jab. The ray of
 * {@link ReinhardRules#LIGHT_PUNCH_REACH} from the eyes is cast on tick
 * {@link ReinhardRules#LIGHT_PUNCH_HIT_TICK}; 4 damage and a 0.6 push along the
 * look. No cooldown: the 8-tick action lock. Never breaks blocks.
 */
public final class LightPunch implements AbilityHandler {
   public static final DestructionLevel DESTRUCTION = DestructionLevel.NONE;

   public static float damage() {
      return ReinhardRules.LIGHT_PUNCH_DAMAGE;
   }

   /** Pure: the hit lands on this elapsed tick of the timeline. */
   public static boolean hitsAt(int elapsed) {
      return elapsed == ReinhardRules.LIGHT_PUNCH_HIT_TICK;
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      state.action.start(ReinhardAbility.LIGHT_PUNCH, ReinhardRules.LIGHT_PUNCH_TICKS, -1);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (state.action.is(ReinhardAbility.LIGHT_PUNCH) && hitsAt(state.action.elapsed())) {
         hit(player);
      }
   }

   private static void hit(ServerPlayer player) {
      LivingEntity target = ReinhardTargets.inReach(player, ReinhardRules.LIGHT_PUNCH_REACH);
      if (target == null || !Strikes.hit(player, target, damage())) {
         return;
      }

      Strikes.knockback(target, player.getLookAngle(), ReinhardRules.LIGHT_PUNCH_KNOCKBACK);
      Vec3 at = contact(player, target);
      HeroFx.flash(player, at);
      ReinhardSounds.playAt(player, at, ReinhardSounds.LIGHT_PUNCH.get(), 0.9F, 1.15F);
   }

   /** The point of the target's box nearest to the eyes along the look. */
   static Vec3 contact(ServerPlayer player, LivingEntity target) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(ReinhardRules.LIGHT_PUNCH_REACH + 1.0));
      return target.getBoundingBox().clip(eye, end).orElse(target.getBoundingBox().getCenter());
   }
}
