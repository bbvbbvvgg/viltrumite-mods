package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame.Offset;
import javax.annotation.Nullable;

/**
 * The three staged fatalities (decisions 68, 85, 89): (1) the head crushed with
 * Mjölnir, (2) the chest punched through and the heart torn out, (3) an
 * aerial finisher with a monstrous hammer blow. Each is a shared main part
 * plus a finish, after the contextual intro. All ticks are PLACEHOLDER
 * (storyboards and durations are [ОТКРЫТО]). Positions are in the scene
 * frame: Thor at the origin facing frame yaw, the victim in front of him
 * facing back. Pure and side-neutral.
 */
public enum FatalityScene {
   HEAD_CRUSH(46, 24, 3.2, new int[]{28}, new Beat[]{Beat.HEAD_CRUSH}),
   HEART_RIP(54, 22, 3.2, new int[]{14, 30, 42}, new Beat[]{Beat.CHEST_PUNCH, Beat.HEART_RIP, Beat.HEART_CRUSH}),
   AERIAL(50, 26, 10.5, new int[]{6, 24, 29, 36}, new Beat[]{Beat.UPPERCUT, Beat.AIR_BLOW, Beat.GROUND_SLAM, Beat.THOR_LAND});

   /** A scripted moment the server turns into damage-free effects (blood, sound, shake). */
   public enum Beat {
      HEAD_CRUSH,
      CHEST_PUNCH,
      HEART_RIP,
      HEART_CRUSH,
      UPPERCUT,
      AIR_BLOW,
      GROUND_SLAM,
      THOR_LAND
   }

   /** How the victim's body lies (client presentation; the server only moves the body). */
   public enum Body {
      STAND,
      KNEEL,
      AIRBORNE,
      FACE_DOWN,
      ON_BACK
   }

   /** Distance from Thor to the victim when the main part begins. */
   public static final double ANCHOR = 1.1;
   private static final FatalityScene[] VALUES = values();

   private final int main;
   private final int finish;
   private final double headroom;
   private final int[] beatTicks;
   private final Beat[] beats;

   FatalityScene(int main, int finish, double headroom, int[] beatTicks, Beat[] beats) {
      this.main = main;
      this.finish = finish;
      this.headroom = headroom;
      this.beatTicks = beatTicks;
      this.beats = beats;
   }

   /** 1-based number used by the dev command and the report. */
   public int number() {
      return this.ordinal() + 1;
   }

   @Nullable
   public static FatalityScene byNumber(int number) {
      return number >= 1 && number <= VALUES.length ? VALUES[number - 1] : null;
   }

   @Nullable
   public static FatalityScene byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   public int main() {
      return this.main;
   }

   public int finish() {
      return this.finish;
   }

   /** Free air the scene needs above the floor (blocks). */
   public double headroom() {
      return this.headroom;
   }

   /** How far in front of Thor the scene reaches (blocks). */
   public double reach() {
      return this == AERIAL ? 3.2 : 2.2;
   }

   /** The beat on this main-part tick, or null. */
   @Nullable
   public Beat beatAt(int tick) {
      for (int i = 0; i < this.beatTicks.length; i++) {
         if (this.beatTicks[i] == tick) {
            return this.beats[i];
         }
      }
      return null;
   }

   public int[] beatTicks() {
      return this.beatTicks.clone();
   }

   /** Victim offset {@code t} ticks into the main part (t may be fractional, clamped to the scene). */
   public Offset victim(double t) {
      return switch (this) {
         case HEAD_CRUSH -> {
            double kneel = SceneFrame.smooth(SceneFrame.span(t, 0, 12));
            double fall = SceneFrame.easeIn(SceneFrame.span(t, 28, 38));
            yield new Offset(ANCHOR + 0.15 * fall, -0.55 * kneel - 0.3 * fall, 0.0);
         }
         case HEART_RIP -> {
            double impaled = SceneFrame.easeOut(SceneFrame.span(t, 14, 18));
            double drop = SceneFrame.smooth(SceneFrame.span(t, 30, 38));
            double fall = SceneFrame.easeIn(SceneFrame.span(t, 44, 54));
            yield new Offset(ANCHOR + 0.15 * impaled + 0.35 * fall, 0.15 * impaled - 0.7 * drop - 0.2 * fall, 0.0);
         }
         case AERIAL -> {
            double rise = SceneFrame.easeOut(SceneFrame.span(t, 6, 20));
            double hang = SceneFrame.span(t, 20, 24);
            double smash = SceneFrame.easeIn(SceneFrame.span(t, 24, 29));
            double up = (7.0 * rise + 0.3 * hang) * (1.0 - smash);
            double forward = ANCHOR + 0.5 * rise + 1.0 * smash;
            yield new Offset(forward, up, 0.0);
         }
      };
   }

   /** Thor offset {@code t} ticks into the main part (only the aerial scene leaves the ground). */
   public Offset thor(double t) {
      if (this != AERIAL) {
         return Offset.ZERO;
      }

      double leap = SceneFrame.easeOut(SceneFrame.span(t, 12, 24));
      double fall = SceneFrame.easeIn(SceneFrame.span(t, 26, 36));
      return new Offset(0.9 * leap + 0.3 * fall, 6.2 * leap * (1.0 - fall), 0.0);
   }

   public Body body(double t) {
      return switch (this) {
         case HEAD_CRUSH -> t >= 30 ? Body.FACE_DOWN : t >= 2 ? Body.KNEEL : Body.STAND;
         case HEART_RIP -> t >= 46 ? Body.ON_BACK : t >= 32 ? Body.KNEEL : Body.STAND;
         case AERIAL -> t >= 29 ? Body.ON_BACK : t >= 6 ? Body.AIRBORNE : Body.STAND;
      };
   }

   /** 0..1 how far the body has gone down to the ground (render rotation), t into the main part. */
   public double lying(double t) {
      return switch (this) {
         case HEAD_CRUSH -> SceneFrame.easeIn(SceneFrame.span(t, 28, 38));
         case HEART_RIP -> SceneFrame.easeIn(SceneFrame.span(t, 44, 54));
         case AERIAL -> SceneFrame.smooth(SceneFrame.span(t, 27, 31));
      };
   }
}
