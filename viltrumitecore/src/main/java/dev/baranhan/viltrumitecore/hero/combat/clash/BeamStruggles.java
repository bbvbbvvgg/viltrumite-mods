package dev.baranhan.viltrumitecore.hero.combat.clash;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ClashStateS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server runtime of the beam struggle (design §3.1). Once per server tick, after every report of the tick:
 * running struggles advance (tug of war, mash pressure, release / aim-away checks), then free beams that meet
 * head-on start new ones. The rules are the pure classes ({@link BeamMath}, {@link TugOfWar}, {@link MashMeter},
 * {@link PairCooldowns}); this class only wires them to entities, damage, FX, sound and the network.
 *
 * <p>Outcomes: the winner's beam goes through — the loser takes {@link ClashRules#LOSER_DAMAGE_PER_POWER} ×
 * winner power, is thrown back along the axis and loses the beam for {@link ClashRules#BEAM_LOCK_TICKS}. A draw
 * (time out, or the muzzles collapse into each other) blows up at the point and hurts both. Releasing or aiming
 * away during the grace window cancels without a penalty; after it, the side that let go loses.
 */
public final class BeamStruggles {
   /** A beam whose ray passes further than this from the clash point (blocks) has left the struggle. */
   static final double BREAK_DISTANCE = 2.5;
   /** Muzzles closer than this (blocks) collapse the struggle into a draw blast. */
   static final double COLLAPSE_DISTANCE = 1.5;
   static final float MIN_POWER = 0.25F;
   static final float MAX_POWER = 4.0F;
   /** Free beams looked at per level and tick (pairs are O(n²)). */
   static final int MAX_BEAMS_SCANNED = 48;
   private static final Map<UUID, Struggle> BY_OWNER = new HashMap<>();
   private static final Map<Integer, Struggle> BY_ID = new LinkedHashMap<>();
   private static final PairCooldowns COOLDOWNS = new PairCooldowns();
   private static int nextId = 1;

   private BeamStruggles() {
   }

   /** One running struggle between beam A and beam B. */
   public static final class Struggle {
      final int id;
      final ResourceKey<Level> dimension;
      final UUID a;
      final UUID b;
      final TugOfWar tug;
      final MashMeter mashA = new MashMeter();
      final MashMeter mashB = new MashMeter();
      ClashBeam beamA;
      ClashBeam beamB;
      Vec3 originA;
      Vec3 originB;

      Struggle(int id, ResourceKey<Level> dimension, ClashBeam beamA, ClashBeam beamB, double start) {
         this.id = id;
         this.dimension = dimension;
         this.a = beamA.owner().getUUID();
         this.b = beamB.owner().getUUID();
         this.beamA = beamA;
         this.beamB = beamB;
         this.originA = beamA.origin();
         this.originB = beamB.origin();
         this.tug = new TugOfWar(start);
      }

      public int id() {
         return this.id;
      }

      /** The clash point in the world right now. */
      public Vec3 point() {
         return BeamMath.at(this.originA, this.originB, this.tug.point());
      }

      boolean isA(UUID owner) {
         return this.a.equals(owner);
      }
   }

   @Nullable
   static Struggle of(UUID owner) {
      return BY_OWNER.get(owner);
   }

   public static int active() {
      return BY_ID.size();
   }

   static int active(ResourceKey<Level> dimension) {
      int count = 0;
      for (Struggle struggle : BY_ID.values()) {
         if (struggle.dimension == dimension) {
            count++;
         }
      }

      return count;
   }

   /** One server tick of one level (ClashEvents, ServerTickEvent END). */
   public static void tick(ServerLevel level) {
      long now = level.getGameTime();
      if (!BY_ID.isEmpty()) {
         List<Struggle> running = new ArrayList<>();
         for (Struggle struggle : BY_ID.values()) {
            if (struggle.dimension == level.dimension()) {
               running.add(struggle);
            }
         }

         for (Struggle struggle : running) {
            advance(level, struggle, now);
         }
      }

      detect(level, now);
   }

   private static void advance(ServerLevel level, Struggle s, long now) {
      LivingEntity ownerA = living(level, s.a);
      LivingEntity ownerB = living(level, s.b);
      if (ownerA == null || ownerB == null) {
         end(level, s, ClashStateS2CPacket.CANCELLED, now);
         return;
      }

      ClashBeam currentA = ClashBeams.current(level, s.a, now);
      ClashBeam currentB = ClashBeams.current(level, s.b, now);
      if (currentA != null) {
         s.beamA = currentA;
         s.originA = currentA.origin();
      }

      if (currentB != null) {
         s.beamB = currentB;
         s.originB = currentB.origin();
      }

      if (s.originA.distanceTo(s.originB) < COLLAPSE_DISTANCE) {
         end(level, s, ClashStateS2CPacket.DRAW, now);
         return;
      }

      Vec3 point = s.point();
      boolean holdA = currentA != null && holds(currentA, point);
      boolean holdB = currentB != null && holds(currentB, point);
      boolean grace = s.tug.inGrace();
      if (!holdA && !holdB) {
         end(level, s, ClashStateS2CPacket.CANCELLED, now);
         return;
      } else if (!holdA) {
         end(level, s, grace ? ClashStateS2CPacket.CANCELLED : ClashStateS2CPacket.B_WINS, now);
         return;
      } else if (!holdB) {
         end(level, s, grace ? ClashStateS2CPacket.CANCELLED : ClashStateS2CPacket.A_WINS, now);
         return;
      }

      int age = s.tug.age();
      double powerA = power(s.beamA);
      double powerB = power(s.beamB);
      double pressureA = s.mashA.pressure(age, powerA) + powerA * Math.max(0.0, s.beamA.autoPressure());
      double pressureB = s.mashB.pressure(age, powerB) + powerB * Math.max(0.0, s.beamB.autoPressure());
      switch (s.tug.tick(pressureA, pressureB)) {
         case A_WINS -> end(level, s, ClashStateS2CPacket.A_WINS, now);
         case B_WINS -> end(level, s, ClashStateS2CPacket.B_WINS, now);
         case DRAW -> end(level, s, ClashStateS2CPacket.DRAW, now);
         default -> broadcast(level, s, ClashStateS2CPacket.RUNNING, ownerA, ownerB);
      }
   }

   /** Pure: the beam still runs through the clash point (in range and within {@link #BREAK_DISTANCE} of its ray). */
   static boolean holds(ClashBeam beam, Vec3 point) {
      Vec3 dir = beam.direction().normalize();
      Vec3 to = point.subtract(beam.origin());
      double along = to.dot(dir);
      if (along < 0.0 || along > Math.min(beam.range(), ClashBeams.MAX_RANGE) + ClashRules.CONTACT_RADIUS) {
         return false;
      }

      return to.subtract(dir.scale(along)).lengthSqr() <= BREAK_DISTANCE * BREAK_DISTANCE;
   }

   private static void detect(ServerLevel level, long now) {
      int running = active(level.dimension());
      if (running >= ClashRules.MAX_CLASHES_PER_LEVEL) {
         return;
      }

      List<ClashBeam> free = new ArrayList<>();
      for (ClashBeam beam : ClashBeams.live(level, now)) {
         if (!BY_OWNER.containsKey(beam.owner().getUUID()) && !beam.owner().isSpectator()) {
            free.add(beam);
            if (free.size() >= MAX_BEAMS_SCANNED) {
               break;
            }
         }
      }

      for (int i = 0; i < free.size(); i++) {
         ClashBeam a = free.get(i);
         if (BY_OWNER.containsKey(a.owner().getUUID())) {
            continue;
         }

         for (int j = i + 1; j < free.size(); j++) {
            ClashBeam b = free.get(j);
            if (BY_OWNER.containsKey(b.owner().getUUID()) || !eligible(level, a.owner(), b.owner())
               || !COOLDOWNS.ready(a.owner().getUUID(), b.owner().getUUID(), now)) {
               continue;
            }

            double rangeA = Math.min(a.range(), ClashBeams.MAX_RANGE);
            double rangeB = Math.min(b.range(), ClashBeams.MAX_RANGE);
            if (a.origin().distanceToSqr(b.origin()) > (rangeA + rangeB) * (rangeA + rangeB)) {
               continue;
            }

            BeamMath.Meeting meeting = BeamMath.meet(a.origin(), a.direction(), rangeA, b.origin(), b.direction(), rangeB);
            if (meeting != null) {
               start(level, a, b, meeting);
               if (++running >= ClashRules.MAX_CLASHES_PER_LEVEL) {
                  return;
               }

               break;
            }
         }
      }
   }

   /** Who may lock beams: never allies, never spectators, players only where PvP between them is allowed. */
   static boolean eligible(ServerLevel level, LivingEntity a, LivingEntity b) {
      if (a == b || a.isSpectator() || b.isSpectator() || a.isAlliedTo(b)) {
         return false;
      }

      if (a instanceof Player pa && b instanceof Player pb) {
         return level.getServer().isPvpAllowed() && pa.canHarmPlayer(pb);
      }

      return true;
   }

   private static void start(ServerLevel level, ClashBeam a, ClashBeam b, BeamMath.Meeting meeting) {
      double start = BeamMath.startFraction(a.origin(), b.origin(), meeting.point());
      Struggle s = new Struggle(nextId++, level.dimension(), a, b, start);
      if (nextId == Integer.MAX_VALUE) {
         nextId = 1;
      }

      BY_ID.put(s.id, s);
      BY_OWNER.put(s.a, s);
      BY_OWNER.put(s.b, s);
      a.onStruggleStart(b.owner());
      b.onStruggleStart(a.owner());
      Vec3 point = s.point();
      HeroFx.preset(level, ClashFx.BEAM_LOCK, point, null, 1.0F, null);
      level.playSound(null, point.x, point.y, point.z, ClashSounds.BEAM_LOCK.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
      broadcast(level, s, ClashStateS2CPacket.RUNNING, a.owner(), b.owner());
   }

   private static void end(ServerLevel level, Struggle s, int status, long now) {
      BY_ID.remove(s.id);
      BY_OWNER.remove(s.a, s);
      BY_OWNER.remove(s.b, s);
      COOLDOWNS.start(s.a, s.b, now, ClashRules.PAIR_COOLDOWN_TICKS);
      LivingEntity ownerA = living(level, s.a);
      LivingEntity ownerB = living(level, s.b);
      Vec3 point = s.point();
      ClashOutcome forA;
      ClashOutcome forB;
      switch (status) {
         case ClashStateS2CPacket.A_WINS -> {
            punish(level, ownerA, ownerB, s.beamA, s.originA, s.originB);
            forA = ClashOutcome.WON;
            forB = ClashOutcome.LOST;
         }
         case ClashStateS2CPacket.B_WINS -> {
            punish(level, ownerB, ownerA, s.beamB, s.originB, s.originA);
            forA = ClashOutcome.LOST;
            forB = ClashOutcome.WON;
         }
         case ClashStateS2CPacket.DRAW -> {
            blast(level, ownerA, ownerB, point);
            forA = ClashOutcome.DRAW;
            forB = ClashOutcome.DRAW;
         }
         default -> {
            HeroFx.preset(level, ClashFx.BEAM_FIZZLE, point, null, 0.8F, null);
            forA = ClashOutcome.CANCELLED;
            forB = ClashOutcome.CANCELLED;
         }
      }

      if (ownerB != null) {
         s.beamA.onStruggleEnd(ownerB, forA);
      }

      if (ownerA != null) {
         s.beamB.onStruggleEnd(ownerA, forB);
      }

      broadcast(level, s, status, ownerA, ownerB);
   }

   private static void punish(ServerLevel level, @Nullable LivingEntity winner, @Nullable LivingEntity loser, ClashBeam winnerBeam, Vec3 winnerMuzzle,
      Vec3 loserMuzzle) {
      float power = power(winnerBeam);
      if (loser != null) {
         hurt(loser, winner, ClashRules.LOSER_DAMAGE_PER_POWER * power);
         push(loser, loserMuzzle.subtract(winnerMuzzle), 0.9 + 0.5 * power);
         ClashBeams.lock(loser, ClashRules.BEAM_LOCK_TICKS);
      }

      HeroFx.preset(level, ClashFx.BEAM_BLAST, loserMuzzle, winnerMuzzle, 1.1F + 0.4F * power, null);
      level.playSound(null, loserMuzzle.x, loserMuzzle.y, loserMuzzle.z, ClashSounds.BEAM_BLAST.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
   }

   private static void blast(ServerLevel level, @Nullable LivingEntity a, @Nullable LivingEntity b, Vec3 point) {
      if (a != null) {
         hurt(a, b, ClashRules.DRAW_DAMAGE);
         push(a, a.position().subtract(point), 1.0);
         ClashBeams.lock(a, ClashRules.BEAM_LOCK_TICKS / 2);
      }

      if (b != null) {
         hurt(b, a, ClashRules.DRAW_DAMAGE);
         push(b, b.position().subtract(point), 1.0);
         ClashBeams.lock(b, ClashRules.BEAM_LOCK_TICKS / 2);
      }

      HeroFx.preset(level, ClashFx.BEAM_BLAST, point, null, 2.2F, null);
      level.playSound(null, point.x, point.y, point.z, ClashSounds.BEAM_BLAST.get(), SoundSource.PLAYERS, 5.0F, 0.8F);
   }

   private static void hurt(LivingEntity target, @Nullable LivingEntity attacker, float amount) {
      DamageSource source = attacker instanceof Player player ? target.damageSources().playerAttack(player)
         : attacker != null ? target.damageSources().mobAttack(attacker) : target.damageSources().magic();
      target.invulnerableTime = 0;
      target.hurt(source, amount);
   }

   private static void push(LivingEntity target, Vec3 away, double strength) {
      if (HeroDamage.isAnchored(target)) {
         return;
      }

      Vec3 flat = new Vec3(away.x, 0.0, away.z);
      if (flat.lengthSqr() < 1.0E-6) {
         Vec3 look = target.getLookAngle();
         flat = new Vec3(-look.x, 0.0, -look.z);
      }

      if (flat.lengthSqr() < 1.0E-6) {
         return;
      }

      Vec3 kick = flat.normalize().scale(strength);
      target.setDeltaMovement(target.getDeltaMovement().add(kick.x, 0.35, kick.z));
      target.hasImpulse = true;
      target.hurtMarked = true;
   }

   /** A mash pulse (attack key) from a struggling player (ClashInputC2SPacket). */
   public static void pulse(ServerPlayer player, int struggleId) {
      Struggle s = BY_OWNER.get(player.getUUID());
      if (s == null || s.id != struggleId || s.dimension != player.level().dimension() || !player.isAlive()) {
         return;
      }

      boolean isA = s.isA(player.getUUID());
      MashMeter meter = isA ? s.mashA : s.mashB;
      int latencyTicks = Math.max(0, Math.min(ClashRules.MAX_LATENCY_TICKS, player.latency / 100));
      if (meter.pulse(s.tug.age(), ClashRules.GRACE_TICKS, latencyTicks) == MashMeter.Pulse.SURGE) {
         Vec3 at = isA ? BeamMath.at(s.originA, s.point(), 0.85) : BeamMath.at(s.originB, s.point(), 0.85);
         player.serverLevel().playSound(null, at.x, at.y, at.z, ClashSounds.BEAM_SURGE.get(), SoundSource.PLAYERS, 2.0F, 0.9F + 0.05F * (meter.surges() % 5));
      }
   }

   private static void broadcast(ServerLevel level, Struggle s, int status, @Nullable Entity ownerA, @Nullable Entity ownerB) {
      int age = s.tug.age();
      int flags = (s.mashA.surging(age) ? ClashStateS2CPacket.FLAG_SURGE_A : 0) | (s.mashB.surging(age) ? ClashStateS2CPacket.FLAG_SURGE_B : 0)
         | (s.tug.inGrace() ? ClashStateS2CPacket.FLAG_GRACE : 0);
      ClashStateS2CPacket packet = new ClashStateS2CPacket(s.id, status, ownerA == null ? -1 : ownerA.getId(), ownerB == null ? -1 : ownerB.getId(),
         s.originA, s.originB, (float)s.tug.point(), age, flags, s.mashA.pulsesInWindow(age), s.mashB.pulsesInWindow(age), s.beamA.beamId(),
         s.beamB.beamId());
      Vec3 point = s.point();
      double range = ClashRules.WATCH_DISTANCE * ClashRules.WATCH_DISTANCE;
      for (ServerPlayer viewer : level.players()) {
         if (viewer.getUUID().equals(s.a) || viewer.getUUID().equals(s.b) || viewer.position().distanceToSqr(point) <= range) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }

   static float power(ClashBeam beam) {
      float power = beam.power();
      return Float.isFinite(power) ? Math.max(MIN_POWER, Math.min(MAX_POWER, power)) : 1.0F;
   }

   @Nullable
   private static LivingEntity living(ServerLevel level, UUID id) {
      Entity entity = level.getEntity(id);
      return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
   }

   /** A player left: their struggle is called off (no penalty, no damage to an empty slot). */
   static void forget(ServerPlayer player) {
      Struggle s = BY_OWNER.get(player.getUUID());
      if (s != null && player.level() instanceof ServerLevel level) {
         end(level, s, ClashStateS2CPacket.CANCELLED, level.getGameTime());
      }

      ClashBeams.forget(player.getUUID());
   }

   static void prune(long now) {
      COOLDOWNS.prune(now);
      ClashBeams.prune(now);
   }

   /** Runtime footprint for the soak test: struggles, owners, cooldown pairs, registry entries. */
   public static String footprint() {
      return "struggles=" + BY_ID.size() + " owners=" + BY_OWNER.size() + " cooldowns=" + COOLDOWNS.size() + " registry=" + ClashBeams.size();
   }

   static void clear() {
      BY_ID.clear();
      BY_OWNER.clear();
      COOLDOWNS.clear();
      ClashBeams.clear();
      nextId = 1;
   }
}
