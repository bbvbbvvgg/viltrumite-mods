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
   /** Vanilla walk cycle in walkAnimation.position units (cos(position * 0.6662)). */
   private static final double STRIDE = Math.PI * 2.0 / 0.6662;
   /** Blend in / out of an attack clip (ticks). */
   private static final double BLEND = 2.0;
   private static final String[] FP_BONES = {"left_forearm", "left_hand", "right_forearm", "right_hand", "mjolnir_grip"};
   private static final Map<Entity, ActionClock> CLOCKS = new WeakHashMap<>();
   private static final Map<Entity, ActionClock> FP_CLOCKS = new WeakHashMap<>();

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
         case HAMMER_THROW -> ThorView.phase(snapshot) == MovePhase.INTRO ? "throw_windup" : "throw_release";
         case RECALL, HAMMER_PULL -> "pull";
         default -> null;
      };
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
      if (!sitting && player.isAlive()) {
         FlightState flight = player instanceof ViltrumiteFlightPlayer flyer ? flyer.getFlightState() : FlightState.NONE;
         if (flight != FlightState.NONE) {
            loop(body, "flight_" + flight.name().toLowerCase(Locale.ROOT), ticks / 20.0, 1.0F);
         } else {
            loop(body, "idle" + hammer, ticks / 20.0, 1.0F);
            float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
            if (speed > 0.01F) {
               Animation gait = clip((player.isSprinting() ? "run" : "walk") + hammer);
               if (gait != null) {
                  double cycle = player.walkAnimation.position(partialTick) / STRIDE;
                  AnimationController.overlay(gait, body, (cycle - Math.floor(cycle)) * gait.lengthSeconds(), speed);
               }
            }
         }
      }

      String name = actionClip(snapshot);
      ActionClock clock = CLOCKS.computeIfAbsent(player, e -> new ActionClock());
      Animation action = name == null ? null : clip(name);
      double at = clock.sample(action == null ? -1 : snapshot.actionId(), snapshot.variant(), snapshot.actionElapsed(), snapshot.actionLength(), ticks);
      if (action != null) {
         AnimationController.overlay(action, body, Math.min(action.lengthSeconds(), at / 20.0), weight(at, snapshot.actionLength()));
      }
   }

   /**
    * First person: the running move's {@code _fp} clip on the forearms, hands and grip of the
    * camera arms. The shoulders stay on ThorFirstPerson's rows (the vanilla arm the part rides).
    */
   public static void applyArms(BakedGeoModel arms, AbstractClientPlayer player, float partialTick) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      String name = snapshot == null ? null : actionClip(snapshot);
      Animation action = name == null ? null : clip(name + ThorAttacks.FP_SUFFIX);
      ActionClock clock = FP_CLOCKS.computeIfAbsent(player, e -> new ActionClock());
      double at = clock.sample(action == null ? -1 : snapshot.actionId(), snapshot == null ? 0 : snapshot.variant(),
         snapshot == null ? 0 : snapshot.actionElapsed(), snapshot == null ? 0 : snapshot.actionLength(), player.tickCount + (double)partialTick);
      if (action == null) {
         return;
      }

      // Save the bones the clip may not touch, overlay, restore them.
      Map<String, float[]> saved = new java.util.HashMap<>();
      for (GeoBone bone : arms.allBones()) {
         if (!fpBone(bone.name)) {
            saved.put(bone.name, new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ});
         }
      }

      AnimationController.overlay(action, arms, Math.min(action.lengthSeconds(), at / 20.0), weight(at, snapshot.actionLength()));
      for (GeoBone bone : arms.allBones()) {
         float[] s = saved.get(bone.name);
         if (s != null) {
            bone.rotX = s[0];
            bone.rotY = s[1];
            bone.rotZ = s[2];
            bone.posX = s[3];
            bone.posY = s[4];
            bone.posZ = s[5];
         }
      }
   }

   private static boolean fpBone(String name) {
      for (String bone : FP_BONES) {
         if (bone.equals(name)) {
            return true;
         }
      }

      return false;
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
