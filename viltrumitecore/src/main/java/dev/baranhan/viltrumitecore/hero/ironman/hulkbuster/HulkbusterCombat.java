package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server driver of the Hulkbuster kit (rework spec §6–7): presses go through {@link HulkbusterControls},
 * moves run on the shared StagedTimeline, every strike lands from its {@link HulkStrikes} row on the
 * contact tick (= first MAIN tick of its step), the grab holds through the shared held-target seam and a
 * PULL control, and every end of a hold releases it. The ultimate is a staged move with hyper armour.
 */
public final class HulkbusterCombat {
   private HulkbusterCombat() {
   }

   // ---------------------------------------------------------------- input

   public static HulkbusterControls.Context context(ServerPlayer player, IronManState state, HulkbusterControls.Input input) {
      HulkbusterKit kit = state.hulkKit;
      HulkbusterMotion motion = kit.motion;
      boolean forwardDash = (motion.state == HulkbusterMotion.State.DASH || motion.state == HulkbusterMotion.State.DASH_WINDUP) && motion.direction == 0;
      return new HulkbusterControls.Context(kit.grab.active(), kit.grab.punches, motion.state == HulkbusterMotion.State.DIVE, !player.onGround(), forwardDash,
         kit.cooldown(input), !state.energy.weaponsLocked() && state.energy.value() >= HulkbusterRules.REPULSORS_COST,
         state.hulkbuster.durability() / HulkbusterLayer.DURABILITY, state.hulkbuster.spareUsed(), motion.backHeld || wallAhead(player),
         motion.state == HulkbusterMotion.State.STAGGER);
   }

   /** A press of one input. Returns true when the input belongs to the Hulkbuster (handled, refused or silent). */
   public static boolean press(ServerPlayer player, IronManState state, HulkbusterControls.Input input) {
      HulkbusterKit kit = state.hulkKit;
      if (input == HulkbusterControls.Input.LMB && !kit.grab.active()) {
         // Tap or hold is decided by the release (HOLD_THRESHOLD); the grab punches fire on the press.
         kit.lmbHeld = 0;
         return true;
      }

      if (input == HulkbusterControls.Input.GUARD) {
         kit.blockHeld = true;
      }

      request(player, state, HulkbusterControls.resolve(input, context(player, state, input)), kit.cooldown(input));
      return true;
   }

   public static void release(ServerPlayer player, IronManState state, HulkbusterControls.Input input) {
      HulkbusterKit kit = state.hulkKit;
      switch (input) {
         case LMB -> {
            int held = kit.lmbHeld;
            kit.lmbHeld = -1;
            if (held >= 0 && held < HulkbusterRules.HOLD_THRESHOLD) {
               request(player, state, HulkbusterControls.resolve(HulkbusterControls.Input.LMB, context(player, state, HulkbusterControls.Input.LMB)));
            } else if (kit.timeline.is(HulkMove.CHARGED) && kit.timeline.phase() == MovePhase.INTRO) {
               kit.chargeHeld = kit.timeline.elapsed();
               onPhase(player, state, kit.timeline.skipTo(MovePhase.MAIN));
            }
         }
         case GUARD -> {
            kit.blockHeld = false;
            if (kit.timeline.is(HulkMove.BLOCK) && kit.timeline.phase() != MovePhase.FINISH) {
               kit.timeline.skipTo(MovePhase.FINISH);
            }
         }
         default -> {
         }
      }
   }

   static void request(ServerPlayer player, IronManState state, HulkbusterControls.Decision decision) {
      request(player, state, decision, 0);
   }

   static void request(ServerPlayer player, IronManState state, HulkbusterControls.Decision decision, int cooldown) {
      if (decision.silent()) {
         return;
      }

      if (!decision.accepted()) {
         refuse(player, decision.refusal(), cooldown);
         return;
      }

      HulkbusterKit kit = state.hulkKit;
      HulkMove move = decision.move();
      StagedTimeline<HulkMove> timeline = kit.timeline;
      int partner = move.paired() ? kit.grab.victimId : -1;
      HulkMove before = timeline.move();
      StagedTimeline.Admission admission;
      if (move == HulkMove.COMBO) {
         admission = comboRequest(kit);
      } else {
         admission = timeline.requestAt(move, partner, decision.step());
      }

      if (admission == StagedTimeline.Admission.REFUSE) {
         refuse(player, HulkbusterControls.BUSY, 0);
         return;
      }

      if (admission.started()) {
         if (before != null && before != timeline.move()) {
            onStop(player, state, before, false);
         }

         onStart(player, state, timeline.move(), timeline.step());
         onPhase(player, state, timeline.entryEvent());
      }
   }

   /** Combo chain: the next step inside the window, step 1 after a pause; one buffered press (StagedTimeline QUEUE). */
   private static StagedTimeline.Admission comboRequest(HulkbusterKit kit) {
      StagedTimeline<HulkMove> timeline = kit.timeline;
      if (timeline.is(HulkMove.COMBO)) {
         return timeline.request(HulkMove.COMBO, -1);
      }

      int step = kit.comboIdle <= HulkbusterRules.COMBO_CHAIN_WINDOW ? kit.comboNext : 0;
      return timeline.requestAt(HulkMove.COMBO, -1, step);
   }

   static void refuse(ServerPlayer player, @Nullable String key, int cooldown) {
      HulkbusterSounds.notify(player, HulkbusterSounds.Cue.REFUSE);
      if (key == null) {
         return;
      }

      player.displayClientMessage(HulkbusterControls.COOLDOWN.equals(key)
         ? Component.translatable(key, Math.max(1, (cooldown + 19) / 20))
         : Component.translatable(key), true);
   }

   private static int cooldownOf(HulkbusterKit kit, HulkMove move) {
      return switch (move) {
         case GRAB_REACH -> kit.grabCooldown;
         case HEAVY_REPULSORS -> kit.repulsorsCooldown;
         case SEISMIC_RIFT -> kit.riftCooldown;
         case VERONICA_CAGE -> kit.cageCooldown;
         case JET_RAM -> kit.ultimateCooldown;
         default -> 0;
      };
   }

   // ---------------------------------------------------------------- tick

   public static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      HulkbusterKit kit = state.hulkKit;
      kit.tickCooldowns();
      if (controlled) {
         interrupt(player, state, InterruptReason.CONTROL);
         releaseGrab(player, state, ReleaseReason.INTERRUPTED);
         kit.lmbHeld = -1;
      }

      if (kit.lmbHeld >= 0 && ++kit.lmbHeld == HulkbusterRules.HOLD_THRESHOLD && !kit.grab.active()) {
         request(player, state, HulkbusterControls.resolve(HulkbusterControls.Input.LMB_HOLD, context(player, state, HulkbusterControls.Input.LMB_HOLD)));
      }

      StagedTimeline<HulkMove> timeline = kit.timeline;
      HulkMove before = timeline.move();
      int stepBefore = timeline.step();
      StagedTimeline.Event event = timeline.tick();
      if (event == StagedTimeline.Event.END) {
         onEnd(player, state, before, stepBefore);
      } else if (event == StagedTimeline.Event.CHAIN) {
         onStart(player, state, timeline.move(), timeline.step());
         onPhase(player, state, timeline.entryEvent());
      } else if (event != StagedTimeline.Event.NONE) {
         onPhase(player, state, event);
      }

      HulkMove move = timeline.move();
      if (move != null) {
         during(player, state, move, timeline.step(), timeline.elapsed());
      }

      if (kit.grab.active()) {
         holdTick(player, state);
      }

      thrownTick(player, state);
   }

   /** A hit taken: hyper armour keeps the move, otherwise a heavy flinch stops it (spec §1.3.2). */
   public static void onHurt(ServerPlayer player, IronManState state, float amount) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.grab.active()) {
         // Damage to the holder speeds up the victim's escape (spec §6.2.6).
         kit.grab.escape += amount * HulkbusterRules.ESCAPE_PER_DAMAGE;
      }

      HulkMove move = kit.timeline.move();
      if (move == null || amount < 6.0F || move.paired()) {
         return;
      }

      if (!move.resistsHit(kit.timeline.phase(), kit.timeline.step())) {
         interrupt(player, state, InterruptReason.HIT);
      }
   }

   public static void interrupt(ServerPlayer player, IronManState state, InterruptReason reason) {
      HulkbusterKit kit = state.hulkKit;
      HulkMove move = kit.timeline.move();
      if (move == null) {
         return;
      }

      if (reason != InterruptReason.LIFECYCLE && move.resists(reason, kit.timeline.phase())) {
         return;
      }

      kit.timeline.clear();
      onStop(player, state, move, true);
   }

   // ---------------------------------------------------------------- move events

   private static void onStart(ServerPlayer player, IronManState state, @Nullable HulkMove move, int step) {
      if (move == null) {
         return;
      }

      HulkbusterKit kit = state.hulkKit;
      switch (move) {
         case COMBO -> {
            kit.comboNext = (step + 1) % HulkMove.COMBO.maxSteps();
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.SWING);
         }
         case CHARGED -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.CHARGE);
         case GRAB_REACH, GRAB_THROW, GRAB_SLAM, DASH_STRIKE, COUNTER -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.SWING);
         case GRAB_FINISH -> {
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.CHARGE);
         }
         case BLOCK -> {
            kit.blockTicks = 0;
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.BLOCK);
         }
         case HEAVY_REPULSORS -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.REPULSOR_CHARGE);
         case SEISMIC_RIFT -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
         case VERONICA_CAGE -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.CAGE_MARK);
         case SPARE_ARM -> HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
         case JET_RAM -> {
            kit.ultimateCooldown = HulkbusterRules.ULT_COOLDOWN;
            kit.ultTarget = -1;
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            kit.ultDir = flat.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.ULT_IGNITE);
            dev.baranhan.viltrumitecore.hero.ironman.IronManLines.say(player, dev.baranhan.viltrumitecore.hero.ironman.IronManLines.EVENT_HULK_RAM);
            HeroFx.launch(player, player.position(), 1.2F, player.level().getBlockState(player.blockPosition().below()));
         }
         default -> {
         }
      }
   }

   private static void onPhase(ServerPlayer player, IronManState state, StagedTimeline.Event event) {
      HulkbusterKit kit = state.hulkKit;
      HulkMove move = kit.timeline.move();
      if (move == null || event != StagedTimeline.Event.ENTER_MAIN) {
         return;
      }

      int step = kit.timeline.step();
      HulkStrikes.Strike strike = HulkStrikes.of(move, step);
      switch (move) {
         case CHARGED -> {
            kit.chargeHeld = Math.max(kit.chargeHeld, 0);
            int level = HulkbusterRules.chargeLevel(kit.chargeHeld);
            Vec3 look = flatLook(player);
            push(player, look.scale(HulkbusterRules.CHARGE_LUNGE[level]).add(0.0, 0.05, 0.0));
            landStrike(player, state, strike, HulkbusterRules.CHARGE_DAMAGE[level], 1.0F + 0.35F * level);
         }
         case GRAB_THROW -> throwVictim(player, state);
         case GRAB_SLAM -> slamVictim(player, state);
         case GRAB_PUNCH, GRAB_FINISH -> facePunch(player, state, strike, move == HulkMove.GRAB_FINISH);
         case HEAVY_REPULSORS -> {
            state.energy.spend(HulkbusterRules.REPULSORS_COST);
            kit.repulsorsCooldown = HulkbusterRules.REPULSORS_COOLDOWN;
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.REPULSOR_BEAM);
         }
         case SEISMIC_RIFT -> rift(player, state);
         case VERONICA_CAGE -> HulkbusterCage.mark(player, state);
         case HERO_LANDING -> heroLanding(player);
         case GRAB_REACH -> {
         }
         default -> {
            if (strike != null) {
               if (move == HulkMove.COMBO || move == HulkMove.DASH_STRIKE) {
                  push(player, flatLook(player).scale(move == HulkMove.DASH_STRIKE ? 0.6 : 0.32));
               }

               landStrike(player, state, strike, strike.damage(), 1.0F);
            }
         }
      }
   }

   /** Per tick of a running move (after its phase events). */
   private static void during(ServerPlayer player, IronManState state, HulkMove move, int step, int elapsed) {
      HulkbusterKit kit = state.hulkKit;
      MovePhase phase = kit.timeline.phase();
      switch (move) {
         case GRAB_REACH -> {
            if (phase == MovePhase.MAIN && !kit.grab.active()) {
               LivingEntity target = grabTarget(player, kit.riftWindow > 0 ? HulkbusterRules.GRAB_REACH_RIFT : HulkbusterRules.GRAB_REACH);
               if (target != null && acquire(player, state, target)) {
                  HulkMove reach = kit.timeline.move();
                  kit.timeline.clear();
                  onStop(player, state, reach, false);
                  StagedTimeline.Admission admission = kit.timeline.requestAt(HulkMove.GRAB_HOLD, kit.grab.victimId, 0);
                  if (admission.started()) {
                     HulkbusterSounds.play(player, HulkbusterSounds.Cue.GRAB);
                     HeroFx.hitStop(player, target.getBoundingBox().getCenter(), 3, player.getId(), target.getId());
                  }
               }
            }
         }
         case BLOCK -> {
            kit.blockTicks++;
            if (!kit.blockHeld && phase != MovePhase.FINISH) {
               kit.timeline.skipTo(MovePhase.FINISH);
            }
         }
         case HEAVY_REPULSORS -> {
            if (phase == MovePhase.MAIN) {
               beamTick(player, state);
            }
         }
         case SPARE_ARM -> {
            if (elapsed == move.ticks(MovePhase.INTRO, 0)) {
               Vec3 shoulder = player.position().add(0.0, 2.6, 0.0).add(sideOf(player, -1.0));
               HeroFx.flash(player, shoulder, 0.8F);
               HeroFx.shards(player, shoulder, 0.6F, null, new float[0], null);
               HulkbusterSounds.play(player, HulkbusterSounds.Cue.ARM_DETACH);
            } else if (elapsed == move.ticks(MovePhase.INTRO, 0) + move.ticks(MovePhase.MAIN, 0)) {
               state.hulkbuster.restore(HulkbusterRules.SPARE_ARM_RESTORE * HulkbusterLayer.DURABILITY);
               state.hulkbuster.markSpareUsed();
               HulkbusterSounds.play(player, HulkbusterSounds.Cue.ARM_DOCK);
               HeroFx.flash(player, player.position().add(0.0, 2.6, 0.0).add(sideOf(player, -1.0)), 0.6F);
            }
         }
         case JET_RAM -> HulkbusterUltimate.tick(player, state, elapsed);
         case HERO_LANDING -> {
            if (phase == MovePhase.INTRO && player.onGround() && elapsed > 1) {
               onPhase(player, state, kit.timeline.skipTo(MovePhase.MAIN));
            } else if (phase != MovePhase.INTRO) {
               player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
               player.hurtMarked = true;
            }
         }
         case CHARGED -> {
            if (phase == MovePhase.INTRO && elapsed == HulkbusterRules.CHARGE_HOLD_MAX - 1) {
               // Held too long: the punch fires by itself (spec §6.3).
               kit.chargeHeld = elapsed + 1;
            }
         }
         default -> {
         }
      }
   }

   private static void onEnd(ServerPlayer player, IronManState state, @Nullable HulkMove move, int step) {
      if (move == null) {
         return;
      }

      HulkbusterKit kit = state.hulkKit;
      switch (move) {
         case COMBO -> kit.comboIdle = 0;
         case GRAB_REACH -> {
            if (!kit.grab.active()) {
               kit.timeline.requestAt(HulkMove.GRAB_MISS, -1, 0);
               kit.grabCooldown = HulkbusterRules.GRAB_COOLDOWN / 2;
               HulkbusterSounds.play(player, HulkbusterSounds.Cue.GRAB_MISS);
            }
         }
         case GRAB_HOLD -> releaseGrab(player, state, ReleaseReason.NORMAL_END);
         case GRAB_PUNCH -> {
            if (kit.grab.active()) {
               // Back into the hold, past the lift.
               kit.timeline.requestAt(HulkMove.GRAB_HOLD, kit.grab.victimId, 0);
               kit.timeline.skipTo(MovePhase.MAIN);
            }
         }
         case JET_RAM -> HulkbusterUltimate.end(player, state);
         default -> {
         }
      }
   }

   /** A move left early (replaced or interrupted). */
   private static void onStop(ServerPlayer player, IronManState state, @Nullable HulkMove move, boolean interrupted) {
      if (move == null) {
         return;
      }

      HulkbusterKit kit = state.hulkKit;
      switch (move) {
         case GRAB_HOLD, GRAB_PUNCH, GRAB_FINISH, GRAB_THROW, GRAB_SLAM -> {
            if (interrupted) {
               releaseGrab(player, state, ReleaseReason.INTERRUPTED);
            }
         }
         case JET_RAM -> HulkbusterUltimate.end(player, state);
         case BLOCK -> kit.blockTicks = 0;
         default -> {
         }
      }
   }

   // ---------------------------------------------------------------- strikes

   /** Lands one strike row at its contact tick: cone + radius, damage, push, hit-stop, shake (via HeroFx), carve. */
   static List<LivingEntity> landStrike(ServerPlayer player, IronManState state, @Nullable HulkStrikes.Strike strike, float damage, float power) {
      List<LivingEntity> hits = new ArrayList<>();
      if (strike == null) {
         return hits;
      }

      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 contact = eye.add(look.scale(strike.reach() * 0.75));
      double reach = strike.reach() + strike.radius();
      AABB box = player.getBoundingBox().inflate(reach);
      for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && IronManTargets.canHit(player, e))) {
         Vec3 centre = target.getBoundingBox().getCenter();
         Vec3 to = centre.subtract(eye);
         double distance = to.length();
         boolean inCone = distance <= strike.reach() + target.getBbWidth() * 0.5 && (strike.cone() <= 0.0 || to.normalize().dot(look) >= strike.cone());
         boolean inRadius = strike.radius() > 0.0 && centre.distanceTo(contact) <= strike.radius() + target.getBbWidth() * 0.5;
         if (!inCone && !inRadius || !visible(player, eye, centre)) {
            continue;
         }

         target.invulnerableTime = 0;
         if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            hits.add(target);
            if (HeroRegistry.allowsImpulse(target)) {
               Vec3 away = new Vec3(centre.x - player.getX(), 0.0, centre.z - player.getZ());
               away = away.lengthSqr() < 1.0E-6 ? flatLook(player) : away.normalize();
               target.setDeltaMovement(target.getDeltaMovement().add(away.scale(strike.push() * power)).add(0.0, strike.lift() * power, 0.0));
               target.hurtMarked = true;
            }
         }
      }

      Vec3 at = hits.isEmpty() ? contact : hits.get(0).getBoundingBox().getCenter();
      if (!hits.isEmpty()) {
         int[] ids = new int[hits.size() + 1];
         ids[0] = player.getId();
         for (int i = 0; i < hits.size(); i++) {
            ids[i + 1] = hits.get(i).getId();
         }

         HeroFx.hitStop(player, at, strike.hitStop(), ids);
         HeroFx.flash(player, at, 0.6F + 0.2F * power);
         HulkbusterSounds.play(player, strike.cue(), at, 1.0F);
      } else {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.SWING, at, 0.8F);
      }

      if (strike.fx() == HulkStrikes.Fx.HAMMER) {
         BlockPos ground = BlockPos.containing(at.x, player.getY() - 0.5, at.z);
         HeroFx.slam(player, new Vec3(at.x, player.getY(), at.z), player.level().getBlockState(ground), 3.5F * power);
      } else if (!hits.isEmpty()) {
         HeroFx.shockwave(player, at, 0.35F * strike.shake() * power, null, 1.5F);
      }

      if (strike.carve() > 0.0) {
         HulkbusterDestruction.soft(player.serverLevel(), at, strike.carve() * power, HulkbusterDestruction.PUNCH_HARDNESS);
      }

      return hits;
   }

   private static boolean visible(ServerPlayer player, Vec3 from, Vec3 to) {
      BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) >= to.distanceToSqr(from) - 1.0;
   }

   // ---------------------------------------------------------------- grab

   @Nullable
   private static LivingEntity grabTarget(ServerPlayer player, double reach) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      AABB box = player.getBoundingBox().inflate(reach + 1.0);
      return player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && grabbable(player, e)).stream()
         .filter(e -> {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            return to.length() <= reach + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.75 && visible(player, eye, e.getBoundingBox().getCenter());
         })
         .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
         .orElse(null);
   }

   static boolean grabbable(ServerPlayer player, LivingEntity target) {
      if (!IronManTargets.canHit(player, target) || target.isPassenger() || target.isVehicle()) {
         return false;
      }

      // Bosses and bodies bigger than 2 x 3 stay on the ground (Thor review C-M11 rule).
      if (target.getBbWidth() > 2.0F || target.getBbHeight() > 3.0F || !target.canChangeDimensions()) {
         return false;
      }

      return HeroRegistry.allowsExternalControl(target, ControlKind.PULL) && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
   }

   private static boolean acquire(ServerPlayer player, IronManState state, LivingEntity target) {
      HulkbusterKit kit = state.hulkKit;
      UUID effect = UUID.randomUUID();
      long expires = player.level().getGameTime() + HulkbusterRules.GRAB_HOLD_MAX + 60L;
      if (!ControlManager.get(player.serverLevel()).tryAcquire(target, player.getUUID(), effect, ControlKind.PULL, expires)) {
         return false;
      }

      kit.grab.clear();
      kit.grab.victimId = target.getId();
      kit.grab.victimUuid = target.getUUID();
      kit.grab.effect = effect;
      kit.grab.strong = target instanceof Player victim && HulkbusterTags.superstrong(victim);
      return true;
   }

   /** The victim's neck in the left fist, in front at chest height (shared server/client anchor). */
   public static Vec3 anchor(Vec3 position, float yaw, float width, float height) {
      Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
      Vec3 left = new Vec3(forward.z, 0.0, -forward.x);
      double reach = 1.55 + width * 0.5;
      return position.add(forward.scale(reach)).add(left.scale(-0.35)).add(0.0, 2.55 - height * 0.85, 0.0);
   }

   private static void holdTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Entity entity = player.level().getEntity(kit.grab.victimId);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.level() != player.level() || target.distanceTo(player) > 8.0) {
         releaseGrab(player, state, ReleaseReason.TARGET_LOST);
         return;
      }

      if (WorldPause.blocksMovement(player, target)) {
         return;
      }

      kit.grab.held++;
      if (!(target instanceof Player)) {
         kit.grab.escape += HulkbusterRules.ESCAPE_MOB_PER_TICK;
      }

      if (kit.grab.escape >= HulkbusterRules.ESCAPE_FULL) {
         escaped(player, state, target);
         return;
      }

      if (kit.grab.held > HulkbusterRules.GRAB_HOLD_MAX) {
         releaseGrab(player, state, ReleaseReason.NORMAL_END);
         return;
      }

      Vec3 at = anchor(player.position(), player.getYRot(), target.getBbWidth(), target.getBbHeight());
      // A teleport handshake every tick resets the victim's prediction: plain setPos, the client follows the anchor.
      target.setPos(at.x, at.y, at.z);
      target.setDeltaMovement(Vec3.ZERO);
      target.resetFallDistance();
      target.hurtMarked = false;
   }

   /** A mash press of a held or caged victim (HulkbusterMotionC2SPacket STRUGGLE from the victim's client). */
   public static void struggle(ServerPlayer victim) {
      Player holder = dev.baranhan.viltrumitecore.hero.HeroHeldTargets.holder(victim);
      if (holder instanceof ServerPlayer owner) {
         IronManState state = IronManState.of(owner);
         if (state != null) {
            HulkbusterKit kit = state.hulkKit;
            if (kit.grab.active() && kit.grab.victimId == victim.getId() || kit.ultTarget == victim.getId()) {
               kit.grab.escape += kit.grab.strong ? HulkbusterRules.ESCAPE_PER_PRESS_STRONG : HulkbusterRules.ESCAPE_PER_PRESS;
               return;
            }
         }
      }

      for (ServerPlayer other : victim.serverLevel().players()) {
         IronManState state = other == victim ? null : IronManState.of(other);
         if (state != null && HulkbusterCage.struggle(victim, state, HulkbusterTags.superstrong(victim) ? 6.0F : 3.0F)) {
            return;
         }
      }
   }

   private static void escaped(ServerPlayer player, IronManState state, LivingEntity target) {
      HulkbusterKit kit = state.hulkKit;
      releaseGrab(player, state, ReleaseReason.INTERRUPTED);
      kit.timeline.clear();
      kit.motion.set(HulkbusterMotion.State.STAGGER);
      kit.motion.staggerTicks = HulkbusterRules.ESCAPE_STAGGER;
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.ESCAPE);
      if (HeroRegistry.allowsImpulse(target)) {
         target.setDeltaMovement(flatLook(player).scale(0.8).add(0.0, 0.45, 0.0));
         target.hurtMarked = true;
      }
   }

   private static void facePunch(ServerPlayer player, IronManState state, @Nullable HulkStrikes.Strike strike, boolean finish) {
      HulkbusterKit kit = state.hulkKit;
      Entity entity = kit.grab.active() ? player.level().getEntity(kit.grab.victimId) : null;
      if (strike == null || !(entity instanceof LivingEntity target)) {
         return;
      }

      Vec3 at = target.getEyePosition();
      target.invulnerableTime = 0;
      if (target.hurt(player.damageSources().playerAttack(player), strike.damage())) {
         HeroFx.hitStop(player, at, strike.hitStop(), player.getId(), target.getId());
         HeroFx.flash(player, at, finish ? 1.4F : 0.5F + 0.15F * kit.grab.punches);
         HeroFx.shockwave(player, at, 0.25F * strike.shake(), null, finish ? 2.5F : 1.0F);
      }

      HulkbusterSounds.play(player, strike.cue(), at, 1.0F + 0.04F * kit.grab.punches);
      kit.grab.escape = Math.max(0.0F, kit.grab.escape - 8.0F);
      if (finish) {
         releaseGrab(player, state, ReleaseReason.NORMAL_END);
         if (target.isAlive() && HeroRegistry.allowsImpulse(target)) {
            target.setDeltaMovement(flatLook(player).scale(strike.push()).add(0.0, strike.lift(), 0.0));
            target.hurtMarked = true;
            kit.thrown.put(target.getId(), 40);
         }

         HulkbusterDestruction.soft(player.serverLevel(), at, strike.carve(), HulkbusterDestruction.PUNCH_HARDNESS);
         line(player, target, "hud.viltrumitecore.ironman.hulkbuster.line_sleep");
      } else {
         kit.grab.punches++;
      }
   }

   /**
    * A spoken line as text (spec §6.2.4 "Спи!"): no film audio. Now a hero subtitle (IronManLines.HULK_SLEEP)
    * with the portrait, seen by the owner and every player within 32 blocks (the victim included); before,
    * it was an action-bar message of the owner and the victim only.
    */
   private static void line(ServerPlayer player, LivingEntity target, String key) {
      dev.baranhan.viltrumitecore.hero.ironman.IronManLines.say(player, dev.baranhan.viltrumitecore.hero.ironman.IronManLines.EVENT_HULK_SLEEP);
   }

   private static void throwVictim(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Entity entity = kit.grab.active() ? player.level().getEntity(kit.grab.victimId) : null;
      releaseGrab(player, state, ReleaseReason.NORMAL_END);
      if (entity instanceof LivingEntity target && target.isAlive()) {
         if (HeroRegistry.allowsImpulse(target)) {
            target.setDeltaMovement(player.getLookAngle().scale(HulkbusterRules.GRAB_THROW_SPEED).add(0.0, 0.35, 0.0));
            target.hurtMarked = true;
         }

         kit.thrown.put(target.getId(), 40);
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.THROW);
      }
   }

   private static void slamVictim(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Entity entity = kit.grab.active() ? player.level().getEntity(kit.grab.victimId) : null;
      releaseGrab(player, state, ReleaseReason.NORMAL_END);
      if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
         return;
      }

      boolean wall = wallAhead(player) && !kit.motion.backHeld;
      Vec3 at;
      if (wall) {
         at = player.getEyePosition().add(flatLook(player).scale(2.2)).add(0.0, -1.2, 0.0);
      } else {
         at = player.position().add(flatLook(player).scale(2.0));
      }

      target.setPos(at.x, wall ? at.y : player.getY(), at.z);
      target.invulnerableTime = 0;
      target.hurt(player.damageSources().playerAttack(player), HulkbusterRules.GRAB_SLAM_DAMAGE);
      if (HeroRegistry.allowsImpulse(target)) {
         target.setDeltaMovement(wall ? flatLook(player).scale(-0.2) : new Vec3(0.0, 0.25, 0.0));
         target.hurtMarked = true;
      }

      BlockPos ground = BlockPos.containing(at.x, player.getY() - 0.5, at.z);
      HeroFx.slam(player, new Vec3(at.x, player.getY(), at.z), player.level().getBlockState(ground), 4.0F);
      HeroFx.hitStop(player, at, 5, player.getId(), target.getId());
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.SLAM, at, 1.0F);
      HulkbusterDestruction.soft(player.serverLevel(), at, 1.8, HulkbusterDestruction.PUNCH_HARDNESS);
   }

   /** Every end of a hold releases the control (death, control, break, exit, logout, dimension, target lost). */
   public static void releaseGrab(ServerPlayer player, IronManState state, ReleaseReason reason) {
      HulkbusterKit kit = state.hulkKit;
      if (!kit.grab.active()) {
         return;
      }

      Entity entity = player.level().getEntity(kit.grab.victimId);
      UUID targetId = kit.grab.victimUuid != null ? kit.grab.victimUuid : entity == null ? null : entity.getUUID();
      if (targetId != null && kit.grab.effect != null) {
         ControlManager.get(player.serverLevel()).release(targetId, kit.grab.effect, reason);
      }

      if (entity instanceof ServerPlayer victim && !victim.hasDisconnected()) {
         // One correction at the release (the victim's client followed the anchor).
         victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
      }

      kit.grab.clear();
      kit.grabCooldown = HulkbusterRules.GRAB_COOLDOWN;
      HulkMove move = kit.timeline.move();
      if (move == HulkMove.GRAB_HOLD) {
         kit.timeline.clear();
      }
   }

   private static void thrownTick(ServerPlayer player, IronManState state) {
      Iterator<Map.Entry<Integer, Integer>> it = state.hulkKit.thrown.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Integer> entry = it.next();
         Entity entity = player.level().getEntity(entry.getKey());
         int left = entry.getValue() - 1;
         if (!(entity instanceof LivingEntity target) || !target.isAlive() || left <= 0) {
            it.remove();
            continue;
         }

         boolean hit = target.horizontalCollision || target.onGround() && left < 36;
         if (hit) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), HulkbusterRules.GRAB_THROW_IMPACT);
            HeroFx.shockwave(player, target.position(), 0.6F, player.level().getBlockState(target.blockPosition().below()), 2.5F);
            HulkbusterDestruction.soft(player.serverLevel(), target.getBoundingBox().getCenter(), 1.4, HulkbusterDestruction.PUNCH_HARDNESS);
            it.remove();
         } else {
            entry.setValue(left);
         }
      }
   }

   /** Suit-up end (spec §4.4): fist and knee into the ground, a crater, everyone around pushed off. */
   private static void heroLanding(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      Vec3 at = player.position();
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0, 1.5, 4.0), e -> e != player && IronManTargets.canHit(player, e))) {
         if (HeroRegistry.allowsImpulse(target)) {
            Vec3 away = target.position().subtract(at);
            Vec3 flat = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0.0, away.z).normalize();
            target.setDeltaMovement(flat.scale(0.9).add(0.0, 0.45, 0.0));
            target.hurtMarked = true;
         }
      }

      BlockPos ground = BlockPos.containing(at.x, at.y - 0.5, at.z);
      HeroFx.slam(player, at, level.getBlockState(ground), 5.0F);
      HeroFx.flash(player, at.add(0.0, 0.4, 0.0), 0.8F);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.HERO_LANDING);
      HulkbusterDestruction.crater(level, at.add(flatLook(player).scale(1.0)), 1.6, HulkbusterDestruction.STEP_HARDNESS);
   }

   // ---------------------------------------------------------------- abilities

   private static void beamTick(ServerPlayer player, IronManState state) {
      Vec3 from = player.getEyePosition().add(0.0, -0.6, 0.0).add(flatLook(player).scale(1.2));
      Vec3 look = player.getLookAngle();
      Vec3 end = from.add(look.scale(HulkbusterRules.REPULSORS_RANGE));
      BlockHitResult block = player.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      AABB box = new AABB(from, stop).inflate(1.2);
      for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && IronManTargets.canHit(player, e))) {
         Vec3 c = target.getBoundingBox().getCenter();
         Vec3 rel = c.subtract(from);
         double along = rel.dot(look);
         if (along < 0.0 || along > stop.distanceTo(from)) {
            continue;
         }

         if (rel.subtract(look.scale(along)).length() <= 1.0 + target.getBbWidth() * 0.5) {
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), HulkbusterRules.REPULSORS_DAMAGE_PER_TICK) && HeroRegistry.allowsImpulse(target)) {
               target.setDeltaMovement(target.getDeltaMovement().add(look.scale(0.18)));
               target.hurtMarked = true;
            }
         }
      }

      // Recoil back onto the machine (spec §6.5).
      push(player, look.scale(-0.035));
      if (player.tickCount % 2 == 0) {
         HeroFx.blade(player, from, stop, null);
         HeroFx.flash(player, stop, 0.5F);
      }

      if (block.getType() != HitResult.Type.MISS && player.tickCount % 4 == 0) {
         HulkbusterDestruction.soft(player.serverLevel(), stop, 1.2, HulkbusterDestruction.PUNCH_HARDNESS);
      }
   }

   private static void rift(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      kit.riftCooldown = HulkbusterRules.RIFT_COOLDOWN;
      kit.riftWindow = HulkbusterRules.GRAB_RIFT_WINDOW + 30;
      ServerLevel level = player.serverLevel();
      Vec3 dir = flatLook(player);
      Vec3 start = player.position().add(dir.scale(1.8));
      List<LivingEntity> hit = new ArrayList<>();
      for (double d = 0.0; d <= HulkbusterRules.RIFT_LENGTH; d += 1.0) {
         Vec3 at = start.add(dir.scale(d));
         BlockPos ground = BlockPos.containing(at.x, player.getY() - 0.5, at.z);
         if ((int)d % 2 == 0) {
            HeroDebris.erupt(player, ground, dir, new HeroDebris.Eruption(1.6, 1.0, 0.0, 4, 0.4, 0.8), 1.0F, e -> e == player);
         }

         for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.4, 1.6, 1.4),
            e -> e != player && IronManTargets.canHit(player, e) && !hit.contains(e))) {
            hit.add(target);
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), HulkbusterRules.RIFT_DAMAGE) && HeroRegistry.allowsImpulse(target)) {
               target.setDeltaMovement(dir.scale(0.2).add(0.0, 1.05, 0.0));
               target.hurtMarked = true;
            }
         }
      }

      HeroFx.slam(player, player.position().add(dir.scale(2.0)), level.getBlockState(player.blockPosition().below()), 4.0F);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.RIFT);
      HulkbusterDestruction.line(level, start, dir, HulkbusterRules.RIFT_LENGTH, player.getY() - 1.0);
   }

   // ---------------------------------------------------------------- helpers

   static Vec3 flatLook(Player player) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      return flat.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
   }

   static Vec3 sideOf(Player player, double amount) {
      Vec3 f = flatLook(player);
      return new Vec3(-f.z, 0.0, f.x).scale(amount);
   }

   static void push(ServerPlayer player, Vec3 impulse) {
      player.setDeltaMovement(player.getDeltaMovement().add(impulse));
      player.hurtMarked = true;
   }

   /** A solid wall right ahead at chest height (wall slam, spec §6.2.5). */
   static boolean wallAhead(ServerPlayer player) {
      Vec3 from = player.position().add(0.0, 1.6, 0.0);
      Vec3 to = from.add(flatLook(player).scale(2.8));
      return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
   }

   /** Is a block hit absorbed by the raised forearms (front cone)? Perfect within PERFECT_BLOCK ticks. */
   public static boolean blocking(IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      return kit.timeline.is(HulkMove.BLOCK) && kit.timeline.phase() != MovePhase.FINISH;
   }

   public static boolean perfect(IronManState state) {
      return blocking(state) && state.hulkKit.blockTicks <= HulkbusterRules.PERFECT_BLOCK;
   }

   /** Perfect block answer (spec §6.4). */
   public static void counter(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      kit.timeline.clear();
      request(player, state, new HulkbusterControls.Decision(HulkMove.COUNTER, 0, null));
   }

   /** Ends everything (suit off, death, logout, hero change, dimension change). */
   public static void stopAll(ServerPlayer player, IronManState state, ReleaseReason reason) {
      HulkbusterKit kit = state.hulkKit;
      HulkMove move = kit.timeline.move();
      kit.timeline.clear();
      if (move != null) {
         onStop(player, state, move, true);
      }

      releaseGrab(player, state, reason);
      HulkbusterUltimate.end(player, state);
      kit.stopChannels();
   }

   public static Vec3 eyeDir(Player player) {
      return player.getLookAngle();
   }
}
