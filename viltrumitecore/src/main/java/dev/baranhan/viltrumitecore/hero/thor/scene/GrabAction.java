package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import javax.annotation.Nullable;

/**
 * The three actions while holding (decisions 62, 87, 122, 125): LMB ground slam, RMB
 * far throw, MMB staged fist barrage (one series = one action). One action
 * per grab.
 */
public enum GrabAction {
   SLAM,
   THROW,
   BARRAGE;

   private static final GrabAction[] VALUES = values();

   /** The scene meaning of a mouse input while holding; null for every other input. */
   @Nullable
   public static GrabAction of(ThorInput input) {
      return switch (input) {
         case STRIKE -> SLAM;
         case STANCE -> THROW;
         case HAMMER -> BARRAGE;
         default -> null;
      };
   }

   @Nullable
   public static GrabAction byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   /** Tick (from the commit) at which the victim leaves the hand. */
   public int releaseTick() {
      return switch (this) {
         case SLAM -> ThorSceneConfig.slamImpactTick;
         case THROW -> ThorSceneConfig.throwReleaseTick;
         case BARRAGE -> ThorSceneConfig.barrageHits * ThorSceneConfig.barrageInterval + 6;
      };
   }

   /** Barrage punch index landing on this action tick, or -1 (the finisher is the release tick). */
   public int punchAt(int tick) {
      if (this != BARRAGE || tick <= 0 || tick % ThorSceneConfig.barrageInterval != 0) {
         return -1;
      }

      int index = tick / ThorSceneConfig.barrageInterval - 1;
      return index < ThorSceneConfig.barrageHits ? index : -1;
   }
}
