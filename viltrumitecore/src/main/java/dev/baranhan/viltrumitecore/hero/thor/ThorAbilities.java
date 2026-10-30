package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Thor's panel slots, in key order: R (electric mode toggle), Y (pull to
 * the thrown Mjölnir), the ultimate (slot 3, default Z), V (teleport point,
 * slot 4) and B (Crush, the hammer blow, slot 5; v3 spec §1.1). The mouse buttons are not slots. A slot is lit
 * only while its input can start an authored move (none in the foundation:
 * all are greyed and refuse with an "in development" message). The ids are
 * internal, not approved ability names.
 */
public final class ThorAbilities {
   /** Slot 1 (R): toggle the electric mode. */
   public static final String THUNDER = "thor:thunder";
   /** Slot 2 (Y): pull to Mjölnir flying after a throw. */
   public static final String ONSLAUGHT = "thor:onslaught";
   /** Slot 3 (Z, temporary default): the ultimate. */
   public static final String WRATH = "thor:wrath";
   /** Slot 4 (V): choose the teleport point. */
   public static final String TELEPORT = "thor:teleport";
   /** Slot 5 (B, v3 spec §1.1): the Mjölnir blow (HAMMER_STRIKE), formerly MMB next to an enemy. */
   public static final String CRUSH = "thor:crush";
   private static final String[] OWN = {THUNDER, ONSLAUGHT, WRATH, TELEPORT, CRUSH};

   private ThorAbilities() {
   }

   public static String[] slotIds() {
      return OWN.clone();
   }

   public static boolean owns(String abilityId) {
      return abilityId != null && Arrays.asList(OWN).contains(abilityId);
   }

   @Nullable
   public static ResourceLocation icon(String abilityId) {
      if (!owns(abilityId)) {
         return null;
      }

      return new ResourceLocation("viltrumitecore", "textures/gui/ability/thor/" + abilityId.substring(abilityId.indexOf(':') + 1) + ".png");
   }

   @Nullable
   public static HeroAction actionFor(String abilityId) {
      if (THUNDER.equals(abilityId)) {
         return HeroAction.CONTEXT_POWER;
      } else if (ONSLAUGHT.equals(abilityId)) {
         return HeroAction.CONTEXT_MOBILITY;
      } else if (WRATH.equals(abilityId)) {
         return HeroAction.ULTIMATE;
      } else {
         if (TELEPORT.equals(abilityId)) {
            return HeroAction.CONTEXT_TELEPORT;
         }
         return CRUSH.equals(abilityId) ? HeroAction.SIGNATURE : null;
      }
   }

   public static List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> list = new ArrayList<>();
      for (String id : OWN) {
         String name = "thor_" + id.substring(id.indexOf(':') + 1);
         ThorInput input = ThorInput.of(actionFor(id));
         list.add(new dev.baranhan.viltrumitecore.ability.ViltrumiteAbility(
            id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0, player -> slotGrey(player, input)));
      }

      return list;
   }

   /** Grey while the input has no authored move, while a move runs, or while its slot recharges (ThorCooldowns). */
   public static boolean slotGrey(Player player, @Nullable ThorInput input) {
      if (input == null || !(player instanceof HeroPlayer heroPlayer)) {
         return true;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return slotGrey(snapshot != null && snapshot.heroId() == HeroId.THOR ? snapshot : null, input);
   }

   static boolean slotGrey(@Nullable HeroPublicSnapshot snapshot, ThorInput input) {
      return !ThorControls.anyAuthored(input) || snapshot == null || snapshot.actionBusy() || ThorCooldowns.recharging(snapshot.cooldowns(), input);
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      System.arraycopy(OWN, 0, loadout, 0, OWN.length);
      return loadout;
   }

   /** Former defaults (v2: R, Y, Z, V; no Crush). */
   public static String[][] previousDefaultLoadouts() {
      String[] v2 = new String[18];
      Arrays.fill(v2, "");
      System.arraycopy(new String[]{THUNDER, ONSLAUGHT, WRATH, TELEPORT}, 0, v2, 0, 4);
      return new String[][]{v2};
   }
}
