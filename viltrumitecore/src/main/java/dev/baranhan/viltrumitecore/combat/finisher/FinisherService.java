package dev.baranhan.viltrumitecore.combat.finisher;

import dev.baranhan.viltrumitecore.balance.CombatBalance;
import dev.baranhan.viltrumitecore.combat.feel.Cinematics;
import dev.baranhan.viltrumitecore.combat.feel.CombatFeel;
import dev.baranhan.viltrumitecore.combat.feel.HitWeight;
import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * Server side of the finisher (wave1-22, task T6). The C2S request lands here; every rule is re-read
 * from the live world through {@link FinisherRules} (the client's hint is a prediction only), then an
 * accepted request kills the target with a {@link HitWeight#FINISHER} hit and plays the hero's
 * finisher scene. The request is per-player cooldown-gated, so a client cannot spam the channel.
 */
public final class FinisherService {
   /** Anti-spam cooldown key of the finisher ({@link PersistentCooldowns} namespaces by owner). */
   public static final String COOLDOWN_KEY = "cinematic:finisher";
   private static final Logger LOGGER = LogUtils.getLogger();

   private FinisherService() {
   }

   /** {@code attacker} asked to finish the entity {@code targetId}; the verdict comes from this world. */
   public static void request(@Nullable ServerPlayer attacker, int targetId) {
      if (attacker == null || !(attacker.level() instanceof ServerLevel level)) {
         return;
      }
      // Authority gate: the attacker must be able to act at all (a forged packet can arrive from a
      // dead, spectating or locked body such as Sinclair's rabbit).
      if (!attacker.isAlive() || attacker.isSpectator() || ActionLocks.locked(attacker)) {
         return;
      }
      if (FinisherRules.onCooldown(PersistentCooldowns.get(attacker, COOLDOWN_KEY))) {
         return;
      }

      // Anti-spam: even a refused request is paid for, so the channel cannot be hammered.
      PersistentCooldowns.set(attacker, COOLDOWN_KEY, FinisherRules.COOLDOWN_TICKS);

      Entity entity = level.getEntity(targetId);
      if (!(entity instanceof LivingEntity target) || target == attacker) {
         return;
      }

      FinisherRules.Verdict verdict = FinisherRules.judge(new FinisherRules.Facts(
         target.isAlive(),
         target.level() == level,
         attacker.hasLineOfSight(target),
         attacker.distanceTo(target),
         target.getHealth(),
         target.getMaxHealth(),
         CombatBalance.isBoss(target),
         target instanceof Player,
         ServerRules.pvp(attacker),
         attacker.isAlliedTo(target)));
      if (verdict != FinisherRules.Verdict.ALLOW) {
         LOGGER.debug("finisher refused for {}: {}", attacker.getScoreboardName(), verdict);
         return;
      }

      execute(attacker, target);
   }

   /** The finishing blow, its weight and the hero's scene; the order is feel, kill, scene. */
   private static void execute(ServerPlayer attacker, LivingEntity target) {
      CombatFeel.hit(attacker, target, HitWeight.FINISHER);
      target.hurt(attacker.damageSources().playerAttack(attacker),
         FinisherRules.finishingDamage(target.getHealth(), target.getAbsorptionAmount()));
      ResourceLocation scene = FinisherScenes.pick(HeroRegistry.get(attacker).finisherScene(attacker), CombatBalance.isBoss(target));
      Cinematics.play(attacker, scene, target);
   }
}
