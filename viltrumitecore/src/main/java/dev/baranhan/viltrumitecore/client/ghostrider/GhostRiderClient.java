package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleClient;
import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleTrails;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for the Ghost Rider (called from ViltrumiteCoreClient's setup). */
public final class GhostRiderClient {
   private GhostRiderClient() {
   }

   public static void init() {
      HellCycleEntity.installClient(HellCycleClient.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(HellCycleTrails::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(HellCycleClient::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderCamera::onFov);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderCamera::onAngles);
   }
}
