package dev.baranhan.viltrumitecore.network;

import dev.baranhan.viltrumitecore.network.packet.BarrageHitS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.BarrageStateC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.BlockToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.BlockVFXS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.ChopBleedS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.ChopC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.ChopHitS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.CoreConfigSyncC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.DashToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.EquipAbilityC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.FlightConfigSyncC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.GrabToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.GrabbedPosSyncS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.HandPosSyncC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.HeroChoiceC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.HeroControlS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.HeroOwnerSnapshotS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.MeltedBlocksS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.OpenRaceScreenS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.PlayerGrabStateSyncS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.PunchC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.RaceChoiceC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.SpawnNpcC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.SpeedToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.SwapAbilityBarC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.ThunderclapC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.simple.SimpleChannel;

public class CoreMessages {
   // v3 clients clamp extra cooldowns at 16 and misread the following phase fields.
   // v4 clients lack ServerRulesS2CPacket and send the merged dash key's Dash Behind as Step Dash.
   // v11 adds HeroLineS2CPacket (hero subtitles); "8".."10" are reserved by parallel Thor branches.
   private static final String PROTOCOL_VERSION = "11";
   private static SimpleChannel INSTANCE;
   private static int packetId = 0;

   private static int id() {
      return packetId++;
   }

   public static void register() {
      SimpleChannel net = ChannelBuilder.named(new ResourceLocation("viltrumitecore", "messages"))
         .networkProtocolVersion(() -> PROTOCOL_VERSION)
         .clientAcceptedVersions(PROTOCOL_VERSION::equals)
         .serverAcceptedVersions(PROTOCOL_VERSION::equals)
         .simpleChannel();
      INSTANCE = net;
      net.messageBuilder(BlockToggleC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(BlockToggleC2SPacket::new)
         .encoder(BlockToggleC2SPacket::encode)
         .consumerMainThread(BlockToggleC2SPacket::handle)
         .add();
      net.messageBuilder(ChopC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(ChopC2SPacket::new)
         .encoder(ChopC2SPacket::encode)
         .consumerMainThread(ChopC2SPacket::handle)
         .add();
      net.messageBuilder(DashToggleC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(DashToggleC2SPacket::new)
         .encoder(DashToggleC2SPacket::encode)
         .consumerMainThread(DashToggleC2SPacket::handle)
         .add();
      net.messageBuilder(EquipAbilityC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(EquipAbilityC2SPacket::new)
         .encoder(EquipAbilityC2SPacket::encode)
         .consumerMainThread(EquipAbilityC2SPacket::handle)
         .add();
      net.messageBuilder(GrabToggleC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(GrabToggleC2SPacket::new)
         .encoder(GrabToggleC2SPacket::encode)
         .consumerMainThread(GrabToggleC2SPacket::handle)
         .add();
      net.messageBuilder(HandPosSyncC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(HandPosSyncC2SPacket::new)
         .encoder(HandPosSyncC2SPacket::encode)
         .consumerMainThread(HandPosSyncC2SPacket::handle)
         .add();
      net.messageBuilder(PunchC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(PunchC2SPacket::new)
         .encoder(PunchC2SPacket::encode)
         .consumerMainThread(PunchC2SPacket::handle)
         .add();
      net.messageBuilder(RaceChoiceC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(RaceChoiceC2SPacket::new)
         .encoder(RaceChoiceC2SPacket::encode)
         .consumerMainThread(RaceChoiceC2SPacket::handle)
         .add();
      net.messageBuilder(SpeedToggleC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(SpeedToggleC2SPacket::new)
         .encoder(SpeedToggleC2SPacket::encode)
         .consumerMainThread(SpeedToggleC2SPacket::handle)
         .add();
      net.messageBuilder(SwapAbilityBarC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(SwapAbilityBarC2SPacket::new)
         .encoder(SwapAbilityBarC2SPacket::encode)
         .consumerMainThread(SwapAbilityBarC2SPacket::handle)
         .add();
      net.messageBuilder(ThunderclapC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(ThunderclapC2SPacket::new)
         .encoder(ThunderclapC2SPacket::encode)
         .consumerMainThread(ThunderclapC2SPacket::handle)
         .add();
      net.messageBuilder(BarrageStateC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(BarrageStateC2SPacket::new)
         .encoder(BarrageStateC2SPacket::encode)
         .consumerMainThread(BarrageStateC2SPacket::handle)
         .add();
      net.messageBuilder(CoreConfigSyncC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(CoreConfigSyncC2SPacket::new)
         .encoder(CoreConfigSyncC2SPacket::toBytes)
         .consumerMainThread(CoreConfigSyncC2SPacket::handle)
         .add();
      net.messageBuilder(FlightConfigSyncC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(FlightConfigSyncC2SPacket::new)
         .encoder(FlightConfigSyncC2SPacket::toBytes)
         .consumerMainThread(FlightConfigSyncC2SPacket::handle)
         .add();
      net.messageBuilder(SpawnNpcC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(SpawnNpcC2SPacket::new)
         .encoder(SpawnNpcC2SPacket::toBytes)
         .consumerMainThread(SpawnNpcC2SPacket::handle)
         .add();
      net.messageBuilder(BlockVFXS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(BlockVFXS2CPacket::new)
         .encoder(BlockVFXS2CPacket::encode)
         .consumerMainThread(BlockVFXS2CPacket::handle)
         .add();
      net.messageBuilder(ChopBleedS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(ChopBleedS2CPacket::new)
         .encoder(ChopBleedS2CPacket::encode)
         .consumerMainThread(ChopBleedS2CPacket::handle)
         .add();
      net.messageBuilder(ChopHitS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(ChopHitS2CPacket::new)
         .encoder(ChopHitS2CPacket::encode)
         .consumerMainThread(ChopHitS2CPacket::handle)
         .add();
      net.messageBuilder(GrabbedPosSyncS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(GrabbedPosSyncS2CPacket::new)
         .encoder(GrabbedPosSyncS2CPacket::encode)
         .consumerMainThread(GrabbedPosSyncS2CPacket::handle)
         .add();
      net.messageBuilder(MeltedBlocksS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(MeltedBlocksS2CPacket::new)
         .encoder(MeltedBlocksS2CPacket::encode)
         .consumerMainThread(MeltedBlocksS2CPacket::handle)
         .add();
      net.messageBuilder(OpenRaceScreenS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(OpenRaceScreenS2CPacket::new)
         .encoder(OpenRaceScreenS2CPacket::encode)
         .consumerMainThread(OpenRaceScreenS2CPacket::handle)
         .add();
      net.messageBuilder(PlayerGrabStateSyncS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(PlayerGrabStateSyncS2CPacket::new)
         .encoder(PlayerGrabStateSyncS2CPacket::encode)
         .consumerMainThread(PlayerGrabStateSyncS2CPacket::handle)
         .add();
      net.messageBuilder(BarrageHitS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(BarrageHitS2CPacket::new)
         .encoder(BarrageHitS2CPacket::encode)
         .consumerMainThread(BarrageHitS2CPacket::handle)
         .add();
      // Appended hero packets: wire ids of every earlier packet stay unchanged.
      net.messageBuilder(HeroChoiceC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(HeroChoiceC2SPacket::new)
         .encoder(HeroChoiceC2SPacket::toBytes)
         .consumerMainThread(HeroChoiceC2SPacket::handle)
         .add();
      net.messageBuilder(HeroInputC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(HeroInputC2SPacket::new)
         .encoder(HeroInputC2SPacket::toBytes)
         .consumerMainThread(HeroInputC2SPacket::handle)
         .add();
      net.messageBuilder(HeroOwnerSnapshotS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(HeroOwnerSnapshotS2CPacket::new)
         .encoder(HeroOwnerSnapshotS2CPacket::toBytes)
         .consumerMainThread(HeroOwnerSnapshotS2CPacket::handle)
         .add();

      net.messageBuilder(HeroControlS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(HeroControlS2CPacket::new)
         .encoder(HeroControlS2CPacket::toBytes)
         .consumerMainThread(HeroControlS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket::encode)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.HeroMouseC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.HeroMouseC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.HeroMouseC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.HeroMouseC2SPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ScanCardS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ScanCardS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ScanCardS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ScanCardS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.VeronicaMenuS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.VeronicaMenuS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.VeronicaMenuS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.VeronicaMenuS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.VeronicaChooseC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.VeronicaChooseC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.VeronicaChooseC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.VeronicaChooseC2SPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.AirJumpSyncS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.AirJumpSyncS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.AirJumpSyncS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.AirJumpSyncS2CPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket::handle)
         .add();

      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindAnswerS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindAnswerS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindAnswerS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindAnswerS2CPacket::handle)
         .add();
      // Reinhard stage 4: the Reid draw scene (registered last: earlier ids stay).
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket.class,id(),NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ServerRulesS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ServerRulesS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ServerRulesS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ServerRulesS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket.class,id(),NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.JinwooFxS2CPacket::handle).add();
   }

   public static <MSG> void sendToServer(MSG message) {
      INSTANCE.sendToServer(message);
   }

   public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
      INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
   }

   public static <MSG> void sendToTracking(MSG message, Entity entity) {
      INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
   }

   public static <MSG> void sendToTrackingAndSelf(MSG message, Entity entity) {
      INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
   }

   public static <MSG> void sendToAll(MSG message) {
      INSTANCE.send(PacketDistributor.ALL.noArg(), message);
   }
}
