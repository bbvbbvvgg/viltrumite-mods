package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Captain America — Steve Rogers, MCU 2012 (spec docs/design/2026-11-03-captain-america-design.md).
 * Own chassis, no Viltrumite kit, no flight. The shield is a world object: in the hand it guards,
 * parries and strikes; thrown it ricochets; the ultimate calls the Avengers.
 */
public final class CaptainHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c01");
   static final UUID ARMOR_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c02");
   static final UUID TOUGHNESS_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c03");
   static final UUID SPEED_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c04");
   static final UUID KNOCKBACK_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c05");
   /** Movement while guarding, rooted moves (removed when idle). */
   static final UUID GUARD_SLOW_ID = UUID.fromString("c4a7a2b0-1942-4e1a-9c2f-0d5a7e3b1c06");
   public static final CaptainHero INSTANCE = new CaptainHero();

   private CaptainHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.CAPTAIN;
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
   public boolean hasAbilityPanel(Player player) {
      return true;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return CaptainAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return CaptainAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return CaptainAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return CaptainAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return CaptainAbilities.panelAbilities();
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return CaptainLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return CaptainAbilities.icon(abilityId);
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      CaptainState state = target instanceof Player p ? CaptainState.of(p) : null;
      // The ceremony pose is not broken by grabs or freezes; everything else is a normal body.
      return state == null || state.move == null || !state.move.ceremony();
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   // --- mouse (spec §5.1-5.2) ---

   @Override
   public boolean claimsBlockAttack(Player player) {
      return true;
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return false;
   }

   /** Items and interactive things own RMB (the wish, food, chests, doors, villagers); see {@link CaptainUse}. */
   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return CaptainUse.yields(player);
   }

   /** A plain block under the crosshair does not stop the shield block; an interactive one (chest, door) does. */
   @Override
   public boolean claimsBlockUse(Player player) {
      return !CaptainUse.yields(player);
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return switch (button) {
         case PRIMARY -> HeroAction.PRIMARY_ATTACK;
         case SECONDARY -> HeroAction.SECONDARY_USE;
         default -> null;
      };
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }
      CaptainState state = CaptainState.ensure(player);
      if (state.move != null && state.move.ceremony()) {
         return false;
      }
      return switch (action) {
         case PRIMARY_ATTACK -> true;
         case SECONDARY_USE -> true;
         case JUMP, AIR_STEP -> state.move == null || !state.move.roots();
         default -> CaptainAbility.byAction(action) != null;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      CaptainSounds.play(player, CaptainSounds.REFUSE, 0.6F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      CaptainState state = CaptainState.ensure(player);
      switch (action) {
         case PRIMARY_ATTACK -> {
            if (pressed) CaptainCombat.primary(player, state);
         }
         case SECONDARY_USE -> CaptainCombat.guard(player, state, pressed);
         default -> {
            CaptainAbility ability = CaptainAbility.byAction(action);
            if (ability != null) {
               CaptainSkills.input(player, state, ability, pressed);
            }
         }
      }
   }

   @Override
   public void handleForcedRelease(ServerPlayer player, HeroAction action) {
      this.handleInput(player, action, false);
   }

   // --- lifecycle ---

   @Override
   public void tick(ServerPlayer player) {
      CaptainState state = CaptainState.ensure(player);
      state.tickCount++;
      if (state.shieldBroken == 1) {
         CaptainSounds.play(player, CaptainSounds.GUARD_UP, 0.6F, 1.4F); // the arm can raise the shield again
      }
      state.tickTimers();
      if (player.isAlive() && state.sinceHurt >= CaptainRules.REGEN_DELAY && ++state.regenTicks >= CaptainRules.REGEN_INTERVAL) {
         state.regenTicks = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(CaptainRules.REGEN_AMOUNT);
         }
      }
      if (state.kinetic > 0 && ++state.kineticIdle > CaptainRules.KINETIC_IDLE && state.tickCount % CaptainRules.KINETIC_DECAY_INTERVAL == 0) {
         state.kinetic = Math.max(0, state.kinetic - 5);
      }
      if (state.tickCount % 20 == 0) {
         applyChassis(player, false);
      }
      CaptainCombat.tick(player, state);
      CaptainSkills.tick(player, state);
      AvengersTeam.tick(player, state);
      slow(player, state.guarding || state.move != null && state.move.roots(), state.move != null && state.move.roots() ? -0.9 : CaptainRules.GUARD_SLOW);
   }

   @Override
   public void enter(ServerPlayer player) {
      CaptainState.ensure(player);
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(0);
      }
      applyChassis(player, true);
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      if (player != null) {
         applyChassis(player, true);
      }
   }

   /** Chassis modifiers (spec §4), idempotent; {@code heal} fills health after becoming him. */
   static void applyChassis(ServerPlayer player, boolean heal) {
      modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Captain health", CaptainRules.HEALTH_BONUS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Captain armor", CaptainRules.ARMOR, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Captain toughness", CaptainRules.TOUGHNESS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Captain speed", CaptainRules.SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE);
      modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Captain footing", CaptainRules.KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
      if (heal) {
         player.setHealth(player.getMaxHealth());
      }
   }

   private static void modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
      }
   }

   private static void slow(ServerPlayer player, boolean on, double amount) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      AttributeModifier current = speed.getModifier(GUARD_SLOW_ID);
      if (!on) {
         if (current != null) speed.removeModifier(GUARD_SLOW_ID);
         return;
      }
      if (current != null && current.getAmount() == amount) {
         return;
      }
      if (current != null) speed.removeModifier(GUARD_SLOW_ID);
      speed.addTransientModifier(new AttributeModifier(GUARD_SLOW_ID, "Captain guard", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
   }

   static void removeChassis(ServerPlayer player) {
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID},
         {Attributes.MOVEMENT_SPEED, SPEED_ID}, {Attributes.MOVEMENT_SPEED, GUARD_SLOW_ID}, {Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID}}) {
         AttributeInstance instance = player.getAttribute((Attribute)pair[0]);
         if (instance != null) {
            instance.removeModifier((UUID)pair[1]);
         }
      }
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      CaptainState state = CaptainState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      CaptainState state = CaptainState.of(player);
      if (state != null) {
         CaptainSkills.cleanup(player, state);
         AvengersTeam.dismiss(player, state, false);
         state.endMove();
         state.guarding = false;
      }
      slow(player, false, 0);
      if (reason == CleanupReason.HERO_CHANGE) {
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      CaptainState state = CaptainState.of(player);
      if (state != null) {
         CaptainSkills.cleanup(player, state);
         AvengersTeam.dismiss(player, state, false);
         state.endMove();
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      CaptainState state = CaptainState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      CaptainState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      CaptainState previous = CaptainState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- body (spec §4) ---

   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   /** No fall damage up to {@link CaptainRules#SAFE_FALL}; beyond it a reduced share (a roll on landing halves the height). */
   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      CaptainState state = CaptainState.of(player);
      if (state == null) {
         return;
      }
      CaptainSkills.onLanded(player, state, fallDistance);
      float distance = state.is(CaptainMove.ROLL) ? fallDistance - CaptainRules.ROLL_FALL : fallDistance;
      if (state.is(CaptainMove.SLAM_LEAP) || state.is(CaptainMove.SLAM_IMPACT)) {
         return;
      }
      float factor = CaptainRules.fallFactor(distance);
      if (factor > 0.0F) {
         player.hurt(player.damageSources().fall(), Math.max(0.0F, distance - 3.0F) * factor);
      }
      if (fallDistance > 5.0F && !state.running()) {
         state.start(CaptainMove.LANDING);
         CaptainFx.around(player, CaptainFx.GROUND, 2, player.position(), new Vec3(Math.min(3.5, 1.2 + fallDistance * 0.1), 0, 0));
         CaptainSounds.play(player, CaptainSounds.LANDING, 0.9F, 1.0F);
      }
   }

   @Override
   public boolean knockbackImmune(Player player) {
      CaptainState state = CaptainState.of(player);
      return state != null && state.move != null && state.move.unstoppable();
   }

   @Override
   public boolean interceptsProjectiles(Player self) {
      if (self == null) {
         return true; // the hero can turn projectiles away (seam probe without a player)
      }
      HeroPublicSnapshot s = self instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
      if (!self.level().isClientSide) {
         CaptainState state = CaptainState.of(self);
         return state != null && state.guarding && state.shieldInHand();
      }
      return s != null && s.heroId() == HeroId.CAPTAIN && CaptainFlags.has(s.heroFlags(), CaptainFlags.GUARDING)
         && !CaptainFlags.has(s.heroFlags(), CaptainFlags.SHIELD_OUT);
   }

   /** A raised shield sends frontal projectiles back where they came from (spec §5.2). */
   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      CaptainState state = CaptainState.of(self);
      if (state == null || !state.guarding || !state.shieldInHand()) {
         return ProjectileResponse.PASS;
      }
      Vec3 from = projectile.position().subtract(projectile.getDeltaMovement().scale(3.0));
      if (!CaptainCombat.frontal(self, from)) {
         return ProjectileResponse.PASS;
      }
      CaptainCombat.onBlocked(self, state, projectile.getOwner() instanceof LivingEntity l ? l : null, true, projectile.position());
      return ProjectileResponse.REFLECT;
   }

   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      CaptainState state = CaptainState.of(self);
      if (state == null) {
         return DamageAbsorb.PASS;
      }
      if (state.iframes > 0 && source.getEntity() != null) {
         return DamageAbsorb.ABSORBED;
      }
      float amount = raw;
      if (state.guarding) {
         DamageAbsorb guarded = CaptainCombat.absorb(self, state, source, raw);
         if (guarded != null && guarded.absorbed()) {
            return guarded;
         }
         if (guarded != null) {
            amount = guarded.passOn();
         }
      }
      if (state.move != null && state.move.ceremony()) {
         amount *= CaptainRules.CEREMONY_DAMAGE_FACTOR;
      }
      return amount < raw ? DamageAbsorb.reduceTo(amount) : DamageAbsorb.PASS;
   }

   // --- contextual interactions (interaction/) ---

   /** The raised, intact vibranium shield: {@code weapon.shield} in GUARD. Server side; NONE otherwise. */
   @Override
   public dev.baranhan.viltrumitecore.hero.combat.CombatPresence combatPresence(Player player) {
      CaptainState state = player.level().isClientSide() ? null : CaptainState.of(player);
      if (state != null && state.guarding && state.shieldInHand() && state.shieldBroken == 0) {
         return SHIELD_GUARD;
      }
      return dev.baranhan.viltrumitecore.hero.combat.CombatPresence.NONE;
   }

   public static final dev.baranhan.viltrumitecore.hero.combat.CombatPresence SHIELD_GUARD = new dev.baranhan.viltrumitecore.hero.combat.CombatPresence(
      "viltrumitecore:vibranium_shield", java.util.Set.of(dev.baranhan.viltrumitecore.hero.combat.CombatTags.WEAPON_SHIELD,
         dev.baranhan.viltrumitecore.hero.combat.CombatTags.WEAPON_SHIELD + ".vibranium"),
      dev.baranhan.viltrumitecore.hero.combat.CombatStance.GUARD, 0);

   @Override
   public boolean guardFaces(ServerPlayer self, Vec3 from) {
      CaptainState state = CaptainState.of(self);
      return state != null && state.guarding && state.shieldInHand() && state.shieldBroken == 0 && CaptainCombat.frontal(self, from);
   }

   @Override
   public boolean react(ServerPlayer self, dev.baranhan.viltrumitecore.interaction.InteractionReaction reaction,
      dev.baranhan.viltrumitecore.interaction.InteractionContext ctx) {
      CaptainState state = CaptainState.of(self);
      if (state == null || !state.guarding || !state.shieldInHand()) {
         return false;
      }
      switch (reaction) {
         case GUARD_SHATTERED -> CaptainCombat.shatterGuard(self, state, dev.baranhan.viltrumitecore.interaction.InteractionTuning.SHATTER_TICKS);
         case GUARD_BRACED -> {
            CaptainCombat.teamBrace(self, state, dev.baranhan.viltrumitecore.interaction.InteractionTuning.STORM_KINETIC);
            dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.say(self, CaptainLines.STORM_SHIELD);
         }
         case METEOR_STOPPED -> CaptainCombat.teamBrace(self, state, dev.baranhan.viltrumitecore.interaction.InteractionTuning.METEOR_KINETIC);
         default -> {
            return false;
         }
      }
      return true;
   }

   /** Every hit Steve lands marks that enemy for the team. */
   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      if (amount > 0.0F) {
         mark(attacker, target);
      }
      return amount;
   }

   @Override
   public void onAttacking(ServerPlayer self, LivingEntity target, DamageSource source) {
      mark(self, target);
   }

   @Override
   public void onAttacked(ServerPlayer self, LivingEntity attacker, DamageSource source) {
      CaptainState state = CaptainState.of(self);
      if (state != null && attacker != self && attacker.isAlive() && !AvengersTeam.ally(self, attacker)) {
         state.lastAttackerId = attacker.getId();
         state.lastAttackerTicks = 100;
      }
   }

   private static void mark(ServerPlayer attacker, LivingEntity target) {
      CaptainState state = CaptainState.of(attacker);
      if (state != null && target != attacker && target.isAlive() && !AvengersTeam.ally(attacker, target)) {
         state.markTarget = target.getUUID();
         state.markTicks = 200;
         state.idle = 0;
      }
   }

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      CaptainState state = CaptainState.of(player);
      if (state != null) {
         state.sinceHurt = 0;
         state.idle = 0;
      }
   }

   // --- snapshot ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      CaptainState state = CaptainState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.CAPTAIN, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      return compose(state);
   }

   static HeroPublicSnapshot compose(CaptainState state) {
      int[] extra = state.cooldowns.clone();
      int flags = 0;
      flags = CaptainFlags.set(flags, CaptainFlags.SHIELD_OUT, !state.shieldInHand());
      flags = CaptainFlags.set(flags, CaptainFlags.GUARDING, state.guarding);
      flags = CaptainFlags.set(flags, CaptainFlags.PARRY_WINDOW, state.guarding && state.guardTicks < CaptainRules.PARRY_WINDOW);
      flags = CaptainFlags.set(flags, CaptainFlags.COUNTER_READY, state.counterLeft > 0);
      flags = CaptainFlags.set(flags, CaptainFlags.CATCH_BONUS, state.catchLeft > 0);
      flags = CaptainFlags.set(flags, CaptainFlags.EMPOWERED, CaptainRules.empowered(state.kinetic));
      flags = CaptainFlags.set(flags, CaptainFlags.IN_COMBAT, state.idle < 100);
      flags = CaptainFlags.set(flags, CaptainFlags.TEAM_ACTIVE, state.scene != null);
      flags = CaptainFlags.set(flags, CaptainFlags.AIMING, state.is(CaptainMove.AIM));
      flags = CaptainFlags.set(flags, CaptainFlags.IFRAMES, state.iframes > 0);
      flags = CaptainFlags.set(flags, CaptainFlags.SHIELD_BROKEN, state.shieldBroken > 0);
      CaptainMove move = state.move;
      int teamLeft = state.scene == null ? 0 : state.scene.fightLeft();
      return new HeroPublicSnapshot(HeroId.CAPTAIN, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, teamLeft, CaptainRules.FIGHT_TICKS, false, false, 0, 0, state.shieldId, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, state.aimPoint, state.kinetic, false, flags, extra, Math.max(0, state.comboStep), state.scene == null ? 0 : state.scene.alive());
   }
}
