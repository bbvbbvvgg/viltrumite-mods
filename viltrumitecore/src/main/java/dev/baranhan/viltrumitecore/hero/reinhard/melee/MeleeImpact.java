package dev.baranhan.viltrumitecore.hero.reinhard.melee;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The feel of a landed ordinary strike: one flash, one hit-stop and one sound per tick, never per target. */
public final class MeleeImpact {
   private MeleeImpact() {
   }

   /** Knockback direction: along the look, bent toward the travel of the blade. */
   public static Vec3 push(ServerPlayer player, MeleeArc.Swing swing) {
      double[][] basis = MeleeArc.basis(player.getYRot(), player.getXRot());
      Vec3 look = player.getLookAngle();
      double side = swing.side() * 0.6;
      return look.add(basis[1][0] * side, 0.0, basis[1][2] * side);
   }

   /** FX and sound of the bodies hit on one tick (multi-target sweeps never stack flashes or shakes). */
   public static void land(ServerPlayer player, MeleeMoves.Move move, List<MeleeHits.Hit> hits, SoundEvent sound, float volume, float pitch) {
      if (hits.isEmpty()) {
         return;
      }

      Vec3 at = hits.get(0).at();
      HeroFx.flash(player, at, move.heavy() ? 0.62F : 0.3F);
      if (move.hitStop() > 0) {
         int[] ids = new int[hits.size() + 1];
         ids[0] = player.getId();
         for (int i = 0; i < hits.size(); i++) {
            ids[i + 1] = hits.get(i).target().getId();
         }

         HeroFx.hitStop(player, at, move.hitStop(), ids);
      }

      ReinhardSounds.playAt(player, at, sound, volume, pitch * (0.94F + player.getRandom().nextFloat() * 0.12F));
   }

   /** Mid-swing with nobody hit: a blade or fist that meets a wall within reach throws a few sparks. */
   public static void glance(ServerPlayer player, MeleeArc.Swing swing, SoundEvent sound) {
      double[][] basis = MeleeArc.basis(player.getYRot(), player.getXRot());
      double[] mid = swing.at(0.5);
      double[] d = MeleeArc.direction(basis, mid[0], mid[1]);
      Vec3 eye = player.getEyePosition();
      BlockHitResult block = player.level().clip(new ClipContext(eye, eye.add(d[0] * swing.reach(), d[1] * swing.reach(), d[2] * swing.reach()),
         ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.BLOCK) {
         return;
      }

      Vec3 at = block.getLocation();
      player.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.25);
      ReinhardSounds.playAt(player, at, sound, 0.35F, 1.55F);
   }
}
