package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
import dev.baranhan.viltrumitecore.hero.BodyScale;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeldInputs;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombat;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHulkbusterSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Server driver of the Hulkbuster Mark 48 (spec §4.4, §14): drop from
 * Veronica over the current suit, assembly with a free-space check of the
 * real final box (standing × 1.7), the big body through the generic
 * bodyScale seam, damage on its own durability first, the kit (punches,
 * jackhammer, slow repulsors, grab and throw, jump slam, thruster hop),
 * exit and break.
 */
public final class IronManHulkbuster {
   /** Rooted while the parts assemble (plan stage 5 Task 5). */
   private static final UUID ROOT_ID = UUID.fromString("ab5dd421-40de-473b-8e9e-a5cf536461fe");
   private static final float STANDING_WIDTH = 0.6F;
   private static final float STANDING_HEIGHT = 1.8F;
   public static final float BREAK_FX_RADIUS = 4.0F;

   public enum Delivery {
      STARTED,
      BUSY,
      UNAVAILABLE,
      NO_ROOM
   }

   private IronManHulkbuster() {
   }

   /** Veronica choice (spec §14.1): over nano or a fully-on mark, not on cooldown, only with room for the big body. */
   public static Delivery deliver(ServerPlayer player, IronManState state, Vec3 podTop) {
      HulkbusterLayer layer = state.hulkbuster;
      boolean partial = layer.phase() == HulkbusterLayer.Phase.PARTIAL;
      if (state.suit.busy() || layer.busy() || layer.active() || !state.suit.worn() && !partial) {
         return Delivery.BUSY;
      }

      if (layer.cooldown() > 0) {
         return Delivery.UNAVAILABLE;
      }

      if (freeSpot(player) == null) {
         return Delivery.NO_ROOM;
      }

      if (!layer.start()) {
         return Delivery.UNAVAILABLE;
      }

      IronManMarks.stopSignature(player, state);
      state.stopCombat();
      HeldInputs.releaseAll(player);
      state.equipSource = podTop;
      IronManCombat.sound(player, IronManHulkbusterSounds.DROP.get(), 1.0F);
      return Delivery.STARTED;
   }

   /** Feet position where the big standing body fits, at or near the player; null = no room. */
   @Nullable
   static Vec3 freeSpot(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      float width = STANDING_WIDTH * HulkbusterLayer.SCALE;
      float height = STANDING_HEIGHT * HulkbusterLayer.SCALE;
      return BodyScale.findFree(player.position(), width, height, box -> level.noCollision(player, box));
   }

   // ---- per tick ----

   public static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      HulkbusterLayer layer = state.hulkbuster;
      HulkbusterKit kit = state.hulkKit;
      boolean wasBig = layer.big();
      kit.tickCooldowns();
      if (controlled) {
         layer.interrupt();
         kit.stopChannels();
         releaseGrab(player, state, ReleaseReason.INTERRUPTED);
      }

      if (layer.needsFitCheck()) {
         fitOrRefuse(player, state);
      }

      HulkbusterLayer.Event event = layer.tick();
      if (event == HulkbusterLayer.Event.READY) {
         fitOrRefuse(player, state);
         if (layer.active()) {
            state.equipSource = null;
            state.rightTool = RightTool.JACKHAMMER;
            state.helmet.reset();
            IronManCombat.sound(player, IronManHulkbusterSounds.ASSEMBLE.get(), 0.8F);
         }
      } else if (event == HulkbusterLayer.Event.EXITED) {
         IronManCombat.sound(player, IronManHulkbusterSounds.EXIT.get(), 1.1F);
      }

      if (layer.broken()) {
         breakHulkbuster(player, state);
      }

      if (layer.active()) {
         // The Mark 48 helmet is always closed (plan stage 5 Global Constraints).
         if (!state.helmet.closed()) {
            state.helmet.reset();
         }

         if (!controlled) {
            jackhammerTick(player, state);
            chargeTick(state);
            grabTick(player, state);
            slamTick(player, state);
            hopTick(player, state);
         }
      } else {
         kit.stopChannels();
         if (kit.grabbedId >= 0) {
            releaseGrab(player, state, ReleaseReason.NORMAL_END);
         }
      }

      thrownTick(player, state);
      root(player, layer.phase() == HulkbusterLayer.Phase.DROPPING || layer.phase() == HulkbusterLayer.Phase.ASSEMBLING);
      if (wasBig != layer.big()) {
         player.refreshDimensions();
      }
   }

   private static void fitOrRefuse(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      Vec3 feet = freeSpot(player);
      if (feet == null) {
         // No room (a block placed during the assembly, a tunnel): parts fly back, no cooldown.
         layer.refuse();
         state.equipSource = null;
         player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.hulkbuster_no_room"), true);
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.6F);
         return;
      }

      if (feet.distanceToSqr(player.position()) > 1.0E-4) {
         player.teleportTo(feet.x, feet.y, feet.z);
      }

      layer.activate();
   }

   /** Durability 0 or death: it falls apart, Tony stays in his suit (spec §14.2). */
   public static void breakHulkbuster(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      if (!layer.present()) {
         return;
      }

      boolean wasBig = layer.big();
      releaseGrab(player, state, ReleaseReason.INTERRUPTED);
      state.hulkKit.stopChannels();
      layer.breakNow();
      HeroFx.flash(player, player.position().add(0.0, 1.6, 0.0));
      HeroFx.shockwave(player, player.position(), 0.8F, null, BREAK_FX_RADIUS);
      IronManCombat.sound(player, IronManHulkbusterSounds.BREAK.get(), 1.0F);
      resetTool(state);
      if (wasBig) {
         player.refreshDimensions();
      }
   }

   private static void resetTool(IronManState state) {
      if (state.rightTool == RightTool.JACKHAMMER || state.rightTool == RightTool.HULK_REPULSOR) {
         state.rightTool = RightTool.REPULSOR;
      }
   }

   /** "Костюм" (spec §4.5 row 4): climb out (cooldown after) or send the partial parts back. True when handled. */
   public static boolean suitKey(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      if (!layer.present()) {
         return false;
      }

      if (layer.exit()) {
         releaseGrab(player, state, ReleaseReason.NORMAL_END);
         state.hulkKit.stopChannels();
         resetTool(state);
         IronManCombat.sound(player, IronManHulkbusterSounds.EXIT.get(), 1.0F);
      }

      return true;
   }

   /** Damage layer between the shield and the mark (spec §14.2): the whole hit, no spill. */
   public static DamageAbsorb absorb(IronManState state, DamageSource source, float amount) {
      if (!state.hulkbuster.active() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return DamageAbsorb.PASS;
      }

      return state.hulkbuster.absorb(amount) ? DamageAbsorb.ABSORBED : DamageAbsorb.PASS;
   }

   // ---- kit ----

   /** LMB: alternating huge punches (spec §14.3). */
   public static void punch(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (!kit.punchReady()) {
         return;
      }

      kit.punchCooldown = HulkbusterKit.PUNCH_CADENCE;
      kit.punchRight = !kit.punchRight;
      state.strikeTicks = state.strikeLength = HulkbusterKit.PUNCH_CADENCE;
      state.recoilRight = kit.punchRight;
      player.swing(kit.punchRight ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
      LivingEntity target = IronManCombat.entityInReach(player, HulkbusterKit.PUNCH_REACH);
      IronManCombat.sound(player, IronManHulkbusterSounds.PUNCH.get(), 0.9F + player.getRandom().nextFloat() * 0.2F);
      if (target != null && target.hurt(player.damageSources().playerAttack(player), HulkbusterKit.PUNCH_DAMAGE)) {
         Vec3 look = player.getLookAngle();
         Vec3 flat = new Vec3(look.x, 0.0, look.z);
         flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
         IronManCombat.push(target, flat.scale(HulkbusterKit.PUNCH_KNOCKBACK).add(0.0, 0.4, 0.0));
         HeroFx.flash(player, target.getBoundingBox().getCenter());
      }
   }

   /** RMB with the jackhammer: pin the crosshair target in front, hits every 4 t up to 3 s. */
   public static void jackhammerPress(ServerPlayer player, IronManState state) {
      if (state.energy.weaponsLocked()) {
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
         return;
      }

      LivingEntity target = IronManCombat.entityInReach(player, HulkbusterKit.JACKHAMMER_RANGE);
      state.hulkKit.jackhammerTicks = 0;
      state.hulkKit.jackhammerTarget = target == null ? -1 : target.getId();
   }

   public static void jackhammerRelease(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.jackhammerTicks < 0) {
         return;
      }

      Entity target = kit.jackhammerTarget < 0 ? null : player.level().getEntity(kit.jackhammerTarget);
      if (target instanceof LivingEntity living && living.isAlive()) {
         IronManCombat.push(living, player.getLookAngle().scale(HulkbusterKit.JACKHAMMER_RELEASE_KNOCKBACK).add(0.0, 0.3, 0.0));
      }

      kit.jackhammerTicks = -1;
      kit.jackhammerTarget = -1;
   }

   private static void jackhammerTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.jackhammerTicks < 0) {
         return;
      }

      kit.jackhammerTicks++;
      Entity entity = kit.jackhammerTarget < 0 ? null : player.level().getEntity(kit.jackhammerTarget);
      if (entity instanceof LivingEntity target && target.isAlive() && target.distanceTo(player) <= HulkbusterKit.JACKHAMMER_RANGE + 2.0) {
         if (HeroRegistry.allowsImpulse(target)) {
            Vec3 pin = player.getEyePosition().add(player.getLookAngle().scale(2.2)).add(0.0, -target.getBbHeight() * 0.5, 0.0);
            target.setDeltaMovement(pin.subtract(target.position()).scale(0.5));
            target.hurtMarked = true;
            target.resetFallDistance();
         }

         if (HulkbusterKit.jackhammerHit(kit.jackhammerTicks)) {
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), HulkbusterKit.JACKHAMMER_DAMAGE)) {
               HeroFx.flash(player, target.getBoundingBox().getCenter());
            }

            IronManCombat.sound(player, IronManHulkbusterSounds.JACKHAMMER.get(), 0.9F + player.getRandom().nextFloat() * 0.2F);
         }
      } else if (HulkbusterKit.jackhammerHit(kit.jackhammerTicks)) {
         IronManCombat.sound(player, IronManHulkbusterSounds.JACKHAMMER.get(), 1.2F);
      }

      if (kit.jackhammerTicks >= HulkbusterKit.JACKHAMMER_MAX) {
         jackhammerRelease(player, state);
      }
   }

   /** RMB with the slow repulsors: charged shots only (spec §14.3, plan stage 5 Task 6). */
   public static void chargePress(IronManState state) {
      state.hulkKit.charge = 0;
   }

   private static void chargeTick(IronManState state) {
      if (state.hulkKit.charge >= 0 && state.hulkKit.charge < HulkbusterKit.SLOW_REPULSOR_CHARGE) {
         state.hulkKit.charge++;
      }
   }

   public static void chargeRelease(ServerPlayer player, IronManState state) {
      int charge = state.hulkKit.charge;
      state.hulkKit.charge = -1;
      if (charge < 0) {
         return;
      }

      if (!HulkbusterKit.slowRepulsorCharged(charge) || state.energy.weaponsLocked() || !state.energy.spend(HulkbusterKit.SLOW_REPULSOR_COST)) {
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.7F);
         return;
      }

      Vec3 from = IronManCombat.hand(player, true);
      Vec3 aim = IronManCombat.aimPoint(player, 48.0).subtract(from);
      Vec3 dir = aim.lengthSqr() < 1.0E-4 ? player.getLookAngle() : aim.normalize();
      RepulsorBlastEntity.shoot(player, from, dir, HulkbusterKit.SLOW_REPULSOR_DAMAGE, 1.0F, 2.5);
      state.recoilTicks = 8;
      state.recoilRight = true;
      IronManCombat.sound(player, IronManCombatSounds.REPULSOR_VOLLEY.get(), 0.6F);
   }

   /** Slot 1: grab the target in front; again: throw it along the look (spec §14.3). */
   public static void grabPress(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.grabbedId >= 0) {
         throwGrabbed(player, state);
         return;
      }

      LivingEntity target = IronManCombat.entityInReach(player, HulkbusterKit.GRAB_RANGE);
      UUID effect = UUID.randomUUID();
      if (target == null || !HeroRegistry.allowsExternalControl(target, ControlKind.PULL)
         || !ControlManager.get(player.serverLevel()).tryAcquire(target, player.getUUID(), effect, ControlKind.PULL,
            player.level().getGameTime() + HulkbusterKit.GRAB_MAX_TICKS + 20L)) {
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.6F);
         return;
      }

      kit.grabbedId = target.getId();
      kit.grabbedUuid = target.getUUID();
      kit.grabEffect = effect;
      kit.grabTicks = 0;
      IronManCombat.sound(player, IronManHulkbusterSounds.GRAB.get(), 1.0F);
   }

   private static void grabTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.grabbedId < 0) {
         return;
      }

      Entity entity = player.level().getEntity(kit.grabbedId);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > 8.0) {
         releaseGrab(player, state, ReleaseReason.TARGET_LOST);
         return;
      }

      kit.grabTicks++;
      if (kit.grabTicks > HulkbusterKit.GRAB_MAX_TICKS) {
         releaseGrab(player, state, ReleaseReason.NORMAL_END);
         return;
      }

      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
      Vec3 hold = player.position().add(flat.scale(1.6)).add(0.0, 1.7, 0.0);
      if (target instanceof ServerPlayer victim) {
         victim.teleportTo(hold.x, hold.y, hold.z);
      } else {
         target.setPos(hold.x, hold.y, hold.z);
      }

      target.setDeltaMovement(Vec3.ZERO);
      target.resetFallDistance();
      target.hurtMarked = true;
   }

   private static void throwGrabbed(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Entity entity = player.level().getEntity(kit.grabbedId);
      releaseGrab(player, state, ReleaseReason.NORMAL_END);
      if (entity instanceof LivingEntity target && target.isAlive()) {
         target.setDeltaMovement(player.getLookAngle().scale(HulkbusterKit.THROW_SPEED).add(0.0, 0.3, 0.0));
         target.hurtMarked = true;
         kit.thrown.put(target.getId(), HulkbusterKit.THROW_TRACK_TICKS);
         IronManCombat.sound(player, IronManHulkbusterSounds.THROW.get(), 1.0F);
      }
   }

   /** Every end of a carry releases the control (death, control, break, exit, logout, target lost). */
   public static void releaseGrab(ServerPlayer player, IronManState state, ReleaseReason reason) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.grabbedId < 0) {
         return;
      }

      Entity entity = player.level().getEntity(kit.grabbedId);
      UUID targetId = kit.grabbedUuid != null ? kit.grabbedUuid : entity == null ? null : entity.getUUID();
      if (targetId != null && kit.grabEffect != null) {
         ControlManager.get(player.serverLevel()).release(targetId, kit.grabEffect, reason);
      }

      kit.grabbedId = -1;
      kit.grabbedUuid = null;
      kit.grabEffect = null;
      kit.grabTicks = 0;
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

         boolean hit = target.horizontalCollision || target.onGround() && left < HulkbusterKit.THROW_TRACK_TICKS - 3;
         if (hit) {
            target.hurt(player.damageSources().playerAttack(player), HulkbusterKit.THROW_IMPACT_DAMAGE);
            HeroFx.shockwave(player, target.position(), 0.5F, null, 2.0F);
            it.remove();
         } else {
            entry.setValue(left);
         }
      }
   }

   /** Slot 2: jump forward-up, slam the ground on landing (spec §14.3). */
   public static void slamPress(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.slamCooldown > 0 || kit.slamArmed || !player.onGround()) {
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.6F);
         return;
      }

      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
      player.setDeltaMovement(flat.scale(1.2).add(0.0, 1.1, 0.0));
      player.hurtMarked = true;
      kit.slamArmed = true;
      kit.slamTicks = 0;
      kit.slamCooldown = HulkbusterKit.SLAM_COOLDOWN;
      IronManCombat.sound(player, IronManHulkbusterSounds.HOP.get(), 0.7F);
   }

   private static void slamTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (!kit.slamArmed) {
         return;
      }

      kit.slamTicks++;
      if (kit.slamTicks > HulkbusterKit.SLAM_ARM_TICKS) {
         kit.slamArmed = false;
         return;
      }

      if (kit.slamTicks < 4 || !player.onGround()) {
         return;
      }

      kit.slamArmed = false;
      ServerLevel level = player.serverLevel();
      BlockPos ground = player.blockPosition().below();
      HeroFx.slam(player, player.position(), level.getBlockState(ground), (float)HulkbusterKit.SLAM_RADIUS);
      double r = HulkbusterKit.SLAM_RADIUS;
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 2.0, r), e -> dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets.canHit(player, e))) {
         Vec3 away = target.position().subtract(player.position());
         double distance = away.horizontalDistance();
         float damage = HulkbusterKit.slamDamage(distance);
         if (damage <= 0.0F) {
            continue;
         }

         if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            Vec3 out = distance < 1.0E-3 ? Vec3.ZERO : new Vec3(away.x / distance, 0.0, away.z / distance);
            IronManCombat.push(target, out.scale(1.4).add(0.0, 0.6, 0.0));
         }
      }

      Vec3 look = player.getLookAngle();
      HeroDebris.erupt(player, ground, new Vec3(look.x, 0.0, look.z), new HeroDebris.Eruption(4.0, 1.0, 0.0, 10, 0.6, 1.0), 4.0F, e -> e == player);
      IronManCombat.sound(player, IronManHulkbusterSounds.SLAM.get(), 1.0F);
   }

   /** Slot 3: short hop on the thrusters, no sustained flight (spec §14.3). */
   public static void hopPress(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.hopCooldown > 0 || state.energy.weaponsLocked() || !state.energy.spend(HulkbusterKit.HOP_COST)) {
         IronManCombat.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.6F);
         return;
      }

      kit.hopTicks = HulkbusterKit.HOP_TICKS;
      kit.hopCooldown = HulkbusterKit.HOP_COOLDOWN;
      player.setDeltaMovement(player.getLookAngle().scale(1.4).add(0.0, 0.8, 0.0));
      player.hurtMarked = true;
      IronManCombat.sound(player, IronManHulkbusterSounds.HOP.get(), 1.0F);
   }

   private static void hopTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.hopTicks > 0) {
         kit.hopTicks--;
         player.resetFallDistance();
      }
   }

   // ---- lifecycle ----

   /** Death: the Hulkbuster falls apart (cooldown), the carry ends (spec §16). */
   public static void onDeath(ServerPlayer player, IronManState state) {
      releaseGrab(player, state, ReleaseReason.CASTER_DEATH);
      breakHulkbuster(player, state);
   }

   public static void onLogout(ServerPlayer player, IronManState state) {
      releaseGrab(player, state, ReleaseReason.CASTER_DISCONNECT);
      state.hulkKit.stopChannels();
   }

   private static void root(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      boolean has = speed.getModifier(ROOT_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(ROOT_ID, "Hulkbuster assembling", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(ROOT_ID);
      }
   }
}
