package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Касание жадности» (spec §5.2): the empty-hand LMB combo. Backhand → palm → flick; each strike
 * stops the air in front of the hand for an instant and the stopped air does the hitting. The hit
 * is resolved on the server at the move's contact tick against live positions (moving targets),
 * at most three targets in a 70° cone. The flick under Lion's Heart leaves a delayed rupture.
 */
public final class GreedsTouch {
   private GreedsTouch() {
   }

   static void press(ServerPlayer player, RegulusState state) {
      RegulusMove move = state.move;
      if (move != null) {
         if (move.isTouch() && state.moveTick >= move.contact()) {
            state.touchQueued = true;
         }

         return;
      }

      start(player, state);
   }

   static void start(ServerPlayer player, RegulusState state) {
      int step = state.chainLeft > 0 && state.comboStep >= 0 && state.comboStep < 2 ? state.comboStep + 1 : 0;
      state.comboStep = step;
      state.touchQueued = false;
      state.chainLeft = 0;
      state.startMove(RegulusMove.touch(step));
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      RegulusMove move = state.move;
      if (move == null || !move.isTouch()) {
         return;
      }

      if (state.moveTick == move.contact() - 2) {
         RegulusSounds.play(player.level(), player.getEyePosition(), RegulusSounds.TOUCH_SWISH, 0.6F, 0.9F + 0.1F * state.comboStep);
      }

      if (state.moveTick == move.contact()) {
         contact(player, state, move);
      }
   }

   /** Move end: open the chain window or run the queued next strike. */
   static void onEnd(ServerPlayer player, RegulusState state, RegulusMove move) {
      if (!move.isTouch()) {
         return;
      }

      if (state.touchQueued && state.comboStep < 2) {
         state.chainLeft = 1;
         start(player, state);
         return;
      }

      state.touchQueued = false;
      state.chainLeft = state.comboStep < 2 ? RegulusRules.TOUCH_CHAIN_WINDOW : 0;
      if (state.comboStep >= 2) {
         state.comboStep = -1;
      }
   }

   private static void contact(ServerPlayer player, RegulusState state, RegulusMove move) {
      ServerLevel level = (ServerLevel) player.level();
      List<LivingEntity> targets = RegulusCombat.cone(player, state, RegulusRules.TOUCH_REACH, RegulusRules.TOUCH_CONE_DEGREES, RegulusRules.TOUCH_MAX_TARGETS);
      Vec3 look = player.getLookAngle();
      Vec3 side = new Vec3(-look.z, 0.0, look.x);
      float damage = move.touchDamage(state.lion);
      boolean any = false;
      for (LivingEntity target : targets) {
         if (!RegulusCombat.hit(player, target, damage)) {
            continue;
         }

         any = true;
         switch (move) {
            case TOUCH_BACKHAND -> RegulusCombat.push(target, look.scale(0.6).add(side.scale(-0.7)), 0.55, 0.12);
            case TOUCH_PALM -> RegulusCombat.push(target, look, 0.9, 0.18);
            default -> {
               RegulusCombat.push(target, look, 1.6, 0.42);
               if (state.lion) {
                  state.ruptures.add(new RegulusState.Rupture(target.getUUID(), level.getGameTime() + RegulusRules.RUPTURE_DELAY, RegulusRules.RUPTURE_DAMAGE));
               }
            }
         }

         Vec3 at = target.getBoundingBox().getCenter();
         RegulusSounds.play(level, at, RegulusSounds.TOUCH_HIT, 0.9F, 1.05F - 0.08F * state.comboStep);
         RegulusNet.fx(level, RegulusFxS2CPacket.TOUCH_HIT, player, target.getId(), at, look, damage, move.ordinal());
      }

      if (!any) {
         Vec3 eye = player.getEyePosition();
         Vec3 reach = eye.add(look.scale(RegulusRules.TOUCH_REACH));
         BlockHitResult block = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
         Vec3 at = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : eye.add(look.scale(2.2));
         if (move == RegulusMove.TOUCH_FLICK && block.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            RegulusCombat.breakBlock(player, level, pos, RegulusRules.SAND_BLOCK_HARDNESS);
         }

         RegulusNet.fx(level, RegulusFxS2CPacket.TOUCH_MISS, player, -1, at, look, damage, move.ordinal());
      }
   }

   /** Delayed ruptures of the Lion flick: the stopped air in the wound lets go. */
   static void tickRuptures(ServerPlayer player, RegulusState state) {
      if (state.ruptures.isEmpty()) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      long now = level.getGameTime();
      Iterator<RegulusState.Rupture> it = state.ruptures.iterator();
      while (it.hasNext()) {
         RegulusState.Rupture rupture = it.next();
         if (now < rupture.at()) {
            continue;
         }

         it.remove();
         Entity entity = level.getEntity(rupture.target());
         if (entity instanceof LivingEntity target && target.isAlive() && target.distanceToSqr(player) < 64.0 * 64.0) {
            target.invulnerableTime = 0;
            RegulusCombat.hit(player, target, rupture.damage());
            Vec3 at = target.getBoundingBox().getCenter();
            RegulusSounds.play(level, at, RegulusSounds.RUPTURE, 1.0F, 1.0F);
            RegulusNet.fx(level, RegulusFxS2CPacket.RUPTURE, player, target.getId(), at);
         }
      }
   }
}
