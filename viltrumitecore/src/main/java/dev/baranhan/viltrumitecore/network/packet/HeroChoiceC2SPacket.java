package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Third-race selection. Appended packet: carries the stable hero id string. */
public class HeroChoiceC2SPacket {
   private final String heroId;

   public HeroChoiceC2SPacket(String heroId) {
      this.heroId = heroId;
   }

   public HeroChoiceC2SPacket(FriendlyByteBuf buffer) {
      this.heroId = buffer.readUtf(32);
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.heroId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player == null) {
            return;
         }

         HeroId id = HeroId.fromKey(this.heroId);
         if (id == null) {
            return;
         }

         if (player instanceof ViltrumiteCorePlayer corePlayer) {
            // Race choice is one-time: a replayed packet must never re-enter a
            // hero — a fresh session would re-arm the totem, wipe cooldowns and
            // re-grant the book (REGULUS -> VILTRUMITE -> REGULUS in two packets).
            if (corePlayer.hasChosenRace()) {
               return;
            }
            corePlayer.setChosenRace(true);
         }

         if (!HeroRegistry.changeHero(player, id) && HeroRegistry.get(player).id() != id && player instanceof ViltrumiteCorePlayer corePlayer) {
            // Refused (e.g. a one-holder hero slot is taken): the choice stays open.
            corePlayer.setChosenRace(false);
            CoreMessages.sendToPlayer(new OpenRaceScreenS2CPacket(), player);
            return;
         }

         HeroRegistry.repairLoadout(player);
      });
      context.setPacketHandled(true);
   }
}
