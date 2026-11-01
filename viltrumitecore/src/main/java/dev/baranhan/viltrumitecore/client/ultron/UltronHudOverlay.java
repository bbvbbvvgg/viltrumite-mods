package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronAdaptation;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronDoctrine;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronPhase;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronScenario;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The uprising's HUD for every player in its range (the phase itself and Ultron Prime are on the boss bars):
 * a compact panel top left with the scenario, Ultron's doctrine, one pip per node (state, power, construction,
 * fortification, reboot), the node and machine counts, every running adaptation protocol with its level and
 * time left, a warning while no sensor is online (adaptation frozen) and the outcome at the end; the player's
 * own hack progress under the crosshair; and the glitch interference pulses of Ultron's network. Nothing is
 * drawn with F1 or while a cinematic owns the screen; the panel steps aside for the debug screen.
 */
public final class UltronHudOverlay {
   static final int PANEL_W = 150;
   private static final int RED = 0xFF3B2E;
   private static float glitchPower;
   private static int glitchLife = 1;
   private static long glitchAt = Long.MIN_VALUE / 2;

   private UltronHudOverlay() {
   }

   /** A screen interference pulse ({@code strength} 0..1 over {@code ticks}); a weaker pulse never cuts a stronger one short. */
   public static void glitch(float strength, int ticks) {
      long now = now();
      if (strength >= glitchLevel(now, 0.0F)) {
         glitchPower = Mth.clamp(strength, 0.0F, 1.0F);
         glitchLife = Math.max(1, ticks);
         glitchAt = now;
      }
   }

   /** Pure: the interference left {@code age} ticks into a pulse. */
   public static float glitchLevel(float power, int life, float age) {
      if (age < 0.0F || age >= life) {
         return 0.0F;
      }
      float t = age / life;
      return power * (float)Math.pow(1.0F - t, 1.5);
   }

   private static float glitchLevel(long now, float partial) {
      return glitchLevel(glitchPower, glitchLife, now - glitchAt + partial);
   }

   private static long now() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   /** Pure: m:ss. */
   public static String clock(int seconds) {
      int s = Math.max(0, seconds);
      return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
   }

   /** Pure: I..V for protocol levels. */
   public static String roman(int level) {
      return switch (level) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> level <= 0 ? "" : Integer.toString(level);
      };
   }

   /** Pure: the network keeps learning while a sensor node is online and powered. */
   public static boolean sensing(List<UltronStateS2CPacket.Node> nodes) {
      for (UltronStateS2CPacket.Node n : nodes) {
         if (UltronNodeKind.byId(n.kind()) == UltronNodeKind.SENSOR && UltronNodeState.byId(n.state()) == UltronNodeState.ONLINE && n.powered()) {
            return true;
         }
      }
      return false;
   }

   static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui || CinematicHud.hiding() || mc.level == null) {
         return;
      }
      long now = now();
      float k = glitchLevel(now, partial);
      if (k > 0.01F) {
         interference(g, k, now, width, height);
      }
      UltronStateS2CPacket s = UltronClientState.get();
      if (s == null) {
         return;
      }
      float time = now + partial;
      if (!mc.options.renderDebug) {
         panel(g, mc.font, s, time);
      }
      if (s.hack() > 0.0F && s.hackNode() >= 0) {
         hackBar(g, mc.font, s, width, height, time);
      }
   }

   // ------------------------------------------------------------------ panel

   private static void panel(GuiGraphics g, Font font, UltronStateS2CPacket s, float time) {
      UltronPhase phase = UltronPhase.values()[Mth.clamp(s.phase(), 0, UltronPhase.values().length - 1)];
      List<Component> lines = new ArrayList<>();
      List<Integer> colours = new ArrayList<>();
      int online = 0, disabled = 0, destroyed = 0;
      for (UltronStateS2CPacket.Node n : s.nodes()) {
         switch (UltronNodeState.byId(n.state())) {
            case ONLINE -> online++;
            case DISABLED -> disabled++;
            case DESTROYED -> destroyed++;
            default -> {
            }
         }
      }
      lines.add(Component.translatable("viltrumitecore.ultron.hud.doctrine", Component.translatable("viltrumitecore.ultron.doctrine."
         + UltronDoctrine.byId(s.doctrine()).key())));
      colours.add(0xE8D6D2);
      lines.add(Component.translatable("viltrumitecore.ultron.hud.nodes", online, disabled, destroyed));
      colours.add(0xD8C8C4);
      lines.add(Component.translatable("viltrumitecore.ultron.hud.forces", s.units(), s.captured().length));
      colours.add(0xD8C8C4);
      UltronAdaptation.Protocol[] protocols = UltronAdaptation.Protocol.values();
      for (int i = 0; i < protocols.length && i < s.protocolSeconds().length; i++) {
         int secs = s.protocolSeconds()[i];
         if (secs > 0) {
            int level = i < s.protocolLevel().length ? s.protocolLevel()[i] : 1;
            lines.add(Component.translatable("viltrumitecore.ultron.hud.protocol", Component.translatable("viltrumitecore.ultron.protocol."
               + protocols[i].key()), roman(level), clock(secs)));
            colours.add(secs <= 10 && ((int)(time / 5.0F) & 1) == 0 ? 0xFFFFFF : 0xFF8A70);
         }
      }
      if ((phase == UltronPhase.MACHINE_WAR || phase == UltronPhase.ADAPTATION) && !s.nodes().isEmpty() && !sensing(s.nodes())) {
         lines.add(Component.translatable("viltrumitecore.ultron.hud.blind"));
         colours.add(0x9AD8FF);
      }
      if (s.outcome() != UltronStateS2CPacket.OUTCOME_NONE) {
         lines.add(Component.translatable(s.outcome() == UltronStateS2CPacket.OUTCOME_VICTORY ? "viltrumitecore.ultron.hud.victory"
            : "viltrumitecore.ultron.hud.defeat"));
         colours.add(s.outcome() == UltronStateS2CPacket.OUTCOME_VICTORY ? 0x9AD8FF : 0xFF5040);
      }
      float scale = 0.75F;
      int lineH = 8;
      int pipRows = (s.nodes().size() + 15) / 16;
      int x = 6;
      int y = 6;
      int w = PANEL_W;
      int h = 4 + 12 + 8 + pipRows * 10 + lines.size() * lineH + 4;
      boolean finale = phase == UltronPhase.FINALE;
      float pulse = finale ? 0.5F + 0.5F * Mth.sin(time * 0.25F) : 0.0F;
      frame(g, x, y, w, h, pulse);
      // title with a little digital tear now and then
      Component title = Component.translatable("viltrumitecore.ultron.hud.title");
      int tear = ((int)(time / 3.0F) * 7919 & 63) == 0 ? 1 : 0;
      g.drawString(font, title, x + 5 + tear, y + 4, 0xFF000000 | mix(RED, 0xFFFFFF, pulse * 0.3F), true);
      Component variant = Component.translatable("viltrumitecore.ultron.variant." + UltronScenario.Variant.values()[Mth.clamp(s.variant(), 0,
         UltronScenario.Variant.values().length - 1)].key());
      g.pose().pushPose();
      g.pose().translate(x + w - 5.0F, y + 5.5F, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, variant, -font.width(variant), 0, 0xFFB09A96, true);
      g.pose().popPose();
      // phase strip under the title (the bar itself is the boss bar; this is a reminder of where we are)
      int py = y + 15;
      UltronPhase[] all = UltronPhase.values();
      int segs = all.length - 1;
      int segW = (w - 10 - (segs - 1) * 2) / segs;
      for (int i = 0; i < segs; i++) {
         int sx = x + 5 + i * (segW + 2);
         int colour = i < phase.ordinal() ? 0xFF7A2018 : i == phase.ordinal() ? 0xFF2A1210 : 0xFF1A1414;
         g.fill(sx, py, sx + segW, py + 3, colour);
         if (i == phase.ordinal()) {
            int fill = Math.round(segW * Mth.clamp(s.progress(), 0.0F, 1.0F));
            g.fill(sx, py, sx + fill, py + 3, 0xFF000000 | RED);
         }
      }
      // node pips
      int ny = py + 7;
      List<UltronStateS2CPacket.Node> nodes = s.nodes();
      for (int i = 0; i < nodes.size(); i++) {
         int col = i % 16;
         int row = i / 16;
         pip(g, nodes.get(i), x + 6 + col * 9, ny + row * 10, time);
      }
      int ty = ny + pipRows * 10 + 2;
      for (int i = 0; i < lines.size(); i++) {
         g.pose().pushPose();
         g.pose().translate(x + 5.0F, ty + i * lineH, 0.0F);
         g.pose().scale(scale, scale, 1.0F);
         g.drawString(font, lines.get(i), 0, 0, 0xFF000000 | colours.get(i), true);
         g.pose().popPose();
      }
   }

   private static void frame(GuiGraphics g, int x, int y, int w, int h, float pulse) {
      int edge = mix(0x5A1612, 0xB02A20, pulse);
      g.fill(x + 1, y, x + w - 1, y + h, 0xB80A0606);
      g.fill(x, y + 1, x + w, y + h - 1, 0xB80A0606);
      g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xC81E0E0C, 0xC80E0808);
      g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF000000 | mix(0xC0302A, 0xFF5A48, pulse));
      g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFF000000 | edge);
      g.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFF000000 | edge);
      g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFF000000 | edge);
   }

   /** One node: colour by state, a lit core for the core node, the build share as a fill, a white rim when fortified. */
   private static void pip(GuiGraphics g, UltronStateS2CPacket.Node n, int px, int py, float time) {
      UltronNodeState state = UltronNodeState.byId(n.state());
      boolean core = UltronNodeKind.byId(n.kind()) == UltronNodeKind.CORE;
      int size = 7;
      if (!n.discovered()) {
         g.fill(px, py, px + size, py + size, 0xFF2A2626);
         g.fill(px + 1, py + 1, px + size - 1, py + size - 1, 0xFF151212);
         g.fill(px + 3, py + 2, px + 4, py + 4, 0xFF5A5252);
         g.fill(px + 3, py + 5, px + 4, py + 6, 0xFF5A5252);
         return;
      }
      int rim = n.fortified() ? 0xFFF4E6E0 : 0xFF3A1E1A;
      g.fill(px, py, px + size, py + size, rim);
      int inner;
      switch (state) {
         case PLANNED -> inner = 0xFF201010;
         case BUILDING -> inner = 0xFF2A1408;
         case ONLINE -> inner = n.powered() ? 0xFF000000 | RED : 0xFF6A1C16;
         case DISABLED -> inner = (n.flags() & UltronStateS2CPacket.FLAG_REBOOTING) != 0 && ((int)(time / 4.0F) & 1) == 0 ? 0xFF4A6A80 : 0xFF8ACCF0;
         default -> inner = 0xFF2A2828;
      }
      g.fill(px + 1, py + 1, px + size - 1, py + size - 1, inner);
      if (state == UltronNodeState.BUILDING) {
         int fill = Math.round((size - 2) * Mth.clamp(n.build(), 0.0F, 1.0F));
         g.fill(px + 1, py + size - 1 - fill, px + size - 1, py + size - 1, 0xFFFF8A3A);
      } else if (state == UltronNodeState.PLANNED) {
         g.fill(px + 3, py + 3, px + 4, py + 4, 0xFF8A2A20);
      } else if (state == UltronNodeState.DESTROYED) {
         for (int i = 1; i < size - 1; i++) {
            g.fill(px + i, py + i, px + i + 1, py + i + 1, 0xFF6A6262);
            g.fill(px + size - 1 - i, py + i, px + size - i, py + i + 1, 0xFF6A6262);
         }
      }
      if (core && state != UltronNodeState.DESTROYED) {
         g.fill(px + 2, py + 2, px + size - 2, py + size - 2, 0xFFFFE0D8);
      }
      if (state == UltronNodeState.ONLINE && n.shield() > 0.01F) {
         int sw = Math.max(1, Math.round((size - 2) * Mth.clamp(n.shield(), 0.0F, 1.0F)));
         g.fill(px + 1, py - 2, px + 1 + sw, py - 1, 0xFFFF9A70);
      }
   }

   // ------------------------------------------------------------------ hack bar

   private static void hackBar(GuiGraphics g, Font font, UltronStateS2CPacket s, int width, int height, float time) {
      UltronStateS2CPacket.Node target = null;
      for (UltronStateS2CPacket.Node n : s.nodes()) {
         if (n.id() == s.hackNode()) {
            target = n;
            break;
         }
      }
      Component kind = Component.translatable("viltrumitecore.ultron.node." + (target == null ? UltronNodeKind.CORE : UltronNodeKind.byId(target.kind())).key());
      int pct = Math.round(Mth.clamp(s.hack(), 0.0F, 1.0F) * 100.0F);
      Component label = Component.translatable("viltrumitecore.ultron.hud.hack", kind, pct);
      int w = 104;
      int x = width / 2 - w / 2;
      int y = height / 2 + 16;
      g.fill(x - 1, y - 1, x + w + 1, y + 5, 0xC0100808);
      int fill = Math.round(w * Mth.clamp(s.hack(), 0.0F, 1.0F));
      int col = mix(0xFF3020, 0xFFFFFF, Mth.clamp(s.hack(), 0.0F, 1.0F) * 0.6F);
      g.fill(x, y, x + fill, y + 4, 0xFF000000 | col);
      // scanning tick across the filled part
      int scan = x + (int)((time * 3.0F) % Math.max(1, fill));
      if (fill > 2) {
         g.fill(scan, y, scan + 1, y + 4, 0xFFFFFFFF);
      }
      float scale = 0.75F;
      g.pose().pushPose();
      g.pose().translate(width / 2.0F, y + 7.0F, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, label, -font.width(label) / 2, 0, 0xFFF0E0DC, true);
      g.pose().popPose();
   }

   // ------------------------------------------------------------------ interference

   /** Torn horizontal bands in red and cyan, a red wash and scan lines, all fading with the pulse. */
   private static void interference(GuiGraphics g, float k, long now, int width, int height) {
      int wash = (int)(k * 40.0F);
      g.fill(0, 0, width, height, wash << 24 | 0x400806);
      long seed = (now / 2) * 0x5DEECE66DL + 11L;
      int bands = 4 + (int)(k * 10.0F);
      for (int i = 0; i < bands; i++) {
         seed = seed * 0x5DEECE66DL + 0xBL & 0xFFFFFFFFFFFFL;
         int y = (int)((seed >>> 16) % Math.max(1, height));
         seed = seed * 0x5DEECE66DL + 0xBL & 0xFFFFFFFFFFFFL;
         int h = 1 + (int)((seed >>> 20) % 6);
         seed = seed * 0x5DEECE66DL + 0xBL & 0xFFFFFFFFFFFFL;
         int shift = (int)((seed >>> 18) % 40) - 20;
         int a = (int)(k * (90 + (seed >>> 30) % 80));
         boolean cyan = (seed >>> 25 & 1) == 0;
         int colour = cyan ? 0x30D8FF : 0xFF2A20;
         int x0 = Math.max(0, shift);
         int x1 = Math.min(width, width + shift);
         g.fill(x0, y, x1, y + h, Mth.clamp(a, 0, 255) << 24 | colour);
      }
      int lines = (int)(k * 60.0F);
      if (lines > 4) {
         for (int y = (int)(now % 3); y < height; y += 3) {
            g.fill(0, y, width, y + 1, lines << 24);
         }
      }
   }

   private static int mix(int a, int b, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int r = (int)Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
      int gg = (int)Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
      int bl = (int)Mth.lerp(t, a & 255, b & 255);
      return r << 16 | gg << 8 | bl;
   }

   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerAboveAll("ultron_hud", UltronHudOverlay::render);
      }
   }
}
