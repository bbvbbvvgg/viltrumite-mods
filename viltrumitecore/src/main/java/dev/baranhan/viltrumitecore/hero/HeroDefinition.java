package dev.baranhan.viltrumitecore.hero;
import net.minecraft.world.phys.Vec3;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * One hero's shared contract. Common code asks this interface, never a concrete
 * character: no hero branches belong in the legacy mixins.
 */
public interface HeroDefinition {
   HeroId id();

   /** Whether this hero may use mod flight at all. Vanilla creative/spectator flight is unaffected. */
   boolean allowsFlight(Player player);

   /**
    * Server: does this hero want vanilla {@code mayfly} now (heroes outside the
    * legacy kit, which grants it itself). Applied by {@link HeroFlightGrant}.
    */
   default boolean grantsFlightAbility(Player player) {
      return false;
   }

   /**
    * Flight tuning for this player, or null for the original flight. Read on
    * BOTH sides from synced data (installed as the FlightProfiles resolver).
    */
   @javax.annotation.Nullable
   default dev.baranhan.viltrumiteflight.util.FlightProfile flightProfile(Player player) {
      return null;
   }

   /** Whether this hero may use the legacy viltrumite ability kit and stats. */
   boolean allowsLegacyAbilities(Player player);

   /**
    * May this player start the given legacy kit ability ("viltrumite:*") now.
    * Read on BOTH sides. Default: kit users that own the ability id.
    */
   default boolean allowsLegacyAbility(Player player, String abilityId) {
      return this.allowsLegacyAbilities(player) && this.ownsAbility(abilityId);
   }

   /**
    * Own ability ids whose slot keys send HeroInputC2SPacket on press/release
    * (client reads this). Default: none.
    */
   default String[] heroInputSlots() {
      return new String[0];
   }

   /** Input action for one of {@link #heroInputSlots()}, or null. */
   default HeroAction heroActionFor(String abilityId) {
      return null;
   }

   /**
    * Hero-specific icon for an ability slot (e.g. kit abilities drawn as this
    * hero), or null for the registry icon. Client reads this.
    */
   @javax.annotation.Nullable
   default net.minecraft.resources.ResourceLocation abilityIcon(String abilityId) {
      return null;
   }

   /**
    * Icon of an ability slot for this player right now (e.g. a slot whose
    * ability depends on the worn suit), or null. Client reads this; default:
    * the player-independent {@link #abilityIcon(String)}.
    */
   @javax.annotation.Nullable
   default net.minecraft.resources.ResourceLocation abilityIcon(String abilityId, Player player) {
      return this.abilityIcon(abilityId);
   }

   /**
    * Hitbox and eye height factor of this player (both sides, Forge
    * EntityEvent.Size in HeroSizeEvents, per-pose table in HeroBodySize).
    * HeroBodyTracker refreshes dimensions when it changes. Default 1 = vanilla.
    */
   default float bodyScale(Player player) {
      return 1.0F;
   }

   /**
    * Hitbox width factor (HeroBodySize; both sides). Default: the same as {@link #bodyScale}.
    * HeroBodyTracker refreshes dimensions when either factor changes.
    */
   default float bodyWidthScale(Player player) {
      return this.bodyScale(player);
   }

   /** Extra entity / block reach (blocks) of a big body: a transient attribute modifier (HeroBodyTracker). */
   default double bodyReachBonus(Player player) {
      return 0.0;
   }

   /** Extra step height (blocks) of a big body: a transient attribute modifier (HeroBodyTracker). */
   default double bodyStepBonus(Player player) {
      return 0.0;
   }

   /**
    * Factor on the legacy flight speed (no profile): CRUISE/SONIC velocity uses
    * the player's max flight speed × this. Both sides (movement is client-side).
    */
   default float flightSpeedScale(Player player) {
      return 1.0F;
   }

   /** Whether the legacy three-page ability bar swap is meaningful for this hero. */
   default boolean allowsAbilityPages(Player player) {
      return this.allowsLegacyAbilities(player);
   }

   /** Whether the ability panel/menu renders for this hero. */
   default boolean hasAbilityPanel(Player player) {
      return this.id() != HeroId.HUMAN;
   }

   /** Which registry ability ids this hero owns (and may equip). */
   boolean ownsAbility(String abilityId);

   /** Panel entries this hero registers into the shared ability registry. */
   default java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return java.util.List.of();
   }

   /**
    * Spoken lines of this hero (cinematic subtitles) and their triggers. The
    * server reads them when an ability starts or a hero event fires
    * ({@link dev.baranhan.viltrumitecore.hero.speech.HeroSpeech}). Default: none.
    */
   default dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return dev.baranhan.viltrumitecore.hero.speech.HeroLines.NONE;
   }

   /**
    * Data-only description of this hero's ultimate for performers that copy it
    * (Sinclair's Audience Participation, spec docs/design/2026-10-28-sinclair-design.md §6.2).
    * Default: none. Must be pure: no side effects, no entity or state references in the result.
    */
   @javax.annotation.Nullable
   default dev.baranhan.viltrumitecore.hero.act.StageAct signatureAct(Player self) {
      return null;
   }

   /**
    * Chassis numbers for a legacy-kit hero, or null to keep the synced
    * Viltrumite stats (config defaults: base 19, 97 % reduction). A hero that
    * must not be a full Viltrumite tank or one-shot puncher returns its own.
    */
   default HeroChassis chassisStats(Player player) {
      return null;
   }

   /**
    * Style of the kit chop for this player (both sides, cheap: no allocation per
    * call). Default: the legacy look and sound, unchanged.
    */
   default ChopStyle chopStyle(Player player) {
      return ChopStyle.DEFAULT;
   }

   /** Does the shared legacy-kit regeneration (every 40 t) apply; heroes with their own regen opt out. */
   default boolean usesLegacyRegen(Player player) {
      return true;
   }

   /** Default slot contents applied on hero enter. Length 18, "" for empty. */
   String[] defaultLoadout();

   /**
    * Former default layouts of this hero. A save that still holds one of them
    * exactly (the player never rearranged it) is moved to {@link #defaultLoadout()}.
    */
   default String[][] previousDefaultLoadouts() {
      return new String[0][];
   }

   /**
    * Maps retired or merged ability ids of a saved loadout (18 slots, "" for
    * empty) to their successors before the ownership check, so a customized
    * layout keeps its other slots instead of resetting to the default. Returns
    * the migrated slots (the argument may be changed and returned). Default:
    * nothing retired.
    */
   default String[] migrateLoadout(String[] slots) {
      return slots;
   }

   /** May this target be placed under the given control kind right now. */
   boolean allowsExternalControl(LivingEntity target, ControlKind kind);

   /** Opt-in policy for claimed LMB; melee-only heroes leave vanilla block mining alone. */
   default boolean claimsBlockAttack(Player player) { return true; }

   /** Opt-in policy for claimed RMB aimed at a block; false leaves vanilla use / place / open on blocks alone. */
   default boolean claimsBlockUse(Player player) { return true; }

   /**
    * Opt-in RMB arbitration (VanillaUse): true = this hero takes a claimed
    * right click only when vanilla would not use it (both hands empty, not on
    * a block, not on an interactive entity). Default false: unchanged claim.
    */
   default boolean yieldsToVanillaUse(Player player) { return false; }

   /** Optional physical catch. True transfers temporary projectile ownership to the hero. */
   default boolean captureProjectile(ServerPlayer self, net.minecraft.world.entity.projectile.Projectile projectile) { return false; }

   /** True only when an explicit continuous ray is mechanically stopped by this hero. */
   default boolean blocksContinuousRay(ServerPlayer self, Vec3 source, Vec3 contact) { return false; }
   /** Server-owned defensive contact. Client renderers may refine only its presentation. */
   default Vec3 defenseContact(Player self) { return null; }

   /** Server rejects stray vanilla entity-hit packets for timeline-owned melee. */
   default boolean allowsVanillaAttack(Player player) { return true; }

   /**
    * Action this hero claims for a mouse button right now, or null for vanilla.
    * Called on BOTH sides (client cancels vanilla, server gates the press in
    * {@link HeldInputs}); must only read synced data on the client.
    */
   default HeroAction mouseAction(MouseButton button, Player player) {
      return null;
   }

   /**
    * Actions that only ever arrive through {@link HeldInputs} (a claimed mouse
    * button): the key packet (HeroInputC2SPacket) drops their presses and
    * releases, so a modified client cannot skip the claim, the held-repeat
    * guard or the block-attack rule by sending them as keys. Default: none.
    */
   default boolean mouseOnly(HeroAction action) {
      return false;
   }

   /**
    * Action for the guard key (vanilla swap-hands, default F) right now, or
    * null for the vanilla swap. Both sides, synced data only on the client:
    * when non-null the client consumes the swap key and sends held edges.
    */
   @javax.annotation.Nullable
   default HeroAction guardAction(Player player) {
      return null;
   }

   /** Server: cancel the vanilla main/off-hand swap for this player right now (also forged packets). */
   default boolean blocksHandSwap(Player player) {
      return false;
   }

   /**
    * Descriptive protections / weak spots / conditions of this hero right now
    * for analysis (Iron Man scan). Must mirror the real damage code; never
    * invent. Server side. Default: nothing known.
    */
   default ScanInfo scanInfo(Player self) {
      return ScanInfo.EMPTY;
   }

   /** Hidden from scans (Iron Man scan skips this player). */
   default boolean hiddenFromScan(Player self) {
      return false;
   }

   /**
    * A divine being (spec: Reinhard §3.3): only divine damage hurts it in
    * full, and execute-style powers skip it. See {@link DivineTargets}. Default false.
    */
   default boolean isDivine(Player self) {
      return false;
   }

   /**
    * Absolute invulnerability right now (Regulus' Lion's Heart). Not the same
    * as divinity: execute-style powers stop at it. Default false.
    */
   default boolean isAbsolutelyInvulnerable(Player self) {
      return false;
   }

   /**
    * What this player brings into a weapon interaction right now: weapon id,
    * tags/traits and stance (shared {@code hero.combat} seam). Both sides,
    * synced data only on the client. Not consulted by runtime hits yet (Thor
    * stage 2 wires clash/block resolution); default: no presence.
    */
   default dev.baranhan.viltrumitecore.hero.combat.CombatPresence combatPresence(Player player) {
      return dev.baranhan.viltrumitecore.hero.combat.CombatPresence.NONE;
   }

   /** Hidden from target-acquiring senses (Homelander focus skips this player). Server side. */
   default boolean hiddenFromFocus(Player self) {
      return false;
   }

   /** A claimed press was refused by canAct (feedback only, e.g. a locked message). */
   default void onInputRefused(ServerPlayer player, HeroAction action) {
   }

   /** Multiplier on this hero's ordinary melee attack damage (heart bonus). */
   default float meleeDamageFactor(Player player) {
      return 1.0F;
   }

   /** Whether fall damage is cancelled for this hero right now. */
   default boolean cancelsFallDamage(Player player) {
      return false;
   }

   /**
    * Server: the hero landed after falling {@code fallDistance} blocks. Runs
    * from LivingFallEvent before any cancel. Use HeroShockwave.land here.
    */
   default void onLanded(ServerPlayer player, float fallDistance) {
   }

   /**
    * Instant super-jump launch velocity in blocks/tick; 0 = no super jump.
    * Read on BOTH sides (the client launches), so it must not depend on
    * server-only state. 2.0 peaks ~20 blocks (HeroSuperJump.apex).
    */
   default float superJumpVelocity(Player player) {
      return 0.0F;
   }

   /** Server: hero-specific sounds/FX after the shared super-jump launch. */
   default void onSuperJump(ServerPlayer player) {
   }

   /**
    * Jumps in the air before touching ground, water, lava, a ladder or a mount
    * again (HeroAirJump / client AirJumpClient); 0 = none. Read on BOTH sides.
    */
   default int airJumps(Player player) {
      return 0;
   }

   /** Vertical velocity of one air jump in blocks/tick (horizontal is kept). Read on BOTH sides. */
   default double airJumpVelocity(Player player) {
      return 0.0;
   }

   /** Opt-in horizontal air steering. Default exactly preserves the legacy velocity. */
   default net.minecraft.world.phys.Vec3 airJumpMotion(Player player, net.minecraft.world.phys.Vec3 current) {
      return new net.minecraft.world.phys.Vec3(current.x,airJumpVelocity(player),current.z);
   }

   /** Same launch seam used by Regulus; default behaviour is unchanged. */
   default net.minecraft.world.phys.Vec3 superJumpMotion(Player player, net.minecraft.world.phys.Vec3 current) {
      net.minecraft.world.phys.Vec3 look=player.getLookAngle();
      return new net.minecraft.world.phys.Vec3(current.x+look.x*HeroSuperJump.FORWARD,superJumpVelocity(player),current.z+look.z*HeroSuperJump.FORWARD);
   }

   /** Server: hero-specific sound/FX after an accepted air jump (the shared ring is drawn by the clients). */
   default void onAirStep(ServerPlayer player) {
   }

   /** Server: this hero player took damage (after armor, before health change). */
   default void onHurt(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
   }

   /**
    * Server: how this hero slows {@code other}'s own resource regen (Iron Man
    * energy, Homelander cooling), 0..1; the strongest source wins
    * ({@link HeroResourceRegen}). Default: no effect.
    */
   default float foreignResourceRegenFactor(Player self, Player other) {
      return 1.0F;
   }

   /**
    * Both sides, MobEffectEvent.Applicable: true = this effect never applies to
    * the hero (DENY). Default: vanilla.
    */
   default boolean blocksEffect(Player self, net.minecraft.world.effect.MobEffectInstance effect) {
      return false;
   }

   /**
    * Server, ProjectileImpactEvent: a projectile is about to hit this hero
    * (see {@link HeroProjectiles}). Anything but PASS skips the hit and is
    * applied there. Default: vanilla hit.
    */
   default ProjectileResponse projectileResponse(ServerPlayer self, net.minecraft.world.entity.projectile.Projectile projectile) {
      return ProjectileResponse.PASS;
   }

   /**
    * Both sides: true when {@link #projectileResponse} may turn a projectile
    * away from this hero. Clients then keep custom projectiles that touch the
    * hero flying until the server outcome arrives (see
    * {@link HeroProjectiles#awaitsServer}); every other hit plays its local
    * impact at once. Default: never.
    */
   default boolean interceptsProjectiles(Player self) {
      return false;
   }

   /**
    * Server, LivingAttackEvent (before armor, knockback, hurt animation): this
    * hero's own damage layers in the fixed order shield → Hulkbuster → mark
    * (see {@link HeroDamageLayers}). {@link DamageAbsorb#ABSORBED} cancels the
    * whole hit. Also runs once for the direct path (control payouts).
    */
   default DamageAbsorb absorbIncoming(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float raw) {
      return DamageAbsorb.PASS;
   }

   /** Server, LivingDamageEvent (after armor/enchantments/Resistance): final HP loss, e.g. HP floors. */
   default float clampFinalDamage(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float afterArmor) {
      return afterArmor;
   }

   /**
    * Server: HP loss from a source that bypasses invulnerability (void,
    * {@code generic_kill}), which skips absorbIncoming and clampFinalDamage.
    * Runs in LivingDamageEvent and on the clean path, never inside an
    * {@link AdminKill} scope ({@code /kill}). Default: unchanged.
    */
   default float clampUnblockableDamage(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float amount) {
      return amount;
   }

   /** Server, LivingHurtEvent: damage this hero deals to {@code target} (multipliers). */
   default float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
      return amount;
   }

   /**
    * Server: the player respawned as a fresh body (PlayerRespawnEvent, also the
    * End-portal return). {@code restoreHero()} on clone skips {@link #enter},
    * so a hero re-applies its chassis here. Must be idempotent. Default: nothing.
    */
   default void onRespawn(ServerPlayer player) {
   }

   /** Server: the player changed dimension (no full cleanup; stop channels if needed). */
   default void onDimensionChange(ServerPlayer player) {
   }

   /** Whether food exhaustion is currently suspended for this hero (Lion). */
   default boolean preventsExhaustion(Player player) {
      return false;
   }

   /**
    * Server, type filter: mobs of this type may flee this hero the way a creeper
    * flees a cat ({@link HeroMobFear} gives them a vanilla AvoidEntityGoal when they
    * join a level). Asked once per mob, so it must be a pure type check.
    */
   default boolean scaresMobType(net.minecraft.world.entity.EntityType<?> type) {
      return false;
   }

   /** Server: does this mob flee this player right now (it avoids him and never targets him). */
   default boolean scaresMob(Player self, net.minecraft.world.entity.Mob mob) {
      return false;
   }

   /**
    * Both sides: full knockback immunity ({@link HeroKnockback}). Vanilla knockback,
    * explosion pushes and the hurt velocity resync are skipped; hero pushes ask
    * IMPULSE through {@link #allowsExternalControl}. The hero's own motion is untouched.
    */
   default boolean knockbackImmune(Player player) {
      return false;
   }

   /**
    * Server: long airborne stretches are legitimate movement (unlimited air steps),
    * so the vanilla "flying is not enabled" kick never counts them.
    */
   default boolean sustainsAirborne(Player player) {
      return false;
   }

   /**
    * Both sides: air acceleration while airborne and not flying (vanilla 0.02, sprint
    * 0.026); 0 keeps vanilla. The client predicts the movement, so it must agree.
    */
   default float airControl(Player player) {
      return 0.0F;
   }

   /** Client: minimum ticks between two predicted air jumps (0 = any fresh press). */
   default int airJumpInterval(Player player) {
      return 0;
   }

   /** May the hero start or continue this action right now. */
   boolean canAct(ServerPlayer player, HeroAction action);

   void tick(ServerPlayer player);

   void handleInput(ServerPlayer player, HeroAction action, boolean pressed);

   /**
    * A held input ended by force, not by the player (HeldInputs.releaseAll:
    * death, disconnect, hero change, erasure, anchoring / control, suit off).
    * Default: the same as the player's release, so heroes that do not care
    * keep their behaviour; a hero whose release fires a charged action
    * overrides it to cancel instead.
    */
   default void handleForcedRelease(ServerPlayer player, HeroAction action) {
      this.handleInput(player, action, false);
   }

   /**
    * Enter the hero after an actual id change (not on restore/clone): default
    * loadout, grants, baseline snapshot. Never called when the id is unchanged.
    */
   void enter(ServerPlayer player);

   /**
    * Server: may this player become this hero now? Checked by
    * HeroRegistry.changeHero before any cleanup; a refusing hero tells the
    * player why. Default: always (a hero with a one-holder slot refuses
    * while another player holds it).
    */
   default boolean admitsEntry(ServerPlayer player) {
      return true;
   }

   /** Both sides: entity held by this hero, or -1. Never infer a hold from a generic PULL lease. */
   default int heldTargetId(Player holder) { return -1; }

   /** Both sides: authoritative hold anchor. partialTick only interpolates the client presentation. */
   default net.minecraft.world.phys.Vec3 heldTargetPosition(Player holder, net.minecraft.world.entity.Entity target, float partialTick) { return null; }

   /** Server: does this hero's hold anchor its held target (damage queues like under an anchor control)? Default: no. */
   default boolean anchorsHeldTarget(Player holder) { return false; }

   /** Server-authoritative public snapshot; written to the synced data for all trackers. */
   HeroPublicSnapshot snapshot(Player player);

   /**
    * Hero-private data saved with the entity's HeroData on logout (cooldowns,
    * charges — duration bookkeeping only). Default: nothing to save.
    */
   default void saveHeroState(Player player, CompoundTag nbt) {
   }

   /**
    * Load counterpart of saveHeroState on login. The default drops the
    * transient hero state object, matching the previous rebuild-empty path.
    */
   default void loadHeroState(Player player, CompoundTag nbt) {
      if (player instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(null);
      }
   }

   /** On respawn the clone receives whatever the hero wants carried. Default: nothing. */
   default void cloneHeroState(Player original, Player clone) {
   }

   /**
    * Idempotent cleanup that precedes transient-state discard. Reasons
    * distinguish death, disconnect and an explicit hero change.
    */
   void cleanup(ServerPlayer player, CleanupReason reason);
}
