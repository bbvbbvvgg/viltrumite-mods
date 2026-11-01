package dev.baranhan.viltrumitecore.client.collapse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client side of structural collapses: keeps the scenes the server announced (at most {@link #MAX_SCENES},
 * the oldest goes first), ticks them and draws their fragments after the entities. A far collapse whose
 * START this client never got still gets its distant boom. Everything is dropped on logout and level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CollapseClient {
   static final int MAX_SCENES = 4;
   private static final List<CollapseScene> SCENES = new ArrayList<>();
   private static Object lastLevel;
   // QA metrics (/collapse is server side; the client logs with -Dviltrumitecore.collapse.log=true)
   static long lastRenderNanos, maxRenderNanos;
   static int lastDrawn;

   private CollapseClient() {
   }

   /** Scenes running now (audio ducking, particle budgets). */
   static int running() {
      return Math.max(1, SCENES.size());
   }

   public static int sceneCount() {
      return SCENES.size();
   }

   public static void handle(CityCollapseS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      if (packet.kind == CityCollapseS2CPacket.START) {
         if (find(packet.id) != null) {
            return;
         }
         while (SCENES.size() >= MAX_SCENES) {
            SCENES.remove(0).close();
         }
         SCENES.add(new CollapseScene(mc.level, packet));
         return;
      }
      CollapseScene scene = find(packet.id);
      if (scene != null) {
         scene.onPhase(packet);
      } else if (packet.kind == CityCollapseS2CPacket.IMPACT) {
         // no scene (joined late, out of range at the start): the far boom still carries
         float strength = dev.baranhan.viltrumitecore.world.collapse.CollapseSeverity.byOrdinal(packet.severity).strength();
         CollapseAudio.cue(dev.baranhan.viltrumitecore.world.collapse.CollapseSounds.DISTANT, CollapseAudio.Layer.DISTANT,
            new Vec3(packet.center.x, packet.groundY + 1.0, packet.center.z), CollapseAudio.loudness(strength), CollapseAudio.pitch(strength, 0.5), 0, running());
         CollapseAudio.cue(dev.baranhan.viltrumitecore.world.collapse.CollapseSounds.RUMBLE, CollapseAudio.Layer.RUMBLE,
            new Vec3(packet.center.x, packet.groundY + 1.0, packet.center.z), 0.8F * CollapseAudio.loudness(strength), CollapseAudio.pitch(strength, 0.5), 0, running());
      }
   }

   private static CollapseScene find(long id) {
      for (CollapseScene s : SCENES) {
         if (s.id == id) {
            return s;
         }
      }
      return null;
   }

   public static void clear() {
      for (CollapseScene s : SCENES) {
         s.close();
      }
      SCENES.clear();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
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
      Iterator<CollapseScene> it = SCENES.iterator();
      while (it.hasNext()) {
         CollapseScene s = it.next();
         if (s.level != mc.level || s.tick()) {
            s.close();
            it.remove();
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SCENES.isEmpty()) {
         return;
      }
      boolean any = false;
      for (CollapseScene s : SCENES) {
         any |= s.hasPieces();
      }
      if (!any) {
         return;
      }
      long t0 = System.nanoTime();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack pose = event.getPoseStack();
      float partial = event.getPartialTick();
      RenderType type = FragmentMesh.type();
      type.setupRenderState();
      int drawn = 0;
      try {
         for (CollapseScene s : SCENES) {
            drawn += s.render(pose, cam, partial);
         }
      } finally {
         VertexBuffer.unbind();
         type.clearRenderState();
      }
      lastDrawn = drawn;
      lastRenderNanos = System.nanoTime() - t0;
      maxRenderNanos = Math.max(maxRenderNanos, lastRenderNanos);
      RenderSystem.assertOnRenderThread();
   }
}
