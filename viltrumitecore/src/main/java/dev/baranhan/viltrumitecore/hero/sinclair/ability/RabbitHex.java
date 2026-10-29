package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexHatEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Rabbit Hex (spec §5.4): 10 t cast (hat off, wand over it, hat back), the spectral hat at tick 6, 18 t telegraph. */
public final class RabbitHex {
   private RabbitHex() {
   }

   public static void press(ServerPlayer player, SinclairState state) {
      if (state.hexCooldown > 0 || state.busy()) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.hexPoint = groundPoint(player);
      state.startMove(SinclairMove.HEX_CAST);
      SinclairSounds.play(player.level(), player.position().add(0.0, 1.6, 0.0), SinclairSounds.HEX_CAST, 1.0F, 1.0F);
   }

   /** §5.4: ray ≤ 24; an entity's feet; a block face dropped to the floor; in the air the far end lowered ≤ 8. */
   public static Vec3 groundPoint(ServerPlayer player) {
      LivingEntity enemy = Stagecraft.rayEnemy(player, SinclairRules.HEX_RANGE);
      if (enemy != null) {
         return drop(player, enemy.position().add(0.0, 0.1, 0.0));
      }

      BlockHitResult block = Stagecraft.blockHit(player, SinclairRules.HEX_RANGE);
      Vec3 at = block != null ? block.getLocation().add(player.getEyePosition().subtract(block.getLocation()).normalize().scale(0.05))
         : player.getEyePosition().add(player.getLookAngle().scale(SinclairRules.HEX_RANGE));
      return drop(player, at);
   }

   private static Vec3 drop(ServerPlayer player, Vec3 at) {
      BlockHitResult floor = player.level().clip(new ClipContext(at.add(0.0, 0.05, 0.0), at.add(0.0, -SinclairRules.HEX_AIR_DROP, 0.0),
         ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return floor.getType() == HitResult.Type.MISS ? at.add(0.0, -SinclairRules.HEX_AIR_DROP, 0.0) : floor.getLocation();
   }

   public static void tickMove(ServerPlayer player, SinclairState state) {
      if (state.moveTick != SinclairRules.HEX_HAT_TICK || state.hexPoint == null) {
         return;
      }

      HexHatEntity hat = SinclairEntities.HEX_HAT.get().create(player.level());
      if (hat != null) {
         hat.setup(player, state.hexPoint);
         player.level().addFreshEntity(hat);
      }

      state.hexCooldown = SinclairRules.HEX_COOLDOWN;
   }

   /** The top hat is off the head during the cast's middle (ticks 1-8). */
   public static boolean hatOff(SinclairState state) {
      return state.move == SinclairMove.HEX_CAST && state.moveTick >= 1 && state.moveTick <= 8;
   }
}
