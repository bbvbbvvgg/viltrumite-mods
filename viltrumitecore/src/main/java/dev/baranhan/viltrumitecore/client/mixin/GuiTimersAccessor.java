package dev.baranhan.viltrumitecore.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only HUD timers: the hero subtitle panel moves above a visible action bar or held-item name. */
@Mixin({Gui.class})
public interface GuiTimersAccessor {
   @Accessor("overlayMessageTime")
   int viltrumitecore$overlayMessageTime();

   @Accessor("toolHighlightTimer")
   int viltrumitecore$toolHighlightTimer();

   @Accessor("lastToolHighlight")
   ItemStack viltrumitecore$lastToolHighlight();
}
