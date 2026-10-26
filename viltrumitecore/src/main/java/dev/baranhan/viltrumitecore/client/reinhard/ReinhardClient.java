package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.render.vfx.OutlineTargets;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for Reinhard: client systems register here (panel style, HUD, race screen head, Wind Reading outline, Step Dash and Dash Behind prediction). */
public final class ReinhardClient {
   private static java.util.function.Consumer<dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket> erasureReceiver = packet -> {};
   public static void onErasure(java.util.function.Consumer<dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket> receiver) {erasureReceiver=receiver;}
   public static void receiveErasure(dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket packet) {erasureReceiver.accept(packet);}

   private static java.util.function.Consumer<dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket> limboReceiver = packet -> {};
   public static void onLimbo(java.util.function.Consumer<dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket> receiver) {limboReceiver=receiver;}
   public static void receiveLimbo(dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket packet) {limboReceiver.accept(packet);}

   private ReinhardClient() {
   }

   public static void init() {
      dev.baranhan.viltrumitecore.client.reinhard.limbo.LimboOverlay.init();
      onLimbo(dev.baranhan.viltrumitecore.client.reinhard.limbo.LimboOverlay::onPacket);
      dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene.init();
      onErasure(dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene::onPacket);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene::onRender);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene::onUnload);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene::onLogout);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutScene::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutScene::onRender);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutScene::onUnload);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutFov::onFov);
      dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer.register(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutErasure.INSTANCE);
      dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer.register(dev.baranhan.viltrumitecore.client.reinhard.cut.DiagonalCutErasure.INSTANCE);
      PanelStyles.register(HeroId.REINHARD, ReinhardPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.REINHARD, ReinhardHeadPreview.INSTANCE);
      OutlineTargets.register(WindReadingClient::colorOf);
      MinecraftForge.EVENT_BUS.addListener(StepDashClient::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(DashBehindClient::onClientTick);
      dev.baranhan.viltrumitecore.client.hero.HeroClientInput.register(HeroId.REINHARD, DashBehindClient::intercept);
      MinecraftForge.EVENT_BUS.addListener(dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawInput::onMovementInput);
   }
}
