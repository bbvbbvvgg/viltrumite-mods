package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The viltrumite hero delegates to the legacy kit. It owns the "viltrumite:*"
 * ability ids and, on exit, resets every legacy action flag/timer and the owned
 * strength modifier so a restored stale state cannot keep running.
 */
public class ViltrumiteHero implements HeroDefinition {

   @Override
   public HeroId id() {
      return HeroId.VILTRUMITE;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return true;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return true;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return abilityId != null && abilityId.startsWith("viltrumite:");
   }

   @Override
   public String[] defaultLoadout() {
      return new String[]{"viltrumite:punch", "viltrumite:dash", "viltrumite:grab", "viltrumite:lock", "viltrumite:block", "viltrumite:speed"};
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      return true;
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
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      if (reason == CleanupReason.HERO_CHANGE) {
         LegacyKit.reset(player);
      } else if (reason == CleanupReason.ERASED && player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core) {
         LegacyKit.resetActions(core);
      }
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      return HeroPublicSnapshot.EMPTY;
   }
}
