package dev.baranhan.viltrumitecore.interaction;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * What the server knows at the trigger point. {@code attacker} is the responsible entity (the source entity of a
 * hit, the projectile itself for PROJECTILE), {@code direct} the touching entity (equal to {@code attacker} for a
 * melee hit), {@code target} the entity being hit.
 */
public record InteractionContext(InteractionTrigger trigger, ServerLevel level, long now, @Nullable Entity attacker, @Nullable Entity direct,
   Entity target, @Nullable DamageSource source, float amount, @Nullable Projectile projectile) {

   /** Current combat presence of a hero player (weapon tags + stance), NONE for anything else. */
   public static CombatPresence presence(@Nullable Entity e) {
      return e instanceof Player p ? HeroRegistry.get(p).combatPresence(p) : CombatPresence.NONE;
   }

   public CombatPresence attackerPresence() {
      return presence(this.attacker);
   }

   public CombatPresence targetPresence() {
      return presence(this.target);
   }

   /** A direct hand-to-hand hit (no projectile, no area relay). */
   public boolean melee() {
      return this.attacker != null && this.direct == this.attacker;
   }

   /** A friendly contact ({@link ContextualInteractions#onFriendlyContact}): no damage was dealt, PvP rules kept the hit away. */
   public boolean friendly() {
      return this.trigger == InteractionTrigger.HIT && this.source == null;
   }

   @Nullable
   public ServerPlayer attackerPlayer() {
      return this.attacker instanceof ServerPlayer p ? p : null;
   }

   @Nullable
   public ServerPlayer targetPlayer() {
      return this.target instanceof ServerPlayer p ? p : null;
   }
}
