package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server side of the Captain / Avengers presentation events (one packet type, {@link CaptainFxS2CPacket}). */
public final class CaptainFx {
   /** Owner only: re-apply velocity a for arg ticks; b.x > 0.5 keeps vanilla gravity. */
   public static final int MOTION = 0;
   /** Hit spark at a; arg 0 shield, 1 fist, 2 kick, 3 counter/return strike, 4 rush. b = attacker eye. */
   public static final int CONTACT = 1;
   public static final int PARRY = 2;
   public static final int BLOCK = 3;
   /** Shield ricochet at a, b = bounce normal / new direction. */
   public static final int RICOCHET = 4;
   public static final int CATCH = 5;
   /** Ground slam ring at a, b.x radius, arg 1 empowered. */
   public static final int SLAM = 6;
   public static final int RUSH = 7;
   /** Finisher ring at a, b.x radius. */
   public static final int FINISHER = 8;
   /** Roll afterimages a -> b. */
   public static final int ROLL = 9;
   /** Kinetic charge reached the empower threshold (glow pulse on the shield). */
   public static final int CHARGED = 10;
   /** Stun stars over ids for arg ticks. */
   public static final int STUN = 11;
   /** Rally: the star flash over Steve at a. */
   public static final int ASSEMBLE = 12;
   /** Throw: release point a, direction b. */
   public static final int THROW = 13;
   // --- Avengers ---
   /** Repulsor beam a -> b (arg 0 hand, 1 both hands). */
   public static final int REPULSOR = 20;
   /** Unibeam a -> b, arg remaining ticks. */
   public static final int UNIBEAM = 21;
   /** Explosion at a, b.x radius, arg 0 missile, 1 arrow. */
   public static final int EXPLOSION = 22;
   /** Hulk / landing shock ring at a, b.x radius, arg 0 hulk smash, 1 iron man, 2 hero landing, 3 thor. */
   public static final int GROUND = 23;
   /** Widow's Bite arc on ids. */
   public static final int BITE = 24;
   /** Pistol tracer a -> b. */
   public static final int SHOT = 25;
   /** Grapple line from entity to a for arg ticks. */
   public static final int GRAPPLE = 27;
   /** Shock arc a -> b. */
   public static final int SHOCK = 28;
   /** Thrusters burst at a (Iron Man take-off / braking). */
   public static final int THRUST = 29;

   private CaptainFx() {
   }

   public static void toOwner(ServerPlayer owner, int type, int arg, Vec3 a, Vec3 b, int... ids) {
      CoreMessages.sendToPlayer(packet(type, owner.getId(), arg, a, b, ids), owner);
   }

   /** Everyone tracking the source plus the source itself (if a player). */
   public static void around(Entity source, int type, int arg, Vec3 a, Vec3 b, int... ids) {
      CoreMessages.sendToTrackingAndSelf(packet(type, source.getId(), arg, a, b, ids), source);
   }

   /** Every player of the level within {@code range} of the point. */
   public static void near(ServerLevel level, Vec3 at, double range, int type, int sourceId, int arg, Vec3 a, Vec3 b, int... ids) {
      CaptainFxS2CPacket packet = packet(type, sourceId, arg, a, b, ids);
      for (ServerPlayer player : level.players()) {
         if (player.position().distanceToSqr(at) <= range * range) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }

   public static void motion(ServerPlayer owner, Vec3 velocity, int ticks, boolean gravity) {
      toOwner(owner, MOTION, ticks, velocity, new Vec3(gravity ? 1 : 0, 0, 0));
   }

   private static CaptainFxS2CPacket packet(int type, int source, int arg, Vec3 a, Vec3 b, int[] ids) {
      Vec3 p = a == null ? Vec3.ZERO : a;
      Vec3 q = b == null ? Vec3.ZERO : b;
      return new CaptainFxS2CPacket(type, source, arg, (float)p.x, (float)p.y, (float)p.z, (float)q.x, (float)q.y, (float)q.z,
         ids == null ? new int[0] : ids);
   }
}
