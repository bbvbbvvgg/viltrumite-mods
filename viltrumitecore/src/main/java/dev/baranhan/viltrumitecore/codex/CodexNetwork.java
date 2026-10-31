package dev.baranhan.viltrumitecore.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The encyclopedia's own small channel {@code viltrumitecore:codex}: one owner-only snapshot packet. It is
 * separate from {@code CoreMessages} on purpose, so the main packet table and its protocol version (20) stay
 * untouched (the parallel contextual-interactions branch also appends there). A peer without the channel is
 * accepted; it simply never sends a snapshot (the encyclopedia then shows no hero icons and no discoveries).
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CodexNetwork {
   public static final String VERSION = "1";
   private static SimpleChannel channel;

   private CodexNetwork() {
   }

   @SubscribeEvent
   public static void onSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(CodexNetwork::register);
   }

   static synchronized void register() {
      if (channel != null) {
         return;
      }
      SimpleChannel net = NetworkRegistry.ChannelBuilder.named(new ResourceLocation("viltrumitecore", "codex"))
         .networkProtocolVersion(() -> VERSION)
         .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
         .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
         .simpleChannel();
      net.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
         .decoder(Snapshot::new)
         .encoder(Snapshot::toBytes)
         .consumerMainThread(Snapshot::handle)
         .add();
      channel = net;
   }

   /** Sends the owner his own record (never anyone else's). */
   public static void sync(ServerPlayer player, CodexRecord record) {
      SimpleChannel net = channel;
      if (net == null || player.connection == null || !net.isRemotePresent(player.connection.connection)) {
         return;
      }
      net.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(new ArrayList<>(record.heroes()), new ArrayList<>(record.interactions())));
   }

   /** Full snapshot of the owner's progress (heroes chosen, interactions discovered). */
   public record Snapshot(List<String> heroes, List<String> interactions) {
      public Snapshot(FriendlyByteBuf buffer) {
         this(readList(buffer, CodexRecord.MAX_HEROES), readList(buffer, CodexRecord.MAX_INTERACTIONS));
      }

      public void toBytes(FriendlyByteBuf buffer) {
         writeList(buffer, this.heroes, CodexRecord.MAX_HEROES);
         writeList(buffer, this.interactions, CodexRecord.MAX_INTERACTIONS);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.baranhan.viltrumitecore.client.codex.CodexClientState.accept(new CodexRecord(this.heroes, this.interactions))));
         context.get().setPacketHandled(true);
      }

      private static List<String> readList(FriendlyByteBuf buffer, int max) {
         int n = buffer.readVarInt();
         if (n < 0 || n > max) {
            throw new IllegalArgumentException("codex list too long: " + n);
         }
         List<String> out = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            out.add(buffer.readUtf(CodexRecord.MAX_ID));
         }
         return out;
      }

      private static void writeList(FriendlyByteBuf buffer, List<String> list, int max) {
         int n = Math.min(max, list.size());
         buffer.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            buffer.writeUtf(list.get(i), CodexRecord.MAX_ID);
         }
      }
   }
}
