package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Hell Chain (LMB / RMB) on foot and on the bike, the chain on a body (hook, yank, whirl swing, tow) and the move
 * timeline of the Ghost Rider. Server only.
 */
public final class GhostRiderCombat {
   private GhostRiderCombat() {
   }

   // --- LMB: the lash ----------------------------------------------------------------------------------------

   public static void primary(ServerPlayer player, GhostRiderState state) {
      state.idle = 0;
      if (!state.rider) {
         return;
      }
      if (state.towing) {
         releaseChain(player, state, ReleaseReason.NORMAL_END, true);
         return;
      }
      if (state.cooldowns[GhostRiderRules.CD_LASH] > 0) {
         return;
      }
      HellCycleEntity bike = GhostRiderSkills.ridden(player);
      if (state.running()) {
         GhostRiderMove m = state.move;
         if (m.locks() || !m.cancellable(state.elapsed)) {
            return;
         }
         state.endMove();
      }
      if (bike != null) {
         state.start(GhostRiderMove.BIKE_LASH);
         state.cooldowns[GhostRiderRules.CD_LASH] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_LASH];
      } else {
         int step = state.comboIdle <= GhostRiderRules.COMBO_WINDOW ? (state.comboStep + 1) % GhostRiderRules.COMBO_LENGTH : 0;
         state.comboStep = step;
         state.start(GhostRiderMove.lash(step));
      }
      state.moveDir = player.getLookAngle();
      GhostRiderSounds.play(player, GhostRiderSounds.CHAIN_LASH, 0.9F, GhostRiderSounds.jitter(player, state.comboStep == 2 ? 0.85F : 1.0F));
   }

   static void lashContact(ServerPlayer player, GhostRiderState state, boolean bike) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double reach = bike ? GhostRiderRules.BIKE_LASH_REACH : GhostRiderRules.LASH_REACH;
      double arc = bike ? GhostRiderRules.BIKE_LASH_ARC : GhostRiderRules.LASH_ARC;
      int step = bike ? 0 : Math.max(0, state.comboStep);
      float damage = bike ? GhostRiderRules.BIKE_LASH_DAMAGE : GhostRiderRules.LASH_DAMAGE[step];
      int burn = bike ? GhostRiderRules.BIKE_LASH_BURN_TICKS : GhostRiderRules.LASH_BURN_TICKS;
      boolean finisher = !bike && step == GhostRiderRules.COMBO_LENGTH - 1;
      List<LivingEntity> targets = GhostRiderTargets.arc(player, eye, look, reach, arc,
         e -> GhostRiderTargets.mayHit(player, e) && GhostRiderTargets.visible(player.level(), eye, GhostRiderTargets.center(e)));
      int hits = 0;
      for (LivingEntity t : targets) {
         if (hits >= (bike ? 4 : GhostRiderRules.LASH_MAX_TARGETS) || !state.hit.add(t.getId())) {
            continue;
         }
         hits++;
         if (GhostRiderTargets.strike(player, t, damage)) {
            Hellfire.ignite(player, t, burn);
            Vec3 out = GhostRiderTargets.outward(player.position(), t);
            GhostRiderTargets.push(t, finisher ? out.scale(0.85).add(0, 0.45, 0) : out.scale(0.35).add(0, 0.18, 0));
            GhostRiderFx.send(player, GhostRiderFx.LASH, GhostRiderTargets.center(t), GhostRiderTargets.hand(player), finisher ? 1.4F : 1.0F, null,
               finisher ? 3 : 1, t.getId());
            GhostRiderSounds.playAt(player, GhostRiderTargets.center(t), GhostRiderSounds.CHAIN_HIT, 1.0F, GhostRiderSounds.jitter(player, 1.0F));
         }
      }
      if (finisher && !targets.isEmpty()) {
         Vec3 tip = GhostRiderTargets.center(targets.get(0));
         for (LivingEntity t : GhostRiderTargets.around(player, tip, GhostRiderRules.LASH_FINISHER_RADIUS,
            e -> GhostRiderTargets.mayAutoHit(player, e) && !state.hit.contains(e.getId()))) {
            state.hit.add(t.getId());
            if (GhostRiderTargets.strike(player, t, GhostRiderRules.LASH_DAMAGE[0] * 0.6F)) {
               Hellfire.ignite(player, t, GhostRiderRules.LASH_BURN_TICKS);
               GhostRiderTargets.push(t, GhostRiderTargets.outward(tip, t).scale(0.6).add(0, 0.3, 0));
            }
         }
      }
      state.comboIdle = 0;
   }

   // --- RMB: the hook ----------------------------------------------------------------------------------------

   public static void secondary(ServerPlayer player, GhostRiderState state) {
      state.idle = 0;
      if (!state.rider) {
         return;
      }
      if (state.chained()) {
         releaseChain(player, state, ReleaseReason.NORMAL_END, state.towing);
         return;
      }
      if (state.cooldowns[GhostRiderRules.CD_GRAB] > 0) {
         GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.5F, 1.0F);
         return;
      }
      if (state.running()) {
         GhostRiderMove m = state.move;
         if (m.locks() || !m.cancellable(state.elapsed)) {
            return;
         }
         state.endMove();
      }
      state.start(GhostRiderSkills.ridden(player) != null ? GhostRiderMove.BIKE_HOOK : GhostRiderMove.HOOK);
      state.moveDir = player.getLookAngle();
      GhostRiderSounds.play(player, GhostRiderSounds.CHAIN_THROW, 1.0F, GhostRiderSounds.jitter(player, 1.0F));
   }

   /** The first body on the aim ray, up to the hook range (blocks stop it). */
   @Nullable
   static LivingEntity aimed(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = GhostRiderTargets.clip(player, eye, eye.add(player.getLookAngle().scale(range)));
      AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
         e -> e instanceof LivingEntity l && GhostRiderTargets.mayHit(player, l) && e.getVehicle() != player && player.getVehicle() != e, 0.45F);
      return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
   }

   static void hookContact(ServerPlayer player, GhostRiderState state, boolean bike) {
      LivingEntity t = aimed(player, GhostRiderRules.HOOK_RANGE);
      if (t == null) {
         state.start(GhostRiderMove.HOOK_MISS);
         state.cooldowns[GhostRiderRules.CD_GRAB] = 20;
         return;
      }
      state.cooldowns[GhostRiderRules.CD_GRAB] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_GRAB];
      if (!GhostRiderTargets.strike(player, t, GhostRiderRules.HOOK_DAMAGE)) {
         state.start(GhostRiderMove.HOOK_MISS);
         return;
      }
      Hellfire.ignite(player, t, GhostRiderRules.LASH_BURN_TICKS);
      GhostRiderFx.send(player, GhostRiderFx.HOOK, GhostRiderTargets.center(t), GhostRiderTargets.hand(player), 1.0F, null, 2, t.getId());
      GhostRiderSounds.playAt(player, GhostRiderTargets.center(t), GhostRiderSounds.CHAIN_HIT, 1.0F, 0.8F);
      state.chainId = t.getId();
      state.chainUuid = t.getUUID();
      state.chainTicks = 0;
      boolean pull = pullable(t) && acquirePull(player, state, t, GhostRiderRules.YANK_TICKS + GhostRiderRules.HOOK_HOLD_TICKS
         + GhostRiderRules.WHIRL_TICKS + GhostRiderRules.SLAM_TICKS + 20);
      state.pulling = pull;
      if (!pull) {
         GhostRiderTargets.stun(player, t, GhostRiderRules.HOOK_STUN_TICKS);
      }
      if (bike && pull) {
         state.towing = true;
         state.endMove();
      } else {
         state.start(GhostRiderMove.YANK);
      }
   }

   static boolean pullable(LivingEntity t) {
      return GhostRiderRules.pullable(t.getBbWidth(), t.getBbHeight(), GhostRiderTargets.isBoss(t))
         && HeroRegistry.allowsExternalControl(t, ControlKind.PULL) && !HeroDamage.isAnchored(t) && !t.isPassenger() && !t.isVehicle();
   }

   static boolean acquirePull(ServerPlayer player, GhostRiderState state, LivingEntity t, int ticks) {
      ControlManager manager = ControlManager.get(player.serverLevel());
      UUID effect = UUID.randomUUID();
      if (!manager.tryAcquire(t, player.getUUID(), effect, ControlKind.PULL, player.level().getGameTime() + ticks)) {
         return false;
      }
      state.chainEffect = effect;
      return true;
   }

   /** The chained body, if the chain still holds it. */
   @Nullable
   static LivingEntity chained(ServerPlayer player, GhostRiderState state) {
      if (!state.chained()) {
         return null;
      }
      Entity e = player.level().getEntity(state.chainId);
      if (!(e instanceof LivingEntity t) || !t.isAlive()) {
         return null;
      }
      if (state.chainEffect != null) {
         ControlManager manager = ControlManager.get(player.serverLevel());
         if (!state.chainEffect.equals(manager.effectIdOf(t))) {
            return null;
         }
      }
      return t;
   }

   /** Every tick with the chain on a body: reel it in, hold it, swing it, tow it; let go when it is lost. */
   static void chainTick(ServerPlayer player, GhostRiderState state) {
      if (!state.chained()) {
         return;
      }
      LivingEntity t = chained(player, state);
      state.chainTicks++;
      if (t == null || t.distanceToSqr(player) > 24.0 * 24.0) {
         releaseChain(player, state, ReleaseReason.TARGET_LOST, false);
         return;
      }
      if (WorldPause.blocksMovement(player, t)) {
         return;
      }
      if (state.towing) {
         HellCycleEntity bike = GhostRiderSkills.ridden(player);
         if (bike == null || state.chainTicks > GhostRiderRules.TOW_TICKS) {
            releaseChain(player, state, ReleaseReason.NORMAL_END, true);
            return;
         }
         place(t, towPoint(bike.position(), bike.getYRot(), t.getBbWidth()));
         if (state.chainTicks % GhostRiderRules.TOW_SCRAPE_INTERVAL == 0 && bike.serverSpeed() > 0.2) {
            GhostRiderTargets.strike(player, t, GhostRiderRules.TOW_SCRAPE_DAMAGE);
         }
         return;
      }
      if (state.is(GhostRiderMove.WHIRL) && state.pulling) {
         place(t, swingPoint(player.position(), state.elapsed, player.getYRot(), t.getBbHeight()));
         return;
      }
      if (state.is(GhostRiderMove.YANK) && state.pulling) {
         Vec3 stop = yankStop(player, t);
         Vec3 at = t.position();
         double k = Math.min(1.0, (state.elapsed + 1) / (double)GhostRiderRules.YANK_TICKS);
         Vec3 next = at.add(stop.subtract(at).scale(Math.min(1.0, 0.35 + 0.65 * k)));
         place(t, next);
         return;
      }
      if (state.pulling && state.chainTicks <= GhostRiderRules.YANK_TICKS + GhostRiderRules.HOOK_HOLD_TICKS && !state.running()) {
         // held at the end of the chain, waiting for the whirl or for the hold to end
         t.setDeltaMovement(Vec3.ZERO);
         return;
      }
      if (!state.running() && state.chainTicks > GhostRiderRules.YANK_TICKS + GhostRiderRules.HOOK_HOLD_TICKS) {
         releaseChain(player, state, ReleaseReason.NORMAL_END, false);
      } else if (!state.pulling && state.chainTicks > GhostRiderRules.HOOK_STUN_TICKS + 10 && !state.running()) {
         releaseChain(player, state, ReleaseReason.NORMAL_END, false);
      }
   }

   static Vec3 yankStop(ServerPlayer player, LivingEntity t) {
      Vec3 flat = GhostRiderTargets.flat(player.getLookAngle());
      return player.position().add(flat.scale(GhostRiderRules.YANK_STOP + t.getBbWidth() * 0.5));
   }

   /** Shared server/client: where the towed body trails, the chain's length behind the bike. */
   public static Vec3 towPoint(Vec3 bike, float yaw, float width) {
      double rad = Math.toRadians(yaw);
      Vec3 heading = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
      return bike.add(heading.scale(-(GhostRiderRules.TOW_LENGTH + width * 0.5))).add(0, 0.05, 0);
   }

   /** Shared server/client: the body on the chain in Chain Whirl, two turns around him in the move. */
   public static Vec3 swingPoint(Vec3 centre, float elapsed, float yaw, float height) {
      double turns = elapsed / (double)GhostRiderRules.WHIRL_TICKS * 2.0;
      double a = Math.toRadians(yaw) + turns * Math.PI * 2.0;
      double lift = Math.min(1.0, elapsed / 6.0) * 1.6;
      return centre.add(-Math.sin(a) * GhostRiderRules.SWING_RADIUS, lift + Math.max(0.0, 1.0 - height * 0.5), Math.cos(a) * GhostRiderRules.SWING_RADIUS);
   }

   static void place(LivingEntity t, Vec3 at) {
      Vec3 before = t.position();
      Vec3 safe = at;
      AABB box = t.getDimensions(t.getPose()).makeBoundingBox(at);
      if (!t.level().noCollision(t, box)) {
         // never through walls: keep the body where it was, nudged up out of the floor
         AABB up = box.move(0, 0.6, 0);
         safe = t.level().noCollision(t, up) ? at.add(0, 0.6, 0) : before;
      }
      t.setPos(safe.x, safe.y, safe.z);
      t.setDeltaMovement(safe.subtract(before).scale(0.5));
      t.resetFallDistance();
      t.hurtMarked = true;
      if (t instanceof Mob mob) {
         mob.getNavigation().stop();
      }
   }

   /** Lets the chain go: the control is released once, a towed body is flung on with the bike's speed. */
   static void releaseChain(ServerPlayer player, GhostRiderState state, ReleaseReason reason, boolean fling) {
      if (!state.chained()) {
         return;
      }
      Entity e = player.level().getEntity(state.chainId);
      if (state.chainUuid != null && state.chainEffect != null) {
         ControlManager.get(player.serverLevel()).release(state.chainUuid, state.chainEffect, reason);
      }
      if (e instanceof LivingEntity t && t.isAlive()) {
         if (fling) {
            HellCycleEntity bike = GhostRiderSkills.ridden(player);
            double rad = Math.toRadians(bike != null ? bike.getYRot() : player.getYRot());
            Vec3 heading = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            double speed = bike != null ? bike.serverSpeed() : 0.3;
            GhostRiderTargets.push(t, heading.scale(Math.max(0.4, speed)).add(0, 0.35, 0));
         }
         if (t instanceof ServerPlayer victim) {
            victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
         }
      }
      state.chainId = -1;
      state.chainUuid = null;
      state.chainEffect = null;
      state.chainTicks = 0;
      state.towing = false;
      state.pulling = false;
   }

   // --- timeline ---------------------------------------------------------------------------------------------

   static void tick(ServerPlayer player, GhostRiderState state) {
      chainTick(player, state);
      GhostRiderMove move = state.move;
      if (move == null) {
         return;
      }
      if (!move.onBike() && GhostRiderSkills.ridden(player) != null && move != GhostRiderMove.TRANSFORM && move != GhostRiderMove.REVERT) {
         state.endMove(); // mounted mid-move: foot moves stop
         return;
      }
      int e = state.elapsed;
      switch (move) {
         case LASH_1, LASH_2, LASH_3 -> {
            if (e == move.contact) lashContact(player, state, false);
         }
         case BIKE_LASH -> {
            if (e == move.contact) lashContact(player, state, true);
         }
         case HOOK, BIKE_HOOK -> {
            if (e == move.contact) {
               hookContact(player, state, move == GhostRiderMove.BIKE_HOOK);
               if (state.move != move) {
                  return; // became YANK / HOOK_MISS / towing
               }
            }
         }
         default -> GhostRiderSkills.moveTick(player, state, move, e);
      }
      if (state.move != move) {
         return;
      }
      state.elapsed++;
      if (state.elapsed >= move.length) {
         GhostRiderSkills.moveEnd(player, state, move);
      }
   }

   static float yawTo(Vec3 from, Vec3 to) {
      return (float)(Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
   }
}
