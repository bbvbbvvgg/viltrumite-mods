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

   /**
    * Squad NPC identity (Conquest, Thula, Lucan, Vidor, spawner clones): the legacy kit hero, not the
    * player race that the legacy setViltrumite(true) adapter maps to (Homelander). Identity only, like a
    * save restore: no hero lifecycle and no session greeting.
    */
   public static void becomeSquadNpc(ServerPlayer npc) {
      HeroRegistry.restoreHero(npc, new HeroSession(HeroId.VILTRUMITE, java.util.UUID.randomUUID(), false));
   }

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

   /** A Viltrumite body (squad NPCs and anyone wearing this hero): tag rules of the contextual interactions. */
   public static final dev.baranhan.viltrumitecore.hero.combat.CombatPresence VILTRUMITE_BODY = new dev.baranhan.viltrumitecore.hero.combat.CombatPresence(
      "viltrumitecore:viltrumite_body", java.util.Set.of(dev.baranhan.viltrumitecore.hero.combat.CombatTags.BODY_VILTRUMITE),
      dev.baranhan.viltrumitecore.hero.combat.CombatStance.NONE, 0);

   @Override
   public dev.baranhan.viltrumitecore.hero.combat.CombatPresence combatPresence(Player player) {
      return player == null ? dev.baranhan.viltrumitecore.hero.combat.CombatPresence.NONE : VILTRUMITE_BODY;
   }

   /**
    * viltrumite_hurls_meteor (METEOR_CAUGHT) and viltrumite_fist_meets_vibranium (STRIKE_RECOILED): a Viltrumite
    * that is awake and free takes the meteor or the recoil; the interaction itself moves the bodies.
    */
   @Override
   public boolean react(ServerPlayer self, dev.baranhan.viltrumitecore.interaction.InteractionReaction reaction,
      dev.baranhan.viltrumitecore.interaction.InteractionContext ctx) {
      if (self == null || !self.isAlive() || self.isSpectator()) {
         return false;
      }
      return switch (reaction) {
         case METEOR_CAUGHT -> !dev.baranhan.viltrumitecore.hero.HeroDamage.isAnchored(self)
            && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(self, ControlKind.IMPULSE);
         case STRIKE_RECOILED -> true;
         default -> false;
      };
   }
}
