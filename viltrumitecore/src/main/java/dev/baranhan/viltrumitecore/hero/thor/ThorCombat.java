package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Thor's combat presence for the shared interaction rules
 * ({@link dev.baranhan.viltrumitecore.hero.combat.HeroInteractions}): Mjölnir
 * in the free hand, or bare fists; the stance is ACTIVE inside the effective
 * part (MAIN) of a strike, NONE otherwise. What a CLASH / BLOCK does to the
 * hit is not approved yet (OPEN DESIGN, foundation m8): only the rule
 * lookup is wired.
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

   /** Presence with the running move's stance. */
   public static CombatPresence presence(HammerState hammer, boolean handFree, @Nullable ThorMove move, @Nullable MovePhase phase) {
      CombatPresence base = presence(hammer, handFree);
      CombatStance stance = stance(move, phase);
      return stance == base.stance() ? base : base.withStance(stance);
   }

   /** ACTIVE only in the MAIN phase of a striking move (the server's damage window). */
   public static CombatStance stance(@Nullable ThorMove move, @Nullable MovePhase phase) {
      if (move == null || phase != MovePhase.MAIN) {
         return CombatStance.NONE;
      }

      return switch (move) {
         case HAMMER_COMBO, FIST_COMBO, HAMMER_STRIKE, ELECTRIC_COMBO, WRATH -> CombatStance.ACTIVE;
         default -> CombatStance.NONE;
      };
   }
}
