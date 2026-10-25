package dev.baranhan.viltrumitecore.util;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ChopBleedS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.ChopHitS2CPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ChopImpactManager {
   public static final Map<LivingEntity, ChopImpactManager.BleedData> BLEEDING_ENTITIES = new HashMap<>();
   public static final Map<Player, Set<LivingEntity>> CHOP_HIT_MEMORY = new HashMap<>();
   private static long lastBleedTick = 0L;

   public static void tickChopSweep(ServerPlayer player) {
      ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)player;
      int chopType = corePlayer.getChopType();
      float baseDamage = 0.0F;
      if (player instanceof ViltrumiteStatHolder statHolder) {
         baseDamage = statHolder.getBaseDamage() * 1.75F;
      }

      float knockback = 0.65F;
      double hitRadius = 3.75;
      Vec3 attackCenter = player.position().add(0.0, (double)(player.getBbHeight() * 0.75F), 0.0);
      Vec3 lookDir = player.getLookAngle().normalize();
      AABB searchBox = player.getBoundingBox().inflate(hitRadius);
      List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, searchBox, targetx -> targetx != player);
      Set<LivingEntity> alreadyHit = CHOP_HIT_MEMORY.computeIfAbsent(player, k -> new HashSet<>());
      boolean hitSomeone = false;

      for (LivingEntity target : targets) {
         if (!alreadyHit.contains(target)) {
            Vec3 targetCenter = target.position().add(0.0, (double)target.getBbHeight() / 2.0, 0.0);
            Vec3 toTarget = targetCenter.subtract(attackCenter);
            double dist3D = toTarget.length();
            if (!(dist3D > hitRadius)) {
               double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
               double expectedY = attackCenter.y + lookDir.y * horizontalDist;
               AABB targetBox = target.getBoundingBox();
               if (!(targetBox.minY > expectedY + 1.5) && !(targetBox.maxY < expectedY - 1.5)) {
                  if (chopType == 0) {
                     double dotProduct = lookDir.dot(toTarget.normalize());
                     if (dotProduct < -0.15) {
                        continue;
                     }
                  }

                  alreadyHit.add(target);
                  if (corePlayer.getGrabbedTarget() == target) {
                     corePlayer.releaseTarget();
                  }

                  target.hurt(player.damageSources().playerAttack(player), baseDamage);
                  if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(target, dev.baranhan.viltrumitecore.hero.control.ControlKind.IMPULSE)) {
                     Vec3 pushDir = new Vec3(toTarget.x, 0.0, toTarget.z).normalize().scale((double)knockback).add(0.0, 0.2, 0.0);
                     target.setDeltaMovement(pushDir);
                     target.hasImpulse = true;
                  }
                  BLEEDING_ENTITIES.put(target, new ChopImpactManager.BleedData(baseDamage, 80, player));
                  Vec3 toAttacker = player.position().subtract(target.position()).normalize();
                  Vec3 hitPos = new Vec3(
                     target.getX() + toAttacker.x * ((double)target.getBbWidth() / 2.0),
                     expectedY,
                     target.getZ() + toAttacker.z * ((double)target.getBbWidth() / 2.0)
                  );
                  ChopHitS2CPacket packet = new ChopHitS2CPacket(hitPos, lookDir);
                  CoreMessages.sendToTracking(packet, player);
                  CoreMessages.sendToPlayer(packet, player);
                  hitSomeone = true;
               }
            }
         }
      }

      if (hitSomeone) {
         dev.baranhan.viltrumitecore.hero.ChopStyle style = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).chopStyle(player);
         player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, style.critVolume(), style.critPitch());
      }
   }

   public static void clearChopMemory(Player player) {
      CHOP_HIT_MEMORY.remove(player);
   }

   public static void tickBleeding(ServerLevel world) {
      if (!BLEEDING_ENTITIES.isEmpty()) {
         long currentTick = world.getGameTime();
         if (currentTick != lastBleedTick) {
            lastBleedTick = currentTick;
            Iterator<Entry<LivingEntity, ChopImpactManager.BleedData>> it = BLEEDING_ENTITIES.entrySet().iterator();

            while (it.hasNext()) {
               Entry<LivingEntity, ChopImpactManager.BleedData> entry = it.next();
               LivingEntity target = entry.getKey();
               ChopImpactManager.BleedData data = entry.getValue();
               if (target.isAlive() && !target.isRemoved()) {
                  data.ticksLeft--;
                  if (data.ticksLeft % 20 == 0) {
                     target.hurt(world.damageSources().magic(), data.damagePerTick);
                     world.playSound(null, target.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7F, 0.5F);
                     world.playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8F, 0.6F);
                     ChopBleedS2CPacket bleedPacket = new ChopBleedS2CPacket(
                        target.getX(), target.getY() + (double)target.getBbHeight() * 0.6, target.getZ()
                     );
                     CoreMessages.sendToTracking(bleedPacket, target);
                     if (target instanceof ServerPlayer serverTarget) {
                        CoreMessages.sendToPlayer(bleedPacket, serverTarget);
                     }
                  }

                  if (data.ticksLeft <= 0) {
                     it.remove();
                  }
               } else {
                  it.remove();
               }
            }
         }
      }
   }

   public static class BleedData {
      public float damagePerTick;
      public int ticksLeft;
      public Player attacker;

      public BleedData(float totalDamage, int totalTicks, Player attacker) {
         this.damagePerTick = totalDamage / ((float)totalTicks / 20.0F);
         this.ticksLeft = totalTicks;
         this.attacker = attacker;
      }
   }
}
