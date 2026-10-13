package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.AbilityHandler;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CatchWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CutRain;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.FistCombo;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.LightPunch;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.ReflectWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SkyKick;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.StepDash;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SheathStrike;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.Stomp;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidToggle;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * Router of Reinhard's abilities (stage 3 Task 1): a slot press → its
 * {@link ReinhardAbility} → the gate ({@link #gate}) → the ability's
 * {@link AbilityHandler#tryStart}. A refusal plays the refuse sound and spends
 * nothing. One action timeline at a time ({@link ReinhardState#action}).
 * Server only, except the pure {@link #gate} and {@link #abilityFor}.
 */
public final class ReinhardActions {
   private static final Map<ReinhardAbility, AbilityHandler> HANDLERS = new EnumMap<>(ReinhardAbility.class);

   static {
      register(ReinhardAbility.LIGHT_PUNCH, new LightPunch());
      register(ReinhardAbility.STEP_DASH, new StepDash());
      register(ReinhardAbility.CATCH, new CatchWindow());
      register(ReinhardAbility.FIST_COMBO, new FistCombo());
      register(ReinhardAbility.SKY_KICK, new SkyKick());
      register(ReinhardAbility.GRAB_THROW, new GrabThrow());
      register(ReinhardAbility.DASH_BEHIND, new DashBehind());
      register(ReinhardAbility.STOMP, new Stomp());
      register(ReinhardAbility.REFLECT, new ReflectWindow());
      register(ReinhardAbility.REID, new ReidToggle());
      register(ReinhardAbility.SHEATH_STRIKE, new SheathStrike());
      register(ReinhardAbility.CUT_RAIN, new CutRain());
      register(ReinhardAbility.WORLD_CUT, new dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut());
   }

   private ReinhardActions() {
   }

   /** Every condition of a press, read from the server state; {@code allowed} = the slot condition of Reid's phase (spec §7.1). */
   public record Gate(boolean canAct, boolean anchored, ReinhardMode mode, ReinhardMode abilityMode, int cooldown, boolean busy, boolean timelineRunning,
      boolean allowed) {
      public Gate(boolean canAct, boolean anchored, ReinhardMode mode, ReinhardMode abilityMode, int cooldown, boolean busy, boolean timelineRunning) {
         this(canAct, anchored, mode, abilityMode, cooldown, busy, timelineRunning, true);
      }
   }

   public enum Refusal {
      NONE,
      CONTROLLED,
      WRONG_MODE,
      CONDITION,
      COOLDOWN,
      BUSY;
   }

   /** Pure gate: control first, then mode, Reid's slot condition, cooldown, busy / a running timeline. */
   public static Refusal gate(Gate g) {
      if (!g.canAct() || g.anchored()) {
         return Refusal.CONTROLLED;
      }

      if (g.mode() != g.abilityMode()) {
         return Refusal.WRONG_MODE;
      }

      if (!g.allowed()) {
         return Refusal.CONDITION;
      }

      if (g.cooldown() > 0) {
         return Refusal.COOLDOWN;
      }

      return g.busy() || g.timelineRunning() ? Refusal.BUSY : Refusal.NONE;
   }

   @Nullable
   public static ReinhardAbility abilityFor(@Nullable HeroAction action) {
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         if (ability.action() == action) {
            return ability;
         }
      }

      return null;
   }

   /** Every ability class is registered in the static block above. */
   static void register(ReinhardAbility ability, AbilityHandler handler) {
      HANDLERS.put(ability, handler);
   }

   @Nullable
   static AbilityHandler handler(ReinhardAbility ability) {
      return HANDLERS.get(ability);
   }

   /** Not alive or anchored by a control: no ability may run. */
   static boolean controlled(ServerPlayer player) {
      return !player.isAlive() || HeroDamage.isAnchored(player);
   }

   /** A slot key edge from {@code HeroInputC2SPacket} (releases are ignored: every stage 3 ability is a press). */
   public static void handleInput(ServerPlayer player, ReinhardState state, HeroAction action, boolean pressed) {
      ReinhardAbility ability = abilityFor(action);
      if (!pressed || ability == null || !ability.shipped()) {
         return;
      }

      AbilityHandler handler = HANDLERS.get(ability);
      if (handler == null) {
         ReinhardHero.INSTANCE.onInputRefused(player, action);
         return;
      }

      if (handler.claimsPress(state)) {
         if (controlled(player)) {
            ReinhardHero.INSTANCE.onInputRefused(player, action);
         } else {
            handler.secondPress(player, state);
         }

         return;
      }

      press(player, state, ability, handler);
   }

   /** Gate + start; shared with the Dash Behind packet (stage 3 Task 8). */
   static boolean press(ServerPlayer player, ReinhardState state, ReinhardAbility ability, AbilityHandler handler) {
      Refusal refusal = gate(new Gate(!controlled(player), HeroDamage.isAnchored(player), state.mode, ability.mode(), state.cooldowns[ability.ordinal()],
         state.busy(), state.action.running(), Reid.allows(ability, state.reid.phase())));
      if (refusal != Refusal.NONE || !handler.tryStart(player, state)) {
         ReinhardHero.INSTANCE.onInputRefused(player, ability.action());
         handler.refused(player, state, refusal);
         return false;
      }

      // Starting an ability is combat (spec §7.3: the 60 s auto-return counts from the last fight).
      state.reid.combat();
      return true;
   }

   /** A Dash Behind press from its own packet: the request rides in the state while the gate and tryStart run. */
   public static void handleDashBehind(ServerPlayer player, ReinhardState state, DashBehind.Request request) {
      ReinhardAbility ability = ReinhardAbility.DASH_BEHIND;
      AbilityHandler handler = HANDLERS.get(ability);
      if (handler == null || !ability.shipped()) {
         return;
      }

      state.behindRequest = request;
      try {
         press(player, state, ability, handler);
      } finally {
         state.behindRequest = null;
      }
   }

   /** Server tick: a controlled Reinhard drops every action; abilities tick, then the timeline advances. */
   public static void tick(ServerPlayer player, ReinhardState state) {
      if (controlled(player)) {
         cancelAll(player, state, null);
         return;
      }

      for (AbilityHandler handler : HANDLERS.values()) {
         handler.tick(player, state);
      }

      state.action.tick();
   }

   /** Mode switch (spec §5): every running action, window, chain and grab ends. */
   public static void onModeChange(ServerPlayer player, ReinhardState state) {
      cancelAll(player, state, null);
   }

   public static void cleanup(ServerPlayer player, ReinhardState state, @Nullable CleanupReason reason) {
      cancelAll(player, state, reason);
   }

   static void cancelAll(ServerPlayer player, ReinhardState state, @Nullable CleanupReason reason) {
      for (AbilityHandler handler : HANDLERS.values()) {
         handler.cancel(player, state, reason);
      }

      state.action.cancel();
   }
}
