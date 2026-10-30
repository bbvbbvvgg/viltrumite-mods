package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorDashS2CPacket;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the double-WASD dash (decisions 110, 135): the client moves
 * the local player at once (its own movement), the server checks that a
 * dash is allowed now (ground, or HOVER with Mjölnir in the hand; never full flight, no scene, not
 * held) and on cooldown, and plays it for everyone around. A refused dash is
 * undone: the player snaps back to the server position (the DASH packet
 * arrives before the first dashed movement packet, so that position is the
 * pre-dash one) and the client stops the burst (ThorDashS2CPacket).
 */
final class ThorDash {
   private static final Map<UUID, Long> NEXT = new HashMap<>();

   private ThorDash() {
   }

   static boolean allowed(ServerPlayer player) {
      ThorState state = ThorState.of(player);
      return state != null && player.isAlive()
         && ThorMovementRules.dashAllowed(ThorBridge.sceneLocked(state), HeroDamage.isAnchored(player) || dev.baranhan.viltrumitecore.hero.HeroHeldTargets.holder(player) != null, ThorScenes.flightState(player), player.onGround(),
         dev.baranhan.viltrumitecore.hero.thor.ThorHero.hammerInHand(player));
   }

   static void onDash(ServerPlayer player, int direction) {
      if (ThorState.of(player) == null) {
         return;
      }

      long now = player.level().getGameTime();
      if (!accepted(direction, allowed(player), now, NEXT.get(player.getUUID()))) {
         refuse(player);
         return;
      }

      NEXT.put(player.getUUID(), now + Math.max(1, ThorSceneConfig.dashCooldown - 2));
      // Audit v3: a dash cuts a strike's recovery.
      dev.baranhan.viltrumitecore.hero.thor.ThorActions.finishRecovery(player, ThorState.of(player));
      HeroFx.airTrail(player, player);
      SceneFx.sound(player, player.position(), ThorCue.DASH);
   }

   /** Pure: the server's verdict on a dash the client has already started (a known direction, allowed now, off cooldown). */
   public static boolean accepted(int direction, boolean allowed, long now, @Nullable Long next) {
      return direction >= 0 && direction <= 3 && allowed && (next == null || now >= next);
   }

   /** Undo a predicted dash: back to the server position (velocity cleared by the absolute teleport), the client stops the burst. */
   private static void refuse(ServerPlayer player) {
      if (player.hasDisconnected()) {
         return;
      }

      if (!player.isPassenger()) {
         player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      }
      CoreMessages.sendToPlayer(new ThorDashS2CPacket(), player);
   }

   static void forget(UUID player) {
      NEXT.remove(player);
   }
}
