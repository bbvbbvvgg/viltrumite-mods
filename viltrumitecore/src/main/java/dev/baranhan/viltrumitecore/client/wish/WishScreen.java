package dev.baranhan.viltrumitecore.client.wish;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes;
import dev.baranhan.viltrumitecore.hero.wish.Wish;
import dev.baranhan.viltrumitecore.hero.wish.WishSounds;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.WishChoiceC2SPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * «чего ты желаешь?» (spec: Od Lagna's Wish GUI). A dark veil with Od Lagna's halo and rising motes, the
 * title, a smooth-scrolled column of wish cards (hover glow, selection, «исполнено» for the hero the player
 * already is), the selected wish's echo line and «исполнить желание» / «отмена». Esc, «отмена» and a sent
 * wish fade the screen out. The client only sends the wish id; the server decides (WishRitual.request).
 *
 * <p>{@code first}: the world's first hero choice (replaces the old race screen), opened by the server; the
 * player needs no item then and the current hero is never marked as fulfilled.
 */
public class WishScreen extends Screen {
   static final ResourceLocation HALO = new ResourceLocation("viltrumitecore", "textures/gui/od_lagna/halo.png");
   private static final int FADE_IN = 7;
   private static final int FADE_OUT = 5;
   private static final int MAX_MOTES = 48;

   private final boolean first;
   private final List<Wish> wishes;
   private final boolean[] fulfilled;
   private final float[] hover;
   private final List<float[]> motes = new ArrayList<>();
   private final Random random = new Random();
   private WishLayout layout;
   private int selected = -1;
   private float scroll;
   private float scrollO;
   private float scrollTarget;
   private float echo;
   private float echoO;
   private int age;
   private int closing = -1;
   private boolean sent;

   public WishScreen(boolean first) {
      super(Component.translatable("gui.viltrumitecore.wish.title"));
      this.first = first;
      this.wishes = OdLagnaWishes.all();
      this.fulfilled = fulfilledMarks(this.wishes, first ? null : currentHeroKey());
      this.hover = new float[this.wishes.size()];
   }

   /**
    * «исполнено» marks: the wish whose hero the player already is. Only for heroes a single wish grants;
    * wishes sharing a hero (the two Iron Man wishes) differ by more than the hero, so the server answers them.
    */
   static boolean[] fulfilledMarks(List<Wish> wishes, String heroKey) {
      boolean[] out = new boolean[wishes.size()];
      if (heroKey == null) {
         return out;
      }
      Map<String, Integer> perHero = new HashMap<>();
      for (Wish wish : wishes) {
         perHero.merge(wish.heroKey(), 1, Integer::sum);
      }
      for (int i = 0; i < wishes.size(); i++) {
         Wish wish = wishes.get(i);
         out[i] = wish.heroKey().equals(heroKey) && perHero.get(wish.heroKey()) == 1;
      }
      return out;
   }

   private static String currentHeroKey() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player instanceof HeroPlayer hero && hero.getHeroId() != null ? hero.getHeroId().key() : null;
   }

   public boolean firstChoice() {
      return this.first;
   }

   @Override
   protected void init() {
      this.layout = new WishLayout(this.width, this.height, this.wishes.size());
      this.scrollTarget = this.layout.clampScroll(this.scrollTarget);
      this.scroll = this.scrollO = this.layout.clampScroll(this.scroll);
      if (this.age == 0) {
         play(WishSounds.OPEN, 1.0F, 0.8F);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void tick() {
      this.age++;
      this.scrollO = this.scroll;
      this.scroll += (this.scrollTarget - this.scroll) * 0.45F;
      if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
         this.scroll = this.scrollTarget;
      }
      this.echoO = this.echo;
      this.echo += ((this.selected >= 0 ? 1 : 0) - this.echo) * 0.35F;
      this.tickMotes();
      if (this.closing >= 0 && ++this.closing >= FADE_OUT) {
         this.closeNow();
      }
   }

   private void tickMotes() {
      for (int i = this.motes.size() - 1; i >= 0; i--) {
         float[] m = this.motes.get(i);
         m[0] += m[2];
         m[1] += m[3];
         m[3] -= 0.004F;
         if (--m[4] <= 0) {
            this.motes.remove(i);
         }
      }
      if (this.motes.size() < MAX_MOTES && this.closing < 0) {
         for (int n = 0; n < 2; n++) {
            float x = this.width * (0.15F + 0.7F * this.random.nextFloat());
            float y = this.height * (0.55F + 0.5F * this.random.nextFloat());
            this.motes.add(new float[]{x, y, (this.random.nextFloat() - 0.5F) * 0.3F, -0.25F - this.random.nextFloat() * 0.45F,
               40 + this.random.nextInt(50), 40 + this.random.nextInt(50), 0.6F + this.random.nextFloat() * 0.8F});
         }
      }
   }

   /** 0..1 screen opacity (fade in, fade out). */
   float fade(float partial) {
      float in = Math.min(1, (this.age + partial) / FADE_IN);
      if (this.closing < 0) {
         return smooth(in);
      }
      return smooth(Math.min(in, 1 - Math.min(1, (this.closing + partial) / FADE_OUT)));
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      WishLayout l = this.layout;
      float a = this.fade(partial);
      float time = this.age + partial;
      float scroll = Mth.lerp(partial, this.scrollO, this.scroll);
      int hovered = this.closing < 0 ? l.rowAt(mouseX, mouseY, scroll) : -1;
      // frame-rate independent hover glow (half-way in ~0.1 s)
      float k = 1 - (float)Math.pow(0.5, Math.max(0, Minecraft.getInstance().getDeltaFrameTime()) * 2.0);
      for (int i = 0; i < this.hover.length; i++) {
         float target = i == hovered ? 1 : 0;
         this.hover[i] += (target - this.hover[i]) * k;
      }

      // veil
      g.fillGradient(0, 0, this.width, this.height, argb(0xB0 * a, 0x080B16), argb(0xE0 * a, 0x030409));
      // halo behind the title
      RenderSystem.enableBlend();
      float pulse = 0.82F + 0.18F * Mth.sin(time * 0.08F);
      int haloSize = Math.round(Math.max(l.cardW + 60, 180) * (0.96F + 0.04F * pulse));
      RenderSystem.setShaderColor(1.0F, 0.85F, 0.5F, 0.55F * a * pulse);
      g.blit(HALO, (this.width - haloSize) / 2, l.titleY + 6 - haloSize / 2, haloSize, haloSize, 0, 0, 128, 128, 128, 128);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      // motes
      for (float[] m : this.motes) {
         float life = m[4] / m[5];
         float ma = Math.min(1, life * 2) * Math.min(1, (1 - life) * 6) * a;
         PixelVfx.guiPixel(g, m[0], m[1], m[6], 255, 214, 140, (int)(200 * ma));
      }
      // title with a soft gold glow
      int titleAlpha = (int)(255 * a);
      if (titleAlpha > 4) {
         g.pose().pushPose();
         g.pose().translate(this.width / 2.0F, l.titleY, 0);
         g.pose().scale(l.titleScale, l.titleScale, 1);
         int tw = this.font.width(this.title);
         int glow = argb(70 * a * pulse, WishStyle.GOLD);
         for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            g.drawString(this.font, this.title, -tw / 2 + o[0], o[1], glow, false);
         }
         g.drawString(this.font, this.title, -tw / 2, 0, argb(titleAlpha, 0xFFF4DC), false);
         g.pose().popPose();
      }

      // the wish column (scissored)
      g.enableScissor(0, l.listTop, this.width, l.listBottom);
      Wish tooltipWish = null;
      for (int i = 0; i < this.wishes.size(); i++) {
         float y = l.rowY(i, scroll);
         if (y + WishLayout.ROW < l.listTop || y > l.listBottom) {
            continue;
         }
         if (this.drawCard(g, i, Math.round(y), a, time) && i == hovered) {
            tooltipWish = this.wishes.get(i);
         }
      }
      g.disableScissor();
      // scroll bar
      if (l.maxScroll() > 0) {
         int x = l.cardX + l.cardW + 4;
         g.fill(x, l.listTop, x + 2, l.listBottom, argb(50 * a, 0xFFFFFF));
         float view = l.listHeight() / (float)l.contentHeight();
         int thumb = Math.max(12, Math.round(l.listHeight() * view));
         int ty = l.listTop + Math.round((l.listHeight() - thumb) * (scroll / l.maxScroll()));
         g.fill(x, ty, x + 2, ty + thumb, argb(200 * a, WishStyle.GOLD));
         // soft edges hint that the column continues
         if (scroll > 0.5F) {
            g.fillGradient(l.cardX, l.listTop, l.cardX + l.cardW, l.listTop + 8, argb(150 * a, 0x080B16), 0);
         }
         if (scroll < l.maxScroll() - 0.5F) {
            g.fillGradient(l.cardX, l.listBottom - 8, l.cardX + l.cardW, l.listBottom, 0, argb(170 * a, 0x05060C));
         }
      }

      // echo of the selected wish
      float e = Mth.lerp(partial, this.echoO, this.echo);
      Component echoText = this.selected >= 0 ? Component.translatable(this.wishes.get(this.selected).echoKey())
         : Component.translatable("gui.viltrumitecore.wish.hint");
      float echoAlpha = this.selected >= 0 ? e : 0.55F * (1 - e);
      if (echoAlpha * a * 255 > 5) {
         String line = this.font.plainSubstrByWidth(echoText.getString(), this.width - 2 * WishLayout.MARGIN);
         g.drawCenteredString(this.font, Component.literal(line).withStyle(s -> s.withItalic(this.selected >= 0)), this.width / 2, l.echoY,
            argb(255 * echoAlpha * a, this.selected >= 0 ? 0xE9D7A8 : 0x9A9AAE));
      }

      // buttons
      boolean canConfirm = this.canConfirm();
      this.drawButton(g, l.confirmX, l.buttonY, l.buttonW, Component.translatable("gui.viltrumitecore.wish.confirm"), canConfirm,
         canConfirm && l.overConfirm(mouseX, mouseY), true, a, time);
      this.drawButton(g, l.cancelX, l.buttonY, l.buttonW, Component.translatable("gui.viltrumitecore.wish.cancel"), this.closing < 0,
         this.closing < 0 && l.overCancel(mouseX, mouseY), false, a, time);

      if (tooltipWish != null && a > 0.9F) {
         g.renderTooltip(this.font, this.font.split(Component.translatable(tooltipWish.textKey()), Math.max(120, l.cardW)), mouseX, mouseY);
      }
   }

   /** One wish card; returns true when its text was cut (the hover tooltip then shows it whole). */
   private boolean drawCard(GuiGraphics g, int i, int y, float a, float time) {
      WishLayout l = this.layout;
      Wish wish = this.wishes.get(i);
      WishStyle style = WishStyle.of(wish.style());
      float h = this.hover[i];
      boolean sel = i == this.selected;
      boolean done = this.fulfilled[i];
      float s = sel ? 1 : 0;
      int x = l.cardX;
      int w = l.cardW;
      int bg = mix(0x121729, 0x3A2E14, Math.max(0.35F * h, 0.75F * s));
      g.fill(x, y, x + w, y + WishLayout.ROW, argb((0x88 + 0x30 * h + 0x30 * s) * a * (done ? 0.6F : 1), bg));
      // accent bar in the hero's colour, wider when hovered or selected
      int bar = 2 + Math.round(2 * Math.max(h, s));
      g.fill(x, y, x + bar, y + WishLayout.ROW, argb((done ? 110 : 230) * a, style.primary));
      if (sel || h > 0.02F) {
         float glow = sel ? 0.75F + 0.25F * Mth.sin(time * 0.2F) : h * 0.6F;
         int border = argb(220 * glow * a, WishStyle.GOLD);
         g.fill(x, y, x + w, y + 1, border);
         g.fill(x, y + WishLayout.ROW - 1, x + w, y + WishLayout.ROW, border);
         g.fill(x + w - 1, y, x + w, y + WishLayout.ROW, border);
      }
      int textX = x + 10 + Math.round(3 * Math.max(h, s));
      int right = x + w - 8;
      Component mark = done ? Component.translatable("gui.viltrumitecore.wish.fulfilled") : null;
      if (mark != null) {
         int mw = this.font.width(mark);
         g.drawString(this.font, mark, right - mw, y + 8, argb(200 * a, 0x8FD18F), false);
         right -= mw + 6;
      }
      String full = Component.translatable(wish.textKey()).getString();
      String text = full;
      boolean cut = false;
      if (this.font.width(text) > right - textX) {
         text = this.font.plainSubstrByWidth(text, right - textX - this.font.width("…")) + "…";
         cut = true;
      }
      int color = done ? 0x8A8A98 : sel ? 0xFFE2A0 : mix(0xDAD6CC, 0xFFFFFF, h);
      if (a * 255 > 5) {
         g.drawString(this.font, text, textX, y + 8, argb(255 * a, color), !done);
      }
      return cut;
   }

   private void drawButton(GuiGraphics g, int x, int y, int w, Component label, boolean active, boolean hot, boolean primary, float a, float time) {
      int fill = !active ? 0x1A1A22 : primary ? mix(0x3A2A0E, 0x6A4A12, hot ? 1 : 0.35F + 0.25F * Mth.sin(time * 0.15F)) : mix(0x181C2C, 0x2A3048, hot ? 1 : 0);
      g.fill(x, y, x + w, y + WishLayout.BUTTON_H, argb((active ? 0xD0 : 0x80) * a, fill));
      int edge = argb((active ? (hot ? 255 : 170) : 60) * a, primary ? WishStyle.GOLD : 0x8C93B8);
      g.fill(x, y, x + w, y + 1, edge);
      g.fill(x, y + WishLayout.BUTTON_H - 1, x + w, y + WishLayout.BUTTON_H, edge);
      g.fill(x, y, x + 1, y + WishLayout.BUTTON_H, edge);
      g.fill(x + w - 1, y, x + w, y + WishLayout.BUTTON_H, edge);
      String text = this.font.plainSubstrByWidth(label.getString(), w - 6);
      if (a * 255 > 5) {
         g.drawCenteredString(this.font, text, x + w / 2, y + 6, argb(255 * a, !active ? 0x6A6A74 : primary ? 0xFFE6A8 : 0xD8DCEC));
      }
   }

   boolean canConfirm() {
      return this.closing < 0 && !this.sent && this.selected >= 0 && !this.fulfilled[this.selected];
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.closing >= 0 || button != 0) {
         return super.mouseClicked(mouseX, mouseY, button);
      }
      WishLayout l = this.layout;
      int row = l.rowAt(mouseX, mouseY, this.scroll);
      if (row >= 0) {
         this.select(row);
         return true;
      }
      if (l.overConfirm(mouseX, mouseY)) {
         this.confirm();
         return true;
      }
      if (l.overCancel(mouseX, mouseY)) {
         this.onClose();
         return true;
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      this.scrollTarget = this.layout.clampScroll(this.scrollTarget - (float)delta * WishLayout.PITCH * 0.75F);
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (this.closing < 0) {
         if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            this.select(this.selected < 0 ? 0 : Math.min(this.wishes.size() - 1, this.selected + 1));
            return true;
         }
         if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            this.select(this.selected < 0 ? 0 : Math.max(0, this.selected - 1));
            return true;
         }
         if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            this.confirm();
            return true;
         }
      }
      return super.keyPressed(key, scanCode, modifiers);
   }

   private void select(int index) {
      if (index < 0 || index >= this.wishes.size() || index == this.selected) {
         return;
      }
      this.selected = index;
      this.scrollTarget = this.layout.scrollToShow(index, this.scrollTarget);
      play(WishSounds.SELECT, 0.9F + 0.04F * index, 0.7F);
   }

   private void confirm() {
      if (!this.canConfirm()) {
         return;
      }
      this.sent = true;
      CoreMessages.sendToServer(new WishChoiceC2SPacket(this.wishes.get(this.selected).id()));
      play(WishSounds.CONFIRM, 1.0F, 0.9F);
      this.closing = 0;
   }

   /** Esc and «отмена»: fade out instead of vanishing. */
   @Override
   public void onClose() {
      if (this.closing < 0) {
         this.closing = 0;
      }
   }

   /** Closes at once (the wish scene has started, or the fade ended). */
   public void closeNow() {
      if (this.minecraft != null && this.minecraft.screen == this) {
         this.minecraft.setScreen(null);
      }
   }

   @Override
   public void renderBackground(GuiGraphics g) {
   }

   private static void play(SoundEvent sound, float pitch, float volume) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
   }

   static float smooth(float k) {
      k = Mth.clamp(k, 0, 1);
      return k * k * (3 - 2 * k);
   }

   static int argb(float alpha, int rgb) {
      int al = Mth.clamp(Math.round(alpha), 0, 255);
      return al << 24 | rgb & 0xFFFFFF;
   }

   static int mix(int from, int to, float k) {
      k = Mth.clamp(k, 0, 1);
      int r = Math.round(Mth.lerp(k, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int g = Math.round(Mth.lerp(k, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
      return r << 16 | g << 8 | b;
   }
}
