package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.scp.scp096.Scp096Entity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Playtest helpers for the opt-in QA driver (SubtitlePlaytest): never called in normal play. */
public final class Scp096Qa {
   private Scp096Qa() {
   }

   /** One line per loaded SCP-096: state, flags, speed, distance and the strongest layers; then the sound summary. */
   public static String describe(Minecraft mc) {
      StringBuilder b = new StringBuilder();
      if (mc.level != null && mc.player != null) {
         for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof Scp096Entity e) {
               b.append(String.format(Locale.ROOT, "[id=%d %s<-%s since=%.1fs flags=%s%s%s%s move=%.3f air=%d dist=%.1f pos=%.1f,%.1f,%.1f layers=%s] ",
                  e.getId(), e.clientState, e.clientPrevState, (e.tickCount - e.clientStateStart) / 20.0F,
                  e.flag(Scp096Entity.FLAG_FACE_OPEN) ? "F" : "-", e.flag(Scp096Entity.FLAG_COVER) ? "C" : "-",
                  e.flag(Scp096Entity.FLAG_CLIMB) ? "K" : "-", e.flag(Scp096Entity.FLAG_LEAP) ? "L" : "-", e.clientMove, e.clientAirTicks,
                  e.distanceTo(mc.player), e.getX(), e.getY(), e.getZ(), layers(e.clientWeights)));
            }
         }
      }
      return b.append(Scp096ClientSounds.describe()).toString();
   }

   /** Turns the camera to the face of the nearest SCP-096 (to trigger it on purpose in a playtest). */
   public static void lookAtNearest(Minecraft mc) {
      if (mc.level == null || mc.player == null) {
         return;
      }
      Scp096Entity best = null;
      for (Entity en : mc.level.entitiesForRendering()) {
         if (en instanceof Scp096Entity e && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
            best = e;
         }
      }
      if (best != null) {
         Vec3 eye = mc.player.getEyePosition();
         Vec3 face = best.getEyePosition();
         double dx = face.x - eye.x;
         double dy = face.y - eye.y;
         double dz = face.z - eye.z;
         mc.player.setYRot((float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
         mc.player.setXRot((float)(-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG)));
      }
   }

   private static String layers(float[] w) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < Scp096Pose.LAYERS; i++) {
         if (w[i] >= 0.05F) {
            b.append(Scp096Pose.CLIP[i]).append('=').append(String.format(Locale.ROOT, "%.2f", w[i])).append(',');
         }
      }
      b.append("sob=").append(String.format(Locale.ROOT, "%.2f", w[Scp096Pose.SOB]));
      return b.toString();
   }
}
