package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeArc;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeHits;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeImpact;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Ordinary drawn-Reid strokes on LMB: diagonal cut → rising cut → wide sweep → overhead
 * finisher (MeleeMoves.REID), crouch = low sweep. No carve, input lock or cinematic kill.
 * Each tick of the contact window sweeps the same view-relative path the clip and the
 * client trail show, several bodies per stroke, never through walls or behind him.
 */
public final class ReidMelee implements AbilityHandler {
   /** Pure: the press starts the next stroke; returns its move index. */
   public static int press(ReinhardState state, boolean crouching) {
      int last = state.meleeAbility == ReinhardAbility.REID_STRIKE ? state.meleeStep : -1;
      int move = MeleeMoves.reidMove(last, state.meleeIdle, crouching);
      state.meleeAbility = ReinhardAbility.REID_STRIKE;
      state.meleeStep = move;
      state.meleeIdle = 0;
      state.meleeQueued = false;
      state.meleeHit.clear();
      state.action.start(ReinhardAbility.REID_STRIKE, MeleeMoves.reid(move).ticks(), -1, move);
      return move;
   }

   @Override
   public boolean tryStart(ServerPlayer p, ReinhardState s) {
      if (!s.reid.drawn() || s.action.running() || s.busy()) {
         return false;
      }

      press(s, p.isShiftKeyDown());
      return true;
   }

   @Override
   public void tick(ServerPlayer p, ReinhardState s) {
      if (MeleeChain.tick(s, ReinhardAbility.REID_STRIKE) && s.reid.drawn() && !s.busy()) {
         press(s, p.isShiftKeyDown());
      }

      ActionTimeline action = s.action;
      if (!action.is(ReinhardAbility.REID_STRIKE)) {
         return;
      }

      MeleeMoves.Move move = MeleeMoves.reid(action.phase());
      MeleeArc.Swing swing = move.swing();
      int tick = action.elapsed();
      if (tick == swing.contactStart() - 2) {
         // The air is cut before the blade arrives: whoosh leads the contact by two ticks.
         ReinhardSounds.play(p, ReinhardSounds.REID_SWING.get(), move.heavy() ? 1.0F : 0.8F, move.heavy() ? 0.82F : 0.96F + 0.04F * action.phase());
      }

      List<MeleeHits.Hit> hits = MeleeHits.sweep(p, swing, tick, s.meleeHit);
      for (MeleeHits.Hit hit : hits) {
         if (Strikes.hit(p, hit.target(), move.damage())) {
            Strikes.knockback(hit.target(), MeleeImpact.push(p, swing), move.knockback());
         }
      }

      MeleeImpact.land(p, move, hits, move.heavy() ? ReinhardSounds.REID_HEAVY.get() : ReinhardSounds.REID_HIT.get(), move.heavy() ? 1.2F : 0.95F, 1.0F);
      if (tick == (swing.contactStart() + swing.contactEnd()) / 2 && s.meleeHit.isEmpty()) {
         MeleeImpact.glance(p, swing, ReinhardSounds.REID_HIT.get());
      }

      if (move.heavy() && tick == swing.contactEnd()) {
         finisherShock(p, swing);
      }
   }

   /** The overhead finisher ends in the ground in front of him: a small dust ring, no block damage. */
   private static void finisherShock(ServerPlayer p, MeleeArc.Swing swing) {
      double[] end = swing.at(1.0);
      double[] d = MeleeArc.direction(MeleeArc.basis(p.getYRot(), p.getXRot()), end[0], end[1]);
      Vec3 eye = p.getEyePosition();
      BlockHitResult block = p.level().clip(new ClipContext(eye, eye.add(d[0] * swing.reach(), d[1] * swing.reach() - 1.0, d[2] * swing.reach()),
         ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
      if (block.getType() == HitResult.Type.BLOCK) {
         HeroFx.shockwave(p, block.getLocation(), 0.2F, p.level().getBlockState(block.getBlockPos()), 2.5F);
      }
   }

   @Override
   public void cancel(ServerPlayer p, ReinhardState s, CleanupReason reason) {
      MeleeChain.reset(s);
   }
}
