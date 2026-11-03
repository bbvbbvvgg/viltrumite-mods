package dev.baranhan.viltrumitecore.client.scp106;

import dev.baranhan.viltrumitecore.scp.scp106.Scp106Entities;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of SCP-106: the renderer and the sky/fog effects of the pocket dimension. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class Scp106ClientSetup {
   private Scp106ClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(Scp106Entities.SCP106.get(), Scp106Renderer::new);
   }

   @SubscribeEvent
   public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
      event.register(new ResourceLocation("viltrumitecore", "pocket"), new PocketEffects());
   }
}
