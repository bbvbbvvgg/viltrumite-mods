package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Audit v3 (item 6): Mjölnir's swing trail. The {@link ThorAttacks.Trail} of every strike row was authored and
 * never drawn; now the strike's row decides that its swing trails, and the ribbon follows the real hammer:
 * {@link ThorAnchors#capture} hands over the posed head and grip (both views, first person reprojected into the
 * world) every frame the hammer is drawn, so the trail is the clip's own arc, on the clip's own ticks. It is
 * laid from {@link #LEAD} ticks before the contact to the end of the contact, fades in {@link #LIFE} seconds,
 * additive, pale steel (a storm blue in the electric mode) and thin at the handle side. World pass, client only.
 */
public final class MjolnirTrails {
   static final double LIFE = 0.14;
   static final int MAX_SAMPLES = 30;
   /** Ticks before the contact tick the ribbon starts (the fast part of the swing). */
   static final int LEAD = 3;
   private static final Map<Player, ArrayDeque<Sample>> TRAILS = new WeakHashMap<>();

   record Sample(Vec3 tip, Vec3 base, long nanos, boolean electric) {
   }

   private MjolnirTrails() {
   }

   public static void init() {
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(MjolnirTrails::onRenderLevel);
   }

   /** Pure: does this strike row trail at this point of its clock. */
   static boolean emitting(@Nullable ThorAttacks.Attack row, double elapsed) {
      if (row == null || row.trail().none() || row.strike() == null) {
         return false;
      }

      ThorAttacks.Phases p = row.phases();
      return elapsed >= p.windup() - LEAD && elapsed <= p.windup() + p.contact();
   }

   /** ThorAnchors.capture: the hammer was just drawn with its head and grip at these world points. */
   static void record(Player player, Vec3 head, Vec3 grip) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      ThorMove move = snapshot == null ? null : ThorView.move(snapshot);
      ThorAttacks.Attack row = move == null ? null : ThorAttacks.of(move, snapshot.resource());
      if (!emitting(row, snapshot == null ? 0.0 : snapshot.actionElapsed() + Minecraft.getInstance().getFrameTime())) {
         return;
      }

      ArrayDeque<Sample> samples = TRAILS.computeIfAbsent(player, p -> new ArrayDeque<>());
      long now = Util.getNanos();
      Sample last = samples.peekLast();
      if (last != null && now - last.nanos() < 4_000_000L) {
         return;
      }

      // The face of the head away from the grip, and a point down the handle: a ribbon as wide as the swing reads.
      Vec3 axis = head.subtract(grip);
      samples.addLast(new Sample(head.add(axis.scale(0.35)), head.subtract(axis.scale(0.45)), now, dev.baranhan.viltrumitecore.hero.thor.ThorFlags.mode(snapshot.heroFlags()) == ThorMode.ELECTRIC));
      while (samples.size() > MAX_SAMPLES) {
         samples.removeFirst();
      }
   }

   static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.rotateCamera(modelView, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buffer = tess.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         long now = Util.getNanos();
         Iterator<Map.Entry<Player, ArrayDeque<Sample>>> it = TRAILS.entrySet().iterator();
         while (it.hasNext()) {
            ArrayDeque<Sample> samples = it.next().getValue();
            while (!samples.isEmpty() && (now - samples.peekFirst().nanos()) / 1.0E9 > LIFE) {
               samples.removeFirst();
            }

            if (samples.isEmpty()) {
               it.remove();
               continue;
            }

            Sample prev = null;
            for (Sample cur : samples) {
               if (prev != null) {
                  float a0 = alpha(now, prev);
                  float a1 = alpha(now, cur);
                  quad(buffer, cam, prev, cur, a0, a1);
               }

               prev = cur;
            }
         }

         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   static float alpha(long now, Sample sample) {
      return Math.max(0.0F, 1.0F - (float)((now - sample.nanos()) / 1.0E9 / LIFE));
   }

   /** Pure: the colour of a sample (0 newest .. 1 oldest): pale steel, or storm blue in the electric mode. */
   static int[] color(boolean electric, float age) {
      return electric ? new int[]{(int)(150 - 70 * age), (int)(200 - 60 * age), 255} : new int[]{(int)(225 - 45 * age), (int)(230 - 40 * age), 240};
   }

   private static void quad(BufferBuilder b, Vec3 cam, Sample s0, Sample s1, float a0, float a1) {
      int[] c0 = color(s0.electric(), 1.0F - a0);
      int[] c1 = color(s1.electric(), 1.0F - a1);
      Vec3 b0 = s0.base().subtract(cam);
      Vec3 t0 = s0.tip().subtract(cam);
      Vec3 t1 = s1.tip().subtract(cam);
      Vec3 b1 = s1.base().subtract(cam);
      int tip0 = (int)(a0 * (s0.electric() ? 170 : 120));
      int tip1 = (int)(a1 * (s1.electric() ? 170 : 120));
      b.vertex(b0.x, b0.y, b0.z).color(c0[0], c0[1], c0[2], (int)(a0 * a0 * 20)).endVertex();
      b.vertex(t0.x, t0.y, t0.z).color(c0[0], c0[1], c0[2], tip0).endVertex();
      b.vertex(t1.x, t1.y, t1.z).color(c1[0], c1[1], c1[2], tip1).endVertex();
      b.vertex(b1.x, b1.y, b1.z).color(c1[0], c1[1], c1[2], (int)(a1 * a1 * 20)).endVertex();
   }

   public static void clear() {
      TRAILS.clear();
   }
}
