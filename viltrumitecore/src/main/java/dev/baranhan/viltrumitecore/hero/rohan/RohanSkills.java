package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.entity.directive.DirectiveEffect;
import dev.baranhan.viltrumitecore.entity.directive.MobDirectives;
import dev.baranhan.viltrumitecore.hero.HeroTargets;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.hero.book.BookProfiles;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.InstructionContext;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instructions;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.RohanFxS2CPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Rohan's abilities on the server (docs/rohan/heavens-door-design.md §3). */
public final class RohanSkills {
   private RohanSkills() {
   }

   // ------------------------------------------------------------------ input

   public static void input(ServerPlayer player, RohanState state, RohanAbility ability) {
      // Heaven's Door closes an open book, or calls / sends back the stand when nobody is in reach
      if (ability == RohanAbility.DOOR && RohanBooks.reading(player)) {
         RohanBooks.close(player);
         return;
      }
      if (state.move != null) {
         refuse(player);
         return;
      }
      if (state.cooldowns[ability.cooldown] > 0) {
         refuse(player);
         return;
      }
      float have = ability.usesInspiration() ? state.inspiration : state.ink;
      if (have + 1.0E-3F < ability.cost()) {
         refuse(player);
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable(ability.usesInspiration()
            ? "book.viltrumitecore.refuse.no_inspiration" : "book.viltrumitecore.refuse.no_ink"), true);
         return;
      }
      switch (ability) {
         case DOOR -> door(player, state);
         case SKETCH -> start(player, state, ability, RohanMove.SKETCH, RohanSounds.SKETCH);
         case NIBS -> {
            start(player, state, ability, RohanMove.NIBS, null);
            state.nibsLeft = RohanRules.NIBS_COUNT;
         }
         case COUNTER -> {
            start(player, state, ability, RohanMove.COUNTER, RohanSounds.DOOR_SUMMON);
            state.counterTicks = RohanRules.COUNTER_WINDOW;
            state.countered = false;
         }
         case SELF_EDIT -> start(player, state, ability, RohanMove.SELF_EDIT, RohanSounds.WRITE);
         case MANUSCRIPT -> {
            start(player, state, ability, RohanMove.MANUSCRIPT, RohanSounds.MANUSCRIPT);
            HeroSpeech.ability(player, ability.id());
         }
      }
   }

   private static void start(ServerPlayer player, RohanState state, RohanAbility ability, RohanMove move,
      @Nullable net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> sound) {
      if (ability.usesInspiration()) {
         state.inspiration = Math.max(0.0F, state.inspiration - ability.cost());
      } else {
         state.ink = Math.max(0.0F, state.ink - ability.cost());
      }
      state.cooldowns[ability.cooldown] = ability.cooldownTicks();
      state.start(move);
      if (sound != null) {
         RohanSounds.play(player, sound, 1.0F, RohanSounds.jitter(player, 1.0F));
      }
      if (ability != RohanAbility.MANUSCRIPT && ability != RohanAbility.NIBS) {
         HeroSpeech.ability(player, ability.id());
      }
   }

   static void refuse(ServerPlayer player) {
      RohanSounds.play(player, RohanSounds.REFUSE, 0.5F, 1.0F);
   }

   // ------------------------------------------------------------------ Heaven's Door

   static void door(ServerPlayer player, RohanState state) {
      LivingEntity target = lookTarget(player, RohanRules.DOOR_RANGE_MARKED);
      if (target != null && player.distanceTo(target) > RohanRules.doorRange(state.marked(target.getId())) + target.getBbWidth() * 0.5) {
         target = null;
      }
      if (target == null) {
         // nobody in reach: the stand comes out or goes back
         state.standOut = !state.standOut;
         RohanSounds.play(player, state.standOut ? RohanSounds.DOOR_SUMMON : RohanSounds.DISMISS, 0.8F, 1.0F);
         state.cooldowns[RohanRules.CD_DOOR] = 10;
         return;
      }
      state.ink = Math.max(0.0F, state.ink - RohanRules.COST_DOOR);
      state.cooldowns[RohanRules.CD_DOOR] = RohanRules.COOLDOWN_TICKS[RohanRules.CD_DOOR];
      state.targetId = target.getId();
      state.start(RohanMove.DOOR);
      RohanSounds.play(player, RohanSounds.DOOR_SUMMON, 1.0F, RohanSounds.jitter(player, 1.0F));
      HeroSpeech.ability(player, RohanAbility.DOOR.id());
   }

   static void doorContact(ServerPlayer player, RohanState state) {
      Entity e = player.level().getEntity(state.targetId);
      LivingEntity target = e instanceof LivingEntity l && l.isAlive() ? l : null;
      double reach = RohanRules.doorRange(target != null && state.marked(target.getId())) + 1.5 + (target == null ? 0 : target.getBbWidth() * 0.5);
      if (target == null || player.distanceTo(target) > reach || !player.hasLineOfSight(target)) {
         // missed: half the ink back, a short cooldown
         state.ink = Math.min(RohanRules.INK_MAX, state.ink + RohanRules.COST_DOOR * 0.5F);
         state.cooldowns[RohanRules.CD_DOOR] = RohanRules.CD_DOOR_MISS;
         state.targetId = -1;
         return;
      }
      RohanSounds.play(target, RohanSounds.DOOR_TOUCH, 1.0F, RohanSounds.jitter(target, 1.0F));
      if (RohanBooks.open(player, List.of(target), BookOrigin.TOUCH, 0, -1) == 0) {
         state.cooldowns[RohanRules.CD_DOOR] = RohanRules.CD_DOOR_MISS;
         state.targetId = -1;
      }
   }

   /** The living body under the crosshair within {@code range} (line of sight, valid book). */
   @Nullable
   static LivingEntity lookTarget(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }
      AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
         e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && !(l instanceof ArmorStand), 0.3F);
      if (hit == null) {
         return null;
      }
      Entity e = hit.getEntity();
      if (e instanceof net.minecraftforge.entity.PartEntity<?> part) {
         e = part.getParent();
      }
      return e instanceof LivingEntity l ? l : null;
   }

   // ------------------------------------------------------------------ ticking

   public static void tick(ServerPlayer player, RohanState state) {
      RohanMove move = state.move;
      if (move == RohanMove.DOOR && state.elapsed == RohanRules.DOOR_CONTACT) {
         doorContact(player, state);
      }
      if (move == RohanMove.SKETCH && state.elapsed == RohanRules.SKETCH_DRAW - 1) {
         state.sketchTicks = RohanRules.SKETCH_SHOW;
         Vec3 at = sketchPoint(player);
         CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(RohanFxS2CPacket.SKETCH, player.getId(), -1, RohanRules.SKETCH_SHOW, at), player);
         state.sketchAt = at;
      }
      if (state.sketchTicks > 0 && state.sketchTicks % 5 == 0 && state.sketchAt != null) {
         sketchScan(player, state);
      }
      if (move == RohanMove.NIBS && state.nibsLeft > 0 && state.elapsed % 2 == 0) {
         state.nibsLeft--;
         nib(player, state);
      }
      if (move == RohanMove.SELF_EDIT && state.elapsed == RohanRules.SELF_EDIT_WRITE) {
         selfEdit(player, state);
      }
      if (state.flyTicks > 0 && state.flyVector != null) {
         state.flyTicks--;
         player.setDeltaMovement(state.flyVector);
         player.hurtMarked = true;
         player.fallDistance = 0.0F;
      }
      if (move == RohanMove.MANUSCRIPT && state.elapsed == RohanRules.MANUSCRIPT_SHOW - 1) {
         manuscript(player, state);
      }
      if (move == RohanMove.COUNTER && state.elapsed == RohanRules.COUNTER_WINDOW - 1 && !state.countered) {
         state.cooldowns[RohanRules.CD_COUNTER] = RohanRules.CD_COUNTER_MISS;
      }
   }

   // ------------------------------------------------------------------ Air Sketch

   static Vec3 sketchPoint(ServerPlayer player) {
      return player.getEyePosition().add(player.getLookAngle().scale(1.0)).add(0.0, -0.15, 0.0);
   }

   static void sketchScan(ServerPlayer player, RohanState state) {
      Vec3 at = state.sketchAt;
      if (at.distanceTo(player.getEyePosition()) > 4.0) {
         // he walked away from his drawing: it fades
         state.sketchTicks = 0;
         return;
      }
      List<LivingEntity> viewers = viewers(player, at, RohanRules.SKETCH_RANGE, RohanRules.SKETCH_MAX_VIEWERS, RohanRules.SKETCH_CONE_MOB,
         RohanRules.SKETCH_CONE_PLAYER);
      if (!viewers.isEmpty()) {
         RohanBooks.open(player, viewers, BookOrigin.SKETCH, 0, 2);
      }
   }

   /** Bodies looking at {@code at} from within range with a clear line of sight, nearest first. */
   static List<LivingEntity> viewers(ServerPlayer player, Vec3 at, double range, int max, double mobCone, double playerCone) {
      List<LivingEntity> out = new ArrayList<>();
      ServerLevel level = player.serverLevel();
      List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range),
         e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand));
      near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
      for (LivingEntity e : near) {
         if (out.size() >= max) {
            break;
         }
         if (RohanBooks.openDirective(e, player.getUUID()) != null || e.getEyePosition().distanceTo(at) > range) {
            continue;
         }
         Vec3 look = e.getViewVector(1.0F);
         Vec3 to = at.subtract(e.getEyePosition());
         double cone = e instanceof Player ? playerCone : mobCone;
         if (!RohanRules.inCone(look.x, look.y, look.z, to.x, to.y, to.z, cone)) {
            continue;
         }
         BlockHitResult hit = level.clip(new ClipContext(e.getEyePosition(), at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
         if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(at) > 0.25) {
            continue;
         }
         if (e instanceof Player p && !HeroTargets.canHit(player, p)) {
            continue;
         }
         out.add(e);
      }
      return out;
   }

   // ------------------------------------------------------------------ Pen Nibs

   static void nib(ServerPlayer player, RohanState state) {
      Vec3 eye = player.getEyePosition();
      float spread = (RohanRules.NIBS_COUNT - state.nibsLeft - 1) * 1.5F - 1.5F;
      Vec3 dir = Vec3.directionFromRotation(player.getXRot() + player.getRandom().nextFloat() - 0.5F, player.getYRot() + spread);
      Vec3 end = eye.add(dir.scale(RohanRules.NIBS_RANGE));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye))
         .inflate(1.0), e -> e instanceof LivingEntity && HeroTargets.canHit(player, e), 0.25F);
      RohanSounds.play(player, RohanSounds.NIB_THROW, 0.7F, RohanSounds.jitter(player, 1.1F));
      Vec3 at = hit != null ? hit.getLocation() : end;
      CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(RohanFxS2CPacket.NIB, player.getId(), hit == null ? -1 : hit.getEntity().getId(), 4, at),
         player);
      if (hit == null) {
         return;
      }
      Entity e = hit.getEntity() instanceof net.minecraftforge.entity.PartEntity<?> part ? part.getParent() : hit.getEntity();
      if (e instanceof LivingEntity target) {
         target.invulnerableTime = 0;
         if (target.hurt(player.damageSources().thrown(player, player), RohanRules.NIB_DAMAGE)) {
            state.idle = 0;
         }
         state.marks.put(target.getId(), state.tickCount + RohanRules.INK_MARK_TICKS);
         RohanSounds.play(target, RohanSounds.NIB_HIT, 0.8F, RohanSounds.jitter(target, 1.0F));
      }
   }

   // ------------------------------------------------------------------ Faster than a Punch

   /**
    * Rohan is hit while the stance is up: the stand's palm reaches first. True when the hit is cancelled (the attacker's
    * book opens with a Safety Lock already written).
    */
   public static boolean counter(ServerPlayer player, RohanState state, LivingEntity attacker) {
      if (state.counterTicks <= 0 || state.countered || attacker == player || player.distanceTo(attacker) > RohanRules.COUNTER_RANGE + attacker.getBbWidth()) {
         return false;
      }
      state.countered = true;
      state.counterTicks = 0;
      state.cooldowns[RohanRules.CD_COUNTER] = RohanRules.COOLDOWN_TICKS[RohanRules.CD_COUNTER];
      CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(RohanFxS2CPacket.COUNTER, player.getId(), attacker.getId(), 10, attacker.getEyePosition()),
         player);
      RohanSounds.play(player, RohanSounds.COUNTER, 1.0F, 1.0F);
      HeroSpeech.ability(player, RohanAbility.COUNTER.id());
      if (RohanBooks.open(player, List.of(attacker), BookOrigin.COUNTER, RohanRules.COUNTER_OPEN, -1) > 0) {
         BookProfile profile = BookProfiles.resolve(attacker);
         if (RohanBooks.globalRefusal(player, attacker, profile) == null) {
            InstructionContext ctx = new InstructionContext(player, attacker, profile, null, null, null, player.level().getGameTime(), 0);
            if (Instructions.SAFETY_LOCK.refusal(ctx) == null) {
               Instructions.SAFETY_LOCK.apply(ctx, RohanRules.lineTicks(Instructions.SAFETY_LOCK.baseTicks(profile.bookClass()), profile, 0));
            }
         }
      }
      return true;
   }

   // ------------------------------------------------------------------ Self-Edit

   static void selfEdit(ServerPlayer player, RohanState state) {
      state.flyVector = MobDirectives.backwards(player, RohanRules.SELF_EDIT_SPEED);
      state.flyTicks = RohanRules.SELF_EDIT_FLY;
      player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
      player.removeEffect(MobEffects.CONFUSION);
      player.removeEffect(MobEffects.DIG_SLOWDOWN);
      MobDirectives.remove(player, d -> d.has(DirectiveEffect.ROOT) || d.has(DirectiveEffect.OPEN));
      RohanSounds.play(player, RohanSounds.WHOOSH, 1.0F, 1.0F);
      CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(RohanFxS2CPacket.SELF_EDIT, player.getId(), player.getId(), RohanRules.SELF_EDIT_FLY,
         player.getEyePosition()), player);
   }

   // ------------------------------------------------------------------ Manuscript

   static void manuscript(ServerPlayer player, RohanState state) {
      Vec3 page = player.getEyePosition().add(player.getLookAngle().scale(0.8));
      CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(RohanFxS2CPacket.MANUSCRIPT, player.getId(), -1, RohanRules.MANUSCRIPT_BOOKS, page), player);
      List<LivingEntity> viewers = new ArrayList<>();
      for (LivingEntity e : viewers(player, page, RohanRules.MANUSCRIPT_RANGE, RohanRules.MANUSCRIPT_MAX * 2, RohanRules.MANUSCRIPT_CONE,
         RohanRules.MANUSCRIPT_CONE)) {
         Vec3 to = e.position().subtract(player.position());
         Vec3 look = player.getLookAngle();
         if (viewers.size() < RohanRules.MANUSCRIPT_MAX && RohanRules.inCone(look.x, 0.0, look.z, to.x, 0.0, to.z, RohanRules.MANUSCRIPT_CONE)) {
            viewers.add(e);
         }
      }
      int opened = viewers.isEmpty() ? 0 : RohanBooks.open(player, viewers, BookOrigin.MANUSCRIPT, RohanRules.MANUSCRIPT_BOOKS, 1);
      if (opened == 0) {
         // nobody read it: half the inspiration comes back
         state.inspiration = Math.min(RohanRules.INSPIRATION_MAX, state.inspiration + RohanRules.MANUSCRIPT_COST * 0.5F);
         state.cooldowns[RohanRules.CD_MANUSCRIPT] = 200;
      } else {
         state.manuscriptTicks = RohanRules.MANUSCRIPT_BOOKS;
      }
   }

   // ------------------------------------------------------------------ cleanup

   public static void cleanup(ServerPlayer player, RohanState state) {
      state.move = null;
      state.elapsed = 0;
      state.sketchTicks = 0;
      state.manuscriptTicks = 0;
      state.counterTicks = 0;
      state.flyTicks = 0;
      state.nibsLeft = 0;
      state.targetId = -1;
      state.marks.clear();
   }

}
