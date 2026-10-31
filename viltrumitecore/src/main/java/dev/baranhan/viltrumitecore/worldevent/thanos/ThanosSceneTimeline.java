package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * The snap and universe scene, the victory scene and the arrival (pure: ticks since the beat
 * start, shared by the server timeline and every client curve).
 */
public final class ThanosSceneTimeline {
   // ---- snap -> universe (ticks after the timer ran out) --------------------------------------
   public static final int RAISE_END = 40;
   public static final int STONES_START = 44;
   public static final int STONE_STEP = 6;
   public static final int STONES_END = 80;
   public static final int FACE_END = 110;
   public static final int FOLD_END = 148;
   public static final int SNAP = 150;
   public static final int DISSOLVE_START = 165;
   public static final int DISSOLVE_END = 325;
   public static final int VOID_END = 365;
   public static final int ARRIVAL_END = 405;
   public static final int LINES = 6;
   public static final int LINE_TICKS = 100;
   public static final int SPEECH_START = ARRIVAL_END;
   public static final int SPEECH_END = SPEECH_START + LINES * LINE_TICKS;
   /** Thanos is removed when the restore starts: the world comes back without him. */
   public static final int THANOS_GONE = SPEECH_END;
   public static final int RESTORE_END = SPEECH_END + 120;
   public static final int LENGTH = RESTORE_END + 20;
   /** World pause length requested for the scene (its watchdog adds 40 ticks). */
   public static final int PAUSE_TICKS = LENGTH + 10;

   // ---- victory ---------------------------------------------------------------------------------
   public static final int VICTORY_STAGGER_END = 30;
   public static final int VICTORY_KNEEL_END = 60;
   public static final int VICTORY_DIM_STEP = 10;
   public static final int VICTORY_CRUMBLE_START = 120;
   public static final int VICTORY_LENGTH = 200;

   // ---- arrival ---------------------------------------------------------------------------------
   public static final int ARRIVAL_PORTAL_END = 20;
   public static final int ARRIVAL_STEP_END = 60;

   public enum Stage {
      RAISE,
      STONES,
      FACE,
      FOLD,
      SNAP,
      DISSOLVE,
      VOID,
      ARRIVAL,
      SPEECH,
      RESTORE,
      SETTLE,
      DONE
   }

   private ThanosSceneTimeline() {
   }

   public static Stage stage(float t) {
      if (t < RAISE_END) {
         return Stage.RAISE;
      } else if (t < STONES_END) {
         return Stage.STONES;
      } else if (t < FACE_END) {
         return Stage.FACE;
      } else if (t < SNAP) {
         return Stage.FOLD;
      } else if (t < DISSOLVE_START) {
         return Stage.SNAP;
      } else if (t < DISSOLVE_END) {
         return Stage.DISSOLVE;
      } else if (t < VOID_END) {
         return Stage.VOID;
      } else if (t < ARRIVAL_END) {
         return Stage.ARRIVAL;
      } else if (t < SPEECH_END) {
         return Stage.SPEECH;
      } else if (t < RESTORE_END) {
         return Stage.RESTORE;
      } else {
         return t < LENGTH ? Stage.SETTLE : Stage.DONE;
      }
   }

   /** Stones lit on the gauntlet during the snap (socket order), 0..6. */
   public static int stonesLit(float t) {
      if (t < STONES_START) {
         return 0;
      }

      return Math.min(ThanosStone.values().length, 1 + (int)((t - STONES_START) / STONE_STEP));
   }

   /**
    * Snap hand pose: 0 open, 1 thumb and middle finger pressed together (the slow fold), 2 the
    * middle finger struck down onto the palm (the snap itself, from {@link #SNAP}).
    */
   public static float snapHand(float t) {
      if (t < FACE_END) {
         return 0.0F;
      }

      if (t < FOLD_END) {
         return smooth((t - FACE_END) / (FOLD_END - FACE_END));
      }

      if (t < SNAP) {
         return 1.0F;
      }

      return 1.0F + Math.min(1.0F, (t - SNAP) / 1.5F);
   }

   /** Universe dissolve progress 0..1 (eased), then held until the restore. */
   public static float dissolve(float t) {
      if (t < DISSOLVE_START) {
         return 0.0F;
      }

      if (t < SPEECH_END) {
         return ease(Math.min(1.0F, (t - DISSOLVE_START) / (DISSOLVE_END - DISSOLVE_START)));
      }

      return 1.0F - restore(t);
   }

   /** World light: full until the dissolve, dying to near nothing as it spreads, back with the restore. */
   public static float light(float t) {
      if (t < DISSOLVE_START) {
         return 1.0F;
      }

      if (t < SPEECH_END) {
         return 1.0F - 0.95F * smooth((t - DISSOLVE_START) / (DISSOLVE_END - 20 - DISSOLVE_START));
      }

      return 0.05F + 0.95F * restore(t);
   }

   /** Od Lagna's presence 0..1: forms after the void, fades into the golden light of the restore. */
   public static float odLagna(float t) {
      if (t < VOID_END) {
         return 0.0F;
      }

      if (t < ARRIVAL_END) {
         return smooth((t - VOID_END) / (ARRIVAL_END - VOID_END));
      }

      if (t < SPEECH_END + 20) {
         return 1.0F;
      }

      return 1.0F - smooth((t - SPEECH_END - 20) / 50.0F);
   }

   /** Index of the line spoken at t (0..5), or -1. */
   public static int line(float t) {
      if (t < SPEECH_START || t >= SPEECH_END) {
         return -1;
      }

      return (int)((t - SPEECH_START) / LINE_TICKS);
   }

   /** Ticks into the current line. */
   public static float lineTime(float t) {
      int line = line(t);
      return line < 0 ? 0.0F : t - SPEECH_START - line * LINE_TICKS;
   }

   /** Share of the line's characters shown: revealed over the first 60 % of the line. */
   public static float lineReveal(float t) {
      int line = line(t);
      if (line < 0) {
         return 0.0F;
      }

      return Math.min(1.0F, lineTime(t) / (LINE_TICKS * 0.6F));
   }

   /** Restore progress 0..1 (eased): the world comes back behind a golden front. */
   public static float restore(float t) {
      if (t < SPEECH_END) {
         return 0.0F;
      }

      return ease(Math.min(1.0F, (t - SPEECH_END) / (RESTORE_END - 20 - SPEECH_END)));
   }

   /** Golden light of the restore 0..1: swells as the world returns, gone at the end. */
   public static float gold(float t) {
      if (t < SPEECH_END - 10 || t >= RESTORE_END) {
         return 0.0F;
      }

      float u = (t - (SPEECH_END - 10)) / (RESTORE_END - (SPEECH_END - 10));
      return (float)Math.sin(Math.PI * u);
   }

   /** Victory: stones still lit (6 at the kneel, then one goes out every 10 ticks). */
   public static int victoryStonesLit(float t) {
      if (t < VICTORY_KNEEL_END) {
         return ThanosStone.values().length;
      }

      return Math.max(0, ThanosStone.values().length - 1 - (int)((t - VICTORY_KNEEL_END) / VICTORY_DIM_STEP));
   }

   /** Victory: crumble progress 0..1 of his body into ash. */
   public static float victoryCrumble(float t) {
      if (t < VICTORY_CRUMBLE_START) {
         return 0.0F;
      }

      return smooth((t - VICTORY_CRUMBLE_START) / (VICTORY_LENGTH - 20 - VICTORY_CRUMBLE_START));
   }

   static float clamp(float v) {
      return Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
   }

   public static float smooth(float v) {
      float t = clamp(v);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float ease(float v) {
      float t = clamp(v);
      return t < 0.5F ? 2.0F * t * t : 1.0F - 2.0F * (1.0F - t) * (1.0F - t);
   }
}
