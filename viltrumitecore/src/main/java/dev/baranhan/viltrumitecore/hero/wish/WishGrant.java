package dev.baranhan.viltrumitecore.hero.wish;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * How a wish changes the player (server only). Every grant goes through the existing hero lifecycle
 * ({@link HeroRegistry#changeHero}); a grant never builds a second way of giving powers.
 */
public interface WishGrant {
   /** The wish is already true for this player (selecting it again is refused, never a re-entry). */
   boolean satisfied(ServerPlayer player);

   /** Lang key of the reason the wish cannot be granted now, or null. Checked at the start and at the swap. */
   @Nullable
   String refusal(ServerPlayer player);

   /** Apply the wish. False = nothing changed (the ritual aborts and says so). */
   boolean grant(ServerPlayer player);

   /** The plain hero grant: the hero with this stable key through {@link HeroRegistry#changeHero}. */
   static WishGrant hero(String heroKey) {
      return new WishGrant() {
         @Override
         public boolean satisfied(ServerPlayer player) {
            return player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.fromKey(heroKey);
         }

         @Override
         @Nullable
         public String refusal(ServerPlayer player) {
            HeroId id = HeroId.fromKey(heroKey);
            if (id == null) {
               return WishRitual.REFUSED_UNKNOWN;
            }
            // admitsEntry: one-holder heroes (Thor's slot) and any future entry rule of the hero itself.
            return HeroRegistry.get(id).id() != id || !HeroRegistry.get(id).admitsEntry(player) ? WishRitual.REFUSED_UNAVAILABLE : null;
         }

         @Override
         public boolean grant(ServerPlayer player) {
            HeroId id = HeroId.fromKey(heroKey);
            return id != null && (this.satisfied(player) || HeroRegistry.changeHero(player, id));
         }
      };
   }
}
