package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The six abilities of the Ghost Rider (server): Hell Cycle, transformation, Hell Dash, Chain Whirl, Hellfire Wave, Penance Stare. */
public final class GhostRiderSkills {
   private GhostRiderSkills() {
   }

   // --- the bike ---------------------------------------------------------------------------------------------

   /** His own Hell Cycle when he rides it. */
   @Nullable
   public static HellCycleEntity ridden(ServerPlayer player) {
      return player.getVehicle() instanceof HellCycleEntity bike && bike.isOwner(player) ? bike : null;
   }

   /** His Hell Cycle in this level (parked or ridden), if it still exists. */
   @Nullable
   static HellCycleEntity bike(ServerPlayer player, GhostRiderState state) {
      HellCycleEntity ridden = ridden(player);
      if (ridden != null) {
         state.bikeId = ridden.getId();
         state.bikeUuid = ridden.getUUID();
         return ridden;
      }
      if (state.bikeId < 0) {
         return null;
      }
      Entity e = player.level().getEntity(state.bikeId);
      if (e instanceof HellCycleEntity bike && bike.isAlive() && bike.isOwner(player) && !bike.burning()) {
         return bike;
      }
      state.bikeId = -1;
      state.bikeUuid = null;
      return null;
   }

   static void dismissBike(ServerPlayer player, GhostRiderState state) {
      HellCycleEntity bike = bike(player, state);
      if (bike != null) {
         bike.burnOut();
         GhostRiderSounds.playAt(player, bike.position(), GhostRiderSounds.BIKE_BURNOUT, 1.0F, 1.0F);
      }
      state.bikeId = -1;
      state.bikeUuid = null;
   }

   // --- input ------------------------------------------------------------------------------------------------

   static void input(ServerPlayer player, GhostRiderState state, GhostRiderAbility ability) {
      state.idle = 0;
      if (!state.rider && !ability.human()) {
         refuse(player, "message.viltrumitecore.ghost_rider.need_rider");
         return;
      }
      if (state.cooldowns[ability.cooldown] > 0) {
         GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.6F, 1.0F);
         return;
      }
      if (state.running()) {
         GhostRiderMove m = state.move;
         if (m.locks() || !m.cancellable(state.elapsed) && m != GhostRiderMove.YANK) {
            GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.4F, 1.1F);
            return;
         }
         state.endMove();
      }
      switch (ability) {
         case CYCLE -> cycle(player, state);
         case TRANSFORM -> transform(player, state);
         case DASH -> dash(player, state);
         case WHIRL -> whirl(player, state);
         case WAVE -> wave(player, state);
         case STARE -> stare(player, state);
      }
   }

   static void refuse(ServerPlayer player, String key) {
      GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.7F, 1.0F);
      player.displayClientMessage(Component.translatable(key), true);
   }

   // --- Hell Cycle (R) ---------------------------------------------------------------------------------------

   static void cycle(ServerPlayer player, GhostRiderState state) {
      if (!state.rider) {
         // Johnny calls the Rider first; the bike forms when the skull is out
         state.summonAfterTransform = true;
         transform(player, state);
         return;
      }
      HellCycleEntity ridden = ridden(player);
      if (ridden != null) {
         GhostRiderCombat.releaseChain(player, state, ReleaseReason.NORMAL_END, false);
         dismissBike(player, state);
         state.cooldowns[GhostRiderRules.CD_CYCLE] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_CYCLE];
         return;
      }
      if (player.isPassenger()) {
         refuse(player, "message.viltrumitecore.ghost_rider.cycle_blocked");
         return;
      }
      HellCycleEntity parked = bike(player, state);
      if (parked != null && !parked.forming() && parked.distanceToSqr(player) <= 8.0 * 8.0 && !parked.isVehicle()) {
         if (player.startRiding(parked)) {
            GhostRiderSounds.play(player, GhostRiderSounds.MOUNT, 0.9F, 1.0F);
         }
         return;
      }
      if (parked != null) {
         parked.burnOut();
      }
      summon(player, state);
   }

   static void summon(ServerPlayer player, GhostRiderState state) {
      Vec3 at = summonSpot(player);
      if (at == null) {
         refuse(player, "message.viltrumitecore.ghost_rider.cycle_blocked");
         return;
      }
      HellCycleEntity bike = ViltrumiteEntities.HELL_CYCLE.get().create(player.level());
      if (bike == null) {
         return;
      }
      bike.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
      bike.setOwner(player);
      bike.mountWhenFormed(player);
      if (!player.level().addFreshEntity(bike)) {
         return;
      }
      state.bikeId = bike.getId();
      state.bikeUuid = bike.getUUID();
      state.cooldowns[GhostRiderRules.CD_CYCLE] = HellCycleRules.SUMMON_COOLDOWN;
      GhostRiderFx.send(player, GhostRiderFx.BIKE_FORM, at, null, 1.0F, null, 0, bike.getId());
      GhostRiderSounds.playAt(player, at, GhostRiderSounds.BIKE_FORM, 1.0F, 1.0F);
      GhostRiderSounds.playAt(player, at, GhostRiderSounds.IGNITION, 1.0F, 1.0F);
      HeroSpeech.ability(player, GhostRiderAbility.CYCLE.id());
   }

   /** A free spot for the bike: ahead of him, else beside, else where he stands (on the ground below, never in a wall). */
   @Nullable
   static Vec3 summonSpot(ServerPlayer player) {
      Vec3 flat = GhostRiderTargets.flat(player.getLookAngle());
      Vec3 right = new Vec3(-flat.z, 0, flat.x);
      Vec3[] tries = {flat.scale(1.6), right.scale(1.3), right.scale(-1.3), flat.scale(-1.6), Vec3.ZERO};
      float w = HellCycleRules.WIDTH / 2.0F;
      for (Vec3 o : tries) {
         Vec3 base = player.position().add(o);
         for (double dy = 0.5; dy >= -2.0; dy -= 0.5) {
            Vec3 p = base.add(0, dy, 0);
            AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + HellCycleRules.HEIGHT, p.z + w);
            AABB below = box.move(0, -0.1, 0);
            if (player.level().noCollision(box) && !player.level().noCollision(below)) {
               return p;
            }
         }
      }
      return null;
   }

   // --- transformation (Y) -----------------------------------------------------------------------------------

   static void transform(ServerPlayer player, GhostRiderState state) {
      if (state.cooldowns[GhostRiderRules.CD_TRANSFORM] > 0) {
         GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.6F, 1.0F);
         return;
      }
      if (state.rider) {
         GhostRiderCombat.releaseChain(player, state, ReleaseReason.INTERRUPTED, false);
         dismissBike(player, state);
         state.summonAfterTransform = false;
         state.start(GhostRiderMove.REVERT);
         GhostRiderSounds.play(player, GhostRiderSounds.REVERT, 1.0F, 1.0F);
      } else {
         state.start(GhostRiderMove.TRANSFORM);
         GhostRiderSounds.play(player, GhostRiderSounds.TRANSFORM, 1.0F, 1.0F);
         HeroSpeech.ability(player, GhostRiderAbility.TRANSFORM.id());
      }
      state.cooldowns[GhostRiderRules.CD_TRANSFORM] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_TRANSFORM];
   }

   static void flare(ServerPlayer player) {
      Vec3 chest = player.position().add(0, 1.2, 0);
      GhostRiderFx.send(player, GhostRiderFx.TRANSFORM, chest, null, 1.0F, null, 0, player.getId());
      GhostRiderSounds.play(player, GhostRiderSounds.SKULL_FIRE, 1.0F, 1.0F);
      for (LivingEntity t : GhostRiderTargets.around(player, chest, GhostRiderRules.FLARE_RADIUS,
         e -> e instanceof Enemy && GhostRiderTargets.mayAutoHit(player, e))) {
         GhostRiderTargets.push(t, GhostRiderTargets.outward(player.position(), t).scale(GhostRiderRules.FLARE_PUSH).add(0, 0.3, 0));
      }
   }

   // --- Hell Dash (Z) ----------------------------------------------------------------------------------------

   static void dash(ServerPlayer player, GhostRiderState state) {
      HellCycleEntity bike = ridden(player);
      if (bike != null) {
         if (bike.dashing() || bike.forming()) {
            GhostRiderSounds.play(player, GhostRiderSounds.REFUSE, 0.5F, 1.0F);
            return;
         }
         bike.startDash();
         state.cooldowns[GhostRiderRules.CD_DASH] = HellCycleRules.BOOST_COOLDOWN;
         GhostRiderSounds.playAt(player, bike.position(), GhostRiderSounds.BOOST, 1.2F, 1.0F);
         HeroSpeech.ability(player, GhostRiderAbility.DASH.id());
         return;
      }
      if (HeroDamage.isAnchored(player)) {
         return;
      }
      state.start(GhostRiderMove.DASH);
      Vec3 flat = GhostRiderTargets.flat(player.getLookAngle());
      state.moveDir = flat;
      state.waveOrigin = player.position();
      player.setDeltaMovement(flat.x * GhostRiderRules.DASH_IMPULSE, Math.max(0.1, player.getDeltaMovement().y), flat.z * GhostRiderRules.DASH_IMPULSE);
      player.hurtMarked = true;
      state.cooldowns[GhostRiderRules.CD_DASH] = GhostRiderRules.DASH_FOOT_COOLDOWN;
      GhostRiderFx.send(player, GhostRiderFx.DASH, player.position(), player.position().add(flat.scale(6.0)), 1.0F, null, 0, player.getId());
      GhostRiderSounds.play(player, GhostRiderSounds.DASH, 1.0F, GhostRiderSounds.jitter(player, 1.0F));
      HeroSpeech.ability(player, GhostRiderAbility.DASH.id());
   }

   static void dashTick(ServerPlayer player, GhostRiderState state, int e) {
      Vec3 from = state.waveOrigin;
      Vec3 to = player.position();
      for (LivingEntity t : GhostRiderTargets.along(player, from.add(0, 0.9, 0), to.add(0, 0.9, 0), GhostRiderRules.DASH_WIDTH,
         x -> GhostRiderTargets.mayAutoHit(player, x))) {
         if (state.hit.add(t.getId()) && GhostRiderTargets.strike(player, t, GhostRiderRules.DASH_DAMAGE)) {
            Hellfire.ignite(player, t, GhostRiderRules.DASH_BURN_TICKS);
            GhostRiderTargets.push(t, GhostRiderTargets.outward(to, t).scale(0.5).add(0, 0.3, 0));
         }
      }
      state.waveOrigin = to;
   }

   // --- Chain Whirl (V) --------------------------------------------------------------------------------------

   static void whirl(ServerPlayer player, GhostRiderState state) {
      LivingEntity held = GhostRiderCombat.chained(player, state);
      if (held != null && state.pulling && !state.towing) {
         // swing the hooked body round on the chain; it is smashed down by the slam
         state.chainTicks = 0;
      } else if (state.towing) {
         GhostRiderCombat.releaseChain(player, state, ReleaseReason.NORMAL_END, true);
      }
      state.start(GhostRiderMove.WHIRL);
      state.cooldowns[GhostRiderRules.CD_WHIRL] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_WHIRL];
      GhostRiderSounds.play(player, GhostRiderSounds.CHAIN_WHIRL, 1.0F, 1.0F);
      HeroSpeech.ability(player, GhostRiderAbility.WHIRL.id());
   }

   static void whirlTick(ServerPlayer player, GhostRiderState state, int e) {
      if (e % GhostRiderRules.WHIRL_HIT_INTERVAL != 4) {
         return;
      }
      Vec3 centre = player.position().add(0, 1.0, 0);
      for (LivingEntity t : GhostRiderTargets.around(player, centre, GhostRiderRules.WHIRL_RADIUS,
         x -> GhostRiderTargets.mayAutoHit(player, x) && x.getId() != state.chainId && x.getVehicle() != player)) {
         if (GhostRiderTargets.strike(player, t, GhostRiderRules.WHIRL_DAMAGE)) {
            Hellfire.ignite(player, t, GhostRiderRules.WHIRL_BURN_TICKS);
            GhostRiderTargets.push(t, GhostRiderTargets.outward(player.position(), t).scale(0.45).add(0, 0.2, 0));
         }
      }
   }

   static void slamContact(ServerPlayer player, GhostRiderState state) {
      Vec3 flat = GhostRiderTargets.flat(player.getLookAngle());
      HellCycleEntity bike = ridden(player);
      Vec3 base = bike != null ? bike.position() : player.position();
      Vec3 at = base.add(flat.scale(bike != null ? 1.5 : GhostRiderRules.SLAM_FORWARD));
      at = new Vec3(at.x, groundY(player, at), at.z);
      LivingEntity held = GhostRiderCombat.chained(player, state);
      if (held != null && state.pulling) {
         GhostRiderCombat.place(held, at.add(0, 0.1, 0));
         state.hit.add(held.getId());
         if (GhostRiderTargets.strike(player, held, GhostRiderRules.SLAM_DAMAGE + GhostRiderRules.SLAM_HELD_BONUS)) {
            Hellfire.ignite(player, held, GhostRiderRules.SLAM_BURN_TICKS);
         }
         GhostRiderCombat.releaseChain(player, state, ReleaseReason.NORMAL_END, false);
      }
      for (LivingEntity t : GhostRiderTargets.around(player, at, GhostRiderRules.SLAM_RADIUS, x -> GhostRiderTargets.mayAutoHit(player, x)
         && x.getVehicle() != player)) {
         if (!state.hit.add(t.getId())) {
            continue;
         }
         double d = Math.sqrt(t.getBoundingBox().distanceToSqr(at));
         if (GhostRiderTargets.strike(player, t, GhostRiderRules.slamDamage(d))) {
            Hellfire.ignite(player, t, GhostRiderRules.SLAM_BURN_TICKS);
            GhostRiderTargets.push(t, GhostRiderTargets.outward(at, t).scale(0.5).add(0, GhostRiderRules.SLAM_LIFT, 0));
         }
      }
      GhostRiderFx.send(player, GhostRiderFx.SLAM, at, GhostRiderFx.radius(at, GhostRiderRules.SLAM_RADIUS), 1.0F,
         player.level().getBlockState(net.minecraft.core.BlockPos.containing(at.x, at.y - 0.5, at.z)), 3);
      GhostRiderSounds.playAt(player, at, GhostRiderSounds.CHAIN_SLAM, 1.3F, GhostRiderSounds.jitter(player, 0.9F));
   }

   static double groundY(ServerPlayer player, Vec3 at) {
      Vec3 top = new Vec3(at.x, at.y + 1.5, at.z);
      Vec3 hit = GhostRiderTargets.clip(player, top, top.add(0, -5.0, 0));
      return hit.y;
   }

   // --- Hellfire Wave (B) ------------------------------------------------------------------------------------

   static void wave(ServerPlayer player, GhostRiderState state) {
      state.start(GhostRiderMove.WAVE);
      state.cooldowns[GhostRiderRules.CD_WAVE] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_WAVE];
      GhostRiderSounds.play(player, GhostRiderSounds.HELLFIRE_IGNITE, 1.0F, 0.8F);
      HeroSpeech.ability(player, GhostRiderAbility.WAVE.id());
   }

   static void waveTick(ServerPlayer player, GhostRiderState state, int e) {
      if (e == GhostRiderRules.WAVE_RELEASE) {
         Vec3 axis = GhostRiderTargets.flat(player.getLookAngle());
         state.waveAxis = axis;
         Vec3 origin = player.position().add(axis.scale(0.8));
         state.waveOrigin = new Vec3(origin.x, groundY(player, origin), origin.z);
         Vec3 end = state.waveOrigin.add(axis.scale(GhostRiderRules.WAVE_REACH));
         GhostRiderFx.send(player, GhostRiderFx.WAVE, state.waveOrigin, end, 1.0F, null, 0, player.getId());
         GhostRiderSounds.play(player, GhostRiderSounds.HELLFIRE_WAVE, 1.3F, 1.0F);
      }
      if (e < GhostRiderRules.WAVE_RELEASE) {
         return;
      }
      double front = (e - GhostRiderRules.WAVE_RELEASE + 1) * GhostRiderRules.WAVE_SPEED;
      Vec3 from = state.waveOrigin.add(0, 0.6, 0);
      for (LivingEntity t : GhostRiderTargets.arc(player, from, state.waveAxis, Math.min(front, GhostRiderRules.WAVE_REACH), GhostRiderRules.WAVE_ARC,
         x -> GhostRiderTargets.mayAutoHit(player, x) && x.getVehicle() != player && !state.hit.contains(x.getId())
            && GhostRiderTargets.visible(player.level(), from.add(0, 0.6, 0), GhostRiderTargets.center(x)))) {
         state.hit.add(t.getId());
         if (GhostRiderTargets.strike(player, t, GhostRiderRules.WAVE_DAMAGE)) {
            Hellfire.ignite(player, t, GhostRiderRules.WAVE_BURN_TICKS);
            GhostRiderTargets.push(t, state.waveAxis.scale(GhostRiderRules.WAVE_PUSH).add(0, 0.25, 0));
         }
      }
   }

   // --- Penance Stare (N) ------------------------------------------------------------------------------------

   static void stare(ServerPlayer player, GhostRiderState state) {
      if (ridden(player) != null || player.isPassenger()) {
         refuse(player, "message.viltrumitecore.ghost_rider.stare_on_bike");
         return;
      }
      LivingEntity target = stareTarget(player);
      if (target == null) {
         refuse(player, "message.viltrumitecore.ghost_rider.stare_no_target");
         return;
      }
      state.stareId = target.getId();
      state.stareUuid = target.getUUID();
      state.stareLostTicks = 0;
      state.stareEffect = null;
      if (HeroRegistry.allowsExternalControl(target, ControlKind.FREEZE) && !HeroDamage.isAnchored(target)) {
         UUID effect = UUID.randomUUID();
         if (ControlManager.get(player.serverLevel()).tryAcquire(target, player.getUUID(), effect, ControlKind.FREEZE,
            player.level().getGameTime() + GhostRiderRules.STARE_TICKS + 5)) {
            state.stareEffect = effect;
         }
      }
      state.start(GhostRiderMove.STARE);
      state.cooldowns[GhostRiderRules.CD_STARE] = GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_STARE];
      GhostRiderSounds.play(player, GhostRiderSounds.STARE, 1.2F, 1.0F);
      HeroSpeech.ability(player, GhostRiderAbility.STARE.id());
      if (target instanceof ServerPlayer victim) {
         victim.displayClientMessage(Component.translatable("message.viltrumitecore.ghost_rider.stared_at", player.getDisplayName()), true);
      }
   }

   /** The soul under the crosshair, else the best one in the narrow cone; always in sight. */
   @Nullable
   static LivingEntity stareTarget(ServerPlayer player) {
      LivingEntity aimed = GhostRiderCombat.aimed(player, GhostRiderRules.STARE_RANGE);
      if (aimed != null) {
         return aimed;
      }
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double cos = Math.cos(Math.toRadians(GhostRiderRules.STARE_CONE / 2.0));
      LivingEntity best = null;
      double bestDot = cos;
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GhostRiderRules.STARE_RANGE),
         e -> GhostRiderTargets.mayHit(player, e))) {
         Vec3 d = e.getEyePosition().subtract(eye);
         double len = d.length();
         if (len > GhostRiderRules.STARE_RANGE || len < 1.0E-3) {
            continue;
         }
         double dot = d.scale(1.0 / len).dot(look);
         if (dot > bestDot && GhostRiderTargets.visible(player.level(), eye, e.getEyePosition())) {
            bestDot = dot;
            best = e;
         }
      }
      return best;
   }

   static void stareTick(ServerPlayer player, GhostRiderState state, int e) {
      Entity entity = player.level().getEntity(state.stareId);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.distanceToSqr(player) > GhostRiderRules.STARE_BREAK_RANGE * GhostRiderRules.STARE_BREAK_RANGE) {
         endStare(player, state, true);
         return;
      }
      if (!GhostRiderTargets.visible(player.level(), player.getEyePosition(), target.getEyePosition())) {
         if (++state.stareLostTicks > 10) {
            endStare(player, state, true);
            return;
         }
      } else {
         state.stareLostTicks = 0;
      }
      if (target instanceof Mob mob) {
         mob.getLookControl().setLookAt(player, 90.0F, 90.0F);
         mob.getNavigation().stop();
      }
      if (e == GhostRiderRules.STARE_VERDICT) {
         verdict(player, state, target);
      }
   }

   static void verdict(ServerPlayer player, GhostRiderState state, LivingEntity target) {
      PenanceLedger ledger = PenanceLedger.get(player.getServer());
      PenanceLedger.Deeds deeds = ledger == null ? PenanceLedger.Deeds.NONE : ledger.deeds(target);
      int sins = deeds.sins();
      boolean boss = GhostRiderTargets.isBoss(target);
      float damage = GhostRiderRules.penanceDamage(sins, boss, target.getMaxHealth());
      state.lastSins = sins;
      state.lastInnocent = damage <= 0.0F;
      Component who = target.getDisplayName();
      if (damage <= 0.0F) {
         state.cooldowns[GhostRiderRules.CD_STARE] = GhostRiderRules.STARE_INNOCENT_COOLDOWN;
         GhostRiderFx.send(player, GhostRiderFx.VERDICT, target.getEyePosition(), player.getEyePosition(), 0.0F, null, 0, target.getId());
         GhostRiderSounds.playAt(player, target.getEyePosition(), GhostRiderSounds.INNOCENT, 1.0F, 1.0F);
         HeroSpeech.say(player, GhostRiderLines.INNOCENT);
         Component msg = Component.translatable("message.viltrumitecore.ghost_rider.verdict_innocent", who);
         player.displayClientMessage(msg, false);
         if (target instanceof ServerPlayer victim) {
            victim.displayClientMessage(msg, false);
         }
         return;
      }
      target.invulnerableTime = 0;
      boolean hurt = target.hurt(Hellfire.source(player.serverLevel(), Hellfire.PENANCE, player), damage);
      if (hurt) {
         Hellfire.ignite(player, target, 60);
      }
      GhostRiderFx.send(player, GhostRiderFx.VERDICT, target.getEyePosition(), player.getEyePosition(), 1.0F, null, 4, target.getId());
      GhostRiderSounds.playAt(player, target.getEyePosition(), GhostRiderSounds.VERDICT, 1.4F, 1.0F);
      HeroSpeech.say(player, GhostRiderLines.GUILTY);
      Component msg = Component.translatable("message.viltrumitecore.ghost_rider.verdict_guilty", who, deeds.playerKills(), deeds.innocentKills(),
         Math.round(deeds.harm()), sins, String.format(java.util.Locale.ROOT, "%.1f", damage));
      player.displayClientMessage(msg, false);
      if (target instanceof ServerPlayer victim) {
         victim.displayClientMessage(msg, false);
      }
   }

   static void endStare(ServerPlayer player, GhostRiderState state, boolean broken) {
      if (state.stareUuid != null && state.stareEffect != null) {
         ControlManager.get(player.serverLevel()).release(state.stareUuid, state.stareEffect, broken ? ReleaseReason.INTERRUPTED : ReleaseReason.NORMAL_END);
      }
      if (broken && state.is(GhostRiderMove.STARE) && state.elapsed < GhostRiderRules.STARE_VERDICT) {
         state.cooldowns[GhostRiderRules.CD_STARE] = GhostRiderRules.STARE_INNOCENT_COOLDOWN;
         player.displayClientMessage(Component.translatable("message.viltrumitecore.ghost_rider.stare_broken"), true);
      }
      state.stareId = -1;
      state.stareUuid = null;
      state.stareEffect = null;
      if (state.is(GhostRiderMove.STARE)) {
         state.endMove();
      }
   }

   // --- timeline hooks ---------------------------------------------------------------------------------------

   static void moveTick(ServerPlayer player, GhostRiderState state, GhostRiderMove move, int e) {
      switch (move) {
         case TRANSFORM -> {
            if (e == GhostRiderRules.TRANSFORM_FLARE_TICK) {
               becomeRider(player, state);
               flare(player);
            }
         }
         case DASH -> dashTick(player, state, e);
         case WHIRL -> whirlTick(player, state, e);
         case SLAM -> {
            if (e == move.contact) slamContact(player, state);
         }
         case WAVE -> waveTick(player, state, e);
         case STARE -> stareTick(player, state, e);
         default -> {
         }
      }
   }

   static void moveEnd(ServerPlayer player, GhostRiderState state, GhostRiderMove move) {
      state.endMove();
      switch (move) {
         case TRANSFORM -> {
            if (!state.rider) {
               becomeRider(player, state);
            }
            if (state.summonAfterTransform) {
               state.summonAfterTransform = false;
               summon(player, state);
            }
         }
         case REVERT -> becomeJohnny(player, state);
         case WHIRL -> state.start(GhostRiderMove.SLAM);
         case STARE -> endStare(player, state, false);
         default -> {
         }
      }
   }

   static void becomeRider(ServerPlayer player, GhostRiderState state) {
      if (state.rider) {
         return;
      }
      state.rider = true;
      GhostRiderHero.applyChassis(player);
      player.clearFire();
   }

   static void becomeJohnny(ServerPlayer player, GhostRiderState state) {
      if (!state.rider) {
         return;
      }
      state.rider = false;
      GhostRiderHero.removeChassis(player);
   }

   /** Stops everything running (cleanup, death, dimension change). The bike burns out when {@code bike}. */
   static void cleanup(ServerPlayer player, GhostRiderState state, boolean bike) {
      GhostRiderCombat.releaseChain(player, state, ReleaseReason.INTERRUPTED, false);
      if (state.stareId >= 0) {
         endStare(player, state, true);
      }
      state.endMove();
      state.summonAfterTransform = false;
      if (bike) {
         dismissBike(player, state);
      }
   }
}
