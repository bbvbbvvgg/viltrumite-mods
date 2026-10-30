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

   /** NOVA: the hat is off the head (spun in the hand from NOVA_HAT_OFF, then a flying prop until the catch). */
   public boolean hatAway(int tick) {
      return this.shape() == ActShape.NOVA && tick >= ActScript.NOVA_HAT_OFF && tick < ActScript.NOVA_CATCH;
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
         // waits for the act's line instead of cutting it (the line card stays readable)
         SinclairLines.say(player, SinclairLines.EVENT_BOW, true);
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

   // --- BEAM: "Footlight Ray" (Homelander's heat vision, Sinclair's way: the card as a mirror) ----------
   // Tension: the footlights pour into the raised card. Main: the ray sweeps with the aim, a pulse every
   // 5 t. Climax: the ray thins to a thread in the hush, then the overdrive flare. Finish: dust, the bow.
   private void beam(ServerPlayer player, int t) {
      Vec3 from = Stagecraft.leftHand(player);
      Vec3 end = from.add(player.getLookAngle().scale(this.act.reach()));
      BlockHitResult block = player.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }

      if (t < ActScript.BEAM_BURST_END) {
         this.focus = end;
      }

      if (t == 2) {
         SinclairSounds.play(player.level(), from, SinclairSounds.ACT_FOOTLIGHTS, 1.0F, 1.0F);
      } else if (t == ActScript.BEAM_START) {
         SinclairSounds.play(player.level(), from, SinclairSounds.ACT_BEAM, 1.2F, 1.0F);
         SinclairCombat.flash(player, from, 0.6F);
      } else if (t == ActScript.BEAM_END) {
         SinclairSounds.play(player.level(), from, SinclairSounds.ACT_BEAM_CHARGE, 1.0F, 1.0F);
      } else if (t == ActScript.BEAM_BURST) {
         SinclairSounds.play(player.level(), from, SinclairSounds.ACT_BEAM_BURST, 1.4F, 1.0F);
         SinclairSounds.play(player.level(), end, SinclairSounds.ACT_BEAM_BURST, 1.0F, 0.85F);
         SinclairCombat.flash(player, end, 1.4F);
      }

      boolean burst = t == ActScript.BEAM_BURST;
      if (!ActScript.beamPulse(t) && !burst) {
         return;
      }

      double width = burst ? ActScript.BEAM_BURST_WIDTH : ActScript.BEAM_WIDTH;
      float damage = (burst ? ActScript.BEAM_BURST_DAMAGE : ActScript.BEAM_DAMAGE) * this.power;
      AABB box = new AABB(from, end).inflate(width);
      for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> SinclairCombat.enemy(player, e))) {
         AABB body = target.getBoundingBox().inflate(width * 0.5);
         if (body.contains(from) || body.clip(from, end).isPresent()) {
            if (SinclairCombat.trickHit(player, target, damage) && burst) {
               SinclairCombat.shove(target, from, 0.9, 0.3);
            }
         }
      }
   }

   // --- NOVA: "Hat Whirl" -----------------------------------------------------------------------
   // Tension: the hat spins on the fingers. Main: thrown, it whirls over the volunteer and draws everyone
   // in. Climax: the hush, the burst. Finish: the hat flies home, the bow.
   private void nova(ServerPlayer player, int t) {
      ActPropEntity hat = this.prop(player);
      if (t == ActScript.NOVA_HAT_OFF) {
         SinclairSounds.play(player.level(), Stagecraft.leftHand(player), SinclairSounds.ACT_HAT_SPIN, 1.0F, 1.0F);
      }

      if (t == ActScript.NOVA_THROW) {
         LivingEntity target = this.volunteer(player);
         Vec3 to = target != null && target.distanceTo(player) <= this.act.reach() + 2.0 ? target.position().add(0.0, 1.2, 0.0)
            : Stagecraft.aimPoint(player, this.act.reach());
         this.focus = to;
         this.hatFrom = Stagecraft.leftHand(player);
         hat = this.spawnProp(player, ActPropEntity.NOVA_HAT, this.hatFrom, ActScript.NOVA_CATCH - ActScript.NOVA_THROW + 4, 1.0F, t);
         SinclairSounds.play(player.level(), this.hatFrom, SinclairSounds.ACT_NOVA_WHIRL, 1.0F, 1.0F);
      }

      if (hat == null) {
         return;
      }

      int burst = ActScript.NOVA.reveal();
      int arrive = ActScript.NOVA_ARRIVE;
      if (t > ActScript.NOVA_THROW && t <= arrive) {
         float k = (t - ActScript.NOVA_THROW) / (float) (arrive - ActScript.NOVA_THROW);
         float e = 1.0F - (1.0F - k) * (1.0F - k);
         hat.place(this.hatFrom.lerp(this.focus, e).add(0.0, Math.sin(k * Math.PI) * 1.2, 0.0));
      } else if (t > arrive && t < burst) {
         hat.place(this.focus);
      }

      if (t == arrive + 8) {
         SinclairSounds.play(player.level(), this.focus, SinclairSounds.ACT_NOVA_WHIRL, 0.9F, 1.15F);
      }

      if (t > ActScript.NOVA_THROW && t < burst) {
         // The whirl pulls everyone near the hat in, harder as it winds up (impulse-allowed bodies only).
         double pullStrength = t < arrive ? 0.03 : 0.04 + 0.04 * (t - arrive) / (double) (burst - arrive);
         for (LivingEntity target : SinclairCombat.enemiesNear(player, hat.position(), ActScript.NOVA_RADIUS + 2.0)) {
            if (HeroRegistry.allowsImpulse(target)) {
               Vec3 pull = hat.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
               if (pull.lengthSqr() > 0.25) {
                  target.setDeltaMovement(target.getDeltaMovement().add(pull.normalize().scale(pullStrength)));
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

      int home = ActScript.NOVA.pauseEnd();
      if (t > home && t < ActScript.NOVA_CATCH) {
         float k = (t - home) / (float) (ActScript.NOVA_CATCH - home);
         Vec3 hand = Stagecraft.leftHand(player);
         hat.place(this.focus.lerp(hand, k * k).add(0.0, Math.sin(k * Math.PI) * 1.5, 0.0));
      } else if (t >= ActScript.NOVA_CATCH) {
         hat.discard();
         this.propId = -1;
      }
   }

   // --- CUT: "Sawing" (Reinhard's World Cut as the sawing-in-half trick) -------------------------
   // Tension: the card blade grows and the line is drawn. Main: two saw strokes along the line. Climax:
   // the blade overhead in the hush, the grand cut. Finish: the seam glows and closes, the bow.
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
      boolean saw = ActScript.cutSaw(t);
      boolean grand = t == ActScript.CUT.reveal();
      if ((saw || grand) && line != null) {
         Vec3 start = line.position();
         Vec3 dir = Vec3.directionFromRotation(0.0F, this.yaw);
         Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
         double reach = this.act.reach();
         Vec3 mid = start.add(dir.scale(reach * 0.5));
         AABB box = new AABB(start, start.add(dir.scale(reach))).inflate(ActScript.CUT_WIDTH + 1.0, 3.0, ActScript.CUT_WIDTH + 1.0);
         for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> SinclairCombat.enemy(player, e))) {
            Vec3 rel = target.position().subtract(start);
            double along = rel.dot(dir);
            double across = Math.abs(rel.dot(side));
            if (along >= -0.5 && along <= reach && across <= ActScript.CUT_WIDTH * 0.5 + target.getBbWidth() * 0.5 && Math.abs(rel.y) <= 2.5) {
               if (SinclairCombat.trickHit(player, target, (grand ? ActScript.CUT_DAMAGE : ActScript.CUT_SAW_DAMAGE) * this.power) && grand) {
                  SinclairCombat.shove(target, target.position().subtract(side.scale(0.01)), 0.0, 0.6);
               }
            }
         }

         if (grand) {
            SinclairSounds.play(player.level(), mid, SinclairSounds.ACT_CUT_SLASH, 1.4F, 1.0F);
            SinclairCombat.flash(player, mid, 1.0F);
         } else {
            SinclairSounds.play(player.level(), mid, SinclairSounds.ACT_SAW_RASP, 1.1F, t == ActScript.CUT_SAWS[0] ? 1.0F : 1.12F);
         }
      }

      if (t >= ActScript.CUT.pauseEnd() && line != null) {
         line.discard();
         this.propId = -1;
      }
   }

   // --- BARRAGE: "Card Fan" (Iron Man's micro-missiles as a conjurer's fan) ----------------------
   // Tension: a fan of twelve cards opens. Main: three conducted volleys. Climax: the last three gather
   // overhead in the hush and fly together. Finish: the bow.
   private void barrage(ServerPlayer player, int t) {
      if (t == 1) {
         SinclairSounds.play(player.level(), Stagecraft.leftHand(player), SinclairSounds.ACT_CARD_FAN, 1.0F, 1.0F);
      } else if (t == ActScript.BARRAGE.hushStart()) {
         SinclairSounds.play(player.level(), Stagecraft.leftHand(player), SinclairSounds.ACT_CARD_FAN, 0.8F, 1.25F);
      }

      if (!ActScript.barrageVolley(t)) {
         return;
      }

      boolean finale = ActScript.barrageFinale(t);
      LivingEntity target = this.volunteer(player);
      if (target == null || target.distanceTo(player) > this.act.reach()) {
         target = this.nearestInFront(player);
      }

      this.focus = target != null ? target.getBoundingBox().getCenter() : Stagecraft.aimPoint(player, this.act.reach());
      Vec3 hand = finale ? player.getEyePosition().add(0.0, 0.9, 0.0) : Stagecraft.leftHand(player);
      for (int i = 0; i < ActScript.BARRAGE_CARDS_PER_VOLLEY; i++) {
         float spreadYaw = (i - 1) * (finale ? 5.0F : 14.0F);
         Vec3 dir = Vec3.directionFromRotation(player.getXRot() - (finale ? 2.0F : 8.0F), player.getYRot() + spreadYaw);
         ActCardEntity card = SinclairEntities.ACT_CARD.get().create(player.level());
         if (card == null) {
            continue;
         }

         card.launch(player, hand.add(dir.scale(0.3)), dir.scale(finale ? 1.15 : 0.9));
         card.aim(target, this.power, this.act.color());
         player.level().addFreshEntity(card);
      }

      if (finale) {
         SinclairSounds.play(player.level(), hand, SinclairSounds.ACT_CARD_FINALE, 1.3F, 1.0F);
      } else {
         SinclairSounds.play(player.level(), hand, SinclairSounds.ACT_CARD_VOLLEY, 1.0F, 0.9F + t / 100.0F);
      }
   }

   @Nullable
   private LivingEntity nearestInFront(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      for (LivingEntity e : SinclairCombat.enemiesNear(player, eye, this.act.reach())) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         if (!dev.baranhan.viltrumitecore.entity.Bystander.is(e) && SinclairRules.inCone(look.x, look.y, look.z, to.x, to.y, to.z, 35.0) && player.hasLineOfSight(e) && to.lengthSqr() < bestDist) {
            bestDist = to.lengthSqr();
            best = e;
         }
      }

      return best;
   }

   // --- AEGIS: "Curtain" (Regulus' invulnerable heart as a velvet curtain) ----------------------
   // Tension: the curtain falls. Main: the slow turn inside, the curtain ripples (-75 % damage, reflect).
   // Climax: the hush, it bursts open. Finish: the shreds rain, the bow.
   private void aegis(ServerPlayer player, int t) {
      if (t == 0) {
         this.spawnProp(player, ActPropEntity.AEGIS_CURTAIN, player.position(), ActScript.AEGIS.pauseEnd() + 2, (float) ActScript.AEGIS_RADIUS, t);
         SinclairSounds.play(player.level(), player.position().add(0.0, 1.0, 0.0), SinclairSounds.ACT_CURTAIN, 1.0F, 1.0F);
      } else if (t == ActScript.AEGIS.main() + 2 || t == ActScript.AEGIS.main() + 18) {
         SinclairSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), SinclairSounds.ACT_CURTAIN_RUSTLE, 0.9F, t > 20 ? 1.08F : 1.0F);
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

         SinclairSounds.play(player.level(), at, SinclairSounds.ACT_CURTAIN_OPEN, 1.3F, 1.0F);
         SinclairCombat.flash(player, at, 1.0F);
      }

      if (t >= ActScript.AEGIS.pauseEnd() && curtain != null) {
         curtain.discard();
         this.propId = -1;
      }
   }

   // --- FINALE: "Metamorphosis" (the spare number for every volunteer without a parody) -----------
   // Tension: the wand calls the chest down. Main: three swords through the lid. Climax: the chest rattles
   // in the hush, the lid flies open and the two swap places. Finish: the bow.
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

      if (t == ActScript.FINALE.hushStart()) {
         SinclairSounds.play(player.level(), this.focus.add(0.0, 0.6, 0.0), SinclairSounds.ACT_CHEST_RATTLE, 1.1F, 1.0F);
      }

      if (ActScript.finaleSword(t)) {
         SinclairSounds.play(player.level(), this.focus.add(0.0, 1.0, 0.0), SinclairSounds.ACT_SWORD, 1.0F, 0.92F + (t - ActScript.FINALE_SWORDS[0]) / 60.0F);
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
