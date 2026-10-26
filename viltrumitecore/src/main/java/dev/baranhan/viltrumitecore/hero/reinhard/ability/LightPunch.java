package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.DestructionLevel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeHits;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeImpact;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Ordinary LMB fists outside Knight: right jab → left jab → right hook (MeleeMoves.FIST),
 * a press during the second half of a strike is buffered, a pause restarts the chain.
 * The first body of a strike takes the vanilla held-item attack (enchantments, attack
 * strength); the hook's second body takes the move damage. No environmental destruction.
 */
public final class LightPunch implements AbilityHandler {
   public static final DestructionLevel DESTRUCTION = DestructionLevel.NONE;

   public static float damage() {
      return ReinhardRules.LIGHT_PUNCH_DAMAGE;
   }

   /** Pure: a jab lands on this elapsed tick of the timeline. */
   public static boolean hitsAt(int elapsed) {
      return elapsed == ReinhardRules.LIGHT_PUNCH_HIT_TICK;
   }

   /** Pure: the press starts the next strike of the chain; returns its move index. */
   public static int press(ReinhardState state) {
      int move = MeleeMoves.next(state.meleeAbility == ReinhardAbility.LIGHT_PUNCH ? state.meleeStep : -1, state.meleeIdle, MeleeMoves.FIST.length);
      state.meleeAbility = ReinhardAbility.LIGHT_PUNCH;
      state.meleeStep = move;
      state.meleeIdle = 0;
      state.meleeQueued = false;
      state.meleeHit.clear();
      state.action.start(ReinhardAbility.LIGHT_PUNCH, MeleeMoves.fist(move).ticks(), -1, move);
      return move;
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      press(state);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (MeleeChain.tick(state, ReinhardAbility.LIGHT_PUNCH)) {
         press(state);
      }

      ActionTimeline action = state.action;
      if (!action.is(ReinhardAbility.LIGHT_PUNCH)) {
         return;
      }

      MeleeMoves.Move move = MeleeMoves.fist(action.phase());
      int tick = action.elapsed();
      if (tick == move.swing().contactStart() - 1) {
         ReinhardSounds.play(player, ReinhardSounds.FIST_SWING.get(), 0.55F, move.swing().side() == 0 ? 1.15F : 0.95F);
      }

      List<MeleeHits.Hit> hits = MeleeHits.sweep(player, move.swing(), tick, state.meleeHit);
      for (int i = 0; i < hits.size(); i++) {
         if (i == 0 && state.meleeHit.size() == hits.size()) {
            // Ordinary melee: vanilla held-item/enchantment/attack-strength behaviour, resolved at the authored contact.
            state.resolvingMelee = true;
            try {
               player.attack(hits.get(i).target());
            } finally {
               state.resolvingMelee = false;
            }
         } else if (Strikes.hit(player, hits.get(i).target(), move.damage())) {
            Strikes.knockback(hits.get(i).target(), MeleeImpact.push(player, move.swing()), move.knockback());
         }
      }

      MeleeImpact.land(player, move, hits, ReinhardSounds.FIST_HIT.get(), 0.9F, move.hitStop() > 0 ? 0.9F : 1.1F);
      if (tick == (move.swing().contactStart() + move.swing().contactEnd()) / 2 && state.meleeHit.isEmpty()) {
         MeleeImpact.glance(player, move.swing(), ReinhardSounds.FIST_HIT.get());
      }
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
      MeleeChain.reset(state);
   }
}
