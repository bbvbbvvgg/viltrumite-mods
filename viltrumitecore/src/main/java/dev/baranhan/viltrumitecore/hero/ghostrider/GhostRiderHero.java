package dev.baranhan.viltrumitecore.hero.ghostrider;

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
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
 * Ghost Rider (spec docs/design/2026-11-02-ghost-rider-design.md): Johnny Blaze, a stunt rider with a plain human body,
 * and the Spirit of Vengeance he turns into: a burning skull in black leather with the Hell Chain, hellfire, the Penance
 * Stare and the Hell Cycle. Own chassis (Rider form only), no Viltrumite kit, no flight.
 */
public final class GhostRiderHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b01");
   static final UUID ARMOR_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b02");
   static final UUID TOUGHNESS_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b03");
   static final UUID KNOCKBACK_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b04");
   static final UUID SPEED_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b05");
   /** Rooted moves (transformation, stare, slam), removed when idle. */
   static final UUID SLOW_ID = UUID.fromString("6a1f3b2c-7d4e-4f8a-9b0c-1d2e3f4a5b06");
   public static final GhostRiderHero INSTANCE = new GhostRiderHero();

   private GhostRiderHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.GHOST_RIDER;
   }

   /** The Rider form on either side (server state, client snapshot). */
   public static boolean rider(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot view = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
         return view != null && view.heroId() == HeroId.GHOST_RIDER && GhostRiderFlags.has(view.heroFlags(), GhostRiderFlags.RIDER);
      }
      GhostRiderState state = GhostRiderState.of(player);
      return state != null && state.rider;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** Tier A as the Rider (below Reinhard and Regulus, beside Thor and the Red Hulk); Johnny is an ordinary man. */
   @Override
   public dev.baranhan.viltrumitecore.balance.HeroBalanceProfile balanceProfile(Player player) {
      return rider(player) ? dev.baranhan.viltrumitecore.balance.BalanceTable.SPIRIT_OF_VENGEANCE : dev.baranhan.viltrumitecore.balance.BalanceTable.UNARMORED;
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
      return GhostRiderAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return GhostRiderAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return GhostRiderAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return GhostRiderAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return GhostRiderAbilities.panelAbilities();
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return GhostRiderLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return GhostRiderAbilities.icon(abilityId);
   }

   /** The transformation and the stare are not broken by grabs, freezes or pushes; otherwise a normal body. */
   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      GhostRiderState state = target instanceof Player p ? GhostRiderState.of(p) : null;
      return state == null || state.move == null || !(state.move == GhostRiderMove.TRANSFORM || state.move == GhostRiderMove.STARE);
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   @Override
   public boolean fireImmune(Player player) {
      return rider(player);
   }

   // --- mouse: the Rider's chain; Johnny fights and mines like anybody ---

   @Override
   public boolean claimsBlockAttack(Player player) {
      return rider(player);
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return !rider(player);
   }

   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return !rider(player) || CaptainUse.yields(player);
   }

   @Override
   public boolean claimsBlockUse(Player player) {
      return rider(player) && !CaptainUse.yields(player);
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      if (!rider(player)) {
         return null;
      }
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
      GhostRiderState state = GhostRiderState.ensure(player);
      boolean locked = state.move != null && state.move.locks();
      return switch (action) {
         case PRIMARY_ATTACK, SECONDARY_USE -> !locked && state.rider;
         case JUMP, AIR_STEP -> state.move == null || !state.move.roots();
         default -> !locked && GhostRiderAbility.byAction(action) != null;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.6F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed) {
         return;
      }
      GhostRiderState state = GhostRiderState.ensure(player);
      switch (action) {
         case PRIMARY_ATTACK -> GhostRiderCombat.primary(player, state);
         case SECONDARY_USE -> GhostRiderCombat.secondary(player, state);
         default -> {
            GhostRiderAbility ability = GhostRiderAbility.byAction(action);
            if (ability != null) {
               GhostRiderSkills.input(player, state, ability);
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
      GhostRiderState state = GhostRiderState.ensure(player);
      state.tickCount++;
      state.tickTimers();
      if (state.rider) {
         if (player.getRemainingFireTicks() > 0) {
            player.clearFire();
         }
         if (Hellfire.burning(player)) {
            Hellfire.extinguish(player);
         }
         if (state.tickCount % 20 == 0) {
            applyChassis(player);
         }
      }
      GhostRiderSkills.bike(player, state);
      GhostRiderCombat.tick(player, state);
      GhostRiderMove m = state.move;
      modifierNow(player, SLOW_ID, "Ghost Rider rooted", m != null && m.roots() ? -0.9 : 0.0);
   }

   @Override
   public void enter(ServerPlayer player) {
      GhostRiderState state = GhostRiderState.ensure(player);
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(0);
      }
      if (state.rider) {
         applyChassis(player);
         player.setHealth(player.getMaxHealth());
      } else {
         removeChassis(player);
      }
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state != null && state.rider) {
         applyChassis(player);
         player.setHealth(player.getMaxHealth());
      }
   }

   /** The Rider's body; the health keeps its fraction (no free healing by changing back and forth). */
   static void applyChassis(ServerPlayer player) {
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean added = modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Ghost Rider health", GhostRiderRules.HEALTH_BONUS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Ghost Rider armor", GhostRiderRules.ARMOR, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Ghost Rider toughness", GhostRiderRules.TOUGHNESS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Ghost Rider weight", GhostRiderRules.KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Ghost Rider stride", GhostRiderRules.SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE);
      if (added) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
   }

   private static boolean modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
         return true;
      }
      return false;
   }

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
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean had = false;
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID},
         {Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID}, {Attributes.MOVEMENT_SPEED, SPEED_ID}, {Attributes.MOVEMENT_SPEED, SLOW_ID}}) {
         AttributeInstance instance = player.getAttribute((Attribute)pair[0]);
         if (instance != null && instance.getModifier((UUID)pair[1]) != null) {
            instance.removeModifier((UUID)pair[1]);
            had |= pair[1] == HEALTH_ID;
         }
      }
      if (had && player.isAlive()) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state != null) {
         GhostRiderSkills.cleanup(player, state, true);
         if (reason == CleanupReason.HERO_CHANGE) {
            state.rider = false;
         }
      }
      modifierNow(player, SLOW_ID, "", 0.0);
      if (reason == CleanupReason.HERO_CHANGE) {
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state != null) {
         GhostRiderSkills.cleanup(player, state, true);
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      GhostRiderState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      GhostRiderState previous = GhostRiderState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- body ---

   @Override
   public boolean cancelsFallDamage(Player player) {
      return rider(player);
   }

   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      float damage = GhostRiderRules.fallDamage(fallDistance);
      if (damage > 0.0F) {
         player.hurt(player.damageSources().fall(), damage);
      }
   }

   @Override
   public boolean knockbackImmune(Player player) {
      GhostRiderState state = GhostRiderState.of(player);
      return state != null && state.move != null && state.move.unstoppable();
   }

   /** The Rider does not burn: fire, lava and hellfire are nothing to him. */
   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      GhostRiderState state = GhostRiderState.of(self);
      if (state == null || !state.rider || raw <= 0.0F) {
         return DamageAbsorb.PASS;
      }
      if (source.is(DamageTypeTags.IS_FIRE) || source.is(Hellfire.HELLFIRE)) {
         return DamageAbsorb.ABSORBED;
      }
      return DamageAbsorb.PASS;
   }

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state != null) {
         state.idle = 0;
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      GhostRiderState state = GhostRiderState.of(attacker);
      if (state != null) {
         state.idle = 0;
      }
      return amount;
   }

   // --- the body on the chain (shared seam: the client mixin places it, no teleport handshake) ---

   @Override
   public int heldTargetId(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot view = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
         if (view == null || view.heroId() != HeroId.GHOST_RIDER) {
            return -1;
         }
         int flags = view.heroFlags();
         boolean swing = view.actionId() == GhostRiderMove.WHIRL.ordinal() && GhostRiderFlags.has(flags, GhostRiderFlags.CHAIN);
         return GhostRiderFlags.has(flags, GhostRiderFlags.TOW) || swing ? view.controlTargetId() : -1;
      }
      GhostRiderState state = GhostRiderState.of(player);
      if (state == null || !state.chained() || !state.pulling) {
         return -1;
      }
      return state.towing || state.is(GhostRiderMove.WHIRL) ? state.chainId : -1;
   }

   @Override
   @Nullable
   public Vec3 heldTargetPosition(Player player, Entity target, float partialTick) {
      if (player.getVehicle() instanceof HellCycleEntity bike) {
         return GhostRiderCombat.towPoint(bike.getPosition(partialTick), Mth.rotLerp(partialTick, bike.yRotO, bike.getYRot()), target.getBbWidth());
      }
      HeroPublicSnapshot view = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
      float elapsed = view == null ? 0 : view.actionElapsed() + partialTick;
      if (!player.level().isClientSide) {
         GhostRiderState state = GhostRiderState.of(player);
         elapsed = state == null ? 0 : state.elapsed;
      }
      return GhostRiderCombat.swingPoint(player.getPosition(partialTick), elapsed, Mth.rotLerp(partialTick, player.yRotO, player.getYRot()),
         target.getBbHeight());
   }

   // --- snapshot ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      GhostRiderState state = GhostRiderState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.GHOST_RIDER, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      return compose(state, player);
   }

   /**
    * actionId = move, variant = move serial, controlTargetId = the chained body or the stare's victim, resource = the
    * sins of the last verdict, resource2 = his bike's entity id + 1 (0 = none).
    */
   static HeroPublicSnapshot compose(GhostRiderState state, Player player) {
      int[] extra = state.cooldowns.clone();
      int flags = 0;
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.RIDER, state.rider);
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.RIDING, player.getVehicle() instanceof HellCycleEntity);
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.CHAIN, state.chained());
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.TOW, state.towing && state.pulling);
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.STARE, state.staring());
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.INNOCENT, state.lastInnocent);
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.IN_COMBAT, state.idle < 100);
      flags = GhostRiderFlags.set(flags, GhostRiderFlags.BIKE_OUT, state.bikeId >= 0);
      GhostRiderMove move = state.move;
      int target = state.staring() ? state.stareId : state.chainId;
      return new HeroPublicSnapshot(HeroId.GHOST_RIDER, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, 0, 0, false, false, 0, 0, target, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, null, Math.max(0, state.lastSins), false, flags, extra, state.serial, state.bikeId + 1);
   }
}
