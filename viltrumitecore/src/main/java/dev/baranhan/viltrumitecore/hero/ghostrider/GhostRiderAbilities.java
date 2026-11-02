package dev.baranhan.viltrumitecore.hero.ghostrider;

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

/** The Ghost Rider's loadout and panel: one page, six own slots. */
public final class GhostRiderAbilities {
   private GhostRiderAbilities() {
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      GhostRiderAbility[] all = GhostRiderAbility.values();
      for (int i = 0; i < all.length; i++) {
         loadout[i] = all[i].id();
      }
      return loadout;
   }

   public static boolean owns(@Nullable String id) {
      return GhostRiderAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(GhostRiderAbility.values()).map(GhostRiderAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      GhostRiderAbility a = GhostRiderAbility.byId(id);
      return a == null ? null : a.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      GhostRiderAbility a = GhostRiderAbility.byId(id);
      return a == null ? null : iconOf(a);
   }

   public static ResourceLocation iconOf(GhostRiderAbility a) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/ghost_rider/" + a.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (GhostRiderAbility a : GhostRiderAbility.values()) {
         String name = "ghost_rider_" + a.key();
         list.add(new ViltrumiteAbility(a.id(), iconOf(a), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, a)));
      }
      return list;
   }

   public static boolean slotGrey(Player player, GhostRiderAbility a) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot s = heroPlayer.getHeroSnapshot();
      if (s == null || s.heroId() != HeroId.GHOST_RIDER) {
         return false;
      }
      return slotGrey(a, s.extraCooldown(a.cooldown), s.heroFlags());
   }

   /** Pure: grey on cooldown; the Rider's powers are grey for Johnny; the stare is grey on the bike. */
   public static boolean slotGrey(GhostRiderAbility a, int cooldown, int flags) {
      if (cooldown > 0) {
         return true;
      }
      boolean rider = GhostRiderFlags.has(flags, GhostRiderFlags.RIDER);
      if (!rider && !a.human()) {
         return true;
      }
      return a == GhostRiderAbility.STARE && GhostRiderFlags.has(flags, GhostRiderFlags.RIDING);
   }
}
