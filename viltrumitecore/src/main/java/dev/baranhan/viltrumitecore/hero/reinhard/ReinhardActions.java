package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.AbilityHandler;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CatchWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.FistCombo;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.LightPunch;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.MeleeChain;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.ReflectWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SkyKick;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.StepDash;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DiagonalWorldCut;
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
      register(ReinhardAbility.REID_STRIKE, new dev.baranhan.viltrumitecore.hero.reinhard.ability.ReidMelee());
      register(ReinhardAbility.STEP_DASH, new StepDash());
      register(ReinhardAbility.CATCH, new CatchWindow());
      register(ReinhardAbility.FIST_COMBO, new FistCombo());
      register(ReinhardAbility.SKY_KICK, new SkyKick());
      register(ReinhardAbility.GRAB_THROW, new GrabThrow());
      register(ReinhardAbility.DASH_BEHIND, new DashBehind());
      register(ReinhardAbility.STOMP, new Stomp());
      register(ReinhardAbility.REFLECT, new ReflectWindow());
      register(ReinhardAbility.REID, new ReidToggle());
      register(ReinhardAbility.DIAGONAL_WORLD_CUT, new DiagonalWorldCut());
      register(ReinhardAbility.SAW_SPHERE, new dev.baranhan.viltrumitecore.hero.reinhard.saw.SawSphere());
      register(ReinhardAbility.WORLD_CUT, new dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut());
      register(ReinhardAbility.WORLD_ERASURE, new dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldErasure());
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
      if (!pressed || ability == null || !ReinhardAbilities.active(ability)) {
         return;
      }

      // The merged dash key reaches here only as Step Dash: the client sends a Knight's
      // press with a target as ReinhardDashBehindC2SPacket (one predictor, one packet path).
      ability = ReinhardAbilities.contextual(ability,player.isShiftKeyDown());
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

   /** One click starts one strike; a click in the second half of a strike is buffered as the next
    * link of the chain (fists outside Knight, drawn Reid, Knight's Fist Combo). No slot/R route. */
   public static void primaryAttack(ServerPlayer player, ReinhardState state) {
      if (controlled(player) || state.busy()) return;
      if (state.mode == ReinhardMode.HOLY_SWORD && state.reid.drawn()) {
         melee(player, state, ReinhardAbility.REID_STRIKE);
      } else if (state.mode == ReinhardMode.KNIGHT) {
         if (FistCombo.claims(state)) FistCombo.queue(state);
         else if (!state.action.running()) new FistCombo().tryStart(player,state);
      } else melee(player, state, ReinhardAbility.LIGHT_PUNCH);
      state.reid.combat();
   }

   private static void melee(ServerPlayer player, ReinhardState state, ReinhardAbility ability) {
      if (state.action.is(ability)) {
         MeleeMoves.Move move = ability == ReinhardAbility.REID_STRIKE ? MeleeMoves.reid(state.action.phase()) : MeleeMoves.fist(state.action.phase());
         MeleeChain.buffer(state, move);
      } else if (!state.action.running()) {
         HANDLERS.get(ability).tryStart(player, state);
      }
   }

   /** Pure: LMB during this action belongs to the strike chain (buffered, never refused). */
   public static boolean meleeChain(@Nullable ReinhardAbility running) {
      return running == ReinhardAbility.FIST_COMBO || running == ReinhardAbility.LIGHT_PUNCH || running == ReinhardAbility.REID_STRIKE;
   }

   static ReinhardMode contextualMode(ReinhardAbility ability, ReinhardMode mode) {
      return ability == ReinhardAbility.STEP_DASH || ability == ReinhardAbility.GRAB_THROW && mode == ReinhardMode.RESTRAINED
         ? mode : ability.mode();
   }

   public static void secondaryUse(ServerPlayer player, ReinhardState state) {
      if (controlled(player)) return;
      if (GrabThrow.holding(state)) { GrabThrow.finish(player, state); return; }
      if (!ReinhardHero.canContextGrab(player)) return;
      press(player, state, ReinhardAbility.GRAB_THROW, HANDLERS.get(ReinhardAbility.GRAB_THROW));
   }

   /** Gate + start; shared with the Dash Behind packet (stage 3 Task 8). */
   static boolean press(ServerPlayer player, ReinhardState state, ReinhardAbility ability, AbilityHandler handler) {
      Refusal refusal = gate(new Gate(!controlled(player), HeroDamage.isAnchored(player), state.mode, contextualMode(ability, state.mode), state.cooldowns[ability.ordinal()],
         state.busy(), state.action.running(), Reid.allows(ability, state.reid.phase())));
      if (refusal != Refusal.NONE || !handler.tryStart(player, state)) {
         ReinhardHero.INSTANCE.onInputRefused(player, ability.action());
         handler.refused(player, state, refusal);
         return false;
      }

      // Starting an ability is combat (spec §7.3: the 60 s auto-return counts from the last fight).
      state.reid.combat();
      // The ability's spoken line, if it has one (World Cut); only a successful start speaks.
      dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.ability(player, ability.id());
      return true;
   }

   /** A Dash Behind press from its own packet: the request rides in the state while the gate and tryStart run. */
   public static void handleDashBehind(ServerPlayer player, ReinhardState state, DashBehind.Request request) {
      ReinhardAbility ability = ReinhardAbility.DASH_BEHIND;
      AbilityHandler handler = HANDLERS.get(ability);
      // Dash Behind has no slot of its own: it is the Knight variant of the merged dash key.
      if (handler == null || !ReinhardAbilities.active(ReinhardAbility.STEP_DASH)) {
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
      state.defense.clear();
      state.arrowCapture.release();
      state.cutAnchor = null;
   }
}
