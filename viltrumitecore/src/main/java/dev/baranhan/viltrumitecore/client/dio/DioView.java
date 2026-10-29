package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Read-only client view of a DIO public snapshot (no server state on the client). */
public final class DioView {
   private static final HeroAction[] ACTIONS = HeroAction.values();

   private DioView() {
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.DIO) {
            return snapshot;
         }
      }

      return null;
   }

   @Nullable
   public static HeroAction action(HeroPublicSnapshot snapshot) {
      int id = snapshot.actionId();
      return id >= 0 && id < ACTIONS.length ? ACTIONS[id] : null;
   }

   public static boolean is(HeroPublicSnapshot snapshot, HeroAction action) {
      return snapshot.actionId() == action.ordinal();
   }

   public static int standPhase(HeroPublicSnapshot snapshot) {
      return snapshot.extraCooldown(DioRules.EX_STAND_PHASE);
   }

   public static int standElapsed(HeroPublicSnapshot snapshot) {
      return snapshot.extraCooldown(DioRules.EX_STAND_ELAPSED);
   }

   /** The stand is drawn at all: out, coming out, or fading away. */
   public static boolean standVisible(HeroPublicSnapshot snapshot) {
      int phase = standPhase(snapshot);
      return phase != DioRules.STAND_NONE || is(snapshot, HeroAction.ROAD_ROLLER) || is(snapshot, HeroAction.TIME_STOP);
   }

   public static float blood(HeroPublicSnapshot snapshot) {
      return snapshot.resource() / 10.0F;
   }
}
