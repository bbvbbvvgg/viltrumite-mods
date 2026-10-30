package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sung Jinwoo, the Shadow Monarch (spec docs/design/2026-10-28-jinwoo-design.md).
 * Own chassis, no Viltrumite kit, no flight; a real shadow army.
 */
public final class JinwooHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("5b1f7c62-7d2e-4f51-9a63-0c4c1f1e9a01");
   static final UUID ARMOR_ID = UUID.fromString("5b1f7c62-7d2e-4f51-9a63-0c4c1f1e9a02");
   static final UUID TOUGHNESS_ID = UUID.fromString("5b1f7c62-7d2e-4f51-9a63-0c4c1f1e9a03");
   static final UUID SPEED_ID = UUID.fromString("5b1f7c62-7d2e-4f51-9a63-0c4c1f1e9a04");
   public static final JinwooHero INSTANCE = new JinwooHero();

   private JinwooHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.JINWOO;
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
      return true;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return JinwooAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return JinwooAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return JinwooAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return JinwooAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return JinwooAbilities.panelAbilities();
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return JinwooLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return JinwooAbilities.icon(abilityId);
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   // --- mouse (spec §5.1-5.3) ---

   /** Daggers drawn: server state, client snapshot flag. */
   public static boolean drawn(Player player) {
      if (!player.level().isClientSide) {
         JinwooState state = JinwooState.of(player);
         return state != null && state.daggers;
      }
      HeroPublicSnapshot s = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
      return s != null && s.heroId() == HeroId.JINWOO && JinwooFlags.has(s.heroFlags(), JinwooFlags.DAGGERS);
   }

   @Override
   public boolean claimsBlockAttack(Player player) {
      return drawn(player);
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return false;
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return switch (button) {
         case PRIMARY -> HeroAction.PRIMARY_ATTACK;
         case SECONDARY -> drawn(player) ? HeroAction.SECONDARY_USE : null;
         case MIDDLE -> HeroAction.MODE_CYCLE;
         default -> null;
      };
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }
      JinwooState state = JinwooState.ensure(player);
      return switch (action) {
         case PRIMARY_ATTACK -> !state.running() || state.move.isCombo();
         case SECONDARY_USE -> state.daggers && state.cooldowns[JinwooAbility.BLOCK_CD] <= 0;
         case MODE_CYCLE -> !state.running() && !state.blocking;
         case JUMP, AIR_STEP -> state.move == null || !state.move.roots();
         default -> JinwooAbility.byAction(action) != null;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      JinwooSounds.play(player, JinwooSounds.REFUSE, 0.7F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      JinwooState state = JinwooState.ensure(player);
      switch (action) {
         case PRIMARY_ATTACK -> {
            if (pressed) JinwooCombat.primary(player, state);
         }
         case SECONDARY_USE -> JinwooCombat.block(player, state, pressed);
         case MODE_CYCLE -> {
            if (pressed) JinwooCombat.toggleDaggers(player, state);
         }
         default -> {
            JinwooAbility ability = JinwooAbility.byAction(action);
            if (ability == null) {
               return;
            }
            if (pressed) {
               JinwooActions.press(player, state, ability);
            } else {
               JinwooActions.release(player, state, action);
            }
         }
      }
   }

   // --- lifecycle ---

   @Override
   public void tick(ServerPlayer player) {
      JinwooState state = JinwooState.ensure(player);
      state.tickCount++;
      state.tickCooldowns();
      if (state.sinceHurt < 10000) state.sinceHurt++;
      boolean domain = state.inDomain(player.position());
      if (state.tickCount % JinwooRules.MANA_REGEN_INTERVAL == 0) {
         state.mana = JinwooRules.regen(state.mana, 0, domain);
      }
      if (player.isAlive() && state.sinceHurt >= JinwooRules.REGEN_DELAY && ++state.regenTicks >= JinwooRules.REGEN_INTERVAL) {
         state.regenTicks = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(JinwooRules.REGEN_AMOUNT);
         }
      }
      if (state.tickCount % 5 == 0) {
         state.remnantNear = Remnants.nearest(player.serverLevel(), player.position(), JinwooRules.EXTRACT_RADIUS) != null;
      }
      if (state.tickCount % 20 == 0) {
         applyChassis(player, false);
      }
      JinwooCombat.tick(player, state);
      JinwooActions.tick(player, state);
      MonarchArise.tickDomain(state);
      ShadowArmy.tick(player, state);
   }

   @Override
   public void enter(ServerPlayer player) {
      JinwooState state = JinwooState.ensure(player);
      state.unstash(player);
      state.roster.seedIfEmpty();
      state.mana = JinwooRules.MANA_MAX;
      setPage(player, 0);
      applyChassis(player, true);
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      if (player == null) {
         return;
      }
      applyChassis(player, true);
      JinwooState state = JinwooState.of(player);
      if (state != null) {
         state.mana = JinwooRules.MANA_MAX;
      }
   }

   private static void setPage(ServerPlayer player, int page) {
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(page);
      }
   }

   /** Chassis modifiers (spec §4), idempotent; {@code heal} fills health after becoming him. */
   static void applyChassis(ServerPlayer player, boolean heal) {
      modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Jinwoo health", JinwooRules.HEALTH_BONUS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Jinwoo armor", JinwooRules.ARMOR, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Jinwoo toughness", JinwooRules.TOUGHNESS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Jinwoo speed", JinwooRules.SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE);
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

   static void removeChassis(ServerPlayer player) {
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID},
         {Attributes.MOVEMENT_SPEED, SPEED_ID}}) {
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
      JinwooState state = JinwooState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      JinwooState state = JinwooState.of(player);
      if (state != null) {
         JinwooActions.cleanup(player, state);
         ShadowArmy.dissolveAll(player, state);
         if (reason == CleanupReason.HERO_CHANGE) {
            state.stash(player);
         }
      }
      if (reason == CleanupReason.HERO_CHANGE) {
         setPage(player, 0);
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      JinwooState state = JinwooState.of(player);
      if (state != null) {
         JinwooActions.cleanup(player, state);
         ShadowArmy.dissolveAll(player, state);
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      JinwooState state = JinwooState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      JinwooState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      JinwooState previous = JinwooState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- body (spec §4) ---

   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      JinwooState state = JinwooState.of(player);
      if (state != null && fallDistance > JinwooRules.LANDING_BURST_HEIGHT && !state.is(JinwooMove.DIVE)) {
         JinwooFx.around(player, JinwooFx.LANDING, 0, player.position(), new Vec3(Math.min(4.0, 1.5 + fallDistance * 0.12), 0, 0));
         JinwooSounds.play(player, JinwooSounds.LANDING, 1.0F, 1.0F);
      }
   }

   @Override
   public int airJumps(Player player) {
      return JinwooRules.AIR_JUMPS;
   }

   @Override
   public double airJumpVelocity(Player player) {
      return JinwooRules.AIR_JUMP_VELOCITY;
   }

   @Override
   public void onAirStep(ServerPlayer player) {
      JinwooFx.around(player, JinwooFx.LANDING, 1, player.position(), new Vec3(1.2, 0, 0));
      JinwooSounds.play(player, JinwooSounds.AIR_STEP, 0.8F, 1.1F);
   }

   @Override
   public boolean knockbackImmune(Player player) {
      JinwooState state = JinwooState.of(player);
      return state != null && state.move != null && state.move.unstoppable();
   }

   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      JinwooState state = JinwooState.of(self);
      if (state == null) {
         return DamageAbsorb.PASS;
      }
      if (state.iframes > 0 && source.getEntity() != null) {
         return DamageAbsorb.ABSORBED;
      }
      float amount = raw;
      if (state.blocking) {
         DamageAbsorb blocked = JinwooCombat.absorb(self, state, source, raw);
         if (blocked != null && blocked.absorbed()) {
            return blocked;
         }
         if (blocked != null) {
            amount = blocked.passOn();
         }
      }
      if (ShadowArmy.guarded(self, state)) {
         amount *= JinwooRules.GUARD_FACTOR;
      }
      return amount < raw ? DamageAbsorb.reduceTo(amount) : DamageAbsorb.PASS;
   }

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      JinwooState state = JinwooState.of(player);
      if (state == null) {
         return;
      }
      state.sinceHurt = 0;
      Entity attacker = source.getEntity();
      if (attacker instanceof LivingEntity living && living != player && !ShadowArmy.ally(player, living)) {
         state.lastAttackerId = living.getId();
         state.lastAttackerTicks = 100;
      }
   }

   // --- snapshot (spec §6.4) ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      JinwooState state = JinwooState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.JINWOO, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      int active = state.roster.activeCount();
      return compose(state, active, player.position());
   }

   static HeroPublicSnapshot compose(JinwooState state, int active, Vec3 at) {
      int[] extra = new int[JinwooAbility.COOLDOWNS];
      System.arraycopy(state.cooldowns, 0, extra, 0, Math.min(extra.length, state.cooldowns.length));
      int flags = 0;
      flags = JinwooFlags.set(flags, JinwooFlags.DAGGERS, state.daggers);
      flags = JinwooFlags.set(flags, JinwooFlags.BLOCKING, state.blocking);
      flags = JinwooFlags.set(flags, JinwooFlags.PARRY_WINDOW, state.blocking && state.blockTicks < JinwooRules.PARRY_WINDOW);
      flags = JinwooFlags.set(flags, JinwooFlags.DOMAIN, state.domainLeft > 0 && state.domainCenter != null);
      flags = JinwooFlags.set(flags, JinwooFlags.GUARD_MODE, state.order == JinwooState.ArmyOrder.GUARD);
      flags = JinwooFlags.set(flags, JinwooFlags.AUTHORITY_HOLD, state.is(JinwooMove.AUTHORITY_GRIP));
      flags = JinwooFlags.set(flags, JinwooFlags.UNSTOPPABLE, state.move != null && state.move.unstoppable());
      flags = JinwooFlags.set(flags, JinwooFlags.REMNANT_NEAR, state.remnantNear);
      flags = JinwooFlags.set(flags, JinwooFlags.ULT, state.is(JinwooMove.ARISE));
      flags = JinwooFlags.set(flags, JinwooFlags.DRAWING, state.drawLeft > 0);
      flags = JinwooFlags.set(flags, JinwooFlags.SHEATHING, state.sheathLeft > 0);
      flags = JinwooFlags.set(flags, JinwooFlags.IN_COMBAT, state.idle < 100);
      JinwooMove move = state.move;
      Vec3 target = state.domainCenter != null && state.domainLeft > 0 ? state.domainCenter : state.orderPoint;
      int domainLeft = state.domainCenter != null ? state.domainLeft : 0;
      return new HeroPublicSnapshot(HeroId.JINWOO, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, domainLeft, JinwooRules.ULT_DOMAIN, false, false, 0, 0, state.targetId, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, target, Math.round(state.mana * 10.0F), false, flags, extra, Math.max(0, state.comboStep),
         active * 100 + state.roster.size());
   }
}
