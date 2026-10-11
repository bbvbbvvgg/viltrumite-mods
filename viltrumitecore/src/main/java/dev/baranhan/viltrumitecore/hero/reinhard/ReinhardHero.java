package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Reinhard van Astrea (spec docs/design/2026-10-11-reinhard-design.md). Own
 * chassis, no Viltrumite kit (spec §1.2.5), no flight (spec §1.2.7).
 */
public final class ReinhardHero implements HeroDefinition {
   /** Knockback-resistance modifier of the chassis (fixed id, never change: saved on the player). */
   public static final UUID KNOCKBACK_MODIFIER_ID = UUID.fromString("c39ed511-76d1-4e69-a3ed-42628d488960");
   private static final String[] EMPTY_LOADOUT = new String[18];

   static {
      java.util.Arrays.fill(EMPTY_LOADOUT, "");
   }

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
      return false;
   }

   @Override
   public String[] defaultLoadout() {
      return EMPTY_LOADOUT.clone();
   }

   /** The spec gives Reinhard no immunity to control; knockback is the attribute (chassis). */
   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      return false;
   }

   @Override
   public void tick(ServerPlayer player) {
      ReinhardState state = ReinhardState.ensure(player);
      state.tick();
      if (player.isAlive() && ++state.regenTicks >= ReinhardRules.REGEN_INTERVAL) {
         state.regenTicks = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(ReinhardRules.REGEN_PER_SECOND);
         }
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
   }

   @Override
   public void enter(ServerPlayer player) {
      ReinhardState.ensure(player);
      applyChassis(player);
   }

   /** Fresh body after death or the End return: enter() is not called there. */
   @Override
   public void onRespawn(ServerPlayer player) {
      applyChassis(player);
   }

   /** Spec §3.1: no fall damage. */
   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   /** Spec §3.1: ordinary damage loses 99.9 % before armor; divine damage passes (spec §3.2). */
   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      return absorb(isDivine(source), raw);
   }

   /** Spec §3.1: ordinary damage never takes him below 1 HP; divine damage is not floored. */
   @Override
   public float clampFinalDamage(ServerPlayer self, DamageSource source, float afterArmor) {
      return clamp(isDivine(source), self.getHealth(), afterArmor);
   }

   /** Pure absorb step of the chassis. */
   static DamageAbsorb absorb(boolean divine, float raw) {
      return divine ? DamageAbsorb.PASS : DamageAbsorb.reduceTo(ReinhardRules.resist(raw));
   }

   /** Pure clamp step of the chassis. */
   static float clamp(boolean divine, float health, float afterArmor) {
      return divine ? afterArmor : ReinhardRules.floor(health, afterArmor);
   }

   // Stage 1 Task 5 replaces this stub with DivineDamage.isDivine.
   private static boolean isDivine(@Nullable DamageSource source) {
      return false;
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

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      ReinhardState.ensure(player).load(nbt);
   }

   /** Mode and cooldowns carry over to the respawned body. */
   @Override
   public void cloneHeroState(Player original, Player clone) {
      ReinhardState previous = ReinhardState.of(original);
      if (previous != null) {
         ReinhardState next = ReinhardState.ensure(clone);
         next.mode = previous.mode;
         next.modeCooldown = previous.modeCooldown;
         next.cooldowns = previous.cooldowns.clone();
      }
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      if (reason == CleanupReason.HERO_CHANGE) {
         AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
         if (knockback != null) {
            removeKnockbackResistance(knockback);
         }
      }
   }
}
