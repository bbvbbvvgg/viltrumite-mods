package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class ChopC2SPacket {
   private final boolean isLeft;
   private final int chopType;

   public ChopC2SPacket(boolean isLeft, int chopType) {
      this.isLeft = isLeft;
      this.chopType = chopType;
   }

   public ChopC2SPacket(FriendlyByteBuf buf) {
      this.isLeft = buf.readBoolean();
      this.chopType = buf.readInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeBoolean(this.isLeft);
      buf.writeInt(this.chopType);
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> {
         if (context.getSender() instanceof ViltrumiteCorePlayer corePlayer) {
            if (!dev.baranhan.viltrumitecore.hero.LegacyKit.allows(context.getSender(), dev.baranhan.viltrumitecore.hero.LegacyKit.CHOP) || HeroDamage.isAnchored(context.getSender())) {
               return;
            }

            if (corePlayer.getChopTicks() > 0 || corePlayer.getPunchTicks() > 0) {
               return;
            }

            // A hero style may force the side (Reinhard with Reid drawn: always left), whatever the packet says.
            corePlayer.setLeftChop(dev.baranhan.viltrumitecore.hero.ChopStyle.resolveLeft(this.isLeft,
               dev.baranhan.viltrumitecore.hero.HeroRegistry.get(context.getSender()).chopStyle(context.getSender())));
            corePlayer.setChopType(this.chopType);
            corePlayer.setChopTicks(20);
         }
      });
      context.setPacketHandled(true);
   }
}
