package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Task 3 packet receiver. Task 4 supplies time-driven visuals over these fixed frames. */
public final class WorldCutScene {
   static final Map<UUID,WorldCutS2CPacket> SCENES=new LinkedHashMap<>();
   private WorldCutScene() {}
   public static void onPacket(WorldCutS2CPacket packet) { SCENES.put(packet.castId(),packet); }
}
