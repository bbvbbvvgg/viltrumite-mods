package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.wish.WishGrant;
import dev.baranhan.viltrumitecore.hero.wish.WishRitual;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * «я желаю стать несокрушимым»: Tony in the nano suit inside the Hulkbuster, through the existing Iron Man
 * lifecycle and the Mark 48 layer. No second Hulkbuster: no drop, no Veronica, the layer is just ACTIVE.
 */
public final class HulkbusterWish implements WishGrant {
   public static final HulkbusterWish INSTANCE = new HulkbusterWish();

   private HulkbusterWish() {
   }

   @Override
   public boolean satisfied(ServerPlayer player) {
      IronManState state = ironMan(player) ? IronManState.of(player) : null;
      return state != null && state.hulkbuster.present();
   }

   @Override
   @Nullable
   public String refusal(ServerPlayer player) {
      if (!HeroRegistry.get(HeroId.IRON_MAN).admitsEntry(player)) {
         return WishRitual.REFUSED_UNAVAILABLE;
      }
      IronManState state = ironMan(player) ? IronManState.of(player) : null;
      if (state != null && (state.suit.busy() || state.hulkbuster.busy())) {
         return WishRitual.REFUSED_BUSY;
      }
      // The real final box of the big body (the same check as the suit-up).
      return IronManHulkbuster.freeSpot(player) == null ? WishRitual.REFUSED_NO_ROOM : null;
   }

   @Override
   public boolean grant(ServerPlayer player) {
      if (!ironMan(player) && !HeroRegistry.changeHero(player, HeroId.IRON_MAN)) {
         return false;
      }
      if (!ironMan(player)) {
         // A running scene deferred the change: the plain Iron Man comes at its end, no Hulkbuster.
         return true;
      }
      IronManState state = IronManState.ensure(player);
      if (!state.suit.worn()) {
         state.suit.autoNano();
      }
      state.hulkbuster.grantByWish();
      HeroRegistry.syncSnapshot(player);
      return true;
   }

   private static boolean ironMan(ServerPlayer player) {
      return player instanceof HeroPlayer hero && hero.getHeroId() == HeroId.IRON_MAN;
   }
}
