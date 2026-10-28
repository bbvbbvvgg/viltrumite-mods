package dev.baranhan.viltrumitecore.client.ironman;

import dev.baranhan.viltrumitecore.client.ironman.mark.MarkState;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import dev.baranhan.viltrumitecore.hero.ironman.IronManVariant;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Suit HUD (spec §15): left — energy bar
 * (cyan, amber below 30, red blinking at 0, "gliding" label) and three
 * overheat pips (Unibeam overheat counter, red on the last), the overheat
 * lock bar, the overdraft warning and "weapons offline"; in flight — speed and altitude below
 * (the right edge belongs to the ability panel).
 * Hidden while the suit is off. Stage 3: with the helmet open only the energy
 * bar (+ weapons offline / gliding) stays; the JARVIS rows need the closed helmet.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class IronManHud {
   private static final int WIDTH = 80;
   private static final int HEIGHT = 5;
   private static final int LOW_ENERGY = 30;

   private IronManHud() {
   }

   /** Bar colour (ARGB) for an energy value; blink = current blink phase at 0. */
   public static int energyColor(float energy, boolean blinkOn) {
      if (energy <= 0.0F) {
         return blinkOn ? 0xFFFF3030 : 0xFF601010;
      }

      return energy < LOW_ENERGY ? 0xFFFFB020 : 0xFF50D8FF;
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (client.options.hideGui || player == null || player.isSpectator()) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      Font font = client.font;
      MarkState markState = MarkState.of(snapshot);
      if (!IronManView.worn(snapshot) && !IronManView.transitioning(snapshot) && markState.equipPhase() == IronManFlags.EQUIP_NONE) {
         // Nanites lost after a core explosion (spec §9.4): countdown until the suit can deploy.
         int lock = snapshot.extraCooldown(0);
         if (lock > 0) {
            graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.nano_lost", (lock + 19) / 20), 8,
               graphics.guiHeight() / 2 - 20, 0xFFB020, true);
         }

         return;
      }

      float energy = IronManView.energy(snapshot);
      boolean blink = (player.tickCount / 5) % 2 == 0;
      int x = 8;
      int y = graphics.guiHeight() / 2 - 20;
      if (markState.equipPhase() != IronManFlags.EQUIP_NONE) {
         String phase = markState.equipPhase() == IronManFlags.EQUIP_EQUIPPING ? "equipping" : markState.equipPhase() == IronManFlags.EQUIP_EXITING ? "exiting" : "partial";
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman." + phase), x, y - 10, blink ? 0xFF50D8FF : 0xFF2A8FB8, true);
      }

      graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.energy"), x, y, 0x9FE8FF, true);
      int barY = y + 11;
      graphics.fill(x - 1, barY - 1, x + WIDTH + 1, barY + HEIGHT + 1, 0xA0081420);
      int fill = Math.round(WIDTH * energy / IronManRules.ENERGY_MAX);
      int color = energyColor(energy, blink);
      // Empty: the whole bar blinks red.
      graphics.fill(x, barY, x + (energy <= 0.0F ? WIDTH : fill), barY + HEIGHT, color);
      graphics.fill(x, barY, x + fill, barY + 1, 0x60FFFFFF);
      // Weapons unlock mark: below it repulsors, Unibeam, missiles, nano weapons and the shield are offline.
      int mark = x + Math.round(WIDTH * IronManRules.WEAPONS_UNLOCK / IronManRules.ENERGY_MAX);
      graphics.fill(mark, barY - 1, mark + 1, barY + HEIGHT + 1, 0xC0FFFFFF);
      String value = Math.round(energy) + "%";
      graphics.drawString(font, value, x + WIDTH + 4, barY - 2, color & 0xFFFFFF, true);
      // Stage 4: mark durability row under the energy bar (spec §15.2), with or without the helmet.
      int below = barY + HEIGHT + 4;
      if (markState.mark() != null && markState.markOn()) {
         float frac = markState.durabilityFraction();
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.durability"), x, below, 0x9FE8FF, true);
         int durY = below + 10;
         graphics.fill(x - 1, durY - 1, x + WIDTH + 1, durY + 3, 0xA0081420);
         graphics.fill(x, durY, x + Math.round(WIDTH * frac), durY + 2, frac < 0.25F ? (blink ? 0xFFFF4030 : 0xFFFFB020) : 0xFF50D8FF);
         graphics.drawString(font, Math.round(frac * 100.0F) + "%", x + WIDTH + 4, durY - 2, 0xFFFFFF, true);
         below += 16;
      }

      // Stage 5: Hulkbuster durability (spec §15.2), gold accent of the Mark 48.
      if (IronManView.flag(snapshot, IronManFlags.Field.HULKBUSTER_PHASE) != 0) {
         float hulkFrac = IronManVariant.hulkDurability(snapshot.variant());
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.hulkbuster"), x, below, 0xFFE0A0, true);
         int hulkY = below + 10;
         graphics.fill(x - 1, hulkY - 1, x + WIDTH + 1, hulkY + 3, 0xA0081420);
         graphics.fill(x, hulkY, x + Math.round(WIDTH * hulkFrac), hulkY + 2, hulkFrac < 0.25F ? (blink ? 0xFFFF4030 : 0xFFFFB020) : 0xFFE0A030);
         graphics.drawString(font, Math.round(hulkFrac * 100.0F) + "%", x + WIDTH + 4, hulkY - 2, 0xFFFFFF, true);
         below += 16;
         below += dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterHud.draw(graphics, font, snapshot, x, below);
      }

      // Stage 3 (spec §15.2): without the closed helmet only energy, durability and the panel.
      if (!HelmetAnim.closedFlag(snapshot)) {
         int bareY = below;
         if (snapshot.resourceLocked()) {
            graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.weapons_offline"), x, bareY, blink ? 0xFF4030 : 0x903020, true);
            bareY += 10;
         }

         if (IronManView.glide(snapshot)) {
            graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.glide"), x, bareY, blink ? 0xFFB020 : 0xFF6030, true);
         }

         return;
      }

      // Overheat pips: Unibeam overheats so far (spec §9.1); the third one is the overdraft.
      int pipY = below;
      int overheats = IronManView.flag(snapshot, IronManFlags.Field.OVERHEAT_COUNT);
      boolean overdraft = IronManView.flag(snapshot, IronManFlags.Field.OVERDRAFT) != 0;
      boolean sputter = IronManView.flag(snapshot, IronManFlags.Field.OVERDRAFT_SPUTTER) != 0;
      for (int i = 0; i < 3; i++) {
         int px = x + i * 8;
         boolean lit = i < overheats || overdraft && i == 2;
         int pip = !lit ? 0x80103040 : i == 2 || overheats >= 2 ? (blink || !overdraft ? 0xFFFF4030 : 0xFF601010) : 0xFFFFB020;
         graphics.fill(px, pipY, px + 6, pipY + 3, pip);
         graphics.fill(px, pipY, px + 6, pipY + 1, lit ? 0xC0FFFFFF : 0x8060D8FF);
      }

      graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.core"), x + 26, pipY - 2, 0x9FE8FF, true);
      // Overheat lock: the bar empties over the 2 s cool-down.
      if (IronManView.flag(snapshot, IronManFlags.Field.OVERHEAT_LOCK) != 0) {
         float cool = 1.0F - IronManView.progress(snapshot, dev.baranhan.viltrumitecore.hero.HeroAction.UNIBEAM, client.getFrameTime());
         graphics.fill(x, pipY + 4, x + Math.round(WIDTH * cool), pipY + 5, 0xFFFF7030);
      }

      int textY = pipY + 7;
      if (overdraft) {
         graphics.drawString(font, Component.translatable(sputter ? "hud.viltrumitecore.ironman.overdraft_critical" : "hud.viltrumitecore.ironman.overdraft"),
            x, textY, blink ? 0xFF4030 : 0xFFB020, true);
         textY += 10;
      } else if (snapshot.resourceLocked()) {
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.weapons_offline"), x, textY, blink ? 0xFF4030 : 0x903020, true);
         textY += 10;
      }

      if (IronManView.glide(snapshot)) {
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.glide"), x, textY, blink ? 0xFFB020 : 0xFF6030, true);
      }

      if (player instanceof ViltrumiteFlightPlayer flyer && flyer.getFlightState() != FlightState.NONE) {
         double speed = player.getDeltaMovement().length() * 20.0;
         int ground = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
         int altitude = Math.max(0, (int)Math.floor(player.getY()) - ground);
         String speedText = Component.translatable("hud.viltrumitecore.ironman.speed", Math.round(speed)).getString();
         String altText = Component.translatable("hud.viltrumitecore.ironman.altitude", altitude).getString();
         // Below the energy block: the right edge belongs to the ability panel.
         int flightY = textY + 12;
         graphics.drawString(font, speedText, x, flightY, 0x9FE8FF, true);
         graphics.drawString(font, altText, x, flightY + 10, 0x9FE8FF, true);
      }
   }
}
