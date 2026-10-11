package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.client.hero.PanelStyle.Bounds;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Spec §9.1: three mode icons next to the panel (active one lit) and the Reid
 * idle bar under them (Stage 4 fills {@code snapshot.resource()}). The panel
 * sits 5 px from the right screen edge, so the row is drawn left of the
 * ability bar, above its first key label and left of the page number.
 */
public final class ReinhardHud {
   public static final int ICON = 12;
   public static final int GAP = 2;
   public static final int BAR_WIDTH = 3 * ICON + 2 * GAP;
   public static final int BAR_HEIGHT = 3;
   private static final int GOLD = 0xFFE8C15A;
   private static final int BAR_BACK = 0xA0101830;
   private static final ResourceLocation[] MODE_ICONS = new ResourceLocation[ReinhardMode.values().length];

   static {
      for (ReinhardMode mode : ReinhardMode.values()) {
         MODE_ICONS[mode.ordinal()] = new ResourceLocation("viltrumitecore", "textures/gui/reinhard/mode_" + mode.key() + ".png");
      }
   }

   private ReinhardHud() {
   }

   public record Layout(Bounds[] icons, Bounds reidBar) {
   }

   /** Pure layout from the ability bar: icons end 2 px left of the bar; the Reid bar 2 px under them ends 1 px above the bar. */
   public static Layout layout(Bounds abilityBar) {
      int x0 = abilityBar.x() - GAP - BAR_WIDTH;
      int barY = abilityBar.y() - 1 - BAR_HEIGHT;
      int iconY = barY - GAP - ICON;
      Bounds[] icons = new Bounds[3];
      for (int i = 0; i < icons.length; i++) {
         icons[i] = new Bounds(x0 + i * (ICON + GAP), iconY, ICON, ICON);
      }

      return new Layout(icons, new Bounds(x0, barY, BAR_WIDTH, BAR_HEIGHT));
   }

   /** The Reid bar shows only while Reid idles out of the sheath (resource = idle ticks left). */
   public static boolean showsReidBar(int resource) {
      return resource > 0;
   }

   public static int reidFillWidth(int resource, int width) {
      float fill = Math.max(0.0F, Math.min(1.0F, resource / (float)ReinhardRules.REID_IDLE_TICKS));
      return Math.round(fill * width);
   }

   public static void draw(GuiGraphics graphics, Bounds abilityBar, HeroPublicSnapshot snapshot) {
      if (snapshot.heroId() != HeroId.REINHARD) {
         return;
      }

      Layout layout = layout(abilityBar);
      int active = ReinhardFlags.mode(snapshot.heroFlags());
      for (int i = 0; i < layout.icons().length; i++) {
         Bounds icon = layout.icons()[i];
         if (i == active) {
            outline(graphics, icon, GOLD);
         } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.4F);
         }

         graphics.blit(MODE_ICONS[i], icon.x(), icon.y(), 0.0F, 0.0F, ICON, ICON, ICON, ICON);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      if (showsReidBar(snapshot.resource())) {
         Bounds bar = layout.reidBar();
         graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), BAR_BACK);
         graphics.fill(bar.x(), bar.y(), bar.x() + reidFillWidth(snapshot.resource(), bar.width()), bar.y() + bar.height(), GOLD);
      }
   }

   private static void outline(GuiGraphics graphics, Bounds b, int color) {
      int x0 = b.x() - 1;
      int y0 = b.y() - 1;
      int x1 = b.x() + b.width() + 1;
      int y1 = b.y() + b.height() + 1;
      graphics.fill(x0, y0, x1, y0 + 1, color);
      graphics.fill(x0, y1 - 1, x1, y1, color);
      graphics.fill(x0, y0, x0 + 1, y1, color);
      graphics.fill(x1 - 1, y0, x1, y1, color);
   }
}
