package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.compat.spider.SpiderLine;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Venom's abilities (the six panel slots) and the body's passive life: the staged transformation and its reverse,
 * tendrils (surface grab and zip, yank, climbing a Spider's line), the lash combo, the shell, the frenzy, the
 * takeover's start, damage reactions, regeneration, hunger and wall clinging. Server only.
 */
public final class VenomSkills {
   private VenomSkills() {
   }

   public static void input(ServerPlayer player, VenomState state, VenomAbility ability) {
      state.idle = 0;
      if (state.possessing()) {
         if (ability == VenomAbility.TAKEOVER) {
            VenomPossession.endAllFor(player, VenomPossession.End.VOLUNTARY);
         }
         return;
      }
      if (state.running()) {
         VenomMove m = state.move;
         if (m.locks()) {
            return;
         }
         boolean toggle = ability == VenomAbility.SHELL && m == VenomMove.SHELL;
         if (!toggle && !m.cancellable(state.elapsed)) {
            return;
         }
      }
      if (state.cooldowns[ability.cooldown] > 0 && !(ability == VenomAbility.SHELL && state.shell)) {
         return;
      }
      switch (ability) {
         case SYMBIOSIS -> symbiosis(player, state);
         case TENDRIL -> tendril(player, state);
         case LASH -> lash(player, state);
         case SHELL -> shell(player, state);
         case RAGE -> rage(player, state);
         case TAKEOVER -> takeover(player, state);
      }
   }

   // --- the transformation -------------------------------------------------------------------------------------

   static void symbiosis(ServerPlayer player, VenomState state) {
      interrupt(player, state);
      state.cooldowns[VenomRules.CD_TRANSFORM] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_TRANSFORM];
      if (state.form) {
         state.start(VenomMove.REVERT);
         VenomSounds.play(player, VenomSounds.REVERT, 1.0F, VenomSounds.jitter(player, 1.0F));
      } else {
         state.start(VenomMove.TRANSFORM);
         VenomSounds.play(player, VenomSounds.TRANSFORM, 1.0F, VenomSounds.jitter(player, 1.0F));
         VenomFx.send(player, VenomFx.TRANSFORM, VenomTargets.center(player), null, 0.0F, 0);
         HeroSpeech.ability(player, VenomAbility.SYMBIOSIS.id());
      }
   }

   static void transformTick(ServerPlayer player, VenomState state) {
      int t = state.elapsed;
      if (t == VenomRules.STAGE_TORSO || t == VenomRules.STAGE_GROW || t == VenomRules.STAGE_FACE) {
         VenomFx.send(player, VenomFx.TRANSFORM, VenomTargets.center(player), null, VenomRules.stage(t), 0);
         VenomSounds.play(player, VenomSounds.SQUIRM, 0.7F, VenomSounds.jitter(player, 0.9F + 0.08F * VenomRules.stage(t)));
      }
      if (t == VenomRules.STAGE_GROW && !state.form) {
         state.form = true; // the proportions: the hitbox grows with the body (HeroBodyTracker)
         VenomHero.applyChassis(player);
      }
   }

   static void revertTick(ServerPlayer player, VenomState state) {
      if (state.elapsed == VenomRules.REVERT_TICKS - 10 && state.form) {
         state.form = false;
         endRage(player, state);
         VenomHero.removeChassis(player);
         VenomFx.send(player, VenomFx.TRANSFORM, VenomTargets.center(player), null, 0.5F, 0);
      }
   }

   // --- tendrils -----------------------------------------------------------------------------------------------

   static void tendril(ServerPlayer player, VenomState state) {
      interrupt(player, state);
      state.cooldowns[VenomRules.CD_TENDRIL] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_TENDRIL];
      state.tendrilSide = -state.tendrilSide;
      state.start(VenomMove.TENDRIL_SHOT);
      VenomSounds.play(player, VenomSounds.TENDRIL_SHOT, 0.9F, VenomSounds.jitter(player, 1.0F));
   }

   /** The tendril flies: the first of a body, a Spider's line or a block on the ray decides what it does. */
   static void tendrilFire(ServerPlayer player, VenomState state) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 far = eye.add(look.scale(VenomRules.TENDRIL_RANGE));
      BlockHitResult block = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
      double blockDist = end.distanceTo(eye);
      // a body on the ray
      List<LivingEntity> bodies = VenomTargets.along(player, eye, end, 0.45, e -> VenomTargets.mayHit(player, e) && e != player);
      LivingEntity body = bodies.isEmpty() ? null : bodies.get(0);
      double bodyDist = body == null ? Double.MAX_VALUE : VenomTargets.center(body).distanceTo(eye);
      // a Spider's web line across the ray
      ServerPlayer lineOwner = null;
      Vec3 lineAt = null;
      double lineDist = Double.MAX_VALUE;
      if (SpiderCompat.has(dev.baranhan.viltrumitecore.compat.spider.SpiderFeature.LINE_STATE)) {
         for (ServerPlayer p : player.serverLevel().players()) {
            if (p == player || p.distanceToSqr(player) > (VenomRules.TENDRIL_RANGE + 40) * (VenomRules.TENDRIL_RANGE + 40)) {
               continue;
            }
            Optional<SpiderLine> line = SpiderCompat.line(p);
            if (line.isEmpty()) {
               continue;
            }
            Vec3[] c = VenomSpider.closest(eye, end, line.get().hand(), line.get().anchor());
            if (c[0].distanceTo(c[1]) <= VenomRules.LINE_CUT_RADIUS && c[0].distanceTo(eye) < lineDist) {
               lineDist = c[0].distanceTo(eye);
               lineOwner = p;
               lineAt = c[1];
            }
         }
      }
      if (lineOwner != null && lineDist < bodyDist && lineDist < blockDist) {
         // the tendril wraps the Spider's line and Venom climbs it up to the anchor
         Vec3 anchor = SpiderCompat.line(lineOwner).map(SpiderLine::anchor).orElse(lineAt);
         state.webSpiderId = lineOwner.getId();
         state.tendrilEnd = lineAt;
         state.zipTarget = anchor;
         state.start(VenomMove.ZIP);
         VenomFx.send(player, VenomFx.WEB, lineAt, null, 0.6F, 0);
         VenomSounds.playAt(player, lineAt, VenomSounds.TENDRIL_STICK, 1.0F, VenomSounds.jitter(player, 1.1F));
         return;
      }
      if (body != null && bodyDist < blockDist) {
         state.tendrilEnd = VenomTargets.center(body);
         VenomFx.send(player, VenomFx.STICK, state.tendrilEnd, VenomTargets.shoulder(player, state.tendrilSide), 1.0F, 2, body.getId());
         VenomSounds.playAt(player, state.tendrilEnd, VenomSounds.TENDRIL_STICK, 1.0F, VenomSounds.jitter(player, 1.0F));
         if (!player.isShiftKeyDown() && VenomCombat.grabbable(body) && yank(player, state, body)) {
            HeroSpeech.say(player, VenomLines.PULL);
            return;
         }
         // a boss, a giant or a crouched Venom: he goes to them
         state.zipTarget = VenomTargets.center(body).subtract(VenomTargets.flat(look).scale(body.getBbWidth() * 0.5 + 1.0));
         state.pullId = body.getId();
         state.start(VenomMove.ZIP);
         return;
      }
      if (block.getType() == HitResult.Type.BLOCK) {
         state.tendrilEnd = end;
         Direction face = block.getDirection();
         state.zipTarget = end.add(face.getStepX() * 0.6, face.getStepY() * 0.2 - (face.getAxis().isHorizontal() ? 1.0 : 0.0), face.getStepZ() * 0.6);
         state.start(VenomMove.ZIP);
         VenomFx.send(player, VenomFx.STICK, end, VenomTargets.shoulder(player, state.tendrilSide), 0.7F, 0);
         VenomSounds.playAt(player, end, VenomSounds.TENDRIL_STICK, 0.8F, VenomSounds.jitter(player, 1.2F));
         return;
      }
      state.tendrilEnd = null; // nothing in reach: it snaps back
   }

   static boolean yank(ServerPlayer player, VenomState state, LivingEntity t) {
      UUID effect = UUID.randomUUID();
      if (!ControlManager.get(player.serverLevel()).tryAcquire(t, player.getUUID(), effect, ControlKind.PULL, player.level().getGameTime() + 40)) {
         return false;
      }
      if (t instanceof ServerPlayer spider && SpiderCompat.isSpider(spider) && SpiderCompat.line(spider).isPresent()) {
         SpiderCompat.cutLine(spider); // yanked off his own line
      }
      state.pullId = t.getId();
      state.pullEffect = effect;
      state.start(VenomMove.PULL);
      return true;
   }

   static void pullTick(ServerPlayer player, VenomState state) {
      Entity e = player.level().getEntity(state.pullId);
      if (!(e instanceof LivingEntity t) || !t.isAlive() || state.pullEffect == null
         || !state.pullEffect.equals(ControlManager.get(player.serverLevel()).effectIdOf(t))) {
         endPull(player, state, ReleaseReason.TARGET_LOST);
         state.endMove();
         return;
      }
      Vec3 to = player.getEyePosition().subtract(0, t.getBbHeight() * 0.5, 0).add(VenomTargets.flat(player.getLookAngle()).scale(1.6));
      Vec3 d = to.subtract(t.position());
      state.tendrilEnd = VenomTargets.center(t);
      if (d.length() < 1.6) {
         // arrived: the tendrils keep it (a hold), ready for a throw or a slam
         endPull(player, state, ReleaseReason.NORMAL_END);
         if (VenomCombat.acquire(player, state, t)) {
            state.start(VenomMove.HOLD);
            VenomSounds.playAt(player, VenomTargets.center(t), VenomSounds.GRAB, 0.9F, VenomSounds.jitter(player, 1.0F));
         } else {
            state.endMove();
         }
         return;
      }
      VenomTargets.place(t, t.position().add(d.normalize().scale(Math.min(d.length(), VenomRules.PULL_SPEED))));
      if (state.elapsed >= VenomRules.PULL_MAX_TICKS) {
         endPull(player, state, ReleaseReason.NORMAL_END);
         state.endMove();
      }
   }

   static void endPull(ServerPlayer player, VenomState state, ReleaseReason reason) {
      Entity e = state.pullId >= 0 ? player.level().getEntity(state.pullId) : null;
      if (e instanceof LivingEntity t && state.pullEffect != null) {
         ControlManager.get(player.serverLevel()).release(t.getUUID(), state.pullEffect, reason);
         if (t instanceof ServerPlayer victim) {
            victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
         }
      }
      state.pullId = -1;
      state.pullEffect = null;
      state.tendrilEnd = null;
   }

   static void zipTick(ServerPlayer player, VenomState state) {
      Vec3 target = state.zipTarget;
      if (state.webSpiderId >= 0) {
         // climbing a Spider's line: the anchor moves with his swing, the line may be gone
         Entity spider = player.level().getEntity(state.webSpiderId);
         Optional<SpiderLine> line = spider instanceof Player p ? SpiderCompat.line(p) : Optional.empty();
         if (line.isEmpty()) {
            state.webSpiderId = -1;
            finishZip(player, state, false);
            return;
         }
         target = line.get().anchor();
         state.tendrilEnd = line.get().at(0.5);
      } else if (state.pullId >= 0) {
         Entity t = player.level().getEntity(state.pullId);
         if (t == null || !t.isAlive()) {
            finishZip(player, state, false);
            return;
         }
         target = VenomTargets.center(t).subtract(VenomTargets.flat(t.position().subtract(player.position())).scale(t.getBbWidth() * 0.5 + 0.9));
         state.tendrilEnd = VenomTargets.center(t);
      }
      if (target == null) {
         finishZip(player, state, false);
         return;
      }
      Vec3 d = target.subtract(player.position());
      if (d.length() < 1.3 || state.elapsed >= VenomRules.ZIP_MAX_TICKS || state.elapsed > 3 && player.horizontalCollision && d.length() < 3.0) {
         finishZip(player, state, true);
         return;
      }
      Vec3 v = d.normalize().scale(Math.min(VenomRules.ZIP_SPEED, d.length()));
      player.setDeltaMovement(v);
      player.fallDistance = 0;
      player.hurtMarked = true;
      player.connection.send(new ClientboundSetEntityMotionPacket(player));
      if (state.elapsed % 5 == 1) {
         VenomSounds.play(player, VenomSounds.ZIP, 0.6F, VenomSounds.jitter(player, 1.0F));
      }
   }

   static void finishZip(ServerPlayer player, VenomState state, boolean arrived) {
      if (state.webSpiderId >= 0 && arrived) {
         Entity spider = player.level().getEntity(state.webSpiderId);
         if (spider instanceof ServerPlayer sp && SpiderCompat.cutLine(sp)) {
            // at the top of the climb the symbiote bites the line through: the Spider drops
            VenomFx.send(player, VenomFx.WEB, player.getEyePosition(), null, 1.0F, 0);
            VenomSounds.play(player, VenomSounds.WEB_TEAR, 1.0F, VenomSounds.jitter(player, 1.0F));
         }
      }
      if (arrived && state.pullId >= 0) {
         // arrived at a body (boss, giant): the next claw is already on the way
         state.comboIdle = 0;
      }
      state.webSpiderId = -1;
      state.pullId = -1;
      state.zipTarget = null;
      state.tendrilEnd = null;
      Vec3 v = player.getDeltaMovement();
      player.setDeltaMovement(v.scale(arrived ? 0.25 : 0.6).add(0, arrived ? 0.25 : 0.0, 0));
      player.hurtMarked = true;
      player.connection.send(new ClientboundSetEntityMotionPacket(player));
      if (arrived && player.horizontalCollision) {
         state.wallTicks = 10;
      }
      state.endMove();
   }

   // --- the lash combo -----------------------------------------------------------------------------------------

   static void lash(ServerPlayer player, VenomState state) {
      interrupt(player, state);
      int step = state.lashIdle <= VenomRules.COMBO_WINDOW + 6 ? state.lashStep + 1 : 0;
      if (step >= 3) {
         step = 0;
      }
      // a lash after two claws finishes the mixed combo with the slam
      if (step == 0 && state.comboIdle <= VenomRules.COMBO_WINDOW && state.comboStep >= 1) {
         step = 2;
      }
      state.lashStep = step;
      state.start(VenomMove.lash(step));
      if (step == 2) {
         state.cooldowns[VenomRules.CD_LASH] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_LASH];
      }
      VenomSounds.play(player, VenomSounds.LASH, 0.9F, VenomSounds.jitter(player, 1.0F - step * 0.08F));
   }

   static void lashContact(ServerPlayer player, VenomState state, VenomMove m) {
      state.lashIdle = 0;
      Vec3 feet = player.position();
      Vec3 look = VenomTargets.flat(player.getLookAngle());
      Vec3 chest = VenomTargets.center(player);
      float rage = state.rage ? 1.15F : 1.0F;
      switch (m) {
         case LASH_SWEEP -> {
            double r = VenomRules.LASH_SWEEP_RADIUS;
            for (int i = 0; i < 8; i++) {
               double a = i * Math.PI / 4;
               VenomSpider.cutLines(player, state, chest, chest.add(Math.cos(a) * r, 0, Math.sin(a) * r), VenomRules.LINE_CUT_RADIUS);
            }
            for (LivingEntity e : VenomTargets.around(player, chest, r, e -> VenomTargets.mayAutoHit(player, e) && state.hit.add(e.getId()))) {
               if (VenomTargets.strike(player, e, VenomRules.LASH_SWEEP_DAMAGE * rage)) {
                  VenomTargets.push(e, VenomTargets.outward(feet, e).scale(0.8).add(0, 0.25, 0));
                  state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_LASH);
               }
            }
            VenomFx.send(player, VenomFx.SWEEP, chest, VenomFx.radius(chest, r), 1.0F, 1);
         }
         case LASH_IMPALE -> {
            Vec3 eye = player.getEyePosition();
            Vec3 end = VenomTargets.clip(player, eye, eye.add(player.getLookAngle().scale(VenomRules.LASH_IMPALE_RANGE)));
            VenomSpider.cutLines(player, state, eye, end, VenomRules.LINE_CUT_RADIUS);
            for (LivingEntity e : VenomTargets.along(player, eye, end, 0.7, e -> VenomTargets.mayHit(player, e) && state.hit.add(e.getId()))) {
               if (VenomTargets.strike(player, e, VenomRules.LASH_IMPALE_DAMAGE * rage)) {
                  VenomTargets.push(e, player.getLookAngle().scale(0.5).add(0, 0.2, 0));
                  VenomFx.send(player, VenomFx.CLAW, VenomTargets.center(e), eye, 1.2F, 2, e.getId());
                  state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_LASH);
               }
            }
            state.tendrilEnd = end;
            VenomFx.send(player, VenomFx.STICK, end, VenomTargets.shoulder(player, 1), 1.0F, 0);
         }
         case LASH_SLAM -> {
            Vec3 at = feet.add(look.scale(2.5));
            double r = VenomRules.LASH_SLAM_RADIUS;
            for (LivingEntity e : VenomTargets.around(player, at, r, e -> VenomTargets.mayAutoHit(player, e) && state.hit.add(e.getId()))) {
               if (VenomTargets.strike(player, e, VenomRules.LASH_SLAM_DAMAGE * rage)) {
                  VenomTargets.push(e, new Vec3(0, 0.85, 0).add(VenomTargets.outward(at, e).scale(0.3)));
                  VenomTargets.stun(player, e, 10);
                  state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_LASH);
               }
            }
            VenomFx.send(player, VenomFx.SLAM, at, VenomFx.radius(at, r), 1.5F, 3);
            VenomSounds.playAt(player, at, VenomSounds.SLAM, 1.2F, VenomSounds.jitter(player, 0.8F));
         }
         default -> {
         }
      }
   }

   // --- shell, frenzy ------------------------------------------------------------------------------------------

   static void shell(ServerPlayer player, VenomState state) {
      if (state.shell) {
         endShell(player, state);
         return;
      }
      interrupt(player, state);
      state.shell = true;
      state.start(VenomMove.SHELL);
      VenomSounds.play(player, VenomSounds.SHELL, 1.0F, VenomSounds.jitter(player, 1.0F));
   }

   static void endShell(ServerPlayer player, VenomState state) {
      if (!state.shell) {
         return;
      }
      state.shell = false;
      state.cooldowns[VenomRules.CD_SHELL] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_SHELL];
      if (state.is(VenomMove.SHELL)) {
         state.endMove();
      }
      VenomSounds.play(player, VenomSounds.SHELL, 0.6F, VenomSounds.jitter(player, 1.3F));
   }

   static void rage(ServerPlayer player, VenomState state) {
      if (state.rage || state.hunger < VenomRules.RAGE_MIN) {
         VenomSounds.play(player, VenomSounds.REFUSE, 0.5F, 0.9F);
         return;
      }
      interrupt(player, state);
      state.start(VenomMove.RAGE);
      VenomSounds.play(player, VenomSounds.SQUIRM, 1.0F, 0.8F);
   }

   static void rageContact(ServerPlayer player, VenomState state) {
      state.rage = true;
      state.rageTicks = 0;
      HeroSpeech.ability(player, VenomAbility.RAGE.id());
      VenomSounds.play(player, VenomSounds.ROAR, 1.6F, VenomSounds.jitter(player, 0.9F));
      VenomFx.send(player, VenomFx.RECOIL, VenomTargets.center(player), null, 2.0F, 0);
      for (LivingEntity e : VenomTargets.around(player, player.position(), 8.0, e -> e instanceof Mob && VenomTargets.mayAutoHit(player, e))) {
         VenomTargets.push(e, VenomTargets.outward(player.position(), e).scale(0.6).add(0, 0.2, 0));
      }
   }

   static void endRage(ServerPlayer player, VenomState state) {
      if (!state.rage) {
         return;
      }
      state.rage = false;
      state.rageTicks = 0;
      state.cooldowns[VenomRules.CD_RAGE] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_RAGE];
   }

   // --- the takeover -------------------------------------------------------------------------------------------

   static void takeover(ServerPlayer player, VenomState state) {
      VenomPossession.Refusal refusal = VenomPossession.check(player, state);
      if (refusal != null) {
         VenomPossession.tellRefusal(player, refusal);
         VenomSounds.play(player, VenomSounds.REFUSE, 0.6F, 0.9F);
         return;
      }
      LivingEntity host = VenomPossession.pick(player, state);
      if (host == null) {
         VenomPossession.tellRefusal(player, VenomPossession.Refusal.NO_TARGET);
         VenomSounds.play(player, VenomSounds.REFUSE, 0.6F, 0.9F);
         return;
      }
      interrupt(player, state);
      if (!VenomPossession.engulf(player, state, host)) {
         VenomSounds.play(player, VenomSounds.REFUSE, 0.6F, 0.9F);
      }
   }

   // --- the body's reactions -----------------------------------------------------------------------------------

   /** A heavy blow splashes the symbiote open; it reforms (REFORM). Fire is VenomWeakness's business. */
   static void damageReaction(ServerPlayer player, VenomState state, DamageSource source, float amount) {
      if (amount < 7.0F || VenomTargets.fire(source)) {
         return;
      }
      if (state.move == null || state.move.cancellable(state.elapsed) && !state.move.unstoppable() && state.move != VenomMove.SHELL) {
         interrupt(player, state);
         state.start(VenomMove.REFORM);
      }
      VenomFx.send(player, VenomFx.RECOIL, VenomTargets.center(player), null, 0.6F, 0);
      VenomSounds.play(player, VenomSounds.REFORM, 0.8F, VenomSounds.jitter(player, 1.0F));
   }

   /** May he crawl walls now: the form, nothing heavy going on. */
   public static boolean mayCrawl(VenomState state) {
      return state.form && !state.shell && !state.possessing() && !state.is(VenomMove.RECOIL) && !state.is(VenomMove.TRANSFORM)
         && !state.is(VenomMove.REVERT);
   }

   static boolean touchingWall(ServerPlayer player) {
      if (player.horizontalCollision) {
         return true;
      }
      BlockPos feet = player.blockPosition();
      for (Direction d : Direction.Plane.HORIZONTAL) {
         for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = feet.above(dy).relative(d);
            if (player.level().getBlockState(p).isFaceSturdy(player.level(), p, d.getOpposite())) {
               Vec3 c = Vec3.atCenterOf(p);
               double gap = Math.abs(d.getAxis().choose(c.x, 0, c.z) - d.getAxis().choose(player.getX(), 0, player.getZ())) - 0.5;
               if (gap <= player.getBbWidth() * 0.5 + 0.08) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   // --- per tick -----------------------------------------------------------------------------------------------

   static void tick(ServerPlayer player, VenomState state) {
      if (state.wallTicks > 0) {
         state.wallTicks--;
      }
      if (mayCrawl(state) && !player.onGround() && touchingWall(player)) {
         state.wallTicks = 4;
         player.fallDistance = 0;
      }
      // hunger
      if (state.idle >= 200 && state.tickCount % 10 == 0 && !state.rage) {
         state.hunger = VenomRules.feed(state.hunger, -VenomRules.HUNGER_DECAY_IDLE);
      }
      if (state.rage) {
         state.rageTicks++;
         state.hunger = VenomRules.feed(state.hunger, -VenomRules.RAGE_DRAIN);
         if (state.rageTicks >= VenomRules.RAGE_MAX_TICKS || state.hunger <= 0 || !state.form) {
            endRage(player, state);
         }
      }
      if (state.shell) {
         state.hunger = VenomRules.feed(state.hunger, -VenomRules.SHELL_DRAIN);
         if (!state.is(VenomMove.SHELL) || state.elapsed >= VenomRules.SHELL_MAX_TICKS || !state.form) {
            endShell(player, state);
         }
      }
      // regeneration: the symbiote knits the host, never while it burns
      if (state.form && state.sinceHurt >= VenomRules.REGEN_DELAY && state.fireBlock <= 0 && player.getHealth() < player.getMaxHealth()
         && !state.possessing()) {
         player.heal(state.rage ? VenomRules.RAGE_REGEN_PER_TICK : VenomRules.REGEN_PER_TICK);
      }
      if (state.form && player.isOnFire()) {
         VenomWeakness.burnt(player, state);
      }
   }

   static void during(ServerPlayer player, VenomState state, VenomMove m) {
      switch (m) {
         case TRANSFORM -> transformTick(player, state);
         case REVERT -> revertTick(player, state);
         case ZIP -> zipTick(player, state);
         case PULL -> pullTick(player, state);
         case ENGULF -> VenomPossession.engulfTick(player, state);
         default -> {
         }
      }
   }

   static void contact(ServerPlayer player, VenomState state, VenomMove m) {
      switch (m) {
         case TENDRIL_SHOT -> tendrilFire(player, state);
         case LASH_SWEEP, LASH_IMPALE, LASH_SLAM -> lashContact(player, state, m);
         case RAGE -> rageContact(player, state);
         case ENGULF -> VenomPossession.engulfContact(player, state);
         case TEAR_WEB -> VenomSpider.tearContact(player, state);
         case CLASH -> VenomSpider.clashContact(player, state);
         case TRANSFORM -> {
            VenomSounds.play(player, VenomSounds.ROAR, 1.4F, VenomSounds.jitter(player, 1.0F));
            VenomFx.send(player, VenomFx.TRANSFORM, VenomTargets.center(player), null, 4.0F, 0);
         }
         default -> {
         }
      }
   }

   static void finish(ServerPlayer player, VenomState state, VenomMove m) {
      switch (m) {
         case TRANSFORM -> {
            if (!state.form) {
               state.form = true;
               VenomHero.applyChassis(player);
            }
         }
         case REVERT -> {
            if (state.form) {
               state.form = false;
               VenomHero.removeChassis(player);
            }
         }
         case TENDRIL_SHOT -> state.tendrilEnd = state.tendrilEnd != null && state.move == VenomMove.TENDRIL_SHOT ? null : state.tendrilEnd;
         case LASH_IMPALE -> state.tendrilEnd = null;
         case PULL -> endPull(player, state, ReleaseReason.NORMAL_END);
         case LASH_SWEEP, LASH_SLAM -> state.lashIdle = 0;
         case ENGULF -> VenomPossession.engulfFailed(player, state);
         default -> {
         }
      }
      if (state.move == m) {
         state.endMove();
      }
   }

   /** Ends the running move cleanly (a new input, a stun, a recoil): lets go of whatever it held. */
   static void interrupt(ServerPlayer player, VenomState state) {
      VenomMove m = state.move;
      if (m == null) {
         return;
      }
      switch (m) {
         case PULL -> endPull(player, state, ReleaseReason.NORMAL_END);
         case ZIP -> {
            state.webSpiderId = -1;
            state.pullId = -1;
            state.zipTarget = null;
            state.tendrilEnd = null;
         }
         case HOLD, THROW, SLAM -> VenomCombat.release(player, state, ReleaseReason.NORMAL_END, null);
         case SHELL -> endShell(player, state);
         case ENGULF -> VenomPossession.engulfFailed(player, state);
         case TRANSFORM -> finish(player, state, m);
         case REVERT -> finish(player, state, m);
         default -> {
         }
      }
      state.tendrilEnd = null;
      if (state.move == m) {
         state.endMove();
      }
   }

   static void cleanup(ServerPlayer player, VenomState state) {
      interrupt(player, state);
      VenomCombat.release(player, state, ReleaseReason.NORMAL_END, null);
      endPull(player, state, ReleaseReason.NORMAL_END);
      endShell(player, state);
      state.rage = false;
      state.rageTicks = 0;
      state.webSpiderId = -1;
      state.wallTicks = 0;
      state.endMove();
   }

   @Nullable
   static LivingEntity living(ServerPlayer player, int id) {
      return id >= 0 && player.level().getEntity(id) instanceof LivingEntity l ? l : null;
   }
}
