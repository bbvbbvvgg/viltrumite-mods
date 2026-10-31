package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client state of the Thanos event from {@link ThanosStateS2CPacket}: the stage, the countdown
 * (estimated between packets on the unpaused client clock), the HUD for everyone who sees the
 * fight (timer to the snap + the six stone pips, the final minute pulses red), the music, the last
 * ten seconds' beeps, the final minute's sky. A watchdog drops everything if the server goes
 * silent for 3 s; a logout or level unload clears it too.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosClient {
   public static final int WATCHDOG_TICKS = 60;
   private static final Object TINT = new Object();
   private static int stage = ThanosStateS2CPacket.NONE;
   private static int thanosId = -1;
   private static int ticksLeft;
   private static int duration = ThanosRules.DURATION_TICKS;
   private static int phase;
   private static int stageTick;
   private static boolean hud;
   private static long clientTicks;
   private static long received;
   private static int lastBeep = -1;
   private static float bossBottom = 31.0F;
   private static float bossSeen = -1.0F;

   private ThanosClient() {
   }

   public static void accept(ThanosStateS2CPacket p) {
      if (p.stage() == ThanosStateS2CPacket.NONE) {
         clear();
         return;
      }

      int before = stage;
      stage = p.stage();
      thanosId = p.thanosId();
      ticksLeft = p.ticksLeft();
      duration = Math.max(1, p.duration());
      phase = p.phase();
      stageTick = p.stageTick();
      hud = p.hud();
      received = clientTicks;
      ThanosSceneClient.onState(before, stage, stageTick);
   }

   public static int stage() {
      return stage;
   }

   public static boolean active() {
      return stage != ThanosStateS2CPacket.NONE;
   }

   /** Does this player see the fight (HUD, music) rather than only the universe scene? */
   public static boolean hud() {
      return hud;
   }

   public static int thanosId() {
      return thanosId;
   }

   public static ThanosPhase phase() {
      return ThanosPhase.byIndex(phase);
   }

   /** The Thanos entity of the event in this client's level, if loaded. */
   @Nullable
   public static ThanosEntity thanos() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || thanosId < 0) {
         return null;
      }

      Entity e = mc.level.getEntity(thanosId);
      return e instanceof ThanosEntity t && !t.isRemoved() ? t : null;
   }

   /** Stage clock: the server's stage tick plus the local unpaused ticks since that packet. */
   public static float stageTime(float partial) {
      return stageTick + (clientTicks - received) + Mth.clamp(partial, 0.0F, 1.0F);
   }

   /** Ticks left on the timer, estimated between packets (the timer holds while the world is stopped). */
   public static int ticksLeft() {
      if (stage != ThanosStateS2CPacket.FIGHT || dev.baranhan.viltrumitecore.client.hero.WorldPauseClient.active()) {
         return ticksLeft;
      }

      return (int)Math.max(0L, ticksLeft - (clientTicks - received));
   }

   public static int duration() {
      return duration;
   }

   public static boolean finalMinute() {
      return stage == ThanosStateS2CPacket.FIGHT && ticksLeft() <= ThanosRules.FINAL_TICKS;
   }

   /** Ticks into his defeat: the stage clock when it is known, else his own state clock. */
   public static float victoryTime(ThanosEntity e, float partial) {
      if (stage == ThanosStateS2CPacket.VICTORY && e.getId() == thanosId) {
         return stageTime(partial);
      }

      return Math.max(0.0F, e.tickCount + partial - e.clientStateStart);
   }

   public static long clientTicks() {
      return clientTicks;
   }

   static void clear() {
      int before = stage;
      stage = ThanosStateS2CPacket.NONE;
      thanosId = -1;
      hud = false;
      lastBeep = -1;
      ThanosMusic.stop();
      SkyTint.INSTANCE.remove(TINT);
      ThanosSceneClient.onState(before, ThanosStateS2CPacket.NONE, 0);
      ThanosFx.clear();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (active()) {
            clear();
         }

         return;
      }

      if (mc.isPaused()) {
         return;
      }

      clientTicks++;
      if (!active()) {
         ThanosMusic.update(false, false, 0.0F);
         return;
      }

      if (clientTicks - received > WATCHDOG_TICKS) {
         clear();
         return;
      }

      boolean fight = stage == ThanosStateS2CPacket.FIGHT || stage == ThanosStateS2CPacket.INTRO;
      boolean late = finalMinute();
      ThanosMusic.update(hud && fight, late, 1.0F);
      if (hud && late) {
         SkyTint.INSTANCE.push(TINT, 4, () -> new SkyTint.Tint(1.0F, 0.62F, 0.6F, 0.14F, 1.0F));
      } else {
         SkyTint.INSTANCE.remove(TINT);
      }

      if (hud && stage == ThanosStateS2CPacket.FIGHT) {
         int seconds = ThanosRules.secondsLeft(ticksLeft());
         if (seconds <= 10 && seconds >= 1 && seconds != lastBeep) {
            lastBeep = seconds;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ThanosSounds.COUNTDOWN.get(), 0.8F + 0.045F * (10 - seconds), 0.9F));
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide() && active() && stage != ThanosStateS2CPacket.SNAP) {
         // a dimension change mid-fight: the next packet brings the state back
         ThanosFx.clear();
      }
   }

   @SubscribeEvent
   public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
      bossSeen = Math.max(bossSeen, event.getY() + event.getIncrement());
   }

   // ---- HUD ---------------------------------------------------------------------------------

   static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (bossSeen > 0.0F) {
         bossBottom = bossSeen;
      }

      bossSeen = -1.0F;
      if (!active() || mc.options.hideGui || CinematicHud.hiding()) {
         return;
      }

      ThanosFx.renderHud(g, partial, width, height);
      if (!hud || stage != ThanosStateS2CPacket.FIGHT && stage != ThanosStateS2CPacket.INTRO) {
         return;
      }

      int left = ticksLeft();
      boolean late = left <= ThanosRules.FINAL_TICKS && stage == ThanosStateS2CPacket.FIGHT;
      float time = clientTicks + partial;
      Font font = mc.font;
      int w = 118;
      int h = 40;
      int x = (width - w) / 2;
      int y = Math.round(Math.max(bossBottom, 12.0F)) + 2;
      float pulse = late ? 0.5F + 0.5F * (float)Math.sin(time * 0.32F) : 0.0F;
      panel(g, x, y, w, h, late, pulse);
      Component title = Component.translatable(late ? "hud.viltrumitecore.thanos.final" : "hud.viltrumitecore.thanos.until_snap");
      int titleColor = late ? mix(0xFF8A7A, 0xFFFFFF, pulse * 0.5F) : 0xE9C46A;
      g.pose().pushPose();
      g.pose().translate(x + w / 2.0F, y + 4.0F, 0.0F);
      g.pose().scale(0.75F, 0.75F, 1.0F);
      g.drawString(font, title, -font.width(title) / 2, 0, titleColor, true);
      g.pose().popPose();
      String clock = ThanosRules.clock(left);
      int seconds = ThanosRules.secondsLeft(left);
      float pop = seconds <= 10 && stage == ThanosStateS2CPacket.FIGHT ? 1.0F + 0.18F * Math.max(0.0F, 1.0F - (left % 20 + (1.0F - partial)) / 6.0F) : 1.0F;
      float scale = 2.0F * pop;
      int clockColor = late ? mix(0xFF4A3A, 0xFFE0D8, pulse * 0.6F) : 0xF4F1EA;
      g.pose().pushPose();
      g.pose().translate(x + w / 2.0F, y + 20.0F, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, clock, -font.width(clock) / 2, -4, clockColor, true);
      g.pose().popPose();
      pips(g, x + w / 2, y + h - 6, time, late);
   }

   private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean late, float pulse) {
      int edge = late ? mix(0x6E1414, 0xB02A20, pulse) : 0x4B2E6B;
      int gold = late ? mix(0xC24A2A, 0xFF8A50, pulse) : 0xB8913A;
      g.fill(x + 1, y, x + w - 1, y + h, 0xC00B0810);
      g.fill(x, y + 1, x + w, y + h - 1, 0xC00B0810);
      g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0211A2E, 0xD0120E18);
      g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF000000 | gold);
      g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFF000000 | edge);
      g.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFF000000 | edge);
      g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFF000000 | edge);
      // gold corner studs
      g.fill(x + 3, y + 3, x + 5, y + 5, 0xFF000000 | gold);
      g.fill(x + w - 5, y + 3, x + w - 3, y + 5, 0xFF000000 | gold);
   }

   /** Six stone pips; the stones of his current move flare. */
   private static void pips(GuiGraphics g, int cx, int cy, float time, boolean late) {
      ThanosEntity thanos = thanos();
      ThanosMove move = thanos == null ? null : thanos.move();
      float moveAge = thanos == null ? 1.0E9F : thanos.tickCount - thanos.clientMoveStart;
      boolean charging = move != null && moveAge >= 0 && moveAge < move.length(phase());
      ThanosStone[] stones = ThanosStone.values();
      int step = 12;
      int start = cx - step * (stones.length - 1) / 2;
      for (int i = 0; i < stones.length; i++) {
         ThanosStone s = stones[i];
         int px = start + i * step;
         boolean hot = charging && move.uses(s);
         float glow = hot ? 0.7F + 0.3F * (float)Math.sin(time * 0.9F + i) : late ? 0.35F + 0.25F * (float)Math.sin(time * 0.3F + i) : 0.2F;
         int halo = (int)(glow * 150.0F) << 24 | s.rgb & 0xFFFFFF;
         g.fill(px - 4, cy - 1, px + 5, cy + 2, halo);
         g.fill(px - 1, cy - 4, px + 2, cy + 5, halo);
         diamond(g, px, cy, 3, 0xFF000000 | mix(s.rgb, 0x000000, hot ? 0.0F : 0.25F));
         g.fill(px - 1, cy - 2, px, cy - 1, 0xC0FFFFFF);
      }
   }

   private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
      for (int dy = -r; dy <= r; dy++) {
         int span = r - Math.abs(dy);
         g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
      }
   }

   static int mix(int a, int b, float t) {
      float k = Mth.clamp(t, 0.0F, 1.0F);
      int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
      int gg = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
      int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
      return r << 16 | gg << 8 | bl;
   }

   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerAboveAll("thanos_hud", ThanosClient::renderHud);
      }
   }
}
