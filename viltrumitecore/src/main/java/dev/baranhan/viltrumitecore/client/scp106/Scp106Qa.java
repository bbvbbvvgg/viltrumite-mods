package dev.baranhan.viltrumitecore.client.scp106;

import dev.baranhan.viltrumitecore.scp.scp106.Scp106Entity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Playtest helpers for the opt-in QA driver (SubtitlePlaytest): never called in normal play. */
public final class Scp106Qa {
   private Scp106Qa() {
   }

   /** One block per loaded SCP-106 (state, flags, distance, strongest clip weights), then the pocket client state. */
   public static String describe(Minecraft mc) {
      StringBuilder b = new StringBuilder();
      if (mc.level != null && mc.player != null) {
         b.append("dim=").append(mc.level.dimension().location()).append(String.format(Locale.ROOT, " me=%.1f,%.1f,%.1f ",
            mc.player.getX(), mc.player.getY(), mc.player.getZ()));
         for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof Scp106Entity e) {
               b.append(String.format(Locale.ROOT, "[id=%d %s<-%s since=%.1fs pocket=%s held=%d move=%.3f dist=%.1f pos=%.1f,%.1f,%.1f layers=",
                  e.getId(), e.clientState, e.clientPrevState, (e.tickCount - e.clientStateStart) / 20.0F, e.flag(Scp106Entity.FLAG_POCKET),
                  e.heldId(), e.clientMove, e.distanceTo(mc.player), e.getX(), e.getY(), e.getZ()));
               for (int i = 0; i < Scp106Renderer.CLIPS.length; i++) {
                  if (e.clientWeights[i] >= 0.05F) {
                     b.append(Scp106Renderer.CLIPS[i]).append('=').append(String.format(Locale.ROOT, "%.2f", e.clientWeights[i])).append(',');
                  }
               }
               b.append("] ");
            }
         }
      }
      return b.append(PocketClient.qaSummary()).toString();
   }

   /** Turns the camera to the nearest SCP-106. */
   public static void lookAtNearest(Minecraft mc) {
      if (mc.level == null || mc.player == null) {
         return;
      }
      Scp106Entity best = null;
      for (Entity en : mc.level.entitiesForRendering()) {
         if (en instanceof Scp106Entity e && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
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
}
