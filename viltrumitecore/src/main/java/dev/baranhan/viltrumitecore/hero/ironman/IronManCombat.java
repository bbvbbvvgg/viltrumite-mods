package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.entity.MicroMissileEntity;
import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.combat.HammerLaunch;
import dev.baranhan.viltrumitecore.hero.ironman.combat.MissileLock;
import dev.baranhan.viltrumitecore.hero.ironman.combat.Overdraft;
import dev.baranhan.viltrumitecore.hero.ironman.combat.Reflect;
import dev.baranhan.viltrumitecore.hero.ironman.combat.Repulsor;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import dev.baranhan.viltrumitecore.hero.ironman.combat.Shield;
import dev.baranhan.viltrumitecore.hero.ironman.combat.ToolCycle;
import dev.baranhan.viltrumitecore.hero.ironman.combat.UnibeamTimeline;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket;
import dev.baranhan.viltrumiteflight.util.FlightState;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Stage 2 nano combat kit (spec §8, §9): drives the pure
 * timelines in {@code combat/} and applies them to the world. Every entry
 * point assumes an Iron Man player; input methods check the suit themselves.
 */
public final class IronManCombat {
   private IronManCombat() {
   }

   // ---- geometry ----

   static Vec3 right(Vec3 look) {
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
      return right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
   }

   /** Palm in front of the shoulder, where the bolt leaves. */
   public static Vec3 hand(ServerPlayer player, boolean rightHand) {
      Vec3 look = player.getLookAngle();
      Vec3 side = right(look).scale(rightHand ? 0.38 : -0.38);
      return player.getEyePosition().add(0.0, -0.35, 0.0).add(side).add(look.scale(0.7));
   }

   /** Outer forearm launcher muzzle with the arm raised along the look (IronManPoser missile stance). */
   public static Vec3 forearm(ServerPlayer player, boolean rightHand) {
      Vec3 look = player.getLookAngle();
      Vec3 side = right(look).scale(rightHand ? 0.46 : -0.46);
      return player.getEyePosition().add(0.0, -0.32, 0.0).add(side).add(look.scale(0.62));
   }

   /** Arc reactor: the Unibeam origin. */
   static Vec3 chest(ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      double height = player.isCrouching() ? 1.05 : 1.32;
      return player.position().add(0.0, height, 0.0).add(look.x * 0.3, 0.0, look.z * 0.3);
   }

   /** Crosshair point: first block or entity along the look within range. */
   public static Vec3 aimPoint(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 to = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, to,
         new AABB(eye, to).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && e != player && !e.isSpectator(), eye.distanceToSqr(to));
      return entity != null ? entity.getLocation() : to;
   }

   @Nullable
   public static LivingEntity entityInReach(ServerPlayer player, double reach) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(reach));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 to = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, to,
         new AABB(eye, to).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && e != player && !e.isSpectator(), eye.distanceToSqr(to));
      return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
   }

   public static void push(Entity target, Vec3 velocity) {
      if (target instanceof LivingEntity living && !HeroRegistry.allowsImpulse(living)) {
         return;
      }

      target.setDeltaMovement(target.getDeltaMovement().add(velocity));
      target.hurtMarked = true;
   }

   public static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
      IronManSounds.play(player, sound, 1.0F, pitch);
   }

   static void flash(ServerPlayer player, float strength, int ticks) {
      CoreMessages.sendToPlayer(new FlashS2CPacket(strength, ticks), player);
   }

   // ---- repulsors (§8.1) ----

   static void fire(ServerPlayer player, IronManState state, Repulsor.Shot shot) {
      if (shot.kind() == Repulsor.Kind.FIZZLE) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 1.0F);
         return;
      }

      if (!shot.fired()) {
         return;
      }

      Vec3 aim = aimPoint(player, IronManRules.REPULSOR_RANGE);
      // Mark 42: while the rocket fist is away only the left palm fires (spec §13.3).
      boolean rightAway = state.suit.markWorn() && state.suit.mark() == dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId.MARK_42 && state.signature.entityId >= 0;
      boolean volley = shot.kind() == Repulsor.Kind.VOLLEY && !rightAway;
      double knockback = Repulsor.knockback(shot.power());
      for (boolean rightHand : volley ? new boolean[]{true, false} : new boolean[]{shot.rightHand() && !rightAway}) {
         Vec3 from = hand(player, rightHand);
         Vec3 dir = aim.subtract(from);
         dir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : dir.normalize();
         RepulsorBlastEntity.shoot(player, from, dir, volley ? shot.damage() / 2.0F : shot.damage(), shot.power(), knockback);
      }

      sound(player, volley ? IronManCombatSounds.REPULSOR_VOLLEY.get() : IronManCombatSounds.REPULSOR_SHOT.get(), 0.9F + player.getRandom().nextFloat() * 0.2F);
      state.recoilTicks = IronManRules.REPULSOR_RECOIL_TICKS;
      state.recoilRight = volley || shot.rightHand() && !rightAway;
      // Movement: brake backwards in flight, lift when firing down in a hover, ground shockwave.
      FlightState flight = IronManHero.flightState(player);
      boolean flying = flight != null && flight != FlightState.NONE;
      Vec3 look = player.getLookAngle();
      Vec3 velocity = player.getDeltaMovement();
      double speed = velocity.length();
      // Heavy marks barely move from their own shots (spec §13.8: recoil ×0.2).
      double recoil = state.spec().recoilMul();
      if (Repulsor.brakes(flying, speed < 1.0E-4 ? 0.0 : look.dot(velocity.scale(1.0 / speed)), speed)) {
         player.setDeltaMovement(velocity.scale(Mth.lerp(recoil, 1.0, Repulsor.brakeFactor(shot.power()))));
         player.hurtMarked = true;
      } else if (Repulsor.lifts(flight == FlightState.HOVER, player.getXRot())) {
         player.setDeltaMovement(velocity.x, Math.max(velocity.y, Repulsor.liftVelocity(shot.power()) * recoil), velocity.z);
         player.hurtMarked = true;
      }

      if (Repulsor.groundShockwave(player.onGround(), shot.kind(), player.getXRot())) {
         groundShockwave(player);
      }
   }

   private static void groundShockwave(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      BlockState below = level.getBlockState(player.blockPosition().below());
      HeroFx.shockwave(player, player.position(), 1.0F, below, (float)IronManRules.REPULSOR_SHOCKWAVE_RADIUS);
      double r = IronManRules.REPULSOR_SHOCKWAVE_RADIUS;
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 1.5, r), e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         Vec3 away = target.position().subtract(player.position());
         double distance = away.horizontalDistance();
         if (distance > r) {
            continue;
         }

         if (target.hurt(player.damageSources().playerAttack(player), IronManRules.REPULSOR_SHOCKWAVE_DAMAGE)) {
            Vec3 out = distance < 1.0E-3 ? Vec3.ZERO : new Vec3(away.x / distance, 0.0, away.z / distance);
            push(target, out.scale(IronManRules.REPULSOR_SHOCKWAVE_KNOCKBACK).add(0.0, 0.5, 0.0));
         }
      }
   }

   // ---- Unibeam and overdraft (§8.2, §9) ----

   static void unibeamPress(ServerPlayer player, IronManState state) {
      // Mark 42 without its chest plate has no Unibeam (spec §13.3).
      if (!state.spec().unibeamOnline()) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
         return;
      }

      if (state.unibeam.press(state.energy.weaponsLocked(), state.spec().unibeamCharge())) {
         sound(player, IronManCombatSounds.UNIBEAM_CHARGE.get(), 1.0F);
      } else if (state.energy.weaponsLocked() || state.unibeam.overheated()) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
      }
   }

   static void unibeamRelease(ServerPlayer player, IronManState state) {
      if (state.overdraft.active()) {
         // §9.3: letting go early kills the beam, the core still blows (weaker).
         state.overdraft.release();
      }

      if (state.unibeam.release()) {
         beamEnded(player, state);
      }
   }

   /** A finished beam always overheats and counts (spec §8.2, §9.1). */
   private static void beamEnded(ServerPlayer player, IronManState state) {
      state.beamDir = null;
      state.beamEnd = null;
      if (state.overdraft.active()) {
         return;
      }

      state.overheat.add();
      sound(player, IronManCombatSounds.UNIBEAM_OVERHEAT.get(), 1.0F);
      // The second-overheat warning is a JARVIS line on the owner's client (helmet closed, JarvisVoice).
   }

   static void unibeamTick(ServerPlayer player, IronManState state) {
      UnibeamTimeline.Event event = state.unibeam.tick();
      switch (event) {
         case CHARGED -> {
            boolean overdraft = state.overheat.nextIsOverdraft();
            if (state.energy.spend(IronManRules.COST_UNIBEAM)) {
               state.unibeam.startBeam(overdraft);
               state.beamDir = player.getLookAngle();
               flash(player, overdraft ? 0.35F : 0.18F, IronManRules.FLASH_OWNER_TICKS);
               if (overdraft) {
                  // JARVIS line on the owner's client (JarvisVoice); the HUD shows it always.
                  state.overdraft.start();
               }
            } else {
               state.unibeam.refuse();
               sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
            }
         }
         case BEAM_MAX -> beamEnded(player, state);
         default -> {
         }
      }

      if (state.unibeam.phase() == UnibeamTimeline.Phase.BEAM) {
         state.beamDir = UnibeamTimeline.turn(state.beamDir == null ? player.getLookAngle() : state.beamDir, player.getLookAngle(), IronManRules.UNIBEAM_TURN_DEG);
         beamTick(player, state);
      }

      Overdraft.Event core = state.overdraft.tick();
      if (core == Overdraft.Event.SPUTTER) {
         sound(player, IronManCombatSounds.OVERDRAFT_SPUTTER.get(), 1.0F);
      } else if (core == Overdraft.Event.EXPLODE) {
         coreExplosion(player, state);
      }

      if (state.coreExplosionWindow > 0) {
         state.coreExplosionWindow--;
      }
   }

   private static void beamTick(ServerPlayer player, IronManState state) {
      ServerLevel level = player.serverLevel();
      Vec3 from = chest(player);
      Vec3 dir = state.beamDir;
      Vec3 end = from.add(dir.scale(IronManRules.UNIBEAM_RANGE));
      BlockHitResult block = null;
      // Glass and leaves give way (up to 3 per tick); everything else stops the beam and only scorches.
      for (int i = 0; i < 4; i++) {
         block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
         if (block.getType() != HitResult.Type.BLOCK || i == 3) {
            break;
         }

         BlockPos pos = block.getBlockPos();
         Set<String> tags = new HashSet<>();
         level.getBlockState(pos).getTags().forEach(tag -> tags.add(tag.location().toString()));
         if (!UnibeamTimeline.breaksBlock(tags) || !HeroDestruction.canDestroy(level, pos) || !HeroDestruction.destroyBlock(level, pos)) {
            break;
         }
      }

      Vec3 hitEnd = block != null && block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
      state.beamEnd = hitEnd;
      boolean overdraft = state.unibeam.overdraft();
      if (!UnibeamTimeline.damageTick(state.unibeam.ticks())) {
         return;
      }

      double radius = IronManRules.UNIBEAM_RADIUS;
      Vec3 segment = hitEnd.subtract(from);
      double length = segment.length();
      AABB box = new AABB(from, hitEnd).inflate(radius + 1.0);
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         Vec3 center = target.getBoundingBox().getCenter();
         double t = length < 1.0E-3 ? 0.0 : Mth.clamp(center.subtract(from).dot(segment) / (length * length), 0.0, 1.0);
         Vec3 closest = from.add(segment.scale(t));
         double reach = radius + target.getBbWidth() / 2.0 + target.getBbHeight() / 4.0;
         if (closest.distanceTo(center) > reach) {
            continue;
         }

         target.invulnerableTime = 0;
         if (target.hurt(player.damageSources().playerAttack(player), UnibeamTimeline.damageAt(from.distanceTo(center), overdraft))) {
            push(target, dir.scale(IronManRules.UNIBEAM_PUSH));
            if (target instanceof Mob mob) {
               mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, IronManRules.UNIBEAM_MOB_BLIND_TICKS, 0, false, false));
               mob.setTarget(null);
            }

            // Only a target the beam really hurt is dazzled (no flash through PvP off / teams).
            if (target instanceof ServerPlayer victim) {
               flash(victim, overdraft ? 1.0F : 0.9F, UnibeamTimeline.flashTicks(IronManRules.FLASH_BEAM_TICKS, true));
            }
         }
      }

      // Players near the source looking into the beam get a shorter flash.
      for (ServerPlayer other : level.players()) {
         if (other == player || other.isSpectator()) {
            continue;
         }

         Vec3 toSource = from.subtract(other.getEyePosition());
         if (UnibeamTimeline.looksInto(other.getLookAngle(), toSource, toSource.length()) && seesPoint(other, from)) {
            flash(other, overdraft ? 0.8F : 0.6F, UnibeamTimeline.flashTicks(IronManRules.FLASH_LOOK_TICKS, true));
         }
      }
   }

   /** Line of sight from the eyes to a point (no flash through walls). */
   static boolean seesPoint(ServerPlayer viewer, Vec3 point) {
      Vec3 eye = viewer.getEyePosition();
      return viewer.level().clip(new net.minecraft.world.level.ClipContext(eye, point, net.minecraft.world.level.ClipContext.Block.VISUAL,
         net.minecraft.world.level.ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
   }

   /** §9.4: the core blows, the player survives on ~2 hearts and loses the nano suit for 30 s. */
   static void coreExplosion(ServerPlayer player, IronManState state) {
      ServerLevel level = player.serverLevel();
      Vec3 at = player.position().add(0.0, 1.0, 0.0);
      float power = state.overdraft.power();
      state.coreExplosionWindow = IronManRules.CORE_EXPLOSION_WINDOW;
      state.unibeam.clear();
      state.beamDir = null;
      state.beamEnd = null;
      sound(player, IronManCombatSounds.CORE_EXPLOSION.get(), 1.0F);
      level.explode(player, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
      double r = IronManRules.CORE_EXTRA_KNOCKBACK_RADIUS;
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r), e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         Vec3 away = target.position().subtract(player.position());
         double distance = away.length();
         if (distance > r || distance < 1.0E-3) {
            continue;
         }

         push(target, away.scale(1.0 / distance).scale(IronManRules.CORE_EXTRA_KNOCKBACK * (1.0 - distance / r)).add(0.0, 0.4, 0.0));
      }

      Vec3 back = player.getLookAngle().scale(-1.0);
      player.setDeltaMovement(new Vec3(back.x, 0.0, back.z).normalize().scale(IronManRules.CORE_SELF_LAUNCH).add(0.0, 0.8, 0.0));
      player.hurtMarked = true;
      HeroFx.shockwave(player, player.position(), 1.0F, null, (float)r);
      for (ServerPlayer other : level.players()) {
         double distance = other.distanceTo(player);
         if (other == player) {
            flash(other, 0.9F, 20);
         } else if (distance < 32.0 && seesPoint(other, at)) {
            flash(other, (float)(1.0 - distance / 32.0), UnibeamTimeline.flashTicks(30, true));
         }
      }

      if (state.suit.markOn()) {
         // Spec §9.4: a mark falls apart like a break and Tony gets the nano; no nano lock then.
         dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks.breakMark(player, state);
      } else {
         state.suit.scatter(IronManRules.NANO_LOST_TICKS);
      }

      state.overheat.resetByExplosion();
      state.stopCombat();
      sound(player, IronManCombatSounds.NANITE_DISSOLVE.get(), 0.7F);
   }

   /** clampFinalDamage seam: own core explosion leaves the player on ~2 hearts. */
   static float clampCoreExplosion(ServerPlayer self, IronManState state, DamageSource source, float afterArmor) {
      boolean ownCore = Overdraft.isOwnCoreExplosion(source.is(DamageTypeTags.IS_EXPLOSION), source.getEntity() == self, state.coreExplosionWindow);
      return ownCore ? Overdraft.survivalClamp(self.getHealth(), afterArmor) : afterArmor;
   }

   // ---- missiles (§8.3) ----

   static void missilesPress(ServerPlayer player, IronManState state) {
      if (!state.missiles.press(state.energy.weaponsLocked(), state.spec().missileMarks())) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
      }
   }

   static void missilesTick(ServerPlayer player, IronManState state) {
      if (!state.missiles.held()) {
         return;
      }

      state.missiles.tick();
      if (state.missiles.ticks() == IronManRules.MISSILE_FLAPS) {
         sound(player, IronManCombatSounds.MISSILE_FLAPS.get(), 1.0F);
      }

      ServerLevel level = player.serverLevel();
      for (int id : state.missiles.marks()) {
         Entity marked = level.getEntity(id);
         if (marked == null || !marked.isAlive()) {
            state.missiles.drop(id);
         }
      }

      // Spec §10: marks need the JARVIS targeting (helmet closed), also against forged input.
      if (state.missiles.flapsOpen() && state.canMarkTargets()) {
         Vec3 eye = player.getEyePosition();
         Vec3 look = player.getLookAngle();
         double range = IronManRules.MISSILE_LOCK_RANGE;
         AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(4.0);
         for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
            if (MissileLock.inCone(eye, look, target.getBoundingBox().getCenter()) && state.missiles.offer(target.getId(), player.hasLineOfSight(target))) {
               sound(player, IronManCombatSounds.SHIELD_OPEN.get(), 2.0F);
            }
         }
      }

      pushMarks(player, state.missiles.marks());
   }

   static void pushMarks(ServerPlayer player, List<Integer> marks) {
      int[] ids = marks.stream().mapToInt(Integer::intValue).toArray();
      HeroRegistry.pushOwnerSection(player, OwnerSection.MARKS, HeroOwnerSnapshot.Section.of(ids));
   }

   static void missilesRelease(ServerPlayer player, IronManState state) {
      MissileLock.Volley volley = state.missiles.release();
      pushMarks(player, List.of());
      if (!volley.fire()) {
         return;
      }

      if (state.energy.weaponsLocked() || !state.energy.spend(IronManRules.COST_MISSILES)) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
         return;
      }

      ServerLevel level = player.serverLevel();
      List<Integer> targets = volley.targets();
      if (targets.isEmpty()) {
         // Spec §11.1: JARVIS auto-lock on the nearest threat (helmet closed, missiles only).
         Integer auto = IronManJarvis.autoLock(player, state);
         if (auto != null) {
            targets = List.of(auto);
         }
      }

      int count = MissileLock.missileCount(targets.size(), state.spec().missileMarks());
      float damageMul = state.spec().missileMul();
      Vec3 look = player.getLookAngle();
      for (int i = 0; i < count; i++) {
         Entity target = targets.isEmpty() ? null : level.getEntity(targets.get(i % targets.size()));
         float offset = MissileLock.fanOffset(i, count);
         Vec3 dir = look.yRot((float)Math.toRadians(-offset)).normalize();
         // From the forearm launchers (arms aimed along the look), alternating sides.
         Vec3 from = forearm(player, i % 2 == 0);
         MicroMissileEntity.launch(player, from, dir, target, damageMul);
      }

      sound(player, IronManCombatSounds.MISSILE_LAUNCH.get(), 1.0F);
   }

   // ---- nano arsenal (§8.4) ----

   static void arsenalPress(ServerPlayer player, IronManState state) {
      RightTool formed = state.arsenal.press(state.energy);
      if (formed == null) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
         return;
      }

      state.rightTool = formed;
      state.hammerCharge = -1;
      sound(player, IronManCombatSounds.NANITE_FORM.get(), formed == RightTool.NANO_HAMMER ? 0.85F : 1.1F);
   }

   static void toolCycle(ServerPlayer player, IronManState state) {
      if (state.heldTool != null) {
         return;
      }

      // A mark passes its signature as the RMB alternative when it has one (spec §6.1).
      RightTool alternative = state.suit.markWorn() && dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignatures.of(state.suit.mark()).onRmb()
         ? RightTool.SIGNATURE : null;
      RightTool next = ToolCycle.next(state.rightTool, state.arsenal.weapon(), alternative);
      if (next == state.rightTool) {
         return;
      }

      if (next == RightTool.REPULSOR && state.rightTool.nanoWeapon() && state.arsenal.dissolve()) {
         sound(player, IronManCombatSounds.NANITE_DISSOLVE.get(), 1.0F);
      }

      state.rightTool = next;
   }

   /** LMB with a formed weapon. */
   static void strike(ServerPlayer player, IronManState state) {
      RightTool weapon = state.arsenal.weapon();
      if (weapon == null || state.arsenal.forming() || state.strikeTicks > 0 || state.energy.weaponsLocked()) {
         return;
      }

      if (weapon == RightTool.NANO_BLADE) {
         state.strikeTicks = state.strikeLength = IronManRules.BLADE_SWING_TICKS;
         bladeSlash(player);
      } else {
         state.strikeTicks = state.strikeLength = IronManRules.HAMMER_SWING_TICKS;
         hammerHit(player);
      }
   }

   private static void bladeSlash(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double reach = IronManRules.BLADE_REACH;
      double cos = Math.cos(Math.toRadians(IronManRules.BLADE_ARC_DEG / 2.0F));
      sound(player, IronManCombatSounds.BLADE_SLASH.get(), 0.9F + player.getRandom().nextFloat() * 0.2F);
      HeroFx.blade(player, eye.add(look.scale(0.6)), eye.add(look.scale(reach)), null);
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
         double distance = to.length();
         if (distance > reach + target.getBbWidth() / 2.0 || distance < 1.0E-3 || look.dot(to.scale(1.0 / distance)) < cos || !player.hasLineOfSight(target)) {
            continue;
         }

         bladeHit(player, target);
      }
   }

   private static void bladeHit(ServerPlayer player, LivingEntity target) {
      if (target.hurt(player.damageSources().playerAttack(player), IronManRules.BLADE)) {
         target.addEffect(new MobEffectInstance(ViltrumiteEffects.SUNDER.get(), IronManRules.BLADE_SUNDER_TICKS, 0, false, true), player);
      }
   }

   private static void hammerHit(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      LivingEntity target = entityInReach(player, IronManRules.HAMMER_REACH);
      if (target != null) {
         double height = heightAboveGround(level, target);
         if (target.hurt(player.damageSources().playerAttack(player), IronManRules.HAMMER)) {
            if (!HammerLaunch.slamsDown(target.onGround(), height)) {
               Vec3 look = player.getLookAngle();
               push(target, new Vec3(look.x, 0.0, look.z).normalize().scale(IronManRules.HAMMER_KNOCKBACK).add(0.0, 0.45, 0.0));
            } else if (HeroRegistry.allowsImpulse(target)) {
               Vec3 v = target.getDeltaMovement();
               target.setDeltaMovement(v.x * 0.3, IronManRules.HAMMER_SLAM_DOWN, v.z * 0.3);
               target.hurtMarked = true;
            }
         }

         sound(player, IronManCombatSounds.HAMMER_HIT.get(), 1.0F);
         HeroFx.flash(player, target.getBoundingBox().getCenter());
         return;
      }

      Vec3 eye = player.getEyePosition();
      BlockHitResult ground = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(IronManRules.HAMMER_REACH + 1.0)),
         ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (ground.getType() == HitResult.Type.BLOCK && ground.getLocation().y <= player.getY() + 0.6) {
         hammerSlam(player, ground.getLocation(), level.getBlockState(ground.getBlockPos()));
      } else {
         sound(player, IronManCombatSounds.BLADE_SLASH.get(), 0.6F);
      }
   }

   private static double heightAboveGround(ServerLevel level, Entity target) {
      Vec3 feet = target.position();
      BlockHitResult down = level.clip(new ClipContext(feet, feet.add(0.0, -8.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
      return down.getType() == HitResult.Type.BLOCK ? feet.y - down.getLocation().y : 8.0;
   }

   private static void hammerSlam(ServerPlayer player, Vec3 at, BlockState material) {
      ServerLevel level = player.serverLevel();
      double r = IronManRules.HAMMER_SLAM_RADIUS;
      HeroFx.slam(player, at, material, (float)r);
      sound(player, IronManCombatSounds.HAMMER_SLAM.get(), 1.0F);
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 2.0, r), e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         if (target.position().distanceTo(at) > r + 0.5) {
            continue;
         }

         if (target.hurt(player.damageSources().playerAttack(player), IronManRules.HAMMER_SLAM) && HeroRegistry.allowsImpulse(target)) {
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(v.x, Math.max(v.y, IronManRules.HAMMER_SLAM_LIFT), v.z);
            target.hurtMarked = true;
         }
      }
   }

   /** RMB release with the hammer: launch the target along the look. */
   static void hammerLaunch(ServerPlayer player, IronManState state) {
      int charge = state.hammerCharge;
      state.hammerCharge = -1;
      if (charge < 0) {
         return;
      }

      LivingEntity target = entityInReach(player, IronManRules.HAMMER_REACH);
      state.strikeTicks = state.strikeLength = IronManRules.HAMMER_SWING_TICKS;
      if (target == null) {
         sound(player, IronManCombatSounds.BLADE_SLASH.get(), 0.6F);
         return;
      }

      if (target.hurt(player.damageSources().playerAttack(player), IronManRules.HAMMER_SLAM) && HeroRegistry.allowsImpulse(target)) {
         Vec3 velocity = player.getLookAngle().scale(HammerLaunch.speed(charge)).add(0.0, 0.25, 0.0);
         target.setDeltaMovement(velocity);
         target.hurtMarked = true;
         state.hammerLaunch.launch(target.getId());
      }

      sound(player, IronManCombatSounds.HAMMER_HIT.get(), 0.8F);
   }

   static void hammerLaunchTick(ServerPlayer player, IronManState state) {
      if (state.hammerCharge >= 0 && state.hammerCharge < IronManRules.HAMMER_CHARGE_MAX) {
         state.hammerCharge++;
      }

      ServerLevel level = player.serverLevel();
      for (int id : state.hammerLaunch.targets()) {
         Entity target = level.getEntity(id);
         if (target == null || !target.isAlive()) {
            state.hammerLaunch.stop(id);
            continue;
         }

         AABB path = target.getBoundingBox().expandTowards(target.getDeltaMovement()).inflate(0.1);
         BlockPos.betweenClosedStream(path).limit(64).forEach(pos -> {
            BlockState block = level.getBlockState(pos);
            if (HammerLaunch.soft(block.isAir(), block.getDestroySpeed(level, pos)) && HeroDestruction.canDestroy(level, pos)) {
               HeroDestruction.destroyBlock(level, pos.immutable());
            }
         });
      }

      state.hammerLaunch.tick();
   }

   // ---- shield (§8.5) ----

   static void guardPress(ServerPlayer player, IronManState state) {
      if (sundered(player) || !state.shield.raise(player.level().getGameTime(), state.energy)) {
         sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
         return;
      }

      sound(player, IronManCombatSounds.SHIELD_OPEN.get(), 1.0F);
   }

   static boolean sundered(LivingEntity entity) {
      return entity.hasEffect(ViltrumiteEffects.SUNDER.get());
   }

   static void shieldTick(ServerPlayer player, IronManState state) {
      if (state.shield.raised() && (state.energy.weaponsLocked() || sundered(player))) {
         state.shield.lower();
      }

      if (state.reflect.isEmpty()) {
         return;
      }

      Iterator<Map.Entry<Integer, Double>> it = state.reflect.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Double> entry = it.next();
         it.remove();
         if (player.level().getEntity(entry.getKey()) instanceof Projectile projectile && projectile.isAlive()) {
            Entity shooter = projectile.getOwner();
            Vec3 toShooter = shooter == null ? null : shooter.getBoundingBox().getCenter().subtract(projectile.position());
            Vec3 incoming = projectile.getDeltaMovement().lengthSqr() < 1.0E-6 ? player.getLookAngle().scale(-1.0) : projectile.getDeltaMovement();
            projectile.setDeltaMovement(Reflect.velocity(incoming, toShooter, entry.getValue()));
            projectile.setOwner(player);
            projectile.hurtMarked = true;
         }
      }
   }

   /** absorbIncoming seam: the raised shield eats front hits; a perfect block reflects/knocks back. */
   static DamageAbsorb absorb(ServerPlayer self, IronManState state, DamageSource source) {
      if (!state.shield.raised() || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null) {
         return DamageAbsorb.PASS;
      }

      Vec3 toSource = source.getSourcePosition().subtract(self.getEyePosition());
      float strength = state.hulkbuster.active() ? dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit.SHIELD_STRENGTH : state.spec().shieldMul();
      Shield.Block block = state.shield.hit(self.level().getGameTime(), self.getLookAngle(), toSource, state.energy, strength);
      if (block == Shield.Block.PASS) {
         return DamageAbsorb.PASS;
      }

      if (block == Shield.Block.PERFECT) {
         sound(self, IronManCombatSounds.SHIELD_PERFECT.get(), 1.0F);
         if (source.getDirectEntity() instanceof Projectile projectile) {
            state.reflect.put(projectile.getId(), Math.max(1.2, projectile.getDeltaMovement().length()));
         } else if (source.getEntity() instanceof LivingEntity attacker && attacker != self) {
            Vec3 away = attacker.position().subtract(self.position());
            Vec3 out = away.horizontalDistanceSqr() < 1.0E-6 ? self.getLookAngle() : new Vec3(away.x, 0.0, away.z).normalize();
            push(attacker, out.scale(IronManRules.SHIELD_PERFECT_KNOCKBACK).add(0.0, 0.4, 0.0));
         }
      } else {
         sound(self, IronManCombatSounds.SHIELD_HIT.get(), 0.9F + self.getRandom().nextFloat() * 0.2F);
      }

      return DamageAbsorb.ABSORBED;
   }

   // ---- nano damage (§4.2) ----

   static void onHurt(ServerPlayer player, IronManState state, float amount) {
      if (!state.suit.nano() || amount < IronManRules.NANO_DAMAGE_HIT) {
         return;
      }

      state.damagedZones |= 1 << player.getRandom().nextInt(3);
      state.repairTicks = IronManRules.NANO_REPAIR_TICKS;
   }

   static void repairTick(ServerPlayer player, IronManState state) {
      if (state.repairTicks > 0 && --state.repairTicks == 0 && state.damagedZones != 0) {
         state.damagedZones = 0;
         sound(player, IronManCombatSounds.NANITE_REPAIR.get(), 1.0F);
      }
   }

   // ---- per tick ----

   static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      if (controlled) {
         // Spec §16: control stops every channel (no shots); an overdraft beam keeps going.
         state.repulsor.cancel();
         state.hammerCharge = -1;
         if (state.unibeam.interrupt()) {
            beamEnded(player, state);
         }

         if (state.missiles.held()) {
            state.missiles.cancel();
            pushMarks(player, List.of());
         }

         state.shield.lower();
      }

      boolean wasCharging = state.repulsor.charging() && state.repulsor.chargeTicks() < IronManRules.REPULSOR_TAP_TICKS;
      state.repulsor.tick();
      if (wasCharging && state.repulsor.chargeTicks() == IronManRules.REPULSOR_TAP_TICKS) {
         sound(player, IronManCombatSounds.REPULSOR_CHARGE.get(), 1.0F);
      }

      if (state.recoilTicks > 0) {
         state.recoilTicks--;
      }

      if (state.strikeTicks > 0) {
         state.strikeTicks--;
      }

      state.arsenal.tick();
      unibeamTick(player, state);
      missilesTick(player, state);
      hammerLaunchTick(player, state);
      shieldTick(player, state);
      repairTick(player, state);
   }

   /** Every held combat input of a suit that just went away ends without a shot. */
   static void suitGone(ServerPlayer player, IronManState state) {
      if (state.missiles.held()) {
         pushMarks(player, List.of());
      }

      boolean beam = state.unibeam.phase() == UnibeamTimeline.Phase.BEAM && !state.overdraft.active();
      state.stopCombat();
      if (beam) {
         state.overheat.add();
      }
   }
}
