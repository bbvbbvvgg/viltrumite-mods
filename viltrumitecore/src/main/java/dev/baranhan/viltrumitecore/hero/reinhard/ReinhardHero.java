package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Reinhard van Astrea (spec docs/design/2026-10-11-reinhard-design.md). Own
 * chassis, no Viltrumite kit (spec §1.2.5), no flight (spec §1.2.7).
 */
public final class ReinhardHero implements HeroDefinition {
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
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
   }

   @Override
   public void enter(ServerPlayer player) {
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
   }
}
