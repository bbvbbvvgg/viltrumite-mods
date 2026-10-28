package dev.baranhan.viltrumitecore.client.thor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The contract between the server's attack table ({@link ThorAttacks}) and the
 * authored clips in {@code thor.animation.json} (rework spec §3/§4, R2a → R2b):
 * <ul>
 *   <li>every row has a third-person clip ({@code clip}) and a first-person clip ({@code fpClip});</li>
 *   <li>each lasts exactly the row: {@code animation_length == phases.length() / 20} s;</li>
 *   <li>each plays once (no {@code loop: true}); {@code hold_on_last_frame} is fine;</li>
 *   <li>each carries exactly one {@code "timeline"} instruction {@value ThorAttacks#CONTACT_MARKER}
 *   at {@code windup / 20} s — the server's damage tick;</li>
 *   <li>the marker is reserved: a clip outside the table (idle, walk, lift, flight…) has none.</li>
 * </ul>
 * One action clock: TP and FP of a row share the length and the contact second.
 */
final class ThorClipContract {
   static final Path FILE = Path.of("src/main/resources/assets/viltrumitecore/animations/thor/thor.animation.json");
   static final double EPSILON = 1.0E-4;
   static final String FP_SUFFIX = ThorAttacks.FP_SUFFIX;

   private ThorClipContract() {
   }

   static double seconds(int ticks) {
      return ticks / (double)ThorAttacks.TICKS_PER_SECOND;
   }

   /** Every broken rule as a readable line; empty = the file matches the table. */
   static List<String> violations(Map<String, Animation> clips) {
      List<String> out = new ArrayList<>();
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         check(row, row.clip(), clips.get(row.clip()), out);
         check(row, row.fpClip(), clips.get(row.fpClip()), out);
      }

      for (Map.Entry<String, Animation> entry : clips.entrySet()) {
         if (ThorAttacks.byClip(entry.getKey()) == null && !contacts(entry.getValue()).isEmpty()) {
            out.add(entry.getKey() + ": not in the attack table but carries a '" + ThorAttacks.CONTACT_MARKER + "' marker");
         }
      }

      return out;
   }

   private static void check(ThorAttacks.Attack row, String name, Animation clip, List<String> out) {
      ThorAttacks.Phases phases = row.phases();
      String expected = String.format(Locale.ROOT, "%s (%d t = %.2f s, contact %d t = %.2f s)", name, phases.length(), seconds(phases.length()),
         phases.contactTick(), seconds(phases.contactTick()));
      if (clip == null) {
         out.add(name + ": missing; expected " + expected);
         return;
      }

      if (Math.abs(clip.lengthSeconds() - seconds(phases.length())) > EPSILON) {
         out.add(String.format(Locale.ROOT, "%s: animation_length %.4f s, expected %.4f s (%d t)", name, clip.lengthSeconds(),
            seconds(phases.length()), phases.length()));
      }

      if (clip.loopType() == Animation.LoopType.LOOP) {
         out.add(name + ": loops; an attack plays once (hold_on_last_frame is fine)");
      }

      List<Double> contacts = contacts(clip);
      if (contacts.size() != 1) {
         out.add(String.format(Locale.ROOT, "%s: %d '%s' markers, expected one at %.4f s", name, contacts.size(), ThorAttacks.CONTACT_MARKER,
            seconds(phases.contactTick())));
      } else if (Math.abs(contacts.get(0) - seconds(phases.contactTick())) > EPSILON) {
         out.add(String.format(Locale.ROOT, "%s: '%s' at %.4f s, expected %.4f s (tick %d, the server's damage tick)", name,
            ThorAttacks.CONTACT_MARKER, contacts.get(0), seconds(phases.contactTick()), phases.contactTick()));
      }
   }

   /** Seconds of the contact instructions ("contact", also inside a Blockbench script such as "contact;"). */
   static List<Double> contacts(Animation clip) {
      List<Double> out = new ArrayList<>();
      for (Animation.Event event : clip.events()) {
         if (event.type() == Animation.Event.Type.INSTRUCTION && hasContact(event.data())) {
            out.add(event.time());
         }
      }

      return out;
   }

   private static boolean hasContact(String data) {
      for (String token : data.split("[;\\s]+")) {
         if (token.equals(ThorAttacks.CONTACT_MARKER)) {
            return true;
         }
      }

      return false;
   }

   /** A minimal file that satisfies the contract (the self-test's reference; a starting point for authoring). */
   static JsonObject reference() {
      JsonObject animations = new JsonObject();
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         animations.add(row.clip(), clip(row));
         animations.add(row.fpClip(), clip(row));
      }

      JsonObject root = new JsonObject();
      root.addProperty("format_version", "1.8.0");
      root.add("animations", animations);
      return root;
   }

   private static JsonObject clip(ThorAttacks.Attack row) {
      JsonObject clip = new JsonObject();
      clip.addProperty("loop", "hold_on_last_frame");
      clip.addProperty("animation_length", seconds(row.length()));
      JsonObject timeline = new JsonObject();
      timeline.addProperty(Double.toString(seconds(row.phases().contactTick())), ThorAttacks.CONTACT_MARKER);
      clip.add("timeline", timeline);
      JsonObject rotation = new JsonObject();
      JsonArray zero = new JsonArray();
      zero.add(0);
      zero.add(0);
      zero.add(0);
      rotation.add("0.0", zero);
      JsonObject arm = new JsonObject();
      arm.add("rotation", rotation);
      JsonObject bones = new JsonObject();
      bones.add("right_arm", arm);
      clip.add("bones", bones);
      return clip;
   }
}
