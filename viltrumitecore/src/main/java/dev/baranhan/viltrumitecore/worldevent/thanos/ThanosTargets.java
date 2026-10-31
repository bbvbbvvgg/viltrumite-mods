package dev.baranhan.viltrumitecore.worldevent.thanos;

import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who Thanos may target and hurt, how he hurts and pushes them, and where his effects are sent.
 * Targets are heroes (survival/adventure players); area hits also reach other combatants but
 * never city bystanders, armor stands or Thanos himself.
 */
public final class ThanosTargets {
   /** Effects are sent to players this close to their position. */
   public static final double FX_RANGE = 160.0;

   private ThanosTargets() {
   }

   /** A player Thanos fights: alive, in play (not spectator or creative). */
   public static boolean hero(Player player) {
      return player.isAlive() && !player.isSpectator() && !player.isCreative();
   }

   /** May an area move hurt this entity? */
   public static boolean victim(LivingEntity entity, ThanosEntity thanos) {
      if (entity == thanos || !entity.isAlive() || entity instanceof ArmorStand || Bystander.is(entity) || entity instanceof ThanosEntity) {
         return false;
      }

      if (entity instanceof Player player && !hero(player)) {
         return false;
      }

      return !WorldPause.isProtected(entity);
   }

   public static List<LivingEntity> victims(ServerLevel level, ThanosEntity thanos, AABB box) {
      return level.getEntitiesOfClass(LivingEntity.class, box, e -> victim(e, thanos));
   }

   public static List<LivingEntity> victims(ServerLevel level, ThanosEntity thanos, Vec3 center, double radius) {
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : victims(level, thanos, new AABB(center, center).inflate(radius, radius + 2.0, radius))) {
         if (e.position().distanceToSqr(center) <= (radius + e.getBbWidth()) * (radius + e.getBbWidth())) {
            out.add(e);
         }
      }

      return out;
   }

   /** One hit from Thanos (mob attack source: hero defences, parries and invulnerability frames apply). */
   public static boolean hurt(ThanosEntity thanos, LivingEntity victim, float amount, float scale) {
      if (amount <= 0.0F || !victim(victim, thanos)) {
         return false;
      }

      return victim.hurt(thanos.damageSources().mobAttack(thanos), amount * scale);
   }

   /** A push from a stone (respects heroes that refuse impulses and anchored targets). */
   public static void push(LivingEntity victim, Vec3 velocity) {
      if (!HeroRegistry.allowsImpulse(victim)) {
         return;
      }

      victim.setDeltaMovement(victim.getDeltaMovement().add(velocity));
      victim.hasImpulse = true;
      victim.hurtMarked = true;
   }

   /** Sets a pull velocity (spatial grab); same gate as {@link #push}. */
   public static void drag(LivingEntity victim, Vec3 velocity) {
      if (!HeroRegistry.allowsImpulse(victim)) {
         return;
      }

      victim.setDeltaMovement(velocity);
      victim.fallDistance = 0.0F;
      victim.hasImpulse = true;
      victim.hurtMarked = true;
   }

   public static void fx(ServerLevel level, Vec3 at, ThanosFxS2CPacket packet) {
      for (ServerPlayer viewer : level.players()) {
         if (viewer.position().distanceToSqr(at) <= FX_RANGE * FX_RANGE) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }

   public static void fx(ServerLevel level, int kind, int a, int b, Vec3 at, Vec3 dir, float power, int extra) {
      fx(level, at, new ThanosFxS2CPacket(kind, a, b, at.x, at.y, at.z, (float)dir.x, (float)dir.y, (float)dir.z, power, extra));
   }

   public static void fxTo(ServerPlayer player, int kind, int a, int b, Vec3 at, Vec3 dir, float power, int extra) {
      CoreMessages.sendToPlayer(new ThanosFxS2CPacket(kind, a, b, at.x, at.y, at.z, (float)dir.x, (float)dir.y, (float)dir.z, power, extra), player);
   }
}
