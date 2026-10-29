package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import javax.annotation.Nullable;

/**
 * Pure clip choice for The World: which clip plays at which clip tick for a
 * given public snapshot state. Strike clips run on the server action clock, so
 * a clip's contact key lands exactly on the server hit tick
 * (TheWorldClipsTest checks the keys against DioRules).
 */
public final class TheWorldClips {
   public enum Clip {
      IDLE("idle", 60, true),
      READY("ready", 40, true),
      SUMMON("summon", 14, false),
      DISMISS("dismiss", 10, false),
      JAB("jab", 9, false),
      CROSS("cross", 9, false),
      HOOK("hook", 14, false),
      BARRAGE("barrage", 4, true),
      FINISHER("finisher", 12, false),
      GUARD("guard", 20, true),
      TIME_STOP("time_stop", 20, false),
      ROLLER_BARRAGE("roller_barrage", 4, true);

      public final String id;
      public final int ticks;
      public final boolean loop;

      Clip(String id, int ticks, boolean loop) {
         this.id = id;
         this.ticks = ticks;
         this.loop = loop;
      }

      public String file() {
         return "animations/dio/the_world_" + this.id + ".animation.json";
      }

      /** Clip time in ticks, wrapped for loops and clamped for one-shots. */
      public double wrap(double t) {
         if (this.loop) {
            double m = t % this.ticks;
            return m < 0 ? m + this.ticks : m;
         }

         return Math.max(0.0, Math.min(this.ticks, t));
      }

      /** Strikes and barrages: the stand steps in front of DIO. */
      public boolean attack() {
         return this == JAB || this == CROSS || this == HOOK || this == BARRAGE || this == FINISHER || this == ROLLER_BARRAGE;
      }
   }

   /** Contact ticks inside each strike clip; the server hit tick of the same action. */
   public static int contactTick(Clip clip) {
      return switch (clip) {
         case JAB -> DioRules.COMBO_CONTACT[0];
         case CROSS -> DioRules.COMBO_CONTACT[1];
         case HOOK -> DioRules.COMBO_CONTACT[2];
         case FINISHER -> DioRules.FINISHER_CONTACT;
         case BARRAGE, ROLLER_BARRAGE -> 0;
         default -> -1;
      };
   }

   /** Ticks of the blend into a clip: strikes snap in so the contact pose is pure. */
   public static float blendIn(Clip clip) {
      return switch (clip) {
         case JAB, CROSS -> 2.0F;
         case HOOK, FINISHER -> 2.5F;
         case BARRAGE, ROLLER_BARRAGE -> 3.0F;
         case SUMMON, DISMISS -> 1.0F;
         case GUARD -> 3.0F;
         case TIME_STOP -> 4.0F;
         default -> 6.0F;
      };
   }

   /** Choice result: the clip and the time inside it, in ticks. */
   public record Choice(Clip clip, double ticks) {
   }

   private TheWorldClips() {
   }

   /**
    * @param action     current DIO action or null
    * @param t          action ticks (server elapsed + render prediction)
    * @param standPhase DioRules.STAND_*
    * @param standT     ticks inside the stand phase
    * @param comboStep  0 jab, 1 cross, 2 hook
    * @param finisher   FLAG_FINISHER
    * @param guard      FLAG_GUARD
    * @param idleT      free-running ticks for the loops
    * @param combat     a strike ended recently (ready stance instead of idle)
    */
   public static Choice choose(@Nullable HeroAction action, double t, int standPhase, double standT, int comboStep, boolean finisher, boolean guard,
      double idleT, boolean combat) {
      if (action == HeroAction.ROAD_ROLLER) {
         if (t > DioRules.ROLLER_SLAM && t <= DioRules.ROLLER_BARRAGE_END) {
            return new Choice(Clip.ROLLER_BARRAGE, Clip.ROLLER_BARRAGE.wrap(t - DioRules.ROLLER_SLAM - DioRules.ROLLER_PULSE));
         }

         if (t > DioRules.ROLLER_BARRAGE_END && t <= DioRules.ROLLER_RESUME + 4) {
            // The last blow lands on the resume tick (contact 5 → 96 + 7 = 103 wind, 108 blast).
            return new Choice(Clip.FINISHER, Clip.FINISHER.wrap(t - (DioRules.ROLLER_RESUME - DioRules.FINISHER_CONTACT)));
         }

         return new Choice(Clip.READY, Clip.READY.wrap(idleT));
      }

      if (standPhase == DioRules.STAND_SUMMONING) {
         return new Choice(Clip.SUMMON, Clip.SUMMON.wrap(standT));
      }

      if (standPhase == DioRules.STAND_DISMISSING) {
         return new Choice(Clip.DISMISS, Clip.DISMISS.wrap(standT));
      }

      if (action == HeroAction.PRIMARY_ATTACK) {
         Clip clip = comboStep <= 0 ? Clip.JAB : comboStep == 1 ? Clip.CROSS : Clip.HOOK;
         return new Choice(clip, clip.wrap(t));
      }

      if (action == HeroAction.MUDA_BARRAGE) {
         if (finisher) {
            return new Choice(Clip.FINISHER, Clip.FINISHER.wrap(t));
         }

         if (t < DioRules.BARRAGE_WINDUP) {
            // Wind-up: the ready guard tightens while the blend runs into the loop.
            return new Choice(Clip.READY, Clip.READY.wrap(idleT));
         }

         return new Choice(Clip.BARRAGE, Clip.BARRAGE.wrap(t - DioRules.BARRAGE_WINDUP));
      }

      if (action == HeroAction.TIME_STOP) {
         return new Choice(Clip.TIME_STOP, Clip.TIME_STOP.wrap(t));
      }

      if (guard) {
         return new Choice(Clip.GUARD, Clip.GUARD.wrap(idleT));
      }

      return combat ? new Choice(Clip.READY, Clip.READY.wrap(idleT)) : new Choice(Clip.IDLE, Clip.IDLE.wrap(idleT));
   }

   /** Barrage loop side at a loop tick: 0 left contact, 2 right contact. */
   public static boolean barrageLeft(double loopTicks) {
      return loopTicks % 4.0 < 2.0;
   }
}
