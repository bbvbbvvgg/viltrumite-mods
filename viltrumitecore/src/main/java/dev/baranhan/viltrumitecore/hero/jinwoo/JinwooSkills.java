package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowMode;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shadow Step, Mutilation, Ruler's Authority, Extraction, Exchange and the Monarch's Charge (spec §5.4-5.8, §5.13). */
public final class JinwooSkills {
   private JinwooSkills() {
   }

   /** Literal keys so resource checks can see every translation (spec §5.7). */
   static String extractedKey(ShadowKind kind, boolean elite) {
      return switch (kind) {
         case INFANTRY -> "message.viltrumitecore.jinwoo.extracted_infantry";
         case KNIGHT -> elite ? "message.viltrumitecore.jinwoo.extracted_igris" : "message.viltrumitecore.jinwoo.extracted_knight";
         case INSECT -> elite ? "message.viltrumitecore.jinwoo.extracted_beru" : "message.viltrumitecore.jinwoo.extracted_insect";
         case BEAST -> elite ? "message.viltrumitecore.jinwoo.extracted_tank" : "message.viltrumitecore.jinwoo.extracted_beast";
      };
   }

   static void hint(ServerPlayer player, String key) {
      player.displayClientMessage(Component.translatable(key), true);
   }

   // --- Shadow Step (spec §5.4) ---

   static boolean step(ServerPlayer player, JinwooState state) {
      float pitch = JinwooRules.stepPitch(player.getXRot()) * Mth.DEG_TO_RAD;
      float yaw = player.getYRot() * Mth.DEG_TO_RAD;
      Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
      if (player.onGround() && dir.y < 0) {
         dir = new Vec3(dir.x, 0, dir.z).normalize();
      }
      double speed = JinwooRules.STEP_DISTANCE / JinwooRules.STEP_TICKS;
      JinwooCombat.autoDraw(player, state);
      state.start(JinwooMove.STEP, -1);
      state.moveDir = dir;
      state.moveOrigin = player.position();
      state.iframes = JinwooRules.STEP_IFRAMES;
      JinwooFx.motion(player, dir.scale(speed), JinwooRules.STEP_TICKS);
      JinwooFx.around(player, JinwooFx.STEP, 0, player.position(), player.position().add(dir.scale(JinwooRules.STEP_DISTANCE)));
      JinwooSounds.play(player, JinwooSounds.STEP, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
      return true;
   }

   /** Cuts every enemy the step passed this tick (swept box of the last move). */
   static void stepTick(ServerPlayer player, JinwooState state) {
      if (state.elapsed > JinwooRules.STEP_TICKS + 1 || state.moveOrigin == null) {
         return;
      }
      Vec3 from = state.moveOrigin;
      Vec3 to = player.position();
      AABB box = new AABB(from, to).inflate(1.1, 1.0, 1.1).expandTowards(0, 1.0, 0);
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> JinwooCombat.mayHit(player, e))) {
         if (state.hit.add(e.getUUID())) {
            JinwooCombat.strike(player, state, e, JinwooRules.STEP_DAMAGE, false, 2);
         }
      }
      state.moveOrigin = to;
   }

   // --- Mutilation (spec §5.5) ---

   static boolean rush(ServerPlayer player, JinwooState state) {
      List<LivingEntity> cone = JinwooTargets.cone(player, JinwooRules.RUSH_RANGE, JinwooRules.RUSH_CONE_DEGREES, e -> JinwooCombat.mayHit(player, e));
      LivingEntity target = cone.isEmpty() ? null : cone.get(0);
      JinwooCombat.autoDraw(player, state);
      state.start(JinwooMove.RUSH, target == null ? -1 : target.getId());
      if (target != null) {
         Vec3 back = player.position().subtract(target.position());
         Vec3 h = new Vec3(back.x, 0, back.z);
         h = h.lengthSqr() < 1.0E-4 ? player.getLookAngle().scale(-1) : h.normalize();
         Vec3 wanted = target.position().add(h.scale(target.getBbWidth() * 0.5 + 1.1));
         Vec3 spot = SafeSpot.find(wanted, new SafeSpot.LevelProbe(player.level(), player), 2.0, 0.5).orElse(null);
         if (spot != null && !HeroDamage.isAnchored(player)) {
            JinwooFx.around(player, JinwooFx.STEP, 1, player.position(), spot);
            float yaw = (float)(Mth.atan2(-(target.getX() - spot.x), target.getZ() - spot.z) * Mth.RAD_TO_DEG);
            player.connection.teleport(spot.x, spot.y, spot.z, yaw, player.getXRot());
         }
      }
      JinwooSounds.play(player, JinwooSounds.RUSH, 1.0F, 1.0F);
      return true;
   }

   static void rushTick(ServerPlayer player, JinwooState state) {
      int t = state.elapsed;
      player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
      Entity locked = state.targetId >= 0 ? player.level().getEntity(state.targetId) : null;
      LivingEntity target = locked instanceof LivingEntity l && l.isAlive() && l.distanceToSqr(player) < 6 * 6 ? l : null;
      if (JinwooRules.rushHitTick(t)) {
         int i = (t - JinwooRules.RUSH_APPROACH) / (JinwooRules.RUSH_HIT_TICKS / JinwooRules.RUSH_HITS);
         if (target != null) {
            JinwooCombat.strike(player, state, target, JinwooRules.RUSH_DAMAGE, true, i % 2);
         } else {
            for (LivingEntity e : JinwooTargets.around(player, player.position().add(0, 1, 0), JinwooRules.RUSH_SELF_RADIUS, e -> JinwooCombat.mayHit(player, e))) {
               JinwooCombat.strike(player, state, e, JinwooRules.RUSH_DAMAGE, true, i % 2);
            }
         }
         JinwooSounds.play(player, JinwooSounds.DAGGER_SWING, 0.6F, 1.2F + (i % 3) * 0.08F);
      }
      if (t == JinwooRules.RUSH_FINAL_TICK) {
         List<LivingEntity> victims = new ArrayList<>();
         if (target != null) {
            victims.add(target);
         } else {
            victims.addAll(JinwooTargets.around(player, player.position().add(0, 1, 0), JinwooRules.RUSH_SELF_RADIUS, e -> JinwooCombat.mayHit(player, e)));
         }
         int[] ids = new int[victims.size() + 1];
         int n = 0;
         for (LivingEntity v : victims) {
            if (JinwooCombat.strike(player, state, v, JinwooRules.RUSH_FINAL, true, 2)) {
               ids[n++] = v.getId();
               if (HeroRegistry.allowsImpulse(v)) {
                  Vec3 d = v.position().subtract(player.position());
                  Vec3 h = new Vec3(d.x, 0, d.z);
                  h = h.lengthSqr() < 1.0E-4 ? player.getLookAngle() : h.normalize();
                  v.setDeltaMovement(h.x * JinwooRules.RUSH_FINAL_KNOCKBACK, 0.35, h.z * JinwooRules.RUSH_FINAL_KNOCKBACK);
                  v.hurtMarked = true;
               }
            }
         }
         ids[n++] = player.getId();
         HeroFx.hitStop(player, player.position(), 3, java.util.Arrays.copyOf(ids, n));
         JinwooSounds.play(player, JinwooSounds.FINISHER, 1.0F, 0.9F);
      }
   }

   // --- Ruler's Authority (spec §5.6) ---

   static boolean authorityGrip(ServerPlayer player, JinwooState state) {
      List<LivingEntity> cone = JinwooTargets.cone(player, JinwooRules.AUTHORITY_RANGE, JinwooRules.AUTHORITY_CONE_DEGREES,
         e -> JinwooCombat.mayHit(player, e) && HeroRegistry.allowsImpulse(e));
      if (cone.isEmpty()) {
         return false;
      }
      state.held.clear();
      for (LivingEntity e : cone) {
         if (state.held.size() >= JinwooRules.AUTHORITY_TARGETS) break;
         state.held.add(e.getId());
         e.setNoGravity(true);
         e.fallDistance = 0.0F;
      }
      state.holdTicks = 0;
      state.holdReleased = false;
      state.start(JinwooMove.AUTHORITY_GRIP, state.held.get(0));
      JinwooFx.around(player, JinwooFx.AUTHORITY, 1, player.position(), Vec3.ZERO, state.held.stream().mapToInt(Integer::intValue).toArray());
      JinwooSounds.play(player, JinwooSounds.AUTHORITY_GRIP, 1.0F, 1.0F);
      return true;
   }

   static void authorityRelease(ServerPlayer player, JinwooState state) {
      if (state.is(JinwooMove.AUTHORITY_GRIP)) {
         state.holdReleased = true;
      }
   }

   static void authorityTick(ServerPlayer player, JinwooState state) {
      if (state.is(JinwooMove.AUTHORITY_GRIP)) {
         state.holdTicks++;
         int i = 0;
         Vec3 eye = player.getEyePosition();
         Vec3 look = player.getLookAngle();
         double lift = JinwooRules.AUTHORITY_LIFT * Math.min(1.0, state.holdTicks / (double)JinwooRules.AUTHORITY_LIFT_TICKS);
         for (int id : state.held) {
            if (!(player.level().getEntity(id) instanceof LivingEntity e) || !e.isAlive() || HeroDamage.isAnchored(e)) {
               i++;
               continue;
            }
            double dist = Math.max(3.0, Math.min(JinwooRules.AUTHORITY_RANGE, e.distanceTo(player)));
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale((i - (state.held.size() - 1) * 0.5) * 1.4);
            Vec3 goal = eye.add(look.scale(dist)).add(side);
            goal = new Vec3(goal.x, Math.max(goal.y, player.getY() + lift * 0.6), goal.z);
            if (state.holdTicks <= JinwooRules.AUTHORITY_LIFT_TICKS) {
               goal = new Vec3(e.getX(), Math.max(e.getY(), player.getY() + lift), e.getZ());
            }
            Vec3 v = goal.subtract(e.position()).scale(0.35);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
            if (e instanceof Mob mob) mob.getNavigation().stop();
            i++;
         }
         if (state.holdReleased || state.holdTicks >= JinwooRules.AUTHORITY_HOLD) {
            slam(player, state);
         }
      } else if (state.is(JinwooMove.AUTHORITY_SLAM)) {
         Vec3 look = player.getLookAngle();
         Vec3 h = new Vec3(look.x, 0, look.z);
         h = h.lengthSqr() < 1.0E-4 ? Vec3.ZERO : h.normalize();
         for (int id : state.held) {
            if (!(player.level().getEntity(id) instanceof LivingEntity e) || !e.isAlive()) continue;
            e.fallDistance = 0.0F;
            boolean impact = e.onGround() || state.elapsed >= JinwooRules.AUTHORITY_SLAM_TICKS - 1;
            if (state.elapsed < 3) {
               e.setDeltaMovement(h.x * 0.6, -2.2, h.z * 0.6);
               e.hurtMarked = true;
            }
            if (impact && state.hit.add(e.getUUID())) {
               e.setNoGravity(false);
               JinwooCombat.strike(player, state, e, JinwooRules.AUTHORITY_DAMAGE, true, 2);
               JinwooFx.around(player, JinwooFx.WAVE, 2, e.position(), new Vec3(2.0, 0, 0));
               JinwooSounds.playAt(player, e.position(), JinwooSounds.AUTHORITY_SLAM, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
         }
      }
   }

   private static void slam(ServerPlayer player, JinwooState state) {
      state.start(JinwooMove.AUTHORITY_SLAM, state.held.isEmpty() ? -1 : state.held.get(0));
      JinwooFx.around(player, JinwooFx.AUTHORITY, 0, player.position(), Vec3.ZERO, state.held.stream().mapToInt(Integer::intValue).toArray());
   }

   /** Every held body gets its gravity back (end, cleanup, death). */
   static void releaseHeld(ServerPlayer player, JinwooState state) {
      for (int id : state.held) {
         if (player.level().getEntity(id) instanceof LivingEntity e) {
            e.setNoGravity(false);
            e.fallDistance = 0.0F;
         }
      }
      if (!state.held.isEmpty()) {
         JinwooFx.around(player, JinwooFx.AUTHORITY, 0, player.position(), Vec3.ZERO);
      }
      state.held.clear();
   }

   // --- Extraction (spec §5.7) ---

   static boolean extract(ServerPlayer player, JinwooState state) {
      Remnants.Remnant remnant = Remnants.nearest(player.serverLevel(), player.position(), JinwooRules.EXTRACT_RADIUS);
      if (remnant == null) {
         hint(player, "message.viltrumitecore.jinwoo.no_remnant");
         return false;
      }
      if (state.roster.full()) {
         hint(player, "message.viltrumitecore.jinwoo.roster_full");
         return false;
      }
      remnant.busy = true;
      state.extractRemnant = remnant.id;
      state.start(JinwooMove.EXTRACT, -1);
      state.moveOrigin = remnant.pos;
      HeroSpeech.ability(player, JinwooAbility.EXTRACTION.id());
      JinwooSounds.play(player, JinwooSounds.EXTRACT_CHANNEL, 1.0F, 1.0F);
      return true;
   }

   static void extractTick(ServerPlayer player, JinwooState state) {
      if (state.elapsed != JinwooRules.EXTRACT_CHANNEL) {
         return;
      }
      ServerLevel level = player.serverLevel();
      Remnants.Remnant remnant = Remnants.byId(level, state.extractRemnant);
      state.extractRemnant = -1;
      if (remnant == null) {
         return;
      }
      double chance = JinwooRules.extractChance(remnant.tier, remnant.failures);
      if (player.getRandom().nextDouble() < chance) {
         ShadowRoster.Entry entry = state.roster.addExtracted(remnant.kind);
         Remnants.remove(level, remnant, 1);
         if (entry == null) {
            hint(player, "message.viltrumitecore.jinwoo.roster_full");
            return;
         }
         JinwooSounds.playAt(player, remnant.pos, JinwooSounds.EXTRACT_RISE, 1.2F, entry.elite ? 0.8F : 1.0F);
         ShadowSoldierEntity s = ShadowArmy.spawn(player, state, entry, remnant.pos, state.roster.activeCount());
         hint(player, extractedKey(entry.kind, entry.elite));
         if (s == null) {
            JinwooFx.around(player, JinwooFx.EXTRACT_FAIL, 1, remnant.pos, Vec3.ZERO);
         }
      } else {
         remnant.failures++;
         remnant.busy = false;
         JinwooFx.around(player, JinwooFx.EXTRACT_FAIL, 0, remnant.pos, Vec3.ZERO);
         JinwooSounds.playAt(player, remnant.pos, JinwooSounds.EXTRACT_FAIL, 1.0F, 1.0F);
         if (remnant.failures >= JinwooRules.EXTRACT_ATTEMPTS) {
            Remnants.remove(level, remnant, 0);
            hint(player, "message.viltrumitecore.jinwoo.remnant_crumbled");
         } else {
            hint(player, "message.viltrumitecore.jinwoo.extract_failed");
         }
      }
   }

   static void cancelExtract(ServerPlayer player, JinwooState state) {
      if (state.extractRemnant >= 0) {
         Remnants.Remnant r = Remnants.byId(player.serverLevel(), state.extractRemnant);
         if (r != null) r.busy = false;
         state.extractRemnant = -1;
         HeroSpeech.interrupt(player, JinwooAbility.EXTRACTION.id());
      }
   }

   // --- Exchange (spec §5.8) ---

   static boolean exchange(ServerPlayer player, JinwooState state) {
      ShadowSoldierEntity partner = ShadowArmy.exchangePartner(player, state);
      if (partner == null) {
         hint(player, "message.viltrumitecore.jinwoo.no_shadow");
         return false;
      }
      state.start(JinwooMove.EXCHANGE, partner.getId());
      JinwooFx.around(player, JinwooFx.EXCHANGE, partner.getId(), player.position(), partner.position());
      JinwooSounds.play(player, JinwooSounds.EXCHANGE, 1.0F, 1.0F);
      return true;
   }

   static void exchangeTick(ServerPlayer player, JinwooState state) {
      player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
      if (state.elapsed != JinwooRules.EXCHANGE_SINK) {
         return;
      }
      Entity e = player.level().getEntity(state.targetId);
      if (!(e instanceof ShadowSoldierEntity partner) || !partner.isAlive() || HeroDamage.isAnchored(player)) {
         return;
      }
      Vec3 mine = player.position();
      Vec3 theirs = partner.position();
      Vec3 spot = SafeSpot.find(theirs, new SafeSpot.LevelProbe(player.level(), player), 3.0, 0.5).orElse(null);
      if (spot == null) {
         return;
      }
      boolean wasSentinel = partner.mode() == ShadowMode.SENTINEL;
      player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
      player.fallDistance = 0.0F;
      if (wasSentinel) {
         ShadowArmy.dropSentinel(player, state, partner);
      }
      partner.teleportTo(mine.x, mine.y, mine.z);
      partner.level().broadcastEntityEvent(partner, ShadowSoldierEntity.EVENT_RISE);
      JinwooFx.around(player, JinwooFx.EXCHANGE, -1, spot, mine);
      JinwooSounds.playAt(player, spot, JinwooSounds.EXCHANGE, 1.0F, 1.2F);
   }

   // --- Monarch's Charge (spec §5.13) ---

   static boolean charge(ServerPlayer player, JinwooState state) {
      List<ShadowSoldierEntity> squad = ShadowArmy.squad(player, state);
      if (squad.isEmpty()) {
         hint(player, "message.viltrumitecore.jinwoo.no_shadow");
         return false;
      }
      Vec3 look = player.getLookAngle();
      Vec3 dir = new Vec3(look.x, 0, look.z);
      dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
      JinwooCombat.autoDraw(player, state);
      state.start(JinwooMove.CHARGE, -1);
      state.moveDir = dir;
      state.moveOrigin = player.position();
      for (ShadowSoldierEntity s : squad) {
         s.brain.reset();
         s.setMode(ShadowMode.CHARGE);
         s.getNavigation().stop();
      }
      JinwooFx.motion(player, dir.scale((double)JinwooRules.CHARGE_LENGTH_BLOCKS / JinwooRules.CHARGE_TICKS), JinwooRules.CHARGE_TICKS);
      JinwooFx.around(player, JinwooFx.CHARGE, 0, player.position(), player.position().add(dir.scale(JinwooRules.CHARGE_LENGTH_BLOCKS)));
      JinwooSounds.play(player, JinwooSounds.CHARGE, 1.2F, 1.0F);
      return true;
   }

   static void chargeTick(ServerPlayer player, JinwooState state) {
      List<ShadowSoldierEntity> squad = ShadowArmy.squad(player, state);
      Vec3 dir = state.moveDir;
      Vec3 origin = state.moveOrigin == null ? player.position() : state.moveOrigin;
      int t = state.elapsed;
      Vec3 right = new Vec3(-dir.z, 0, dir.x);
      if (t <= JinwooRules.CHARGE_TICKS) {
         Vec3 apex = origin.add(dir.scale(JinwooRules.chargeDistance(t)));
         int i = 0;
         for (ShadowSoldierEntity s : squad) {
            if (s.mode() != ShadowMode.CHARGE) continue;
            double[] slot = JinwooRules.wedgeSlot(i++);
            Vec3 goal = apex.add(right.scale(slot[0])).subtract(dir.scale(slot[1] - 1.8));
            Vec3 v = goal.subtract(s.position());
            s.setDeltaMovement(v.x * 0.7, s.getDeltaMovement().y, v.z * 0.7);
            s.setYRot((float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
            s.setYBodyRot(s.getYRot());
            s.setYHeadRot(s.getYRot());
            for (LivingEntity e : JinwooTargets.around(s, s.getBoundingBox().getCenter(), 1.6, e -> JinwooCombat.mayHit(player, e) && s.mayHit(e))) {
               if (corridor(origin, dir, e.position()) && state.hit.add(e.getUUID())) {
                  float bonus = s.empowered() ? JinwooRules.DOMAIN_DAMAGE_BONUS : 1.0F;
                  if (e.hurt(s.damageSources().mobAttack(s), JinwooRules.CHARGE_DAMAGE * bonus)) {
                     JinwooFx.around(player, JinwooFx.CONTACT, 2, e.getBoundingBox().getCenter(), s.position());
                     if (HeroRegistry.allowsImpulse(e)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.6, 0.3, dir.z * 0.6));
                        e.hurtMarked = true;
                     }
                  }
               }
            }
         }
         // Jinwoo cuts through the wave's victims and anything in his own path.
         for (LivingEntity e : JinwooTargets.around(player, player.getBoundingBox().getCenter(), 1.8, e -> JinwooCombat.mayHit(player, e))) {
            if (corridor(origin, dir, e.position())) {
               state.hit.add(e.getUUID());
            }
         }
      }
      if (t == JinwooRules.CHARGE_TICKS) {
         List<Integer> ids = new ArrayList<>();
         for (LivingEntity e : JinwooTargets.around(player, origin.add(dir.scale(JinwooRules.CHARGE_LENGTH_BLOCKS * 0.5)), JinwooRules.CHARGE_LENGTH_BLOCKS * 0.6,
            e -> JinwooCombat.mayHit(player, e))) {
            if (state.hit.contains(e.getUUID()) && corridor(origin, dir, e.position()) && JinwooCombat.strike(player, state, e, JinwooRules.CHARGE_FINISH, true, 2)) {
               ids.add(e.getId());
            }
         }
         ids.add(player.getId());
         HeroFx.hitStop(player, player.position(), 2, ids.stream().mapToInt(Integer::intValue).toArray());
         JinwooSounds.play(player, JinwooSounds.FINISHER, 1.0F, 0.8F);
         endCharge(player, state);
      }
   }

   static void endCharge(ServerPlayer player, JinwooState state) {
      for (ShadowSoldierEntity s : ShadowArmy.squad(player, state)) {
         if (s.mode() == ShadowMode.CHARGE) {
            s.setMode(state.order == JinwooState.ArmyOrder.GUARD ? ShadowMode.GUARD : state.order == JinwooState.ArmyOrder.ATTACK ? ShadowMode.ATTACK : ShadowMode.FOLLOW);
         }
      }
   }

   /** Inside the 14 × 4 corridor of the charge. */
   static boolean corridor(Vec3 origin, Vec3 dir, Vec3 at) {
      Vec3 d = at.subtract(origin);
      double along = d.x * dir.x + d.z * dir.z;
      double side = Math.abs(d.x * -dir.z + d.z * dir.x);
      return along >= -1.0 && along <= JinwooRules.CHARGE_LENGTH_BLOCKS + 2.0 && side <= JinwooRules.CHARGE_WIDTH * 0.5 + 0.8;
   }

   static boolean isPlayer(Entity e) {
      return e instanceof Player;
   }
}
