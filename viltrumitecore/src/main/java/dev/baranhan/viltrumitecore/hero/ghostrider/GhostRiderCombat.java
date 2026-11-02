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
            if (bike == null && (m.isLash() || m == GhostRiderMove.PUNCH)) {
               state.primaryQueued = true; // a click early in a foot strike chains as soon as it may (QA: the third lash got lost)
            }
            return;
         }
         state.endMove();
      }
      if (bike != null) {
         state.start(GhostRiderMove.BIKE_LASH);
         state.cooldowns[GhostRiderRules.CD_LASH] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_LASH];
      } else {
         LivingEntity close = punchTarget(player, GhostRiderRules.PUNCH_RANGE);
         if (close != null) {
            // a body in his face takes the burning fist; the chain combo keeps its place (spec §5.4)
            state.start(GhostRiderMove.PUNCH);
            state.punchTarget = close.getId();
            state.moveDir = player.getLookAngle();
            GhostRiderSounds.play(player, GhostRiderSounds.HELLFIRE_IGNITE, 0.45F, GhostRiderSounds.jitter(player, 1.35F));
            return;
         }
         int step = state.comboIdle <= GhostRiderRules.COMBO_WINDOW ? (state.comboStep + 1) % GhostRiderRules.COMBO_LENGTH : 0;
         state.comboStep = step;
         state.start(GhostRiderMove.lash(step));
      }
      state.moveDir = player.getLookAngle();
      GhostRiderSounds.play(player, GhostRiderSounds.CHAIN_LASH, 0.9F, GhostRiderSounds.jitter(player, state.comboStep == 2 ? 0.85F : 1.0F));
   }

   /**
    * The body a close LMB punches: the one under the crosshair when its box is within {@code reach} of his eyes (an
    * innocent too - the blow is aimed), else the nearest one he may strike unaimed in the fist's cone. Null: the chain.
    */
   @Nullable
   static LivingEntity punchTarget(ServerPlayer player, double reach) {
      Vec3 eye = player.getEyePosition();
      LivingEntity aimed = aimed(player, reach + 1.5, false);
      if (aimed != null) {
         return Math.sqrt(aimed.getBoundingBox().distanceToSqr(eye)) <= reach ? aimed : null;
      }
      List<LivingEntity> near = GhostRiderTargets.arc(player, eye, player.getLookAngle(), reach, GhostRiderRules.PUNCH_ARC,
         e -> GhostRiderTargets.mayAutoHit(player, e) && e.getVehicle() != player && player.getVehicle() != e
            && GhostRiderTargets.visible(player.level(), eye, GhostRiderTargets.center(e)));
      return near.isEmpty() ? null : near.get(0);
   }

   /** The fist lands (PUNCH contact): the body it was thrown at if still in reach, else whoever stepped into it. */
   static void punchContact(ServerPlayer player, GhostRiderState state) {
      double reach = GhostRiderRules.PUNCH_RANGE + GhostRiderRules.PUNCH_SLACK;
      Vec3 eye = player.getEyePosition();
      LivingEntity t = player.level().getEntity(state.punchTarget) instanceof LivingEntity l && GhostRiderTargets.mayHit(player, l)
         && Math.sqrt(l.getBoundingBox().distanceToSqr(eye)) <= reach && GhostRiderTargets.visible(player.level(), eye, GhostRiderTargets.center(l))
         ? l : punchTarget(player, reach);
      state.punchTarget = -1;
      state.comboIdle = 0;
      if (t == null || !state.hit.add(t.getId())) {
         return; // a swing at the air: the ignite flare at the throw was its sound
      }
      if (GhostRiderTargets.strike(player, t, GhostRiderRules.PUNCH_DAMAGE)) {
         Hellfire.ignite(player, t, GhostRiderRules.PUNCH_BURN_TICKS);
         Vec3 out = GhostRiderTargets.outward(player.position(), t);
         GhostRiderTargets.push(t, out.scale(0.55).add(0, 0.22, 0));
         // the knuckles meet the near side of the body, at chest height
         Vec3 c = GhostRiderTargets.center(t);
         Vec3 at = c.subtract(out.scale(t.getBbWidth() * 0.5));
         GhostRiderFx.send(player, GhostRiderFx.PUNCH, at, GhostRiderTargets.hand(player), 1.0F, null, 2, t.getId());
         player.level().playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, GhostRiderSounds.jitter(player, 0.8F));
         GhostRiderSounds.playAt(player, at, GhostRiderSounds.HELLFIRE_IGNITE, 0.8F, GhostRiderSounds.jitter(player, 1.1F));
      }
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
      // the sweep spares the innocent: only an on-foot lash aimed straight at one lands on it (never one from the bike)
      LivingEntity aimed = bike ? null : GhostRiderTargets.aimed(targets, eye, look, GhostRiderRules.LASH_AIM_DEGREES);
      targets.removeIf(t -> t != aimed && !GhostRiderTargets.mayAutoHit(player, t));
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

   /**
    * The first body on the aim ray up to {@code range} (blocks stop it); from the saddle the chain only bites the guilty
    * (the innocent are passed over).
    */
   @Nullable
   static LivingEntity aimed(ServerPlayer player, double range, boolean bike) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = GhostRiderTargets.clip(player, eye, eye.add(player.getLookAngle().scale(range)));
      AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
      java.util.function.Predicate<Entity> ok = e -> e instanceof LivingEntity l
         && (bike ? GhostRiderTargets.mayAutoHit(player, l) : GhostRiderTargets.mayHit(player, l))
         && e.getVehicle() != player && player.getVehicle() != e;
      // the body truly under the crosshair wins; only when the line misses every body does the forgiving margin
      // pick one - the one nearest the line of sight, not the nearest whose widened corner the line clips
      EntityHitResult exact = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box, ok, 0.0F);
      if (exact != null && exact.getEntity() instanceof LivingEntity l) {
         return l;
      }
      Vec3 axis = end.subtract(eye).normalize();
      LivingEntity pick = null;
      double best = -2.0;
      for (Entity e : player.level().getEntities(player, box, ok)) {
         if (!(e instanceof LivingEntity l) || e.getBoundingBox().inflate(0.45).clip(eye, end).isEmpty()) {
            continue;
         }
         double dot = e.getBoundingBox().getCenter().subtract(eye).normalize().dot(axis);
         if (dot > best) {
            best = dot;
            pick = l;
         }
      }
      return pick;
   }

   static void hookContact(ServerPlayer player, GhostRiderState state, boolean bike) {
      LivingEntity t = aimed(player, GhostRiderRules.HOOK_RANGE, bike);
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
      state.chainHold = null;
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
         if (t instanceof ServerPlayer) {
            // a player moves on its own client: a zero velocity set here never reaches it, so a hooked player walked
            // out of the hold at once (QA, dedicated server 2026-11-02); place() pulls it back where the yank left it
            if (state.chainHold == null) {
               state.chainHold = t.position();
            }
            place(t, state.chainHold);
         } else {
            t.setDeltaMovement(Vec3.ZERO);
         }
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

   /** Pure: a stun interrupts the Rider's running move or chain (form changes run on; nothing running = nothing to do). */
   public static boolean stunInterrupts(boolean anchored, @javax.annotation.Nullable GhostRiderMove move, boolean chained) {
      if (!anchored) {
         return false;
      }
      return chained || move != null && move != GhostRiderMove.TRANSFORM && move != GhostRiderMove.REVERT;
   }

   /** Longest step of the chain's swept move: a body never skips a block between two checks. */
   public static final double SWEEP_STEP = 0.25;

   /**
    * Pure: how far along {@code from -> to} a body may go, checked every {@link #SWEEP_STEP}: the last free position before
    * the first blocked one (a step blocked at the feet may lift by {@code stepUp}, a kerb or a floor edge). A destination
    * past a wall is never reached through it.
    */
   public static Vec3 sweep(Vec3 from, Vec3 to, double stepUp, java.util.function.Predicate<Vec3> free) {
      Vec3 d = to.subtract(from);
      int steps = Math.max(1, (int)Math.ceil(d.length() / SWEEP_STEP));
      Vec3 last = from;
      double lift = 0.0;
      for (int i = 1; i <= steps; i++) {
         Vec3 p = from.add(d.scale(i / (double)steps)).add(0, lift, 0);
         if (free.test(p)) {
            last = p;
         } else if (lift == 0.0 && stepUp > 0.0 && free.test(p.add(0, stepUp, 0)) && free.test(last.add(0, stepUp, 0))) {
            lift = stepUp;
            last = p.add(0, stepUp, 0);
         } else {
            break;
         }
      }
      return last;
   }

   static void place(LivingEntity t, Vec3 at) {
      Vec3 before = t.position();
      // never through walls: the body moves along the path and stops at the first block in the way (the whole path is
      // checked, not only the destination, so turning toward a wall cannot carry it to the far side)
      Vec3 safe = sweep(before, at, 0.6, p -> t.level().noCollision(t, t.getDimensions(t.getPose()).makeBoundingBox(p)));
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
      state.chainHold = null;
   }

   // --- timeline ---------------------------------------------------------------------------------------------

   static void tick(ServerPlayer player, GhostRiderState state) {
      if (stunInterrupts(HeroDamage.isAnchored(player), state.move, state.chained())) {
         // a stun (any shared FREEZE, not only stopped time) ends a running attack and lets the chained body go: no
         // contact damage and no dragging while he cannot act
         releaseChain(player, state, ReleaseReason.INTERRUPTED, false);
         if (state.move != null && state.move != GhostRiderMove.TRANSFORM && state.move != GhostRiderMove.REVERT) {
            state.endMove();
         }
         return;
      }
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
         case PUNCH -> {
            if (e == move.contact) punchContact(player, state);
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
      } else if (state.takeQueuedPrimary()) {
         primary(player, state);
      }
   }

   static float yawTo(Vec3 from, Vec3 to) {
      return (float)(Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
   }
}
