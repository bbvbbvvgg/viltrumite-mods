package dev.baranhan.viltrumitecore.client.budget;

import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client setup of the Ultimate Hero Expansion: skins, scene camera and flash. */
@Mod.EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BudgetHeroClient {
   private BudgetHeroClient() {
   }

   /** FMLClientSetup (enqueued): one skin provider per roster hero. */
   public static void init() {
      for (BudgetHeroSpec spec : BudgetHeroes.roster()) {
         HeroSkins.register(spec.id(), new HeroSkins.Provider() {
            @Override
            public ResourceLocation defaultSkin() {
               return BudgetSkins.skin(spec);
            }

            @Override
            public String defaultModelName() {
               return "default";
            }

            @Override
            public boolean suppressCosmetics(AbstractClientPlayer player) {
               return true;
            }

            @Override
            public boolean suppressCape(AbstractClientPlayer player) {
               return true;
            }
         });
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         BudgetSceneClient.tick();
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (BudgetSceneClient.active()) {
         float partial = (float)event.getPartialTick();
         event.setYaw(event.getYaw() + BudgetSceneClient.yawOffset(partial));
         event.setRoll(event.getRoll() + BudgetSceneClient.rollOffset(partial));
      }
   }

   @SubscribeEvent
   public static void onFov(ViewportEvent.ComputeFov event) {
      if (BudgetSceneClient.active()) {
         event.setFOV(event.getFOV() * BudgetSceneClient.fovFactor((float)event.getPartialTick()));
      }
   }

   @SubscribeEvent
   public static void onGui(RenderGuiEvent.Post event) {
      BudgetSceneClient.renderFlash(event.getGuiGraphics(), event.getPartialTick());
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      BudgetSceneClient.reset();
   }

   @SubscribeEvent
   public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
      BudgetSceneClient.reset();
   }

   static boolean inWorld() {
      return Minecraft.getInstance().player != null;
   }
}
