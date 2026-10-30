package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «Маленький король» (spec §5.7). Regulus places his stopped heart into a willing, living,
 * non-hostile being near him (MMB, ≤6 blocks, in sight, at most four subjects). While the heart
 * beats inside a living host, Lion's Heart costs no strain. The canon limits are mechanical: the
 * host must live, move (not frozen in time or bound), and stay within 48 blocks; otherwise the
 * heart hops to the next subject or, with none left, returns and the strain runs again. A killed
 * host lashes back (+350 strain). The heart is visible on the host within 24 blocks — an enemy who
 * finds and kills the subjects breaks the invulnerability.
 */
public final class LittleKing {
   private LittleKing() {
   }

   /** ASSIGN_HEART press (MMB): bind the looked-at being, release it if bound, crouch = release all. */
   static void press(ServerPlayer player, RegulusState state) {
      if (state.busy()) {
         return;
      }

      LivingEntity target = pick(player, state);
      if (target == null) {
         if (player.isShiftKeyDown() && !state.subjects.isEmpty()) {
            releaseAll(player, state, true);
         } else {
            RegulusHero.refuse(player, state);
         }

         return;
      }

      if (state.subjects.contains(target.getUUID())) {
         release(player, state, target);
         return;
      }

      if (state.subjects.size() >= RegulusRules.KING_MAX_SUBJECTS) {
         RegulusHero.refuse(player, state);
         return;
      }

      state.castSubject = target.getUUID();
      state.startMove(RegulusMove.LITTLE_KING);
      HeroSpeech.ability(player, RegulusAbilities.LITTLE_KING);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move != RegulusMove.LITTLE_KING || state.moveTick != RegulusRules.KING_BIND_AT || state.castSubject == null) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      Entity entity = level.getEntity(state.castSubject);
      state.castSubject = null;
      if (!(entity instanceof LivingEntity target) || !eligible(player, state, target) || target.distanceTo(player) > RegulusRules.KING_RANGE + 1.5
         || state.subjects.size() >= RegulusRules.KING_MAX_SUBJECTS || state.subjects.contains(target.getUUID())) {
         return;
      }

      state.subjects.add(target.getUUID());
      if (state.host == null) {
         state.host = target.getUUID();
         state.hopTimer = RegulusRules.KING_HOP_TICKS;
      }

      Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
      RegulusSounds.play(level, at, RegulusSounds.KING_BIND, 0.9F, 1.0F);
      RegulusNet.fx(level, RegulusFxS2CPacket.KING_BIND, player, target.getId(), at);
   }

   /** Is the heart beating in a living host right now (the strain-free Lion)? */
   static boolean hostAlive(ServerPlayer player, RegulusState state) {
      if (state.host == null) {
         return false;
      }

      Entity entity = ((ServerLevel) player.level()).getEntity(state.host);
      return entity instanceof LivingEntity living && hostValid(player, living);
   }

   /** Every tick: prune lost subjects, keep the heart in a valid host, beat. */
   static void tick(ServerPlayer player, RegulusState state) {
      if (state.subjects.isEmpty()) {
         state.host = null;
         state.hopTimer = 0;
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      boolean hostDied = false;
      for (int i = state.subjects.size() - 1; i >= 0; i--) {
         UUID id = state.subjects.get(i);
         Entity entity = level.getEntity(id);
         if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.isRemoved()) {
            if (id.equals(state.host) && (entity == null || entity instanceof LivingEntity dead && dead.isDeadOrDying())) {
               hostDied = entity != null;
            }

            state.subjects.remove(i);
         }
      }

      if (state.host != null && !state.subjects.contains(state.host)) {
         if (hostDied) {
            backlash(player, state);
         }

         state.host = null;
      }

      LivingEntity host = state.host == null ? null : (LivingEntity) level.getEntity(state.host);
      if (state.hopTimer > 0) {
         state.hopTimer--;
      }

      if (host == null || !hostValid(player, host) || state.hopTimer <= 0) {
         LivingEntity next = nextHost(player, state, level, host);
         if (next != null && next != host) {
            hop(player, state, host, next);
            host = next;
         } else if (host != null && !hostValid(player, host)) {
            // the heart cannot beat there and has nowhere to go: it stays, silent
            host = null;
         } else if (host != null) {
            state.hopTimer = RegulusRules.KING_HOP_TICKS;
         }

         if (state.host == null && next != null) {
            state.host = next.getUUID();
         }
      }

      if (host != null && player.tickCount % 20 == 0) {
         RegulusSounds.play(level, host.position().add(0.0, host.getBbHeight() * 0.65, 0.0), RegulusSounds.HEARTBEAT, 0.45F, 1.0F);
      }
   }

   private static void backlash(ServerPlayer player, RegulusState state) {
      state.strain = RegulusRules.strainAdd(state.strain, RegulusRules.STRAIN_BACKLASH);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
      RegulusSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), RegulusSounds.STRAIN, 1.0F, 0.8F);
      HeroSpeech.trigger(player, HeroLineTrigger.event(RegulusLines.EVENT_HOST_LOST));
   }

   /** The next valid subject after {@code current} in bind order, or null. */
   @Nullable
   private static LivingEntity nextHost(ServerPlayer player, RegulusState state, ServerLevel level, @Nullable LivingEntity current) {
      int n = state.subjects.size();
      int start = current == null ? -1 : state.subjects.indexOf(current.getUUID());
      for (int k = 1; k <= n; k++) {
         UUID id = state.subjects.get(Math.floorMod(start + k, n));
         if (level.getEntity(id) instanceof LivingEntity living && hostValid(player, living)) {
            return living;
         }
      }

      return null;
   }

   private static void hop(ServerPlayer player, RegulusState state, @Nullable LivingEntity from, LivingEntity to) {
      state.host = to.getUUID();
      state.hopTimer = RegulusRules.KING_HOP_TICKS;
      state.hops++;
      ServerLevel level = (ServerLevel) player.level();
      Vec3 at = to.position().add(0.0, to.getBbHeight() * 0.65, 0.0);
      Vec3 dir = from == null ? Vec3.ZERO : from.position().add(0.0, from.getBbHeight() * 0.65, 0.0).subtract(at);
      RegulusSounds.play(level, at, RegulusSounds.KING_HOP, 0.7F, 1.0F);
      RegulusNet.fx(level, RegulusFxS2CPacket.KING_HOP, player, to.getId(), at, dir, 1.0F, from == null ? -1 : from.getId());
   }

   static void release(ServerPlayer player, RegulusState state, LivingEntity target) {
      state.subjects.remove(target.getUUID());
      if (target.getUUID().equals(state.host)) {
         state.host = null;
         state.hopTimer = 0;
      }

      Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
      RegulusSounds.play(player.level(), at, RegulusSounds.KING_RELEASE, 0.7F, 1.0F);
      RegulusNet.fx((ServerLevel) player.level(), RegulusFxS2CPacket.KING_RELEASE, player, target.getId(), at);
   }

   /** Every subject goes free (cleanup, crouch + MMB). Silent when the body is gone. */
   static void releaseAll(ServerPlayer player, RegulusState state, boolean audible) {
      if (audible && player.level() instanceof ServerLevel level) {
         for (UUID id : state.subjects) {
            if (level.getEntity(id) instanceof LivingEntity living) {
               RegulusNet.fx(level, RegulusFxS2CPacket.KING_RELEASE, player, living.getId(), living.position().add(0.0, living.getBbHeight() * 0.6, 0.0));
            }
         }

         RegulusSounds.play(level, player.position().add(0.0, 1.3, 0.0), RegulusSounds.KING_RELEASE, 0.8F, 0.9F);
      }

      state.subjects.clear();
      state.host = null;
      state.hopTimer = 0;
      state.castSubject = null;
   }

   /** Willing, living, non-hostile; another player only when allied; never another Regulus. */
   static boolean eligible(ServerPlayer self, RegulusState state, LivingEntity target) {
      if (target == self || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand || target instanceof Enemy) {
         return false;
      }

      if (target instanceof Player other) {
         return self.isAlliedTo(other) && RegulusState.of(other) == null;
      }

      return true;
   }

   /** Can the heart beat in this host right now: alive, near, not stopped in time, not bound. */
   static boolean hostValid(ServerPlayer player, LivingEntity host) {
      if (!host.isAlive() || host.level() != player.level() || host.distanceToSqr(player) > RegulusRules.KING_LEASH * RegulusRules.KING_LEASH) {
         return false;
      }

      if (HeroDamage.isAnchored(host) || DioTimeStop.freezing(host) != null) {
         return false;
      }

      MobEffectInstance slow = host.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
      return slow == null || slow.getAmplifier() < 3;
   }

   /** Pure host-validity rule for tests: alive, within the leash, not frozen. */
   static boolean hostValid(boolean alive, double distance, boolean frozen, int slownessAmplifier) {
      return alive && distance <= RegulusRules.KING_LEASH && !frozen && slownessAmplifier < 3;
   }

   @Nullable
   private static LivingEntity pick(ServerPlayer player, RegulusState state) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 end = eye.add(look.scale(RegulusRules.KING_RANGE));
      AABB sweep = player.getBoundingBox().expandTowards(look.scale(RegulusRules.KING_RANGE)).inflate(1.0);
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      for (Entity entity : player.level().getEntities(player, sweep)) {
         if (!(entity instanceof LivingEntity living) || !eligible(player, state, living)) {
            continue;
         }

         var clip = entity.getBoundingBox().inflate(0.3).clip(eye, end);
         if (clip.isEmpty()) {
            continue;
         }

         double d = eye.distanceToSqr(clip.get());
         if (d < bestDist && RegulusCombat.sees(player, eye, entity)) {
            best = living;
            bestDist = d;
         }
      }

      return best;
   }
}
