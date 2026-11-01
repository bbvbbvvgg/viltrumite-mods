package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Red Hulk's panel abilities (spec §5.3-5.8): Thermal Clap, Seismic Slam, Destruction Leap, Heat Blast,
 * Thermal Fury and the ultimate Critical Overheat, plus the forced overheat vent. Server only.
 */
public final class RedHulkSkills {
   /** Craters stay small: a slam or a landing tears a few blocks, the ultimate a little more (mobGriefing honoured). */
   static final HeroDebris.Eruption SEISMIC_CRATER = new HeroDebris.Eruption(2.6, 1.0, 0.0, 10, 0.35, 0.9);
   static final HeroDebris.Eruption LEAP_CRATER = new HeroDebris.Eruption(2.2, 1.0, 0.0, 8, 0.35, 0.8);
   static final HeroDebris.Eruption ULT_CRATER = new HeroDebris.Eruption(3.5, 1.5, 0.0, 22, 0.45, 1.1);

   private RedHulkSkills() {
   }

   // --- input -------------------------------------------------------------------------------------------------

   /** A panel key press. A running move is cut from its cancel tick; earlier the press waits for its end. */
   public static void input(ServerPlayer player, RedHulkState state, RedHulkAbility ability) {
      state.idle = 0;
      // second presses inside the move they started
      if (ability == RedHulkAbility.LEAP && state.is(RedHulkMove.LEAP_AIR)) {
         if (!state.leapDive && state.elapsed > 4) {
            state.leapDive = true;
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * 0.5, RedHulkRules.LEAP_DIVE, v.z * 0.5);
            player.hurtMarked = true;
            RedHulkSounds.play(player, RedHulkSounds.SWING_HEAVY, 1.0F, 0.6F);
         }
         return;
      }
      if (ability == RedHulkAbility.BLAST && state.is(RedHulkMove.BLAST) && state.elapsed >= RedHulkRules.BLAST_FIRE_START
         && state.elapsed < RedHulkRules.BLAST_FIRE_END) {
         state.elapsed = RedHulkRules.BLAST_FIRE_END; // close the beam early, go to the recovery
         return;
      }
      if (state.cooldowns[ability.cooldown] > 0 || !ready(state, ability)) {
         RedHulkSounds.play(player, RedHulkSounds.REFUSE, 0.6F, 1.0F);
         return;
      }
      if (state.running()) {
         RedHulkMove m = state.move;
         if (m.locks() || m == RedHulkMove.LEAP_AIR || m == RedHulkMove.THROW && state.elapsed < m.contact) {
            RedHulkSounds.play(player, RedHulkSounds.REFUSE, 0.6F, 1.0F);
            return;
         }
         boolean early = m.isCombo() ? state.elapsed < m.contact : !m.cancellable(state.elapsed);
         if (early && m != RedHulkMove.HOLD && m != RedHulkMove.STAGGER && m != RedHulkMove.GRAB) {
            state.queuedAbility = ability; // the wind-up of the current blow finishes first
            return;
         }
         if (state.holding()) {
            RedHulkCombat.release(player, state, ReleaseReason.INTERRUPTED);
         }
         RedHulkCombat.interrupt(player, state);
      }
      start(player, state, ability);
   }

   /** Heat requirements: Heat Blast from 300, Critical Overheat from 700, none while overheated. */
   static boolean ready(RedHulkState state, RedHulkAbility ability) {
      return switch (ability) {
         case BLAST -> RedHulkRules.canBlast(state.heat, state.overheated());
         case OVERHEAT -> RedHulkRules.canUlt(state.heat, state.overheated());
         case FURY -> !state.overheated() && !state.furious();
         default -> true;
      };
   }

   static void start(ServerPlayer player, RedHulkState state, RedHulkAbility ability) {
      state.cooldowns[ability.cooldown] = ability.cooldownTicks();
      state.moveDir = player.getLookAngle();
      switch (ability) {
         case CLAP -> {
            state.start(RedHulkMove.CLAP);
            RedHulkSounds.play(player, RedHulkSounds.SWING_HEAVY, 0.9F, 0.9F);
         }
         case SEISMIC -> {
            state.start(RedHulkMove.SEISMIC);
            RedHulkSounds.play(player, RedHulkSounds.SWING_HEAVY, 1.0F, 0.7F);
         }
         case LEAP -> {
            state.start(RedHulkMove.LEAP_CROUCH);
            state.leapDive = false;
         }
         case BLAST -> {
            state.start(RedHulkMove.BLAST);
            RedHulkSounds.play(player, RedHulkSounds.BLAST_CHARGE, 1.1F, 1.0F);
         }
         case FURY -> state.start(RedHulkMove.FURY);
         case OVERHEAT -> {
            state.start(RedHulkMove.ULT_CHARGE);
            HeroSpeech.ability(player, ability.id());
            RedHulkSounds.play(player, RedHulkSounds.ULT_CHARGE, 1.6F, 1.0F);
            RedHulkFx.send(player, RedHulkFx.ULT_CHARGE, chest(player), null, 1.0F, null, 0);
         }
      }
   }

   // --- timeline ----------------------------------------------------------------------------------------------

   /** Per-tick logic of the dynamic moves; true when the move was fully handled (its clock or a transition). */
   static boolean step(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      switch (move) {
         case LEAP_AIR -> {
            player.resetFallDistance();
            if (state.elapsed > 2 && (player.onGround() || player.isInWater())) {
               land(player, state);
            } else if (state.elapsed > RedHulkRules.LEAP_AIR_MAX) {
               state.endMove();
            }
            return true;
         }
         case BLAST -> {
            if (state.elapsed == RedHulkRules.BLAST_FIRE_START) {
               state.blastHeat = state.heat;
               state.heat = RedHulkRules.afterBlast(state.heat);
               RedHulkSounds.play(player, RedHulkSounds.BLAST_FIRE, RedHulkSounds.heatVolume(state.blastHeat, 1.4F), 1.0F);
            }
            if (RedHulkRules.blastPulse(state.elapsed)) {
               blastPulse(player, state);
            }
            return false;
         }
         case ULT_CHARGE -> {
            // the furnace climbs to critical; everything nearby is dragged towards it
            state.heat = Math.min(RedHulkRules.HEAT_MAX, state.heat + Math.max(4, (RedHulkRules.HEAT_MAX - state.heat) / 12));
            state.sinceHeat = 0;
            if (state.elapsed % 2 == 0) {
               Vec3 c = player.position();
               for (LivingEntity t : RedHulkTargets.around(player, c, RedHulkRules.ULT_RADIUS * 0.8, e -> RedHulkTargets.mayHit(player, e))) {
                  RedHulkTargets.push(t, RedHulkTargets.outward(c, t).scale(-RedHulkRules.ULT_PULL));
               }
            }
            return false;
         }
         case ULT_RECOVER -> {
            state.heat = 0;
            return false;
         }
         default -> {
            return false;
         }
      }
   }

   static void contact(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      switch (move) {
         case CLAP -> clap(player, state);
         case SEISMIC -> seismic(player, state);
         case FURY -> fury(player, state);
         case ULT_RELEASE -> ultRelease(player, state);
         case VENT -> vent(player, state);
         case ENTER -> {
            RedHulkSounds.play(player, RedHulkSounds.ENTER, 1.4F, 1.0F);
            RedHulkFx.send(player, RedHulkFx.FURY, chest(player), null, 0.6F, null, 0);
         }
         default -> {
         }
      }
   }

   /** End-of-move transitions; true when the next move of the chain was started. */
   static boolean chain(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      switch (move) {
         case LEAP_CROUCH -> {
            launch(player, state);
            return true;
         }
         case ULT_CHARGE -> {
            state.start(RedHulkMove.ULT_RELEASE);
            return true;
         }
         case ULT_RELEASE -> {
            state.start(RedHulkMove.ULT_RECOVER);
            RedHulkSounds.play(player, RedHulkSounds.ULT_RECOVER, 1.2F, 1.0F);
            return true;
         }
         case VENT -> {
            state.heat = Math.min(state.heat, RedHulkRules.HEAT_AFTER_VENT);
            state.sinceHeat = 0;
            return false;
         }
         default -> {
            return false;
         }
      }
   }

   // --- abilities ---------------------------------------------------------------------------------------------

   /** Thermal Clap: both palms meet, a cone of superheated air throws everything in front away and sets it alight. */
   static void clap(ServerPlayer player, RedHulkState state) {
      Vec3 hands = chest(player).add(RedHulkTargets.flat(player.getLookAngle()).scale(1.0));
      Vec3 look = player.getLookAngle();
      Vec3 flat = RedHulkTargets.flat(look);
      List<LivingEntity> targets = RedHulkTargets.arc(player, RedHulkRules.CLAP_REACH, RedHulkRules.CLAP_ARC, e -> RedHulkTargets.mayHit(player, e));
      int n = 0;
      for (LivingEntity t : targets) {
         if (!RedHulkTargets.visible(player.level(), player.getEyePosition(), RedHulkTargets.center(t))) {
            continue;
         }
         double d = Math.sqrt(t.distanceToSqr(player));
         float damage = RedHulkRules.falloff(d, RedHulkRules.CLAP_REACH, RedHulkRules.CLAP_DAMAGE, RedHulkRules.CLAP_DAMAGE * 0.6F);
         if (RedHulkTargets.strike(player, t, damage)) {
            n++;
            double k = 1.0 - 0.4 * Math.min(1.0, d / RedHulkRules.CLAP_REACH);
            RedHulkTargets.push(t, flat.scale(RedHulkRules.CLAP_PUSH * k).add(0, 0.3, 0));
            t.setSecondsOnFire(RedHulkRules.CLAP_IGNITE_SECONDS);
         }
      }
      // the wave turns projectiles in the cone around
      AABB box = player.getBoundingBox().inflate(RedHulkRules.CLAP_REACH);
      for (Projectile p : player.level().getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != player)) {
         Vec3 d = p.position().subtract(player.getEyePosition());
         if (d.length() <= RedHulkRules.CLAP_REACH && RedHulkTargets.flat(d).dot(flat) > Math.cos(Math.toRadians(RedHulkRules.CLAP_ARC / 2))) {
            p.setDeltaMovement(flat.scale(Math.max(0.6, p.getDeltaMovement().length())).add(0, 0.1, 0));
            p.hurtMarked = true;
         }
      }
      player.clearFire();
      state.addHeat(RedHulkRules.CLAP_HEAT + RedHulkRules.CLAP_HEAT_PER_TARGET * Math.min(4, n));
      RedHulkFx.send(player, RedHulkFx.CLAP, hands, hands.add(look.scale(RedHulkRules.CLAP_REACH)), RedHulkFx.scale(state.heat, 1.0F), null,
         n > 0 ? 2 : 0, player.getId());
      RedHulkSounds.playAt(player, hands, RedHulkSounds.CLAP, RedHulkSounds.heatVolume(state.heat, 1.5F), RedHulkSounds.heatPitch(state.heat, 1.0F));
   }

   /** Seismic Slam: both fists into the ground, the earth cracks in a ring and throws everyone up. */
   static void seismic(ServerPlayer player, RedHulkState state) {
      Vec3 c = player.position().add(RedHulkTargets.flat(player.getLookAngle()).scale(1.0));
      impact(player, state, c, RedHulkRules.SEISMIC_RADIUS, RedHulkRules.SEISMIC_DAMAGE, RedHulkRules.SEISMIC_EDGE_DAMAGE, RedHulkRules.SEISMIC_LIFT,
         RedHulkRules.SEISMIC_STUN);
      erupt(player, SEISMIC_CRATER);
      state.addHeat(RedHulkRules.SEISMIC_HEAT);
      RedHulkFx.send(player, RedHulkFx.SEISMIC, c, RedHulkFx.radius(c, RedHulkRules.SEISMIC_RADIUS), RedHulkFx.scale(state.heat, 1.0F), ground(player),
         3, player.getId());
      RedHulkSounds.playAt(player, c, RedHulkSounds.SEISMIC, RedHulkSounds.heatVolume(state.heat, 1.8F), RedHulkSounds.heatPitch(state.heat, 1.0F));
   }

   /** Destruction Leap take-off at the end of the crouch: a long arc the way he looks. */
   static void launch(ServerPlayer player, RedHulkState state) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = RedHulkTargets.flat(look);
      double up = RedHulkRules.LEAP_UP + Math.max(0.0, look.y) * 0.5;
      double forward = RedHulkRules.LEAP_FORWARD * (1.0 - Math.max(0.0, look.y) * 0.35);
      player.setDeltaMovement(flat.x * forward, up, flat.z * forward);
      player.hurtMarked = true;
      player.resetFallDistance();
      state.leapStartY = player.getY();
      state.start(RedHulkMove.LEAP_AIR);
      RedHulkFx.send(player, RedHulkFx.LEAP_LAUNCH, player.position(), null, RedHulkFx.scale(state.heat, 1.0F), ground(player), 0);
      RedHulkSounds.play(player, RedHulkSounds.LEAP_LAUNCH, 1.4F, 1.0F);
   }

   /** Destruction Leap landing: the higher the fall, the wider (up to x1.3) the shockwave. */
   static void land(ServerPlayer player, RedHulkState state) {
      double drop = Math.max(0.0, state.leapStartY - player.getY()) + (state.leapDive ? 4.0 : 0.0);
      double k = Math.min(1.3, 1.0 + drop / 30.0);
      state.start(RedHulkMove.LEAP_LAND);
      Vec3 c = player.position();
      double r = RedHulkRules.LEAP_RADIUS * k;
      impact(player, state, c, r, RedHulkRules.LEAP_DAMAGE * (float)k, RedHulkRules.LEAP_EDGE_DAMAGE, RedHulkRules.LEAP_LIFT, 8);
      erupt(player, LEAP_CRATER.scaled(k));
      state.addHeat(RedHulkRules.LEAP_HEAT);
      RedHulkFx.send(player, RedHulkFx.LEAP_LAND, c, RedHulkFx.radius(c, r), RedHulkFx.scale(state.heat, (float)k), ground(player), 3, player.getId());
      RedHulkSounds.play(player, RedHulkSounds.LEAP_LAND, RedHulkSounds.heatVolume(state.heat, 1.8F), RedHulkSounds.heatPitch(state.heat, 1.0F));
      player.resetFallDistance();
   }

   /** One Heat Blast pulse: a beam from the chest along the aim, through up to five bodies, stopped by blocks. */
   static void blastPulse(ServerPlayer player, RedHulkState state) {
      Vec3 from = chest(player);
      Vec3 to = RedHulkTargets.clip(player, from, from.add(player.getLookAngle().scale(RedHulkRules.BLAST_RANGE)));
      float damage = RedHulkRules.blastPulseDamage(state.blastHeat);
      int n = 0;
      Vec3 dir = to.subtract(from).normalize();
      for (LivingEntity t : RedHulkTargets.along(player, from, to, RedHulkRules.BLAST_WIDTH, e -> RedHulkTargets.mayHit(player, e))) {
         if (n >= RedHulkRules.BLAST_MAX_TARGETS) {
            break;
         }
         if (RedHulkTargets.strike(player, t, damage)) {
            n++;
            t.setSecondsOnFire(RedHulkRules.BLAST_IGNITE_SECONDS);
            RedHulkTargets.push(t, dir.scale(0.15));
         }
      }
      RedHulkFx.send(player, RedHulkFx.BLAST, from, to, RedHulkFx.scale(state.blastHeat, 1.0F), null, 0);
   }

   /** Thermal Fury: the roar at the contact; the buff runs for {@link RedHulkRules#FURY_TICKS}. */
   static void fury(ServerPlayer player, RedHulkState state) {
      state.fury = RedHulkRules.FURY_TICKS;
      state.addHeat(RedHulkRules.FURY_HEAT);
      Vec3 c = player.position();
      for (LivingEntity t : RedHulkTargets.around(player, c, RedHulkRules.FURY_ROAR_RADIUS, e -> RedHulkTargets.mayHit(player, e))) {
         t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RedHulkRules.FURY_WEAKNESS_TICKS, 0, false, true, true), player);
         RedHulkTargets.push(t, RedHulkTargets.outward(c, t).scale(0.6).add(0, 0.15, 0));
      }
      HeroSpeech.ability(player, RedHulkAbility.FURY.id());
      RedHulkFx.send(player, RedHulkFx.FURY, chest(player), null, RedHulkFx.scale(state.heat, 1.0F), null, 0);
      RedHulkSounds.play(player, RedHulkSounds.FURY_ROAR, 2.0F, 1.0F);
   }

   /** Critical Overheat: the body bursts, a burning shockwave levels the area; the heat is spent at once. */
   static void ultRelease(ServerPlayer player, RedHulkState state) {
      Vec3 c = player.position();
      double r = RedHulkRules.ULT_RADIUS;
      for (LivingEntity t : RedHulkTargets.around(player, c.add(0, 1.0, 0), r, e -> RedHulkTargets.mayHit(player, e))) {
         double d = Math.sqrt(t.distanceToSqr(player));
         if (RedHulkTargets.strike(player, t, RedHulkRules.falloff(d, r, RedHulkRules.ULT_DAMAGE, RedHulkRules.ULT_EDGE_DAMAGE))) {
            t.setSecondsOnFire(RedHulkRules.ULT_IGNITE_SECONDS);
            double k = 1.0 - 0.5 * Math.min(1.0, d / r);
            RedHulkTargets.push(t, RedHulkTargets.outward(c, t).scale(RedHulkRules.ULT_PUSH * k).add(0, RedHulkRules.ULT_LIFT * k, 0));
         }
      }
      erupt(player, ULT_CRATER);
      state.heat = 0;
      state.fury = 0;
      Vec3 at = chest(player);
      RedHulkFx.send(player, RedHulkFx.ULT_RELEASE, at, RedHulkFx.radius(at, r), 1.0F, ground(player), 5, player.getId());
      RedHulkSounds.play(player, RedHulkSounds.ULT_RELEASE, 3.0F, 1.0F);
   }

   /** Overheat: the body vents a scalding burst around him; heat drops to {@link RedHulkRules#HEAT_AFTER_VENT} at the end. */
   static void vent(ServerPlayer player, RedHulkState state) {
      Vec3 c = player.position();
      for (LivingEntity t : RedHulkTargets.around(player, c.add(0, 1.0, 0), RedHulkRules.VENT_RADIUS, e -> RedHulkTargets.mayHit(player, e))) {
         if (RedHulkTargets.strike(player, t, RedHulkRules.VENT_DAMAGE)) {
            RedHulkTargets.push(t, RedHulkTargets.outward(c, t).scale(0.8).add(0, 0.25, 0));
         }
      }
      RedHulkFx.send(player, RedHulkFx.VENT, chest(player), RedHulkFx.radius(chest(player), RedHulkRules.VENT_RADIUS), 1.0F, null, 0);
      RedHulkSounds.play(player, RedHulkSounds.VENT, 1.6F, 1.0F);
   }

   /** Ring damage with a falloff, a launch and a short stun (slam, leap landing). */
   static void impact(ServerPlayer player, RedHulkState state, Vec3 c, double r, float center, float edge, double lift, int stun) {
      for (LivingEntity t : RedHulkTargets.around(player, c, r, e -> RedHulkTargets.mayHit(player, e))) {
         double d = Math.sqrt(t.distanceToSqr(c));
         if (!RedHulkTargets.strike(player, t, RedHulkRules.falloff(d, r, center, edge))) {
            continue;
         }
         double k = 1.0 - 0.4 * Math.min(1.0, d / r);
         RedHulkTargets.push(t, RedHulkTargets.outward(c, t).scale(0.4 * k).add(0, lift * k, 0));
         if (stun > 0) {
            RedHulkTargets.stun(player, t, stun);
         }
         if (state.heat >= RedHulkRules.BLAZING) {
            t.setSecondsOnFire(2);
         }
      }
   }

   static void erupt(ServerPlayer player, HeroDebris.Eruption spec) {
      if (!player.onGround()) {
         return;
      }
      HeroDebris.erupt(player, player.getOnPos(), RedHulkTargets.flat(player.getLookAngle()), spec, 2.0F, e -> !RedHulkTargets.mayHit(player, e));
   }

   static BlockState ground(ServerPlayer player) {
      BlockPos below = player.getOnPos();
      BlockState state = player.level().getBlockState(below);
      return state.isAir() ? null : state;
   }

   static Vec3 chest(ServerPlayer player) {
      return player.position().add(0, player.getBbHeight() * 0.68, 0);
   }

   // --- forced moves ------------------------------------------------------------------------------------------

   /** The furnace hit 1000: lock the heat abilities, vent (every running move but the ultimate ends). */
   static void overheat(ServerPlayer player, RedHulkState state) {
      state.overheat = RedHulkRules.OVERHEAT_TICKS;
      state.fury = 0;
      if (state.holding()) {
         RedHulkCombat.release(player, state, ReleaseReason.INTERRUPTED);
      }
      state.queuedAbility = null;
      state.comboQueued = false;
      state.start(RedHulkMove.VENT);
      RedHulkSounds.play(player, RedHulkSounds.OVERHEAT, 1.5F, 1.0F);
      HeroSpeech.say(player, RedHulkLines.OVERHEATED);
      HeroFx.flash(player, chest(player), 0.6F);
   }

   /** A plain fall: a heavy landing pose, a small shockwave from high up. */
   static void onLanded(ServerPlayer player, RedHulkState state, float fallDistance) {
      if (state.is(RedHulkMove.LEAP_AIR)) {
         land(player, state);
         return;
      }
      if (fallDistance < RedHulkRules.LANDING_FALL || state.running()) {
         return;
      }
      state.start(RedHulkMove.LANDING);
      Vec3 c = player.position();
      if (fallDistance >= RedHulkRules.LANDING_SHOCK_FALL) {
         impact(player, state, c, RedHulkRules.LANDING_SHOCK_RADIUS, RedHulkRules.LANDING_SHOCK_DAMAGE, RedHulkRules.LANDING_SHOCK_DAMAGE * 0.5F, 0.35, 0);
      }
      float k = Math.min(1.6F, 0.7F + fallDistance / 16.0F);
      RedHulkFx.send(player, RedHulkFx.LANDING, c, RedHulkFx.radius(c, 1.5 * k), k, ground(player), 0);
      RedHulkSounds.play(player, RedHulkSounds.LANDING, Math.min(1.6F, 0.8F + fallDistance / 20.0F), 1.0F);
   }

   /** Hero change, death, dimension change: let go, end every move. */
   static void cleanup(ServerPlayer player, RedHulkState state) {
      RedHulkCombat.release(player, state, ReleaseReason.INTERRUPTED);
      state.thrownId = -1;
      state.queuedAbility = null;
      state.comboQueued = false;
      state.endMove();
   }
}
