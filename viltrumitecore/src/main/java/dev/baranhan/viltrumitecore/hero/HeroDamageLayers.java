package dev.baranhan.viltrumitecore.hero;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared damage-layer pipeline. Heroes change incoming and outgoing damage
 * only through three {@link HeroDefinition} hooks; this class runs them at
 * fixed points so every hero sees the same order.
 *
 * <p>Fixed layer order for every hero:
 * <ol>
 *   <li><b>control</b> — {@link HeroDamage#route} queues hits while the target
 *       is anchored; queued hits are not charged to any layer. The deferred
 *       payout ({@code ControlManager.release}) enters this pipeline once, at
 *       the shield step, through {@link HeroDamage#applyCleanDamage};</li>
 *   <li><b>shield</b>, <b>Hulkbuster</b>, <b>mark</b> — inside the hero's own
 *       {@link HeroDefinition#absorbIncoming} (in this order). A breaking hit
 *       is absorbed fully and does not spill to the next layer;</li>
 *   <li><b>inner suit / nano armor</b> — vanilla armor attributes;</li>
 *   <li><b>Tony's HP</b> — {@link HeroDefinition#clampFinalDamage} after armor.</li>
 * </ol>
 *
 * <p>Event points: {@code absorbIncoming} in {@code LivingAttackEvent} (before
 * armor, knockback and the hurt animation), {@code clampFinalDamage} in
 * {@code LivingDamageEvent}, {@code modifyOutgoingDamage} in
 * {@code LivingHurtEvent}. The direct path {@link HeroDamage#applyCleanDamage}
 * fires no Forge event and runs outgoing → absorb → clamp itself, so nothing is
 * charged twice. Sources that bypass invulnerability (void, {@code generic_kill})
 * skip absorb and clamp; they reach only
 * {@link HeroDefinition#clampUnblockableDamage}, except inside {@link AdminKill}
 * ({@code /kill} stays an admin tool for every hero) or an {@link Annihilation}
 * scope (the Reid strike kills every mortal target).
 */
public final class HeroDamageLayers {
   /** Target side of one hit. */
   public interface Target {
      DamageAbsorb absorbIncoming(float raw);

      float clampFinalDamage(float afterArmor);

      /** HP loss from a source that bypasses invulnerability (void, generic_kill). Default unchanged. */
      default float clampUnblockable(float amount) {
         return amount;
      }
   }

   /** Attacker side of one hit. */
   public interface Attacker {
      float modifyOutgoingDamage(float amount);
   }

   public static final Target NO_LAYERS = new Target() {
      @Override
      public DamageAbsorb absorbIncoming(float raw) {
         return DamageAbsorb.PASS;
      }

      @Override
      public float clampFinalDamage(float afterArmor) {
         return afterArmor;
      }
   };
   public static final Attacker NO_ATTACKER = amount -> amount;

   /** Hit scale from a partial absorb, consumed by the same hit's LivingHurtEvent. */
   private static final Map<UUID, Float> PENDING_SCALE = new HashMap<>();

   private HeroDamageLayers() {
   }

   /** Logout: drop a partial-absorb scale that no LivingHurtEvent consumed. */
   public static void forget(ServerPlayer player) {
      PENDING_SCALE.remove(player.getUUID());
   }

   // ---- pure pipeline ----

   /** LivingAttackEvent step. Iframe hits and bypassing sources never reach a layer. */
   public static DamageAbsorb attack(Target target, boolean bypassesLayers, boolean inInvulnerabilityFrames, float raw) {
      if (bypassesLayers || inInvulnerabilityFrames || raw <= 0.0F) {
         return DamageAbsorb.PASS;
      }

      DamageAbsorb result = target.absorbIncoming(raw);
      return result == null ? DamageAbsorb.PASS : result;
   }

   /** Amount that continues after the attack step (never above raw). */
   public static float passOn(DamageAbsorb result, float raw) {
      if (result.absorbed()) {
         return 0.0F;
      }

      return result.passOn() < 0.0F ? raw : Math.min(raw, result.passOn());
   }

   /** Multiplier for the same hit's LivingHurtEvent amount (1 = unchanged). */
   public static float hurtScale(DamageAbsorb result, float raw) {
      return raw <= 0.0F ? 1.0F : passOn(result, raw) / raw;
   }

   /** LivingDamageEvent step outside an admin kill. */
   public static float finalDamage(Target target, boolean bypassesLayers, float afterArmor) {
      return finalDamage(target, bypassesLayers, false, afterArmor);
   }

   /**
    * LivingDamageEvent step: after armor, enchantments and Resistance. A
    * bypassing source reaches only {@link Target#clampUnblockable}, and not
    * even that inside an admin kill.
    */
   public static float finalDamage(Target target, boolean bypassesLayers, boolean adminKill, float afterArmor) {
      if (afterArmor <= 0.0F) {
         return afterArmor;
      }

      if (bypassesLayers) {
         return adminKill ? afterArmor : Math.max(0.0F, target.clampUnblockable(afterArmor));
      }

      return Math.max(0.0F, target.clampFinalDamage(afterArmor));
   }

   /** True inside a scope where hero HP floors on unblockable damage step aside: /kill ({@link AdminKill}) or a Reid strike ({@link Annihilation}). */
   public static boolean unflooredScope() {
      return AdminKill.active() || Annihilation.active();
   }

   /** {@link #finalDamage} with the current scope ({@link #unflooredScope}). */
   public static float finalDamageInScope(Target target, boolean bypassesLayers, float afterArmor) {
      return finalDamage(target, bypassesLayers, unflooredScope(), afterArmor);
   }

   /** LivingHurtEvent step for a hero attacker. */
   public static float outgoing(Attacker attacker, float amount) {
      return amount <= 0.0F ? amount : Math.max(0.0F, attacker.modifyOutgoingDamage(amount));
   }

   /** Direct path outside an admin kill. */
   public static float clean(Target target, Attacker attacker, boolean bypassesLayers, float amount) {
      return clean(target, attacker, bypassesLayers, false, amount);
   }

   /** Direct path: outgoing → absorb → clamp. Returns the HP to remove (0 = absorbed). */
   public static float clean(Target target, Attacker attacker, boolean bypassesLayers, boolean adminKill, float amount) {
      float hit = bypassesLayers ? amount : outgoing(attacker, amount);
      DamageAbsorb result = attack(target, bypassesLayers, false, hit);
      if (result.absorbed()) {
         return 0.0F;
      }

      return finalDamage(target, bypassesLayers, adminKill, passOn(result, hit));
   }

   /** Vanilla LivingEntity.hurt cooldown: inside it a hit no stronger than the last one does nothing. */
   public static boolean inInvulnerabilityFrames(int invulnerableTime, float lastHurt, float amount) {
      return invulnerableTime > 10 && amount <= lastHurt;
   }

   // ---- Minecraft glue ----

   public static boolean bypassesLayers(DamageSource source) {
      return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
   }

   public static Target target(ServerPlayer self, DamageSource source) {
      HeroDefinition hero = HeroRegistry.get(self);
      return new Target() {
         @Override
         public DamageAbsorb absorbIncoming(float raw) {
            return hero.absorbIncoming(self, source, raw);
         }

         @Override
         public float clampFinalDamage(float afterArmor) {
            return hero.clampFinalDamage(self, source, afterArmor);
         }

         @Override
         public float clampUnblockable(float amount) {
            return hero.clampUnblockableDamage(self, source, amount);
         }
      };
   }

   public static Attacker attacker(ServerPlayer attacker, LivingEntity target, DamageSource source) {
      HeroDefinition hero = HeroRegistry.get(attacker);
      return amount -> hero.modifyOutgoingDamage(attacker, target, source, amount);
   }

   /** Hero attacker of a hit, or null (self hits and non-player sources have none). */
   @javax.annotation.Nullable
   public static ServerPlayer heroAttacker(LivingEntity target, DamageSource source) {
      return source.getEntity() instanceof ServerPlayer attacker && attacker != target ? attacker : null;
   }

   /**
    * LivingAttackEvent for a player target. Returns true when the hit must be
    * cancelled. An absorbed hit still opens vanilla invulnerability frames, so
    * per-tick sources (fire, cactus) are not charged every tick.
    */
   public static boolean onAttack(ServerPlayer player, DamageSource source, float amount) {
      PENDING_SCALE.remove(player.getUUID());
      boolean iframes = !source.is(DamageTypeTags.BYPASSES_COOLDOWN)
         && inInvulnerabilityFrames(player.invulnerableTime, ((dev.baranhan.viltrumitecore.mixin.LivingEntityHurtAccessor)player).viltrumitecore$getLastHurt(), amount);
      DamageAbsorb result = attack(target(player, source), bypassesLayers(source), iframes, amount);
      if (result.absorbed()) {
         ((dev.baranhan.viltrumitecore.mixin.LivingEntityHurtAccessor)player).viltrumitecore$setLastHurt(amount);
         player.invulnerableTime = 20;
         return true;
      }

      float scale = hurtScale(result, amount);
      if (scale < 1.0F) {
         PENDING_SCALE.put(player.getUUID(), scale);
      }

      return false;
   }

   /** LivingHurtEvent: partial absorb of this hit, then the hero attacker's outgoing hook. */
   public static float onHurt(LivingEntity target, DamageSource source, float amount) {
      Float scale = PENDING_SCALE.remove(target.getUUID());
      if (scale != null) {
         amount *= scale;
      }

      ServerPlayer attacker = heroAttacker(target, source);
      return attacker == null ? amount : outgoing(attacker(attacker, target, source), amount);
   }

   /** LivingDamageEvent for a player target. */
   public static float onDamage(ServerPlayer player, DamageSource source, float afterArmor) {
      return finalDamageInScope(target(player, source), bypassesLayers(source), afterArmor);
   }

   /** Direct path used by {@link HeroDamage#applyCleanDamage}. */
   public static float onClean(LivingEntity target, DamageSource source, float amount) {
      Target layers = target instanceof ServerPlayer player ? target(player, source) : NO_LAYERS;
      ServerPlayer heroAttacker = heroAttacker(target, source);
      Attacker attacker = heroAttacker == null ? NO_ATTACKER : attacker(heroAttacker, target, source);
      return clean(layers, attacker, bypassesLayers(source), unflooredScope(), amount);
   }
}
