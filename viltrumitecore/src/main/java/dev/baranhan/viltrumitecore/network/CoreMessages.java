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
   // v10 (Thor round 3): HeroFx ELECTRIC_BOLT, the dash config sync and the scene contract changes; the
   // Thor move table is checked separately by the viltrumitecore:thor_moves channel (its fingerprint).
   // v11 adds HeroLineS2CPacket (hero subtitles). "12" = Thor + Hulkbuster build (#74) alone,
   // "13" = integration/current-heroes (#82: DIO, Sinclair, Jinwoo) alone.
   // v14 (integration/all-heroes-final): #74 + #82 together; Thor/Hulkbuster packets first, then
   // HeroLineS2CPacket and the DIO / Jinwoo packets.
   // "15" was claimed by three parallel branches with three different tables: #86 (Thor GUARD bit in
   // the Thor snapshot), #88 (Od Lagna's wish packets) and #89 (Regulus Reborn: RegulusFxS2CPacket
   // replaces the first kit's packets). v16 (final integration of #85-#92): all of them together;
   // the wish packets first, then RegulusFxS2CPacket, both after the Jinwoo packet. A client built
   // from any single "15" branch is refused.
   // v17 (feat/captain-america-2012): CaptainFxS2CPacket appended after RegulusFxS2CPacket.
   // v19 (Thanos city event, after main's v18): ThanosFxS2CPacket and ThanosStateS2CPacket appended
   // after CaptainFxS2CPacket. The Thanos branch's own "17" table is refused.
   // v18 on feat/chitauri-invasion alone had the Chitauri packets right after RegulusFxS2CPacket; that
   // table is refused. v20 (final integration of #101/#99/#98): the existing table is kept as is, then
   // ChitauriPortalS2CPacket and ChitauriFxS2CPacket, then the four Iron Legion packets, are appended last.
   // The Iron Legion branch's own "17" table (Legion packets right after Regulus) is refused.
   // v21 was used twice, by two branches alone: Living City 2.0 (#110, citizen entity syncs a new STATE byte)
   // and Reinhard's THE FINAL PUNCH (#109, C2S chord + S2C beats appended after the Legion packets).
   // v22 (final integration of #107/#108/#110/#109): both changes together; a "21" client is refused.
   // v23 (Red Hulk, feat/red-hulk): six HeroAction values appended after FINAL_PUNCH and the hero id red_hulk;
   // the packet table itself is unchanged, but a "22" peer would decode the new action ordinals wrongly.
   // v23 (Meteor Rain 2.0): MeteorRainFxS2CPacket appended last (red sky, fire zones, brands, far impacts).
   // v23 (Chitauri evolution): FX kinds 14..23 for captors, pylons, siege, interceptors; new entity types.
   // v23 (feat/ultron-uprising): UltronFxS2CPacket and UltronStateS2CPacket appended after the Final Punch packets.
   // v24 (final integration of #113/#114/#115 + the Ultron uprising): all four v23 changes together, one table:
   // ... FINAL_PUNCH packets, MeteorRainFxS2CPacket, UltronFxS2CPacket, UltronStateS2CPacket; the six Red Hulk
   // HeroAction values and the Chitauri FX kinds 14..23. Every "23" peer (each branch alone) is refused.
   // v24 (Living City 3.0 infrastructure, feat/city-infrastructure-disasters): CityPowerS2CPacket appended last;
   //     new blocks dark_fixture / dark_lantern / dark_end_rod (registry sync refuses a mismatch anyway).
   // v26 (structural collapse, feat/city-structural-collapse): CityCollapseS2CPacket appended last; 24/25 are left
   // to the parallel branches of the same integration round.
   // v27 (integration of #124/#125/#126/#127): one table with CityPowerS2CPacket, CityCollapseS2CPacket and
   //     AftermathS2CPacket appended in that order after the Ultron packets. Every "24" and "26" peer is refused.
   // v28 (feat/godzilla-city-catastrophe): GodzillaFxS2CPacket appended last after AftermathS2CPacket; a "27"
   //     peer is refused.
   private static final String PROTOCOL_VERSION = "28";
   private static SimpleChannel INSTANCE;
   private static int packetId = 0;

   private static int id() {
      return packetId++;
   }

   /** Channel whose version is the Thor move-table fingerprint (ThorMoves.fingerprint). */
   public static final ResourceLocation THOR_MOVES_CHANNEL = new ResourceLocation("viltrumitecore", "thor_moves");
   private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("viltrumitecore/network");
   @javax.annotation.Nullable
   private static SimpleChannel thorMoves;

   /**
    * Foundation M2: after every session registered its Thor moves (common
    * setup), a carrier channel advertises the move-table fingerprint. Forge
    * compares it in the login handshake: a client and server with different
    * move tables are refused before play, the disconnect screen names
    * {@code viltrumitecore:thor_moves}, and both logs name both fingerprints.
    * Forge reads the version once when the channel is built, so this must run
    * after the last ThorMoves.register.
    */
   public static void registerThorMoveHandshake() {
      if (thorMoves != null) {
         return;
      }

      String local = dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves.fingerprint();
      thorMoves = ChannelBuilder.named(THOR_MOVES_CHANNEL)
         .networkProtocolVersion(() -> local)
         .clientAcceptedVersions(remote -> acceptsThorMoves("server", local, remote))
         .serverAcceptedVersions(remote -> acceptsThorMoves("client", local, remote))
         .simpleChannel();
   }

   static boolean acceptsThorMoves(String side, String local, String remote) {
      boolean ok = dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves.compatible(local, remote);
      if (!ok && isProbe(remote)) {
         // Forge asks every channel about ABSENT / ACCEPTVANILLA for each status ping
         // (server: every 5 s, client: server list). That is a probe, not a peer.
         LOGGER.debug("Thor move table: {} probe {} refused", side, remote);
      } else if (!ok) {
         LOGGER.error("Thor move table mismatch: local {} vs {} {}; client and server must run the same viltrumitecore build", local, side, remote);
      }

      return ok;
   }

   /** Forge's placeholder versions for "no channel" and "vanilla peer" (status pings, server list). */
   static boolean isProbe(String remote) {
      return net.minecraftforge.network.NetworkRegistry.ABSENT.version().equals(remote)
         || net.minecraftforge.network.NetworkRegistry.ACCEPTVANILLA.equals(remote);
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
      // Thor session C: scene sync and scene input.
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket::handle).add();
      // Hulkbuster rework (protocol 12): movement requests and refusals.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionC2SPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionS2CPacket::handle).add();
      // Thor integration: a refused, already predicted dash is stopped on the client.
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorDashS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorDashS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorDashS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorDashS2CPacket::handle).add();
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
      // Od Lagna's Wish (docs/design/2026-10-30-od-lagna-wish-design.md): appended, ids of older packets unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.WishChoiceC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.WishChoiceC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.WishChoiceC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.WishChoiceC2SPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket::handle).add();
      // Regulus Reborn (#89): one FX packet for every Regulus event; appended after the wish packets.
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket::handle)
         .add();
      // Captain America 2012 (v17/v18): one FX packet for Steve, the shield and the summoned Avengers; after Regulus.
      net.messageBuilder(dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.hero.captain.net.CaptainFxS2CPacket::handle)
         .add();
      // Thanos city event (v19): his effects and the event state (HUD, music, scenes); appended last.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket::handle)
         .add();
      // Chitauri invasion (v20, after Thanos): the portal state, then one FX packet for every invasion effect.
      net.messageBuilder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket::handle)
         .add();
      net.messageBuilder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket::handle)
         .add();
      // Iron Man stage 6, the Iron Legion (docs/design/2026-10-31-ironman-legion-design.md), v20: appended after the Chitauri packets, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket::handle).add();
      // Reinhard's secret THE FINAL PUNCH (docs/design/2026-10-31-reinhard-final-punch.md), v21: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchC2SPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchC2SPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchC2SPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket::handle).add();
      // Meteor Rain 2.0, v23: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket::encode)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket::handle).add();
      // ULTRON — ВОССТАНИЕ МАШИН (docs/plans/2026-11-01-ultron-uprising.md), v23: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket::handle).add();
      net.messageBuilder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket::handle).add();
      // Living City 3.0 infrastructure (power grid, blackouts, city emergency), v24: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket::encode)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket::handle).add();
      // Structural collapse (docs/city-collapse-integration.md), v26: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket::encode)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket::handle).add();
      // City disaster aftermath, v24: appended last, older ids unchanged.
      net.messageBuilder(dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket::encode)
         .consumerMainThread(dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket::handle).add();
      // ГОДЗИЛЛА — ГОРОДСКАЯ КАТАСТРОФА (docs/plans/2026-11-05-godzilla-city-catastrophe.md), v28: appended last.
      net.messageBuilder(dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket::new)
         .encoder(dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket::toBytes)
         .consumerMainThread(dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket::handle).add();
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
