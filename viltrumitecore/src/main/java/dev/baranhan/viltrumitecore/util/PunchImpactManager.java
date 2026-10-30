package dev.baranhan.viltrumitecore.util;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PunchImpactManager {
   public static final Map<Entity, PunchImpactManager.MeteorData> LAUNCHED_ENTITIES = new HashMap<>();

   public static void executePunch(ServerPlayer player) {
      ServerLevel world = (ServerLevel)player.level();
      Vec3 eyePos = player.getEyePosition();
      Vec3 direction = player.getLookAngle().normalize();
      Vec3 apex = eyePos.subtract(direction.scale(2.0));
      float punchStr = 0.0F;
      LivingEntity grabbedTarget = null;
      float damage = 0.0F;
      if (player instanceof ViltrumiteStatHolder statHolder) {
         damage = statHolder.getBaseDamage();
      }

      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         punchStr = corePlayer.getPunchStrength();
         grabbedTarget = corePlayer.getGrabbedTarget();
      }

      float finalDamage = damage * 2.0F + punchStr * damage * 3.0F;
      float launchForce = 4.5F + punchStr * 5.5F;
      double scale = 1.0 + (double)punchStr * 1.5;
      double rOut = 9.0 * scale;
      double rBase = 9.0 * scale;
      double rIn = 1.0 * scale;
      double minCosTheta = rOut / Math.sqrt(rOut * rOut + rBase * rBase);
      AABB hitBox = new AABB(
         apex.x - rBase, apex.y - rBase, apex.z - rBase, apex.x + rBase, apex.y + rBase, apex.z + rBase
      );
      List<Entity> entities = world.getEntities(player, hitBox);
      boolean grabbedWasHit = false;

      for (Entity target : entities) {
         if (target instanceof LivingEntity) {
            LivingEntity livingTarget = (LivingEntity)target;
            Vec3 targetVector = livingTarget.getBoundingBox().getCenter().subtract(apex);
            double d = targetVector.length();
            double h = targetVector.dot(direction);
            double distToAxisSq = targetVector.lengthSqr() - h * h;
            boolean inMainShape = d >= rIn && d <= rOut && h / d >= minCosTheta;
            boolean inGuaranteedZone = h > 0.0 && h <= 3.5 * scale && distToAxisSq <= 2.25 * scale * scale;
            if (inMainShape || inGuaranteedZone) {
               if (livingTarget == grabbedTarget) {
                  grabbedWasHit = true;
               }

               if (player instanceof ViltrumiteCorePlayer corePlayer) {
                  corePlayer.releaseTarget();
               }

               boolean var10000;
               label136: {
                  livingTarget.hurt(player.damageSources().playerAttack(player), finalDamage);
                  if (livingTarget instanceof ViltrumiteCorePlayer) {
                     ViltrumiteCorePlayer coreTarget = (ViltrumiteCorePlayer)livingTarget;
                     if (coreTarget.isBlocking()) {
                        var10000 = true;
                        break label136;
                     }
                  }

                  var10000 = false;
               }

               boolean isTargetBlocking = var10000;
               boolean successfulBlock = false;
               if (isTargetBlocking) {
                  Vec3 targetLook = livingTarget.getLookAngle().normalize();
                  Vec3 toAttacker = player.position().subtract(livingTarget.position()).normalize();
                  if (targetLook.dot(toAttacker) > 0.0) {
                     successfulBlock = true;
                  }
               }

               // Hero policy: an active Lion's Heart cannot be knocked back or launched.
               if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(livingTarget, dev.baranhan.viltrumitecore.hero.control.ControlKind.IMPULSE)) {
                  if (successfulBlock) {
                     Vec3 blockKnockback = direction.scale(1.5).add(0.0, 0.2, 0.0);
                     livingTarget.setDeltaMovement(blockKnockback);
                     livingTarget.hasImpulse = true;
                     if (livingTarget instanceof ServerPlayer serverTarget) {
                        serverTarget.connection.send(new ClientboundSetEntityMotionPacket(serverTarget));
                     }
                  } else {
                     if (isTargetBlocking) {
                        ((ViltrumiteCorePlayer)livingTarget).setBlocking(false);
                     }

                     Vec3 launchVelocity = direction.scale((double)launchForce).add(0.0, 0.5, 0.0);
                     livingTarget.setDeltaMovement(launchVelocity);
                     livingTarget.hasImpulse = true;
                     if (livingTarget instanceof ViltrumiteFlightPlayer flightTarget) {
                        flightTarget.stopFlight();
                     }

                     LAUNCHED_ENTITIES.put(livingTarget, new PunchImpactManager.MeteorData(launchVelocity, 30, player.getUUID()));
                  }
               }
            }
         }
      }

      if (grabbedTarget != null && !grabbedWasHit) {
         if (player instanceof ViltrumiteCorePlayer corePlayer) {
            corePlayer.releaseTarget();
         }

         grabbedTarget.hurt(player.damageSources().playerAttack(player), finalDamage);
         if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(grabbedTarget, dev.baranhan.viltrumitecore.hero.control.ControlKind.IMPULSE)) {
            Vec3 launchVelocity = direction.scale((double)launchForce).add(0.0, 0.5, 0.0);
            grabbedTarget.setDeltaMovement(launchVelocity);
            grabbedTarget.hasImpulse = true;
            if (grabbedTarget instanceof ViltrumiteFlightPlayer flightTarget) {
               flightTarget.stopFlight();
            }

            LAUNCHED_ENTITIES.put(grabbedTarget, new PunchImpactManager.MeteorData(launchVelocity, 30, player.getUUID()));
         }
      }

      int searchRad = (int)Math.ceil(9.0 * scale);
      BlockPos pos1 = BlockPos.containing(apex.x - (double)searchRad, apex.y - (double)searchRad, apex.z - (double)searchRad);
      BlockPos pos2 = BlockPos.containing(apex.x + (double)searchRad, apex.y + (double)searchRad, apex.z + (double)searchRad);
      float dropChance = ViltrumiteCoreConfig.INSTANCE.punchBlockDropChance / 100.0F;
      boolean brokeAnyBlockShockwave = false;

      for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
         Vec3 blockCenter = pos.getCenter();
         Vec3 blockVector = blockCenter.subtract(apex);
         double dSq = blockVector.lengthSqr();
         if (!(dSq < 0.01) && !(dSq > rOut * rOut)) {
            double d = Math.sqrt(dSq);
            if (!(d < 0.1) && !(d > rOut)) {
               double h = blockVector.dot(direction);
               double cosTheta = h / d;
               double distToAxisSq = blockVector.lengthSqr() - h * h;
               boolean inMainShape = d >= rIn && cosTheta >= minCosTheta;
               boolean inTunnel = d < 5.0 * scale && distToAxisSq <= 2.5 * scale * scale && h > 0.0;
               if (inMainShape || inTunnel) {
                  BlockState state = world.getBlockState(pos);
                  if (!state.isAir() && state.getDestroySpeed(world, pos) >= 0.0F) {
                     if (world.random.nextFloat() < dropChance) {
                        Block.dropResources(state, world, pos);
                     }

                     world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                     world.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        (double)pos.getX() + 0.5,
                        (double)pos.getY() + 0.5,
                        (double)pos.getZ() + 0.5,
                        15,
                        0.25,
                        0.25,
                        0.25,
                        0.05
                     );
                     brokeAnyBlockShockwave = true;
                  }
               }
            }
         }
      }

      if (brokeAnyBlockShockwave) {
         world.playSound(null, BlockPos.containing(apex), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
      }
   }

   public static void tickMeteorPhysics(ServerLevel world) {
      if (!LAUNCHED_ENTITIES.isEmpty()) {
         Iterator<Entry<Entity, PunchImpactManager.MeteorData>> it = LAUNCHED_ENTITIES.entrySet().iterator();
         float vaporizeChance = ViltrumiteCoreConfig.INSTANCE.punchBlockDropChance / 100.0F;

         while (it.hasNext()) {
            Entry<Entity, PunchImpactManager.MeteorData> entry = it.next();
            Entity entity = entry.getKey();
            PunchImpactManager.MeteorData data = entry.getValue();
            Vec3 velocity = data.velocity;
            double speed = velocity.length();
            if (entity instanceof Player) {
               data.ticksLeft--;
               if (data.ticksLeft <= 0) {
                  it.remove();
                  continue;
               }
            }

            if (!entity.isRemoved() && world.hasChunkAt(entity.blockPosition()) && !(speed < 0.5)) {
               AABB travelBox = entity.getBoundingBox().expandTowards(velocity).inflate(0.2);
               int minX = Mth.floor(travelBox.minX);
               int minY = Mth.floor(travelBox.minY);
               int minZ = Mth.floor(travelBox.minZ);
               int maxX = Mth.floor(travelBox.maxX);
               int maxY = Mth.floor(travelBox.maxY);
               int maxZ = Mth.floor(travelBox.maxZ);
               int blocksBroken = 0;
               MutableBlockPos mutablePos = new MutableBlockPos();
               boolean hitUnbreakable = false;

               for (int x = minX; x <= maxX; x++) {
                  for (int y = minY; y <= maxY; y++) {
                     for (int z = minZ; z <= maxZ; z++) {
                        mutablePos.set(x, y, z);
                        BlockState state = world.getBlockState(mutablePos);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                           float hardness = state.getDestroySpeed(world, mutablePos);
                           if (hardness >= 0.0F && hardness <= 50.0F) {
                              boolean drop = world.random.nextFloat() >= vaporizeChance;
                              world.destroyBlock(mutablePos, drop);
                              blocksBroken++;
                           } else if (hardness < 0.0F) {
                              hitUnbreakable = true;
                           }
                        }
                     }
                  }
               }

               double crossSectionArea = (double)(entity.getBbWidth() * entity.getBbHeight());
               double baseThreshold = speed * Math.max(4.0, Math.min(crossSectionArea * 3.0, 32.0));
               double downwardRatio = 0.0;
               if (velocity.y < -0.5) {
                  downwardRatio = Math.abs(velocity.y) / speed;
               }

               double dynamicThreshold = baseThreshold * (1.0 - downwardRatio * 0.6);
               if (hitUnbreakable || (double)blocksBroken > dynamicThreshold) {
                  float explosionPower = (float)Math.max(3.0, speed);
                  world.explode(entity, entity.getX(), entity.getY(), entity.getZ(), explosionPower, ExplosionInteraction.BLOCK);
                  // Keep the fly_into_wall type but name the launcher as the
                  // cause so a hero target can arm its Counter on the hit.
                  Entity launcher = data.attackerId != null ? world.getEntity(data.attackerId) : null;
                  entity.hurt(new net.minecraft.world.damagesource.DamageSource(world.damageSources().flyIntoWall().typeHolder(), null, launcher), (float)speed * 10.0F);
                  it.remove();
               } else if (blocksBroken > 0) {
                  double brakeForce = 0.85;
                  if (velocity.y < -0.5) {
                     brakeForce = 0.7;
                  }

                  Vec3 newVelocity = velocity.scale(brakeForce);
                  entity.setDeltaMovement(newVelocity);
                  entity.hasImpulse = true;
                  data.velocity = newVelocity;
               } else {
                  Vec3 newVelocity = new Vec3(velocity.x * 0.98, velocity.y * 0.98 - 0.08, velocity.z * 0.98);
                  entity.setDeltaMovement(newVelocity);
                  entity.hasImpulse = true;
                  data.velocity = newVelocity;
               }
            } else {
               it.remove();
            }
         }
      }
   }

   public static class MeteorData {
      public Vec3 velocity;
      public int ticksLeft;
      // The launcher: credit for the eventual wall hit (Counter attribution).
      @javax.annotation.Nullable
      public final java.util.UUID attackerId;

      public MeteorData(Vec3 velocity, int ticksLeft, @javax.annotation.Nullable java.util.UUID attackerId) {
         this.velocity = velocity;
         this.ticksLeft = ticksLeft;
         this.attackerId = attackerId;
      }
   }
}
