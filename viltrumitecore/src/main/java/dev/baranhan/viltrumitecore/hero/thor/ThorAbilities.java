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
 * Thor's three panel slots: two context keys and the separate ultimate. The
 * mouse buttons are not slots. A slot is lit only while its input can start a
 * shipped move (none in stage 1: all three are greyed and refuse with an
 * "in development" message).
 */
public final class ThorAbilities {
   /** Slot 1 (R): electric mode / lightning call. */
   public static final String THUNDER = "thor:thunder";
   /** Slot 2 (Y): lightning dash / teleport strike / dive slam. */
   public static final String ONSLAUGHT = "thor:onslaught";
   /** Slot 3 (Z): Wrath of Thor, the ultimate. */
   public static final String WRATH = "thor:wrath";
   private static final String[] OWN = {THUNDER, ONSLAUGHT, WRATH};

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
      } else {
         return WRATH.equals(abilityId) ? HeroAction.ULTIMATE : null;
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

   /** Grey while the input has no shipped move, or while a move runs. */
   public static boolean slotGrey(Player player, @Nullable ThorInput input) {
      if (input == null || !(player instanceof HeroPlayer heroPlayer)) {
         return true;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return slotGrey(snapshot != null && snapshot.heroId() == HeroId.THOR ? snapshot : null, input);
   }

   static boolean slotGrey(@Nullable HeroPublicSnapshot snapshot, ThorInput input) {
      return !ThorControls.anyShipped(input) || snapshot == null || snapshot.actionBusy();
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      System.arraycopy(OWN, 0, loadout, 0, OWN.length);
      return loadout;
   }
}
