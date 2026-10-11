package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.network.packet.HeroControlS2CPacket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owner-private hero data mirrored on the client (heart carrier ids for the
 * heart counter), plus the shared world-control view (domes + anchored
 * targets) written by HeroControlS2CPacket for render and HUD consumers.
 *
 * Entity ids and expiry times are per world: everything is dropped on log out
 * and on a new local player (respawn, dimension change), else old ids light up
 * unrelated entities of the next world (ESP boxes on random mobs).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
   modid = "viltrumitecore",
   value = {net.minecraftforge.api.distmarker.Dist.CLIENT},
   bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientHeroData {
   private static final dev.baranhan.viltrumitecore.hero.OwnerSections SECTIONS = new dev.baranhan.viltrumitecore.hero.OwnerSections();
   private static Map<UUID, HeroControlS2CPacket.DomeInfo> domes = Map.of();
   private static Map<UUID, HeroControlS2CPacket.ControlInfo> controls = Map.of();

   private ClientHeroData() {
   }

   public static void setCarriers(int[] ids) {
      setSection(dev.baranhan.viltrumitecore.hero.OwnerSection.CARRIERS, dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot.Section.of(ids == null ? new int[0] : ids));
   }

   /** Replaces one owner section (expiry counts from the client game time now). */
   public static void setSection(dev.baranhan.viltrumitecore.hero.OwnerSection section, dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot.Section value) {
      SECTIONS.set(section, value, now());
   }

   /** Live ids of an owner section. */
   public static int[] section(dev.baranhan.viltrumitecore.hero.OwnerSection section) {
      return SECTIONS.ids(section, now());
   }

   /** Is {@code id} live in the section now; no allocation (per-entity render paths). */
   public static boolean inSection(dev.baranhan.viltrumitecore.hero.OwnerSection section, int id) {
      return SECTIONS.contains(section, id, now());
   }

   /** Drops all owner sections (world change). The control view is resent by the server. */
   public static void clearSections() {
      SECTIONS.clear();
   }

   @net.minecraftforge.eventbus.api.SubscribeEvent
   public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
      clearSections();
      domes = Map.of();
      controls = Map.of();
   }

   @net.minecraftforge.eventbus.api.SubscribeEvent
   public static void onClone(net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone event) {
      clearSections();
   }

   public static int[] carriers() {
      return section(dev.baranhan.viltrumitecore.hero.OwnerSection.CARRIERS);
   }

   public static int heartCount() {
      return carriers().length;
   }

   private static long now() {
      net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
      return client.level == null ? 0L : client.level.getGameTime();
   }

   /** Replaces the whole control view (full snapshot for the current dimension). */
   public static void setControlSnapshot(List<HeroControlS2CPacket.DomeInfo> domeList, List<HeroControlS2CPacket.ControlInfo> controlList) {
      Map<UUID, HeroControlS2CPacket.DomeInfo> domeMap = new java.util.HashMap<>();
      for (HeroControlS2CPacket.DomeInfo dome : domeList) {
         domeMap.put(dome.id(), dome);
      }

      Map<UUID, HeroControlS2CPacket.ControlInfo> controlMap = new java.util.HashMap<>();
      for (HeroControlS2CPacket.ControlInfo control : controlList) {
         controlMap.put(control.target(), control);
      }

      domes = Map.copyOf(domeMap);
      controls = Map.copyOf(controlMap);
   }

   /** Domes in the client's current dimension. */
   public static Collection<HeroControlS2CPacket.DomeInfo> domes() {
      return domes.values();
   }

   /** Live control records keyed by target UUID. */
   public static Collection<HeroControlS2CPacket.ControlInfo> controls() {
      return controls.values();
   }

   /** The control record anchored on this entity, if any. */
   public static HeroControlS2CPacket.ControlInfo controlOf(UUID targetId) {
      return controls.get(targetId);
   }
}
