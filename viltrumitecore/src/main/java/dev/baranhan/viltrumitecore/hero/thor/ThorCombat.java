package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import java.util.Set;

/**
 * Thor's combat presence for the shared interaction rules: Mjölnir in the
 * hand, or bare fists. Stance stays NONE until guard / strikes ship (stage 2);
 * a thrown hammer (stage 3) will carry its own presence with stance THROWN.
 */
public final class ThorCombat {
   public static final String MJOLNIR_ID = "thor:mjolnir";
   public static final String FISTS_ID = "thor:fists";
   public static final CombatPresence MJOLNIR = new CombatPresence(MJOLNIR_ID, Set.of(
      CombatTags.WEAPON_HAMMER, CombatTags.ELEMENT_LIGHTNING, CombatTags.TRAIT_DIVINE,
      CombatTags.TRAIT_THROWABLE, CombatTags.TRAIT_RETURNING, CombatTags.TRAIT_UNBREAKABLE), CombatStance.NONE, 0);
   public static final CombatPresence FISTS = new CombatPresence(FISTS_ID, Set.of(CombatTags.BODY_BARE), CombatStance.NONE, 0);

   private ThorCombat() {
   }

   public static CombatPresence presence(HammerState hammer, boolean handFree) {
      return hammer == HammerState.IN_HAND && handFree ? MJOLNIR : FISTS;
   }
}
