package dev.baranhan.viltrumitecore.hero.captain;

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

/** Captain's loadout and panel (spec §5): one page, five own slots. */
public final class CaptainAbilities {
   private CaptainAbilities() {
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      CaptainAbility[] all = CaptainAbility.values();
      for (int i = 0; i < all.length; i++) {
         loadout[i] = all[i].id();
      }
      return loadout;
   }

   public static boolean owns(@Nullable String id) {
      return CaptainAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(CaptainAbility.values()).map(CaptainAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      CaptainAbility a = CaptainAbility.byId(id);
      return a == null ? null : a.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      CaptainAbility a = CaptainAbility.byId(id);
      return a == null ? null : iconOf(a);
   }

   public static ResourceLocation iconOf(CaptainAbility a) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/captain/" + a.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (CaptainAbility a : CaptainAbility.values()) {
         String name = "captain_" + a.key();
         list.add(new ViltrumiteAbility(a.id(), iconOf(a), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, a)));
      }
      return list;
   }

   public static boolean slotGrey(Player player, CaptainAbility a) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot s = heroPlayer.getHeroSnapshot();
      if (s == null || s.heroId() != HeroId.CAPTAIN) {
         return false;
      }
      return slotGrey(a, s.extraCooldown(a.cooldown), s.heroFlags());
   }

   /** Pure: the slot state from snapshot values. Throw stays lit while the shield flies (press = recall). */
   public static boolean slotGrey(CaptainAbility a, int cooldown, int flags) {
      boolean out = CaptainFlags.has(flags, CaptainFlags.SHIELD_OUT);
      return switch (a) {
         case THROW -> !out && cooldown > 0;
         case SLAM -> cooldown > 0 || out;
         case ASSEMBLE -> cooldown > 0 || CaptainFlags.has(flags, CaptainFlags.TEAM_ACTIVE);
         default -> cooldown > 0;
      };
   }
}
