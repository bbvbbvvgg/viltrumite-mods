package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Client half of the body scale seam: the client computes player dimensions
 * itself, so it refreshes them when a player's synced bodyScale changes.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class BodyScaleClient {
   private static final Map<Integer, Float> LAST = new HashMap<>();

   private BodyScaleClient() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      Minecraft client = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || client.level == null) {
         LAST.clear();
         return;
      }

      for (Player player : client.level.players()) {
         // Height and width factors together: either one changing refreshes the box.
         float scale = HeroRegistry.get(player).bodyScale(player) * 1024.0F + HeroRegistry.get(player).bodyWidthScale(player);
         Float previous = LAST.put(player.getId(), scale);
         if (previous != null && previous != scale || previous == null && scale != 1025.0F) {
            player.refreshDimensions();
         }
      }

      if (LAST.size() > client.level.players().size() * 2 + 8) {
         LAST.keySet().removeIf(id -> client.level.getEntity(id) == null);
      }
   }
}
