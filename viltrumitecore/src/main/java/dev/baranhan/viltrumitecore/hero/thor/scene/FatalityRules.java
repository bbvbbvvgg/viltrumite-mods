package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import javax.annotation.Nullable;

/**
 * Who gets a fatality and which one (decisions 53, 66, 85-88, 93): only a
 * LETHAL_HIT, only with Mjölnir in the hand at the hit (a thrown or
 * returning hammer never starts one), only on players / heroes, never twice
 * for one victim; the scene is random with no direct repeat and does not
 * depend on the kind of hit. Pure.
 */
public final class FatalityRules {
   public enum Verdict {
      START,
      NOT_LETHAL,
      HAMMER_NOT_IN_HAND,
      NOT_A_PLAYER,
      TARGET_GONE,
      ALREADY_IN_SCENE,
      THOR_BUSY
   }

   private FatalityRules() {
   }

   public static Verdict decide(ThorEventType type, boolean hammerInHand, boolean targetIsPlayer, boolean targetAlive, boolean targetInScene,
      boolean thorInScene) {
      if (type != ThorEventType.LETHAL_HIT) {
         return Verdict.NOT_LETHAL;
      } else if (!hammerInHand) {
         return Verdict.HAMMER_NOT_IN_HAND;
      } else if (!targetIsPlayer) {
         return Verdict.NOT_A_PLAYER;
      } else if (!targetAlive) {
         return Verdict.TARGET_GONE;
      } else if (targetInScene) {
         return Verdict.ALREADY_IN_SCENE;
      } else if (thorInScene) {
         return Verdict.THOR_BUSY;
      }

      return Verdict.START;
   }

   /** Random scene, never the previous one of this Thor ({@code previous} null = any). {@code roll} is any int. */
   public static FatalityScene pick(@Nullable FatalityScene previous, int roll) {
      FatalityScene[] all = FatalityScene.values();
      if (previous == null) {
         return all[Math.floorMod(roll, all.length)];
      }

      FatalityScene[] rest = new FatalityScene[all.length - 1];
      int n = 0;
      for (FatalityScene scene : all) {
         if (scene != previous) {
            rest[n++] = scene;
         }
      }
      return rest[Math.floorMod(roll, rest.length)];
   }
}
