package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * «Застывший песок» (spec §5.3): he scoops a handful of whatever lies at his feet (sand, earth,
 * gravel, stone dust, water when he stands in it or under rain), stops its time and flicks it. The
 * grains keep their speed through flesh (pierce 3) and soft blocks. Nothing to scoop in mid-air.
 */
public final class StoppedSand {
   private StoppedSand() {
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.sandCooldown > 0 || state.busy()) {
         return;
      }

      int material = material(player);
      if (material < 0) {
         RegulusHero.refuse(player, state);
         return;
      }

      state.startMove(RegulusMove.SAND);
      dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.ability(player, RegulusAbilities.STOPPED_SAND);
      state.castMaterial = material;
      state.sandCooldown = RegulusRules.SAND_COOLDOWN;
      RegulusSounds.play(player.level(), player.position().add(0.0, 0.4, 0.0), RegulusSounds.SAND_SCOOP, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move != RegulusMove.SAND) {
         return;
      }

      if (state.moveTick == RegulusRules.SAND_RELEASE) {
         Vec3 look = player.getLookAngle();
         RegulusSounds.play(player.level(), player.getEyePosition(), RegulusSounds.SAND_RELEASE, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
         StoppedGrains.fire(player, state, look, state.castMaterial);
      }
   }

   /**
    * The block state id of what he scoops, or -1 with nothing in reach: water when wet, else the
    * first solid block within two below the feet.
    */
   static int material(ServerPlayer player) {
      ServerLevel level = (ServerLevel) player.level();
      if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
         return Block.getId(Blocks.WATER.defaultBlockState());
      }

      BlockPos feet = player.blockPosition();
      for (int dy = 0; dy <= 2; dy++) {
         BlockPos pos = feet.below(dy);
         BlockState state = level.getBlockState(pos);
         if (!state.isAir() && state.isSolidRender(level, pos) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)) {
            return Block.getId(state);
         }

         if (!state.getFluidState().isEmpty()) {
            return Block.getId(state.getFluidState().createLegacyBlock());
         }
      }

      return -1;
   }
}
