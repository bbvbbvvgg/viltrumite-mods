package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Tony Stark (spec docs/design/2026-10-09-ironman-design.md). No Viltrumite
 * kit: everything comes from the suit.
 */
public class IronManHero implements HeroDefinition {
   private static final UUID ARMOR_ID = UUID.fromString("7c3e9a51-2f6d-4b8e-a1c4-3d5f6e7a8b01");
   private static final UUID TOUGHNESS_ID = UUID.fromString("7c3e9a51-2f6d-4b8e-a1c4-3d5f6e7a8b02");
   private static final UUID KNOCKBACK_ID = UUID.fromString("7c3e9a51-2f6d-4b8e-a1c4-3d5f6e7a8b03");
   private static final UUID HEALTH_ID = UUID.fromString("7c3e9a51-2f6d-4b8e-a1c4-3d5f6e7a8b04");

   @Override
   public HeroId id() {
      return HeroId.IRON_MAN;
   }

   /**
    * Mod flight only while the suit is fully worn. Read on BOTH sides (the
    * flight mixin and input check the local player): the client reads the
    * synced snapshot flag, the server its own state.
    */
   @Override
   public boolean allowsFlight(Player player) {
      if (player != null && player.level().isClientSide()) {
         return suitWornFlag(player) && hulkPhase(player) == 0;
      }

      IronManState state = IronManState.of(player);
      return state != null && state.wantsFlight();
   }

   /** Synced Hulkbuster phase (HulkbusterLayer.Phase ordinal) of an Iron Man snapshot, 0 otherwise. */
   static int hulkPhase(Player player) {
      if (!(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) || heroPlayer.getHeroSnapshot().heroId() != HeroId.IRON_MAN) {
         return 0;
      }

      return IronManFlags.get(heroPlayer.getHeroSnapshot().heroFlags(), IronManFlags.Field.HULKBUSTER_PHASE);
   }

   /** Hulkbuster Mark 48 body (rework spec §2): 1.3 x 3.2 from the torso closing to the end of the exit. Both sides. */
   @Override
   public float bodyScale(Player player) {
      return hulkBig(player) ? dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules.HEIGHT_SCALE : 1.0F;
   }

   @Override
   public float bodyWidthScale(Player player) {
      return hulkBig(player) ? dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules.WIDTH_SCALE : 1.0F;
   }

   /** The 3.2-block suit walks up one block (step 0.6 -> 1.0), like Thor's body. Both sides. */
   @Override
   public double bodyStepBonus(Player player) {
      return hulkBig(player) ? 0.4 : 0.0;
   }

   static boolean hulkBig(Player player) {
      if (player == null) {
         return false;
      }

      if (player.level().isClientSide()) {
         int phase = hulkPhase(player);
         if (phase == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ACTIVE.ordinal() || phase == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.EXITING.ordinal()) {
            return true;
         }

         if (phase != dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ASSEMBLING.ordinal() || !(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
            return false;
         }

         int[] extra = heroPlayer.getHeroSnapshot().extraCooldowns();
         int scene = extra.length > dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.SCENE ? extra[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.SCENE] : 0;
         return dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.sceneAssemble(scene) >= dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.TORSO_TICK;
      }

      IronManState state = IronManState.of(player);
      return state != null && state.hulkbuster.big();
   }

   /** The giant's own thrusters keep it up during jumps, hover, the scene and the ultimate (no fly kick). */
   @Override
   public boolean sustainsAirborne(Player player) {
      IronManState state = player.level().isClientSide() ? null : IronManState.of(player);
      return state != null && state.hulkbuster.present();
   }

   /** The Mark 48 in combat (tags body.mech.heavy, weapon.fist.jackhammer, trait.armored); otherwise the suit's own presence. */
   @Override
   public dev.baranhan.viltrumitecore.hero.combat.CombatPresence combatPresence(Player player) {
      IronManState state = player.level().isClientSide() ? null : IronManState.of(player);
      if (state != null && state.hulkbuster.active()) {
         return dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterTags.presence(dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCombat.blocking(state), state.hulkKit.timeline.running());
      }

      return dev.baranhan.viltrumitecore.hero.HeroDefinition.super.combatPresence(player);
   }

   /** Mouse-held victim of the Hulkbuster (grab or the ultimate's captive): the shared held-target seam. */
   @Override
   public int heldTargetId(Player player) {
      if (player.level().isClientSide()) {
         if (hulkPhase(player) != dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ACTIVE.ordinal() || !(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
            return -1;
         }

         int[] extra = heroPlayer.getHeroSnapshot().extraCooldowns();
         return extra.length > dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.VICTIM ? extra[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.VICTIM] - 1 : -1;
      }

      IronManState state = IronManState.of(player);
      if (state == null || !state.hulkbuster.active()) {
         return -1;
      }

      return state.hulkKit.grab.active() ? state.hulkKit.grab.victimId : state.hulkKit.ultTarget;
   }

   @Override
   public net.minecraft.world.phys.Vec3 heldTargetPosition(Player player, net.minecraft.world.entity.Entity target, float partialTick) {
      return dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCombat.anchor(player.getPosition(partialTick), net.minecraft.util.Mth.rotLerp(partialTick, player.yRotO, player.getYRot()),
         target.getBbWidth(), target.getBbHeight());
   }

   @Override
   public boolean grantsFlightAbility(Player player) {
      IronManState state = IronManState.of(player);
      return state != null && state.wantsFlight();
   }

   /**
    * Normal flight is the original (Homelander) flight: no profile. Only the
    * 0-energy glide uses a profile. Both sides: the client reads the synced
    * flags (≤ 1 tick behind).
    */
   @Override
   public dev.baranhan.viltrumiteflight.util.FlightProfile flightProfile(Player player) {
      if (player == null) {
         return null;
      }

      if (player.level().isClientSide()) {
         return profileFor(suitWornFlag(player), glideFlag(player));
      }

      IronManState state = IronManState.of(player);
      return state == null ? null : profileFor(state.wantsFlight(), state.glide);
   }

   @javax.annotation.Nullable
   static dev.baranhan.viltrumiteflight.util.FlightProfile profileFor(boolean worn, boolean glide) {
      return worn && glide ? IronManRules.glideProfile() : null;
   }

   static boolean glideFlag(Player player) {
      return player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
         && heroPlayer.getHeroSnapshot().heroId() == HeroId.IRON_MAN
         && IronManFlags.is(heroPlayer.getHeroSnapshot().heroFlags(), IronManFlags.Field.GLIDE);
   }

   /** Synced SUIT_WORN bit of an Iron Man snapshot (any side). */
   static boolean suitWornFlag(Player player) {
      if (!(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return snapshot.heroId() == HeroId.IRON_MAN && IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.SUIT_WORN);
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbility(Player player, String abilityId) {
      return false;
   }

   @Override
   public boolean hasAbilityPanel(Player player) {
      return true;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return true;
   }

   /** No fall damage while armored, including the retract wave (spec §8.6) and moving mark plates. */
   @Override
   public boolean cancelsFallDamage(Player player) {
      IronManState state = IronManState.of(player);
      return state != null && (state.suit.armored() || state.suit.equipping() || state.suit.exiting() || state.hulkbuster.present());
   }

   /** Mark flight speed (spec §13: Starboost ×1.35, War Machine / Iron Heart ×0.8, Mark 42 lost legs). Both sides. */
   @Override
   public float flightSpeedScale(Player player) {
      if (player != null && player.level().isClientSide()) {
         if (!(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) || heroPlayer.getHeroSnapshot().heroId() != HeroId.IRON_MAN) {
            return 1.0F;
         }

         int variant = heroPlayer.getHeroSnapshot().variant();
         dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = IronManVariant.mark(variant);
         if (mark == null) {
            return 1.0F;
         }

         int lost = mark == dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId.MARK_42
            ? dev.baranhan.viltrumitecore.hero.ironman.mark.SuitPart.fullMask(mark) & ~IronManVariant.parts(variant) : 0;
         return dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec.mark(mark, lost).flightSpeedMul();
      }

      IronManState state = IronManState.of(player);
      return state == null ? 1.0F : state.spec().flightSpeedMul();
   }

   /** Mark 15 passive (spec §13.4): not seen by focus or scans while it is on. */
   @Override
   public boolean hiddenFromScan(Player self) {
      IronManState state = IronManState.of(self);
      return state != null && state.spec().stealth();
   }

   @Override
   public boolean hiddenFromFocus(Player self) {
      return this.hiddenFromScan(self);
   }

   /** Mark weapon factor and the signature's per-hit factor (Mark 15 first hit from camo). */
   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
      IronManState state = IronManState.of(attacker);
      if (state == null || !state.suit.markWorn()) {
         return amount;
      }

      return amount * state.spec().weaponMul()
         * dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignatures.of(state.suit.mark()).outgoingFactor(attacker, state, target);
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return IronManAbilities.owns(abilityId);
   }

   @Override
   public java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return IronManAbilities.panelAbilities();
   }

   @Override
   public net.minecraft.resources.ResourceLocation abilityIcon(String abilityId) {
      return IronManAbilities.icon(abilityId);
   }

   @Override
   public net.minecraft.resources.ResourceLocation abilityIcon(String abilityId, Player player) {
      return IronManAbilities.icon(abilityId, player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null);
   }

   @Override
   public String[] heroInputSlots() {
      return IronManAbilities.slotIds();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return IronManAbilities.actionFor(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return IronManAbilities.defaultLoadout();
   }

   @Override
   public String[][] previousDefaultLoadouts() {
      return IronManAbilities.previousDefaultLoadouts();
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   /** Control (spec §16): nothing starts while anchored or dead. */
   public static boolean controlled(ServerPlayer player) {
      return HeroDamage.isAnchored(player) || !player.isAlive() || player.isSpectator();
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (controlled(player)) {
         return false;
      }

      IronManState hulkState = IronManState.of(player);
      if (hulkState != null && hulkState.enteringSuitId >= 0) {
         // Walking into the empty suit: the entry plays out, nothing else starts (spec §12.6).
         return false;
      }

      if (hulkState != null && hulkState.hulkbuster.busy() && action != HeroAction.SUIT) {
         // Parts assembling or climbing out: rooted, nothing else starts (plan stage 5 Task 5).
         return false;
      }

      if (hulkState != null && hulkState.hulkbuster.active()) {
         return action != HeroAction.HELMET && action != HeroAction.INTERACT;
      }

      return switch (action) {
         case SUIT, VERONICA -> true;
         case SECONDARY_USE, TOOL_CYCLE, UNIBEAM, MISSILES, NANO_ARSENAL, GUARD, HELMET, SCAN, COUNTERMEASURES -> {
            IronManState state = IronManState.of(player);
            yield state != null && state.suit.worn();
         }
         case INTERACT -> true;
         // Same gate as the claim: a forged HeroInputC2SPacket cannot punch on foot.
         case PRIMARY_ATTACK -> {
            IronManState state = IronManState.of(player);
            yield state != null && (claimsPrimary(state.suit.worn(), flightState(player)) || claimsWeapon(state.suit.worn(), state.arsenal.weapon() != null));
         }
         default -> false;
      };
   }

   /**
    * LMB: in flight (fly-by / air strike, spec §6.1) or with a formed nano
    * weapon; otherwise vanilla (nano punch via meleeDamageFactor). RMB in the
    * suit: hero interaction on a HeroInteractable target, else the current
    * RMB tool; Shift+RMB stays vanilla (blocks, doors). MMB cycles the tool.
    */
   @Override
   public HeroAction mouseAction(dev.baranhan.viltrumitecore.hero.MouseButton button, Player player) {
      if (player == null) {
         return null;
      }

      boolean worn;
      boolean weapon;
      if (player.level().isClientSide()) {
         HeroPublicSnapshot snapshot = ((dev.baranhan.viltrumitecore.hero.HeroPlayer)player).getHeroSnapshot();
         worn = suitWornFlag(player);
         weapon = worn && dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.byId(
            IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.RMB_TOOL)).nanoWeapon();
      } else {
         IronManState state = IronManState.of(player);
         worn = state != null && state.suit.worn();
         weapon = worn && state.arsenal.weapon() != null;
      }

      boolean hulk = player.level().isClientSide()
         ? hulkPhase(player) == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ACTIVE.ordinal()
         : IronManState.of(player) != null && IronManState.of(player).hulkbuster.active();
      if (hulk) {
         return switch (button) {
            case PRIMARY -> HeroAction.PRIMARY_ATTACK;
            case SECONDARY -> player.isShiftKeyDown() ? null : HeroAction.SECONDARY_USE;
            default -> null;
         };
      }

      return switch (button) {
         case PRIMARY -> claimsPrimary(worn, flightState(player)) || claimsWeapon(worn, weapon) ? HeroAction.PRIMARY_ATTACK : null;
         case SECONDARY -> {
            boolean shift = player.isShiftKeyDown();
            boolean interact = !shift && interactTarget(player) != null;
            // An item in either hand (food, blocks, buckets...) keeps vanilla RMB; the drawn blade has no RMB.
            if (!interact && (handsBusy(player) || rightTool(player) == dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.NANO_BLADE)) {
               yield null;
            }

            yield secondaryAction(worn, shift, interact);
         }
         case MIDDLE -> worn ? HeroAction.TOOL_CYCLE : null;
         default -> null;
      };
   }

   /** Vanilla use wins while the player holds anything (both sides read the synced hand items). */
   static boolean handsBusy(Player player) {
      return !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
   }

   /** Current RMB tool on either side: the synced flag on the client, the state on the server. */
   static dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool rightTool(Player player) {
      if (player.level().isClientSide()) {
         HeroPublicSnapshot snapshot = ((dev.baranhan.viltrumitecore.hero.HeroPlayer)player).getHeroSnapshot();
         return dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.byId(IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.RMB_TOOL));
      }

      IronManState state = IronManState.of(player);
      return state == null ? dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.REPULSOR : state.rightTool;
   }

   /**
    * RMB: the hero interaction (own empty suit) works without armor too
    * (spec §12.6); the RMB tool needs the suit. Shift+RMB stays vanilla.
    */
   static HeroAction secondaryAction(boolean worn, boolean shift, boolean interactTarget) {
      if (shift) {
         return null;
      }

      if (interactTarget) {
         return HeroAction.INTERACT;
      }

      return worn ? HeroAction.SECONDARY_USE : null;
   }

   static boolean claimsWeapon(boolean worn, boolean weaponFormed) {
      return worn && weaponFormed;
   }

   /** Entity under the crosshair (reach 4) that offers a hero interaction. */
   @javax.annotation.Nullable
   static dev.baranhan.viltrumitecore.entity.HeroInteractable interactTarget(Player player) {
      net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
      net.minecraft.world.phys.Vec3 end = eye.add(player.getLookAngle().scale(4.0));
      net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
         new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
         e -> e instanceof dev.baranhan.viltrumitecore.entity.HeroInteractable interactable && interactable.canHeroInteract(player), 16.0);
      return hit != null && hit.getEntity() instanceof dev.baranhan.viltrumitecore.entity.HeroInteractable interactable ? interactable : null;
   }

   /** F (guard key) raises the shield while any suit is on; the vanilla swap is blocked (spec §6.1, §8.5). */
   @Override
   public HeroAction guardAction(Player player) {
      return suitPresent(player) ? HeroAction.GUARD : null;
   }

   @Override
   public boolean blocksHandSwap(Player player) {
      return suitPresent(player);
   }

   static boolean suitPresent(Player player) {
      if (player == null) {
         return false;
      }

      if (player.level().isClientSide()) {
         return suitWornFlag(player) || player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
            && heroPlayer.getHeroSnapshot().heroId() == HeroId.IRON_MAN
            && (IronManFlags.is(heroPlayer.getHeroSnapshot().heroFlags(), IronManFlags.Field.DEPLOYING)
               || IronManFlags.is(heroPlayer.getHeroSnapshot().heroFlags(), IronManFlags.Field.RETRACTING));
      }

      IronManState state = IronManState.of(player);
      return state != null && state.suit.state() != SuitState.NONE;
   }

   static boolean claimsPrimary(boolean worn, @javax.annotation.Nullable dev.baranhan.viltrumiteflight.util.FlightState state) {
      return worn && state != null && state != dev.baranhan.viltrumiteflight.util.FlightState.NONE;
   }

   @javax.annotation.Nullable
   static dev.baranhan.viltrumiteflight.util.FlightState flightState(Player player) {
      return player instanceof dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer flightPlayer ? flightPlayer.getFlightState() : null;
   }

   /**
    * Flight energy per tick (spec §5.1, §5.3): HOVER/CRUISE drain, SONIC
    * drains more, grounded regen (Energy.tick after its delay). At 0 the
    * flight turns into a glide (no thrust, no drain) until the bar is back
    * at WEAPONS_UNLOCK or the player lands.
    */
   static void flightEnergyTick(IronManState state, @javax.annotation.Nullable dev.baranhan.viltrumiteflight.util.FlightState flight) {
      flightEnergyTick(state, flight, 1.0F);
   }

   /** @param regenScale foreign slow-down of the regen only (HeroResourceRegen); drain is never scaled */
   static void flightEnergyTick(IronManState state, @javax.annotation.Nullable dev.baranhan.viltrumiteflight.util.FlightState flight, float regenScale) {
      boolean flying = state.suit.worn() && flight != null && flight != dev.baranhan.viltrumiteflight.util.FlightState.NONE;
      if (!flying) {
         state.glide = false;
      } else if (state.glide) {
         if (state.energy.value() >= IronManRules.WEAPONS_UNLOCK) {
            state.glide = false;
         }
      } else {
         state.energy.drain(switch (flight) {
            case SONIC -> IronManRules.DRAIN_SONIC * state.spec().sonicDrainMul();
            case CRUISE -> IronManRules.DRAIN_CRUISE;
            default -> IronManRules.DRAIN_HOVER;
         });
         state.glide = state.energy.empty();
      }

      state.energy.tick(regenScale);
   }

   /** Fall without flight (e.g. after the suit's flight ended mid-air): real fall distance. */
   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      IronManState state = IronManState.of(player);
      if (state != null && state.suit.armored() && fallDistance >= IronManRules.HEAVY_FALL
         && player.level().getGameTime() - state.landedAt > 1L) {
         IronManLandings.heavy(player, state, fallDistance);
      }
   }

   /** Nano punch on the ground (server-side melee). */
   @Override
   public float meleeDamageFactor(Player player) {
      IronManState state = IronManState.of(player);
      return state != null && state.suit.worn() ? IronManRules.NANO_MELEE_FACTOR : 1.0F;
   }

   @Override
   public void tick(ServerPlayer player) {
      IronManState state = IronManState.ensure(player);
      dev.baranhan.viltrumitecore.hero.HeroPassiveEffect.keep(player, net.minecraft.world.effect.MobEffects.REGENERATION, IronManRules.PASSIVE_REGEN_AMPLIFIER);
      boolean controlled = controlled(player);
      if (controlled) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.onControl(player, state);
      }

      dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.onSuitEvent(player, state, state.suit.tick());
      dev.baranhan.viltrumiteflight.util.FlightState flight = flightState(player);
      flightEnergyTick(state, flight, dev.baranhan.viltrumitecore.hero.HeroResourceRegen.scale(player));
      syncArmor(player, state.suit.armored(), state.spec());
      state.airStrike.tick();
      if (state.heavyPoseTicks > 0) {
         state.heavyPoseTicks--;
      }

      net.minecraft.world.phys.Vec3 velocity = FlyBy.velocityOf(player);
      LandingKind kind = state.landing.tick(player.onGround(), velocity.length(), velocity.y, flight, state.airStrike.armed());
      long now = player.level().getGameTime();
      // onLanded (packet-time fall event) may already have played this touchdown.
      if (kind != LandingKind.NONE && state.suit.armored() && now - state.landedAt > 1L) {
         IronManLandings.land(player, state, kind, state.landing.impactSpeed());
      }
      if (!state.suit.worn()) {
         // Suit off / wave: every held mouse action ends without a shot (spec §16).
         dev.baranhan.viltrumitecore.hero.HeldInputs.releaseAll(player);
         IronManCombat.suitGone(player, state);
      }

      IronManCombat.tick(player, state, controlled(player));
      IronManJarvis.tick(player, state);
      dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.tick(player, state, controlled);
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.tick(player, state, controlled);
      dev.baranhan.viltrumitecore.hero.ironman.veronica.IronManVeronica.tick(player, state);

      if (state.flyByCooldown > 0) {
         state.flyByCooldown--;
      }

      if (state.suit.worn() && !controlled(player) && flightState(player) == dev.baranhan.viltrumiteflight.util.FlightState.SONIC) {
         SonicRam.tick(player, state);
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      IronManState state = IronManState.ensure(player);
      if (!pressed) {
         release(player, state, action);
         return;
      }

      if (!this.canAct(player, action)) {
         if (action == HeroAction.SUIT && state.suit.nanoLocked()) {
            this.onInputRefused(player, action);
         }
         return;
      }

      if (state.hulkbuster.active() && hulkInput(player, state, action)) {
         return;
      }

      switch (action) {
         case SUIT -> {
            Suit suit = state.suit;
            if (dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.suitKey(player, state)
               || dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.suitKey(player, state)) {
               return;
            }

            if (suit.state() == SuitState.NONE && suit.nanoLocked()) {
               this.onInputRefused(player, action);
            } else if (suit.toggle()) {
               IronManSounds.play(player, suit.state() == SuitState.DEPLOYING
                  ? dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_NANO_DEPLOY.get()
                  : dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_NANO_RETRACT.get(), 1.0F, 1.0F);
            }
         }
         case PRIMARY_ATTACK -> {
            if (state.arsenal.weapon() != null) {
               IronManCombat.strike(player, state);
               return;
            }

            dev.baranhan.viltrumiteflight.util.FlightState flight = flightState(player);
            if (AirStrike.canArm(flight, player.getXRot(), IronManLandings.groundAhead(player))) {
               // This LMB arms the air strike instead of a fly-by (spec §8.6).
               state.airStrike.arm();
            } else if (state.flyByCooldown <= 0) {
               state.flyByCooldown = IronManRules.FLYBY_COOLDOWN;
               FlyBy.hit(player);
            }
         }
         case SECONDARY_USE -> {
            state.heldTool = state.rightTool;
            switch (state.rightTool) {
               case REPULSOR -> state.repulsor.press();
               case NANO_HAMMER -> state.hammerCharge = state.arsenal.forming() || state.energy.weaponsLocked() ? -1 : 0;
               case SIGNATURE -> signaturePress(player, state);
               default -> {
               }
            }
         }
         case INTERACT -> {
            dev.baranhan.viltrumitecore.entity.HeroInteractable target = interactTarget(player);
            if (target != null) {
               target.heroInteract(player);
            }
         }
         case TOOL_CYCLE -> IronManCombat.toolCycle(player, state);
         case UNIBEAM -> IronManCombat.unibeamPress(player, state);
         case MISSILES -> IronManCombat.missilesPress(player, state);
         case NANO_ARSENAL -> {
            // Slot 3: the nano arsenal, or the worn mark's signature (spec §6.2).
            if (state.suit.markWorn()) {
               signaturePress(player, state);
            } else if (state.suit.nano()) {
               IronManCombat.arsenalPress(player, state);
            }
         }
         case VERONICA -> dev.baranhan.viltrumitecore.hero.ironman.veronica.IronManVeronica.press(player, state);
         case GUARD -> IronManCombat.guardPress(player, state);
         case HELMET -> IronManJarvis.helmetPress(player, state);
         case SCAN -> IronManJarvis.scanPress(player, state);
         case COUNTERMEASURES -> IronManJarvis.countermeasuresPress(player, state);
         default -> {
         }
      }
   }

   /** Hulkbuster kit (rework spec §6–7): every press goes through HulkbusterControls (one resolver). */
   private static boolean hulkInput(ServerPlayer player, IronManState state, HeroAction action) {
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input input = hulkInputOf(action);
      if (input != null) {
         return dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCombat.press(player, state, input);
      }

      // The tool cycle, helmet and veronica keys have no meaning in the Mark 48.
      return action == HeroAction.TOOL_CYCLE || action == HeroAction.HELMET || action == HeroAction.VERONICA;
   }

   @javax.annotation.Nullable
   static dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input hulkInputOf(HeroAction action) {
      return switch (action) {
         case PRIMARY_ATTACK -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.LMB;
         case SECONDARY_USE -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.RMB;
         case GUARD -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.GUARD;
         case UNIBEAM -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.SLOT_1;
         case MISSILES -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.SLOT_2;
         case NANO_ARSENAL -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.SLOT_3;
         case COUNTERMEASURES -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.SLOT_4;
         case SIGNATURE -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input.ULTIMATE;
         default -> null;
      };
   }

   /** Releases always pass (channels must stop); without the suit they cancel without a shot. */
   private static void release(ServerPlayer player, IronManState state, HeroAction action) {
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input hulkInput = hulkInputOf(action);
      if (hulkInput != null && state.hulkbuster.present()) {
         dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCombat.release(player, state, hulkInput);
         return;
      }

      boolean live = state.suit.worn() && !controlled(player);
      switch (action) {
         case SECONDARY_USE -> {
            dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool tool = state.heldTool;
            state.heldTool = null;
            if (tool == dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.REPULSOR) {
               if (live) {
                  IronManCombat.fire(player, state, state.repulsor.release(state.energy));
               } else {
                  state.repulsor.cancel();
               }
            } else if (tool == dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.NANO_HAMMER) {
               if (live) {
                  IronManCombat.hammerLaunch(player, state);
               } else {
                  state.hammerCharge = -1;
               }
            } else if (tool == dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.SIGNATURE) {
               signatureRelease(player, state);
            }
         }
         case NANO_ARSENAL -> signatureRelease(player, state);
         case UNIBEAM -> IronManCombat.unibeamRelease(player, state);
         case MISSILES -> {
            if (live) {
               IronManCombat.missilesRelease(player, state);
            } else if (state.missiles.held()) {
               state.missiles.cancel();
               IronManCombat.pushMarks(player, java.util.List.of());
            }
         }
         case GUARD -> state.shield.lower(player.level().getGameTime());
         default -> {
         }
      }
   }

   /** Signature start (slot 3 or RMB tool): cooldown and the energy lock are checked here, costs by the signature. */
   static void signaturePress(ServerPlayer player, IronManState state) {
      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = state.suit.mark();
      if (mark == null || !state.suit.markWorn()) {
         return;
      }

      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignature signature = dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignatures.of(mark);
      if (state.signature.cooldown > 0 || state.energy.weaponsLocked()) {
         IronManSounds.play(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 0.9F);
         return;
      }

      if (signature.held()) {
         state.signature.held = true;
      }

      signature.press(player, state);
   }

   /** Release of a held signature: always routed while it is held (spec §16 release rule). */
   static void signatureRelease(ServerPlayer player, IronManState state) {
      boolean held = state.signature.held;
      state.signature.held = false;
      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = state.suit.mark();
      if (held && mark != null && state.suit.markWorn()) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignatures.of(mark).release(player, state);
      }
   }

   /** Suit key while the nanites are still lost after a core explosion (spec §9.4). */
   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      IronManState state = IronManState.of(player);
      if (action == HeroAction.SUIT && state != null && state.suit.nanoLocked()) {
         int seconds = (state.suit.nanoLockTicks() + 19) / 20;
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.viltrumitecore.ironman.nano_lost", seconds), true);
      }
   }

   /** Damage layers (spec §4.3, §8.5): shield first, then the worn mark's durability. */
   @Override
   public dev.baranhan.viltrumitecore.hero.DamageAbsorb absorbIncoming(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float raw) {
      IronManState state = IronManState.of(self);
      if (state == null) {
         return dev.baranhan.viltrumitecore.hero.DamageAbsorb.PASS;
      }

      if (state.suit.worn()) {
         dev.baranhan.viltrumitecore.hero.DamageAbsorb shield = IronManCombat.absorb(self, state, source);
         if (shield.absorbed()) {
            return shield;
         }
      }

      dev.baranhan.viltrumitecore.hero.DamageAbsorb hulk = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.absorb(self, state, source, raw);
      if (hulk.absorbed()) {
         return hulk;
      }

      return dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.absorb(state, source, raw);
   }

   @Override
   public float clampFinalDamage(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float afterArmor) {
      IronManState state = IronManState.of(self);
      return state == null ? afterArmor : IronManCombat.clampCoreExplosion(self, state, source, afterArmor);
   }

   @Override
   public void onHurt(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
      IronManState state = IronManState.of(player);
      if (state != null) {
         IronManCombat.onHurt(player, state, amount);
         IronManJarvis.onHurt(player, state, source);
      }
   }

   @Override
   public void enter(ServerPlayer player) {
      IronManState.ensure(player);
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      IronManState state = IronManState.of(player);
      return snapshotOf(state == null ? new IronManState() : state);
   }

   /**
    * Pure snapshot: flags, one channel timeline, energy x10, weapon lock,
    * the Unibeam end point and extra cooldowns ([0] = nano lock ticks).
    * Channel priority: suit wave > Unibeam > missiles > RMB hold (repulsor
    * charge, hammer charge, blade dash) > LMB weapon strike > weapon forming.
    */
   static HeroPublicSnapshot snapshotOf(IronManState state) {
      Suit suit = state.suit;
      int flags = 0;
      flags = IronManFlags.set(flags, IronManFlags.Field.SUIT_WORN, suit.worn());
      // A broken mark puts the nano on at once; its wave still plays (autoNanoTicks).
      flags = IronManFlags.set(flags, IronManFlags.Field.DEPLOYING, suit.state() == SuitState.DEPLOYING || suit.autoNanoTicks() > 0);
      flags = IronManFlags.set(flags, IronManFlags.Field.RETRACTING, suit.state() == SuitState.RETRACTING);
      flags = IronManFlags.set(flags, IronManFlags.Field.GLIDE, state.glide);
      flags = IronManFlags.set(flags, IronManFlags.Field.HEAVY_LANDING, state.heavyPoseTicks > 0);
      flags = IronManFlags.set(flags, IronManFlags.Field.OVERHEAT_COUNT, state.overheat.count());
      flags = IronManFlags.set(flags, IronManFlags.Field.OVERHEAT_LOCK, state.unibeam.overheated());
      flags = IronManFlags.set(flags, IronManFlags.Field.RMB_TOOL, state.rightTool.ordinal());
      flags = IronManFlags.set(flags, IronManFlags.Field.SHIELD_UP, state.shield.raised());
      flags = IronManFlags.set(flags, IronManFlags.Field.DAMAGED_ZONES, state.damagedZones);
      flags = IronManFlags.set(flags, IronManFlags.Field.UNIBEAM_PHASE, state.unibeam.phase().ordinal());
      flags = IronManFlags.set(flags, IronManFlags.Field.MISSILE_FLAPS, state.missiles.flapsOpen());
      flags = IronManFlags.set(flags, IronManFlags.Field.OVERDRAFT_SPUTTER, state.overdraft.sputtering());
      flags = IronManFlags.set(flags, IronManFlags.Field.RECOIL, state.recoilTicks > 0);
      flags = IronManFlags.set(flags, IronManFlags.Field.SHOT_HAND, state.recoilRight);
      flags = IronManFlags.set(flags, IronManFlags.Field.OVERDRAFT, state.overdraft.active());
      flags = IronManFlags.set(flags, IronManFlags.Field.HELMET_CLOSED, suit.state() != SuitState.NONE && state.helmet.closed());
      flags = IronManFlags.set(flags, IronManFlags.Field.SCAN_ACTIVE, state.scan.active());
      // Stage 4: mark plates and the signature.
      int equipPhase = suit.equipping() ? IronManFlags.EQUIP_EQUIPPING : suit.exiting() ? IronManFlags.EQUIP_EXITING
         : suit.partial() ? IronManFlags.EQUIP_PARTIAL : IronManFlags.EQUIP_NONE;
      flags = IronManFlags.set(flags, IronManFlags.Field.EQUIP_PHASE, equipPhase);
      boolean markWorn = suit.markWorn();
      flags = IronManFlags.set(flags, IronManFlags.Field.SIGNATURE_ACTIVE, markWorn && state.signature.active);
      flags = IronManFlags.set(flags, IronManFlags.Field.SIGNATURE_AUX, markWorn && state.signature.aux);
      flags = IronManFlags.set(flags, IronManFlags.Field.MARK_CAMO,
         markWorn && suit.mark() == dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId.MARK_15 && state.signature.active);
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer hulk = state.hulkbuster;
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit kit = state.hulkKit;
      flags = IronManFlags.set(flags, IronManFlags.Field.HULKBUSTER_PHASE, hulk.phase().ordinal());
      int actionId = -1;
      int elapsed = 0;
      int length = 0;
      if (hulk.busy()) {
         // Drop / assembly / climbing out of the Hulkbuster (SUIT timeline with HULKBUSTER_PHASE).
         actionId = HeroAction.SUIT.ordinal();
         elapsed = hulk.ticks();
         length = hulk.phaseLength();
      } else if (hulk.active() && kit.timeline.running()) {
         // Hulkbuster move: actionId = HulkMove.WIRE_OFFSET + wire id, phase and step in HulkbusterWire.MOVE_VARIANT.
         actionId = kit.timeline.move().wire();
         elapsed = kit.timeline.elapsed();
         length = kit.timeline.length();
      } else if (suit.transitioning()) {
         actionId = HeroAction.SUIT.ordinal();
         elapsed = suit.ticks();
         length = suit.waveLength();
      } else if (suit.autoNanoTicks() > 0) {
         actionId = HeroAction.SUIT.ordinal();
         elapsed = IronManRules.SUIT_DEPLOY_TICKS - suit.autoNanoTicks();
         length = IronManRules.SUIT_DEPLOY_TICKS;
      } else if (suit.equipping() || suit.exiting()) {
         actionId = HeroAction.SUIT.ordinal();
         elapsed = suit.ticks();
         length = suit.waveLength();
      } else if (markWorn && state.signature.length > 0) {
         actionId = HeroAction.SIGNATURE.ordinal();
         elapsed = state.signature.ticks;
         length = state.signature.length;
      } else if (state.unibeam.active() || state.unibeam.overheated()) {
         actionId = HeroAction.UNIBEAM.ordinal();
         elapsed = state.unibeam.ticks();
         length = switch (state.unibeam.phase()) {
            case CHARGE -> state.unibeam.chargeTicks();
            case BEAM -> state.unibeam.overdraft() ? dev.baranhan.viltrumitecore.hero.ironman.combat.Overdraft.explodeAt() : IronManRules.UNIBEAM_MAX;
            default -> IronManRules.UNIBEAM_OVERHEAT_LOCK;
         };
      } else if (state.missiles.held()) {
         actionId = HeroAction.MISSILES.ordinal();
         elapsed = state.missiles.ticks();
         length = IronManRules.MISSILE_FLAPS;
      } else if (state.repulsor.charging()) {
         actionId = HeroAction.SECONDARY_USE.ordinal();
         elapsed = state.repulsor.chargeTicks();
         length = IronManRules.REPULSOR_CHARGE_MAX;
      } else if (state.hammerCharge >= 0) {
         actionId = HeroAction.SECONDARY_USE.ordinal();
         elapsed = state.hammerCharge;
         length = IronManRules.HAMMER_CHARGE_MAX;
      } else if (state.strikeTicks > 0 && state.strikeLength > 0) {
         actionId = HeroAction.PRIMARY_ATTACK.ordinal();
         elapsed = state.strikeLength - state.strikeTicks;
         length = state.strikeLength;
      } else if (state.arsenal.forming() || state.arsenal.dissolveTicks() > 0) {
         actionId = HeroAction.NANO_ARSENAL.ordinal();
         elapsed = IronManRules.NANO_FORM_TICKS - Math.max(state.arsenal.formTicks(), state.arsenal.dissolveTicks());
         length = IronManRules.NANO_FORM_TICKS;
      } else if (state.scan.active() && state.scan.targetId() >= 0) {
         // Lowest priority: the scan reticle fill (spec §11.2).
         actionId = HeroAction.SCAN.ordinal();
         elapsed = state.scan.ticks();
         length = IronManRules.SCAN_TICKS;
      }

      // [4]-[7] reserved, [8] Hulkbuster cooldown, [9..] the Hulkbuster block (HulkbusterWire).
      int[] extra = extraCooldowns(hulkExtra(state, suit.nanoLockTicks(), state.countermeasures.cooldown(), state.veronicaCooldown, state.signature.cooldown, hulk.cooldown()));
      // controlTargetId = the entity being scanned (reticle), -1 otherwise.
      int scanTarget = state.scan.active() ? state.scan.targetId() : -1;
      net.minecraft.world.phys.Vec3 target;
      if (suit.equipping() || hulk.phase() == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.DROPPING || hulk.phase() == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ASSEMBLING) {
         target = state.equipSource;
      } else if (hulk.active()) {
         target = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCage.point(state);
      } else if (state.unibeam.phase() == dev.baranhan.viltrumitecore.hero.ironman.combat.UnibeamTimeline.Phase.BEAM) {
         target = state.beamEnd;
      } else {
         target = markWorn ? state.signature.point : null;
      }

      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = suit.markOn() || suit.equipping() || suit.exiting() ? suit.mark() : null;
      int variant = IronManVariant.pack(mark, suit.parts(), hulk.parts(), hulk.durability() / dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.DURABILITY);
      int durability = suit.markOn() && mark != null ? Math.round(state.roster.durability(mark) * 10.0F) : 0;
      return new HeroPublicSnapshot(HeroId.IRON_MAN, actionId, elapsed, length,
         0, false, 0, 0, false, false, 0, 0, scanTarget, new int[HeroPublicSnapshot.COOLDOWN_COUNT], false,
         -1, target, Math.round(state.energy.value() * 10.0F), state.energy.weaponsLocked(), flags, extra, variant, durability);
   }

   /** The Iron Man extra values with the Hulkbuster block from index HulkbusterWire.BASE (zeros when it is off). */
   static int[] hulkExtra(IronManState state, int nano, int countermeasures, int veronica, int signature, int hulkCooldown) {
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer hulk = state.hulkbuster;
      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit kit = state.hulkKit;
      int[] out = new int[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.SIZE];
      out[0] = nano;
      out[1] = countermeasures;
      out[2] = veronica;
      out[3] = signature;
      out[8] = hulkCooldown;
      if (hulk.busy()) {
         out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.SCENE] = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.scene(hulk.sceneTick(), hulk.air() ? 1 : 2, kit.sceneYaw,
            hulk.phase() == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ASSEMBLING ? hulk.ticks() : -1);
      }

      if (!hulk.active()) {
         return out;
      }

      dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion m = kit.motion;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.MOVE_VARIANT] = kit.timeline.variant();
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.MOTION] = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.motion(m.state, m.direction, m.dashCharges,
         dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules.damageStage(hulk.fraction()), hulk.spareUsed(), dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules.chargeLevel(kit.chargeHeld));
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.MOTION_TICKS] = Math.min(4095, m.ticks);
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_REPULSORS] = kit.repulsorsCooldown;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_RIFT] = kit.riftCooldown;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_CAGE] = kit.cageCooldown;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_SPARE_ARM] = hulk.spareUsed() ? 1 : 0;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_ULTIMATE] = kit.ultimateCooldown;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CD_GRAB] = kit.grabCooldown;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.DASH_RECHARGE] = m.dashRecharge;
      int victim = kit.grab.active() ? kit.grab.victimId : kit.ultTarget;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.VICTIM] = victim + 1;
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.ESCAPE] = Math.round(Math.min(100.0F, kit.grab.escape));
      out[dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire.CAGE] = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCage.wire(state);
      return out;
   }

   /** extraCooldowns: [0] nano lock, [1] countermeasures, [2] Veronica, [3] signature; trailing zeros dropped. */
   static int[] extraCooldowns(int... values) {
      int length = values.length;
      while (length > 0 && values[length - 1] <= 0) {
         length--;
      }

      return java.util.Arrays.copyOf(values, length);
   }

   /** Scan card hero part: only what the suit really does to damage (IronManCombat.absorb, nano armor attributes). */
   @Override
   public dev.baranhan.viltrumitecore.hero.ScanInfo scanInfo(Player self) {
      IronManState state = IronManState.of(self);
      if (state == null) {
         return dev.baranhan.viltrumitecore.hero.ScanInfo.EMPTY;
      }

      dev.baranhan.viltrumitecore.hero.ScanInfo info = scanInfoFor(state.suit.armored(), state.spec(), state.shield.raised(), state.overheat.count(),
         state.energy.weaponsLocked(), state.helmet.closed());
      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = state.suit.markOn() ? state.suit.mark() : null;
      if (mark == null) {
         return info;
      }

      // The mark takes every hit until it breaks (IronManMarks.absorb).
      java.util.List<dev.baranhan.viltrumitecore.hero.ScanLine> protections = new java.util.ArrayList<>(info.protections());
      protections.add(0, dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.mark", mark.displayName(),
         Math.round(state.roster.durability(mark)), Math.round(state.roster.maxDurability(mark))));
      return new dev.baranhan.viltrumitecore.hero.ScanInfo(protections, info.weakSpots(), info.conditions());
   }

   static dev.baranhan.viltrumitecore.hero.ScanInfo scanInfoFor(boolean armored, boolean shieldUp, int overheats, boolean weaponsOffline, boolean helmetClosed) {
      return scanInfoFor(armored, dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec.NANO, shieldUp, overheats, weaponsOffline, helmetClosed);
   }

   /** Honest card (spec §11.2): armor numbers of the suit on Tony now (nano or mark). */
   static dev.baranhan.viltrumitecore.hero.ScanInfo scanInfoFor(boolean armored, dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec spec, boolean shieldUp, int overheats,
      boolean weaponsOffline, boolean helmetClosed) {
      java.util.List<dev.baranhan.viltrumitecore.hero.ScanLine> protections = new java.util.ArrayList<>();
      java.util.List<dev.baranhan.viltrumitecore.hero.ScanLine> conditions = new java.util.ArrayList<>();
      if (armored) {
         protections.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.nano_armor",
            Math.round(spec.armor()), Math.round(spec.toughness()), Math.round(spec.knockbackRes() * 100.0)));
         protections.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.no_fall"));
      }

      if (shieldUp) {
         protections.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.shield", Math.round(IronManRules.SHIELD_CONE_DEG)));
      }

      if (overheats > 0) {
         conditions.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.overheats", overheats));
      }

      if (weaponsOffline) {
         conditions.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.weapons_offline"));
      }

      if (armored && !helmetClosed) {
         conditions.add(dev.baranhan.viltrumitecore.hero.ScanLine.of("scan.viltrumitecore.ironman.helmet_open"));
      }

      return new dev.baranhan.viltrumitecore.hero.ScanInfo(protections, java.util.List.of(), conditions);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      IronManState state = IronManState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      IronManState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      IronManState previous = IronManState.of(original);
      if (previous != null && clone instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(original.isDeadOrDying() ? IronManState.cloneForRespawn(previous) : IronManState.cloneForPortal(previous));
      }
   }

   /** Owner sections Iron Man writes. */
   private static final dev.baranhan.viltrumitecore.hero.OwnerSection[] OWNED_SECTIONS = {
      dev.baranhan.viltrumitecore.hero.OwnerSection.THREATS,
      dev.baranhan.viltrumitecore.hero.OwnerSection.MARKS,
      dev.baranhan.viltrumitecore.hero.OwnerSection.SCAN
   };

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      IronManState state = IronManState.of(player);
      if (state != null) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.stopSignature(player, state);
         boolean wasBig = state.hulkbuster.big();
         switch (reason) {
            case DEATH, ERASED -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.onDeath(player, state);
            case DISCONNECT -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.onLogout(player, state);
            case HERO_CHANGE -> dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.releaseGrab(player, state, dev.baranhan.viltrumitecore.hero.control.ReleaseReason.HERO_CHANGE);
         }

         state.onCleanup(reason);
         if (reason == CleanupReason.ERASED && player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) {
            heroPlayer.viltrumitecore$setHeroState(state.erasedCopy());
         }
         if (reason != CleanupReason.DISCONNECT) {
            // Threat brackets, missile locks and the scan glow must not outlive the suit (stale ESP boxes).
            dev.baranhan.viltrumitecore.hero.HeroRegistry.clearOwnerSections(player, OWNED_SECTIONS);
         }

         if (wasBig && !state.hulkbuster.big() && reason != CleanupReason.DISCONNECT) {
            player.refreshDimensions();
         }
      }

      if (reason != CleanupReason.DISCONNECT) {
         syncArmor(player, false, dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec.NANO);
      }

      if (reason == CleanupReason.HERO_CHANGE) {
         dev.baranhan.viltrumitecore.hero.HeroPassiveEffect.drop(player, net.minecraft.world.effect.MobEffects.REGENERATION, IronManRules.PASSIVE_REGEN_AMPLIFIER);
      }
   }

   /** Spec §16: the suit on the player stays through a dimension change; pod, empty suit and a delivery go back. */
   @Override
   public void onDimensionChange(ServerPlayer player) {
      IronManState state = IronManState.of(player);
      if (state != null) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.stopSignature(player, state);
         dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.onDimensionChange(player, state);
         // Entity ids of the old level mean other entities here.
         state.threats.clear();
         state.scan.cancel();
         state.missiles.cancel();
         dev.baranhan.viltrumitecore.hero.HeroRegistry.clearOwnerSections(player, OWNED_SECTIONS);
         // The Hulkbuster stays on (spec §16); a carried target stays behind.
         dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.releaseGrab(player, state, dev.baranhan.viltrumitecore.hero.control.ReleaseReason.TARGET_LOST);
      }
   }

   /** Suit armor attributes with fixed ids: present exactly while armored, values of the suit on Tony (nano or mark). */
   static void syncArmor(ServerPlayer player, boolean armored, dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec spec) {
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Iron Man nano armor", spec.armor(), armored);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Iron Man nano toughness", spec.toughness(), armored);
      modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Iron Man nano stability", spec.knockbackRes(), armored);
      modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Iron Man suit health", IronManRules.SUIT_HEALTH_BONUS, armored);
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   private static void modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount, boolean present) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance == null) {
         return;
      }

      AttributeModifier current = instance.getModifier(id);
      if (current != null && (!present || current.getAmount() != amount)) {
         instance.removeModifier(id);
         current = null;
      }

      if (present && current == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
      }
   }
}
