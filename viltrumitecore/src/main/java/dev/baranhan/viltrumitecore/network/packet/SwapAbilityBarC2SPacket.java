package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.hero.AbilityPages;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class SwapAbilityBarC2SPacket {
   public SwapAbilityBarC2SPacket() {
   }

   public SwapAbilityBarC2SPacket(FriendlyByteBuf buf) {
   }

   public void encode(FriendlyByteBuf buf) {
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> {
         net.minecraft.server.level.ServerPlayer player = context.getSender();
         if (player instanceof ViltrumiteAbilityUser abilityUser && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).allowsAbilityPages(player)) {
            String[] slots = new String[AbilityPages.PAGES * AbilityPages.PAGE_SIZE];
            for (int slot = 0; slot < slots.length; slot++) {
               slots[slot] = abilityUser.getAbilityInSlot(slot);
            }
            abilityUser.setActivePage(AbilityPages.next(abilityUser.getActivePage(), slots));
         }
      });
      context.setPacketHandled(true);
   }
}
