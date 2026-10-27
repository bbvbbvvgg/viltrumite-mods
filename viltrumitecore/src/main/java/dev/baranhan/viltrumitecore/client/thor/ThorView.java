package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Client reads of Thor's public snapshot (every tracker sees the same view). */
public final class ThorView {
   private ThorView() {
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.THOR) {
            return snapshot;
         }
      }

      return null;
   }

   public static MjolnirPlace place(HeroPublicSnapshot snapshot, Player player) {
      return MjolnirPlace.of(ThorFlags.hammer(snapshot.heroFlags()), ThorHands.free(player));
   }

   /** The running move, or null. */
   @Nullable
   public static ThorMove move(HeroPublicSnapshot snapshot) {
      return ThorMove.byWireId(snapshot.actionId());
   }

   public static MovePhase phase(HeroPublicSnapshot snapshot) {
      return StagedTimeline.phaseOf(snapshot.variant());
   }

   public static int step(HeroPublicSnapshot snapshot) {
      return StagedTimeline.stepOf(snapshot.variant());
   }
}
