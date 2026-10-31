package dev.baranhan.viltrumitecore.client.ironman.legion;

import dev.baranhan.viltrumitecore.client.ironman.jarvis.JarvisVoice;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSquad;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus;
import dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Client side of the Iron Legion (design §8–§9): the owner's last squad
 * status (HUD, command menu), the JARVIS lines of its events and the menu
 * packet. The lines queue briefly when another JARVIS line is playing, so a
 * "Legion activated" never swallows "three suits at your disposal".
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ClientLegion {
   /** A queued line is dropped when it could not play within this many ticks. */
   private static final int LINE_PATIENCE = 120;
   private static final Deque<Pending> PENDING = new ArrayDeque<>();
   private static LegionStatus status = LegionStatus.NONE;
   private static long statusTick;

   private ClientLegion() {
   }

   private record Pending(JarvisVoice.Line line, long until) {
   }

   /** The local player owns the suit with this owner entity id (his crosshair passes through it). */
   public static boolean isLocalOwner(int ownerId) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && ownerId >= 0 && player.getId() == ownerId;
   }

   public static LegionStatus status() {
      return status;
   }

   /** Ticks since the last status packet (HUD fades stale data). */
   public static long statusAge() {
      Minecraft client = Minecraft.getInstance();
      return client.level == null ? Long.MAX_VALUE : client.level.getGameTime() - statusTick;
   }

   public static void openMenu(LegionMenuS2CPacket packet) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         client.setScreen(new LegionScreen(packet));
      }
   }

   public static void status(LegionStatus next) {
      Minecraft client = Minecraft.getInstance();
      status = next;
      statusTick = client.level == null ? 0L : client.level.getGameTime();
      long now = statusTick;
      // Most important first: a critical suit or the recall outranks the greeting.
      queue(next, LegionSquad.EVENT_CRITICAL, JarvisVoice.Line.LEGION_CRITICAL, now);
      queue(next, LegionSquad.EVENT_RETURN, JarvisVoice.Line.LEGION_RETURN, now);
      queue(next, LegionSquad.EVENT_ON, JarvisVoice.Line.LEGION_ON, now);
      queue(next, LegionSquad.EVENT_THREE, JarvisVoice.Line.LEGION_THREE, now);
      queue(next, LegionSquad.EVENT_THREAT, JarvisVoice.Line.LEGION_THREAT, now);
      if (client.screen instanceof LegionScreen screen) {
         screen.onStatus(next);
      }
   }

   private static void queue(LegionStatus next, int event, JarvisVoice.Line line, long now) {
      if (next.has(event) && PENDING.stream().noneMatch(p -> p.line() == line)) {
         PENDING.addLast(new Pending(line, now + LINE_PATIENCE));
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      Minecraft client = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || client.level == null || client.isPaused() || PENDING.isEmpty()) {
         return;
      }

      long now = client.level.getGameTime();
      while (!PENDING.isEmpty() && PENDING.peekFirst().until() < now) {
         PENDING.pollFirst();
      }

      Pending next = PENDING.peekFirst();
      if (next != null && JarvisVoice.legion(next.line())) {
         PENDING.pollFirst();
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      status = LegionStatus.NONE;
      statusTick = 0L;
      PENDING.clear();
      LegionSuitRenderer.clear();
      LegionVfx.clear();
   }
}
