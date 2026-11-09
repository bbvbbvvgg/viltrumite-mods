package dev.baranhan.viltrumitecore.hero.combat.clash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server registry of the beams that are on right now (wave 1, session 23). A source calls {@link #report} every
 * tick while its beam is on; an entry that is not refreshed for {@link ClashRules#BEAM_EXPIRY_TICKS} ticks is gone.
 * The struggle runtime ({@link BeamStruggles}) reads the registry once per server tick, after every player and
 * entity ticked, so a report made anywhere in the tick counts for that tick.
 *
 * <p>The three questions a source asks back are {@link #struggling}, {@link #clashPoint} and {@link #beamLocked}.
 * All of them are server-only and cheap (one map lookup).
 */
public final class ClashBeams {
   /** Longest beam the struggle looks at (blocks); a longer report is treated as this long. */
   public static final double MAX_RANGE = 256.0;
   private static final Map<ResourceKey<Level>, Map<UUID, Entry>> LIVE = new HashMap<>();
   private static final Map<UUID, Long> LOCKS = new HashMap<>();

   private record Entry(ClashBeam beam, long seen) {
   }

   private ClashBeams() {
   }

   /** The beam of {@code beam.owner()} is on this tick. Ignored on the client, for a dead owner and while locked. */
   public static void report(@Nullable ClashBeam beam) {
      if (beam == null || !valid(beam)) {
         return;
      }

      LivingEntity owner = beam.owner();
      if (owner == null || !(owner.level() instanceof ServerLevel level) || !owner.isAlive() || owner.isRemoved() || beamLocked(owner)) {
         return;
      }

      LIVE.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(owner.getUUID(), new Entry(beam, level.getGameTime()));
   }

   /** The owner's beam is locked in a struggle: stop the beam's own damage along the ray. */
   public static boolean struggling(@Nullable LivingEntity owner) {
      return owner != null && !owner.level().isClientSide() && BeamStruggles.of(owner.getUUID()) != null;
   }

   /** Where the owner's beam ends while it struggles (draw it to here), else null. */
   @Nullable
   public static Vec3 clashPoint(@Nullable LivingEntity owner) {
      if (owner == null || owner.level().isClientSide()) {
         return null;
      }

      BeamStruggles.Struggle struggle = BeamStruggles.of(owner.getUUID());
      return struggle == null ? null : struggle.point();
   }

   /** The owner just lost a struggle: the beam is knocked out for {@link ClashRules#BEAM_LOCK_TICKS}. */
   public static boolean beamLocked(@Nullable LivingEntity owner) {
      if (owner == null) {
         return false;
      }

      Long until = LOCKS.get(owner.getUUID());
      return until != null && owner.level().getGameTime() < until;
   }

   /** Pure: a report the struggle can use (finite, non-zero direction, long enough, positive power). */
   public static boolean valid(ClashBeam beam) {
      Vec3 origin = beam.origin();
      Vec3 direction = beam.direction();
      return origin != null && direction != null && finite(origin) && finite(direction) && direction.lengthSqr() > 1.0E-8
         && Double.isFinite(beam.range()) && beam.range() >= ClashRules.MIN_BEAM_LENGTH && Float.isFinite(beam.power()) && beam.power() > 0.0F;
   }

   static void lock(LivingEntity owner, int ticks) {
      LOCKS.put(owner.getUUID(), owner.level().getGameTime() + Math.max(0, ticks));
      for (Map<UUID, Entry> beams : LIVE.values()) {
         beams.remove(owner.getUUID());
      }
   }

   /** The owner's beam if it was reported within the expiry window, else null. */
   @Nullable
   static ClashBeam current(ServerLevel level, UUID owner, long now) {
      Map<UUID, Entry> beams = LIVE.get(level.dimension());
      Entry entry = beams == null ? null : beams.get(owner);
      return entry != null && now - entry.seen() <= ClashRules.BEAM_EXPIRY_TICKS ? entry.beam() : null;
   }

   /** Live beams of a level in a stable order (owner UUID); drops expired entries and dead owners. */
   static List<ClashBeam> live(ServerLevel level, long now) {
      Map<UUID, Entry> beams = LIVE.get(level.dimension());
      if (beams == null || beams.isEmpty()) {
         return List.of();
      }

      List<ClashBeam> out = new ArrayList<>(beams.size());
      Iterator<Map.Entry<UUID, Entry>> it = beams.entrySet().iterator();
      while (it.hasNext()) {
         Entry entry = it.next().getValue();
         LivingEntity owner = entry.beam().owner();
         if (now - entry.seen() > ClashRules.BEAM_EXPIRY_TICKS || owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level) {
            it.remove();
         } else {
            out.add(entry.beam());
         }
      }

      out.sort(Comparator.comparing(beam -> beam.owner().getUUID()));
      return out;
   }

   static void prune(long now) {
      LOCKS.values().removeIf(until -> until <= now);
      LIVE.values().forEach(beams -> beams.values().removeIf(entry -> now - entry.seen() > ClashRules.BEAM_EXPIRY_TICKS));
      LIVE.values().removeIf(Map::isEmpty);
   }

   static void forget(UUID owner) {
      LOCKS.remove(owner);
      LIVE.values().forEach(beams -> beams.remove(owner));
   }

   static int size() {
      int size = 0;
      for (Map<UUID, Entry> beams : LIVE.values()) {
         size += beams.size();
      }

      return size + LOCKS.size();
   }

   static void clear() {
      LIVE.clear();
      LOCKS.clear();
   }

   private static boolean finite(Vec3 v) {
      return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }
}
