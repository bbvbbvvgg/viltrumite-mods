package dev.baranhan.viltrumitecore.client.cinematic.finisher;

import dev.baranhan.viltrumitecore.balance.CombatBalance;
import dev.baranhan.viltrumitecore.combat.finisher.FinisherRules;
import dev.baranhan.viltrumitecore.combat.finisher.FinisherTargets;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.FinisherRequestC2SPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client prediction of the finisher (wave1-22, task T6): which body the local hero would finish right
 * now, and the key press that asks the server for it. The candidate is re-picked once per client tick
 * (the entity under the crosshair first, then the nearest one in reach) and read by the HUD prompt, so
 * the hint and the press can never point at different bodies. Prediction only: the server re-runs
 * {@link FinisherRules} on live data. Client thread only.
 */
public final class FinisherTargeting {
   @Nullable
   private static LivingEntity candidate;

   private FinisherTargeting() {
   }

   /** One client tick: re-pick the candidate (none while a screen is open or the body cannot act). */
   public static void tick(Minecraft client) {
      LocalPlayer player = client.player;
      candidate = player == null || client.level == null || client.screen != null || !player.isAlive() || player.isSpectator()
         ? null : pick(player);
   }

   /** The body the crosshair (or the reach) offers right now, or null. */
   @Nullable
   public static LivingEntity candidate() {
      return candidate;
   }

   /** Key press: ask the server to finish the predicted candidate (nothing when there is none). */
   public static void press() {
      LivingEntity target = candidate;
      if (target != null) {
         CoreMessages.sendToServer(new FinisherRequestC2SPacket(target.getId()));
      }
   }

   @Nullable
   static LivingEntity pick(LocalPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getViewVector(1.0F);
      Vec3 end = eye.add(look.scale(FinisherRules.RANGE));
      AABB box = player.getBoundingBox().expandTowards(look.scale(FinisherRules.RANGE)).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
         entity -> entity instanceof LivingEntity living && living != player && allowed(player, living),
         FinisherRules.RANGE * FinisherRules.RANGE);
      if (hit != null && hit.getEntity() instanceof LivingEntity crosshair) {
         return crosshair;
      }

      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FinisherRules.RANGE), e -> e != player)) {
         if (!allowed(player, other)) {
            continue;
         }
         double distance = player.distanceTo(other);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = other;
         }
      }
      return best;
   }

   /**
    * The client's half of the verdict: the same pure function as the server, fed with the facts the
    * client knows (the synced PvP rule and the scoreboard teams included), so a hint never promises a
    * finish the server would refuse.
    */
   static boolean allowed(LocalPlayer player, LivingEntity target) {
      return FinisherRules.judge(new FinisherRules.Facts(
         target.isAlive(),
         target.level() == player.level(),
         player.hasLineOfSight(target),
         player.distanceTo(target),
         target.getHealth(),
         target.getMaxHealth(),
         CombatBalance.isBoss(target),
         target instanceof Player,
         ServerRules.pvp(player),
         FinisherTargets.allied(player, target))) == FinisherRules.Verdict.ALLOW;
   }
}
