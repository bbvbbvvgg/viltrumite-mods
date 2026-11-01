package dev.baranhan.viltrumitecore.worldevent;

import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.meteor.MeteorRain;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Shared seam: which world events run now. A start command refuses while another big event
 * runs, unless the operator gives {@code force} (explicit permission). Only the Chitauri
 * invasion and the Ultron uprising are exclusive: trio/conquest squads, meteor rain and the
 * Thanos event keep their old relation to each other, and each of them refuses to start over
 * the invasion or the uprising (and those two over each of them and over each other).
 */
public final class WorldEventConflicts {
   private WorldEventConflicts() {
   }

   /** Names of the running events other than the Chitauri invasion (for /chitauri start and /ultron start). */
   public static List<Component> othersThanInvasion(ServerLevel level) {
      List<Component> out = new ArrayList<>();
      for (ServerLevel any : level.getServer().getAllLevels()) {
         for (WorldEvent event : WorldEventManager.get(any).activeEvents()) {
            if (!event.isOver()) {
               out.add(Component.literal(event.type().id()));
            }
         }
      }
      if (!MeteorRain.sessions().isEmpty()) {
         out.add(Component.translatable("viltrumitecore.chitauri.conflict.meteor"));
      }
      if (ThanosEvent.running()) {
         out.add(Component.translatable("entity.viltrumitecore.thanos"));
      }
      if (UltronUprising.running(level.getServer())) {
         out.add(Component.translatable(UltronUprising.CONFLICT_KEY));
      }
      return out;
   }

   /**
    * The exclusive events, when they run: the invasion and the Ultron uprising (for /event start,
    * /meteor start, /thanos start and /ultron start).
    */
   public static List<Component> invasion(ServerLevel level) {
      List<Component> out = new ArrayList<>();
      if (ChitauriInvasion.running(level.getServer())) {
         out.add(Component.translatable("viltrumitecore.chitauri.conflict.invasion"));
      }
      if (UltronUprising.running(level.getServer())) {
         out.add(Component.translatable(UltronUprising.CONFLICT_KEY));
      }
      return out;
   }

   /** Pure: a start may go ahead when nothing conflicts or the operator forced it. */
   public static boolean mayStart(int conflicts, boolean force) {
      return conflicts == 0 || force;
   }
}
