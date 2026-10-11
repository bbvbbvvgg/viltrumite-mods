package dev.baranhan.viltrumitecore.client.ironman;

import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import net.minecraft.world.entity.Entity;

/**
 * Scan highlight (spec §11.2): the scanned target glows JARVIS cyan through
 * walls for the SCAN owner section lifetime (200 t), via OutlineTargets.
 */
public final class ScanHighlight {
   public static final int CYAN = 0x60E0FF;

   private ScanHighlight() {
   }

   public static int colorOf(Entity entity) {
      return ClientHeroData.inSection(OwnerSection.SCAN, entity.getId()) ? CYAN : -1;
   }
}
