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
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.UUID;
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

   /** Spec §5: MMB cycles the mode (both sides; the client cancels pick-block). */
   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return button == MouseButton.MIDDLE ? HeroAction.MODE_CYCLE : null;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }

      ReinhardState state = ReinhardState.ensure(player);
      return switch (action) {
         case MODE_CYCLE -> ModeCycle.press(state.mode, state.modeCooldown, state.busy()).accepted();
         default -> false;
      };
   }

   /** A refused press always gives feedback (add_hero §4.4). */
   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      ReinhardSounds.play(player, ReinhardSounds.REFUSE.get(), 0.8F, 0.6F);
   }

   @Override
   public void tick(ServerPlayer player) {
      ReinhardState state = ReinhardState.ensure(player);
      state.tick();
      VoidRescue.tick(player);
      if (player.isAlive() && ++state.regenTicks >= ReinhardRules.REGEN_INTERVAL) {
         state.regenTicks = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(ReinhardRules.REGEN_PER_SECOND);
         }
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (action == HeroAction.MODE_CYCLE && pressed) {
         cycleMode(player, ReinhardState.ensure(player));
      }
   }

   /** Spec §5: no pose; cooldown 3 s; own sound per mode; the panel page follows the mode. */
   private void cycleMode(ServerPlayer player, ReinhardState state) {
      ModeCycle.Result result = ModeCycle.press(state.mode, state.modeCooldown, state.busy());
      if (!result.accepted()) {
         this.onInputRefused(player, HeroAction.MODE_CYCLE);
         return;
      }

      ReinhardMode previous = state.mode;
      state.mode = result.next();
      state.modeCooldown = ReinhardRules.MODE_COOLDOWN;
      if (previous == ReinhardMode.HOLY_SWORD) {
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

   /** Mode in heroFlags bits 0-1; ability cooldowns at their ordinal, mode cooldown at EXTRA_MODE_CD. */
   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      ReinhardState state = ReinhardState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }

      int[] extra = new int[HeroPublicSnapshot.EXTRA_COOLDOWN_MAX];
      System.arraycopy(state.cooldowns, 0, extra, 0, Math.min(state.cooldowns.length, ReinhardFlags.EXTRA_MODE_CD));
      extra[ReinhardFlags.EXTRA_MODE_CD] = state.modeCooldown;
      int flags = ReinhardFlags.withMode(0, state.mode);
      return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false, -1, null, 0, false, flags, extra);
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      if (reason == CleanupReason.HERO_CHANGE) {
         setPage(player, 0);
         AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
         if (knockback != null) {
            removeKnockbackResistance(knockback);
         }
      }
   }
}
