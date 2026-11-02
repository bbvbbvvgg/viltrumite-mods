package dev.baranhan.viltrumitecore.hero.rohan;

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

/** Rohan's loadout and panel: one page, six own slots. */
public final class RohanAbilities {
   private RohanAbilities() {
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      RohanAbility[] all = RohanAbility.values();
      for (int i = 0; i < all.length; i++) {
         loadout[i] = all[i].id();
      }
      return loadout;
   }

   public static boolean owns(@Nullable String id) {
      return RohanAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(RohanAbility.values()).map(RohanAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      RohanAbility a = RohanAbility.byId(id);
      return a == null ? null : a.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      RohanAbility a = RohanAbility.byId(id);
      return a == null ? null : iconOf(a);
   }

   public static ResourceLocation iconOf(RohanAbility a) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/rohan/" + a.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (RohanAbility a : RohanAbility.values()) {
         String name = "rohan_" + a.key();
         list.add(new ViltrumiteAbility(a.id(), iconOf(a), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, a)));
      }
      return list;
   }

   public static boolean slotGrey(Player player, RohanAbility a) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot s = heroPlayer.getHeroSnapshot();
      if (s == null || s.heroId() != HeroId.ROHAN) {
         return false;
      }
      return slotGrey(a, s.extraCooldown(a.cooldown), s.resource(), s.resource2());
   }

   /** Pure: grey on cooldown or without the ink (Inspiration for the Manuscript) to start it. */
   public static boolean slotGrey(RohanAbility a, int cooldown, int ink, int inspiration) {
      if (cooldown > 0) {
         return true;
      }
      return a.usesInspiration() ? inspiration < a.cost() : ink < a.cost();
   }
}
