package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.scp.scp096.Scp096State;
import java.util.Arrays;

/**
 * Pure layer logic of the SCP-096 renderer (no Minecraft classes, unit-tested): which clip layers a state wants
 * (0..1 targets), how fast each layer fades, and where in its clip each layer is. The renderer eases the weights
 * toward the targets in real time and blends the layers as a weighted average ({@link Scp096Blend}), so every change
 * of state is a crossfade; the one-shot clips start and end on the poses of the loops around them (scp096_anims.py).
 */
public final class Scp096Pose {
   public static final int SIT = 0;
   public static final int PEEK = 1;
   public static final int STAND = 2;
   public static final int COVER = 3;
   public static final int WALK = 4;
   public static final int STAND_UP = 5;
   public static final int SIT_DOWN = 6;
   public static final int GAZE = 7;
   public static final int GAZE_SIT = 8;
   public static final int RISE = 9;
   public static final int HYSTERIA = 10;
   public static final int RUN = 11;
   public static final int RAGE = 12;
   public static final int LEAP = 13;
   public static final int CLIMB = 14;
   public static final int CALM = 15;
   /** Number of full-body layers (indices 0..LAYERS-1). */
   public static final int LAYERS = 16;
   /** Extra eased slots after the layers: the additive sobbing and the share of the head look. */
   public static final int SOB = 16;
   public static final int LOOK = 17;
   public static final int SLOTS = 18;

   /** Clip name per layer, in {@link #FILE} (0 calm, 1 rage, 2 chase). */
   public static final String[] CLIP = {"sit", "sit_peek", "stand", "cover", "walk", "stand_up", "sit_down", "gaze", "gaze_sit", "rise",
      "hysteria", "run", "rage", "leap", "climb", "calm"};
   public static final int[] FILE = {0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 1};
   /** Clip lengths in seconds (as generated); the one-shots are held on their last frame. */
   public static final double[] LENGTH = {4.0, 3.0, 4.0, 3.0, 1.0, 1.6, 1.6, 1.3, 1.3, 1.5, 2.0, 0.6, 1.6, 0.8, 0.9, 6.0};
   public static final boolean[] ONE_SHOT = {false, false, false, false, false, true, true, true, true, true, false, false, false, false, false,
      true};

   /** Ground speeds (blocks per tick) where the shuffle and the run take over from standing and from the crouch. */
   static final float WALK_FROM = 0.006F;
   static final float WALK_FULL = 0.045F;
   static final float RUN_FROM = 0.02F;
   static final float RUN_FULL = 0.13F;
   /** Ticks in the air before a chase counts as a leap or a fall. */
   static final float AIR_TICKS = 3.0F;

   private Scp096Pose() {
   }

   /**
    * Target weights of every slot for this frame.
    *
    * @param since seconds since the state began on this client (large when it was already in it when tracked)
    * @param air   ticks in the air (with the partial tick)
    */
   public static void targets(Scp096State state, Scp096State prev, float since, boolean fromSit, boolean faceOpen, boolean cover, boolean climb,
         boolean leap, float move, float air, float[] out) {
      Arrays.fill(out, 0, SLOTS, 0.0F);
      switch (state) {
         case SIT -> {
            if ((prev == Scp096State.STAND || prev == Scp096State.WANDER) && since < LENGTH[SIT_DOWN]) {
               out[SIT_DOWN] = 1.0F;
            } else {
               out[faceOpen ? PEEK : SIT] = 1.0F;
               out[SOB] = faceOpen ? 0.0F : 1.0F;
               out[LOOK] = faceOpen ? 0.45F : 0.0F;
            }
         }
         case STAND, WANDER -> {
            if (prev == Scp096State.SIT && since < LENGTH[STAND_UP]) {
               out[STAND_UP] = 1.0F;
            } else {
               float walk = walkWeight(move);
               out[cover ? COVER : STAND] = 1.0F - walk;
               out[WALK] = walk;
               out[SOB] = cover ? 1.0F - walk : 0.0F;
               out[LOOK] = cover ? 0.1F : 0.4F;
            }
         }
         case TRIGGERED -> {
            out[fromSit ? GAZE_SIT : GAZE] = 1.0F;
            out[LOOK] = 0.8F;
         }
         case HYSTERIA -> {
            out[fromSit && since < LENGTH[RISE] ? RISE : HYSTERIA] = 1.0F;
            out[LOOK] = 0.2F;
         }
         case CHASE -> {
            if (climb) {
               out[CLIMB] = 1.0F;
            } else if (leap || air > AIR_TICKS) {
               out[LEAP] = 1.0F;
            } else {
               float run = runWeight(move);
               out[RUN] = run;
               out[RAGE] = 1.0F - run;
            }
            out[LOOK] = climb ? 0.0F : 0.55F;
         }
         case CALMING -> {
            out[CALM] = 1.0F;
            out[SOB] = since > 3.3F ? 1.0F : 0.0F;
         }
      }
   }

   /** Easing rate (1/s) of a slot: the startle and the chase snap in, the calm states drift. */
   public static float rate(int slot, Scp096State state) {
      if (slot == GAZE || slot == GAZE_SIT) {
         return 18.0F;
      }
      if (state == Scp096State.TRIGGERED || state == Scp096State.HYSTERIA) {
         return 14.0F;
      }
      if (state == Scp096State.CHASE) {
         return slot == LEAP || slot == CLIMB ? 9.0F : 11.0F;
      }
      if (slot == SOB || slot == LOOK) {
         return 3.0F;
      }
      return slot == PEEK || slot == SIT ? 4.0F : 7.0F;
   }

   /** Eases weights toward the targets over dt seconds; snaps on the first frame or after a long gap. */
   public static void ease(float[] weights, float[] targets, Scp096State state, float dt, boolean snap) {
      for (int i = 0; i < SLOTS; i++) {
         if (snap) {
            weights[i] = targets[i];
         } else {
            float k = 1.0F - (float)Math.exp(-rate(i, state) * dt);
            weights[i] += (targets[i] - weights[i]) * k;
            if (Math.abs(weights[i] - targets[i]) < 1.0E-4F) {
               weights[i] = targets[i];
            }
         }
      }
   }

   /**
    * Clip time (seconds) of a layer: one-shots from the state start (the rise and the hysteria loop chain without a
    * seam), the walk and the run by gait phase, the rest from the entity's age.
    */
   public static double time(int layer, float since, float ageSeconds, double phase, boolean fromSit, int id) {
      double len = LENGTH[layer];
      return switch (layer) {
         case STAND_UP, SIT_DOWN, GAZE, GAZE_SIT, RISE, CALM -> Math.min(Math.max(since, 0.0F), len);
         case HYSTERIA -> wrap(since - (fromSit ? LENGTH[RISE] : 0.0), len);
         case WALK, RUN -> wrap(phase, 1.0) * len;
         case CLIMB -> wrap(ageSeconds * 1.4, len);
         default -> wrap(ageSeconds + id * 0.37, len);
      };
   }

   /** Shuffle share by ground speed (blocks per tick). */
   public static float walkWeight(float move) {
      return smooth((move - WALK_FROM) / (WALK_FULL - WALK_FROM));
   }

   /** Run share of the chase by ground speed (the rest is the waiting crouch). */
   public static float runWeight(float move) {
      return smooth((move - RUN_FROM) / (RUN_FULL - RUN_FROM));
   }

   /** Envelope of an upper-body strike overlay at t seconds into a clip of the given length. */
   public static float strike(double t, double len) {
      if (t < 0.0 || t >= len) {
         return 0.0F;
      }
      return smooth((float)(t / 0.06)) * smooth((float)((len - t) / 0.12));
   }

   static double wrap(double t, double len) {
      double r = t % len;
      return r < 0.0 ? r + len : r;
   }

   public static float smooth(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }
}
