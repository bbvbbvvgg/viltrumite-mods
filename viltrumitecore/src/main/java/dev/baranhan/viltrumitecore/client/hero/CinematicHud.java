package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Temporary clean cinematic view: while any registered scene runs, the whole game HUD (hotbar,
 * health, food, armour, crosshair, hero ability bars, every overlay and RenderGuiEvent.Post
 * panel) and the first-person hand are skipped for that frame only. Nothing is written to the
 * player's options, so the HUD is back on the very next frame after the scene's predicate turns
 * false (normal end, cancel, death, logout, dimension change, reload: each scene's predicate is
 * its own live state). Hero subtitles and the scene's own layers are still drawn.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class CinematicHud {
   /** A scene-owned layer drawn on the clean screen. */
   public interface Layer {void render(GuiGraphics graphics, float partialTick, int width, int height);}
   private record Entry(BooleanSupplier active, Layer layer) {}
   private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
   private CinematicHud() {}

   /** Hide the HUD while {@code active}; {@code layer} (nullable) is drawn instead. */
   public static void hideWhile(BooleanSupplier active, Layer layer) {ENTRIES.add(new Entry(active, layer));}

   public static boolean hiding() {
      for (Entry e : ENTRIES) if (test(e)) return true;
      return false;
   }

   private static boolean test(Entry e) {
      try {return e.active().getAsBoolean();}
      catch (RuntimeException error) {return false;}
   }

   /** Pure: a frame hides the HUD only for a live scene and a live player. */
   public static boolean hides(boolean scene, boolean inWorld) {return scene && inWorld;}

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onGuiPre(RenderGuiEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (!hides(hiding(), mc.player != null && mc.level != null)) return;
      event.setCanceled(true);
      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth(), h = g.guiHeight();
      float pt = event.getPartialTick();
      for (Entry e : ENTRIES) {
         if (e.layer() == null || !test(e)) continue;
         try {e.layer().render(g, pt, w, h);}
         catch (RuntimeException error) {com.mojang.logging.LogUtils.getLogger().warn("Cinematic layer failed", error);}
      }
      if (mc.gui instanceof ForgeGui gui) HeroSubtitles.render(gui, g, pt, w, h);
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onHand(RenderHandEvent event) {
      if (hiding()) event.setCanceled(true);
   }
}
