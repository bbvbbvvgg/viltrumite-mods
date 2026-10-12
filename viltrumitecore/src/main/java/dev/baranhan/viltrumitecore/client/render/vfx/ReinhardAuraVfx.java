package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.client.reinhard.AuraRules;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Reinhard's mode auras (spec §8, pixel style, additive): Knight — white wind motes circling the
 * feet (stateless, {@link AuraRules#windMote}); Holy Sword — gold sparks near the body (spawned per
 * tick); Restrained — none. The local first-person view keeps only a few feet motes. In every mode,
 * Mana Absorption: faint blue motes drift from a drained player to Reinhard, seen by those two only. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReinhardAuraVfx {
   private static final float MOTE_SIZE = 0.035F;
   private static final float SPARK_SIZE = 0.04F;
   private static final float MANA_MOTE_SIZE = 0.03F;
   private static final double SPARK_RISE_PER_TICK = 0.02;
   private static final List<Spark> SPARKS = new ArrayList<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static ClientLevel lastLevel;

   private ReinhardAuraVfx() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         // Logout or level change.
         lastLevel = client.level;
         SPARKS.clear();
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      SPARKS.removeIf(spark -> ++spark.age > AuraRules.GOLD_SPARK_LIFE_TICKS);
      for (Player player : client.level.players()) {
         HeroPublicSnapshot snapshot = ReinhardView.of(player);
         if (snapshot == null || player.isInvisible()) {
            continue;
         }

         double chance = AuraRules.goldSparkChance(ReinhardView.mode(snapshot), localFirstPerson(client, player));
         if (chance > 0.0 && RANDOM.nextDouble() < chance) {
            double half = player.getBbWidth() * 0.6;
            Vec3 at = player.position().add((RANDOM.nextDouble() * 2.0 - 1.0) * half, 0.2 + RANDOM.nextDouble() * (player.getBbHeight() - 0.3),
               (RANDOM.nextDouble() * 2.0 - 1.0) * half);
            SPARKS.add(new Spark(at));
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      List<Player> knights = new ArrayList<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = ReinhardView.of(player);
         if (snapshot != null && !player.isInvisible() && ReinhardView.mode(snapshot) == ReinhardMode.KNIGHT) {
            knights.add(player);
         }
      }

      List<Vec3[]> manaLines = manaLines(client, level, event.getPartialTick());
      if (knights.isEmpty() && SPARKS.isEmpty() && manaLines.isEmpty()) {
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
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Player knight : knights) {
            int count = AuraRules.windMotes(ReinhardMode.KNIGHT, localFirstPerson(client, knight));
            double seconds = (knight.tickCount + partialTick) / 20.0;
            Vec3 feet = knight.getPosition(partialTick);
            int seed = knight.getUUID().hashCode();
            for (int i = 0; i < count; i++) {
               AuraRules.Mote mote = AuraRules.windMote(i, count, seconds, seed);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, feet.add(mote.x(), mote.y(), mote.z()), MOTE_SIZE, 240, 248, 255,
                  (int) (170.0F * mote.alpha()));
            }
         }

         double now = (client.player.tickCount + partialTick) / 20.0;
         for (int line = 0; line < manaLines.size(); line++) {
            Vec3 from = manaLines.get(line)[0];
            Vec3 to = manaLines.get(line)[1];
            Vec3 along = to.subtract(from);
            Vec3 side = along.lengthSqr() < 1.0E-6 ? Vec3.ZERO : new Vec3(-along.z, 0.0, along.x).normalize();
            for (int i = 0; i < AuraRules.MANA_MOTES; i++) {
               AuraRules.ManaMote mote = AuraRules.manaMote(i, AuraRules.MANA_MOTES, now, line * 7919);
               Vec3 at = from.add(along.scale(mote.t())).add(side.scale(mote.side())).add(0.0, mote.up(), 0.0);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, at, MANA_MOTE_SIZE, 110, 170, 255, (int) (120.0F * mote.alpha()));
            }
         }

         for (Spark spark : SPARKS) {
            float life = (spark.age + partialTick) / AuraRules.GOLD_SPARK_LIFE_TICKS;
            int alpha = (int) (220.0F * Math.max(0.0F, 1.0F - life));
            if (alpha > 0) {
               Vec3 at = spark.pos.add(0.0, (spark.age + partialTick) * SPARK_RISE_PER_TICK, 0.0);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, at, SPARK_SIZE, 255, 214, 90, alpha);
            }
         }

         tesselator.end();
      } finally {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   /**
    * Mana Absorption links of the local player (owner section MANA_LINKS, only the two ends get it):
    * chest of the drained player → chest of Reinhard. Local Reinhard: from each linked player to him;
    * local drained player: from him to each linked Reinhard.
    */
   private static List<Vec3[]> manaLines(Minecraft client, ClientLevel level, float partialTick) {
      List<Vec3[]> lines = new ArrayList<>();
      Player self = client.player;
      if (self == null) {
         return lines;
      }

      boolean selfIsReinhard = ReinhardView.of(self) != null;
      for (int id : ClientHeroData.section(OwnerSection.MANA_LINKS)) {
         Entity other = level.getEntity(id);
         if (!(other instanceof Player player) || player.isRemoved()) {
            continue;
         }

         boolean otherIsReinhard = ReinhardView.of(player) != null;
         if (selfIsReinhard == otherIsReinhard) {
            continue;
         }

         Player drained = selfIsReinhard ? player : self;
         Player reinhard = selfIsReinhard ? self : player;
         lines.add(new Vec3[]{chest(drained, partialTick), chest(reinhard, partialTick)});
      }

      return lines;
   }

   private static Vec3 chest(Player player, float partialTick) {
      return player.getPosition(partialTick).add(0.0, player.getBbHeight() * 0.6, 0.0);
   }

   private static boolean localFirstPerson(Minecraft client, Player player) {
      return player == client.player && client.options.getCameraType().isFirstPerson();
   }

   private static final class Spark {
      final Vec3 pos;
      int age;

      Spark(Vec3 pos) {
         this.pos = pos;
      }
   }
}
