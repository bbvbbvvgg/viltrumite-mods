package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroMouseC2SPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Client half of the mouse seam (spec §6.1). When the local hero claims a
 * button, the vanilla attack / use / pick-block is cancelled (no swing, no
 * survival hotbar swap from MMB) and press/release edges go to the server.
 * Releases come from a tick watching isDown(): a screen opening, the window
 * losing focus or the claim disappearing while held also sends the release.
 * MIDDLE is the heart key mapping (default MMB); it wins over pick-block only
 * when both share the key.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HeroMouseInput {
   private static final boolean[] SENT_DOWN = new boolean[MouseButton.values().length];
   private static final boolean[] CLICKED = new boolean[MouseButton.values().length];

   private HeroMouseInput() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player == null) {
         return;
      }

      MouseButton button;
      if (event.isAttack()) {
         button = MouseButton.PRIMARY;
      } else if (event.isUseItem()) {
         button = MouseButton.SECONDARY;
      } else if (event.isPickBlock() && AbilityInputManager.heroKeyOwns(client, event.getKeyMapping())) {
         button = MouseButton.MIDDLE;
      } else {
         return;
      }

      if (claim(player, button) != null) {
         event.setCanceled(true);
         event.setSwingHand(false);
         if (button != MouseButton.MIDDLE) {
            CLICKED[button.ordinal()] = true;
         }
      }
   }

   /** Client tick (END): send press/release edges for claimed buttons. */
   public static void tick(Minecraft client) {
      Player player = client.player;
      if (player == null) {
         java.util.Arrays.fill(SENT_DOWN, false);
         java.util.Arrays.fill(CLICKED, false);
         return;
      }

      KeyMapping heart = AbilityInputManager.assignHeartKey;
      boolean heartClicked = false;
      if (heart != null) {
         while (heart.consumeClick()) {
            heartClicked = true;
         }

         if (AbilityInputManager.heroKeyOwns(client, client.options.keyPickItem)) {
            // Drain stray pick-item clicks so a later tick never replays them.
            while (client.options.keyPickItem.consumeClick()) {
            }
         }
      }

      boolean focused = client.screen == null && client.isWindowActive();
      for (MouseButton button : MouseButton.values()) {
         int i = button.ordinal();
         if (button == MouseButton.GUARD) {
            // The guard key has its own edges (HeroGuardInput, before vanilla keybinds).
            continue;
         }

         KeyMapping key = switch (button) {
            case PRIMARY -> client.options.keyAttack;
            case SECONDARY -> client.options.keyUse;
            default -> heart;
         };
         boolean clicked = button == MouseButton.MIDDLE ? heartClicked : CLICKED[i];
         CLICKED[i] = false;
         boolean down = focused && key != null && (key.isDown() || clicked) && claim(player, button) != null;
         if (down != SENT_DOWN[i]) {
            SENT_DOWN[i] = down;
            CoreMessages.sendToServer(new HeroMouseC2SPacket(button, down));
         }
      }
   }

   private static HeroAction claim(Player player, MouseButton button) {
      var hero=HeroRegistry.get(player);
      if(button==MouseButton.PRIMARY && !hero.claimsBlockAttack(player)
         && Minecraft.getInstance().hitResult!=null && Minecraft.getInstance().hitResult.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
      if(button==MouseButton.SECONDARY && !hero.claimsBlockUse(player)
         && Minecraft.getInstance().hitResult!=null && Minecraft.getInstance().hitResult.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
      return hero.mouseAction(button, player);
   }
}
