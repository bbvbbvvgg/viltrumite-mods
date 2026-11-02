package dev.baranhan.viltrumitecore.client.t1000;

import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Playtest helpers for the opt-in QA driver (SubtitlePlaytest): never called in normal play. */
public final class T1000Qa {
   /** Render cost of the metal layer: nanoseconds of the last drawn body and bodies drawn since the last report. */
   static long lastNanos;
   static long maxNanos;
   static int drawn;

   private T1000Qa() {
   }

   static void record(long nanos) {
      lastNanos = nanos;
      maxNanos = Math.max(maxNanos, nanos);
      drawn++;
   }

   /** One line per loaded T-1000 as this client sees it (synced state), then droplets and the body render cost. */
   public static String describe(Minecraft mc) {
      StringBuilder b = new StringBuilder();
      if (mc.level != null && mc.player != null) {
         for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof T1000Entity e) {
               b.append(String.format(Locale.ROOT, "[id=%d form=%s age=%.0f action=%s/%.0f arms=%s,%s morph=%.2f,%.2f flags=%d wounds=%d hp=%.1f look=%s dist=%.1f pos=%.1f,%.1f,%.1f bb=%.2fx%.2f alive=%s] ",
                  e.getId(), e.form(), e.formAge(0.0F), e.acting() ? e.action() : "NONE", e.actionAge(0.0F), e.rightArm(), e.leftArm(), e.armMorph(true, 0.0F),
                  e.armMorph(false, 0.0F), e.flags(), e.wounds().size(), e.getHealth(), e.disguiseKind(), e.distanceTo(mc.player), e.getX(), e.getY(), e.getZ(),
                  e.getBbWidth(), e.getBbHeight(), e.isAlive()));
            }
         }
      }
      b.append(String.format(Locale.ROOT, "drops=%d bodyMs=%.3f maxMs=%.3f drawn=%d", T1000Droplets.count(), lastNanos / 1.0E6, maxNanos / 1.0E6, drawn));
      maxNanos = 0L;
      drawn = 0;
      return b.toString();
   }

   /** Turns the camera to the nearest T-1000's head. */
   public static void lookAtNearest(Minecraft mc) {
      if (mc.level == null || mc.player == null) {
         return;
      }
      T1000Entity best = null;
      for (Entity en : mc.level.entitiesForRendering()) {
         if (en instanceof T1000Entity e && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
            best = e;
         }
      }
      if (best != null) {
         Vec3 eye = mc.player.getEyePosition();
         Vec3 at = best.position().add(0.0, best.getBbHeight() * 0.6, 0.0);
         double dx = at.x - eye.x;
         double dy = at.y - eye.y;
         double dz = at.z - eye.z;
         mc.player.setYRot((float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
         mc.player.setXRot((float)(-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG)));
      }
   }
}
