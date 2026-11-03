package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
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

/** Venom's loadout and panel: one page, six own slots. */
public final class VenomAbilities {
   private VenomAbilities() {
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      VenomAbility[] all = VenomAbility.values();
      for (int i = 0; i < all.length; i++) {
         loadout[i] = all[i].id();
      }
      return loadout;
   }

   public static boolean owns(@Nullable String id) {
      return VenomAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(VenomAbility.values()).map(VenomAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      VenomAbility a = VenomAbility.byId(id);
      return a == null ? null : a.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      VenomAbility a = VenomAbility.byId(id);
      return a == null ? null : iconOf(a);
   }

   public static ResourceLocation iconOf(VenomAbility a) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/venom/" + a.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (VenomAbility a : VenomAbility.values()) {
         String name = "venom_" + a.key();
         list.add(new ViltrumiteAbility(a.id(), iconOf(a), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, a)));
      }
      return list;
   }

   public static boolean slotGrey(Player player, VenomAbility a) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot s = heroPlayer.getHeroSnapshot();
      if (s == null || s.heroId() != HeroId.VENOM) {
         return false;
      }
      return slotGrey(a, s.extraCooldown(a.cooldown), s.heroFlags(), s.resource());
   }

   /** Pure: grey on cooldown; the symbiote's powers are grey for the bare host; rage and the takeover need hunger. */
   public static boolean slotGrey(VenomAbility a, int cooldown, int flags, int hunger) {
      if (cooldown > 0) {
         return true;
      }
      boolean form = VenomFlags.has(flags, VenomFlags.FORM);
      if (!form && !a.human()) {
         return true;
      }
      if (a == VenomAbility.RAGE) {
         return !VenomFlags.has(flags, VenomFlags.RAGE) && hunger < VenomRules.RAGE_MIN;
      }
      if (a == VenomAbility.TAKEOVER) {
         return !VenomFlags.has(flags, VenomFlags.POSSESSING) && (!VenomFlags.has(flags, VenomFlags.RAGE) || hunger < VenomRules.TAKEOVER_COST);
      }
      return false;
   }
}
