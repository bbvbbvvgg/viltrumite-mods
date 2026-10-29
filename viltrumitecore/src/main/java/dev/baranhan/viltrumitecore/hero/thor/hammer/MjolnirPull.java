package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * №220: Y while Mjölnir flies after a throw pulls Thor to it and carries
 * him with it (MAGNETIZED_TO_HAMMER, a Thor state, not a hammer phase: the
 * hammer stays the one THROWN body). Never for a resting, stuck, unthrown or
 * held hammer. [ОТКРЫТО] details (the moment of attachment, what happens when
 * the hammer stops, hits or turns back, duration, steering) are PLACEHOLDERs:
 * the pull simply ends when the hammer is not THROWN any more, Thor keeps
 * his momentum, and fall damage is cancelled for a short grace.
 */
public final class MjolnirPull {
   private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
   private static final Map<UUID, Long> GRACE = new HashMap<>();

   private MjolnirPull() {
   }

   /** Pure: the pull may start only on a hammer flying after a throw (spec §3.2). */
   public static boolean allowed(MjolnirPhase phase) {
      return phase.pullable();
   }

   static synchronized boolean start(ServerPlayer thor) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = MjolnirWorld.record(server);
      MjolnirEntity hammer = MjolnirWorld.entity(server, record);
      if (!thor.getUUID().equals(record.activeOwner) || !allowed(record.phase) || hammer == null || hammer.level() != thor.level()) {
         return false;
      }

      ACTIVE.put(thor.getUUID(), 0);
      ThorSounds.play(thor, ThorCue.PULL);
      return true;
   }

   public static synchronized boolean active(UUID thor) {
      return ACTIVE.containsKey(thor);
   }

   public static void tick(ServerPlayer thor, ThorState state) {
      Integer ticks;
      synchronized (MjolnirPull.class) {
         ticks = ACTIVE.get(thor.getUUID());
      }

      if (ticks == null) {
         return;
      }

      MinecraftServer server = thor.server;
      MjolnirRecord record = MjolnirWorld.record(server);
      MjolnirEntity hammer = MjolnirWorld.entity(server, record);
      if (hammer == null || !allowed(record.phase) || hammer.level() != thor.level() || !thor.isAlive() || ticks >= MjolnirTuning.PULL_MAX_TICKS) {
         end(thor, state);
         return;
      }

      Vec3 flight = hammer.getDeltaMovement();
      Vec3 behind = flight.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flight.normalize().scale(-1.2);
      Vec3 anchor = hammer.position().add(behind).subtract(0.0, thor.getBbHeight() * 0.5, 0.0);
      Vec3 to = anchor.subtract(thor.position());
      double distance = to.length();
      Vec3 velocity;
      if (distance > MjolnirTuning.PULL_ATTACH) {
         double speed = Math.max(MjolnirTuning.PULL_SPEED, flight.length() + MjolnirTuning.PULL_CATCH_UP);
         velocity = to.scale(Math.min(speed, distance) / distance);
      } else {
         // Close: moves with the hammer.
         velocity = flight.add(to.scale(0.3));
      }

      thor.setDeltaMovement(velocity);
      thor.hurtMarked = true;
      thor.fallDistance = 0.0F;
      synchronized (MjolnirPull.class) {
         ACTIVE.put(thor.getUUID(), ticks + 1);
      }
   }

   static void end(ServerPlayer thor, ThorState state) {
      stop(thor);
      if (state.timeline.is(ThorMove.HAMMER_PULL)) {
         state.timeline.skipTo(MovePhase.FINISH);
      }
   }

   /** Ends the pull (move stop, death, logout, dimension); the fall grace starts. */
   public static synchronized void stop(ServerPlayer thor) {
      if (ACTIVE.remove(thor.getUUID()) != null) {
         long now = thor.serverLevel().getGameTime();
         // A-N4: expired graces go when a new one starts, so the table never grows past the recent pullers.
         prune(GRACE, now);
         GRACE.put(thor.getUUID(), now + MjolnirTuning.PULL_FALL_GRACE);
         thor.fallDistance = 0.0F;
      }
   }

   /** Pure: drop the graces that ended before {@code now}. */
   static void prune(Map<UUID, Long> graces, long now) {
      graces.values().removeIf(until -> until <= now);
   }

   public static synchronized boolean graced(Player player) {
      if (player.level().isClientSide) {
         return false;
      }

      UUID id = player.getUUID();
      return ACTIVE.containsKey(id) || GRACE.getOrDefault(id, Long.MIN_VALUE) > player.level().getGameTime();
   }

   public static synchronized void clear() {
      ACTIVE.clear();
      GRACE.clear();
   }
}
