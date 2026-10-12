package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Head preview per hero on the race screen; unregistered heroes keep the flat skin face. */
public final class HeroHeadPreviews {
   private static final Map<HeroId, Preview> PREVIEWS = new EnumMap<>(HeroId.class);

   private HeroHeadPreviews() {
   }

   public static synchronized void register(HeroId hero, Preview preview) {
      PREVIEWS.put(Objects.requireNonNull(hero, "hero"), Objects.requireNonNull(preview, "preview"));
   }

   public static synchronized Preview resolve(@Nullable HeroId hero, Preview fallback) {
      Preview preview = hero == null ? null : PREVIEWS.get(hero);
      return preview == null ? fallback : preview;
   }

   /** Draws the hero's head at (x, y), size px square; the fallback is the face + hat layer of {@code skin}. */
   public static void draw(@Nullable HeroId hero, GuiGraphics graphics, int x, int y, int size, float partialTick, ResourceLocation skin) {
      resolve(hero, skinFace(skin)).draw(graphics, x, y, size, partialTick);
   }

   /** The flat player-head face: base face + hat layer of a 64×64 skin. */
   public static Preview skinFace(ResourceLocation skin) {
      return (graphics, x, y, size, partialTick) -> {
         graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
         graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
      };
   }

   /** Tests only. */
   static synchronized void clear() {
      PREVIEWS.clear();
   }

   @FunctionalInterface
   public interface Preview {
      void draw(GuiGraphics graphics, int x, int y, int size, float partialTick);
   }
}
