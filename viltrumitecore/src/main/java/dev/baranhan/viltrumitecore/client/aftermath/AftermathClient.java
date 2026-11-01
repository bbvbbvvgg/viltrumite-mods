package dev.baranhan.viltrumitecore.client.aftermath;

import dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * City disaster aftermath on this client: the ruin zones the server sent ({@link AftermathS2CPacket}), their
 * clocks (counted on here between updates), and every tick the scanner, the effects, the soundscape and the
 * fog of a badly wrecked quarter. Forgets everything on a level change and on logout; nothing here exists on
 * a dedicated server.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class AftermathClient {
   /** QA: {@code -Dviltrumitecore.aftermath.log=true} logs {@link #debugLine()}, FPS and the client cost every 10 s. */
   private static final boolean QA_LOG = Boolean.getBoolean("viltrumitecore.aftermath.log");
   private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("viltrumitecore/aftermath");
   private static long qaNanos;

   /** Fog pull-in at full fog channel when standing in the ruin: the far plane comes to this share. */
   static final float FOG_PULL = 0.62F;
   private static final Map<Integer, ClientZone> ZONES = new HashMap<>();
   private static final List<ClientZone> SORTED = new ArrayList<>();
   private static ClientLevel lastLevel;
   private static int clock;
   private static float fog;
   private static float fogGoal;

   private AftermathClient() {
   }

   public static void handle(AftermathS2CPacket packet) {
      switch (packet.kind) {
         case AftermathS2CPacket.SYNC -> {
            for (AftermathZone.Snapshot s : packet.zones) {
               ClientZone zn = ZONES.computeIfAbsent(s.id(), ClientZone::new);
               zn.apply(s);
            }
         }
         case AftermathS2CPacket.REMOVE -> {
            for (int id : packet.ids) {
               ZONES.remove(id);
               AftermathAudio.forget(id);
            }
         }
         case AftermathS2CPacket.CLEAR -> clear();
         default -> {
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         clear();
      }

      if (mc.level == null || mc.isPaused()) {
         return;
      }

      clock++;
      if (ZONES.isEmpty()) {
         fogGoal = 0.0F;
         fog += (fogGoal - fog) * 0.03F;
         AftermathFx.wind(clock, false);
         return;
      }

      long start = QA_LOG ? System.nanoTime() : 0L;
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      SORTED.clear();
      for (ClientZone zn : ZONES.values()) {
         zn.age++;
         if (clock % 10 == 0 || zn.age < 200) {
            zn.update();
         }

         zn.distance = Math.sqrt(zn.distanceSq(cam.x, cam.y, cam.z));
         SORTED.add(zn);
      }

      SORTED.sort(Comparator.comparingDouble(z -> z.distance - z.radius));
      RandomSource random = mc.level.getRandom();
      AftermathScanner.tick(mc.level, SORTED, clock);
      AftermathFx.wind(clock, SORTED.get(0).distance - SORTED.get(0).radius < 200.0);
      AftermathFx.tick(mc, SORTED, cam, random, clock);
      AftermathAudio.tick(mc, SORTED, cam, random, clock);
      float goal = 0.0F;
      for (ClientZone zn : SORTED) {
         goal = Math.max(goal, zn.channel(AftermathRules.FOG) * inside(zn.distance, zn.radius));
      }

      fogGoal = goal;
      fog += (fogGoal - fog) * 0.03F;
      if (QA_LOG) {
         qaNanos += System.nanoTime() - start;
         if (clock % 200 == 0) {
            LOGGER.info("AMQA client {} fps={} cost={}us/tick particles={}", debugLine(), mc.getFps(), qaNanos / 1000L / 200L,
               mc.particleEngine.countParticles());
            qaNanos = 0L;
         }
      }
   }

   /** Pure: how deep the camera is in a ruin for its fog: 1 well inside, 0 beyond 1.3 radii. */
   public static float inside(double distance, float radius) {
      return Mth.clamp((float)((radius * 1.3 - distance) / Math.max(1.0, radius * 0.6)), 0.0F, 1.0F);
   }

   /** Pure: fog far plane in a ruin with fog {@code f} 0..1 (never closer than 28 blocks). */
   public static float fogFar(float far, float f) {
      return Math.max(Math.min(far, 28.0F), far * (1.0F - FOG_PULL * Mth.clamp(f, 0.0F, 1.0F)));
   }

   @SubscribeEvent
   public static void onRenderFog(ViewportEvent.RenderFog event) {
      if (fog <= 0.01F || event.getType() != FogType.NONE) {
         return;
      }

      event.setFarPlaneDistance(fogFar(event.getFarPlaneDistance(), fog));
      event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), event.getFarPlaneDistance() * 0.3F));
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onFogColor(ViewportEvent.ComputeFogColor event) {
      if (fog <= 0.01F) {
         return;
      }

      // dusty grey-brown air
      float k = Mth.clamp(fog * 0.55F, 0.0F, 0.55F);
      event.setRed(Mth.lerp(k, event.getRed(), 0.46F));
      event.setGreen(Mth.lerp(k, event.getGreen(), 0.43F));
      event.setBlue(Mth.lerp(k, event.getBlue(), 0.39F));
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
      fog = 0.0F;
   }

   static void clear() {
      ZONES.clear();
      SORTED.clear();
      AftermathAudio.clear();
      fogGoal = 0.0F;
   }

   // ---------------------------------------------------------------- QA

   /** Zones known now. */
   public static int zoneCount() {
      return ZONES.size();
   }

   /** Emitters found in all zones. */
   public static int emitterCount() {
      int n = 0;
      for (ClientZone zn : ZONES.values()) {
         n += zn.emitters.size();
      }

      return n;
   }

   /** One line for the QA log: zones, emitters, stage of the nearest, fog, beds, one-shots played. */
   public static String debugLine() {
      ClientZone near = SORTED.isEmpty() ? null : SORTED.get(0);
      return String.format(java.util.Locale.ROOT, "aftermath zones=%d emitters=%d nearest=%s fog=%.2f beds=%d oneshots=%d", ZONES.size(), emitterCount(),
         near == null ? "-" : near.id + ":" + near.stage.id + "@" + Math.round(near.distance), fog, AftermathAudio.beds(), AftermathAudio.played());
   }
}
