package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusState;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One shared hero damage path. Interceptors at Player/LivingEntity hurt route
 * every external hit here; internal hero damage bypasses vanilla hurt entirely.
 * Ordering: capture attacker -> queue under control -> lion block -> pass.
 */
public final class HeroDamage {
   private static final ThreadLocal<Set<UUID>> ROUTING = ThreadLocal.withInitial(HashSet::new);

   private HeroDamage() {
   }

   public enum DamageKind {
      EXTERNAL,
      INTERNAL,
      DEFERRED_RELEASE
   }

   public enum DamageResult {
      PASS,
      BLOCKED,
      QUEUED,
      APPLIED
   }

   /** Re-entry guard: nested hurt() calls inside routing skip interception. */
   public static boolean isRouting(Entity entity) {
      return entity != null && ROUTING.get().contains(entity.getUUID());
   }

   public static DamageResult route(LivingEntity target, DamageSource source, float amount, DamageKind kind) {
      if (target.level().isClientSide() || !target.isAlive() || amount <= 0.0F) {
         return DamageResult.PASS;
      }

      if (isProtected(target)) return DamageResult.BLOCKED;

      Set<UUID> routing = ROUTING.get();
      if (!routing.add(target.getUUID())) {
         return DamageResult.PASS;
      }

      try {
         // Record a real living attacker once, including hits that get blocked.
         // Self damage and non-living sources never arm the Counter.
         LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
         if (capturesAttacker(kind, attacker != null, attacker == target) && target instanceof ServerPlayer player) {
            RegulusState state = RegulusHero.stateOf(player);
            if (state != null) {
               state.attackerId = attacker.getUUID();
               state.attackerTick = target.level().getGameTime();
               state.attackerLastPos = attacker.position();
            }
         }

         boolean anchored = false;
         ControlManager manager = null;
         if (target.level() instanceof ServerLevel serverLevel) {
            manager = ControlManager.get(serverLevel);
            anchored = manager.isAnchored(target);
         }

         RegulusState state = target instanceof ServerPlayer player ? RegulusHero.stateOf(player) : null;
         DamageResult result = decide(kind, true, anchored, state != null && state.lionActive, amount);
         if (result == DamageResult.QUEUED && manager != null) {
            manager.queueDamage(target, source, payableAmount(target, source, amount));
         }

         return result;
      } finally {
         routing.remove(target.getUUID());
      }
   }

   /** Routing order: dead/empty hits pass, control queues (except payouts), Lion blocks external only. */
   static DamageResult decide(DamageKind kind, boolean alive, boolean anchored, boolean lionActive, float amount) {
      return decide(kind, alive, anchored, lionActive, amount, false);
   }

   static DamageResult decide(DamageKind kind, boolean alive, boolean anchored, boolean lionActive, float amount, boolean protectedBody) {
      if (!alive || amount <= 0.0F) {
         return DamageResult.PASS;
      }

      if (protectedBody) return DamageResult.BLOCKED;

      if (kind != DamageKind.DEFERRED_RELEASE && anchored) {
         return DamageResult.QUEUED;
      }

      // Lion's Heart blocks every external hit outright (it is not Resistance).
      if (kind == DamageKind.EXTERNAL && lionActive) {
         return DamageResult.BLOCKED;
      }

      return DamageResult.PASS;
   }

   /** The Counter arms on external living-attacker hits only — never internal or self damage. */
   static boolean capturesAttacker(DamageKind kind, boolean livingAttacker, boolean selfHit) {
      return kind == DamageKind.EXTERNAL && livingAttacker && !selfHit;
   }

   /** One-per-session hero totem: Regulus only, never on void/kill damage. */
   static boolean totemEligible(boolean isRegulus, boolean bypassesInvulnerability, boolean totemConsumed) {
      return isRegulus && !bypassesInvulnerability && !totemConsumed;
   }

   /**
    * Payable amount for queueing: evaluate mitigation once without HP loss.
    * Viltrumite-hero victims reuse their legacy reduction/threshold values.
    */
   private static float payableAmount(LivingEntity target, DamageSource source, float amount) {
      if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
         amount = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
            amount, (float)target.getArmorValue(), (float)target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)
         );
      }

      if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) {
         return amount;
      }

      net.minecraft.world.effect.MobEffectInstance resistance = target.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
      if (resistance != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)) {
         int reduction = (resistance.getAmplifier() + 1) * 5;
         amount = Math.max(amount * (float)(25 - reduction) / 25.0F, 0.0F);
      }

      if (amount > 0.0F && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
         int protection = net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);
         if (protection > 0) {
            amount = net.minecraft.world.damagesource.CombatRules.getDamageAfterMagicAbsorb(amount, (float)protection);
         }
      }

      return amount;
   }

   /**
    * Clean HP loss for hero-internal damage: ignores armor/Resistance/Lion but
    * still queues under anchor control and still runs the lethal boundary.
    */
   public static void applyInternal(ServerPlayer player, float amount) {
      if (player.level().isClientSide() || amount <= 0.0F) {
         return;
      }

      if (isProtected(player)) return;

      ControlManager manager = player.level() instanceof ServerLevel serverLevel ? ControlManager.get(serverLevel) : null;
      boolean anchored = manager != null && manager.isAnchored(player);
      if (decide(DamageKind.INTERNAL, player.isAlive(), anchored, false, amount) == DamageResult.QUEUED && manager != null) {
         manager.queueDamage(player, player.damageSources().generic(), amount);
         return;
      }

      // Internal drains are self-inflicted: Regulus's health-delta bookkeeping
      // subtracts this share so it never reads as an interrupting hit (spec
      // 7.1/10.4/11.1). Queued damage is not recorded — it applies as a payout.
      RegulusState state = RegulusHero.stateOf(player);
      if (state != null) {
         state.internalDamage += amount;
      }

      // Self-inflicted drains (overheat, blood price, backlash) never flinch:
      // no hurt animation, red flash or camera shake per drain tick.
      applyCleanDamage(player, player.damageSources().generic(), amount, false);
   }

   /**
    * Apply damage directly (deferred release + internal causes). The lethal
    * boundary (hero totem) is handled here for every path uniformly.
    */
   public static void applyCleanDamage(LivingEntity target, DamageSource source, float amount) {
      applyCleanDamage(target, source, amount, true);
   }

   /** Same as above; flinch=false skips the hurt animation (self-inflicted drains). */
   public static void applyCleanDamage(LivingEntity target, DamageSource source, float amount, boolean flinch) {
      if (amount <= 0.0F || target.level().isClientSide()) {
         return;
      }

      if (isProtected(target)) return;

      HeroDamageLayers.notifyAttack(target, source);
      // No Forge damage event fires on this path: run the hero layers here, once.
      amount = HeroDamageLayers.onClean(target, source, amount);
      if (amount <= 0.0F) {
         return;
      }

      float newHealth = target.getHealth() - amount;
      if (newHealth <= 0.0F) {
         if (target instanceof ServerPlayer player && tryHeroTotem(player, source)) {
            return;
         }

         target.setHealth(0.0F);
         target.die(source);
         return;
      }

      target.setHealth(newHealth);
      if (flinch) {
         target.hurtTime = target.hurtDuration = 10;
      }
   }

   /**
    * Common lethal boundary for any hero fatal path (vanilla hurt, clean
    * damage, blood price). Consumes the one-per-session hero totem exactly once.
    */
   public static boolean tryHeroTotem(ServerPlayer player, DamageSource source) {
      HeroPlayer heroPlayer = player instanceof HeroPlayer hp ? hp : null;
      boolean isRegulus = heroPlayer != null && heroPlayer.getHeroId() == HeroId.REGULUS;
      boolean consumed = isRegulus && heroPlayer.getHeroSession().totemConsumed();
      if (!totemEligible(isRegulus, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), consumed)) {
         return false;
      }

      HeroSession session = heroPlayer.getHeroSession();

      // Mark consumed before restoring health so a nested lethal hit cannot
      // double-dip the revival.
      heroPlayer.viltrumitecore$setHeroSession(session.consumeTotem());
      player.setHealth(1.0F);
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
      player.level().broadcastEntityEvent(player, (byte)35);
      return true;
   }

   /** Protection wins before queueing/layers, including internal and deferred clean damage. */
   public static boolean isProtected(LivingEntity target) {
      return BodyIsolation.active(target) || dev.baranhan.viltrumitecore.hero.control.WorldPause.isProtected(target);
   }

   /** Queue the hit under any anchor control; used by routed EXTERNAL damage. */
   public static boolean isAnchored(@Nullable LivingEntity target) {
      if (target == null || !(target.level() instanceof ServerLevel level)) {
         return false;
      }

      return BodyIsolation.active(target) || dev.baranhan.viltrumitecore.hero.control.WorldPause.isPaused(target) || ControlManager.get(level).isAnchored(target);
   }
}
