package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Plays the authored R2b clips of thor.animation.json on Thor's own body (third person) and
 * on the camera arms (first person). Before this, only crouch / sit / lift were read from the
 * file and every strike was the procedural vanilla-arm keys of ThorPoser bridged onto the geo.
 * <ul>
 *   <li>Locomotion: idle / walk / run (the {@code _hammer} variants while Mjölnir is in the hand),
 *   flight hover / cruise / sonic. Full-body clips over the vanilla bridge; the head keeps the look.</li>
 *   <li>Attacks: the {@link ThorAttacks} row of the running move (its synced combat pose) on the
 *   snapshot clock, at full weight, with a short blend in and out.</li>
 *   <li>Throw / recall: throw_windup, throw_release, pull.</li>
 * </ul>
 */
public final class ThorClipPlayer {
   /**
    * World distance (blocks) of one walk / run cycle: make_thor_clips.py GAIT, gait_blocks(). The stance foot of
    * the clip moves back at exactly this pace, so a cycle driven by the distance walked never slides (owner
    * playtest: the old cycle followed vanilla's walkAnimation, ~30% faster than the feet moved, and its sprint
    * value saturates). Checked against the clips by ThorGaitTest.
    */
   static final double WALK_CYCLE_BLOCKS = 2.0 * 8.5 / 16.0 / 0.48;
   static final double RUN_CYCLE_BLOCKS = 2.0 * 8.0 / 16.0 / 0.34;
   /** Leg bones the locomotion keeps while an attack plays on the move (owner playtest: the legs froze mid-step). */
   static final String[] LEG_BONES = {"right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot"};
   private static final String[] PELVIS = {"body"};
   /** Attack clips whose legs are the move itself (kicks, lunges, slams, sweeps, tackles): they keep their legs. */
   private static final String[] LEG_DRIVEN = {"kick", "lunge", "slam", "pound", "tackle", "sweep", "spin", "wrath"};
   /** Ticks of throw_windup (make_thor_clips.py THROW_COIL): the INTRO's coil continues this far into the hold. */
   static final int THROW_COIL = 10;
   /** Crossfade (ticks) from the pose the throw reached into the release. */
   private static final double THROW_FADE = 1.5;
   /** Blend in / out of an attack clip (ticks). */
   private static final double BLEND = 2.0;
   /** Guard pose blend in / out (ticks). */
   private static final double GUARD_BLEND = 3.0;
   private static final Map<Entity, ActionClock> CLOCKS = new WeakHashMap<>();
   private static final Map<Entity, ActionClock> FP_CLOCKS = new WeakHashMap<>();
   private static final Map<Entity, Gait> GAITS = new WeakHashMap<>();
   private static final Map<Entity, double[]> THROW_REACHED = new WeakHashMap<>();
   private static final Map<Entity, double[]> GUARD = new WeakHashMap<>();
   private static final Map<Entity, double[]> FP_GUARD = new WeakHashMap<>();

   /** Distance walked on the ground (blocks), from the synced positions, so remote players get it too. */
   static final class Gait {
      double distance;
      double last;
      int tick = Integer.MIN_VALUE;

      double at(Entity entity, float partialTick) {
         if (entity.tickCount != this.tick) {
            double step = Math.sqrt(sq(entity.getX() - entity.xo) + sq(entity.getZ() - entity.zo));
            // A teleport or a respawn is not a step.
            this.last = step > 1.5 ? 0.0 : step;
            if (this.tick != Integer.MIN_VALUE) {
               this.distance += this.last;
            }

            this.tick = entity.tickCount;
         }

         return this.distance - this.last * (1.0 - partialTick);
      }

      private static double sq(double v) {
         return v * v;
      }
   }

   /** One action clip with the clip-local time it plays at (ticks), and the clip the throw crossfades from. */
   record Shot(String name, double ticks, @Nullable String fromName, double fromTicks, float fromFade) {
      Shot(String name, double ticks) {
         this(name, ticks, null, 0.0, 0.0F);
      }
   }

   private ThorClipPlayer() {
   }

   @Nullable
   static Animation clip(String name) {
      return AnimCache.animation(ThorAssets.ANIMATIONS, name);
   }

   /** The authored clip of the running move (TP name), or null. */
   @Nullable
   static String actionClip(HeroPublicSnapshot snapshot) {
      ThorMove move = ThorView.move(snapshot);
      if (move == null) {
         return null;
      }

      ThorAttacks.Attack row = ThorAttacks.of(move, snapshot.resource());
      if (row != null) {
         return row.clip();
      }

      return switch (move) {
         case HAMMER_THROW -> switch (ThorView.phase(snapshot)) {
            case INTRO -> "throw_windup";
            case MAIN -> snapshot.actionElapsed() < THROW_COIL ? "throw_windup" : "throw_hold";
            default -> "throw_release";
         };
         case RECALL, HAMMER_PULL -> "pull";
         case PICKUP -> ThorView.phase(snapshot) == MovePhase.INTRO ? null : "lift_thor";
         default -> null;
      };
   }

   /**
    * The clip-local time of the running move's clip. Most clips span the whole move; the throw is three clips over
    * its phases (wind-up across the INTRO into the hold, the hold looped, the release from the FINISH, which the
    * server jumps to on the release) and the pick-up's heave starts on its contact tick.
    */
   static Shot shot(Entity entity, HeroPublicSnapshot snapshot, String name, double at) {
      ThorMove move = ThorView.move(snapshot);
      if (move == ThorMove.HAMMER_THROW) {
         int windup = dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirTuning.THROW_WINDUP;
         int finish = windup + dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirTuning.THROW_HOLD_MAX;
         double[] reached = THROW_REACHED.computeIfAbsent(entity, e -> new double[]{0.0, 0.0});
         switch (name) {
            case "throw_windup" -> {
               reached[0] = 0.0;
               reached[1] = at;
               return new Shot(name, at);
            }
            case "throw_hold" -> {
               reached[0] = 1.0;
               reached[1] = at - THROW_COIL;
               return new Shot(name, at - THROW_COIL);
            }
            default -> {
               double t = Math.max(0.0, at - finish);
               float fade = (float)Math.max(0.0, 1.0 - t / THROW_FADE);
               return new Shot(name, t, reached[0] > 0.5 ? "throw_hold" : "throw_windup", reached[1], fade);
            }
         }
      }

      if (move == ThorMove.PICKUP) {
         return new Shot(name, Math.max(0.0, at - dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirTuning.PICKUP_REACH));
      }

      return new Shot(name, at);
   }

   /** The attack keeps its own legs (they are the move), else the locomotion keeps stepping under it. */
   static boolean legDriven(String clip) {
      for (String part : LEG_DRIVEN) {
         if (clip.contains(part)) {
            return true;
         }
      }

      return false;
   }

   /** Third person, after the vanilla bridge and before the crouch / sit / lift clips. */
   public static void applyBody(BakedGeoModel body, AbstractClientPlayer player, float partialTick, boolean sitting) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         CLOCKS.remove(player);
         return;
      }

      double ticks = player.tickCount + (double)partialTick;
      boolean holding = ThorView.place(snapshot, player) == MjolnirPlace.HAND;
      String hammer = holding ? "_hammer" : "";
      float stepping = 0.0F;
      if (!sitting && player.isAlive()) {
         FlightState flight = player instanceof ViltrumiteFlightPlayer flyer ? flyer.getFlightState() : FlightState.NONE;
         if (flight != FlightState.NONE) {
            loop(body, "flight_" + flight.name().toLowerCase(Locale.ROOT), ticks / 20.0, 1.0F);
         } else {
            loop(body, "idle" + hammer, ticks / 20.0, 1.0F);
            float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
            if (speed > 0.01F) {
               boolean run = player.isSprinting();
               Animation gait = clip((run ? "run" : "walk") + hammer);
               if (gait != null) {
                  double walked = GAITS.computeIfAbsent(player, e -> new Gait()).at(player, partialTick);
                  double cycle = walked / (run ? RUN_CYCLE_BLOCKS : WALK_CYCLE_BLOCKS);
                  AnimationController.overlay(gait, body, (cycle - Math.floor(cycle)) * gait.lengthSeconds(), speed);
                  stepping = speed;
               }
            }
         }
      }

      // Audit v3: the guard (F, ThorGuard) over the locomotion; walking or flying keeps its legs.
      float guard = guardWeight(GUARD, player, snapshot, ticks);
      Animation guardClip = guard > 0.0F && !sitting ? clip(guardClip(holding)) : null;
      if (guardClip != null) {
         boolean flying = player instanceof ViltrumiteFlightPlayer flyer && flyer.getFlightState() != FlightState.NONE;
         float keep = flying ? 1.0F : stepping;
         float[][] legs = keep > 0.0F ? save(body, LEG_BONES) : null;
         float[][] pelvis = legs != null ? save(body, PELVIS) : null;
         AnimationController.overlay(guardClip, body, clipSeconds(guardClip, ticks), guard);
         if (legs != null) {
            restore(body, LEG_BONES, legs, keep);
            restorePosition(body, PELVIS, pelvis, keep);
         }
      }

      String name = actionClip(snapshot);
      ActionClock clock = CLOCKS.computeIfAbsent(player, e -> new ActionClock());
      Animation action = name == null ? null : clip(name);
      double at = clock.sample(action == null ? -1 : snapshot.actionId(), snapshot.variant(), snapshot.actionElapsed(), snapshot.actionLength(), ticks);
      if (action != null) {
         Shot shot = shot(player, snapshot, name, at);
         float w = weight(at, snapshot.actionLength());
         float[][] legs = stepping > 0.0F && !legDriven(name) ? save(body, LEG_BONES) : null;
         float[][] pelvis = legs != null ? save(body, PELVIS) : null;
         if (shot.fromName() != null && shot.fromFade() > 0.0F) {
            Animation from = clip(shot.fromName());
            if (from != null) {
               AnimationController.overlay(from, body, clipSeconds(from, shot.fromTicks()), w);
            }
         }

         AnimationController.overlay(action, body, clipSeconds(action, shot.ticks()), w * (1.0F - shot.fromFade()));
         if (legs != null) {
            // Walking or running into an attack: the legs keep the gait, the attack owns the body above the hips.
            // The pelvis height goes with the legs (they hang from it); the torso keeps the attack's turn.
            restore(body, LEG_BONES, legs, stepping);
            restorePosition(body, PELVIS, pelvis, stepping);
         }
      }
   }

   /** Clip-local seconds: looped clips wrap, one-shots hold their last frame. */
   static double clipSeconds(Animation clip, double ticks) {
      double seconds = Math.max(0.0, ticks) / 20.0;
      double length = Math.max(1.0E-3, clip.lengthSeconds());
      return clip.loopType() == Animation.LoopType.LOOP ? seconds % length : Math.min(length, seconds);
   }

   static float[][] save(BakedGeoModel model, String[] names) {
      float[][] out = new float[names.length][];
      for (int i = 0; i < names.length; i++) {
         GeoBone bone = model.getBone(names[i]);
         out[i] = bone == null ? null : new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ};
      }

      return out;
   }

   static void restorePosition(BakedGeoModel model, String[] names, float[][] saved, float weight) {
      for (int i = 0; i < names.length; i++) {
         GeoBone bone = model.getBone(names[i]);
         float[] s = saved[i];
         if (bone != null && s != null) {
            bone.posX += (s[3] - bone.posX) * weight;
            bone.posY += (s[4] - bone.posY) * weight;
            bone.posZ += (s[5] - bone.posZ) * weight;
         }
      }
   }

   /** Puts the saved channels back by {@code weight} (1 = exactly as saved). */
   static void restore(BakedGeoModel model, String[] names, float[][] saved, float weight) {
      for (int i = 0; i < names.length; i++) {
         GeoBone bone = model.getBone(names[i]);
         float[] s = saved[i];
         if (bone == null || s == null) {
            continue;
         }

         bone.rotX += (s[0] - bone.rotX) * weight;
         bone.rotY += (s[1] - bone.rotY) * weight;
         bone.rotZ += (s[2] - bone.rotZ) * weight;
         bone.posX += (s[3] - bone.posX) * weight;
         bone.posY += (s[4] - bone.posY) * weight;
         bone.posZ += (s[5] - bone.posZ) * weight;
      }
   }

   /**
    * First person, audit v3 (item 3): the camera arms play the whole {@code _fp} clip, shoulders included
    * (make_thor_clips.py solves each camera arm from its shoulder: the turn and the slide of armorRightArm /
    * armorLeftArm are the motion, the forearm and hand only finish it). Before, the shoulders were restored to
    * ThorFirstPerson's procedural rows, so only the forearms moved and the authored swings read as twitches.
    * Under the move: the idle hold of the hand (with or without Mjölnir) and the guard.
    */
   public static void applyArms(BakedGeoModel arms, AbstractClientPlayer player, float partialTick) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         FP_CLOCKS.remove(player);
         return;
      }

      double ticks = player.tickCount + (double)partialTick;
      boolean holding = ThorView.place(snapshot, player) == MjolnirPlace.HAND;
      loop(arms, (holding ? "idle_hammer" : "idle") + ThorAttacks.FP_SUFFIX, ticks / 20.0, 1.0F);
      float guard = guardWeight(FP_GUARD, player, snapshot, ticks);
      if (guard > 0.0F) {
         loop(arms, guardClip(holding) + ThorAttacks.FP_SUFFIX, ticks / 20.0, guard);
      }

      String name = actionClip(snapshot);
      Animation action = name == null ? null : clip(name + ThorAttacks.FP_SUFFIX);
      ActionClock clock = FP_CLOCKS.computeIfAbsent(player, e -> new ActionClock());
      double at = clock.sample(action == null ? -1 : snapshot.actionId(), snapshot.variant(), snapshot.actionElapsed(), snapshot.actionLength(), ticks);
      if (action != null) {
         Shot shot = shot(player, snapshot, name, at);
         AnimationController.overlay(action, arms, clipSeconds(action, shot.ticks()), weight(at, snapshot.actionLength()));
      }
   }

   /** The running move has an authored first-person clip (ThorFirstPerson's procedural rows then stay out). */
   public static boolean playsFpClip(@Nullable HeroPublicSnapshot snapshot) {
      String name = snapshot == null ? null : actionClip(snapshot);
      return name != null && clip(name + ThorAttacks.FP_SUFFIX) != null;
   }

   /** First person: the left camera arm is drawn while the guard or the running clip moves it (AlwaysVisibleOffhandMixin). */
   public static boolean wantsOffhand(AbstractClientPlayer player) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         return false;
      }

      if (ThorFlags.guard(snapshot.heroFlags())) {
         return true;
      }

      String name = actionClip(snapshot);
      Animation action = name == null ? null : clip(name + ThorAttacks.FP_SUFFIX);
      return action != null && action.boneAnimations().containsKey(ThorAssets.FP_LEFT_ARM);
   }

   static String guardClip(boolean holding) {
      return holding ? "guard_hammer" : "guard_fist";
   }

   /** The guard pose's weight: up while the flag is set and no move runs, a short smooth blend either way. */
   static float guardWeight(Map<Entity, double[]> blends, Entity entity, HeroPublicSnapshot snapshot, double ticks) {
      boolean up = ThorFlags.guard(snapshot.heroFlags()) && ThorView.move(snapshot) == null;
      double[] blend = blends.computeIfAbsent(entity, e -> new double[]{0.0, ticks});
      double dt = Math.max(0.0, Math.min(5.0, ticks - blend[1]));
      blend[1] = ticks;
      blend[0] = up ? Math.min(1.0, blend[0] + dt / GUARD_BLEND) : Math.max(0.0, blend[0] - dt / GUARD_BLEND);
      double x = blend[0];
      return (float)(x * x * (3.0 - 2.0 * x));
   }

   private static void loop(BakedGeoModel model, String name, double seconds, float weight) {
      Animation clip = clip(name);
      if (clip != null) {
         double length = Math.max(1.0E-3, clip.lengthSeconds());
         AnimationController.overlay(clip, model, seconds % length, weight);
      }
   }

   /** Full weight inside the action, a short ramp at its first and last ticks. */
   static float weight(double at, int length) {
      double in = Math.min(1.0, at / BLEND + 0.25);
      double out = length > 0 ? Math.min(1.0, (length - at) / BLEND + 0.25) : 1.0;
      return (float)Math.max(0.0, Math.min(in, out));
   }
}
