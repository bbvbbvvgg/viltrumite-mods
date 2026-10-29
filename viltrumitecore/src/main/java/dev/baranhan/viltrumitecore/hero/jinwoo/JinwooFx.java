package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server side of Jinwoo's presentation events (one packet type, {@link JinwooFxS2CPacket}). */
public final class JinwooFx {
   /** Owner only: re-apply velocity (x,y,z) for {@code arg} ticks. */
   public static final int MOTION = 0;
   /** Dagger contact spark at (x,y,z); arg = 0 right blade, 1 left, 2 both. */
   public static final int CONTACT = 1;
   public static final int PARRY = 2;
   /** Shadow Step: afterimages from (x,y,z) to (x2,y2,z2). */
   public static final int STEP = 3;
   /** A soul remnant appears: arg = remnant id, (x,y,z) position, x2 = kind ordinal. */
   public static final int REMNANT_ADD = 4;
   /** A remnant ends: arg = id, x2 = 1 extracted / 0 crumbled / -1 expired. */
   public static final int REMNANT_REMOVE = 5;
   /** Ruler's Authority threads: ids = held targets; arg = 1 grip, 0 released. */
   public static final int AUTHORITY = 6;
   /** Shadow wave ring at (x,y,z), x2 = radius. */
   public static final int WAVE = 7;
   /** Landing burst at (x,y,z). */
   public static final int LANDING = 8;
   /** Order marker: arg = target entity id or -1 for a point (x,y,z); x2 = order (0 attack, 1 guard, 2 sentinel). */
   public static final int COMMAND = 9;
   /** Summon/recall shadow streams from the owner to (x,y,z); arg = 1 summon, 0 recall. */
   public static final int STREAM = 10;
   /** Exchange: puddles at (x,y,z) and (x2,y2,z2). */
   public static final int EXCHANGE = 11;
   /** Extraction failure smoke at (x,y,z). */
   public static final int EXTRACT_FAIL = 12;
   /** Monarch charge streaks from (x,y,z) to (x2,y2,z2). */
   public static final int CHARGE = 13;

   private JinwooFx() {
   }

   public static void toOwner(ServerPlayer owner, int type, int arg, Vec3 a, Vec3 b, int... ids) {
      CoreMessages.sendToPlayer(packet(type, owner.getId(), arg, a, b, ids), owner);
   }

   /** Everyone tracking the source plus the source itself. */
   public static void around(Entity source, int type, int arg, Vec3 a, Vec3 b, int... ids) {
      CoreMessages.sendToTrackingAndSelf(packet(type, source.getId(), arg, a, b, ids), source);
   }

   /** Every player of the level within {@code range} of the point. */
   public static void near(ServerLevel level, Vec3 at, double range, int type, int sourceId, int arg, Vec3 a, Vec3 b, int... ids) {
      JinwooFxS2CPacket packet = packet(type, sourceId, arg, a, b, ids);
      for (ServerPlayer player : level.players()) {
         if (player.position().distanceToSqr(at) <= range * range) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }

   public static void motion(ServerPlayer owner, Vec3 velocity, int ticks) {
      toOwner(owner, MOTION, ticks, velocity, Vec3.ZERO);
   }

   private static JinwooFxS2CPacket packet(int type, int source, int arg, Vec3 a, Vec3 b, int[] ids) {
      Vec3 p = a == null ? Vec3.ZERO : a;
      Vec3 q = b == null ? Vec3.ZERO : b;
      return new JinwooFxS2CPacket(type, source, arg, (float)p.x, (float)p.y, (float)p.z, (float)q.x, (float)q.y, (float)q.z, ids == null ? new int[0] : ids);
   }
}
