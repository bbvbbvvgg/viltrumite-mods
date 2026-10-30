package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Mark 48 helmet HUD rows (rework spec §2, §5, §6) under the Iron Man energy and Hulkbuster durability rows:
 * dash charges, the jet charge or hover fuel, the grip (victim's escape meter) and the ability cooldowns. A player
 * held in a Hulkbuster's fist (any hero) sees the break-free prompt with the same meter. Fills and text only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterHud {
   private static final int WIDTH = 80;
   private static final int GOLD = 0xFFE0A030;
   private static final int DIM = 0xA0081420;
   static final String DASH = "hud.viltrumitecore.ironman.hulkbuster.dash";
   static final String JET = "hud.viltrumitecore.ironman.hulkbuster.jet";
   static final String HOVER = "hud.viltrumitecore.ironman.hulkbuster.hover";
   static final String GRIP = "hud.viltrumitecore.ironman.hulkbuster.grip";
   static final String STRUGGLE = "hud.viltrumitecore.ironman.hulkbuster.struggle";
   static final String READY = "hud.viltrumitecore.ironman.hulkbuster.ready";
   /** Ability rows: the move whose name is shown and its cooldown index in the wire. */
   private static final HulkMove[] ABILITIES = {HulkMove.GRAB_REACH, HulkMove.HEAVY_REPULSORS, HulkMove.SEISMIC_RIFT, HulkMove.VERONICA_CAGE, HulkMove.JET_RAM};
   private static final int[] COOLDOWNS = {HulkbusterWire.CD_GRAB, HulkbusterWire.CD_REPULSORS, HulkbusterWire.CD_RIFT, HulkbusterWire.CD_CAGE,
      HulkbusterWire.CD_ULTIMATE};

   private HulkbusterHud() {
   }

   /** The owner's rows inside the Iron Man HUD column; returns the height used. */
   public static int draw(GuiGraphics graphics, Font font, HeroPublicSnapshot snapshot, int x, int y) {
      if (HulkbusterView.phase(snapshot) != HulkbusterLayer.Phase.ACTIVE) {
         return 0;
      }

      int row = y;
      // Dash charges: one gold cell per charge, the next one filling.
      graphics.drawString(font, Component.translatable(DASH), x, row, 0xFFE0A0, true);
      int charges = HulkbusterView.dashCharges(snapshot);
      int cellX = x + 44;
      for (int i = 0; i < HulkbusterRules.DASH_CHARGES; i++) {
         graphics.fill(cellX + i * 14, row + 1, cellX + i * 14 + 12, row + 7, DIM);
         if (i < charges) {
            graphics.fill(cellX + i * 14, row + 1, cellX + i * 14 + 12, row + 7, GOLD);
         } else if (i == charges) {
            int recharge = HulkbusterView.extra(snapshot, HulkbusterWire.DASH_RECHARGE);
            graphics.fill(cellX + i * 14, row + 1, cellX + i * 14 + Math.round(12.0F * recharge / HulkbusterRules.DASH_RECHARGE), row + 7, 0x90E0A030);
         }
      }

      row += 10;
      Integer charge = HulkbusterMotionClient.jetChargeTicks();
      HulkbusterMotion.State state = HulkbusterView.motion(snapshot);
      if (charge != null || state == HulkbusterMotion.State.JET_CHARGE) {
         float k = (charge != null ? charge : HulkbusterView.motionTicks(snapshot)) / (float)HulkbusterRules.JET_CHARGE_MAX;
         row = bar(graphics, font, Component.translatable(JET), x, row, Math.min(1.0F, k), 0xFFFF9A30);
      } else if (state == HulkbusterMotion.State.HOVER || HulkbusterMotionClient.hovering()) {
         float left = 1.0F - Math.min(HulkbusterRules.HOVER_MAX, HulkbusterView.motionTicks(snapshot)) / (float)HulkbusterRules.HOVER_MAX;
         row = bar(graphics, font, Component.translatable(HOVER), x, row, left, 0xFF60D0FF);
      }

      if (HulkbusterView.victim(snapshot) >= 0) {
         float grip = 1.0F - Math.min(HulkbusterRules.ESCAPE_FULL, HulkbusterView.escape(snapshot)) / (float)HulkbusterRules.ESCAPE_FULL;
         row = bar(graphics, font, Component.translatable(GRIP), x, row, grip, grip < 0.3F ? 0xFFFF4030 : GOLD);
      }

      // The state column starts after the longest name (long locales: "Heavy repulsors" ran into "ready").
      int stateX = x + WIDTH + 4;
      for (HulkMove ability : ABILITIES) {
         stateX = Math.max(stateX, x + font.width(Component.translatable(ability.translationKey())) + 6);
      }

      for (int i = 0; i < ABILITIES.length; i++) {
         int ticks = HulkbusterView.extra(snapshot, COOLDOWNS[i]);
         Component name = Component.translatable(ABILITIES[i].translationKey());
         Component state2 = ticks > 0 ? Component.literal((ticks + 19) / 20 + "s") : Component.translatable(READY);
         graphics.drawString(font, name, x, row, ticks > 0 ? 0x808080 : 0xFFE0A0, true);
         graphics.drawString(font, state2, stateX, row, ticks > 0 ? 0xA0A0A0 : 0x80FF80, true);
         row += 9;
      }

      return row - y + 4;
   }

   private static int bar(GuiGraphics graphics, Font font, Component label, int x, int y, float k, int color) {
      graphics.drawString(font, label, x, y, 0xFFE0A0, true);
      int barY = y + 10;
      graphics.fill(x - 1, barY - 1, x + WIDTH + 1, barY + 3, DIM);
      graphics.fill(x, barY, x + Math.round(WIDTH * Math.max(0.0F, Math.min(1.0F, k))), barY + 2, color);
      return y + 16;
   }

   /** Suit-up system check (fix spec item 2): one line per docked part, typed out on the owner's helmet HUD. */
   static final String CHECK = "hud.viltrumitecore.ironman.hulkbuster.check.";
   static final String[] CHECK_LINES = {"inbound", "legs", "torso", "arms", "shoulders", "helmet", "ready"};
   static final int CHARS_PER_TICK = 3;

   /** Scene tick at which a check line starts typing: the delivery, each plate lock, the final reactor flash. */
   static int checkStart(int line, int dropTicks) {
      if (line == 0) {
         return 0;
      }

      if (line <= HulkbusterLayer.PARTS) {
         return dropTicks + HulkbusterLayer.PART_LOCK[line - 1];
      }

      return dropTicks + dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterSuitUp.REACTOR_FLASH;
   }

   /** Characters of a line shown at a scene tick (0 = not yet). */
   static int checkChars(int line, double sceneTick, int dropTicks, int length) {
      double since = sceneTick - checkStart(line, dropTicks);
      return since < 0.0 ? 0 : Math.min(length, 1 + (int)(since * CHARS_PER_TICK));
   }

   private static void systemCheck(GuiGraphics graphics, Font font, HeroPublicSnapshot snapshot, float partialTick, int tickCount) {
      HulkbusterLayer.Phase phase = HulkbusterView.phase(snapshot);
      if (!HulkbusterView.scene(phase)) {
         return;
      }

      int drop = HulkbusterView.sceneAir(snapshot) ? HulkbusterLayer.DROP_TICKS_AIR : HulkbusterLayer.DROP_TICKS_GROUND;
      double tick = (phase == HulkbusterLayer.Phase.ASSEMBLING ? drop : 0) + snapshot.actionElapsed() + partialTick;
      int x = 10;
      int y = graphics.guiHeight() / 2 - 44;
      for (int i = 0; i < CHECK_LINES.length; i++) {
         String text = "> " + Component.translatable(CHECK + CHECK_LINES[i]).getString();
         int shown = checkChars(i, tick, drop, text.length());
         if (shown <= 0) {
            break;
         }

         boolean last = i == CHECK_LINES.length - 1;
         int colour = last ? (tickCount / 3 % 2 == 0 ? 0xFFFFFF : 0x9FE6FF) : shown < text.length() ? 0xE0F6FF : 0x7FD8FF;
         graphics.drawString(font, text.substring(0, shown), x, y, colour, true);
         y += 10;
      }
   }

   /** The victim's prompt: whoever is in a Hulkbuster's fist sees it, whatever the hero. */
   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui || mc.player == null) {
         return;
      }

      HeroPublicSnapshot own = IronManView.of(mc.player);
      if (own != null) {
         systemCheck(event.getGuiGraphics(), mc.font, own, event.getPartialTick(), mc.player.tickCount);
      }

      Player holder = HeroHeldTargets.holder(mc.player);
      HeroPublicSnapshot snapshot = holder == null ? null : IronManView.of(holder);
      if (snapshot == null || !HulkbusterView.big(HulkbusterView.phase(snapshot)) || HulkbusterView.victim(snapshot) != mc.player.getId()) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      Font font = mc.font;
      int cx = graphics.guiWidth() / 2;
      int y = graphics.guiHeight() / 2 + 24;
      boolean blink = (mc.player.tickCount / 5) % 2 == 0;
      Component text = Component.translatable(STRUGGLE);
      graphics.drawString(font, text, cx - font.width(text) / 2, y, blink ? 0xFFFFFF : 0xFFB020, true);
      float k = Math.min(HulkbusterRules.ESCAPE_FULL, HulkbusterView.escape(snapshot)) / (float)HulkbusterRules.ESCAPE_FULL;
      graphics.fill(cx - WIDTH / 2 - 1, y + 11, cx + WIDTH / 2 + 1, y + 15, DIM);
      graphics.fill(cx - WIDTH / 2, y + 12, cx - WIDTH / 2 + Math.round(WIDTH * k), y + 14, 0xFF80FF60);
   }
}
