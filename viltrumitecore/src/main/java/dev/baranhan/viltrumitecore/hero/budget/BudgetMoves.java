package dev.baranhan.viltrumitecore.hero.budget;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.BudgetSceneS2CPacket;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Server effects of the five shared mechanics. Everything is a one-shot:
 * no lingering entities, no repeating damage, bounded particle counts and a
 * capped number of targets per hit, so no move can loop or flood a server.
 */
public final class BudgetMoves {
   static final double PUNCH_RANGE = 4.0;
   static final double BEAM_RANGE = 24.0;
   static final double ULT_RANGE = 32.0;
   static final int MAX_AREA_TARGETS = 12;
   static final int BUFF_TICKS = 200;

   private BudgetMoves() {
   }

   static void perform(ServerPlayer player, BudgetHeroSpec spec, BudgetMove move, BudgetState state) {
      perform(player, move, state, 1.0F);
   }

   /**
    * NEXT-GENERATION MULTIVERSE FRAMEWORK: the same five mechanics for any
    * living caster. Players pass their state and scale 1; multiverse NPCs pass
    * no state (no fall guard, no granted-effect bookkeeping) and their own
    * damage scale.
    */
   /** NPC entry point: no hero state. */
   public static void cast(LivingEntity caster, BudgetMove move, float damageScale) {
      perform(caster, move, null, damageScale);
   }

   static void perform(LivingEntity player, BudgetMove move, @Nullable BudgetState state, float damageScale) {
      float scale = Math.max(0.0F, damageScale);
      switch (move.type()) {
         case A -> punch(player, move, scale);
         case B -> beam(player, move, false, scale);
         case C -> dash(player, move, state);
         case D -> buff(player, move, state);
         case E -> {
            if (move.shape() == BudgetShape.RING) {
               ring(player, move, scale);
            } else {
               beam(player, move, true, scale);
            }
         }
      }
   }

   // ---- type A: strike -------------------------------------------------

   static void punch(LivingEntity player, BudgetMove move, float scale) {
      ServerLevel level = (ServerLevel)player.level();
      player.swing(InteractionHand.MAIN_HAND, true);
      LivingEntity target = nearestInFront(player, PUNCH_RANGE);
      sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
      if (target == null) {
         Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.5));
         level.sendParticles(dust(move.color(), 1.0F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
         return;
      }

      hurt(player, target, move.damage() * scale);
      target.knockback(0.6, player.getX() - target.getX(), player.getZ() - target.getZ());
      level.sendParticles(dust(move.color(), 1.4F), target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
      level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
   }

   // ---- type B / E: beam -----------------------------------------------

   static void beam(LivingEntity player, BudgetMove move, boolean ultimate, float scale) {
      ServerLevel level = (ServerLevel)player.level();
      player.swing(ultimate ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
      double range = ultimate ? ULT_RANGE : BEAM_RANGE;
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 end = eye.add(look.scale(range));
      BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }

      AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.5);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, sweep, entity -> validTarget(player, entity));
      Vec3 impact = hit != null ? hit.getLocation() : end;

      // The beam itself: one coloured dust line, thicker for ultimates.
      Vec3 start = eye.add(look.scale(0.8)).add(0.0, -0.25, 0.0);
      double length = impact.distanceTo(start);
      int steps = (int)Math.min(64, Math.ceil(length / 0.5));
      DustParticleOptions dust = dust(move.color(), ultimate ? 2.5F : 1.3F);
      for (int i = 0; i <= steps; i++) {
         Vec3 p = start.lerp(impact, steps == 0 ? 1.0 : (double)i / steps);
         level.sendParticles(dust, p.x, p.y, p.z, ultimate ? 3 : 1, ultimate ? 0.15 : 0.02, ultimate ? 0.15 : 0.02, ultimate ? 0.15 : 0.02, 0.0);
      }

      sound(player, ultimate ? SoundEvents.ENDER_DRAGON_SHOOT : SoundEvents.BLAZE_SHOOT, ultimate ? 2.0F : 1.0F, ultimate ? 0.7F : 1.3F);
      if (hit != null && hit.getEntity() instanceof LivingEntity target) {
         hurt(player, target, move.damage() * scale);
         target.knockback(ultimate ? 1.0 : 0.4, -look.x, -look.z);
      }

      level.sendParticles(ultimate ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0.0, 0.0, 0.0, 0.0);
      if (ultimate) {
         level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.9F);
         area(player, impact, 3.5, move.damage() * 0.6F * scale, hit != null ? hit.getEntity() : null);
         ringParticles(level, player.position().add(0.0, 0.1, 0.0), 2.0, move.color(), 20);
      }
   }

   // ---- type C: dash ---------------------------------------------------

   static void dash(LivingEntity player, BudgetMove move, @Nullable BudgetState state) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
      player.setDeltaMovement(flat.x * 1.8, Math.max(0.2, player.getDeltaMovement().y), flat.z * 1.8);
      player.hurtMarked = true;
      player.fallDistance = 0.0F;
      if (state != null && player instanceof ServerPlayer serverPlayer) {
         state.noFallUntil = BudgetHero.now(serverPlayer) + 60;
      }
      ServerLevel level = (ServerLevel)player.level();
      level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
      level.sendParticles(dust(move.color(), 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
      sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
   }

   // ---- type D: power-up -----------------------------------------------

   static void buff(LivingEntity player, BudgetMove move, @Nullable BudgetState state) {
      if (move.buff().effect() == null) {
         return;
      }

      player.addEffect(new MobEffectInstance(move.buff().effect(), BUFF_TICKS, 1, false, true, true));
      if (state != null) {
         state.granted.add(move.buff().effect());
      }
      player.swing(InteractionHand.OFF_HAND, true);
      ServerLevel level = (ServerLevel)player.level();
      ringParticles(level, player.position().add(0.0, 0.2, 0.0), 1.2, move.color(), 16);
      ringParticles(level, player.position().add(0.0, 1.2, 0.0), 0.9, move.color(), 12);
      sound(player, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.2F);
   }

   // ---- type E (ring): area around the caster --------------------------

   static void ring(LivingEntity player, BudgetMove move, float scale) {
      ServerLevel level = (ServerLevel)player.level();
      player.swing(InteractionHand.MAIN_HAND, true);
      player.swing(InteractionHand.OFF_HAND, true);
      Vec3 center = player.position();
      ringParticles(level, center.add(0.0, 0.2, 0.0), 2.5, move.color(), 24);
      ringParticles(level, center.add(0.0, 0.4, 0.0), 5.0, move.color(), 36);
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0);
      level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
      area(player, center, 5.0, move.damage() * scale, null);
   }

   // ---- scenes ---------------------------------------------------------

   static void startScene(ServerPlayer player, BudgetHeroSpec spec, BudgetState state) {
      BudgetScene scene = spec.scene();
      CoreMessages.sendToTrackingAndSelf(new BudgetSceneS2CPacket(player.getId(), scene.ordinal(), scene.windup), player);
      if (scene.freeze) {
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, scene.windup, 6, false, false, false));
         state.granted.add(MobEffects.MOVEMENT_SLOWDOWN);
      }

      if (scene.hover) {
         player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, scene.windup, 0, false, false, false));
         state.granted.add(MobEffects.LEVITATION);
         state.noFallUntil = BudgetHero.now(player) + scene.windup + 100;
      }

      switch (scene) {
         case GOKU -> sound(player, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.8F);
         case GOJO -> sound(player, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 1.0F);
         case SUKUNA -> sound(player, SoundEvents.WITHER_AMBIENT, 1.0F, 0.6F);
         case SUPERMAN -> sound(player, SoundEvents.BEACON_ACTIVATE, 1.5F, 1.2F);
         case SHREK -> sound(player, SoundEvents.RAVAGER_ROAR, 3.0F, 0.8F);
         case AIZEN -> sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 0.6F);
         case ICHIGO -> sound(player, SoundEvents.WITHER_SPAWN, 1.0F, 1.4F);
         case VERGIL -> sound(player, SoundEvents.BELL_RESONATE, 2.0F, 1.6F);
         case EREN -> {
            // Visual-only lightning: no fire, no damage, no block changes.
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(player.serverLevel());
            if (bolt != null) {
               bolt.moveTo(player.getX(), player.getY(), player.getZ());
               bolt.setVisualOnly(true);
               player.serverLevel().addFreshEntity(bolt);
            }
            sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.7F);
         }
         case AINZ -> sound(player, SoundEvents.WITHER_AMBIENT, 1.5F, 0.4F);
         case SHADOW -> sound(player, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
         case ESCANOR -> sound(player, SoundEvents.BLAZE_AMBIENT, 2.0F, 0.6F);
         default -> {
         }
      }
   }

   /** One cheap particle puff every 5 ticks while the scene winds up. */
   static void tickScene(ServerPlayer player, BudgetHeroSpec spec, BudgetState state) {
      if (state.sceneTicks % 5 != 0) {
         return;
      }

      ServerLevel level = player.serverLevel();
      BudgetScene scene = spec.scene();
      Vec3 hands = player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0.0, -0.4, 0.0);
      switch (scene) {
         case GOKU -> level.sendParticles(dust(scene.flashColor, 2.0F), hands.x, hands.y, hands.z, 6, 0.15, 0.15, 0.15, 0.0);
         case SUKUNA -> {
            if (state.sceneTicks == 0) {
               level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(1.0), player.getZ(), 20, 0.6, 0.6, 0.6, 0.05);
            }
         }
         case SHREK -> ringParticles(level, player.position().add(0.0, 0.3, 0.0), 1.5 + (20 - state.sceneTicks) * 0.15, scene.flashColor, 16);
         case AIZEN -> ringParticles(level, player.position().add(0.0, 0.5, 0.0), 1.0 + (scene.windup - state.sceneTicks) * 0.15, scene.flashColor, 20);
         case ICHIGO -> {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            level.sendParticles(dust(0x100010, 2.5F), player.getX(), player.getY(2.2), player.getZ(), 8, 0.2, 0.4, 0.2, 0.0);
         }
         case VERGIL -> {
            for (int i = 0; i < 3; i++) {
               double dx = (player.getRandom().nextDouble() - 0.5) * 8.0;
               double dz = (player.getRandom().nextDouble() - 0.5) * 8.0;
               level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dx, player.getY(0.6), player.getZ() + dz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.8F);
         }
         case EREN -> level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 10, 0.6, 0.8, 0.6, 0.05);
         case AINZ -> {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.2), player.getZ(), 10, 1.0, 0.1, 1.0, 0.02);
            sound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F, 0.5F);
         }
         case SHADOW -> {
            double r = 1.0 + state.sceneTicks * 0.1;
            ringParticles(level, player.position().add(0.0, 1.0, 0.0), r, scene.flashColor, 18);
         }
         case ESCANOR -> level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 3.0, player.getZ(), 12, 0.4, 0.4, 0.4, 0.01);
         default -> level.sendParticles(dust(scene.flashColor, 1.5F), player.getX(), player.getY(0.5), player.getZ(), 6, 0.5, 0.6, 0.5, 0.0);
      }
   }

   static void cancelScene(ServerPlayer player) {
      CoreMessages.sendToTrackingAndSelf(new BudgetSceneS2CPacket(player.getId(), BudgetScene.NONE.ordinal(), 0), player);
   }

   // ---- helpers --------------------------------------------------------

   public static void area(LivingEntity player, Vec3 center, double radius, float damage, @Nullable Entity skip) {
      List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
         entity -> entity != skip && validTarget(player, entity) && entity.distanceToSqr(center) <= radius * radius);
      int count = 0;
      for (LivingEntity target : targets) {
         if (count++ >= MAX_AREA_TARGETS) {
            break;
         }

         hurt(player, target, damage);
         target.knockback(1.0, center.x - target.getX(), center.z - target.getZ());
      }
   }

   private static void hurt(LivingEntity player, LivingEntity target, float damage) {
      if (damage > 0.0F) {
         target.hurt(player instanceof Player human ? player.damageSources().playerAttack(human) : player.damageSources().mobAttack(player), damage);
      }
   }

   static boolean validTarget(LivingEntity player, Entity entity) {
      return entity instanceof LivingEntity living && living != player && living.isAlive() && !living.isSpectator()
         && living.isPickable() && !player.isPassengerOfSameVehicle(living)
         && !dev.baranhan.viltrumitecore.hero.budget.multiverse.MultiverseFighter.alliedCaster(player, living);
   }

   static LivingEntity nearestInFront(LivingEntity player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
         entity -> validTarget(player, entity))) {
         Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
         double distance = to.length();
         if (distance > range + entity.getBbWidth() || distance < 1.0E-3) {
            continue;
         }

         double facing = to.normalize().dot(look);
         if (facing < 0.5) {
            continue;
         }

         double score = distance * (2.0 - facing);
         if (score < bestScore) {
            bestScore = score;
            best = entity;
         }
      }

      return best;
   }

   public static void ringParticles(ServerLevel level, Vec3 center, double radius, int color, int points) {
      DustParticleOptions dust = dust(color, 1.5F);
      for (int i = 0; i < points; i++) {
         double angle = Math.PI * 2.0 * i / points;
         level.sendParticles(dust, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static DustParticleOptions dust(int color, float size) {
      return new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F), size);
   }

   static void sound(LivingEntity player, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
   }

}
