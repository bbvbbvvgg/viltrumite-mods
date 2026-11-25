package dev.baranhan.viltrumitecore.client.cinematic;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * On-screen layer of a running cinematic scene (wave1-22, task T4): the letterbox bars, the
 * "[key] skip" hint taken from the real keybinding, and the visual slow motion (the sound bed ducks
 * to the scene's clock rate; the world itself is never slowed — the server is untouched). The clean
 * screen (no hotbar, no hand) comes from the shared {@code client.hero.CinematicHud}, which owns it
 * for every hero scene; this class only adds the layer. Client thread only.
 */
public final class CinematicHud {
   /** Owner key of our sound duck. */
   private static final Object DUCK = new Object();
   private static final int DUCK_FADE = 4;
   private static final int BAR_FADE = 6;
   private static final float BAR_FRACTION = 0.11F;
   private static float bars;
   private static float barsBefore;
   private static boolean ducked;

   private CinematicHud() {
   }

   /** Registers the layer and the tick (called from the client setup, next to the camera). */
   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(CinematicHud::onClientTick);
      // The hidden-HUD frame is the shared hero service; our layer draws inside it.
      dev.baranhan.viltrumitecore.client.hero.CinematicHud.hideWhile(CinematicCamera::active, CinematicHud::render);
   }

   static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      barsBefore = bars;
      bars = approach(bars, barsTarget(CinematicCamera.active(), CinematicCamera.letterbox()), 1.0F / BAR_FADE);
      float rate = CinematicCamera.clockRate();
      boolean slowed = rate < 1.0F;
      if (slowed) {
         SoundDuck.INSTANCE.duck(DUCK, SoundSource.MASTER, rate, DUCK_FADE, Set.of());
      } else if (releaseNow(ducked, slowed)) {
         SoundDuck.INSTANCE.release(DUCK, DUCK_FADE);
      }
      ducked = slowed;
   }

   /**
    * Pure: release the sound bed once, on the tick the duck ends. Calling release every tick would
    * recompute the fade step from the current level, so the tail would decay exponentially instead of
    * linearly.
    */
   static boolean releaseNow(boolean ducked, boolean slowed) {
      return ducked && !slowed;
   }

   /** Pure: the letterbox target of this tick (1 = bars fully out). */
   static float barsTarget(boolean playing, boolean letterbox) {
      return playing && letterbox ? 1.0F : 0.0F;
   }

   /** Pure: one step towards {@code target} without overshooting it. */
   static float approach(float current, float target, float step) {
      if (current < target) {
         return Math.min(target, current + step);
      }
      return Math.max(target, current - step);
   }

   /** Pure: "[key] text", the key name coming from the binding and the text from the language file. */
   static Component skipHint(KeyMapping skip) {
      return Component.translatable("hud.viltrumitecore.cinematic.skip", skip == null ? "?" : skip.getTranslatedKeyMessage().getString());
   }

   /** The layer drawn while the HUD is hidden: letterbox bars and the skip hint. */
   static void render(GuiGraphics graphics, float partialTick, int width, int height) {
      float k = Mth.lerp(partialTick, barsBefore, bars);
      int bar = Math.round(height * BAR_FRACTION * k);
      if (bar > 0) {
         graphics.fill(0, 0, width, bar, 0xFF000000);
         graphics.fill(0, height - bar, width, height, 0xFF000000);
      }
      Component hint = skipHint(AbilityInputManager.skipCinematicKey);
      var font = Minecraft.getInstance().font;
      graphics.drawString(font, hint, (width - font.width(hint)) / 2, height - bar - 12, 0xFFE6E6E6, true);
   }
}
