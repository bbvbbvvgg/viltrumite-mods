package dev.baranhan.viltrumitecore.hero.combat;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;

/**
 * The one shared interaction rule set of the mod (foundation m8): every hero
 * session resolves through this instance instead of building its own
 * {@link InteractionRules#defaults()}, so the rules never diverge. Sessions
 * add tag rules with {@link #register}; a pair of heroes is never named.
 * Server main thread.
 */
public final class HeroInteractions {
   private static InteractionRules rules = InteractionRules.defaults();

   private HeroInteractions() {
   }

   public static synchronized InteractionRules rules() {
      return rules;
   }

   public static synchronized void register(InteractionRule rule) {
      rules.register(rule);
   }

   /** Pure lookup on the shared rules. */
   public static synchronized Optional<InteractionRules.Resolution> resolve(CombatPresence a, CombatPresence b) {
      return rules.resolve(a, b);
   }

   /** What the two players' current presences resolve to (each hero publishes its own presence). */
   public static Optional<InteractionRules.Resolution> resolve(Player a, Player b) {
      return resolve(HeroRegistry.get(a).combatPresence(a), HeroRegistry.get(b).combatPresence(b));
   }

   /** Tests only. */
   public static synchronized void resetForTests() {
      rules = InteractionRules.defaults();
   }
}
