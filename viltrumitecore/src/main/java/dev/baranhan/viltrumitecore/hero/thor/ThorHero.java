package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ReleaseCause;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPull;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirWorld;
import dev.baranhan.viltrumitecore.hero.thor.hammer.ThorTransform;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorMovementRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneView;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Thor, God of War Ragnarök (spec docs/design/2026-10-27-thor-design.md).
 * Foundation: identity, skin and Mjölnir on the body, context routing, the
 * staged move timeline, the combat presence and the session bridge
 * (hero/thor/bridge). No move reachable from a key is authored here: every
 * press resolves by context and is refused as "in development" until a
 * session registers it. No kit, vanilla chassis. Session A: the
 * one world Mjölnir (hero/thor/hammer) is entered, projected, dropped and
 * claimed from the lifecycle hooks below. Flight (session C, rework §5):
 * the original Viltrumite Flight only with Mjölnir in the hand, no flight
 * at all without it, none while a scene holds Thor (hero/thor/scene).
 */
public final class ThorHero implements HeroDefinition {
   public static final ThorHero INSTANCE = new ThorHero();

   private ThorHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.THOR;
   }

   /**
    * Both sides: flight only with Mjölnir really in the hand and no scene
    * holding Thor (rework spec §5; the client reads the synced hammer and
    * scene flags, the server its projected state). When it turns false
    * mid-flight the shared flight module resets the flight and the mayfly
    * grant is withdrawn (HeroFlightGrant): Thor falls normally.
    */
   @Override
   public boolean allowsFlight(Player player) {
      return ThorMovementRules.allowsFlight(player != null, player != null && ThorScenes.claimsMouse(player), player != null && hammerInHand(player));
   }

   /** Server: vanilla mayfly for the flight toggle while flight is allowed. */
   @Override
   public boolean grantsFlightAbility(Player player) {
      return ThorState.of(player) != null && this.allowsFlight(player);
   }

   /**
    * Both sides: Mjölnir really in the hand (attached and the right hand
    * free, ThorHands.holds) on the same wire values the flight permission
    * reads. The original flight is the only flight Thor has (null profile).
    */
   public static boolean hammerInHand(Player player) {
      return ThorHands.holds(hammer(player), ThorHands.free(player));
   }

   /** Where Mjölnir is: live state on the server, the synced flags on the client. */
   static HammerState hammer(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
         return snapshot != null && snapshot.heroId() == HeroId.THOR ? ThorFlags.hammer(snapshot.heroFlags()) : HammerState.HOLSTERED;
      }

      ThorState state = ThorState.of(player);
      return state == null ? HammerState.HOLSTERED : state.hammer();
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return false;
   }

   @Override
   public boolean hasAbilityPanel(Player player) {
      return true;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return ThorAbilities.owns(abilityId);
   }

   @Override
   public java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return ThorAbilities.panelAbilities();
   }

   @Override
   public net.minecraft.resources.ResourceLocation abilityIcon(String abilityId) {
      return ThorAbilities.icon(abilityId);
   }

   @Override
   public String[] heroInputSlots() {
      return ThorAbilities.slotIds();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return ThorAbilities.actionFor(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return ThorAbilities.defaultLoadout();
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   /** LMB on a block mines, except while a scene owns the mouse (grab choice, teleport confirm). */
   @Override
   public boolean claimsBlockAttack(Player player) {
      return ThorScenes.claimsMouse(player);
   }

   /**
    * RMB on a block stays vanilla (use / place / open), except while a scene
    * owns the mouse (grab throw facing a door, C-M15).
    */
   @Override
   public boolean claimsBlockUse(Player player) {
      return ThorScenes.claimsMouse(player);
   }

   /**
    * B-B1: outside a scene Thor takes RMB only when vanilla would not use it
    * (VanillaUse: both hands empty, not on a block, not on a villager / mount
    * / pet / armor stand / non-living entity such as a lying Mjölnir or an
    * ally). OPEN(owner): RMB priority on a lying Mjölnir; conservative = the
    * entity's own interaction (session A's pick-up).
    */
   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return !ThorScenes.claimsMouse(player);
   }

   /**
    * Both sides: only buttons whose context move is authored leave vanilla;
    * while a scene holds Thor every mouse button goes to the scene.
    */
   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      if (ThorScenes.claimsMouse(player)) {
         ThorInput input = ThorInput.of(button);
         return input == null ? null : input.action();
      }

      return ThorControls.claimedMouse(button, context(player));
   }

   /** LMB, RMB and MMB inputs reach Thor only through HeldInputs (review foundation m5). */
   @Override
   public boolean mouseOnly(HeroAction action) {
      return ThorInput.mouseOnly(action);
   }

   /** No vanilla hit in full flight (decisions 22, 34) or while a scene owns the mouse. */
   @Override
   public boolean allowsVanillaAttack(Player player) {
      return ThorMovementRules.attacksAllowed(ThorScenes.fullFlight(player), ThorScenes.claimsMouse(player));
   }

   /**
    * Server (C-M5): while a teleport selection or a fatality holds Thor in
    * place (possibly above the ground, from HOVER), the vanilla
    * "flying is not enabled" counter does not count it.
    */
   @Override
   public boolean sustainsAirborne(Player player) {
      return ThorScenes.holdsInPlace(player);
   }

   /** Both sides: the body a scene holds (grab, fatality), or -1. */
   @Override
   public int heldTargetId(Player holder) {
      return holder.level().isClientSide ? ThorSceneView.heldId(holder) : ThorScenes.heldId(holder);
   }

   @Override
   public net.minecraft.world.phys.Vec3 heldTargetPosition(Player holder, net.minecraft.world.entity.Entity target, float partialTick) {
      return holder.level().isClientSide ? ThorSceneView.heldPosition(holder, target, partialTick) : ThorScenes.heldPosition(holder, target);
   }

   /** A body in Thor's scene presses nothing of its own. */
   @Override
   public boolean anchorsHeldTarget(Player holder) {
      return true;
   }

   /** Claim context: live state on the server, the synced snapshot on the client. */
   static ThorContext context(Player player) {
      HammerState hammer = HammerState.HOLSTERED;
      ThorMode mode = ThorMode.NORMAL;
      boolean grabWindow = false;
      boolean detached = false;
      if (player.level().isClientSide) {
         HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
         if (snapshot != null && snapshot.heroId() == HeroId.THOR) {
            hammer = ThorFlags.hammer(snapshot.heroFlags());
            mode = ThorFlags.mode(snapshot.heroFlags());
            grabWindow = ThorFlags.grabWindow(snapshot.heroFlags());
            detached = ThorFlags.hammerDetached(snapshot.heroFlags());
         }
      } else {
         ThorState state = ThorState.of(player);
         if (state != null) {
            hammer = state.hammer();
            mode = state.mode();
            grabWindow = ThorBridge.grabWindowActive(state);
            detached = state.hammerDetached();
         }
      }

      boolean enemyNear = ThorTargets.nearestEnemy(player, ThorRules.MELEE_ENEMY_RADIUS) != null;
      return new ThorContext(hammer, mode, ThorHands.free(player), player.isShiftKeyDown(), !player.onGround(), ThorScenes.fullFlight(player), enemyNear,
         grabWindow, detached, false);
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      return player.isAlive() && !HeroDamage.isAnchored(player) && ThorInput.of(action) != null;
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      ThorFeedback.refuse(player, Component.translatable(ThorFeedback.BUSY));
   }

   @Override
   public void tick(ServerPlayer player) {
      ThorState state = ThorState.ensure(player);
      MjolnirWorld.project(player, state);
      ThorActions.tick(player, state);
      MjolnirPull.tick(player, state);
      ThorTransform.tick(player, state);
      MjolnirWorld.project(player, state);
   }

   /**
    * Slot keys arrive here without canAct (HeroInputC2SPacket): gate every
    * press again. Releases (HeldInputs, always delivered) go to the move the
    * press chose, e.g. a charged MMB throw.
    */
   /**
    * A forced release (death, logout, hero change, erasure, anchoring) is
    * never the player's: the move hears it as {@link ReleaseCause#FORCED}
    * (a charged throw does not fire, the storm cancels quietly).
    */
   @Override
   public void handleForcedRelease(ServerPlayer player, HeroAction action) {
      ThorInput input = ThorInput.of(action);
      ThorState state = input == null ? null : ThorState.of(player);
      if (state != null) {
         ThorActions.release(player, state, input, ReleaseCause.FORCED);
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      ThorInput input = ThorInput.of(action);
      if (input == null) {
         return;
      }

      if (!pressed) {
         ThorState state = ThorState.of(player);
         if (state != null) {
            ThorActions.release(player, state, input);
         }

         return;
      }

      ThorState current = ThorState.of(player);
      if (current != null && player.isAlive() && ThorScenes.routeInput(player, current, input, true)) {
         return;
      }

      if (!this.canAct(player, action)) {
         this.onInputRefused(player, action);
         return;
      }

      ThorActions.press(player, ThorState.ensure(player), input);
   }

   /** One active Thor (session A №3.5): the first successful choice holds the slot while online. */
   @Override
   public boolean admitsEntry(ServerPlayer player) {
      if (MjolnirWorld.admits(player)) {
         return true;
      }

      ThorFeedback.refuse(player, Component.translatable(MjolnirWorld.SLOT_TAKEN));
      return false;
   }

   /**
    * The hammer is not reset here: the world store claims the slot, creates
    * Mjölnir in the hand when it never existed (the first fall is then
    * cancelled for good) or calls the existing one back after the
    * transformation scene.
    */
   @Override
   public void enter(ServerPlayer player) {
      ThorState state = ThorState.ensure(player);
      state.timeline.clear();
      state.clearTransient();
      state.setMode(ThorMode.NORMAL);
      MjolnirWorld.onEnter(player, state);
      ThorTransform.begin(player, state);
      MjolnirWorld.project(player, state);
      player.displayClientMessage(Component.translatable(ThorFeedback.CONTROLS), false);
   }

   /** The transformation scene protects the new Thor (№215). */
   @Override
   public float clampFinalDamage(ServerPlayer self, DamageSource source, float afterArmor) {
      return ThorTransform.shielded(self) ? 0.0F : afterArmor;
   }

   /** No fall damage while pulled to the flying hammer and shortly after (Y, №220; PLACEHOLDER grace). */
   @Override
   public boolean cancelsFallDamage(Player player) {
      return MjolnirPull.graced(player);
   }

   @Override
   public CombatPresence combatPresence(Player player) {
      // No context(): that scans for enemies; the presence needs the hammer and the running move only.
      ThorMove move = null;
      MovePhase phase = null;
      if (player.level().isClientSide) {
         HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
         if (snapshot != null && snapshot.heroId() == HeroId.THOR) {
            move = ThorMove.byWireId(snapshot.actionId());
            phase = StagedTimeline.phaseOf(snapshot.variant());
         }
      } else {
         ThorState state = ThorState.of(player);
         if (state != null && state.timeline.running()) {
            move = state.timeline.move();
            phase = state.timeline.phase();
         }
      }

      return ThorCombat.presence(hammer(player), ThorHands.free(player), move, phase);
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      ThorState state = ThorState.of(player);
      return composeSnapshot(state == null ? new ThorState() : state, ThorCooldowns.wire(player));
   }

   /** Pure wire composer without cooldowns (all ready). */
   static HeroPublicSnapshot composeSnapshot(ThorState state) {
      return composeSnapshot(state, new int[HeroPublicSnapshot.COOLDOWN_COUNT]);
   }

   /**
    * Pure wire composer: running move as actionId/elapsed/length/variant, the
    * partner as controlTargetId, the session strike pose as resource, the
    * session cooldowns at their ThorCooldowns indices.
    */
   static HeroPublicSnapshot composeSnapshot(ThorState state, int[] cooldowns) {
      var timeline = state.timeline;
      return new HeroPublicSnapshot(HeroId.THOR, timeline.wireId(), timeline.elapsed(), timeline.length(), 0, false, 0, 0, false, false, 0, 0,
         timeline.partnerId(), cooldowns.clone(), timeline.running(), -1, null, timeline.running() ? state.combatPose() : 0,
         false,
         ThorFlags.compose(state.hammer(), state.mode(), ThorBridge.grabWindowActive(state), ThorBridge.cameraLeased(state), state.hammerDetached(), ThorBridge.sceneLocked(state)),
         new int[0], timeline.variant(), 0);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      ThorState state = ThorState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      ThorState.ensure(player).load(nbt);
   }

   /**
    * Every clone (respawn after death, End return): the old body's move and
    * the sessions' transient state are stopped (on a death clone the cleanup
    * already did it; stopping twice is a no-op), and the new body starts as a
    * fresh one that keeps the hammer projection of the old body. Session A's
    * world store stays the truth and re-projects on the first tick.
    */
   @Override
   public void cloneHeroState(Player original, Player clone) {
      ThorState previous = ThorState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         if (original instanceof ServerPlayer old) {
            ThorActions.stop(old, previous, ThorLifecycle.Cause.CLONE);
         }

         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      ThorState state = ThorState.of(player);
      if (state != null) {
         ThorActions.stop(player, state, ThorLifecycle.Cause.DIMENSION_CHANGE);
         MjolnirPull.stop(player);
         MjolnirWorld.onDimensionChange(player, state);
      }
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      ThorState state = ThorState.of(player);
      if (state == null) {
         return;
      }

      ThorActions.stop(player, state, ThorLifecycle.Cause.of(reason));
      MjolnirPull.stop(player);
      ThorTransform.stop(player, state);
      MjolnirWorld.onCleanup(player, state, reason);
      if (reason == CleanupReason.ERASED && player instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(state.erasedCopy());
      }
   }
}
