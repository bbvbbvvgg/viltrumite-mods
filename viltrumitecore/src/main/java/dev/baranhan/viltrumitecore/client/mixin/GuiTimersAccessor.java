package dev.baranhan.viltrumitecore.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** HUD timers: the hero subtitle panel moves above a visible action bar or held-item name. */
@Mixin({Gui.class})
public interface GuiTimersAccessor {
   @Accessor("overlayMessageTime")
   int viltrumitecore$overlayMessageTime();

   /** The Thanos war's hold hint fades early when the hold ends (it rides the action bar). */
   @Accessor("overlayMessageTime")
   void viltrumitecore$setOverlayMessageTime(int time);

   @Accessor("overlayMessageString")
   Component viltrumitecore$overlayMessage();

   @Accessor("toolHighlightTimer")
   int viltrumitecore$toolHighlightTimer();

   @Accessor("lastToolHighlight")
   ItemStack viltrumitecore$lastToolHighlight();
}
