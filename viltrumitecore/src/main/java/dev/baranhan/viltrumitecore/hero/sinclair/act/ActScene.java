package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.act.StageAct;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairLines;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActCardEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActPropEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One performance of a number (spec §6.1). Sinclair's own code stages every shape: the server beats
 * (damage, props, freeze) run here on the act move's clock; the client draws the same clock from the
 * snapshot (move ordinal + elapsed) and the props' synced data.
 */
public final class ActScene {
   /** The performer slows to a stage walk while the number runs. */
   public static final UUID STAGE_WALK_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a104");
   public static final double STAGE_WALK = -0.6;
   private final StageAct act;
   private final ActScript script;
   private final float power;
   private final int volunteerId;
   @Nullable
   private final UUID volunteer;
   private Vec3 focus = Vec3.ZERO;
   private float yaw;
   private int propId = -1;
   private final UUID freezeEffect = UUID.randomUUID();
   private final List<UUID> frozen = new ArrayList<>();
   private final List<Integer> victims = new ArrayList<>();
   private Vec3 hatFrom = Vec3.ZERO;

   public ActScene(StageAct act, @Nullable LivingEntity volunteer) {
      StageAct safe = act.wellFormed() ? act : SinclairRepertoire.fallback();
      this.act = safe;
      this.script = ActScript.of(safe.shape());
      this.power = SinclairRules.actPower(safe.power());
      this.volunteerId = volunteer == null ? -1 : volunteer.getId();
      this.volunteer = volunteer == null ? null : volunteer.getUUID();
   }

   public StageAct act() {
      return this.act;
   }

   public ActShape shape() {
      return this.act.shape();
   }

   public ActScript script() {
      return this.script;
   }

   public float power() {
      return this.power;
   }

   /** The act's focus point (snapshot actionTarget). */
   public Vec3 focus() {
      return this.focus;
   }

   /** AEGIS: the curtain is closed (damage ×0.25, projectiles reflect). */
   public boolean curtainClosed(int tick) {
      return this.shape() == ActShape.AEGIS && tick < ActScript.AEGIS.reveal();
   }

   /** NOVA: the hat is away from the head. */
   public boolean hatAway(int tick) {
      return this.shape() == ActShape.NOVA && tick >= ActScript.NOVA_THROW && tick < ActScript.NOVA_CATCH;
   }

   public void begin(ServerPlayer player, SinclairState state) {
      this.yaw = player.getYRot();
      this.focus = Stagecraft.aimPoint(player, this.act.reach());
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null && speed.getModifier(STAGE_WALK_ID) == null) {
         speed.addTransientModifier(new AttributeModifier(STAGE_WALK_ID, "Sinclair stage walk", STAGE_WALK, AttributeModifier.Operation.MULTIPLY_TOTAL));
      }

      SinclairSounds.play(player.level(), player.position().add(0.0, 1.0, 0.0), SinclairSounds.ACT_DRUMROLL, 1.0F, 1.0F);
   }

   public void tick(ServerPlayer player, SinclairState state, int t) {
      switch (this.shape()) {
         case BEAM -> this.beam(player, t);
         case NOVA -> this.nova(player, t);
         case CUT -> this.cut(player, t);
         case BARRAGE -> this.barrage(player, t);
         case AEGIS -> this.aegis(player, t);
         case FINALE -> this.finale(player, t);
      }

      if (t == this.script.bowStart()) {
         SinclairSounds.play(player.level(), player.position().add(0.0, 1.0, 0.0), SinclairSounds.ACT_BOW, 1.0F, 1.0F);
         HeroSpeech.trigger(player, HeroLineTrigger.event(SinclairLines.EVENT_BOW));
         AssistantEntity assistant = Stagecraft.onStage(player, state);
         if (assistant != null) {
            assistant.setState(AssistantEntity.BOW, this.script.length() - this.script.bowStart());
         }
      }
   }

   @Nullable
   private LivingEntity volunteer(ServerPlayer player) {
      if (this.volunteerId < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(this.volunteerId);
      return entity instanceof LivingEntity living && living.isAlive() && living.getUUID().equals(this.volunteer) ? living : null;
   }

   // --- BEAM: "Footlight Ray" -------------------------------------------------------------------
   private void beam(ServerPlayer player, int t) {
      Vec3 from = Stagecraft.leftHand(player);
      Vec3 end = from.add(player.getLookAngle().scale(this.act.reach()));
      BlockHitResult block = player.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }

      this.focus = end;
      if (t == ActScript.BEAM_START) {
         SinclairSounds.play(player.level(), from, SinclairSounds.ACT_BEAM, 1.2F, 1.0F);
      }

      if (!ActScript.beamPulse(t)) {
         return;
      }

      AABB box = new AABB(from, end).inflate(ActScript.BEAM_WIDTH);
      for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> SinclairCombat.enemy(player, e))) {
         AABB body = target.getBoundingBox().inflate(ActScript.BEAM_WIDTH * 0.5);
         if (body.contains(from) || body.clip(from, end).isPresent()) {
            SinclairCombat.trickHit(player, target, ActScript.BEAM_DAMAGE * this.power);
         }
      }
   }

   // --- NOVA: "Hat Whirl" -----------------------------------------------------------------------
   private void nova(ServerPlayer player, int t) {
      ActPropEntity hat = this.prop(player);
      if (t == ActScript.NOVA_THROW) {
         LivingEntity target = this.volunteer(player);
         Vec3 to = target != null && target.distanceTo(player) <= this.act.reach() + 2.0 ? target.position().add(0.0, 1.2, 0.0)
            : Stagecraft.aimPoint(player, this.act.reach());
         this.focus = to;
         this.hatFrom = Stagecraft.wandTip(player);
         hat = this.spawnProp(player, ActPropEntity.NOVA_HAT, this.hatFrom, ActScript.NOVA_CATCH - ActScript.NOVA_THROW + 4, 1.0F, t);
         SinclairSounds.play(player.level(), this.hatFrom, SinclairSounds.ACT_NOVA_WHIRL, 1.0F, 1.0F);
      }

      if (hat == null) {
         return;
      }

      int burst = ActScript.NOVA.reveal();
      if (t > ActScript.NOVA_THROW && t <= 20) {
         float k = (t - ActScript.NOVA_THROW) / 10.0F;
         float e = 1.0F - (1.0F - k) * (1.0F - k);
         hat.place(this.hatFrom.lerp(this.focus, e).add(0.0, Math.sin(k * Math.PI) * 1.2, 0.0));
      } else if (t > 20 && t < burst) {
         hat.place(this.focus);
      }

      if (t > ActScript.NOVA_THROW && t < burst) {
         // The whirl pulls everyone near the hat in a little (impulse-allowed bodies only).
         for (LivingEntity target : SinclairCombat.enemiesNear(player, hat.position(), ActScript.NOVA_RADIUS + 2.0)) {
            if (HeroRegistry.allowsImpulse(target)) {
               Vec3 pull = hat.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
               if (pull.lengthSqr() > 0.25) {
                  target.setDeltaMovement(target.getDeltaMovement().add(pull.normalize().scale(0.06)));
                  target.hurtMarked = true;
               }
            }
         }
      }

      if (t == burst) {
         Vec3 at = hat.position();
         for (LivingEntity target : SinclairCombat.enemiesNear(player, at, ActScript.NOVA_RADIUS)) {
            if (SinclairCombat.trickHit(player, target, ActScript.NOVA_DAMAGE * this.power)) {
               SinclairCombat.shove(target, at, 1.1, 0.45);
               SinclairCombat.daze(target);
            }
         }

         SinclairSounds.play(player.level(), at, SinclairSounds.ACT_NOVA_BURST, 1.4F, 1.0F);
         SinclairCombat.flash(player, at, 1.4F);
      }

      if (t > burst && t < ActScript.NOVA_CATCH) {
         float k = (t - burst) / (float) (ActScript.NOVA_CATCH - burst);
         Vec3 hand = Stagecraft.wandTip(player);
         hat.place(this.focus.lerp(hand, k * k).add(0.0, Math.sin(k * Math.PI) * 1.5, 0.0));
      } else if (t >= ActScript.NOVA_CATCH) {
         hat.discard();
         this.propId = -1;
      }
   }

   // --- CUT: "Sawing" ---------------------------------------------------------------------------
   private void cut(ServerPlayer player, int t) {
      if (t == ActScript.CUT_TELEGRAPH) {
         this.yaw = player.getYRot();
         Vec3 feet = player.position();
         Vec3 dir = Vec3.directionFromRotation(0.0F, this.yaw);
         this.focus = feet.add(dir.scale(this.act.reach()));
         this.spawnProp(player, ActPropEntity.CUT_LINE, feet.add(dir.scale(0.8)), ActScript.CUT.pauseEnd() - t + 2, this.act.reach(), t);
         SinclairSounds.play(player.level(), feet, SinclairSounds.ACT_CUT_SAW, 1.2F, 1.0F);
      }

      ActPropEntity line = this.prop(player);
      if (t == ActScript.CUT.reveal() && line != null) {
         Vec3 start = line.position();
         Vec3 dir = Vec3.directionFromRotation(0.0F, this.yaw);
         Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
         double reach = this.act.reach();
         AABB box = new AABB(start, start.add(dir.scale(reach))).inflate(ActScript.CUT_WIDTH + 1.0, 3.0, ActScript.CUT_WIDTH + 1.0);
         for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> SinclairCombat.enemy(player, e))) {
            Vec3 rel = target.position().subtract(start);
            double along = rel.dot(dir);
            double across = Math.abs(rel.dot(side));
            if (along >= -0.5 && along <= reach && across <= ActScript.CUT_WIDTH * 0.5 + target.getBbWidth() * 0.5 && Math.abs(rel.y) <= 2.5) {
               if (SinclairCombat.trickHit(player, target, ActScript.CUT_DAMAGE * this.power)) {
                  SinclairCombat.shove(target, target.position().subtract(side.scale(0.01)), 0.0, 0.6);
               }
            }
         }

         SinclairCombat.flash(player, start.add(dir.scale(reach * 0.5)), 1.0F);
      }

      if (t >= ActScript.CUT.pauseEnd() && line != null) {
         line.discard();
         this.propId = -1;
      }
   }

   // --- BARRAGE: "Card Fan" ---------------------------------------------------------------------
   private void barrage(ServerPlayer player, int t) {
      if (!ActScript.barrageVolley(t)) {
         return;
      }

      LivingEntity target = this.volunteer(player);
      if (target == null || target.distanceTo(player) > this.act.reach()) {
         target = this.nearestInFront(player);
      }

      this.focus = target != null ? target.getBoundingBox().getCenter() : Stagecraft.aimPoint(player, this.act.reach());
      Vec3 hand = Stagecraft.leftHand(player);
      for (int i = 0; i < ActScript.BARRAGE_CARDS_PER_VOLLEY; i++) {
         float spreadYaw = (i - 1) * 14.0F;
         Vec3 dir = Vec3.directionFromRotation(player.getXRot() - 8.0F, player.getYRot() + spreadYaw);
         ActCardEntity card = SinclairEntities.ACT_CARD.get().create(player.level());
         if (card == null) {
            continue;
         }

         card.launch(player, hand.add(dir.scale(0.3)), dir.scale(0.9));
         card.aim(target, this.power, this.act.color());
         player.level().addFreshEntity(card);
      }

      SinclairSounds.play(player.level(), hand, SinclairSounds.ACT_CARD_VOLLEY, 1.0F, 0.9F + t / 60.0F);
   }

   @Nullable
   private LivingEntity nearestInFront(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      for (LivingEntity e : SinclairCombat.enemiesNear(player, eye, this.act.reach())) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         if (SinclairRules.inCone(look.x, look.y, look.z, to.x, to.y, to.z, 35.0) && player.hasLineOfSight(e) && to.lengthSqr() < bestDist) {
            bestDist = to.lengthSqr();
            best = e;
         }
      }

      return best;
   }

   // --- AEGIS: "Curtain" ------------------------------------------------------------------------
   private void aegis(ServerPlayer player, int t) {
      if (t == 0) {
         this.spawnProp(player, ActPropEntity.AEGIS_CURTAIN, player.position(), ActScript.AEGIS.bowStart() + 2, (float) ActScript.AEGIS_RADIUS, t);
         SinclairSounds.play(player.level(), player.position().add(0.0, 1.0, 0.0), SinclairSounds.ACT_CURTAIN, 1.0F, 1.0F);
      }

      ActPropEntity curtain = this.prop(player);
      if (curtain != null) {
         curtain.place(player.position());
      }

      this.focus = player.position();
      if (t == ActScript.AEGIS.reveal()) {
         Vec3 at = player.position().add(0.0, 1.0, 0.0);
         for (LivingEntity target : SinclairCombat.enemiesNear(player, at, this.act.reach())) {
            if (SinclairCombat.trickHit(player, target, ActScript.AEGIS_DAMAGE * this.power)) {
               SinclairCombat.shove(target, at, 1.0, 0.4);
            }
         }

         SinclairSounds.play(player.level(), at, SinclairSounds.ACT_CURTAIN_OPEN, 1.2F, 1.0F);
         SinclairCombat.flash(player, at, 1.0F);
      }

      if (t >= ActScript.AEGIS.bowStart() && curtain != null) {
         curtain.discard();
         this.propId = -1;
      }
   }

   // --- FINALE: "Metamorphosis" ----------------------------------------------------------------
   private void finale(ServerPlayer player, int t) {
      if (t == 0) {
         LivingEntity target = this.volunteer(player);
         Vec3 point = target != null && target.distanceTo(player) <= this.act.reach() ? target.position() : ground(player, Stagecraft.aimPoint(player, this.act.reach()));
         this.focus = point;
         this.spawnProp(player, ActPropEntity.FINALE_CHEST, point.add(0.0, 7.0, 0.0), ActScript.FINALE.bowStart() + 2, (float) ActScript.FINALE_RADIUS, t);
      }

      ActPropEntity chest = this.prop(player);
      if (chest != null && t <= ActScript.FINALE_LAND) {
         float k = t / (float) ActScript.FINALE_LAND;
         chest.place(this.focus.add(0.0, 7.0 * (1.0 - k * k), 0.0));
      }

      if (t == ActScript.FINALE_LAND) {
         SinclairSounds.play(player.level(), this.focus, SinclairSounds.ACT_CHEST, 1.3F, 1.0F);
         ControlManager manager = player.level() instanceof ServerLevel level ? ControlManager.get(level) : null;
         long until = player.level().getGameTime() + (ActScript.FINALE.reveal() - ActScript.FINALE_LAND) + 10;
         for (LivingEntity victim : SinclairCombat.enemiesNear(player, this.focus.add(0.0, 0.5, 0.0), ActScript.FINALE_RADIUS + 0.5)) {
            this.victims.add(victim.getId());
            if (manager != null && HeroRegistry.allowsExternalControl(victim, ControlKind.FREEZE)
               && manager.tryAcquire(victim, player.getUUID(), this.freezeEffect, ControlKind.FREEZE, until)) {
               this.frozen.add(victim.getUUID());
            }
         }
      }

      if (ActScript.finaleSword(t)) {
         SinclairSounds.play(player.level(), this.focus.add(0.0, 1.0, 0.0), SinclairSounds.ACT_SWORD, 1.0F, 0.9F + t / 80.0F);
         ControlManager manager = player.level() instanceof ServerLevel level ? ControlManager.get(level) : null;
         for (Integer id : this.victims) {
            Entity entity = player.level().getEntity(id);
            if (!(entity instanceof LivingEntity victim) || !victim.isAlive()) {
               continue;
            }

            float damage = ActScript.FINALE_SWORD_DAMAGE * this.power;
            if (manager != null && this.frozen.contains(victim.getUUID())) {
               manager.queueDamage(victim, player.damageSources().indirectMagic(player, player), damage);
            } else {
               SinclairCombat.trickHit(player, victim, damage);
            }
         }
      }

      if (t == ActScript.FINALE.reveal()) {
         this.releaseFreeze(player);
         SinclairSounds.play(player.level(), this.focus.add(0.0, 1.0, 0.0), SinclairSounds.ACT_CURTAIN_OPEN, 1.0F, 1.2F);
         SinclairCombat.flash(player, this.focus.add(0.0, 1.0, 0.0), 1.2F);
         if (this.victims.size() == 1 && player.level().getEntity(this.victims.get(0)) instanceof LivingEntity victim && victim.isAlive()) {
            this.swapWith(player, victim);
         }
      }

      if (t >= ActScript.FINALE.bowStart() && chest != null) {
         chest.discard();
         this.propId = -1;
      }
   }

   private void swapWith(ServerPlayer player, LivingEntity victim) {
      if (!HeroRegistry.allowsImpulse(victim)) {
         return;
      }

      Optional<Vec3> forPlayer = SafeSpot.find(victim.position(), new SafeSpot.LevelProbe(player.level(), player), 2.0, 0.5);
      Optional<Vec3> forVictim = SafeSpot.find(player.position(), new SafeSpot.LevelProbe(player.level(), victim), 2.0, 0.5);
      if (forPlayer.isEmpty() || forVictim.isEmpty()) {
         return;
      }

      Vec3 a = forPlayer.get();
      Vec3 b = forVictim.get();
      victim.teleportTo(b.x, b.y, b.z);
      victim.fallDistance = 0.0F;
      player.teleportTo(a.x, a.y, a.z);
      player.fallDistance = 0.0F;
      player.setDeltaMovement(Vec3.ZERO);
      player.hurtMarked = true;
      SinclairSounds.play(player.level(), a.add(0.0, 1.0, 0.0), SinclairSounds.SWAP, 1.0F, 0.8F);
   }

   private static Vec3 ground(ServerPlayer player, Vec3 at) {
      BlockHitResult floor = player.level().clip(new ClipContext(at.add(0.0, 0.5, 0.0), at.add(0.0, -12.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return floor.getType() == HitResult.Type.MISS ? at : floor.getLocation();
   }

   // --- props and cleanup -----------------------------------------------------------------------
   @Nullable
   private ActPropEntity spawnProp(ServerPlayer player, int kind, Vec3 at, int life, float reach, int sceneTick) {
      ActPropEntity old = this.prop(player);
      if (old != null) {
         old.discard();
      }

      ActPropEntity prop = SinclairEntities.ACT_PROP.get().create(player.level());
      if (prop == null) {
         return null;
      }

      prop.setup(player, kind, at, this.yaw, this.act.color(), reach, sceneTick, life);
      player.level().addFreshEntity(prop);
      this.propId = prop.getId();
      return prop;
   }

   @Nullable
   private ActPropEntity prop(ServerPlayer player) {
      if (this.propId < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(this.propId);
      return entity instanceof ActPropEntity prop && !prop.isRemoved() ? prop : null;
   }

   private void releaseFreeze(ServerPlayer player) {
      if (!(player.level() instanceof ServerLevel level) || this.frozen.isEmpty()) {
         return;
      }

      ControlManager manager = ControlManager.get(level);
      for (UUID id : this.frozen) {
         manager.release(id, this.freezeEffect, ReleaseReason.NORMAL_END);
      }

      this.frozen.clear();
   }

   /** End or interrupt: props go, frozen victims are let go, the stage walk ends. */
   public void stop(ServerPlayer player, SinclairState state) {
      ActPropEntity prop = this.prop(player);
      if (prop != null) {
         prop.discard();
      }

      this.propId = -1;
      this.releaseFreeze(player);
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(STAGE_WALK_ID);
      }
   }
}
