package dev.baranhan.viltrumitecore.client.codex;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.hero.speech.PlayerBustPortrait;
import dev.baranhan.viltrumitecore.client.hero.speech.SubtitleRenderer;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * Portraits of the encyclopedia and the wish-card badges, in the subtitle look: a subtitle panel
 * ({@link SubtitleRenderer#panel}) with the hero's own subtitle portrait inside ({@link HeroPortraits}: the
 * custom model, else the bust of the hero skin), NPC skin busts, client-side entity previews (never added to
 * the world, never ticked) and Od Lagna's halo. Anything that fails to draw falls back to the halo once and
 * is logged.
 */
public final class CodexPortraits {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ResourceLocation HALO = new ResourceLocation("viltrumitecore", "textures/gui/od_lagna/halo.png");
   private static final Map<String, Entity> PREVIEWS = new HashMap<>();
   private static final Set<String> FAILED = new HashSet<>();
   @Nullable
   private static ClientLevel previewLevel;

   private CodexPortraits() {
   }

   /** Panel and portrait in the box (x, y, w, h) at opacity {@code alpha}. */
   public static void framed(GuiGraphics g, CodexEntry.Visual visual, int accent, int x, int y, int w, int h, float alpha) {
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      SubtitleRenderer.panel(g, w, h, alpha);
      g.pose().popPose();
      draw(g, visual, accent, x + 2, y + 2, w - 4, h - 4, alpha);
   }

   /** The hero's subtitle portrait in a small subtitle panel (wish-card badge). */
   public static void heroBadge(GuiGraphics g, String heroKey, int x, int y, int size, float alpha) {
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      SubtitleRenderer.panel(g, size, size, alpha);
      g.pose().popPose();
      hero(g, heroKey, new HeroPortraits.Frame(x + 2, y + 2, size - 4, size - 4, alpha, seconds(), localId(), null));
   }

   public static void draw(GuiGraphics g, CodexEntry.Visual visual, int accent, int x, int y, int w, int h, float alpha) {
      if (w <= 2 || h <= 2 || alpha <= 0.01F) {
         return;
      }
      HeroPortraits.Frame frame = new HeroPortraits.Frame(x, y, w, h, alpha, seconds(), localId(), null);
      String failKey = visual.type() + ":" + visual.refs() + ":" + visual.variant();
      if (FAILED.contains(failKey)) {
         halo(g, accent, x, y, w, h, alpha);
         return;
      }
      try {
         switch (visual.type()) {
            case HERO -> hero(g, visual.refs().get(0), frame);
            case SKINS -> {
               int n = visual.refs().size();
               int i = n <= 1 ? 0 : (int)(seconds() / 3.0) % n;
               boolean slim = i < visual.slim().size() && visual.slim().get(i);
               PlayerBustPortrait.INSTANCE.renderSkin(g, frame, new ResourceLocation(visual.refs().get(i)), slim);
            }
            case ENTITY -> {
               if (!entity(g, visual.refs().get(0), visual.variant(), x, y, w, h, alpha)) {
                  halo(g, accent, x, y, w, h, alpha);
               }
            }
            case HALO -> halo(g, 0xFFD98A, x, y, w, h, alpha);
         }
      } catch (RuntimeException | LinkageError e) {
         FAILED.add(failKey);
         LOGGER.warn("[codex] portrait {} failed, showing the halo instead: {}", failKey, e.toString());
         RenderSystem.colorMask(true, true, true, true);
         RenderSystem.disableScissor();
         Lighting.setupFor3DItems();
         halo(g, accent, x, y, w, h, alpha);
      }
   }

   /** The hero's subtitle portrait: its registered one, else the bust of its default hero skin, else the own skin. */
   static void hero(GuiGraphics g, String heroKey, HeroPortraits.Frame frame) {
      HeroId id = HeroId.fromKey(heroKey);
      HeroPortraits.Portrait custom = HeroPortraits.registered(id);
      if (custom != null) {
         custom.render(g, frame);
         return;
      }
      Optional<HeroSkins.Provider> provider = id == null ? Optional.empty() : HeroSkins.provider(id);
      if (provider.isPresent()) {
         PlayerBustPortrait.INSTANCE.renderSkin(g, frame, provider.get().defaultSkin(), "slim".equals(provider.get().defaultModelName()));
         return;
      }
      PlayerBustPortrait.INSTANCE.render(g, frame);
   }

   static void halo(GuiGraphics g, int tint, int x, int y, int w, int h, float alpha) {
      float t = (float)seconds();
      float pulse = 0.85F + 0.15F * Mth.sin(t * 1.6F);
      int size = Math.round(Math.min(w, h) * (0.92F + 0.06F * pulse));
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 0.85F * alpha * pulse);
      g.blit(HALO, x + (w - size) / 2, y + (h - size) / 2, size, size, 0, 0, 128, 128, 128, 128);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   /** A client-only preview of the entity type, fitted into the box and slowly turning; false when there is none. */
   static boolean entity(GuiGraphics g, String typeId, @Nullable String variant, int x, int y, int w, int h, float alpha) {
      Minecraft mc = Minecraft.getInstance();
      Entity entity = preview(mc, typeId, variant);
      if (entity == null) {
         return false;
      }
      float bbH = Math.max(0.3F, entity.getBbHeight());
      float bbW = Math.max(0.3F, entity.getBbWidth());
      float scale = Math.min(h * 0.86F / bbH, w * 0.8F / bbW);
      double t = seconds();
      float yaw = 200.0F + 18.0F * (float)Math.sin(t * 2.0 * Math.PI / 9.0);
      EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
      PoseStack pose = g.pose();
      g.flush();
      g.enableScissor(x, y, x + w, y + h);
      RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
      RenderSystem.enableDepthTest();
      pose.pushPose();
      pose.translate(x + w / 2.0F, y + h * 0.5F + bbH * scale * 0.5F, 150.0F);
      pose.scale(scale, scale, -scale);
      pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
      pose.mulPose(Axis.XP.rotationDegrees(-8.0F));
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
      Lighting.setupForEntityInInventory();
      dispatcher.setRenderShadow(false);
      try {
         dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, g.bufferSource(), LightTexture.FULL_BRIGHT);
         g.flush();
      } finally {
         dispatcher.setRenderShadow(true);
         pose.popPose();
         Lighting.setupFor3DItems();
         RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
         g.disableScissor();
      }
      if (alpha < 0.99F) {
         // entity renderers draw opaque: fade them with a veil in the panel colour
         g.fill(x, y, x + w, y + h, Math.round((1 - alpha) * 255) << 24 | 0x1E2024);
      }
      return true;
   }

   @Nullable
   private static Entity preview(Minecraft mc, String typeId, @Nullable String variant) {
      if (mc.level == null) {
         return null;
      }
      if (mc.level != previewLevel) {
         PREVIEWS.clear();
         previewLevel = mc.level;
      }
      String key = typeId + "#" + variant;
      if (PREVIEWS.containsKey(key)) {
         return PREVIEWS.get(key);
      }
      Entity entity = null;
      EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(typeId));
      if (type != null) {
         entity = type.create(mc.level);
      }
      if (entity != null) {
         entity.setPos(0, 0, 0);
         entity.setYRot(0);
         entity.setXRot(0);
         if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = 0;
            living.yHeadRot = living.yHeadRotO = 0;
         }
         if (variant != null && entity instanceof AvengerEntity avenger) {
            for (AvengerKind kind : AvengerKind.values()) {
               if (kind.key().equals(variant)) {
                  avenger.previewKind(kind);
               }
            }
         }
      }
      PREVIEWS.put(key, entity);
      return entity;
   }

   private static UUID localId() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null ? mc.player.getUUID() : new UUID(0L, 0L);
   }

   static double seconds() {
      return Util.getMillis() / 1000.0;
   }
}
