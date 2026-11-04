package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Venom's claws (LMB), the tendril grab (RMB) with hold / throw / slam, and the move timeline. Server only; every hit
 * is a server hitbox test (arc, capsule or radius) with the shared target rules.
 */
public final class VenomCombat {
   private VenomCombat() {
   }

   // --- LMB: claws, or the throw of a held body ----------------------------------------------------------------

   public static void primary(ServerPlayer player, VenomState state) {
      state.idle = 0;
      if (!state.form) {
         return;
      }
      if (state.holding()) {
         if (!state.is(VenomMove.THROW) && !state.is(VenomMove.SLAM)) {
            state.start(VenomMove.THROW);
            VenomSounds.play(player, VenomSounds.THROW, 0.9F, VenomSounds.jitter(player, 1.0F));
         }
         return;
      }
      if (state.running()) {
         VenomMove m = state.move;
         if (m.locks() || !m.cancellable(state.elapsed)) {
            if (m.isClaw() || m.isLash()) {
               state.primaryQueued = true;
            }
            return;
         }
         VenomSkills.interrupt(player, state);
      }
      int step = state.comboIdle <= VenomRules.COMBO_WINDOW ? (state.comboStep + 1) % VenomRules.COMBO_LENGTH : 0;
      // a claw right after a lash keeps the mixed combo going: the third hit is always the heavy one
      if (state.lashIdle <= VenomRules.COMBO_WINDOW && step == 0) {
         step = 1;
      }
      state.comboStep = step;
      state.start(VenomMove.claw(step));
      VenomSounds.play(player, VenomSounds.CLAW, 0.8F, VenomSounds.jitter(player, step == 2 ? 0.8F : 1.05F));
   }

   static void clawContact(ServerPlayer player, VenomState state) {
      int step = Math.max(0, state.comboStep);
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double reach = VenomRules.CLAW_REACH + (state.rage ? 0.5 : 0.0);
      state.comboIdle = 0;
      // the claws cut a Spider's line in their path, whoever hangs from it
      VenomSpider.cutLines(player, state, eye, eye.add(look.scale(reach)), VenomRules.LINE_CUT_RADIUS + 0.4);
      List<LivingEntity> targets = VenomTargets.arc(player, eye, look, reach, VenomRules.CLAW_ARC_DEG,
         e -> VenomTargets.mayHit(player, e) && VenomTargets.visible(player.level(), eye, VenomTargets.center(e)));
      if (targets.isEmpty()) {
         return;
      }
      LivingEntity aimed = VenomTargets.aimed(targets, eye, look, 40.0);
      if (aimed != null && SpiderCompat.isSpider(aimed instanceof Player p ? p : null) && VenomSpider.clashes(player, aimed)) {
         VenomSpider.clash(player, state, (Player)aimed);
         return;
      }
      float damage = VenomRules.CLAW_DAMAGE[step];
      boolean finisher = step == VenomRules.COMBO_LENGTH - 1;
      int landed = 0;
      for (LivingEntity t : targets) {
         if (!state.hit.add(t.getId()) || landed >= (finisher ? 4 : 2)) {
            continue;
         }
         if (VenomTargets.strike(player, t, damage)) {
            landed++;
            Vec3 out = VenomTargets.outward(player.position(), t);
            VenomTargets.push(t, out.scale(finisher ? 0.9 : 0.35).add(0, finisher ? 0.35 : 0.12, 0));
            Vec3 at = VenomTargets.center(t).subtract(out.scale(t.getBbWidth() * 0.5));
            VenomFx.send(player, VenomFx.CLAW, at, VenomTargets.hand(player), finisher ? 1.4F : 1.0F, finisher ? 3 : 1, t.getId());
            VenomSounds.playAt(player, at, VenomSounds.CLAW_HIT, 0.9F, VenomSounds.jitter(player, finisher ? 0.8F : 1.0F));
            state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_CLAW);
         }
      }
   }

   // --- RMB: grab a body with the tendrils; with a body held: slam it ------------------------------------------

   public static void secondary(ServerPlayer player, VenomState state) {
      state.idle = 0;
      if (!state.form) {
         return;
      }
      if (state.holding()) {
         if (state.is(VenomMove.HOLD)) {
            state.start(VenomMove.SLAM);
            VenomSounds.play(player, VenomSounds.LASH, 0.8F, VenomSounds.jitter(player, 0.8F));
         }
         return;
      }
      if (state.cooldowns[VenomRules.CD_GRAB] > 0) {
         return;
      }
      if (state.running()) {
         if (state.move.locks() || !state.move.cancellable(state.elapsed)) {
            return;
         }
         VenomSkills.interrupt(player, state);
      }
      state.cooldowns[VenomRules.CD_GRAB] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_GRAB];
      state.start(VenomMove.GRAB);
      VenomSounds.play(player, VenomSounds.TENDRIL_SHOT, 0.8F, VenomSounds.jitter(player, 1.15F));
   }

   /** The body a grab reaches: aimed first, else the nearest one in front within the grab range. */
   @Nullable
   static LivingEntity grabTarget(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      List<LivingEntity> near = VenomTargets.arc(player, eye, look, VenomRules.GRAB_RANGE, 70.0,
         e -> VenomTargets.mayHit(player, e) && grabbable(e) && VenomTargets.visible(player.level(), eye, VenomTargets.center(e)));
      LivingEntity aimed = VenomTargets.aimed(near, eye, look, 25.0);
      return aimed != null ? aimed : near.isEmpty() ? null : near.get(0);
   }

   static boolean grabbable(LivingEntity t) {
      return !VenomTargets.isBoss(t) && t.getBbWidth() <= 2.2F && t.getBbHeight() <= 3.2F
         && HeroRegistry.allowsExternalControl(t, ControlKind.PULL) && !HeroDamage.isAnchored(t) && !t.isPassenger() && !t.isVehicle()
         && !VenomPossession.involved(t);
   }

   static void grabContact(ServerPlayer player, VenomState state) {
      LivingEntity t = grabTarget(player);
      if (t == null) {
         // nothing within reach: a Spider's line in the path is caught instead (the swing ends, he drops)
         Vec3 eye = player.getEyePosition();
         if (VenomSpider.cutLines(player, state, eye, eye.add(player.getLookAngle().scale(VenomRules.GRAB_RANGE)), VenomRules.LINE_CUT_RADIUS) == 0) {
            state.start(VenomMove.GRAB_MISS);
         }
         return;
      }
      if (!acquire(player, state, t)) {
         state.start(VenomMove.GRAB_MISS);
         return;
      }
      // the special grab: a Spider snatched off his own line loses it
      if (t instanceof ServerPlayer spider && SpiderCompat.isSpider(spider) && SpiderCompat.line(spider).isPresent()) {
         SpiderCompat.cutLine(spider);
         VenomFx.send(player, VenomFx.WEB, VenomTargets.center(spider), null, 1.0F, 2, spider.getId());
         VenomSounds.playAt(player, VenomTargets.center(spider), VenomSounds.WEB_TEAR, 1.0F, VenomSounds.jitter(player, 1.0F));
      }
      state.start(VenomMove.HOLD);
      VenomFx.send(player, VenomFx.STICK, VenomTargets.center(t), VenomTargets.shoulder(player, 1), 1.0F, 2, t.getId());
      VenomSounds.playAt(player, VenomTargets.center(t), VenomSounds.GRAB, 1.0F, VenomSounds.jitter(player, 1.0F));
      VenomTargets.strike(player, t, 2.0F);
   }

   static boolean acquire(ServerPlayer player, VenomState state, LivingEntity t) {
      UUID effect = UUID.randomUUID();
      if (!ControlManager.get(player.serverLevel()).tryAcquire(t, player.getUUID(), effect, ControlKind.PULL,
         player.level().getGameTime() + VenomRules.HOLD_MAX_TICKS + 40)) {
         return false;
      }
      state.heldId = t.getId();
      state.heldUuid = t.getUUID();
      state.heldEffect = effect;
      state.holdTicks = 0;
      return true;
   }

   @Nullable
   static LivingEntity held(ServerPlayer player, VenomState state) {
      if (!state.holding()) {
         return null;
      }
      Entity e = player.level().getEntity(state.heldId);
      if (!(e instanceof LivingEntity t) || !t.isAlive() || t.level() != player.level()) {
         return null;
      }
      if (state.heldEffect != null && !state.heldEffect.equals(ControlManager.get(player.serverLevel()).effectIdOf(t))) {
         return null;
      }
      return t;
   }

   /** Where a held body hangs: in front of the chest, lifted off the ground. Shared by both sides. */
   public static Vec3 holdPoint(Vec3 feet, float yaw, float holderHeight, float targetHeight) {
      Vec3 flat = Vec3.directionFromRotation(0, yaw);
      return feet.add(flat.scale(1.7)).add(0, holderHeight * 0.55 - targetHeight * 0.4, 0);
   }

   static void holdTick(ServerPlayer player, VenomState state) {
      if (!state.holding()) {
         return;
      }
      LivingEntity t = held(player, state);
      state.holdTicks++;
      if (t == null || t.distanceToSqr(player) > 9.0 * 9.0 || !state.form) {
         release(player, state, ReleaseReason.TARGET_LOST, null);
         return;
      }
      if (state.is(VenomMove.HOLD) || state.is(VenomMove.THROW) && state.elapsed < VenomMove.THROW.contact
         || state.is(VenomMove.SLAM) && state.elapsed < VenomMove.SLAM.contact) {
         Vec3 at = holdPoint(player.position(), player.getYRot(), player.getBbHeight(), t.getBbHeight());
         if (state.is(VenomMove.SLAM)) {
            float k = state.elapsed / (float)VenomMove.SLAM.contact;
            at = at.add(0, 1.2 * Math.sin(k * Math.PI) , 0); // lifted high, then down
         }
         VenomTargets.place(t, at);
         t.fallDistance = 0;
         ControlManager.get(player.serverLevel()).refresh(t.getUUID(), player.getUUID(), state.heldEffect, ControlKind.PULL,
            player.level().getGameTime() + 40);
         if (state.holdTicks % 20 == 0) {
            VenomTargets.strike(player, t, 1.0F); // the tendrils squeeze
         }
      }
      if (state.is(VenomMove.HOLD) && state.holdTicks >= VenomRules.HOLD_MAX_TICKS) {
         release(player, state, ReleaseReason.NORMAL_END, Vec3.ZERO);
         state.endMove();
      }
   }

   static void throwContact(ServerPlayer player, VenomState state) {
      LivingEntity t = held(player, state);
      if (t == null) {
         release(player, state, ReleaseReason.TARGET_LOST, null);
         return;
      }
      Vec3 dir = player.getLookAngle();
      Vec3 velocity = dir.scale(VenomRules.THROW_SPEED).add(0, 0.3, 0);
      release(player, state, ReleaseReason.NORMAL_END, velocity);
      VenomThrows.track(player, t, VenomRules.THROW_IMPACT_DAMAGE);
      VenomSounds.playAt(player, VenomTargets.center(t), VenomSounds.THROW, 1.0F, VenomSounds.jitter(player, 0.85F));
   }

   static void slamContact(ServerPlayer player, VenomState state) {
      LivingEntity t = held(player, state);
      if (t == null) {
         release(player, state, ReleaseReason.TARGET_LOST, null);
         return;
      }
      Vec3 ground = new Vec3(t.getX(), Math.floor(player.getY()), t.getZ());
      Vec3 down = VenomTargets.clip(player, VenomTargets.center(t), VenomTargets.center(t).add(0, -6, 0));
      VenomTargets.place(t, new Vec3(t.getX(), Math.max(down.y, ground.y - 4), t.getZ()));
      release(player, state, ReleaseReason.NORMAL_END, new Vec3(0, -0.4, 0));
      VenomTargets.strike(player, t, VenomRules.SLAM_DAMAGE);
      VenomTargets.stun(player, t, 12);
      Vec3 at = t.position();
      VenomFx.send(player, VenomFx.SLAM, at, VenomFx.radius(at, 3.0), 1.3F, 4, t.getId());
      VenomSounds.playAt(player, at, VenomSounds.SLAM, 1.2F, VenomSounds.jitter(player, 0.85F));
      // the blast layer is cosmetic: played as Venom's own sound so the slam cannot stun its caster
      VenomWeakness.playOwn(player, at, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.4F);
      for (LivingEntity e : VenomTargets.around(player, at, 2.8, e -> e != t && VenomTargets.mayAutoHit(player, e))) {
         VenomTargets.strike(player, e, VenomRules.SLAM_DAMAGE * 0.4F);
         VenomTargets.push(e, VenomTargets.outward(at, e).scale(0.6).add(0, 0.3, 0));
      }
      state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_CLAW * 2);
   }

   /** Lets a held body go once (the control is released), optionally with a velocity. */
   static void release(ServerPlayer player, VenomState state, ReleaseReason reason, @Nullable Vec3 velocity) {
      if (!state.holding()) {
         return;
      }
      Entity e = player.level().getEntity(state.heldId);
      if (state.heldUuid != null && state.heldEffect != null) {
         ControlManager.get(player.serverLevel()).release(state.heldUuid, state.heldEffect, reason);
      }
      if (e instanceof LivingEntity t && t.isAlive()) {
         if (velocity != null) {
            VenomTargets.push(t, velocity);
         }
         if (t instanceof ServerPlayer victim) {
            victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
            if (velocity != null) {
               victim.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(victim));
            }
         }
      }
      state.heldId = -1;
      state.heldUuid = null;
      state.heldEffect = null;
      state.holdTicks = 0;
   }

   // --- timeline -------------------------------------------------------------------------------------------------

   static void tick(ServerPlayer player, VenomState state) {
      holdTick(player, state);
      VenomMove m = state.move;
      if (m == null) {
         return;
      }
      if (HeroDamage.isAnchored(player) && !m.unstoppable()) {
         VenomSkills.interrupt(player, state);
         return;
      }
      state.elapsed++;
      if (state.elapsed == m.contact) {
         contact(player, state, m);
      }
      if (state.move != m) {
         return; // the contact chained into another move
      }
      VenomSkills.during(player, state, m);
      if (state.move != m) {
         return;
      }
      if (state.takeQueuedPrimary()) {
         state.endMove();
         primary(player, state);
         return;
      }
      if (!m.loops() && !m.selfEnding() && state.elapsed >= m.length) {
         VenomSkills.finish(player, state, m);
      }
   }

   static void contact(ServerPlayer player, VenomState state, VenomMove m) {
      switch (m) {
         case CLAW_1, CLAW_2, CLAW_3 -> clawContact(player, state);
         case GRAB -> grabContact(player, state);
         case THROW -> throwContact(player, state);
         case SLAM -> slamContact(player, state);
         default -> VenomSkills.contact(player, state, m);
      }
   }
}
