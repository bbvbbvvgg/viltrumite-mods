package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Regulus hero: server-authoritative lifecycle and input. Abilities live in
 * dedicated classes; this class owns the action lock, the shared rules and the
 * dispatch on validated input edges.
 */
public class RegulusHero implements HeroDefinition {
   public static final String ACTION_LION = "lion";
   public static final String ACTION_DEBRIS = "debris";
   public static final String ACTION_MANIA = "mania";
   public static final String ACTION_EMBRACE = "embrace";
   public static final String ACTION_COUNTER = "counter";
   public static final String ACTION_RITUAL = "ritual";

   @Nullable
   public static RegulusState stateOf(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.REGULUS) {
         Object state = heroPlayer.viltrumitecore$getHeroState();
         return state instanceof RegulusState regulus ? regulus : null;
      }

      return null;
   }

   /** Lion's Heart is absolute invulnerability (spec Reinhard §3.3). */
   @Override
   public boolean isAbsolutelyInvulnerable(Player self) {
      return absolutelyInvulnerable(stateOf(self));
   }

   public static boolean absolutelyInvulnerable(@Nullable RegulusState state) {
      return state != null && state.lionActive;
   }

   public static RegulusState ensureState(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      Object state = heroPlayer.viltrumitecore$getHeroState();
      if (!(state instanceof RegulusState regulus)) {
         RegulusState created = new RegulusState();
         heroPlayer.viltrumitecore$setHeroState(created);
         return created;
      } else {
         return regulus;
      }
   }

   @Override
   public HeroId id() {
      return HeroId.REGULUS;
   }

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
   public boolean ownsAbility(String abilityId) {
      for (String id : RegulusAbilities.slotIds()) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String[] heroInputSlots() {
      return RegulusAbilities.slotIds();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return RegulusAbilities.actionFor(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      String[] loadout = new String[18];
      String[] slots = RegulusAbilities.slotIds();
      System.arraycopy(slots, 0, loadout, 0, slots.length);
      return loadout;
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, dev.baranhan.viltrumitecore.hero.control.ControlKind kind) {
      RegulusState state = target instanceof Player player ? stateOf(player) : null;
      // An active Lion's Heart makes the hero immune to all control kinds.
      return state == null || !state.lionActive;
   }

   @Override
   public float meleeDamageFactor(Player player) {
      RegulusState state = stateOf(player);
      return state == null ? 1.0F : RegulusRules.heartBonus(state.hearts());
   }

   @Override
   public boolean preventsExhaustion(Player player) {
      RegulusState state = stateOf(player);
      return state != null && state.lionActive;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      RegulusState state = ensureState(player);
      if (HeroDamage.isAnchored(player)) {
         return false;
      }

      // A Mania channel grounds the caster (spec 8.2): no super jump.
      if (action == HeroAction.JUMP && state.channelTargetId != null) {
         return false;
      }

      return actionPermitted(action, state.lionActive, state.busy(), state.channelTargetId != null, state.ritualTicks >= 0, state.madnessTicksLeft > 0);
   }

   /**
    * §6.2/§10-11/§16 input policy — pure so JUnit covers the whole matrix.
    * Jump always passes; Lion leaves only the off-toggle; a busy cast, an
    * open channel or a running ritual locks every slot; the Counter is a
    * madness-only slot and the ritual cannot re-enter mid-madness.
    */
   static boolean actionPermitted(HeroAction action, boolean lionActive, boolean busy, boolean channelOpen, boolean ritualActive, boolean madnessActive) {
      if (action == HeroAction.JUMP) {
         return true;
      }

      if (lionActive) {
         return action == HeroAction.LIONS_HEART;
      }

      if (busy || channelOpen || ritualActive) {
         return false;
      }

      if (action == HeroAction.COUNTER) {
         return madnessActive;
      }

      if (action == HeroAction.RITUAL) {
         return !madnessActive;
      }

      return true;
   }

   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      RegulusState state = stateOf(player);
      if (state != null) {
         RegulusMovement.onLanding(player, state, fallDistance);
      }
   }

   @Override
   public float superJumpVelocity(Player player) {
      return RegulusRules.SUPER_JUMP_VELOCITY;
   }

   @Override
   public void onSuperJump(ServerPlayer player) {
      RegulusMovement.onSuperJump(player);
   }

   /** Heart key (MIDDLE = the heart key mapping, default MMB) beats vanilla pick-block. */
   @Override
   public HeroAction mouseAction(dev.baranhan.viltrumitecore.hero.MouseButton button, Player player) {
      return button == dev.baranhan.viltrumitecore.hero.MouseButton.MIDDLE ? HeroAction.ASSIGN_HEART : null;
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      if (action == HeroAction.ASSIGN_HEART) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viltrumitecore.hearts.locked"), true);
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      RegulusState state = ensureState(player);
      if (pressed) {
         this.tryStart(player, state, action);
      } else {
         this.tryRelease(player, state, action);
      }
   }

   private void tryStart(ServerPlayer player, RegulusState state, HeroAction action) {
      if (action == HeroAction.ASSIGN_HEART) {
         if (player.isAlive() && this.canAct(player, action)) {
            RegulusHearts.assignLookedAt(player, state);
         } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viltrumitecore.hearts.locked"), true);
         }
         return;
      }
      if (!this.canAct(player, action)) {
         return;
      }

      String abilityId = RegulusAbilities.abilityFor(action);
      // The Lion off-toggle is not a new cast: it reaches the running state
      // even when the slot was re-equipped or the page changed mid-run, so the
      // player can always turn Lion off with a second press (spec 6.4).
      boolean lionOffToggle = action == HeroAction.LIONS_HEART && state.lionActive;
      boolean equipped = abilityId != null && isEquippedOnActivePage(player, abilityId);
      boolean onCooldown = abilityId != null && state.cooldownOf(abilityId) > 0;
      if (abilityId == null || !RegulusRules.mayStartAbility(equipped, onCooldown, lionOffToggle)) {
         return;
      }

      switch (action) {
         case LIONS_HEART -> LionsHeart.toggle(player, state);
         case DEBRIS_KICK -> DebrisKick.start(player, state);
         case MANIA -> Mania.start(player, state);
         case GREEDS_EMBRACE -> GreedsEmbrace.start(player, state);
         case COUNTER -> Counter.start(player, state);
         default -> {
         }
      }
   }

   private void tryRelease(ServerPlayer player, RegulusState state, HeroAction action) {
      // Releases address the server's active cast, even if the client swapped
      // pages or the slot contents changed mid-press.
      if (action != HeroAction.MANIA) {
         return;
      }

      if (state.channelTargetId != null) {
         Mania.endChannel(player, state, ReleaseReason.NORMAL_END);
      } else if (ACTION_MANIA.equals(state.actionId) && !state.eventFired) {
         // Releasing during the windup cancels the cast for free (spec 8.1).
         state.clearAction();
      }
   }

   /**
    * The fist joined the kit after release: an existing loadout with an empty
    * sixth slot (the old heart key) gets the punch there once.
    */
   private static void ensurePunchSlot(ServerPlayer player) {
      if (!(player instanceof ViltrumiteAbilityUser abilityUser)) {
         return;
      }

      String current = abilityUser.getAbilityInSlot(5);
      if (current == null || current.isEmpty()) {
         abilityUser.setAbilityInSlot(5, RegulusAbilities.PUNCH);
      }
   }

   /** The fist uses the free-cast policy of a normal ability (no Lion, cast, channel or ritual). */
   public static boolean canPunch(ServerPlayer player) {
      RegulusState state = stateOf(player);
      if (state == null || HeroDamage.isAnchored(player)) {
         return false;
      }

      return actionPermitted(HeroAction.DEBRIS_KICK, state.lionActive, state.busy(), state.channelTargetId != null, state.ritualTicks >= 0, state.madnessTicksLeft > 0);
   }

   public static boolean punchEquipped(ServerPlayer player) {
      return isEquippedOnActivePage(player, RegulusAbilities.PUNCH);
   }

   private static boolean isEquippedOnActivePage(Player player, String abilityId) {
      if (!(player instanceof ViltrumiteAbilityUser abilityUser)) {
         return false;
      }

      int base = abilityUser.getActivePage() * 6;
      for (int slot = base; slot < base + 6; slot++) {
         if (abilityId.equals(abilityUser.getAbilityInSlot(slot))) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void enter(ServerPlayer player) {
      RegulusState state = ensureState(player);
      RegulusPassives.apply(player);
      player.setHealth(player.getMaxHealth());
      Evangelium.grant(player);
      state.lastSeenHealth = player.getHealth();
   }

   @Override
   public void tick(ServerPlayer player) {
      RegulusState state = ensureState(player);
      // Madness: the authority runs wild — every cooldown runs at double speed.
      int cooldownRate = state.madnessTicksLeft > 0 ? RegulusRules.MADNESS_COOLDOWN_RATE : 1;
      for (int i = 0; i < cooldownRate; i++) {
         state.tickCooldowns();
      }
      ensurePunchSlot(player);
      this.tickActionLock(player, state);
      this.tickDamageBookkeeping(player, state);
      // Base passives are transient modifiers — vanilla never serializes them,
      // so relogin/respawn strips them; apply() is idempotent and re-adds them
      // every tick (spec §4: always-on while Regulus).
      RegulusPassives.apply(player);
      RegulusMovement.tick(player, state);
      RegulusHearts.tick(player, state);
      LionsHeart.tick(player, state);
      DebrisKick.tick(player, state);
      Mania.tick(player, state);
      GreedsEmbrace.tick(player, state);
      Counter.tick(player, state);
      Evangelium.tick(player, state);
   }

   private void tickActionLock(ServerPlayer player, RegulusState state) {
      if (state.actionId == null) {
         return;
      }

      state.actionElapsed++;
      if (state.actionElapsed >= state.actionLength) {
         state.clearAction();
      }
   }

   /**
    * Actual EXTERNAL HP loss interrupts pre-event casts; self-inflicted
    * internal drains (blood price, backlash, overheat) and blocked/queued hits
    * never count as interrupting hits (spec 7.1/10.4/11.1).
    */
   private void tickDamageBookkeeping(ServerPlayer player, RegulusState state) {
      float health = player.getHealth();
      if (state.lastSeenHealth < 0.0F) {
         state.lastSeenHealth = health;
         state.internalDamage = 0.0F;
         return;
      }

      float lost = RegulusRules.externalHealthLoss(state.lastSeenHealth, health, state.internalDamage);
      state.lastSeenHealth = health;
      state.internalDamage = 0.0F;

      if (lost <= 0.0F) {
         return;
      }

      // A real hit before the action's event cancels the cast for free.
      // The Counter is the answer to a hit: it can never be knocked out of.
      if (state.busy() && !state.eventFired && !ACTION_COUNTER.equals(state.actionId)) {
         state.clearAction();
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      RegulusState state = stateOf(player);
      if (state != null) {
         state.saveHeroData(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      ensureState(player).loadHeroData(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      // Death hands forward duration bookkeeping only: cooldowns carry over,
      // combat state dies with the body (a2/a3 audit finding).
      RegulusState previous = stateOf(original);
      if (previous != null) {
         ensureState(clone).inheritPersistent(previous);
      }
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      RegulusState state = stateOf(player);
      if (state == null) {
         return;
      }

      if (player.level() instanceof ServerLevel level) {
         ControlManager.get(level).cleanupCaster(player.getUUID(), reason);
      }

      // Cleanup reasons map 1:1 onto release reasons so a death or disconnect
      // never freezes the victim (spec 8.2: only a normal end freezes).
      Mania.endChannel(player, state, ControlManager.cleanupReleaseReason(reason));
      LionsHeart.forceOff(player, state);
      RegulusHearts.releaseAll(player, state, reason);
      GreedsEmbrace.cleanup(player, state, reason);
      Evangelium.cleanup(player, state, reason);
      state.resetTransient(reason);
      if (reason == CleanupReason.ERASED && player instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(state.erasedCopy());
      }

      if (reason == CleanupReason.HERO_CHANGE) {
         RegulusPassives.remove(player);
      }
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      RegulusState state = stateOf(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.REGULUS, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[6], false);
      }

      int[] cooldowns = new int[6];
      String[] ids = {RegulusAbilities.LIONS_HEART, RegulusAbilities.DEBRIS_KICK, RegulusAbilities.MANIA, RegulusAbilities.GREEDS_EMBRACE, RegulusAbilities.COUNTER, RegulusAbilities.EVANGELIUM};
      for (int i = 0; i < ids.length; i++) {
         cooldowns[i] = state.cooldownOf(ids[i]);
      }

      int controlTargetId = -1;
      if (state.channelTargetId != null && player.level() instanceof ServerLevel level) {
         Entity target = level.getEntity(state.channelTargetId);
         if (target != null) {
            controlTargetId = target.getId();
         }
      }

      int actionId = state.actionId == null ? -1 : actionOrdinalOf(state.actionId);
      int windowLeft = state.lionActive ? Math.max(0, state.lionWindowMax - state.lionElapsed) : 0;
      return new HeroPublicSnapshot(
         HeroId.REGULUS,
         actionId,
         state.actionElapsed,
         state.actionLength,
         state.hearts(),
         state.lionActive,
         windowLeft,
         state.lionWindowMax,
         state.lionElapsed > state.lionWindowMax,
         state.madnessTicksLeft > 0,
         state.madnessTicksLeft,
         state.ritualTicks,
         controlTargetId,
         cooldowns,
         state.busy(),
         -1,
         state.actionPoint
      );
   }

   private static int actionOrdinalOf(String actionId) {
      return switch (actionId) {
         case ACTION_LION -> HeroAction.LIONS_HEART.ordinal();
         case ACTION_DEBRIS -> HeroAction.DEBRIS_KICK.ordinal();
         case ACTION_MANIA -> HeroAction.MANIA.ordinal();
         case ACTION_EMBRACE -> HeroAction.GREEDS_EMBRACE.ordinal();
         case ACTION_COUNTER -> HeroAction.COUNTER.ordinal();
         default -> HeroAction.RITUAL.ordinal();
      };
   }

   @Nullable
   public static UUID channelTarget(ServerPlayer player) {
      RegulusState state = stateOf(player);
      return state == null ? null : state.channelTargetId;
   }

   /** Scan: Lion's Heart blocks every external hit (the same state HeroDamage.decide reads). */
   @Override
   public dev.baranhan.viltrumitecore.hero.ScanInfo scanInfo(Player self) {
      RegulusState state = stateOf(self);
      return scanInfoFor(state != null && state.lionActive);
   }

   public static dev.baranhan.viltrumitecore.hero.ScanInfo scanInfoFor(boolean lionActive) {
      if (!lionActive) {
         return dev.baranhan.viltrumitecore.hero.ScanInfo.EMPTY;
      }

      return new dev.baranhan.viltrumitecore.hero.ScanInfo(java.util.List.of(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.regulus.lion_heart")),
         java.util.List.of(), java.util.List.of());
   }
}
