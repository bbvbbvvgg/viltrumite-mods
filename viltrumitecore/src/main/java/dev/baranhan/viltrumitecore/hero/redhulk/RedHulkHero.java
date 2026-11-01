package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.captain.CaptainUse;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Red Hulk (spec docs/design/2026-11-01-red-hulk-design.md): a 2.5-block gamma brute whose body is a furnace.
 * Own chassis, no Viltrumite kit, no flight. Every blow, ability and some incoming damage heat him up; the heat
 * makes him hit harder and burn the air around him, feeds Heat Blast and the ultimate, and at 1000 overheats him.
 */
public final class RedHulkHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d01");
   static final UUID ARMOR_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d02");
   static final UUID TOUGHNESS_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d03");
   static final UUID SPEED_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d04");
   static final UUID KNOCKBACK_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d05");
   /** Rooted moves, overheat and the ultimate recovery (removed when idle). */
   static final UUID SLOW_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d06");
   /** Thermal Fury's stride (removed when it ends). */
   static final UUID FURY_SPEED_ID = UUID.fromString("5e0d7c1a-4a7b-4c55-9d1e-7f2a3b6c8d07");
   public static final RedHulkHero INSTANCE = new RedHulkHero();

   private RedHulkHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.RED_HULK;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** Global balance profile: tier A brawler (below Reinhard S+ and Regulus S). */
   @Override
   public dev.baranhan.viltrumitecore.balance.HeroBalanceProfile balanceProfile(Player player) {
      return dev.baranhan.viltrumitecore.balance.BalanceTable.RAGE_TITAN;
   }

   @Override
   public float bodyScale(Player player) {
      return RedHulkRules.BODY_HEIGHT_SCALE;
   }

   @Override
   public float bodyWidthScale(Player player) {
      return RedHulkRules.BODY_WIDTH_SCALE;
   }

   @Override
   public double bodyReachBonus(Player player) {
      return RedHulkRules.BODY_REACH_BONUS;
   }

   @Override
   public double bodyStepBonus(Player player) {
      return RedHulkRules.BODY_STEP_BONUS;
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
      return RedHulkAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return RedHulkAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return RedHulkAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return RedHulkAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return RedHulkAbilities.panelAbilities();
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return RedHulkLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return RedHulkAbilities.icon(abilityId);
   }

   /** The ultimate charge and the vent are not broken by grabs or freezes; everything else is a normal body. */
   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      RedHulkState state = target instanceof Player p ? RedHulkState.of(p) : null;
      return state == null || state.move == null || !(state.move == RedHulkMove.ULT_CHARGE || state.move == RedHulkMove.ULT_RELEASE);
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   // --- mouse ---

   @Override
   public boolean claimsBlockAttack(Player player) {
      return true;
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return false;
   }

   /** Items and interactive things own RMB (food, the wish, chests, doors, villagers): the same rule as Captain America. */
   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return CaptainUse.yields(player);
   }

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
      RedHulkState state = RedHulkState.ensure(player);
      boolean locked = state.move != null && state.move.locks();
      return switch (action) {
         case PRIMARY_ATTACK, SECONDARY_USE -> !locked;
         case JUMP, AIR_STEP -> state.move == null || !state.move.roots() && !locked;
         default -> !locked && RedHulkAbility.byAction(action) != null;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      RedHulkSounds.play(player, RedHulkSounds.REFUSE, 0.6F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed) {
         return;
      }
      RedHulkState state = RedHulkState.ensure(player);
      switch (action) {
         case PRIMARY_ATTACK -> RedHulkCombat.primary(player, state);
         case SECONDARY_USE -> RedHulkCombat.secondary(player, state);
         default -> {
            RedHulkAbility ability = RedHulkAbility.byAction(action);
            if (ability != null) {
               RedHulkSkills.input(player, state, ability);
            }
         }
      }
   }

   @Override
   public void handleForcedRelease(ServerPlayer player, HeroAction action) {
      // every input is a press; nothing is held
   }

   // --- lifecycle ---

   @Override
   public void tick(ServerPlayer player) {
      RedHulkState state = RedHulkState.ensure(player);
      state.tickCount++;
      state.tickTimers();
      heat(player, state);
      if (state.tickCount % 20 == 0) {
         applyChassis(player, false);
      }
      RedHulkCombat.tick(player, state);
      RedHulkMove m = state.move;
      double slow = 0.0;
      if (m != null && m.roots()) {
         slow = -0.9;
      } else if (m == RedHulkMove.ULT_RECOVER) {
         slow = RedHulkRules.ULT_RECOVER_SLOW;
      } else if (m == RedHulkMove.HOLD || m == RedHulkMove.STAGGER) {
         slow = -0.35;
      } else if (state.overheated()) {
         slow = RedHulkRules.OVERHEAT_SLOW;
      }
      modifierNow(player, SLOW_ID, "Red Hulk weight", slow);
      modifierNow(player, FURY_SPEED_ID, "Red Hulk fury", state.furious() ? RedHulkRules.FURY_SPEED : 0.0);
   }

   /** Temperature: cooling, the fury creep, the overheat trigger, the aura and the tier cue. */
   static void heat(ServerPlayer player, RedHulkState state) {
      boolean wet = player.isInWaterRainOrBubble();
      boolean ult = state.move != null && state.move.ultimate();
      if (!ult && state.move != RedHulkMove.VENT) {
         state.heat = RedHulkRules.cool(state.heat, state.sinceHeat, state.furious(), state.overheated(), wet);
      }
      if (state.furious() && state.tickCount % 20 == 0) {
         state.addHeat(RedHulkRules.FURY_CREEP);
      }
      if (state.heat >= RedHulkRules.HEAT_MAX && !state.overheated() && !ult && player.isAlive()) {
         RedHulkSkills.overheat(player, state);
      }
      int tier = RedHulkRules.tier(state.heat);
      if (tier > state.lastTier && tier >= 2) {
         RedHulkSounds.play(player, RedHulkSounds.HEAT_UP, 0.9F, tier == 3 ? 0.85F : 1.0F);
      }
      state.lastTier = tier;
      if (tier >= 2 && state.tickCount % RedHulkRules.AURA_INTERVAL == 0 && player.isAlive()) {
         float damage = tier == 3 ? RedHulkRules.AURA_DAMAGE_BLAZING : RedHulkRules.AURA_DAMAGE;
         for (LivingEntity t : RedHulkTargets.around(player, player.position().add(0, 1.0, 0), RedHulkRules.AURA_RADIUS,
            e -> RedHulkTargets.auraMayHit(player, e))) {
            if (RedHulkTargets.strike(player, t, damage) && tier == 3) {
               t.setSecondsOnFire(RedHulkRules.AURA_IGNITE_SECONDS);
            }
         }
      }
      if (player.isOnFire() && state.heat < RedHulkRules.BLAZING) {
         state.addHeat(2); // standing in fire stokes the furnace
      }
   }

   @Override
   public void enter(ServerPlayer player) {
      RedHulkState state = RedHulkState.ensure(player);
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(0);
      }
      applyChassis(player, true);
      if (!state.running()) {
         state.start(RedHulkMove.ENTER);
      }
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      if (player != null) {
         applyChassis(player, true);
      }
   }

   static void applyChassis(ServerPlayer player, boolean heal) {
      modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Red Hulk health", RedHulkRules.HEALTH_BONUS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Red Hulk armor", RedHulkRules.ARMOR, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Red Hulk toughness", RedHulkRules.TOUGHNESS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Red Hulk speed", RedHulkRules.SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE);
      modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Red Hulk footing", RedHulkRules.KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
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

   /** A transient MULTIPLY_TOTAL speed modifier, replaced only when the amount changes (0 = removed). */
   private static void modifierNow(ServerPlayer player, UUID id, String name, double amount) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      AttributeModifier current = speed.getModifier(id);
      if (amount == 0.0) {
         if (current != null) speed.removeModifier(id);
         return;
      }
      if (current != null && current.getAmount() == amount) {
         return;
      }
      if (current != null) speed.removeModifier(id);
      speed.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
   }

   static void removeChassis(ServerPlayer player) {
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID},
         {Attributes.MOVEMENT_SPEED, SPEED_ID}, {Attributes.MOVEMENT_SPEED, SLOW_ID}, {Attributes.MOVEMENT_SPEED, FURY_SPEED_ID},
         {Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID}}) {
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
      RedHulkState state = RedHulkState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      state.overheat = 0;
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      RedHulkState state = RedHulkState.of(player);
      if (state != null) {
         RedHulkSkills.cleanup(player, state);
         state.fury = 0;
         if (reason == CleanupReason.HERO_CHANGE) {
            state.heat = 0;
            state.overheat = 0;
         }
      }
      modifierNow(player, SLOW_ID, "", 0.0);
      modifierNow(player, FURY_SPEED_ID, "", 0.0);
      if (reason == CleanupReason.HERO_CHANGE) {
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      RedHulkState state = RedHulkState.of(player);
      if (state != null) {
         RedHulkSkills.cleanup(player, state);
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      RedHulkState state = RedHulkState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      RedHulkState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      RedHulkState previous = RedHulkState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- body ---

   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   /** Falls below {@link RedHulkRules#SAFE_FALL} never hurt; a big fall lands with a small shockwave. */
   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      RedHulkState state = RedHulkState.of(player);
      if (state == null) {
         return;
      }
      boolean leap = state.is(RedHulkMove.LEAP_AIR);
      RedHulkSkills.onLanded(player, state, fallDistance);
      float damage = leap ? 0.0F : RedHulkRules.fallDamage(fallDistance);
      if (damage > 0.0F) {
         player.hurt(player.damageSources().fall(), damage);
      }
   }

   @Override
   public boolean knockbackImmune(Player player) {
      RedHulkState state = RedHulkState.of(player);
      return state != null && (state.furious() || state.move != null && state.move.unstoppable());
   }

   /** Fire and lava mostly feed the furnace; explosions partly. Fury, overheat and the ultimate scale what is left. */
   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      RedHulkState state = RedHulkState.of(self);
      if (state == null || raw <= 0.0F) {
         return DamageAbsorb.PASS;
      }
      float amount = raw;
      float absorbed = 0.0F;
      if (source.is(DamageTypeTags.IS_FIRE)) {
         absorbed = raw * RedHulkRules.FIRE_ABSORB;
      } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
         absorbed = raw * RedHulkRules.EXPLOSION_ABSORB;
      }
      if (absorbed > 0.0F) {
         amount -= absorbed;
         if (!state.overheated()) {
            state.addHeat(RedHulkRules.heatFromAbsorbed(absorbed));
         }
      }
      RedHulkMove m = state.move;
      amount *= RedHulkRules.damageTakenFactor(state.furious(), state.overheated(), m == RedHulkMove.ULT_CHARGE, m == RedHulkMove.ULT_RECOVER);
      if (amount <= 0.0F) {
         return DamageAbsorb.ABSORBED;
      }
      return Math.abs(amount - raw) > 1.0E-4F ? DamageAbsorb.reduceTo(amount) : DamageAbsorb.PASS;
   }

   /** Heat and fury scale every blow he deals (x1.0 cool to x1.45 at most). */
   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      RedHulkState state = RedHulkState.of(attacker);
      if (state == null || amount <= 0.0F) {
         return amount;
      }
      state.idle = 0;
      return amount * RedHulkRules.damageFactor(state.heat, state.furious());
   }

   /** Pain heats him; a heavy blow staggers him when he is not mid-move or raging. */
   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      RedHulkState state = RedHulkState.of(player);
      if (state == null) {
         return;
      }
      state.sinceHurt = 0;
      state.idle = 0;
      if (!state.overheated() && !source.is(DamageTypeTags.IS_FIRE)) {
         state.addHeat(RedHulkRules.heatFromHit(amount));
      }
      if (amount >= RedHulkRules.STAGGER_DAMAGE && !state.running() && !state.furious() && player.isAlive()) {
         state.start(RedHulkMove.STAGGER);
         RedHulkSounds.play(player, RedHulkSounds.HURT, 1.0F, RedHulkSounds.jitter(player, 1.0F));
      }
   }

   // --- held target (shared seam: the client mixin places the body, no teleport handshake) ---

   @Override
   public int heldTargetId(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot view = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
         return view != null && view.heroId() == HeroId.RED_HULK && RedHulkFlags.has(view.heroFlags(), RedHulkFlags.HOLDING) ? view.controlTargetId() : -1;
      }
      RedHulkState state = RedHulkState.of(player);
      return state == null ? -1 : state.heldId;
   }

   @Override
   public Vec3 heldTargetPosition(Player player, net.minecraft.world.entity.Entity target, float partialTick) {
      return RedHulkCombat.anchor(player.getPosition(partialTick), net.minecraft.util.Mth.rotLerp(partialTick, player.yRotO, player.getYRot()),
         target.getBbWidth(), target.getBbHeight());
   }

   // --- snapshot ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      RedHulkState state = RedHulkState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.RED_HULK, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      return compose(state);
   }

   /**
    * actionId = move, variant = move serial (clip restarts), resource = heat 0-1000, resourceLocked = overheated,
    * lionWindowLeft / Max = overheat lock, resource2 = fury ticks left, controlTargetId = held body.
    */
   static HeroPublicSnapshot compose(RedHulkState state) {
      int[] extra = state.cooldowns.clone();
      int flags = 0;
      flags = RedHulkFlags.set(flags, RedHulkFlags.IN_COMBAT, state.idle < 100);
      flags = RedHulkFlags.set(flags, RedHulkFlags.HOLDING, state.holding());
      flags = RedHulkFlags.set(flags, RedHulkFlags.OVERHEAT, state.overheated());
      flags = RedHulkFlags.set(flags, RedHulkFlags.FURY, state.furious());
      flags = RedHulkFlags.set(flags, RedHulkFlags.AURA, RedHulkRules.tier(state.heat) >= 2);
      flags = RedHulkFlags.set(flags, RedHulkFlags.BLAST_READY, RedHulkRules.canBlast(state.heat, state.overheated()));
      flags = RedHulkFlags.set(flags, RedHulkFlags.ULT_READY, RedHulkRules.canUlt(state.heat, state.overheated()));
      flags = RedHulkFlags.set(flags, RedHulkFlags.BEAM, state.is(RedHulkMove.BLAST) && state.elapsed >= RedHulkRules.BLAST_FIRE_START
         && state.elapsed <= RedHulkRules.BLAST_FIRE_END);
      RedHulkMove move = state.move;
      return new HeroPublicSnapshot(HeroId.RED_HULK, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, state.overheat, RedHulkRules.OVERHEAT_TICKS, false, false, 0, 0, state.heldId, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, null, state.heat, state.overheated(), flags, extra, state.serial, state.fury);
   }
}
