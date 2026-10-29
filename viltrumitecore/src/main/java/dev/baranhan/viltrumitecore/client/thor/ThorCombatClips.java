package dev.baranhan.viltrumitecore.client.thor;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatTimings;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Session B's third-person clips: one per strike pose (ThorStrike, the
 * snapshot resource) and one default per move (pose 0), all keyed to the
 * server phases of the strike's ThorAttacks row (wind-up at the end of the intro, the
 * hit at the end of the main phase, recovery to the move's length).
 * PLACEHOLDER art: angles are not approved animations and were not checked
 * in a running client.
 */
public final class ThorCombatClips {
   /** Electric poses (per move): 1 = right hand / default, 2 = left hand. */
   public static final int RIGHT_HAND = 1;
   public static final int LEFT_HAND = 2;

   private static final float[] HOLD = {ThorClips.HOLD_PITCH, 0.0F, 0.0F};
   private static final float[] GUARD = {-40.0F, 0.0F, 0.0F};
   private static final float[] REST = {0.0F, 0.0F, 0.0F};

   /** Wind-up / hit angles per strike: right arm, off arm (null = stays), body (pitch, yaw, roll). */
   private record Swing(float[] windRight, float[] hitRight, @Nullable float[] windOff, @Nullable float[] hitOff, float[] windBody, float[] hitBody) {
   }

   private static final Map<ThorStrike, Swing> SWINGS = new EnumMap<>(ThorStrike.class);
   private static boolean registered;

   static {
      right(ThorStrike.SWING_RIGHT, a(-100, 50, 0), a(-70, -60, 0), a(0, 20, 0), a(0, -25, 0));
      right(ThorStrike.SWING_BACKHAND, a(-90, -60, 0), a(-80, 60, 0), a(0, -20, 0), a(0, 25, 0));
      right(ThorStrike.OVERHEAD, a(-190, 0, 0), a(-40, 0, 0), a(-5, 0, 0), a(15, 0, 0));
      right(ThorStrike.SPIN, a(-90, 70, 0), a(-90, -90, 0), a(0, 40, 0), a(0, -320, 0));
      right(ThorStrike.UPPERCUT, a(-10, 0, 0), a(-170, 0, 0), a(10, 0, 0), a(-10, 0, 0));
      right(ThorStrike.RISING, a(-20, 30, 0), a(-150, -20, 0), a(10, 10, 0), a(-5, -10, 0));
      right(ThorStrike.GROUND_SLAM, a(-200, 0, 0), a(-30, 0, 0), a(-10, 0, 0), a(35, 0, 0));
      right(ThorStrike.HEAVY_DOWN, a(-170, 40, 20), a(-50, -40, 0), a(0, 25, 0), a(10, -20, 0));
      right(ThorStrike.HEAVY_REVERSE, a(-150, -50, -20), a(-60, 50, 0), a(0, -25, 0), a(10, 20, 0));
      right(ThorStrike.HEAVY_SPIN, a(-100, 80, 0), a(-90, -100, 0), a(5, 50, 0), a(5, -330, 0));
      right(ThorStrike.POMMEL, a(-40, 0, 0), a(-95, 0, 0), a(0, 10, 0), a(10, -10, 0));
      right(ThorStrike.DOUBLE_SWING, a(-110, 70, 0), a(-80, -80, 0), a(0, 30, 0), a(5, -40, 0));
      right(ThorStrike.THRUST, a(-30, 0, 0), a(-90, 0, 0), a(-10, 15, 0), a(20, -5, 0));
      right(ThorStrike.LUNGE, a(-120, 40, 0), a(-60, -40, 0), a(25, 15, 0), a(25, -15, 0));
      right(ThorStrike.LUNGE_FINISH, a(-180, 0, 0), a(-40, 0, 0), a(5, 0, 0), a(30, 0, 0));
      right(ThorStrike.FALLING_SLAM, a(-200, 0, 0), a(-20, 0, 0), a(-15, 0, 0), a(40, 0, 0));
      right(ThorStrike.LOW_SWEEP, a(-50, 80, 0), a(-40, -90, 0), a(30, 30, 0), a(30, -40, 0));
      right(ThorStrike.SWEEP_LAUNCH, a(-20, 0, 0), a(-175, 0, 0), a(25, 0, 0), a(-10, 0, 0));
      fists(ThorStrike.JAB, null, null, a(-50, 0, 0), a(-90, 0, 0), a(0, 10, 0), a(0, -10, 0));
      fists(ThorStrike.CROSS, a(-50, 0, 0), a(-90, -10, 0), null, null, a(0, 15, 0), a(0, -20, 0));
      fists(ThorStrike.HOOK, null, null, a(-70, -40, 0), a(-90, 50, 0), a(0, -15, 0), a(0, 25, 0));
      fists(ThorStrike.FIST_UPPERCUT, a(-10, 0, 0), a(-160, 0, 0), null, null, a(10, 10, 0), a(-10, -5, 0));
      fists(ThorStrike.HAYMAKER, a(-80, 70, 0), a(-90, -60, 0), null, null, a(0, 35, 0), a(5, -35, 0));
      fists(ThorStrike.HAMMER_FIST, a(-170, 0, 0), a(-60, 0, 0), null, null, a(-5, 0, 0), a(15, 0, 0));
      fists(ThorStrike.HAMMER_FIST_LEFT, null, null, a(-170, 0, 0), a(-60, 0, 0), a(-5, 0, 0), a(15, 0, 0));
      fists(ThorStrike.GROUND_POUND, a(-190, 0, 0), a(-20, 0, 0), a(-190, 0, 0), a(-20, 0, 0), a(-10, 0, 0), a(40, 0, 0));
      fists(ThorStrike.BODY_BLOW, a(-30, 0, 0), a(-70, -10, 0), null, null, a(5, 10, 0), a(15, -10, 0));
      fists(ThorStrike.ELBOW, null, null, a(-90, -30, 0), a(-90, 60, 0), a(0, -20, 0), a(0, 30, 0));
      fists(ThorStrike.PUSH_KICK, a(20, 0, 0), a(30, 0, 0), a(20, 0, 0), a(30, 0, 0), a(0, 0, 0), a(-15, 0, 0));
      fists(ThorStrike.TACKLE, a(-60, 0, 0), a(-70, 0, 0), a(-60, 0, 0), a(-70, 0, 0), a(20, 0, 0), a(30, 0, 0));
      fists(ThorStrike.TACKLE_HOOK, a(-80, 70, 0), a(-90, -60, 0), null, null, a(10, 35, 0), a(10, -35, 0));
      right(ThorStrike.MMB_BLOW, a(-170, 30, 0), a(-50, -20, 0), a(0, 15, 0), a(15, -15, 0));
      right(ThorStrike.MMB_FINISHER, a(-200, 0, 0), a(-20, 0, 0), a(-10, 0, 0), a(35, 0, 0));
   }

   private ThorCombatClips() {
   }

   private static float[] a(float pitch, float yaw, float roll) {
      return new float[]{pitch, yaw, roll};
   }

   private static void right(ThorStrike strike, float[] wind, float[] hit, float[] windBody, float[] hitBody) {
      SWINGS.put(strike, new Swing(wind, hit, null, null, windBody, hitBody));
   }

   private static void fists(ThorStrike strike, @Nullable float[] windRight, @Nullable float[] hitRight, @Nullable float[] windOff, @Nullable float[] hitOff,
      float[] windBody, float[] hitBody) {
      SWINGS.put(strike, new Swing(windRight == null ? GUARD : windRight, hitRight == null ? GUARD : hitRight, windOff == null ? GUARD : windOff,
         hitOff == null ? GUARD : hitOff, windBody, hitBody));
   }

   /** Idempotent (client init). */
   public static synchronized void registerAll() {
      if (registered) {
         return;
      }

      registered = true;
      for (ThorStrike strike : ThorStrike.values()) {
         ThorClips.registerPose(strike.move(), strike.pose(), clip(strike));
      }

      ThorClips.register(ThorMove.HAMMER_COMBO, clip(ThorStrike.SWING_RIGHT), clip(ThorStrike.SWING_BACKHAND));
      ThorClips.register(ThorMove.FIST_COMBO, clip(ThorStrike.JAB), clip(ThorStrike.CROSS));
      ThorClips.register(ThorMove.HAMMER_STRIKE, clip(ThorStrike.MMB_BLOW));
      ThorClips.register(ThorMove.ELECTRIC_MODE, mode());
      ThorClips.Clip right = discharge(true);
      ThorClips.Clip left = discharge(false);
      ThorClips.register(ThorMove.ELECTRIC_COMBO, right, left);
      ThorClips.registerPose(ThorMove.ELECTRIC_COMBO, RIGHT_HAND, right);
      ThorClips.registerPose(ThorMove.ELECTRIC_COMBO, LEFT_HAND, left);
      ThorClips.register(ThorMove.ELECTRIC_WAVE, wave());
      ThorClips.register(ThorMove.ELECTRIC_STORM, storm());
      ThorClips.register(ThorMove.WRATH, wrath());
   }

   /**
    * Rest → wind-up peak → contact at the end of the intro (the tick the
    * server lands the damage, ENTER_MAIN; review B-m1) → follow-through held
    * through main → rest at the end of the move.
    */
   static ThorClips.Clip clip(ThorStrike strike) {
      ThorMoveDefinition d = ThorCombatTimings.of(ThorAttacks.of(strike));
      Swing swing = SWINGS.get(strike);
      boolean fist = strike.move() == ThorMove.FIST_COMBO;
      float[] rest = fist ? GUARD : HOLD;
      float[][] rightArm = rows(d, rest, swing.windRight(), swing.hitRight());
      float[][] offArm = swing.windOff() == null ? null : rows(d, fist ? GUARD : REST, swing.windOff(), swing.hitOff());
      float[][] body = rows(d, REST, swing.windBody(), swing.hitBody());
      return new ThorClips.Clip(rightArm, offArm, body);
   }

   private static float[][] rows(ThorMoveDefinition d, float[] rest, float[] wind, float[] hit) {
      int hitAt = contactTick(d);
      float windAt = windTick(d);
      int followAt = d.intro() + d.main();
      if (followAt <= hitAt) {
         return new float[][]{
            key(0, rest[0], rest[1], rest[2]), key(windAt, wind[0], wind[1], wind[2]), key(hitAt, hit[0], hit[1], hit[2]), key(d.length(), rest[0], rest[1], rest[2])
         };
      }

      return new float[][]{
         key(0, rest[0], rest[1], rest[2]), key(windAt, wind[0], wind[1], wind[2]), key(hitAt, hit[0], hit[1], hit[2]), key(followAt, hit[0], hit[1], hit[2]),
         key(d.length(), rest[0], rest[1], rest[2])
      };
   }

   /** The clip's contact key: the server's damage tick (ENTER_MAIN = the end of the intro). */
   static int contactTick(ThorMoveDefinition d) {
      return d.intro();
   }

   /** Wind-up peak two thirds into the intro, strictly between rest and contact. */
   static float windTick(ThorMoveDefinition d) {
      return Math.max(0.5F, Math.min(d.intro() - 0.5F, d.intro() * 2.0F / 3.0F));
   }

   private static ThorClips.Clip mode() {
      int end = ThorCombatTimings.of(ThorMove.ELECTRIC_MODE).length();
      return new ThorClips.Clip(new float[][]{key(0, HOLD[0], 0, 0), key(end / 2.0F, -150, 0, 10), key(end, HOLD[0], 0, 0)}, null, null);
   }

   private static ThorClips.Clip discharge(boolean right) {
      ThorMoveDefinition d = ThorCombatTimings.of(ThorMove.ELECTRIC_COMBO);
      float[][] pointing = rows(d, REST, a(-70, 10, 0), a(-95, 0, 0));
      float[][] still = rows(d, REST, REST, REST);
      float[][] body = rows(d, REST, a(0, right ? 15 : -15, 0), a(0, right ? -10 : 10, 0));
      return right ? new ThorClips.Clip(pointing, still, body) : new ThorClips.Clip(still, pointing, body);
   }

   private static ThorClips.Clip wave() {
      ThorMoveDefinition d = ThorCombatTimings.of(ThorMove.ELECTRIC_WAVE);
      return new ThorClips.Clip(rows(d, REST, a(-40, 0, 30), a(-20, 0, 80)), rows(d, REST, a(-40, 0, -30), a(-20, 0, -80)),
         rows(d, REST, a(-10, 0, 0), a(10, 0, 0)));
   }

   private static ThorClips.Clip storm() {
      ThorMoveDefinition d = ThorCombatTimings.of(ThorMove.ELECTRIC_STORM);
      int top = d.intro() + d.main();
      float[][] rightArm = {key(0, -150, 0, 20), key(top, -165, 0, 30), key(d.length(), 0, 0, 0)};
      float[][] offArm = {key(0, -150, 0, -20), key(top, -165, 0, -30), key(d.length(), 0, 0, 0)};
      float[][] body = {key(0, -8, 0, 0), key(top, -12, 0, 0), key(d.length(), 0, 0, 0)};
      return new ThorClips.Clip(rightArm, offArm, body);
   }

   private static ThorClips.Clip wrath() {
      ThorMoveDefinition d = ThorCombatTimings.of(ThorMove.WRATH);
      return new ThorClips.Clip(rows(d, HOLD, a(-185, 0, 0), a(-80, 0, 0)), rows(d, REST, a(-40, 0, -20), a(-20, 0, -10)),
         rows(d, REST, a(-12, 0, 0), a(25, 0, 0)));
   }

   /**
    * Pure: the open right arm raised along the view while Mjölnir flies back
    * (#154; look up and the arm goes higher). Integrated: only the fallback
    * when this client does not track the hammer entity; ThorPoser aims at the
    * real hammer through session A's MjolnirClient otherwise.
    */
   public static float[][] reach(float viewPitch) {
      float pitch = Math.max(-170.0F, Math.min(-30.0F, -90.0F + viewPitch));
      return new float[][]{key(0, pitch, 0, -6.0F)};
   }
}
