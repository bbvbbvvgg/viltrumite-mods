package dev.baranhan.viltrumitecore.codex;

import dev.baranhan.viltrumitecore.codex.hero.HeroSelectService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
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
 * The encyclopedia's own small channel {@code viltrumitecore:codex}. It is separate from
 * {@code CoreMessages} on purpose, so the main packet table and its protocol version stay untouched. A peer
 * without the channel is accepted; it simply never talks codex (no icons, no unlock toasts, and the hero
 * select screen says the server does not support it).
 *
 * <p>v2 (Codex 2.0): the snapshot also carries opened entries and finished tutorials; new messages for the
 * unlock toast and for the hero select screen (request, result, open). Every decision is the server's.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CodexNetwork {
   public static final String VERSION = "2";
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
         .decoder(Snapshot::new).encoder(Snapshot::toBytes).consumerMainThread(Snapshot::handle).add();
      net.messageBuilder(Unlocked.class, 1, NetworkDirection.PLAY_TO_CLIENT)
         .decoder(Unlocked::new).encoder(Unlocked::toBytes).consumerMainThread(Unlocked::handle).add();
      net.messageBuilder(HeroSelectRequest.class, 2, NetworkDirection.PLAY_TO_SERVER)
         .decoder(HeroSelectRequest::new).encoder(HeroSelectRequest::toBytes).consumerMainThread(HeroSelectRequest::handle).add();
      net.messageBuilder(HeroSelectResult.class, 3, NetworkDirection.PLAY_TO_CLIENT)
         .decoder(HeroSelectResult::new).encoder(HeroSelectResult::toBytes).consumerMainThread(HeroSelectResult::handle).add();
      net.messageBuilder(OpenHeroSelect.class, 4, NetworkDirection.PLAY_TO_CLIENT)
         .decoder(OpenHeroSelect::new).encoder(OpenHeroSelect::toBytes).consumerMainThread(OpenHeroSelect::handle).add();
      net.messageBuilder(TutorialDone.class, 5, NetworkDirection.PLAY_TO_SERVER)
         .decoder(TutorialDone::new).encoder(TutorialDone::toBytes).consumerMainThread(TutorialDone::handle).add();
      channel = net;
   }

   private static void send(ServerPlayer player, Object message) {
      SimpleChannel net = channel;
      if (net == null || player.connection == null || !net.isRemotePresent(player.connection.connection)) {
         return;
      }
      net.send(PacketDistributor.PLAYER.with(() -> player), message);
   }

   /** Client side: false when the server has no codex channel (then nothing is sent). */
   public static boolean sendToServer(@Nullable Connection connection, Object message) {
      SimpleChannel net = channel;
      if (net == null || connection == null || !net.isRemotePresent(connection)) {
         return false;
      }
      net.sendToServer(message);
      return true;
   }

   /** Sends the owner his own record (never anyone else's). */
   public static void sync(ServerPlayer player, CodexRecord record) {
      send(player, new Snapshot(new ArrayList<>(record.heroes()), new ArrayList<>(record.interactions()), new ArrayList<>(record.entries()),
         new ArrayList<>(record.tutorials())));
   }

   /** The unlock toast for one newly opened entry. */
   public static void unlocked(ServerPlayer player, String entryId) {
      send(player, new Unlocked(entryId));
   }

   public static void heroSelectResult(ServerPlayer player, HeroSelectService.Status status, String hero, int cooldownTicks) {
      send(player, new HeroSelectResult(status.id, hero, Math.max(0, cooldownTicks)));
   }

   /** /heroselect: the client opens the hero select screen. */
   public static void openHeroSelect(ServerPlayer player) {
      send(player, new OpenHeroSelect());
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

   /** Full snapshot of the owner's progress. */
   public record Snapshot(List<String> heroes, List<String> interactions, List<String> entries, List<String> tutorials) {
      public Snapshot(FriendlyByteBuf buffer) {
         this(readList(buffer, CodexRecord.MAX_HEROES), readList(buffer, CodexRecord.MAX_INTERACTIONS), readList(buffer, CodexRecord.MAX_ENTRIES),
            readList(buffer, CodexRecord.MAX_TUTORIALS));
      }

      public void toBytes(FriendlyByteBuf buffer) {
         writeList(buffer, this.heroes, CodexRecord.MAX_HEROES);
         writeList(buffer, this.interactions, CodexRecord.MAX_INTERACTIONS);
         writeList(buffer, this.entries, CodexRecord.MAX_ENTRIES);
         writeList(buffer, this.tutorials, CodexRecord.MAX_TUTORIALS);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         CodexRecord record = new CodexRecord(this.heroes, this.interactions, this.entries, this.tutorials);
         context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.baranhan.viltrumitecore.client.codex.CodexClientState.accept(record)));
         context.get().setPacketHandled(true);
      }
   }

   /** S2C: one entry was newly opened (toast). */
   public record Unlocked(String id) {
      public Unlocked(FriendlyByteBuf buffer) {
         this(buffer.readUtf(CodexRecord.MAX_ID));
      }

      public void toBytes(FriendlyByteBuf buffer) {
         buffer.writeUtf(this.id, CodexRecord.MAX_ID);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.baranhan.viltrumitecore.client.codex.CodexClientHooks.unlocked(this.id)));
         context.get().setPacketHandled(true);
      }
   }

   /** C2S: switch to this hero; an empty hero is a status query (cooldown, permission). */
   public record HeroSelectRequest(String hero) {
      public HeroSelectRequest(FriendlyByteBuf buffer) {
         this(buffer.readUtf(CodexRecord.MAX_ID));
      }

      public void toBytes(FriendlyByteBuf buffer) {
         buffer.writeUtf(this.hero, CodexRecord.MAX_ID);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         ServerPlayer player = context.get().getSender();
         if (player != null) {
            context.get().enqueueWork(() -> HeroSelectService.handle(player, this.hero));
         }
         context.get().setPacketHandled(true);
      }
   }

   /** S2C: the server's answer; {@code cooldownTicks} is the time until the next switch is allowed. */
   public record HeroSelectResult(int status, String hero, int cooldownTicks) {
      public HeroSelectResult(FriendlyByteBuf buffer) {
         this(buffer.readVarInt(), buffer.readUtf(CodexRecord.MAX_ID), buffer.readVarInt());
      }

      public void toBytes(FriendlyByteBuf buffer) {
         buffer.writeVarInt(this.status);
         buffer.writeUtf(this.hero, CodexRecord.MAX_ID);
         buffer.writeVarInt(this.cooldownTicks);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.baranhan.viltrumitecore.client.codex.CodexClientHooks.selectResult(HeroSelectService.Status.byId(this.status), this.hero,
               this.cooldownTicks)));
         context.get().setPacketHandled(true);
      }
   }

   /** S2C: open the hero select screen (/heroselect). */
   public record OpenHeroSelect() {
      public OpenHeroSelect(FriendlyByteBuf buffer) {
         this();
      }

      public void toBytes(FriendlyByteBuf buffer) {
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.baranhan.viltrumitecore.client.codex.CodexClientHooks.openHeroSelect()));
         context.get().setPacketHandled(true);
      }
   }

   /** C2S: the first-time tutorial of this hero was finished or skipped. */
   public record TutorialDone(String hero) {
      public TutorialDone(FriendlyByteBuf buffer) {
         this(buffer.readUtf(CodexRecord.MAX_ID));
      }

      public void toBytes(FriendlyByteBuf buffer) {
         buffer.writeUtf(this.hero, CodexRecord.MAX_ID);
      }

      public void handle(Supplier<NetworkEvent.Context> context) {
         ServerPlayer player = context.get().getSender();
         if (player != null && HeroSelectService.parse(this.hero) != null) {
            context.get().enqueueWork(() -> CodexProgress.tutorialDone(player, this.hero));
         }
         context.get().setPacketHandled(true);
      }
   }
}
