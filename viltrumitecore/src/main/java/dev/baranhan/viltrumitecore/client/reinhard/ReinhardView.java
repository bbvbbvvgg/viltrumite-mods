package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Reinhard snapshot (mode, Reid bits, action). */
public final class ReinhardView {
   private ReinhardView() {
   }

   /** Reinhard snapshot of this entity, or null. */
   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.REINHARD) {
            return snapshot;
         }
      }

      return null;
   }

   public static ReinhardMode mode(HeroPublicSnapshot snapshot) {
      return ReinhardMode.byOrdinal(ReinhardFlags.mode(snapshot.heroFlags()));
   }

   public static ReinhardPoses.ReidPhase reid(HeroPublicSnapshot snapshot) {
      return ReinhardPoses.reidPhase(snapshot.heroFlags());
   }
}
