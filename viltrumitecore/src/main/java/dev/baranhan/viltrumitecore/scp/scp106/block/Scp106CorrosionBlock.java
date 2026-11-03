package dev.baranhan.viltrumitecore.scp.scp106.block;

import dev.baranhan.viltrumitecore.scp.scp106.Scp106Entity;
import dev.baranhan.viltrumitecore.scp.scp106.Scp106Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SCP-106's corrosion: a black-brown film on any face (floor, wall, ceiling) where it walked, emerged or passed
 * through. It withers whoever stands in it and rots away by itself 6..10 minutes later (a scheduled tick, saved with
 * the chunk), so the city is never permanently stained.
 */
public class Scp106CorrosionBlock extends MultifaceBlock {
   private final MultifaceSpreader spreader = new MultifaceSpreader(this);

   public Scp106CorrosionBlock(Properties p) {
      super(p);
   }

   @Override
   public MultifaceSpreader getSpreader() {
      return this.spreader;
   }

   @Override
   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
      super.onPlace(state, level, pos, old, moving);
      if (!level.isClientSide && !old.is(this)) {
         level.scheduleTick(pos, this, Scp106Rules.corrosionLifetime(level.random.nextDouble()));
      }
   }

   @Override
   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      level.removeBlock(pos, false);
   }

   @Override
   public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
      if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof Scp106Entity) && level.getGameTime() % 20 == 0
         && !(entity instanceof Player p && (p.isCreative() || p.isSpectator()))) {
         living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
      }
   }
}
