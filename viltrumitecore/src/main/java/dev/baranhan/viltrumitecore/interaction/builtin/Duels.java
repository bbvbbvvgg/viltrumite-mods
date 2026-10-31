package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.InteractionRule;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/** Shared checks of player-vs-player interactions (tag rules of {@code hero.combat}, never hero ids). */
final class Duels {
   private Duels() {
   }

   /** The struck player when the hit is a hand-to-hand hit between two rival players (or a friendly contact if allowed). */
   @Nullable
   static ServerPlayer meleeDefender(InteractionContext ctx, boolean allowFriendly) {
      ServerPlayer a = ctx.attackerPlayer();
      ServerPlayer d = ctx.targetPlayer();
      if (a == null || d == null || a == d || !ctx.melee() || !a.isAlive() || !d.isAlive()) {
         return null;
      }
      if (ctx.friendly()) {
         return allowFriendly ? d : null;
      }
      return InteractionTargets.rivals(a, d) ? d : null;
   }

   /** Directional rule match: the attacker matches the rule's first side, the defender its second. */
   static boolean directed(InteractionRule rule, CombatPresence attacker, CombatPresence defender) {
      return rule.first().matches(attacker) && rule.second().matches(defender);
   }

   /** The defender's hero says its raised guard faces the attacker. */
   static boolean guardFaces(ServerPlayer defender, ServerPlayer attacker) {
      return HeroRegistry.get(defender).guardFaces(defender, attacker.position());
   }
}
