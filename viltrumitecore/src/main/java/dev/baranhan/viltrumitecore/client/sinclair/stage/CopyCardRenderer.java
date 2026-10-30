package dev.baranhan.viltrumitecore.client.sinclair.stage;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.sinclair.SinclairView;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCard;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * The magic card of the copy cinematic, drawn on the GUI layer of both participants (design 2026-11-02 §2.3):
 * letterbox, a velvet card with a gold frame that unfolds edge-on to face-on in perspective, glows in the act
 * colour and sheds motes, shows the act name, what was copied from whom and a short description, then folds
 * and flies off (to Sinclair's palm / out of the volunteer). Sizes derive from the scaled GUI height, so the
 * card fits every GUI scale and window size.
 */
final class CopyCardRenderer {
   static final int VELVET_TOP = 0x2A1238;
   static final int VELVET_BOTTOM = 0x150A1F;
   static final int GOLD = 0xF2C14E;
   static final int GOLD_DARK = 0x9C6B1E;
   static final int IVORY = 0xF3EBDD;
   static final int LAVENDER = 0xC9B6FF;
   /** Reference card height the text sizes are authored for (GUI pixels). */
   static final float BASE_HEIGHT = 170.0F;

   private CopyCardRenderer() {
   }

   /** Pure layout (tested): card height for a GUI size — 62 % of the height, never wider than half the width. */
   static float cardHeight(int guiWidth, int guiHeight) {
      float h = Math.min(guiHeight * 0.62F, 240.0F);
      float w = h * 0.72F;
      if (w > guiWidth * 0.5F) {
         h = guiWidth * 0.5F / 0.72F;
      }

      return Math.max(60.0F, h);
   }

   static void render(GuiGraphics g, CopyCinematicClient.Live live, float partial) {
      Minecraft mc = Minecraft.getInstance();
      double t = live.tick();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float weight = (float) CopyCinematic.letterbox(t);
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate(0.0F, 0.0F, 400.0F);
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      try {
         // House lights down and the letterbox.
         g.fill(0, 0, w, h, (int) (weight * 0.34F * 255.0F) << 24 | 0x0A0612);
         int bar = (int) (h * 0.105F * weight);
         if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            g.fill(0, bar, w, bar + 1, (int) (weight * 120.0F) << 24 | GOLD_DARK);
            g.fill(0, h - bar - 1, w, h - bar, (int) (weight * 120.0F) << 24 | GOLD_DARK);
         }

         double scale = CopyCinematic.cardScale(t);
         if (scale <= 0.01) {
            return;
         }

         int color = SinclairView.color(live.snapshot());
         float cardH = cardHeight(w, h) * (float) scale;
         float cardW = cardH * 0.72F;
         double flight = CopyCinematic.cardFlight(t);
         float cx = w * 0.5F + (float) (live.asVolunteer() ? 0.0 : -w * 0.30 * flight);
         float cy = h * 0.48F + (float) (live.asVolunteer() ? -h * 0.70 * flight : h * 0.34 * flight);
         Card card = new Card(cx, cy, cardW, cardH, (float) Math.toRadians(CopyCinematic.cardTurn(t)));
         float glow = (float) CopyCinematic.glow(t);
         Matrix4f m = pose.last().pose();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         // Additive halo in the act colour.
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         quads(m, b -> {
            for (int i = 6; i >= 1; i--) {
               float grow = i * 0.045F;
               card.rect(b, m, -0.5F - grow * 0.72F, -0.5F - grow * 0.5F, 0.5F + grow * 0.72F, 0.5F + grow * 0.5F, color, color, (int) (glow * 46.0F / i));
            }
         });
         RenderSystem.defaultBlendFunc();
         quads(m, b -> {
            card.rect(b, m, -0.5F, -0.5F, 0.5F, 0.5F, GOLD, GOLD_DARK, 255);
            card.rect(b, m, -0.47F, -0.475F, 0.47F, 0.475F, VELVET_TOP, VELVET_BOTTOM, 255);
            card.frame(b, m, -0.43F, -0.445F, 0.43F, 0.445F, 0.006F, GOLD, 210);
            card.frame(b, m, -0.41F, -0.43F, 0.41F, 0.43F, 0.003F, color, 160);
            // Corner pips and the divider under the title.
            for (int sx = -1; sx <= 1; sx += 2) {
               for (int sy = -1; sy <= 1; sy += 2) {
                  float px = sx * 0.37F;
                  float py = sy * 0.39F;
                  card.rect(b, m, px - 0.025F, py - 0.018F, px + 0.025F, py + 0.018F, GOLD, GOLD_DARK, 230);
               }
            }

            card.rect(b, m, -0.3F, -0.035F, 0.3F, -0.03F, GOLD, GOLD, (int) (220 * CopyCinematic.textAlpha(t)));
         });
         text(g, mc.font, live, card, t, color);
         motes(m, card, t, color, glow);
      } finally {
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         pose.popPose();
      }
   }

   // --- the card in perspective -----------------------------------------------------------------
   /** A card turned by {@code turn} radians around its vertical axis, centred on (cx, cy); local units are fractions of its size. */
   record Card(float cx, float cy, float w, float h, float turn) {
      float focal() {
         return this.w * 2.4F;
      }

      float x(float u, float v) {
         float x = u * this.w;
         float z = x * Mth.sin(this.turn);
         return this.cx + x * Mth.cos(this.turn) * this.focal() / (this.focal() + z);
      }

      float y(float u, float v) {
         float z = u * this.w * Mth.sin(this.turn);
         return this.cy + v * this.h * this.focal() / (this.focal() + z);
      }

      /** Horizontal squash of flat content at the card centre (text). */
      float squash() {
         return Math.abs(Mth.cos(this.turn));
      }

      void rect(BufferBuilder b, Matrix4f m, float u0, float v0, float u1, float v1, int top, int bottom, int alpha) {
         if (alpha <= 2) {
            return;
         }

         vertex(b, m, u0, v1, bottom, alpha);
         vertex(b, m, u1, v1, bottom, alpha);
         vertex(b, m, u1, v0, top, alpha);
         vertex(b, m, u0, v0, top, alpha);
      }

      void frame(BufferBuilder b, Matrix4f m, float u0, float v0, float u1, float v1, float t, int rgb, int alpha) {
         float tu = t * this.h / this.w;
         this.rect(b, m, u0, v0, u1, v0 + t, rgb, rgb, alpha);
         this.rect(b, m, u0, v1 - t, u1, v1, rgb, rgb, alpha);
         this.rect(b, m, u0, v0, u0 + tu, v1, rgb, rgb, alpha);
         this.rect(b, m, u1 - tu, v0, u1, v1, rgb, rgb, alpha);
      }

      private void vertex(BufferBuilder b, Matrix4f m, float u, float v, int rgb, int alpha) {
         b.vertex(m, this.x(u, v), this.y(u, v), 0.0F).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).endVertex();
      }
   }

   interface Batch {
      void draw(BufferBuilder buffer);
   }

   static void quads(Matrix4f m, Batch batch) {
      // GuiGraphics.fill/drawString flush through RenderType.gui(), whose clear state turns blending off:
      // without this every translucent layer (halo, frames, motes) would draw opaque.
      RenderSystem.enableBlend();
      BufferBuilder buffer = Tesselator.getInstance().getBuilder();
      buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      batch.draw(buffer);
      Tesselator.getInstance().end();
   }

   // --- text --------------------------------------------------------------------------------------
   static void text(GuiGraphics g, Font font, CopyCinematicClient.Live live, Card card, double t, int color) {
      float alpha = (float) CopyCinematic.textAlpha(t);
      if (alpha <= 0.02F || card.squash() < 0.5F) {
         return;
      }

      HeroPublicSnapshot snapshot = live.snapshot();
      ActShape shape = SinclairView.shape(snapshot);
      if (shape == null) {
         shape = ActShape.FINALE;
      }

      SinclairRepertoire.Source source = SinclairView.flag(snapshot, SinclairFlags.FALLBACK) ? SinclairRepertoire.Source.FALLBACK
         : SinclairView.flag(snapshot, SinclairFlags.FROM_CONTRACT) ? SinclairRepertoire.Source.CONTRACT : SinclairRepertoire.Source.REPERTOIRE;
      Entity volunteer = live.volunteer();
      String heroKey = null;
      if (volunteer instanceof Player player) {
         HeroPublicSnapshot other = ((HeroPlayer) player).getHeroSnapshot();
         heroKey = other == null ? HeroId.HUMAN.key() : other.heroId().key();
      }

      CopyCard.Plan plan = CopyCard.plan(shape, heroKey, source);
      Component header = Component.translatable(live.asVolunteer() && !plan.fallback() ? "copy.viltrumitecore.sinclair.header.volunteer" : plan.header());
      Component title = Component.translatable(plan.title());
      Component origin = Component.translatable(plan.origin());
      Component who = volunteer == null ? Component.translatable("copy.viltrumitecore.sinclair.volunteer")
         : volunteer instanceof Player player ? Component.translatable(plan.heroName()).append(" · ").append(player.getName())
         : volunteer.getType().getDescription();
      Component desc = Component.translatable(plan.desc());
      float k = card.h() / BASE_HEIGHT;
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate(card.cx(), card.cy(), 0.0F);
      pose.scale(card.squash(), 1.0F, 1.0F);
      int a = Math.max(4, (int) (alpha * 255.0F)) << 24;
      float inner = card.w() * 0.78F;
      line(g, font, header, -card.h() * 0.385F, 0.62F * k, inner, a | GOLD);
      line(g, font, Component.literal(suit(shape)), -card.h() * 0.30F, 1.3F * k, inner, a | color);
      line(g, font, title, -card.h() * 0.17F, 1.55F * k, inner, a | IVORY);
      line(g, font, origin, card.h() * 0.0F, 0.68F * k, inner, a | GOLD);
      line(g, font, who, card.h() * 0.075F, 0.62F * k, inner, a | LAVENDER);
      float ds = 0.6F * k;
      List<FormattedCharSequence> rows = font.split(desc, Math.max(20, (int) (inner / ds)));
      float y = card.h() * 0.17F;
      for (int i = 0; i < Math.min(5, rows.size()); i++) {
         pose.pushPose();
         pose.translate(0.0F, y, 0.0F);
         pose.scale(ds, ds, 1.0F);
         g.drawString(font, rows.get(i), -font.width(rows.get(i)) / 2, 0, (int) (alpha * 225.0F) << 24 | IVORY, false);
         pose.popPose();
         y += 10.0F * ds;
      }

      line(g, font, Component.translatable("hero.viltrumitecore.sinclair"), card.h() * 0.40F, 0.5F * k, inner, (int) (alpha * 170.0F) << 24 | GOLD);
      pose.popPose();
      g.flush();
   }

   /** One centred line, shrunk to fit the inner width. */
   static void line(GuiGraphics g, Font font, Component text, float y, float scale, float maxWidth, int argb) {
      int width = font.width(text);
      float s = width * scale > maxWidth ? maxWidth / width : scale;
      PoseStack pose = g.pose();
      pose.pushPose();
      pose.translate(0.0F, y, 0.0F);
      pose.scale(s, s, 1.0F);
      g.drawString(font, text, -width / 2, -4, argb, false);
      pose.popPose();
   }

   static String suit(ActShape shape) {
      return switch (shape) {
         case BEAM -> "\u2666";
         case NOVA -> "\u2663";
         case CUT -> "\u2660";
         case BARRAGE -> "\u2665";
         case AEGIS -> "\u2660";
         case FINALE -> "\u2605";
      };
   }

   // --- motes and sparkles ------------------------------------------------------------------------
   static void motes(Matrix4f m, Card card, double t, int color, float glow) {
      if (glow <= 0.01F) {
         return;
      }

      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      quads(m, b -> {
         for (int i = 0; i < 36; i++) {
            double u = (t * 0.045 + i * 0.1373) % 1.0;
            double p = (i * 0.618) % 1.0;
            float px;
            float py;
            if (p < 0.25) {
               px = (float) (p * 4.0 - 0.5);
               py = -0.5F;
            } else if (p < 0.5) {
               px = 0.5F;
               py = (float) ((p - 0.25) * 4.0 - 0.5);
            } else if (p < 0.75) {
               px = (float) (0.5 - (p - 0.5) * 4.0);
               py = 0.5F;
            } else {
               px = -0.5F;
               py = (float) (0.5 - (p - 0.75) * 4.0);
            }

            float x = card.x(px, py) + (float) (Math.signum(px) * u * card.w() * 0.18);
            float y = card.y(px, py) - (float) (u * card.h() * 0.22);
            float size = (float) (1.0 + (i % 3) * 0.7) * card.h() / BASE_HEIGHT;
            int alpha = (int) (glow * 210.0F * (1.0 - u));
            int rgb = i % 3 == 0 ? GOLD : i % 3 == 1 ? color : IVORY;
            square(b, m, x, y, size, rgb, alpha);
         }

         // Four-point sparkles on the corners, twinkling.
         for (int c = 0; c < 4; c++) {
            float px = c % 2 == 0 ? -0.5F : 0.5F;
            float py = c < 2 ? -0.5F : 0.5F;
            float tw = (float) Math.max(0.0, Math.sin(t * 0.5 + c * 1.7));
            float s = (2.0F + 5.0F * tw) * card.h() / BASE_HEIGHT;
            float x = card.x(px, py);
            float y = card.y(px, py);
            int alpha = (int) (glow * 230.0F * tw);
            rectAbs(b, m, x - s, y - 0.6F, x + s, y + 0.6F, IVORY, alpha);
            rectAbs(b, m, x - 0.6F, y - s, x + 0.6F, y + s, IVORY, alpha);
         }
      });
      RenderSystem.defaultBlendFunc();
   }

   static void square(BufferBuilder b, Matrix4f m, float x, float y, float s, int rgb, int alpha) {
      rectAbs(b, m, x - s * 0.5F, y - s * 0.5F, x + s * 0.5F, y + s * 0.5F, rgb, alpha);
   }

   static void rectAbs(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, int alpha) {
      if (alpha <= 2) {
         return;
      }

      int r = rgb >> 16 & 255;
      int gg = rgb >> 8 & 255;
      int bb = rgb & 255;
      b.vertex(m, x0, y1, 0.0F).color(r, gg, bb, alpha).endVertex();
      b.vertex(m, x1, y1, 0.0F).color(r, gg, bb, alpha).endVertex();
      b.vertex(m, x1, y0, 0.0F).color(r, gg, bb, alpha).endVertex();
      b.vertex(m, x0, y0, 0.0F).color(r, gg, bb, alpha).endVertex();
   }
}
