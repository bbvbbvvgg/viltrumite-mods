package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * §3.4 / №195: anyone but the active Thor who RMBs the resting model tries
 * to lift it: one calm two-handed attempt of about 1.5–2 s that always
 * fails and never moves the hammer. One attempt per participant, presses
 * during it are ignored, a short pause after it; a hit interrupts it; the
 * active Thor's recall rips the hammer away and knocks the lifter back
 * (№212). Players only: NPCs never touch the hammer. Transient.
 */
public final class MjolnirLift {
   public static final String FAIL = "hud.viltrumitecore.thor.lift_fail";
   public static final String RIPPED = "hud.viltrumitecore.thor.lift_ripped";

   private record Attempt(Vec3 start, long until) {
   }

   private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
   private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

   private MjolnirLift() {
   }

   /** Pure gate: no attempt running for this participant and the pause after the last one is over. */
   static boolean mayBegin(boolean running, long cooldownUntil, long now) {
      return !running && now >= cooldownUntil;
   }

   static synchronized InteractionResult begin(ServerPlayer player, MjolnirEntity hammer) {
      long now = player.serverLevel().getGameTime();
      UUID id = player.getUUID();
      if (!mayBegin(ATTEMPTS.containsKey(id), COOLDOWNS.getOrDefault(id, 0L), now)) {
         return InteractionResult.CONSUME;
      }

      if (player.distanceTo(hammer) > MjolnirTuning.LIFT_REACH + hammer.getBbWidth()) {
         return InteractionResult.PASS;
      }

      ATTEMPTS.put(id, new Attempt(player.position(), now + MjolnirTuning.LIFT_TICKS));
      player.serverLevel().playSound(null, hammer.getX(), hammer.getY(), hammer.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 0.6F);
      return InteractionResult.CONSUME;
   }

   public static synchronized boolean lifting(UUID player) {
      return ATTEMPTS.containsKey(player);
   }

   /** Entity ids of the players lifting near this hammer (synced for their pose). */
   static synchronized int[] lifterIds(MjolnirEntity hammer) {
      if (ATTEMPTS.isEmpty()) {
         return new int[0];
      }

      MinecraftServer server = hammer.getServer();
      List<Integer> ids = new ArrayList<>();
      for (UUID id : ATTEMPTS.keySet()) {
         ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
         if (player != null && player.level() == hammer.level()) {
            ids.add(player.getId());
         }
      }

      return ids.stream().mapToInt(Integer::intValue).toArray();
   }

   public static synchronized void tick(MinecraftServer server) {
      if (ATTEMPTS.isEmpty()) {
         return;
      }

      long now = server.overworld().getGameTime();
      MjolnirEntity hammer = MjolnirWorld.entity(server);
      Iterator<Map.Entry<UUID, Attempt>> it = ATTEMPTS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Attempt> entry = it.next();
         ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
         Attempt attempt = entry.getValue();
         boolean gone = player == null || !player.isAlive() || hammer == null || hammer.phase() != MjolnirPhase.RESTING || player.level() != hammer.level()
            || player.position().distanceTo(attempt.start()) > MjolnirTuning.LIFT_MOVE_CANCEL;
         if (gone) {
            it.remove();
            COOLDOWNS.put(entry.getKey(), now + MjolnirTuning.LIFT_COOLDOWN);
         } else if (now >= attempt.until()) {
            // It does not move: the attempt always fails.
            it.remove();
            COOLDOWNS.put(entry.getKey(), now + MjolnirTuning.LIFT_COOLDOWN);
            player.displayClientMessage(Component.translatable(FAIL), true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.6F);
         }
      }
   }

   /** A hit on the lifter interrupts the attempt. */
   public static synchronized void interrupt(LivingEntity victim) {
      if (victim instanceof ServerPlayer player && ATTEMPTS.remove(player.getUUID()) != null) {
         COOLDOWNS.put(player.getUUID(), player.serverLevel().getGameTime() + MjolnirTuning.LIFT_COOLDOWN);
      }
   }

   /** №212: the active Thor calls it during an attempt: ripped away at once, the lifter knocked back. */
   static synchronized void rip(MinecraftServer server, Vec3 hammer) {
      if (ATTEMPTS.isEmpty()) {
         return;
      }

      long now = server.overworld().getGameTime();
      for (UUID id : new ArrayList<>(ATTEMPTS.keySet())) {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         ATTEMPTS.remove(id);
         COOLDOWNS.put(id, now + MjolnirTuning.LIFT_COOLDOWN);
         if (player == null) {
            continue;
         }

         Vec3 away = player.position().subtract(hammer).multiply(1.0, 0.0, 1.0);
         Vec3 dir = away.lengthSqr() < 1.0E-6 ? player.getLookAngle().reverse().multiply(1.0, 0.0, 1.0) : away.normalize();
         if (HeroRegistry.allowsImpulse(player) && dir.lengthSqr() > 1.0E-6) {
            Vec3 push = dir.normalize().scale(MjolnirTuning.LIFT_RIP_PUSH);
            player.setDeltaMovement(push.x, 0.45, push.z);
            player.hurtMarked = true;
         }

         player.displayClientMessage(Component.translatable(RIPPED), true);
      }
   }

   public static synchronized void stop(UUID player) {
      ATTEMPTS.remove(player);
   }

   public static synchronized void clear() {
      ATTEMPTS.clear();
      COOLDOWNS.clear();
   }
}
