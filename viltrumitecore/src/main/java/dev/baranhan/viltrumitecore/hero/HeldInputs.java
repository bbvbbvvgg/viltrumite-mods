package dev.baranhan.viltrumitecore.hero;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * Held mouse inputs (spec §6.1). The claim and canAct are checked only on the
 * press; a repeated press while held is ignored; the release always reaches
 * the action that was started, even when the claim or canAct changed since
 * (tool switch, landing, lock). Forced releases (control, death, disconnect,
 * hero change, hero-specific e.g. suit off) end everything still held.
 * Same pattern as HeroInputC2SPacket: releases always pass.
 */
public final class HeldInputs {
   /** The hero side of the seam. */
   public interface Sink {
      @Nullable
      HeroAction claim(MouseButton button);

      boolean canAct(HeroAction action);

      void handle(HeroAction action, boolean pressed);

      default void refused(HeroAction action) {
      }
   }

   private static final Map<UUID, HeldInputs> PLAYERS = new HashMap<>();
   private final EnumMap<MouseButton, HeroAction> held = new EnumMap<>(MouseButton.class);

   /** True when the press started an action. */
   public boolean press(MouseButton button, Sink sink) {
      if (this.held.containsKey(button)) {
         return false;
      }

      HeroAction action = sink.claim(button);
      if (action == null) {
         return false;
      }

      if (!sink.canAct(action)) {
         sink.refused(action);
         return false;
      }

      this.held.put(button, action);
      sink.handle(action, true);
      return true;
   }

   /** True when a started action was ended. */
   public boolean release(MouseButton button, Sink sink) {
      HeroAction action = this.held.remove(button);
      if (action == null) {
         return false;
      }

      sink.handle(action, false);
      return true;
   }

   public void releaseAll(Sink sink) {
      for (MouseButton button : MouseButton.values()) {
         this.release(button, sink);
      }
   }

   @Nullable
   public HeroAction heldAction(MouseButton button) {
      return this.held.get(button);
   }

   public boolean isEmpty() {
      return this.held.isEmpty();
   }

   /** Generic forced release each tick: anchored by control or dead. */
   public static boolean forcesRelease(boolean anchored, boolean alive) {
      return anchored || !alive;
   }

   // --- server glue (main thread only) ---

   public static Sink sinkFor(ServerPlayer player) {
      HeroDefinition hero = HeroRegistry.get(player);
      return new Sink() {
         @Override
         public HeroAction claim(MouseButton button) {
            return button == MouseButton.GUARD ? hero.guardAction(player) : hero.mouseAction(button, player);
         }

         @Override
         public boolean canAct(HeroAction action) {
            // Spec 2026-10-28-sinclair §7: a locked body (rabbit hex) starts no held action.
            return player.isAlive() && !ActionLocks.locked(player) && hero.canAct(player, action);
         }

         @Override
         public void handle(HeroAction action, boolean pressed) {
            hero.handleInput(player, action, pressed);
         }

         @Override
         public void refused(HeroAction action) {
            hero.onInputRefused(player, action);
         }
      };
   }

   public static void onPacket(ServerPlayer player, MouseButton button, boolean pressed) {
      HeldInputs inputs = PLAYERS.computeIfAbsent(player.getUUID(), id -> new HeldInputs());
      if (pressed) {
         inputs.press(button, sinkFor(player));
      } else {
         inputs.release(button, sinkFor(player));
      }

      if (inputs.isEmpty()) {
         PLAYERS.remove(player.getUUID());
      }
   }

   /** Force-release everything this player holds (routed to the current hero). */
   public static void releaseAll(ServerPlayer player) {
      HeldInputs inputs = PLAYERS.remove(player.getUUID());
      if (inputs != null) {
         inputs.releaseAll(sinkFor(player));
      }
   }

   /** Server tick, before the hero tick. */
   public static void tick(ServerPlayer player) {
      if (PLAYERS.containsKey(player.getUUID()) && forcesRelease(HeroDamage.isAnchored(player), player.isAlive())) {
         releaseAll(player);
      }
   }

   public static boolean holds(ServerPlayer player, MouseButton button) {
      HeldInputs inputs = PLAYERS.get(player.getUUID());
      return inputs != null && inputs.heldAction(button) != null;
   }
}
