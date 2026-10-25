package dev.baranhan.viltrumitecore.client.ironman.mark.sig;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.ironman.IronManCombatVfx;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.RocketFistEntity;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManVariant;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Pixel VFX of the mark signatures (animation-system §8): micro-laser beam, the
 * Mark 17 pulses, the War Machine rounds ({@link GunRounds}: travelling tracers,
 * muzzle flash, whiz, impact sparks and chips) and casings, the
 * rocket fist flame trail, the slam dive trail and the Mark 15 screen tint.
 * Everything reads the synced snapshot or the synced entities.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class SignatureVfx {
   private static final int CASING_LIFE = 40;
   private static final int CASING_MAX = 96;
   private static final List<Casing> CASINGS = new ArrayList<>();
   private static final Map<UUID, Integer> GUN_ELAPSED = new HashMap<>();
   private static ClientLevel lastLevel;

   private SignatureVfx() {
   }

   /** A spent shell: a small gold pixel that falls under gravity. */
   private static final class Casing {
      Vec3 pos;
      Vec3 vel;
      int age;

      Casing(Vec3 pos, Vec3 vel) {
         this.pos = pos;
         this.vel = vel;
      }

      void step() {
         this.pos = this.pos.add(this.vel);
         this.vel = new Vec3(this.vel.x * 0.98, this.vel.y - 0.035, this.vel.z * 0.98);
         this.age++;
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         lastLevel = level;
         CASINGS.clear();
         GUN_ELAPSED.clear();
         GunRounds.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null || !IronManView.worn(snapshot) || !IronManView.channel(snapshot, HeroAction.SIGNATURE)
            || IronManVariant.mark(snapshot.variant()) != MarkId.WAR_MACHINE_MK2) {
            GUN_ELAPSED.remove(player.getUUID());
            continue;
         }

         // The server fires on even ticks, so a new odd elapsed value is a fresh shot.
         int elapsed = snapshot.actionElapsed();
         Integer previous = GUN_ELAPSED.put(player.getUUID(), elapsed);
         if (elapsed % SignatureRules.GUN_INTERVAL == 1 && (previous == null || previous != elapsed)) {
            if (CASINGS.size() < CASING_MAX) {
               spawnCasing(player, level.random);
            }

            Vec3 point = snapshot.actionTarget();
      if(point!=null)point=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.endpoint(point);
            if (point != null) {
               Vec3 shoulder = shoulder(player, 1.0F);
               Vec3 toPoint = point.subtract(shoulder);
               if (toPoint.lengthSqr() > 1.0) {
                  GunRounds.fire(level, player, shoulder.add(toPoint.normalize().scale(0.9)), point);
               }
            }
         }
      }

      GunRounds.tick(level);

      Iterator<Casing> casings = CASINGS.iterator();
      while (casings.hasNext()) {
         Casing casing = casings.next();
         casing.step();
         if (casing.age > CASING_LIFE) {
            casings.remove();
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || !hasWork(level)) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.rotateCamera(modelViewStack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Player player : level.players()) {
            HeroPublicSnapshot snapshot = IronManView.of(player);
            if (snapshot != null && IronManView.worn(snapshot) && IronManView.channel(snapshot, HeroAction.SIGNATURE)) {
               drawSignature(buffer, cameraPos, camera, player, snapshot, partialTick);
            }
         }

         GunRounds.draw(buffer, cameraPos, camera, partialTick);
         for (Casing casing : CASINGS) {
            PixelVfx.billboardPixel(buffer, cameraPos, camera, casing.pos, 0.05F, 255, 210, 90, 230);
         }

         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RocketFistEntity fist) {
               drawFist(buffer, cameraPos, camera, fist, partialTick);
            }
         }

         tessellator.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   /** Mark 15 screen tint for the camouflaged player (both views). */
   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.options.hideGui) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null || !IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.MARK_CAMO)) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x1A5AB8FF);
   }

   private static boolean hasWork(ClientLevel level) {
      if (!CASINGS.isEmpty() || GunRounds.busy()) {
         return true;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot != null && IronManView.worn(snapshot) && IronManView.channel(snapshot, HeroAction.SIGNATURE)) {
            return true;
         }
      }

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof RocketFistEntity) {
            return true;
         }
      }

      return false;
   }

   private static void drawSignature(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, HeroPublicSnapshot snapshot, float partialTick) {
      MarkId mark = IronManVariant.mark(snapshot.variant());
      Vec3 point = snapshot.actionTarget();
      if(point!=null)point=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.endpoint(point);
      if (mark == null || point == null) {
         return;
      }

      int flags = snapshot.heroFlags();
      boolean active = IronManFlags.is(flags, IronManFlags.Field.SIGNATURE_ACTIVE);
      int elapsed = snapshot.actionElapsed();
      switch (mark) {
         case MARK_7 -> {
            if (active) {
               drawLaser(buffer, cameraPos, camera, player, point, partialTick);
            }
         }
         case MARK_17 -> {
            if (active && SignatureRules.pulseAt(elapsed) >= 0) {
               drawPulse(buffer, cameraPos, camera, player, point, partialTick);
            }
         }
         case IRON_HEART_MK3 -> {
            if (active && IronManFlags.is(flags, IronManFlags.Field.SIGNATURE_AUX)) {
               drawDive(buffer, cameraPos, camera, player, point, partialTick);
            }
         }
         default -> {
         }
      }
   }

   private static void drawLaser(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, Vec3 point, float partialTick) {
      Vec3 from = hand(player, partialTick);
      PixelVfx.beamDots(buffer, cameraPos, camera, from, point, 0.05F, 0.05F, 255, 60, 60, 200);
      PixelVfx.beamDots(buffer, cameraPos, camera, from.add(0.0, 0.03, 0.0), point, 0.08F, 0.025F, 120, 240, 255, 180);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, point, 0.14F, 255, 90, 90, 220);
   }

   private static void drawPulse(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, Vec3 point, float partialTick) {
      Vec3 from = IronManCombatVfx.chest(player, partialTick);
      boolean defended=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.defended(point);
      float flicker = 0.85F + 0.15F * Mth.sin((player.tickCount + partialTick) * 1.9F);
      PixelVfx.beamDots(buffer, cameraPos, camera, from, point, 0.06F, defended?.045F:.3F, 90, 170, 255, (int)(110 * flicker));
      PixelVfx.beamDots(buffer, cameraPos, camera, from, point, 0.06F, defended?.02F:.13F, 230, 250, 255, (int)(240 * flicker));
      PixelVfx.crossGlow(buffer, cameraPos, camera, from, 0.35F, 170, 230, 255, 230);
      PixelVfx.crossGlow(buffer, cameraPos, camera, point, defended?.065F:.45F, 170, 230, 255, 230);
   }

   private static void drawDive(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, Vec3 start, float partialTick) {
      Vec3 chest = IronManCombatVfx.chest(player, partialTick);
      PixelVfx.beamDots(buffer, cameraPos, camera, start, chest, 0.35F, 0.05F, 150, 200, 255, 110);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, start, 0.12F, 170, 220, 255, 160);
   }

   private static void drawFist(BufferBuilder buffer, Vec3 cameraPos, Camera camera, RocketFistEntity fist, float partialTick) {
      Vec3 at = fist.getPosition(partialTick);
      Vec3 prev = null;
      int i = 0;
      for (Vec3 point : fist.trail()) {
         if (prev != null) {
            PixelVfx.beamDots(buffer, cameraPos, camera, prev, point, 0.1F, 0.05F + i * 0.004F, 255, 150, 70, 90 + i * 8);
         }

         prev = point;
         i++;
      }

      if (prev != null) {
         PixelVfx.beamDots(buffer, cameraPos, camera, prev, at, 0.1F, 0.08F, 255, 150, 70, 150);
      }

      PixelVfx.crossGlow(buffer, cameraPos, camera, at, 0.22F, 255, 200, 120, 200);
   }

   private static void spawnCasing(Player player, RandomSource random) {
      Vec3 right = rightVector(player);
      Vec3 at = shoulder(player, 1.0F).add(right.scale(0.1));
      Vec3 velocity = right.scale(0.10 + random.nextDouble() * 0.05)
         .add(0.0, 0.10 + random.nextDouble() * 0.05, 0.0)
         .add(player.getLookAngle().scale(-0.02 * random.nextDouble()));
      CASINGS.add(new Casing(at, velocity));
   }

   /** Right shoulder, where the War Machine turret sits. */
   private static Vec3 shoulder(Player player, float partialTick) {
      return player.getPosition(partialTick).add(0.0, 1.42, 0.0).add(rightVector(player).scale(0.45));
   }

   private static Vec3 rightVector(Player player) {
      double yaw = Math.toRadians(player.yBodyRot);
      return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
   }

   /** Palm-side forearm point of the right hand, as the server computes it for the nano weapons. */
   private static Vec3 hand(Player player, float partialTick) {
      Vec3 look = player.getViewVector(partialTick);
      Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
      return player.getEyePosition(partialTick).add(0.0, -0.35, 0.0).add(right.scale(0.38)).add(look.scale(0.7));
   }
}
