package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Adaptation;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CatchWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ArrowWard;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.CloudStep;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Detox;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ManaAbsorption;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Parry;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Phoenix;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.WindReading;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidLife;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Reinhard van Astrea (spec docs/design/2026-10-11-reinhard-design.md). Own
 * chassis, no Viltrumite kit (spec §1.2.5), no flight (spec §1.2.7).
 */
public final class ReinhardHero implements HeroDefinition {
   /** Knockback-resistance modifier of the chassis (fixed id, never change: saved on the player). */
   public static final UUID KNOCKBACK_MODIFIER_ID = UUID.fromString("c39ed511-76d1-4e69-a3ed-42628d488960");
   /** Declared after every static field the constructor may read (static init order). */
   public static final ReinhardHero INSTANCE = new ReinhardHero();

   private ReinhardHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.REINHARD;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean grantsFlightAbility(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** Lethal Chop (spec §7.2): of the kit only {@code viltrumite:chop}, and not while Reid is being drawn or sheathed. */
   @Override
   public boolean allowsLegacyAbility(Player player, String abilityId) {
      return allowsKitAbility(abilityId, flags(player));
   }

   /** Pure: the kit ability rule over the synced Reinhard flags. */
   public static boolean allowsKitAbility(String abilityId, int heroFlags) {
      return dev.baranhan.viltrumitecore.hero.LegacyKit.CHOP.equals(abilityId)
         && !ReinhardFlags.has(heroFlags, ReinhardFlags.REID_DRAWN)
         && !ReinhardFlags.has(heroFlags, ReinhardFlags.REID_DRAWING) && !ReinhardFlags.has(heroFlags, ReinhardFlags.REID_SHEATHING);
   }

   /** Fixed chop base (35 per chop = ×1.75), no Viltrumite tank numbers. */
   @Override
   public dev.baranhan.viltrumitecore.hero.HeroChassis chassisStats(Player player) {
      return CHASSIS;
   }

   private static final dev.baranhan.viltrumitecore.hero.HeroChassis CHASSIS = new dev.baranhan.viltrumitecore.hero.HeroChassis(ReinhardRules.CHOP_BASE_DAMAGE, 0.0F, 0.0F);
   private static final dev.baranhan.viltrumitecore.hero.ChopStyle CHOP = new dev.baranhan.viltrumitecore.hero.ChopStyle(false, 2.0F, 0xFFFFFF, 0xB0142A, true, 1.4F, 0.8F);
   private static final dev.baranhan.viltrumitecore.hero.ChopStyle CHOP_LEFT = new dev.baranhan.viltrumitecore.hero.ChopStyle(true, 2.0F, 0xFFFFFF, 0xB0142A, true, 1.4F, 0.8F);

   /** Lethal Chop look: a 2× white-crimson arc with an air ripple, louder and lower; Reid drawn = always the left hand (spec §9.2). */
   @Override
   public dev.baranhan.viltrumitecore.hero.ChopStyle chopStyle(Player player) {
      return chopStyle(flags(player));
   }

   public static dev.baranhan.viltrumitecore.hero.ChopStyle chopStyle(int heroFlags) {
      return ReinhardFlags.has(heroFlags, ReinhardFlags.REID_DRAWN) ? CHOP_LEFT : CHOP;
   }

   /** Synced Reinhard flags of a player (server: the live state; client: the snapshot), 0 when unknown. */
   private static int flags(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         ReinhardState state = ReinhardState.of(serverPlayer);
         if (state != null) {
            return interactionFlags(state);
         }
      }

      dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot = player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
         ? heroPlayer.getHeroSnapshot() : null;
      return snapshot == null ? 0 : snapshot.heroFlags();
   }

   /** Live context claims must include the same mode bits as the public snapshot. */
   static int interactionFlags(ReinhardState state) {
      return ReinhardFlags.withMode(Reid.withFlags(stateFlags(state, 0), state.reid.phase()), state.mode);
   }

   @Override
   public boolean hasAbilityPanel(Player player) {
      return true;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return ReinhardAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return ReinhardAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return ReinhardAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return ReinhardAbilities.actionFor(abilityId);
   }

   @Override
   public java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return ReinhardAbilities.panelAbilities();
   }

   @Override
   public net.minecraft.resources.ResourceLocation abilityIcon(String abilityId) {
      return ReinhardAbilities.icon(abilityId);
   }

   /** Reid sheathed / drawn icon from the synced snapshot (works on both sides). */
   @Override
   public net.minecraft.resources.ResourceLocation abilityIcon(String abilityId, net.minecraft.world.entity.player.Player player) {
      dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot = player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
         ? heroPlayer.getHeroSnapshot() : null;
      return ReinhardAbilities.icon(abilityId, snapshot == null ? 0 : snapshot.heroFlags());
   }

   /** The spec gives Reinhard no immunity to control; knockback is the attribute (chassis). */
   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   /** Spec §5: MMB cycles the mode (both sides; the client cancels pick-block). */
   @Override
   public boolean claimsBlockAttack(Player player) { return false; }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      ReinhardState state=ReinhardState.of(player);
      return state!=null && state.resolvingMelee;
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return claimedMouse(button, ReinhardFlags.has(flags(player), ReinhardFlags.GRAB_HOLDING) || button == MouseButton.SECONDARY && canContextGrab(player));
   }

   /** Empty hands and an explicit ray target keep normal item/block use untouched. */
   public static boolean canContextGrab(Player player) {
      if (player == null || !player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return false;
      ReinhardMode mode = ReinhardMode.values()[ReinhardFlags.mode(flags(player))];
      if (mode == ReinhardMode.HOLY_SWORD) return false;
      var target = ReinhardTargets.lookTarget(player, ReinhardRules.GRAB_REACH, e -> e.isAlive() && !player.isAlliedTo(e)
         && !e.isAlliedTo(player) && (!(e instanceof Player other) || !other.isCreative() && player.canHarmPlayer(other)));
      return target != null && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(target, ControlKind.PULL)
         && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(target);
   }

   /** Pure input policy shared by local claims and authoritative server claims. */
   public static HeroAction claimedMouse(MouseButton button, boolean holding) {
      if (button == MouseButton.PRIMARY) return HeroAction.PRIMARY_ATTACK;
      if (button == MouseButton.MIDDLE) return HeroAction.MODE_CYCLE;
      return button == MouseButton.SECONDARY && holding ? HeroAction.SECONDARY_USE : null;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }

      ReinhardState state = ReinhardState.ensure(player);
      return switch (action) {
         case PRIMARY_ATTACK -> !state.busy() && (!state.action.running() || state.action.is(ReinhardAbility.FIST_COMBO));
         case SECONDARY_USE -> dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.holding(state) || canContextGrab(player);
         case MODE_CYCLE -> ModeCycle.press(state.mode, state.modeCooldown, state.busy()).accepted();
         // Cloudy Sky (spec §4.4): super jump and air steps in every mode.
         // Except while Reid is being drawn: the 6 s scene roots him (spec §7.1).
         case JUMP, AIR_STEP -> !cinematicRoot(state) && state.reid.phase() != dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.Phase.DRAWING;
         // Abilities (stage 3+): the mode, cooldown and timeline gate lives in ReinhardActions.
         default -> ReinhardActions.abilityFor(action) != null;
      };
   }

   public static boolean cinematicRoot(ReinhardState state) {
      return state.action.is(ReinhardAbility.WORLD_CUT) || state.action.is(ReinhardAbility.DIAGONAL_WORLD_CUT);
   }

   /** A refused press always gives feedback (add_hero §4.4). */
   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      ReinhardSounds.play(player, ReinhardSounds.REFUSE.get(), 0.8F, 0.6F);
   }

   @Override
   public void tick(ServerPlayer player) {
      ReinhardState state = ReinhardState.ensure(player);
      if (cinematicRoot(state)) {
         if(state.cutAnchor==null)state.cutAnchor=player.position();
         var anchor=state.cutAnchor;
         if(player.position().distanceToSqr(anchor)>1E-6)
            player.connection.teleport(anchor.x,anchor.y,anchor.z,player.getYRot(),player.getXRot());
         player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
         player.hurtMarked=true;
      } else state.cutAnchor=null;
      state.arrowCapture.tick(player);
      state.tick();
      ReinhardActions.tick(player, state);
      ReidLife.tick(player, state, state.reid.tick());
      VoidRescue.tick(player);
      if (player.tickCount % ReinhardRules.WIND_SCAN_INTERVAL == 0) {
         WindReading.scan(player, state);
      }
      if (player.isAlive() && ++state.regenTicks >= ReinhardRules.REGEN_INTERVAL) {
         state.regenTicks = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(ReinhardRules.REGEN_PER_SECOND);
         }
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (action == HeroAction.PRIMARY_ATTACK) {
         if (pressed) ReinhardActions.primaryAttack(player, ReinhardState.ensure(player));
         return;
      }
      if (action == HeroAction.MODE_CYCLE) {
         if (pressed) {
            cycleMode(player, ReinhardState.ensure(player));
         }

         return;
      }

      if (action == HeroAction.SECONDARY_USE) {
         if (pressed) ReinhardActions.secondaryUse(player, ReinhardState.ensure(player));
         return;
      }
      ReinhardActions.handleInput(player, ReinhardState.ensure(player), action, pressed);
   }

   /** Spec §5: no pose; cooldown 3 s; own sound per mode; the panel page follows the mode. */
   private void cycleMode(ServerPlayer player, ReinhardState state) {
      ModeCycle.Result result = ModeCycle.press(state.mode, state.modeCooldown, state.busy());
      if (!result.accepted()) {
         this.onInputRefused(player, HeroAction.MODE_CYCLE);
         return;
      }

      ReinhardMode previous = state.mode;
      ReinhardActions.onModeChange(player, state);
      state.mode = result.next();
      state.modeCooldown = ReinhardRules.MODE_COOLDOWN;
      if (previous == ReinhardMode.HOLY_SWORD) {
         if (state.reid.phase() != dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.Phase.SHEATHED) {
            ReinhardSounds.play(player, ReinhardSounds.REID_SHEATHE.get(), 0.8F, 1.0F);
         }
         state.onLeaveHolySword();
      }

      setPage(player, state.mode.page());
      ReinhardSounds.play(player, ReinhardSounds.mode(state.mode), 1.0F, 1.0F);
   }

   /** ACTIVE_PAGE is synced entity data: server only. */
   private static void setPage(ServerPlayer player, int page) {
      if (player instanceof ViltrumiteAbilityUser abilityUser) {
         abilityUser.setActivePage(page);
      }
   }

   @Override
   public void enter(ServerPlayer player) {
      ReinhardState state = ReinhardState.ensure(player);
      state.mode = ReinhardMode.RESTRAINED;
      state.modeCooldown = 0;
      setPage(player, ReinhardMode.RESTRAINED.page());
      applyChassis(player);
      Detox.cleanse(player);
   }

   /** Mana Absorption (spec §4.7): non-allied players nearby refill their own resource at half speed. */
   @Override
   public float foreignResourceRegenFactor(Player self, Player other) {
      return ManaAbsorption.factor(other == self, other.distanceToSqr(self), self.isAlliedTo(other));
   }

   /** Detoxification (spec §4.6): harmful effects never apply (both sides). */
   @Override
   public boolean blocksEffect(Player self, MobEffectInstance effect) {
      return Detox.blocks(effect);
   }

   /** Fresh body after death or the End return: enter() is not called there. */
   @Override
   public void onRespawn(ServerPlayer player) {
      applyChassis(player);
   }

   /** Cloudy Sky (spec §4.4): five air steps (both sides). */
   @Override
   public net.minecraft.world.phys.Vec3 airJumpMotion(Player player, net.minecraft.world.phys.Vec3 current) {
      return CloudStep.motion(current,player.getLookAngle(),player.getYRot(),airJumpVelocity(player),false);
   }

   @Override
   public net.minecraft.world.phys.Vec3 superJumpMotion(Player player, net.minecraft.world.phys.Vec3 current) {
      return CloudStep.motion(current,player.getLookAngle(),player.getYRot(),superJumpVelocity(player),true);
   }

   @Override
   public int airJumps(Player player) {
      return CloudStep.steps();
   }

   @Override
   public double airJumpVelocity(Player player) {
      return ReinhardRules.AIR_STEP_VELOCITY;
   }

   @Override
   public void onAirStep(ServerPlayer player) {
      CloudStep.onStep(player);
   }

   /** Cloudy Sky (spec §4.4): the super jump (both sides). */
   @Override
   public float superJumpVelocity(Player player) {
      return ReinhardRules.SUPER_JUMP_VELOCITY;
   }

   @Override
   public void onSuperJump(ServerPlayer player) {
      CloudStep.onSuperJump(player);
   }

   /** Spec §3.1: no fall damage. */
   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   /**
    * Spec §4.1: a direct melee hit from the front is parried (absorbed, pose, spark).
    * Spec §3.1: other ordinary damage loses 99.9 % before armor. Divine damage (spec §3.2)
    * is never resisted; it meets only Protection Acquisition here (immune type →
    * absorbed with a spark, otherwise the hit is counted and passes) and the
    * Phoenix in {@link #clampFinalDamage}.
    */
   private static void react(ServerPlayer self, ReinhardState state,
      dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind kind, net.minecraft.world.phys.Vec3 source) {
      var delta = source.subtract(self.getEyePosition());
      float yaw = net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)) - self.getYRot());
      float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
      // Never force a front palm through the torso toward a rear threat.
      if(Math.abs(yaw)>100 && kind!=dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.BRACE)
         kind=dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.EVADE;
      boolean changed=state.defense.contact(kind, yaw, pitch);
      if(changed || state.defense.age()>=8) state.defense.aim(state.reid.phase()!=Reid.Phase.SHEATHED || state.action.running());
   }

   @Override
   public boolean blocksContinuousRay(ServerPlayer self, net.minecraft.world.phys.Vec3 source, net.minecraft.world.phys.Vec3 contact) {
      if (!self.isAlive()) return false;
      ReinhardState state = ReinhardState.ensure(self);
      react(self, state, dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.BEAM, source);
      state.reid.combat();
      if (self.tickCount % 4 == 0) {var palm=defenseContact(self);HeroFx.flash(self,palm==null?contact:palm,.12F);}
      return true;
   }

   @Override
   public net.minecraft.world.phys.Vec3 defenseContact(Player player) {
      net.minecraft.world.phys.Vec3 local=null;
      if(player instanceof ServerPlayer) {
         var s=ReinhardState.of(player); if(s!=null && s.defense.kind()!=dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.EVADE)local=s.defense.palm();
      } else if(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer hp) {
         var snap=hp.getHeroSnapshot();
         if(snap!=null && snap.heroId()==id() && dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.kind(snap.heroFlags())!=dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.NONE && dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.kind(snap.heroFlags())!=dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.EVADE) local=snap.actionTarget();
      }
      return local==null ? null : dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseContact.world(player,local);
   }

   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      ReinhardState state = ReinhardState.ensure(self);
      WindReading.onAttacked(self, state, source.getEntity());
      if (ReidLife.isCombat(source.getEntity(), source.getDirectEntity())) {
         state.reid.combat();
      }

      if (!isDivine(source)) {
         if(source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            var at=source.getSourcePosition();
            if(at!=null)react(self,state,dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.BRACE,at);
         }
         if (Parry.parries(self, source, false)) {
            var attacker = (LivingEntity)source.getEntity();
            var context=dev.baranhan.viltrumitecore.hero.HeroAttackContext.current();
            var kind = context==dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.WAVE ? dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.BRACE
               : context==dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.SHOT ? dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.PROJECTILE
               : raw >= 20 ? dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.EVADE
               : attacker.getMainHandItem().isEmpty() ? dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.PALM
               : dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.WEAPON;
            boolean idle = state.defense.kind() == dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.NONE;
            react(self, state, kind, attacker.getEyePosition());
            if (idle) Parry.effects(self, attacker);
            return DamageAbsorb.ABSORBED;
         }

         return absorb(false, raw);
      }

      DivineStep step = divineStep(state.adaptation, divineType(source));
      state.adaptation = step.next();
      if (step.result().absorbed()) {
         HeroFx.flash(self, self.position().add(0.0, 1.0, 0.0));
      }

      return step.result();
   }

   /**
    * Spec §3.1: ordinary damage never takes him below 1 HP. Divine damage is not
    * floored: a lethal divine hit is the Phoenix's (spec §4.2) or death.
    */
   @Override
   public float clampFinalDamage(ServerPlayer self, DamageSource source, float afterArmor) {
      boolean divine = isDivine(source);
      if (Phoenix.saves(divine, self.getHealth(), afterArmor, PersistentCooldowns.get(self, ReinhardRules.PHOENIX_KEY))) {
         Phoenix.revive(self, source.getEntity());
         return 0.0F;
      }

      return clamp(divine, self.getHealth(), afterArmor);
   }

   /** One divine hit through Protection Acquisition: the result and the next state. */
   public record DivineStep(DamageAbsorb result, Adaptation next) {
   }

   /** Pure: an adapted type is absorbed (state unchanged); any other divine hit counts and lands. */
   static DivineStep divineStep(Adaptation adaptation, ResourceLocation type) {
      if (adaptation.immuneTo(type)) {
         return new DivineStep(DamageAbsorb.ABSORBED, adaptation);
      }

      return new DivineStep(DamageAbsorb.PASS, adaptation.onDivineHit(type));
   }

   /** Pure: ordinary damage never touches Protection Acquisition (spec §4.8). */
   static Adaptation ordinaryStep(Adaptation adaptation) {
      return adaptation;
   }

   private static ResourceLocation divineType(DamageSource source) {
      return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(DivineDamage.DIVINE.location());
   }

   /**
    * Arrow Protection (spec §4.3): every projectile is turned aside; the catch and
    * reflect windows (Restrained palm / Knight backhand, stage 3) win over the passive. Mode-independent.
    */
   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      ReinhardState state = ReinhardState.of(self);
      if (state == null) return ProjectileResponse.DEFLECT;
      react(self, state, projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow
         && !(projectile instanceof net.minecraft.world.entity.projectile.ThrownTrident) && !state.arrowCapture.holding()
         ? dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.ARROW
         : dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.PROJECTILE, projectile.position());
      // A real projectile keeps its speed, ownership and (for missiles) target in the shared seam.
      if (projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow
         && !(projectile instanceof net.minecraft.world.entity.projectile.ThrownTrident))
         return state.arrowCapture.holding() || state.defense.kind()==dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind.EVADE ? ProjectileResponse.DEFLECT : ProjectileResponse.CATCH;
      return ProjectileResponse.REFLECT;
   }

   @Override
   public boolean captureProjectile(ServerPlayer self, Projectile projectile) {
      return projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
         && ReinhardState.ensure(self).arrowCapture.start(self, arrow);
   }

   /** Pure: the open palm windows (stage 3) win over the passive deflection; no state = no window. */
   static ProjectileResponse projectileResponse(@javax.annotation.Nullable ReinhardState state) {
      return ArrowWard.response(state != null && CatchWindow.isOpen(state),
         state != null && dev.baranhan.viltrumitecore.hero.reinhard.ability.ReflectWindow.isOpen(state));
   }

   /** Spec §3.1: void and generic_kill never take him below 1 HP (/kill still kills: AdminKill). */
   @Override
   public float clampUnblockableDamage(ServerPlayer self, DamageSource source, float amount) {
      return ReinhardRules.floor(self.getHealth(), amount);
   }

   /** Pure absorb step of the chassis. */
   static DamageAbsorb absorb(boolean divine, float raw) {
      return divine ? DamageAbsorb.PASS : DamageAbsorb.reduceTo(ReinhardRules.resist(raw));
   }

   /** Pure clamp step of the chassis. */
   static float clamp(boolean divine, float health, float afterArmor) {
      return divine ? afterArmor : ReinhardRules.floor(health, afterArmor);
   }

   private static boolean isDivine(DamageSource source) {
      return DivineDamage.isDivine(source);
   }

   /** Dealing damage is combat: Reid's 60 s auto-return starts over (spec §7.3). */
   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      ReinhardState state = ReinhardState.of(attacker);
      if (state != null && amount > 0.0F) {
         state.reid.combat();
      }

      return amount;
   }

   /** Spec §3.3: Reinhard is a divine being. */
   @Override
   public boolean isDivine(Player self) {
      return true;
   }

   /** Base max health stays vanilla 20 (no modifier); knockback resistance 100 %; start full. */
   private static void applyChassis(ServerPlayer player) {
      AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
      if (knockback != null) {
         applyKnockbackResistance(knockback);
      }

      player.setHealth(player.getMaxHealth());
   }

   /** Idempotent: exactly one chassis modifier afterwards. */
   static void applyKnockbackResistance(AttributeInstance instance) {
      instance.removeModifier(KNOCKBACK_MODIFIER_ID);
      instance.addPermanentModifier(new AttributeModifier(KNOCKBACK_MODIFIER_ID, "Reinhard knockback resistance",
         ReinhardRules.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION));
   }

   static void removeKnockbackResistance(AttributeInstance instance) {
      instance.removeModifier(KNOCKBACK_MODIFIER_ID);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      ReinhardState state = ReinhardState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   /** Login: the page follows the saved mode. */
   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      ReinhardState state = ReinhardState.ensure(player);
      state.load(nbt);
      if (player instanceof ServerPlayer serverPlayer) {
         setPage(serverPlayer, state.mode.page());
      }
   }

   /** Mode and cooldowns carry over to the respawned body; adaptations do not (spec §4.8). The Phoenix timer is not hero state. */
   @Override
   public void cloneHeroState(Player original, Player clone) {
      ReinhardState previous = ReinhardState.of(original);
      if (previous != null && clone instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   /** Mode in heroFlags bits 0-1; ability cooldowns at their ordinal, mode cooldown at EXTRA_MODE_CD. */
   @Override
   public int heldTargetId(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot view = player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer hp ? hp.getHeroSnapshot() : null;
         return view != null && view.heroId() == id() && ReinhardFlags.has(view.heroFlags(), ReinhardFlags.GRAB_HOLDING)
            ? view.controlTargetId() : -1;
      }
      ReinhardState state = ReinhardState.of(player);
      return state == null || state.grab == null ? -1 : state.grab.targetId();
   }

   @Override
   public net.minecraft.world.phys.Vec3 heldTargetPosition(Player player, net.minecraft.world.entity.Entity target, float partialTick) {
      return dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.anchor(
         player.getPosition(partialTick), net.minecraft.util.Mth.rotLerp(partialTick, player.yRotO, player.getYRot()), target.getBbWidth(), target.getBbHeight());
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      ReinhardState state = ReinhardState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }

      int sceneFlags = 0;
      // A level-owned carve/scene can outlive its caster's timeline. Advertise the same
      // admission locks that the server uses, including another player's active cast.
      if (player instanceof net.minecraft.server.level.ServerPlayer server) {
         if (dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutManager.get(server.serverLevel()).size() >= 2)
            sceneFlags |= 1 << ReinhardFlags.WORLD_CUT_ACTIVE;
         if (dev.baranhan.viltrumitecore.hero.control.WorldPause.active(server.getServer()))
            sceneFlags |= 1 << ReinhardFlags.WORLD_ERASURE_ACTIVE;
      }
      return composeSnapshot(state, PersistentCooldowns.get(player, ReinhardRules.PHOENIX_KEY), sceneFlags);
   }

   /** Shared production composer; pure state input permits testing the full wire snapshot. */
   static HeroPublicSnapshot composeSnapshot(ReinhardState state, int phoenixTicks, int sceneFlags) {
      int[] extra = new int[HeroPublicSnapshot.EXTRA_COOLDOWN_MAX];
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         extra[ability.snapshotIndex()] = state.cooldowns[ability.ordinal()];
      }
      extra[ReinhardFlags.EXTRA_MODE_CD] = state.modeCooldown;
      extra[ReinhardFlags.EXTRA_PHOENIX] = phoenixSeconds(phoenixTicks);
      int flags = ReinhardFlags.withMode(sceneFlags, state.mode);
      if (state.parryTicks > 0) flags |= 1 << ReinhardFlags.PARRY_POSE;
      flags |= state.defense.flags();
      flags = stateFlags(state, flags);
      flags = Reid.withFlags(flags, state.reid.phase());
      ActionTimeline action = state.action;
      // resource = Reid's idle ticks left while drawn (the HUD bar, spec §9.1).
      return new HeroPublicSnapshot(HeroId.REINHARD, action.wireId(), action.elapsed(), action.length(), 0, false, 0, 0, false, false, 0, 0,
         action.targetId(), new int[HeroPublicSnapshot.COOLDOWN_COUNT], action.running(), -1, state.defense.palm(), state.reid.idleLeft(), false, flags, extra, action.phase(), state.arrowCapture.entityId());
   }

   /** Stage 3 window / hold bits of the snapshot (catch, reflect, grab, hit-stop). */
   static int stateFlags(ReinhardState state, int flags) {
      if (CatchWindow.isOpen(state)) {
         flags |= 1 << ReinhardFlags.CATCH_WINDOW;
      }

      if (dev.baranhan.viltrumitecore.hero.reinhard.ability.ReflectWindow.isOpen(state)) {
         flags |= 1 << ReinhardFlags.REFLECT_WINDOW;
      }

      if (state.grab != null) {
         flags |= 1 << ReinhardFlags.GRAB_HOLDING;
      }

      if(state.worldErasure!=null && !state.worldErasure.ended()) flags|=1<<ReinhardFlags.WORLD_ERASURE_ACTIVE;
      return flags;
   }

   /** Phoenix cooldown for the snapshot, whole seconds rounded up. */
   static int phoenixSeconds(int ticks) {
      return ticks <= 0 ? 0 : (ticks + 19) / 20;
   }

   /** A dimension change ends every action (grab, windows, timeline). */
   @Override
   public void onDimensionChange(ServerPlayer player) {
      ReinhardState state = ReinhardState.of(player);
      if (state != null) {
         ReinhardActions.cleanup(player, state, null);
      }
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      ReinhardState state = ReinhardState.of(player);
      if (state != null) {
         ReinhardActions.cleanup(player, state, reason);
         if (reason == CleanupReason.ERASED && player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) {
            heroPlayer.viltrumitecore$setHeroState(state.erasedCopy());
         }
      }

      if (reason != CleanupReason.DISCONNECT) {
         // Wind Reading outlines end with the hero / the body (a logout drops the client sections anyway).
         WindReading.clear(player);
      }
      if (reason == CleanupReason.HERO_CHANGE) {
         setPage(player, 0);
         AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
         if (knockback != null) {
            removeKnockbackResistance(knockback);
         }
      }
   }
}
