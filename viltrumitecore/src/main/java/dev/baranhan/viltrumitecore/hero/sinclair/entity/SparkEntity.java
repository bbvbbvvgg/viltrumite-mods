package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Prestige spark (spec §5.0): 3.2 b/t, 48 blocks, 2.0 damage (assistant 1.5). */
public class SparkEntity extends SinclairProjectile {
   public SparkEntity(EntityType<? extends SparkEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected int maxAge() {
      return (int) Math.ceil(SinclairRules.SPARK_RANGE / SinclairRules.SPARK_SPEED);
   }

   @Override
   protected void onHitEntity(EntityHitResult hit) {
      LivingEntity target = SinclairCombat.living(hit.getEntity());
      ServerPlayer owner = SinclairOwned.liveOwner(this, this.sinclairOwner);
      if (target != null && owner != null) {
         float damage = this.fromAssistant() ? SinclairRules.ASSIST_SPARK_DAMAGE : SinclairRules.SPARK_DAMAGE;
         SinclairCombat.projectileHit(owner, this, target, damage);
      }

      SinclairSounds.play(this.level(), this.position(), SinclairSounds.SPARK_HIT, 0.7F, 0.9F + this.random.nextFloat() * 0.25F);
      this.discard();
   }

   @Override
   protected void onHitBlock(BlockHitResult hit) {
      SinclairSounds.play(this.level(), hit.getLocation(), SinclairSounds.SPARK_HIT, 0.45F, 1.3F + this.random.nextFloat() * 0.2F);
      this.discard();
   }
}
