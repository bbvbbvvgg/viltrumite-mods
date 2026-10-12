package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Wind Reading outline (spec §4.5), for the local Reinhard only: the ids the
 * server put in his THREATS owner section glow pale wind-blue. The section is
 * Iron Man's too (per owner), so it is read only while the local hero is
 * Reinhard. Registered in {@link ReinhardClient#init}. Client only.
 */
public final class WindReadingClient {
   /** Pale wind blue. */
   public static final int WIND_COLOR = 0xD8F4FF;

   private WindReadingClient() {
   }

   /** OutlineTargets source: WIND_COLOR or -1 (not ours, the next source decides). */
   public static int colorOf(Entity entity) {
      Minecraft client = Minecraft.getInstance();
      return color(client.player != null && ReinhardView.of(client.player) != null, ClientHeroData.inSection(OwnerSection.THREATS, entity.getId()));
   }

   /** Pure. */
   static int color(boolean localIsReinhard, boolean marked) {
      return localIsReinhard && marked ? WIND_COLOR : -1;
   }
}
