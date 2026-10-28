package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import java.util.Set;
import net.minecraft.world.entity.player.Player;

/**
 * Combat tags of the Hulkbuster (rework spec §1.3.6). Special encounters are tag rules, never hero pairs:
 * a victim with {@link #TRAIT_SUPERSTRONG} breaks free faster.
 */
public final class HulkbusterTags {
   public static final String BODY_MECH_HEAVY = "body.mech.heavy";
   public static final String WEAPON_FIST_JACKHAMMER = "weapon.fist.jackhammer";
   public static final String TRAIT_ARMORED = "trait.armored";
   public static final String TRAIT_SUPERSTRONG = "trait.superstrong";

   private HulkbusterTags() {
   }

   public static CombatPresence presence(boolean guarding, boolean striking) {
      CombatStance stance = guarding ? CombatStance.GUARD : striking ? CombatStance.ACTIVE : CombatStance.NONE;
      return new CombatPresence("hulkbuster_mk48", Set.of(BODY_MECH_HEAVY, WEAPON_FIST_JACKHAMMER, TRAIT_ARMORED), stance, 3);
   }

   public static boolean superstrong(Player player) {
      return HeroRegistry.get(player).combatPresence(player).has(TRAIT_SUPERSTRONG);
   }
}
