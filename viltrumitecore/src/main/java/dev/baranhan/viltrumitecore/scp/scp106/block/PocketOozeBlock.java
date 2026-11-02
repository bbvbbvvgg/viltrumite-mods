package dev.baranhan.viltrumitecore.scp.scp106.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The Old Man's black ooze: slow to wade, it eats at whoever stands in it (short wither, never lethal on its own). */
public class PocketOozeBlock extends Block {
   public PocketOozeBlock(Properties p) {
      super(p);
   }

   @Override
   public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
      if (!level.isClientSide && entity instanceof Player player && !player.isCreative() && !player.isSpectator() && level.getGameTime() % 20 == 0) {
         if (player.getHealth() > 6.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0, false, true));
         }
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
      }
      super.stepOn(level, pos, state, entity);
   }
}
