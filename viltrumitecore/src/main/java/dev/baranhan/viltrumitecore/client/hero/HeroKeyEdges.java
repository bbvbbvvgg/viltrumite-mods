package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Slot-id edge state; no fixed ability limit and no positional ownership across heroes. */
public final class HeroKeyEdges {
   private record State(HeroAction action, boolean down, boolean sent) {}
   private final Map<String, State> states = new HashMap<>();
   private Object session;
   private String hero;
   private String layout;
   private boolean focused;

   public void clear() {
      states.clear();
      session = null;
      hero = null;
      layout = null;
      focused = false;
   }

   /** A new hero/page/binding/focus context samples held keys without firing them. */
   public void tick(Object session, String hero, String layout, boolean focused, String[] slots,
      Function<String, HeroAction> actionFor, Predicate<String> isDown, BiConsumer<HeroAction, Boolean> send) {
      boolean sameHero = this.session == session && Objects.equals(this.hero, hero);
      boolean changed = !sameHero || !Objects.equals(this.layout, layout) || this.focused != focused;
      if (changed) {
         // A page change or GUI must stop this hero's channels. Never replay an old hero's
         // release against a different hero or a new connection/player object.
         if (sameHero) {
            states.values().stream().filter(State::sent).forEach(s -> send.accept(s.action(), false));
         }
         states.clear();
      }
      this.session = session;
      this.hero = hero;
      this.layout = layout;
      this.focused = focused;
      var live = new HashSet<String>();
      for (String slot : slots) {
         HeroAction action = actionFor.apply(slot);
         if (action == null || !live.add(slot)) continue;
         boolean down = focused && isDown.test(slot);
         State old = states.get(slot);
         boolean sent = old != null && old.sent();
         if (!changed && (old == null || old.action() != action || old.down() != down)) {
            if (old != null && old.action() != action && sent) {
               send.accept(old.action(), false);
               sent = false;
            }
            if (down) {
               send.accept(action, true);
               sent = true;
            } else if (sent) {
               send.accept(action, false);
               sent = false;
            }
         }
         states.put(slot, new State(action, down, sent));
      }
      states.entrySet().removeIf(entry -> {
         if (live.contains(entry.getKey())) return false;
         if (entry.getValue().sent()) send.accept(entry.getValue().action(), false);
         return true;
      });
   }
}
