package dev.baranhan.viltrumitecore.hero.thor.combat;

import static dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike.*;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Session B's melee routes: which strike the n-th press of a chain plays,
 * from the rhythm of LMB / RMB and the context the chain started in
 * (sprint, jump, sneak). Pure. DEMO / PLACEHOLDER routes: the approved
 * counts are 8–10 armed (#38) and 4–5 unarmed (#145), the concrete list is
 * "[ОТКРЫТО]" (spec §6).
 */
public final class ComboBook {
   public enum Button {
      LMB,
      RMB
   }

   /** Context of the chain's first press. */
   public enum Starter {
      ANY,
      SPRINT,
      /** Jumping or falling (not flying: no attacks in full flight). */
      AIRBORNE,
      SNEAK
   }

   public record Route(String id, ThorMove move, Starter starter, List<Button> buttons, List<ThorStrike> strikes) {
      public Route {
         if (buttons.size() != strikes.size() || buttons.isEmpty()) {
            throw new IllegalArgumentException(id + ": one strike per press");
         }

         buttons = List.copyOf(buttons);
         strikes = List.copyOf(strikes);
      }

      public int length() {
         return this.buttons.size();
      }
   }

   /** The strike of the newest press; {@code last}: no route continues this chain. {@code completed}: a route ends exactly here. */
   public record Match(Route route, ThorStrike strike, boolean last, boolean completed) {
   }

   private static final Button L = Button.LMB;
   private static final Button R = Button.RMB;

   /**
    * Mjölnir in the hand: 10 routes (PLACEHOLDER). The main LMB chain is the rework's
    * four trajectories (spec §3): horizontal, diagonal from above, uppercut, finisher
    * into the ground; LLL+R ends in the spin instead, LL+R in the overhead.
    */
   public static final List<Route> ARMED = List.of(
      route("thunder_chain", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, L, L), List.of(SWING_RIGHT, SWING_BACKHAND, UPPERCUT, GROUND_SLAM)),
      route("thunder_spin", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, L, R), List.of(SWING_RIGHT, SWING_BACKHAND, UPPERCUT, SPIN)),
      route("overhead", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, R), List.of(SWING_RIGHT, SWING_BACKHAND, OVERHEAD)),
      route("earthbreaker", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, R, R), List.of(SWING_RIGHT, RISING, GROUND_SLAM)),
      route("heavy_spin", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(R, R, R), List.of(HEAVY_DOWN, HEAVY_REVERSE, HEAVY_SPIN)),
      route("pommel_sweep", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(R, L, L), List.of(HEAVY_DOWN, POMMEL, DOUBLE_SWING)),
      route("pommel_thrust", ThorMove.HAMMER_COMBO, Starter.ANY, List.of(R, L, R), List.of(HEAVY_DOWN, POMMEL, THRUST)),
      route("charge", ThorMove.HAMMER_COMBO, Starter.SPRINT, List.of(L, L), List.of(LUNGE, LUNGE_FINISH)),
      route("falling_slam", ThorMove.HAMMER_COMBO, Starter.AIRBORNE, List.of(L), List.of(FALLING_SLAM)),
      route("low_sweep", ThorMove.HAMMER_COMBO, Starter.SNEAK, List.of(R, R), List.of(LOW_SWEEP, SWEEP_LAUNCH)));

   /**
    * Bare fists: 5 routes of 3 heavy strikes (PLACEHOLDER; rework spec §3). The hook
    * left the boxer chain when it became three strikes: {@link #UNROUTED}.
    */
   public static final List<Route> UNARMED = List.of(
      route("boxer", ThorMove.FIST_COMBO, Starter.ANY, List.of(L, L, L), List.of(JAB, CROSS, FIST_UPPERCUT)),
      route("haymaker", ThorMove.FIST_COMBO, Starter.ANY, List.of(L, L, R), List.of(JAB, CROSS, HAYMAKER)),
      route("ground_pound", ThorMove.FIST_COMBO, Starter.ANY, List.of(R, R, R), List.of(HAMMER_FIST, HAMMER_FIST_LEFT, GROUND_POUND)),
      route("brawler", ThorMove.FIST_COMBO, Starter.ANY, List.of(R, L, R), List.of(HAMMER_FIST, ELBOW, PUSH_KICK)),
      route("tackle", ThorMove.FIST_COMBO, Starter.SPRINT, List.of(L, L, L), List.of(TACKLE, BODY_BLOW, TACKLE_HOOK)));

   /**
    * Strikes no route plays any more; kept because pose ids are ordinals
    * (append only) and the clip contract still names them.
    */
   public static final java.util.Set<ThorStrike> UNROUTED = java.util.Set.of(HOOK);

   private ComboBook() {
   }

   private static Route route(String id, ThorMove move, Starter starter, List<Button> buttons, List<ThorStrike> strikes) {
      return new Route(id, move, starter, buttons, strikes);
   }

   public static List<Route> routes(ThorMove move) {
      return move == ThorMove.HAMMER_COMBO ? ARMED : move == ThorMove.FIST_COMBO ? UNARMED : List.of();
   }

   /**
    * The strike of the newest press of {@code chain} (all presses so far,
    * newest last) for a chain that started in {@code starter}; null when no
    * route has this prefix (the caller then starts a new chain with the
    * newest press). Routes of the chain's own starter win over ANY.
    */
   @Nullable
   public static Match match(ThorMove move, Starter starter, List<Button> chain) {
      if (chain.isEmpty()) {
         return null;
      }

      List<Route> candidates = candidates(move, starter, chain);
      if (candidates.isEmpty() && starter != Starter.ANY) {
         candidates = candidates(move, Starter.ANY, chain);
      }

      if (candidates.isEmpty()) {
         return null;
      }

      int index = chain.size() - 1;
      boolean last = true;
      boolean completed = false;
      for (Route route : candidates) {
         if (route.length() > chain.size()) {
            last = false;
         } else {
            completed = true;
         }
      }

      Route first = candidates.get(0);
      return new Match(first, first.strikes().get(index), last, completed);
   }

   private static List<Route> candidates(ThorMove move, Starter starter, List<Button> chain) {
      List<Route> found = new ArrayList<>();
      for (Route route : routes(move)) {
         if (route.starter() == starter && route.length() >= chain.size() && route.buttons().subList(0, chain.size()).equals(chain)) {
            found.add(route);
         }
      }

      return found;
   }
}
