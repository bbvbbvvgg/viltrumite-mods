package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.util.ArrayList;
import java.util.List;

/**
 * Key-pose beats of a timed clip, in server ticks, derived from its row of the
 * attack table (R2b clip plans, previews). Heavy shape: the gather starts a
 * third into the windup, the hammer hangs loaded just before the contact, the
 * contact is the damage tick, the weight carries past the target through the
 * contact phase and settles halfway through the recovery, the clip ends in the
 * guard it started from (chains start from it).
 */
final class ThorClipBeats {
   record Beat(String name, int tick) {
      double seconds() {
         return this.tick / (double)ThorAttacks.TICKS_PER_SECOND;
      }
   }

   static final String START = "start";
   static final String GATHER = "gather";
   static final String LOADED = "loaded";
   static final String CONTACT = "contact";
   static final String OVERSWING = "overswing";
   static final String SETTLE = "settle";
   static final String END = "end";

   private ThorClipBeats() {
   }

   static List<Beat> of(ThorAttacks.Phases p) {
      List<Beat> beats = new ArrayList<>();
      int windup = p.windup();
      if (windup > 0) {
         int loaded = windup - (windup >= 6 ? 2 : 1);
         add(beats, START, 0);
         add(beats, GATHER, Math.min(loaded - 1, Math.round(windup * 0.35F)));
         add(beats, LOADED, loaded);
      }

      // A clip without windup (electric mode, storm) starts on its contact.
      add(beats, CONTACT, p.contactTick());
      if (p.contact() > 0 && p.recovery() > 0) {
         add(beats, OVERSWING, p.chainFrom());
      }

      if (p.recovery() > 1) {
         add(beats, SETTLE, p.chainFrom() + Math.round(p.recovery() * 0.5F));
      }

      add(beats, END, p.length());
      return beats;
   }

   /** Evenly spaced beats of a clip outside the table (loops, lift, flight). */
   static List<Beat> even(int lengthTicks, int count) {
      List<Beat> beats = new ArrayList<>();
      for (int i = 0; i < count; i++) {
         add(beats, "f" + i, Math.round(lengthTicks * i / (float)Math.max(1, count - 1)));
      }

      return beats;
   }

   private static void add(List<Beat> beats, String name, int tick) {
      if (tick < 0 || !beats.isEmpty() && tick <= beats.get(beats.size() - 1).tick()) {
         return;
      }

      beats.add(new Beat(name, tick));
   }
}
