package dev.baranhan.viltrumitecore.client.hero;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * First-person arms of a hero for scenes that pose the arms in the world (spec item 12: lifting Mjölnir showed
 * the vanilla skin on every hero). Shared code asks the hero, never a HeroId branch:
 * <ol>
 *   <li>a hero that draws its own arms (a separate suit model) registers a {@link Drawer};</li>
 *   <li>otherwise the arms take the hero's hand texture, the same one vanilla's renderHand gets through
 *   {@link HeroSkins} (suits, skins, nano), so the lift arms match the hero's normal first-person hands.</li>
 * </ol>
 */
public final class HeroLiftArms {
   private static final Map<HeroId, Drawer> DRAWERS = new EnumMap<>(HeroId.class);

   private HeroLiftArms() {
   }

   /** Draws both arms of {@code model} (already posed in its part space) on {@code stack}; true when it drew them. */
   public interface Drawer {
      boolean draw(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, PoseStack stack, MultiBufferSource buffers, int light);
   }

   public static void register(HeroId id, Drawer drawer) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(drawer, "drawer");
      if (DRAWERS.putIfAbsent(id, drawer) != null) {
         throw new IllegalStateException("Lift arms already registered for " + id);
      }
   }

   @Nullable
   public static Drawer drawer(AbstractClientPlayer player) {
      return player instanceof HeroPlayer hero ? DRAWERS.get(hero.getHeroId()) : null;
   }

   /** The texture of the hero's first-person hands: its hand skin when it has one, else {@code fallback}. */
   public static ResourceLocation handTexture(AbstractClientPlayer player, ResourceLocation fallback) {
      ResolvedHeroSkin skin = HeroSkins.resolve(player).orElse(null);
      return skin != null && !skin.suppressOverride() ? skin.handTexture() : fallback;
   }
}
