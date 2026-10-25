package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.AirJumpSyncS2CPacket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Air jumps, server side (any hero with {@link HeroDefinition#airJumps} > 0).
 * The client predicts the step ({@code client.hero.AirJumpClient}) and sends
 * {@link HeroAction#AIR_STEP} through the generic input packet; the server keeps
 * its own counter with the same {@link AirJumpRules}, then confirms or corrects:
 * <ul>
 * <li>accepted: the counter, fall distance, its own copy of the velocity, step FX
 * for the players that see him and the hero's sound. No motion packet: the
 * client already moved, and a late authoritative velocity would boost twice.</li>
 * <li>refused: no FX, no sound; a motion packet cancels the predicted boost and a
 * sync packet sets the client counter to the server's.</li>
 * </ul>
 */
public final class HeroAirJump {
   private static final Map<UUID, Integer> USED = new ConcurrentHashMap<>();
   private static Sink sink = new NetworkSink();

   private HeroAirJump() {
   }

   /** The player side of one press (an adapter over ServerPlayer; a fake in tests). */
   public interface Body {
      int used();

      void setUsed(int used);

      int max();

      double velocity();

      boolean resetState();

      boolean anchored();

      boolean canAct();

      Vec3 deltaMovement();

      void setDeltaMovement(Vec3 velocity);

      void resetFall();
   }

   /** What a press sends (network in game, recorded in tests). */
   public interface Sink {
      /** Accepted: step FX for the others and the hero's own extras. */
      void stepped(Body body);

      /** Refused: authoritative velocity for the client. */
      void motion(Body body, Vec3 velocity);

      /** Refused: the server counter for the client. */
      void sync(Body body, int used);
   }

   /** Pure core of a press: true when accepted. */
   public static boolean press(Body body, Sink out) {
      int used = body.used();
      if (AirJumpRules.allow(used, body.max(), body.resetState(), body.anchored(), body.canAct())) {
         Vec3 v = body.deltaMovement();
         body.setDeltaMovement(new Vec3(v.x, body.velocity(), v.z));
         body.setUsed(used + 1);
         body.resetFall();
         out.stepped(body);
         return true;
      }

      Vec3 v = body.deltaMovement();
      out.motion(body, new Vec3(v.x, Math.min(v.y, 0.0), v.z));
      out.sync(body, used);
      return false;
   }

   /** Server side of the AIR_STEP input. */
   public static void onInput(ServerPlayer player) {
      HeroDefinition hero = HeroRegistry.get(player);
      if (hero.airJumps(player) <= 0) {
         return;
      }

      press(new PlayerBody(player, hero), sink);
   }

   /** Player tick (server): the same reset states as the client. */
   public static void tick(ServerPlayer player) {
      if (resetState(player)) {
         USED.remove(player.getUUID());
      }
   }

   public static void forget(ServerPlayer player) {
      USED.remove(player.getUUID());
   }

   static boolean resetState(ServerPlayer player) {
      return AirJumpRules.resets(player.onGround(), player.isInWater(), player.isInLava(), player.onClimbable(), player.getAbilities().flying,
         player.isPassenger());
   }

   private record PlayerBody(ServerPlayer player, HeroDefinition hero) implements Body {
      @Override
      public int used() {
         return USED.getOrDefault(this.player.getUUID(), 0);
      }

      @Override
      public void setUsed(int used) {
         USED.put(this.player.getUUID(), used);
      }

      @Override
      public int max() {
         return this.hero.airJumps(this.player);
      }

      @Override
      public double velocity() {
         return this.hero.airJumpVelocity(this.player);
      }

      @Override
      public boolean resetState() {
         return HeroAirJump.resetState(this.player);
      }

      @Override
      public boolean anchored() {
         return HeroDamage.isAnchored(this.player);
      }

      @Override
      public boolean canAct() {
         return this.player.isAlive() && !this.player.isSpectator() && this.hero.canAct(this.player, HeroAction.AIR_STEP);
      }

      @Override
      public Vec3 deltaMovement() {
         return this.player.getDeltaMovement();
      }

      @Override
      public void setDeltaMovement(Vec3 velocity) {
         // Server copy only (no hurtMarked): the client moved already.
         this.player.setDeltaMovement(velocity);
      }

      @Override
      public void resetFall() {
         this.player.resetFallDistance();
      }
   }

   private static final class NetworkSink implements Sink {
      @Override
      public void stepped(Body body) {
         ServerPlayer player = ((PlayerBody) body).player();
         CoreMessages.sendToTracking(new AirJumpSyncS2CPacket(player.getId(), body.used(), true), player);
         ((PlayerBody) body).hero().onAirStep(player);
      }

      @Override
      public void motion(Body body, Vec3 velocity) {
         ServerPlayer player = ((PlayerBody) body).player();
         player.setDeltaMovement(velocity);
         player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), velocity));
      }

      @Override
      public void sync(Body body, int used) {
         ServerPlayer player = ((PlayerBody) body).player();
         CoreMessages.sendToPlayer(new AirJumpSyncS2CPacket(player.getId(), used, false), player);
      }
   }
}
