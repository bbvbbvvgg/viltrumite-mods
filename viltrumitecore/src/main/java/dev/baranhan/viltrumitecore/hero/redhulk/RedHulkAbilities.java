package dev.baranhan.viltrumitecore.hero.redhulk;

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

/** The Red Hulk's loadout and panel: one page, six own slots. */
public final class RedHulkAbilities {
   private RedHulkAbilities() {
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      RedHulkAbility[] all = RedHulkAbility.values();
      for (int i = 0; i < all.length; i++) {
         loadout[i] = all[i].id();
      }
      return loadout;
   }

   public static boolean owns(@Nullable String id) {
      return RedHulkAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(RedHulkAbility.values()).map(RedHulkAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      RedHulkAbility a = RedHulkAbility.byId(id);
      return a == null ? null : a.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      RedHulkAbility a = RedHulkAbility.byId(id);
      return a == null ? null : iconOf(a);
   }

   public static ResourceLocation iconOf(RedHulkAbility a) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/red_hulk/" + a.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (RedHulkAbility a : RedHulkAbility.values()) {
         String name = "red_hulk_" + a.key();
         list.add(new ViltrumiteAbility(a.id(), iconOf(a), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, a)));
      }
      return list;
   }

   public static boolean slotGrey(Player player, RedHulkAbility a) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot s = heroPlayer.getHeroSnapshot();
      if (s == null || s.heroId() != HeroId.RED_HULK) {
         return false;
      }
      return slotGrey(a, s.extraCooldown(a.cooldown), s.heroFlags());
   }

   /** Pure: the slot is grey on cooldown, and the heat abilities also while the heat is short or overheated. */
   public static boolean slotGrey(RedHulkAbility a, int cooldown, int flags) {
      if (cooldown > 0) {
         return true;
      }
      return switch (a) {
         case BLAST -> !RedHulkFlags.has(flags, RedHulkFlags.BLAST_READY);
         case OVERHEAT -> !RedHulkFlags.has(flags, RedHulkFlags.ULT_READY);
         case FURY -> RedHulkFlags.has(flags, RedHulkFlags.OVERHEAT) || RedHulkFlags.has(flags, RedHulkFlags.FURY);
         default -> false;
      };
   }
}
