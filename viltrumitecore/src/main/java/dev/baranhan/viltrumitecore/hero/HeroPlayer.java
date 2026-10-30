package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;

/**
 * Hero seam on the Player. Canonical identity lives in the synced hero id;
 * ViltrumiteCorePlayer.isViltrumite/setViltrumite are compatibility adapters
 * over it, implemented in the mixins.
 */
public interface HeroPlayer {
   HeroId getHeroId();

   HeroSession getHeroSession();

   /**
    * The public synchronized visual snapshot. Never null; an EMPTY snapshot is
    * returned before the first server write or for non-hero players.
    */
   HeroPublicSnapshot getHeroSnapshot();

   // Internal wiring used by HeroRegistry and the network layer. Not part of
   // the public gameplay contract.
   void viltrumitecore$setHeroId(HeroId id);

   void viltrumitecore$setHeroSession(HeroSession session);

   void viltrumitecore$setTotemConsumed(boolean consumed);

   @Nullable
   Object viltrumitecore$getHeroState();

   void viltrumitecore$setHeroState(@Nullable Object state);

   HeroOwnerSnapshot viltrumitecore$getOwnerSnapshot();

   void viltrumitecore$setOwnerSnapshot(HeroOwnerSnapshot snapshot);

   void viltrumitecore$setSyncedSnapshot(HeroPublicSnapshot snapshot);

   /** Whether a synchronized entity-data key affects hero body dimensions. */
   boolean viltrumitecore$isBodyKey(net.minecraft.network.syncher.EntityDataAccessor<?> key);

   /** True while vanilla mayfly was granted by HeroFlightGrant (NBT HeroGrantedMayfly). */
   boolean viltrumitecore$isMayflyGranted();

   void viltrumitecore$setMayflyGranted(boolean granted);

   /** True once after loading a legacy Viltrumite save: its saved slots must be migrated. */
   boolean viltrumitecore$consumeLegacyLoadout();
}
