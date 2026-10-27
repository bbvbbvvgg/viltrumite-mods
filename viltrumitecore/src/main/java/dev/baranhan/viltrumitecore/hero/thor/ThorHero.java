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
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPull;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirWorld;
import dev.baranhan.viltrumitecore.hero.thor.hammer.ThorTransform;
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
 * session registers it. No kit, no flight, vanilla chassis. Session A: the
 * one world Mjölnir (hero/thor/hammer) is entered, projected, dropped and
 * claimed from the lifecycle hooks below.
 */
public final class ThorHero implements HeroDefinition {
   public static final ThorHero INSTANCE = new ThorHero();

   private ThorHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.THOR;
   }

   /** No flight in the foundation. */
   @Override
   public boolean allowsFlight(Player player) {
      return false;
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

   /** LMB on a block always mines, also once hammer strikes ship. */
   @Override
   public boolean claimsBlockAttack(Player player) {
      return false;
   }

   /** RMB on a block stays vanilla (use / place / open); RMB in the air feeds the combos (on a lying Mjölnir: OPEN). */
   @Override
   public boolean claimsBlockUse(Player player) {
      return false;
   }

   /** Both sides: only buttons whose context move is authored leave vanilla (none in the foundation). */
   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return ThorControls.claimedMouse(button, context(player));
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
            hammer = state.hammer;
            mode = state.mode;
            grabWindow = state.grabWindow > 0;
            detached = state.hammerDetached;
         }
      }

      boolean enemyNear = ThorTargets.nearestEnemy(player, ThorRules.MELEE_ENEMY_RADIUS) != null;
      return new ThorContext(hammer, mode, ThorHands.free(player), player.isShiftKeyDown(), !player.onGround(), false, enemyNear, grabWindow, detached, false);
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
      state.mode = ThorMode.NORMAL;
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
      return ThorCombat.presence(context(player).hammer(), ThorHands.free(player));
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      ThorState state = ThorState.of(player);
      return composeSnapshot(state == null ? new ThorState() : state);
   }

   /** Pure wire composer: running move as actionId/elapsed/length/variant, the partner as controlTargetId, the session strike pose as resource. */
   static HeroPublicSnapshot composeSnapshot(ThorState state) {
      var timeline = state.timeline;
      return new HeroPublicSnapshot(HeroId.THOR, timeline.wireId(), timeline.elapsed(), timeline.length(), 0, false, 0, 0, false, false, 0, 0,
         timeline.partnerId(), new int[HeroPublicSnapshot.COOLDOWN_COUNT], timeline.running(), -1, null, timeline.running() ? state.combatPose : 0,
         false,
         ThorFlags.compose(state.hammer, state.mode, state.grabWindow > 0, state.cameraLease != null, state.hammerDetached), new int[0],
         timeline.variant(), 0);
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

   @Override
   public void cloneHeroState(Player original, Player clone) {
      ThorState previous = ThorState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      ThorState state = ThorState.of(player);
      if (state != null) {
         ThorActions.stop(player, state);
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

      ThorActions.stop(player, state);
      MjolnirPull.stop(player);
      ThorTransform.stop(player, state);
      MjolnirWorld.onCleanup(player, state, reason);
      if (reason == CleanupReason.ERASED && player instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(state.erasedCopy());
      }
   }
}
