package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import javax.annotation.Nullable;

/**
 * Heaven's Door clips (one animation file each, {@code animations/rohan/heavens_door_<id>.animation.json}, made by
 * tools/assets/make_rohan_assets.py) and the pure choice of clip + clip time from Rohan's synced move clock.
 */
public final class HeavensDoorClips {
   /** Ticks of the dismiss clip once the stand is no longer out. */
   public static final int DISMISS_TICKS = 10;

   public enum Clip {
      IDLE("idle", 40, true),
      APPEAR("appear", RohanRules.DOOR_SUMMON, false),
      TOUCH("touch", 10, false),
      WRITE("write", 12, true),
      GUARD("guard", RohanRules.COUNTER_WINDOW, false),
      SELF("self", RohanRules.SELF_EDIT_WRITE + RohanRules.SELF_EDIT_FLY, false),
      PRESENT("present", RohanRules.MANUSCRIPT_SHOW, false),
      DISMISS("dismiss", DISMISS_TICKS, false);

      public final String id;
      public final int length;
      public final boolean loop;

      Clip(String id, int length, boolean loop) {
         this.id = id;
         this.length = length;
         this.loop = loop;
      }

      public String file() {
         return "animations/rohan/heavens_door_" + this.id + ".animation.json";
      }

      public double wrap(double ticks) {
         if (this.loop) {
            double m = ticks % this.length;
            return m < 0.0 ? m + this.length : m;
         }
         return Math.max(0.0, Math.min(this.length, ticks));
      }
   }

   /** Where the stand stands for a clip. */
   public enum Place {
      SHOULDER,
      LUNGE,
      AT_BOOK,
      GUARD,
      CHEEK,
      PRESENT
   }

   public record Choice(Clip clip, double ticks, Place place) {
   }

   private HeavensDoorClips() {
   }

   /**
    * @param move        Rohan's current move or null
    * @param t           ticks into the move (with partial tick)
    * @param reading     a book is open (the stand waits beside it)
    * @param sinceShown  ticks since the stand became visible (appear clip outside the Heaven's Door move)
    * @param clientTicks free-running clock for the loops
    */
   public static Choice choose(@Nullable RohanMove move, double t, boolean reading, double sinceShown, double clientTicks) {
      if (move != null) {
         switch (move) {
            case DOOR -> {
               if (t < RohanRules.DOOR_SUMMON) {
                  return new Choice(Clip.APPEAR, Clip.APPEAR.wrap(t), Place.SHOULDER);
               }
               return new Choice(Clip.TOUCH, Clip.TOUCH.wrap(t - RohanRules.DOOR_SUMMON), Place.LUNGE);
            }
            case COUNTER -> {
               return new Choice(Clip.GUARD, Clip.GUARD.wrap(t), Place.GUARD);
            }
            case WRITE -> {
               return new Choice(Clip.WRITE, Clip.WRITE.wrap(clientTicks), reading ? Place.AT_BOOK : Place.SHOULDER);
            }
            case RIP -> {
               return new Choice(Clip.TOUCH, Clip.TOUCH.wrap(t), reading ? Place.AT_BOOK : Place.SHOULDER);
            }
            case SELF_EDIT -> {
               return new Choice(Clip.SELF, Clip.SELF.wrap(t), Place.CHEEK);
            }
            case MANUSCRIPT -> {
               return new Choice(Clip.PRESENT, Clip.PRESENT.wrap(t), Place.PRESENT);
            }
            default -> {
            }
         }
      }
      if (sinceShown < Clip.APPEAR.length) {
         return new Choice(Clip.APPEAR, Clip.APPEAR.wrap(sinceShown), reading ? Place.AT_BOOK : Place.SHOULDER);
      }
      return new Choice(Clip.IDLE, Clip.IDLE.wrap(clientTicks), reading ? Place.AT_BOOK : Place.SHOULDER);
   }

   /** Opacity while appearing (paper unfolding, three-step stutter) and dismissing (pure). */
   public static float appearAlpha(double ticks) {
      double u = Math.max(0.0, Math.min(1.0, ticks / Clip.APPEAR.length));
      double stepped = Math.floor(u * 6.0) / 6.0;
      return (float)(0.25 + 0.75 * (u * 0.6 + stepped * 0.4));
   }

   public static float dismissAlpha(double ticks) {
      double u = Math.max(0.0, Math.min(1.0, ticks / DISMISS_TICKS));
      return (float)(1.0 - u * u);
   }
}
