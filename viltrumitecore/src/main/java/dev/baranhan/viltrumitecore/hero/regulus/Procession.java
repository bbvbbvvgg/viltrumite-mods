package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «Шествие» (spec §5.5, needs Lion's Heart): he simply walks, hands behind his back, and the world
 * that is in the way gives. ×2.2 walking speed for 50 ticks; anything living he walks into is struck
 * (10, thrown aside, once per 10 ticks each); blocks up to hardness 5 in his path shatter
 * (60-block budget). A second press stops the walk.
 */
public final class Procession {
   static final UUID SPEED_ID = UUID.fromString("4e5a1c0b-7d3e-4a7c-9b1f-2c6d8e0f1a02");

   private Procession() {
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.move == RegulusMove.PROCESSION) {
         stop(player, state);
         state.clearMove();
         return;
      }

      if (!state.lion || state.processionCooldown > 0 || state.busy()) {
         if (!state.lion) {
            RegulusHero.refuse(player, state);
         }

         return;
      }

      state.startMove(RegulusMove.PROCESSION);
      state.processionCooldown = RegulusRules.PROCESSION_COOLDOWN;
      state.debrisBudget = RegulusRules.PROCESSION_BLOCK_BUDGET;
      state.rammed.clear();
      speed(player, true);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move != RegulusMove.PROCESSION) {
         return;
      }

      if (!state.lion) {
         stop(player, state);
         state.clearMove();
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
      if (state.moveTick % 6 == 0) {
         RegulusSounds.play(level, player.position(), RegulusSounds.PROCESSION_STEP, 0.7F, 0.9F + level.random.nextFloat() * 0.15F);
      }

      // ram what is in front of the body
      AABB front = player.getBoundingBox().inflate(0.35, 0.0, 0.35).move(flat.scale(0.6));
      long now = level.getGameTime();
      for (Entity entity : level.getEntities(player, front)) {
         if (!RegulusCombat.hittable(player, entity, state)) {
            continue;
         }

         Long last = state.rammed.get(entity.getUUID());
         if (last != null && now - last < RegulusRules.PROCESSION_HIT_GAP) {
            continue;
         }

         state.rammed.put(entity.getUUID(), now);
         LivingEntity target = (LivingEntity) entity;
         Vec3 side = target.position().subtract(player.position());
         Vec3 away = flat.scale(0.8).add(new Vec3(side.x, 0.0, side.z).normalize().scale(0.6));
         target.invulnerableTime = 0;
         if (RegulusCombat.hit(player, target, RegulusRules.PROCESSION_DAMAGE)) {
            RegulusCombat.push(target, away, 1.4, 0.45);
            Vec3 at = target.getBoundingBox().getCenter();
            RegulusSounds.play(level, at, RegulusSounds.PROCESSION_RAM, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            RegulusNet.fx(level, RegulusFxS2CPacket.PROCESSION_RAM, player, target.getId(), at, flat, 1.0F, 0);
         }
      }

      // the wall gives: body-sized column one step ahead
      if (state.debrisBudget > 0 && flat.lengthSqr() > 0.0) {
         Vec3 ahead = player.position().add(flat.scale(0.9));
         for (int dy = 0; dy <= 2 && state.debrisBudget > 0; dy++) {
            for (int dx = -1; dx <= 1 && state.debrisBudget > 0; dx++) {
               Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(dx * 0.45);
               BlockPos pos = BlockPos.containing(ahead.add(side).add(0.0, dy + 0.1, 0.0));
               if (dy == 0 && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()) {
                  continue; // a step he can walk up stays
               }

               if (!level.getBlockState(pos).isAir() && RegulusCombat.breakBlock(player, level, pos, RegulusRules.PROCESSION_BLOCK_HARDNESS)) {
                  state.debrisBudget--;
               }
            }
         }
      }
   }

   static void stop(ServerPlayer player, RegulusState state) {
      speed(player, false);
      state.rammed.clear();
   }

   static void speed(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      speed.removeModifier(SPEED_ID);
      if (on) {
         speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Regulus Procession", RegulusRules.PROCESSION_SPEED - 1.0,
            AttributeModifier.Operation.MULTIPLY_TOTAL));
      }
   }
}
