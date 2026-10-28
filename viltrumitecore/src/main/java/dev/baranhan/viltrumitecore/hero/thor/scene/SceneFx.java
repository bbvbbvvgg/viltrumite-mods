package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.entity.BloodDropEntity;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Damage, allowed destruction, blood and sound of session C, on the shared
 * modules: HeroDestruction (mobGriefing, unbreakable blocks; spawn
 * protection via mayInteract — protected blocks stay), HeroFx (rings,
 * flashes, hit stop), BloodDropEntity (splashes and ground stains).
 * Gameplay first, FX after. Server main thread.
 */
final class SceneFx {
   private SceneFx() {
   }

   static void sound(ServerPlayer source, Vec3 at, SoundEvent sound, float volume, float pitch) {
      source.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }

   static void move(LivingEntity target, Vec3 velocity) {
      target.setDeltaMovement(velocity);
      target.hasImpulse = true;
      target.hurtMarked = true;
      if (target instanceof ServerPlayer player) {
         player.connection.send(new ClientboundSetEntityMotionPacket(player));
      }
   }

   static void trail(ServerPlayer thor, LivingEntity body) {
      HeroFx.airTrail(thor, body);
   }

   static void blood(ServerPlayer thor, Vec3 at, Vec3 push, int count) {
      BloodDropEntity.burst(thor.serverLevel(), at, push, count, false);
   }

   /** One Thor hit from a scene (no LETHAL_HIT: that event belongs to B's combat path). */
   static boolean hurt(ServerPlayer thor, LivingEntity target, float amount) {
      target.invulnerableTime = 0;
      return target.hurt(thor.damageSources().playerAttack(thor), amount);
   }

   /** Allowed destruction of a small bowl around a point. */
   static void crater(ServerPlayer thor, BlockPos center, int radius) {
      ServerLevel level = thor.serverLevel();
      java.util.List<BlockPos> blocks = new java.util.ArrayList<>();
      for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 0, radius))) {
         if (pos.distSqr(center) <= radius * radius + 1 && level.isLoaded(pos) && level.mayInteract(thor, pos)) {
            blocks.add(pos.immutable());
         }
      }

      // MINOR-7: through the shared block budget (CarveJobs), HeroDestruction decides each block (mobGriefing, protected).
      dev.baranhan.viltrumitecore.hero.thor.combat.ElectricFx.carve(level, blocks);
   }

   @Nullable
   private static BlockState floor(ServerLevel level, Vec3 at) {
      BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
      BlockState state = level.getBlockState(below);
      return state.isAir() ? null : state;
   }

   /** Ground under a point: the first block with collision at or below it (up to 6 down), else the point. */
   static Vec3 ground(ServerLevel level, Vec3 at) {
      BlockPos start = BlockPos.containing(at.x, at.y + 0.5, at.z);
      for (int i = 0; i < 7; i++) {
         BlockPos pos = start.below(i);
         var shape = level.getBlockState(pos).getCollisionShape(level, pos);
         if (!shape.isEmpty()) {
            return new Vec3(at.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
         }
      }
      return at;
   }

   /** MINOR-8: the slam drives the body at most this far down onto the ground; a deeper drop smashes it where it is. */
   static final double SLAM_MAX_DROP = 1.5;

   static void slam(ServerPlayer thor, LivingEntity victim, Vec3 at) {
      ServerLevel level = thor.serverLevel();
      Vec3 floor = ground(level, at);
      // C-B2: only onto a free spot right below; never a long teleport down, never into blocks.
      Vec3 ground = at.y - floor.y <= SLAM_MAX_DROP && SceneSpace.boxFree(level, victim, floor) ? floor : at;
      victim.setPos(ground.x, ground.y, ground.z);
      hurt(thor, victim, ThorSceneConfig.slamDamage);
      BlockState material = floor(level, ground);
      crater(thor, BlockPos.containing(ground.x, ground.y - 0.5, ground.z), ThorSceneConfig.slamCraterRadius);
      move(victim, new Vec3(0, -0.4, 0));
      HeroFx.slam(thor, ground, material, 2.5F);
      HeroFx.hitStop(thor, ground, 3, thor.getId(), victim.getId());
      blood(thor, ground.add(0, 0.3, 0), new Vec3(0, 0.25, 0), 8);
      sound(thor, ground, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.3F);
      sound(thor, ground, SoundEvents.ANVIL_LAND, 0.7F, 0.6F);
   }

   static void barragePunch(ServerPlayer thor, @Nullable LivingEntity victim, int index) {
      if (victim == null) {
         return;
      }

      hurt(thor, victim, ThorSceneConfig.barrageDamage);
      Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.7, 0);
      blood(thor, at, SceneFrame.forward(thor.getYRot()).scale(0.2), 2);
      HeroFx.hitStop(thor, at, 1, thor.getId(), victim.getId());
      sound(thor, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 0.8F + 0.06F * index);
   }

   static void barrageFinisher(ServerPlayer thor, LivingEntity victim) {
      hurt(thor, victim, ThorSceneConfig.barrageFinisherDamage);
      Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
      blood(thor, at, SceneFrame.forward(thor.getYRot()).scale(0.4), 6);
      HeroFx.flash(thor, at, 0.6F);
      sound(thor, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
   }

   /** A thrown body met a surface. */
   static void impact(ServerPlayer thor, LivingEntity body, Vec3 at, float damage, int radius) {
      hurt(thor, body, damage);
      if (radius > 0) {
         Vec3 v = body.getDeltaMovement();
         Vec3 ahead = v.lengthSqr() < 1.0E-4 ? at : at.add(v.normalize().scale(0.6));
         crater(thor, BlockPos.containing(ahead.x, ahead.y + body.getBbHeight() * 0.5, ahead.z), radius);
      }
      HeroFx.shockwave(thor, at, 1.0F, floor(thor.serverLevel(), at), 2.0F);
      blood(thor, at.add(0, body.getBbHeight() * 0.5, 0), Vec3.ZERO, 5);
      sound(thor, at, SoundEvents.GENERIC_EXPLODE, 0.5F, 1.4F);
   }

   /** A thrown body ploughs into somebody else. */
   static void bodyHit(ServerPlayer thor, LivingEntity other, Vec3 velocity) {
      hurt(thor, other, ThorSceneConfig.throwImpactDamage * 0.5F);
      if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(other)) {
         move(other, velocity.scale(0.5).add(0, 0.3, 0));
      }
      sound(thor, other.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.8F);
   }

   // --- fatality beats (no damage: the death was decided at the scene start) ---

   static void beat(ServerPlayer thor, LivingEntity victim, FatalityPlan plan, FatalityScene.Beat beat) {
      Vec3 feet = victim.position();
      Vec3 head = feet.add(0, victim.getBbHeight() * 0.85, 0);
      Vec3 chest = feet.add(0, victim.getBbHeight() * 0.6, 0);
      Vec3 forward = SceneFrame.forward(plan.frameYaw());
      ServerLevel level = thor.serverLevel();
      switch (beat) {
         case HEAD_CRUSH -> {
            blood(thor, head, new Vec3(0, 0.3, 0), 26);
            HeroFx.slam(thor, feet, floor(level, feet), 2.5F);
            HeroFx.hitStop(thor, head, 4, thor.getId(), victim.getId());
            sound(thor, head, SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
            sound(thor, head, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.8F, 0.6F);
         }
         case CHEST_PUNCH -> {
            blood(thor, chest, forward.scale(0.45), 20);
            HeroFx.hitStop(thor, chest, 4, thor.getId(), victim.getId());
            sound(thor, chest, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.5F);
            sound(thor, chest, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.8F, 0.7F);
         }
         case HEART_RIP -> {
            blood(thor, chest, forward.scale(-0.3).add(0, 0.2, 0), 16);
            sound(thor, chest, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.0F);
            sound(thor, chest, SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
         }
         case HEART_CRUSH -> {
            Vec3 hand = thor.position().add(0, 1.5, 0).add(forward.scale(0.5));
            blood(thor, hand, new Vec3(0, -0.1, 0), 10);
            sound(thor, hand, SoundEvents.SLIME_BLOCK_BREAK, 1.0F, 0.5F);
         }
         case UPPERCUT -> {
            blood(thor, head, new Vec3(0, 0.5, 0), 8);
            HeroFx.launch(thor, feet, 1.0F, floor(level, feet));
            sound(thor, head, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.5F);
            sound(thor, head, SoundEvents.ANVIL_PLACE, 0.7F, 0.6F);
         }
         case AIR_BLOW -> {
            blood(thor, chest, new Vec3(0, -0.5, 0), 30);
            HeroFx.flash(thor, chest, 1.0F);
            HeroFx.hitStop(thor, chest, 5, thor.getId(), victim.getId());
            sound(thor, chest, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 0.9F);
            sound(thor, chest, SoundEvents.ANVIL_LAND, 1.0F, 0.4F);
         }
         case GROUND_SLAM -> {
            Vec3 ground = ground(level, feet);
            HeroFx.slam(thor, ground, floor(level, ground), 4.0F);
            blood(thor, ground.add(0, 0.3, 0), new Vec3(0, 0.15, 0), 18);
            sound(thor, ground, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.8F);
         }
         case THOR_LAND -> {
            Vec3 ground = ground(level, thor.position());
            HeroFx.slam(thor, ground, floor(level, ground), 2.5F);
            sound(thor, ground, SoundEvents.ANVIL_LAND, 0.6F, 0.7F);
         }
      }
   }

   static boolean isPlayer(LivingEntity entity) {
      return entity instanceof Player;
   }
}
