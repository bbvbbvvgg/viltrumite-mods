package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class HumanHero implements HeroDefinition {
   @Override
   public HeroId id() {
      return HeroId.HUMAN;
   }

   /** A plain human holds no fighting stance: no extra empty off-hand arm in first person. */
   @Override
   public boolean showsIdleOffhand(Player player) {
      return false;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** A plain human keeps vanilla hunger and no regeneration (balance spec §3). */
   @Override
   public boolean heroSustain(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return false;
   }

   @Override
   public String[] defaultLoadout() {
      return new String[0];
   }

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
   public void cleanup(ServerPlayer player, CleanupReason reason) {
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      return HeroPublicSnapshot.EMPTY;
   }
}
