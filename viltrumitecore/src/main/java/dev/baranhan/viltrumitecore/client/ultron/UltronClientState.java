package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronCapture;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/** The last uprising state the server sent this client (HUD, node markers, tethers, loops). */
public final class UltronClientState {
   @Nullable
   private static UltronStateS2CPacket state;
   private static long receivedAt;
   private static int lastPhase = -1;
   private static long phaseChangedAt;

   private UltronClientState() {
   }

   public static void accept(UltronStateS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      long now = mc.level == null ? 0L : mc.level.getGameTime();
      if (!packet.active()) {
         state = null;
         lastPhase = -1;
         UltronCapture.setClientCaptured(new int[0]);
         return;
      }
      if (packet.phase() != lastPhase) {
         lastPhase = packet.phase();
         phaseChangedAt = now;
      }
      state = packet;
      receivedAt = now;
      UltronCapture.setClientCaptured(packet.captured());
   }

   @Nullable
   public static UltronStateS2CPacket get() {
      return state;
   }

   public static boolean active() {
      return state != null;
   }

   public static long receivedAt() {
      return receivedAt;
   }

   public static long phaseChangedAt() {
      return phaseChangedAt;
   }

   /** World left / disconnected: forget everything. */
   public static void reset() {
      state = null;
      lastPhase = -1;
      UltronCapture.setClientCaptured(new int[0]);
   }
}
