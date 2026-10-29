package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Body scale seam (both sides): a hero may scale its player's hitbox and eye height
 * ({@link HeroDefinition#bodyScale}, {@link HeroDefinition#bodyWidthScale}; per-pose table in
 * {@link HeroBodySize}). HeroBodyTracker refreshes dimensions when the scale changes; the pose fit
 * checks use the same scaled boxes (EntityPoseBoxMixin).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroSizeEvents {
   private HeroSizeEvents() {
   }

   @SubscribeEvent
   public static void onSize(EntityEvent.Size event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }

      HeroBodySize.Scale scale = HeroBodySize.of(player);
      if (scale.vanilla()) {
         return;
      }

      float eye = event.getNewEyeHeight();
      event.setNewSize(HeroBodySize.dimensions(event.getNewSize(), event.getPose(), scale));
      event.setNewEyeHeight(HeroBodySize.eyeHeight(eye, event.getPose(), scale));
   }
}
