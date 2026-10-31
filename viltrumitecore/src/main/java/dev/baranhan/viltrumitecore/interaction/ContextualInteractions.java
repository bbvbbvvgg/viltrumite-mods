package dev.baranhan.viltrumitecore.interaction;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.interaction.builtin.DivineWeaponClash;
import dev.baranhan.viltrumitecore.interaction.builtin.LegionVsChitauri;
import dev.baranhan.viltrumitecore.interaction.builtin.MjolnirMeetsVibranium;
import dev.baranhan.viltrumitecore.interaction.builtin.MjolnirShattersMeteor;
import dev.baranhan.viltrumitecore.interaction.builtin.ReidShattersVibranium;
import dev.baranhan.viltrumitecore.interaction.builtin.ThunderOverchargesArmor;
import dev.baranhan.viltrumitecore.interaction.builtin.TitanScattersSummons;
import dev.baranhan.viltrumitecore.interaction.builtin.VibraniumStopsMeteor;
import dev.baranhan.viltrumitecore.interaction.builtin.ReidStrainsLionsHeart;
import dev.baranhan.viltrumitecore.interaction.builtin.MjolnirReboundsOffShield;
import dev.baranhan.viltrumitecore.interaction.builtin.ViltrumiteFistMeetsVibranium;
import dev.baranhan.viltrumitecore.interaction.builtin.RepulsorSplitsOnVibranium;
import dev.baranhan.viltrumitecore.interaction.builtin.TimeStoneRewindsLedger;
import dev.baranhan.viltrumitecore.interaction.builtin.ReidCleavesMeteor;
import dev.baranhan.viltrumitecore.interaction.builtin.ViltrumiteHurlsMeteor;
import dev.baranhan.viltrumitecore.interaction.builtin.ShadowExchangeMeteor;
import dev.baranhan.viltrumitecore.interaction.builtin.TitanBreaksGuard;
import dev.baranhan.viltrumitecore.interaction.builtin.MeteorDownsLeviathan;
import dev.baranhan.viltrumitecore.interaction.builtin.ThunderChargesLegion;
import dev.baranhan.viltrumitecore.interaction.builtin.LightningRunsTheLeviathan;
import dev.baranhan.viltrumitecore.interaction.builtin.HeavyHitterHaltsLeviathan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

/**
 * The one registry and dispatcher of contextual interactions. Three trigger points
 * ({@link InteractionTrigger}) are wired from the shared event handlers; each asks the registered interactions of
 * that trigger in priority order, the first that matches and fires wins. {@link InteractionGuard} keeps a fired
 * collision from firing again, swallows the rest of the same stroke and blocks nested reactions. Server only;
 * the client only plays the FX presets and sounds the server sends.
 */
public final class ContextualInteractions {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final InteractionGuard GUARD = new InteractionGuard();
   private static final List<ContextualInteraction> ALL = new ArrayList<>();
   /** QA trace in the server log (-Dviltrumitecore.interactions.trace=true). */
   private static final boolean TRACE = Boolean.getBoolean("viltrumitecore.interactions.trace");
   private static boolean booted;

   private ContextualInteractions() {
   }

   /** Every built-in interaction, in catalog order. Stateless instances (unit tests build them). */
   public static List<ContextualInteraction> builtins() {
      return List.of(new ReidShattersVibranium(), new MjolnirMeetsVibranium(), new ReidStrainsLionsHeart(), new MjolnirReboundsOffShield(),
         new DivineWeaponClash(), new ViltrumiteFistMeetsVibranium(), new RepulsorSplitsOnVibranium(), new TimeStoneRewindsLedger(),
         new VibraniumStopsMeteor(), new ReidCleavesMeteor(), new ViltrumiteHurlsMeteor(), new ShadowExchangeMeteor(), new TitanBreaksGuard(),
         new MeteorDownsLeviathan(), new MjolnirShattersMeteor(), new ThunderOverchargesArmor(), new ThunderChargesLegion(),
         new LightningRunsTheLeviathan(), new HeavyHitterHaltsLeviathan(), new LegionVsChitauri(), new TitanScattersSummons());
   }

   /** Common setup: registers the built-ins once. */
   public static synchronized void bootstrap() {
      if (booted) {
         return;
      }
      booted = true;
      InteractionFx.init();
      for (ContextualInteraction interaction : builtins()) {
         register(interaction);
      }
   }

   public static synchronized void register(ContextualInteraction interaction) {
      String id = interaction.spec().id();
      for (ContextualInteraction existing : ALL) {
         if (existing.spec().id().equals(id)) {
            throw new IllegalArgumentException("duplicate contextual interaction " + id);
         }
      }
      ALL.add(interaction);
      // Stable: equal priorities keep registration order.
      ALL.sort(Comparator.comparingInt((ContextualInteraction i) -> i.spec().priority()).reversed());
      interaction.onRegister();
   }

   public static synchronized List<ContextualInteraction> registered() {
      return Collections.unmodifiableList(new ArrayList<>(ALL));
   }

   public static InteractionGuard guard() {
      return GUARD;
   }

   /** Server stop: no cooldown or aftermath survives into the next world. */
   public static void reset() {
      GUARD.clear();
   }

   // ---- trigger points ----

   /** LivingAttackEvent (before the damage layers). True = the hit is consumed. */
   public static boolean onAttack(LivingEntity target, DamageSource source, float amount) {
      if (!(target.level() instanceof ServerLevel level) || GUARD.reentrant() || ALL.isEmpty()) {
         return false;
      }
      Entity attacker = source.getEntity();
      if (attacker == null || attacker == target) {
         return false;
      }
      long now = level.getGameTime();
      if (GUARD.inAftermath(attacker.getId(), target.getId(), now)) {
         return true; // the rest of a stroke that already collided
      }
      InteractionContext ctx = new InteractionContext(InteractionTrigger.HIT, level, now, attacker, source.getDirectEntity(), target, source, amount, null);
      return run(ctx, attacker.getId(), target.getId()).cancel();
   }

   /**
    * A striker's melee move touched a player its target rules spare (PvP off, team mate): no hit was dealt, but a
    * cooperative interaction (Mjolnir on an ally's raised shield) may still happen. Called by the striker's own
    * strike resolution. True = an interaction fired.
    */
   public static boolean onFriendlyContact(net.minecraft.server.level.ServerPlayer striker, LivingEntity touched) {
      if (!(striker.level() instanceof ServerLevel level) || GUARD.reentrant() || ALL.isEmpty() || touched == striker) {
         return false;
      }
      long now = level.getGameTime();
      if (GUARD.inAftermath(striker.getId(), touched.getId(), now)) {
         return false;
      }
      InteractionContext ctx = new InteractionContext(InteractionTrigger.HIT, level, now, striker, striker, touched, null, 0.0F, null);
      return run(ctx, striker.getId(), touched.getId()).fired();
   }

   /** LivingHurtEvent (before the hero layers): the amount after the interactions. */
   public static float onHurt(LivingEntity target, DamageSource source, float amount) {
      if (!(target.level() instanceof ServerLevel level) || GUARD.reentrant() || ALL.isEmpty()) {
         return amount;
      }
      Entity attacker = source.getEntity();
      if (attacker == null || attacker == target) {
         return amount;
      }
      InteractionContext ctx = new InteractionContext(InteractionTrigger.HURT, level, level.getGameTime(), attacker, source.getDirectEntity(), target, source,
         amount, null);
      InteractionOutcome out = run(ctx, attacker.getId(), target.getId());
      return out.cancel() ? 0.0F : amount * out.scale();
   }

   /** ProjectileImpactEvent on an entity. True = skip the impact. */
   public static boolean onProjectile(Projectile projectile, HitResult result) {
      if (!(projectile.level() instanceof ServerLevel level) || GUARD.reentrant() || ALL.isEmpty() || !(result instanceof EntityHitResult hit)) {
         return false;
      }
      Entity target = hit.getEntity();
      if (target == projectile.getOwner()) {
         return false;
      }
      InteractionContext ctx = new InteractionContext(InteractionTrigger.PROJECTILE, level, level.getGameTime(), projectile, projectile, target, null, 0.0F,
         projectile);
      return run(ctx, projectile.getId(), target.getId()).cancel();
   }

   private static InteractionOutcome run(InteractionContext ctx, int a, int b) {
      for (ContextualInteraction interaction : registered()) {
         InteractionSpec spec = interaction.spec();
         if (spec.trigger() != ctx.trigger() || GUARD.coolingDown(spec.id(), a, b, ctx.now())) {
            continue;
         }
         boolean matches;
         try {
            matches = interaction.matches(ctx);
         } catch (RuntimeException e) {
            LOGGER.error("contextual interaction {} failed its check", spec.id(), e);
            continue;
         }
         if (!matches) {
            continue;
         }
         InteractionOutcome out;
         GUARD.enter();
         try {
            out = interaction.apply(ctx);
         } catch (RuntimeException e) {
            LOGGER.error("contextual interaction {} failed", spec.id(), e);
            out = InteractionOutcome.PASS;
         } finally {
            GUARD.exit();
         }
         if (out.fired()) {
            GUARD.fired(spec, a, b, ctx.now());
            InteractionDiscovery.fired(spec, ctx);
            if (TRACE) {
               LOGGER.info("INTERACTION {} fired: {} -> {} (cancel={}, scale={})", spec.id(), ctx.attacker(), ctx.target(), out.cancel(), out.scale());
            }
            return out;
         }
      }
      return InteractionOutcome.PASS;
   }
}
