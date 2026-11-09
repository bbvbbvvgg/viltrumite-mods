package dev.baranhan.viltrumitecore.client.codex.hero;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Key H (rebindable, "Viltrumite" key category): opens the hero select screen in game. */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeroSelectKeys {
   public static final KeyMapping OPEN = new KeyMapping("key.viltrumitecore.hero_select", GLFW.GLFW_KEY_H, "category.viltrumitecore.keys");

   private HeroSelectKeys() {
   }

   @SubscribeEvent
   public static void onRegister(RegisterKeyMappingsEvent event) {
      event.register(OPEN);
   }

   @Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
   public static final class Ticks {
      private Ticks() {
      }

      @SubscribeEvent
      public static void onTick(TickEvent.ClientTickEvent event) {
         if (event.phase != TickEvent.Phase.END) {
            return;
         }
         Minecraft mc = Minecraft.getInstance();
         while (OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
               mc.setScreen(new HeroSelectScreen());
            }
         }
      }
   }
}
