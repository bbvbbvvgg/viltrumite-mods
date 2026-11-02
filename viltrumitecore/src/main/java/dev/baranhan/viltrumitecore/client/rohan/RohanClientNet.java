package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.network.packet.RohanBookS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.RohanFxS2CPacket;

/** Client entry of the Rohan packets. */
public final class RohanClientNet {
   private RohanClientNet() {
   }

   public static void receiveBook(RohanBookS2CPacket packet) {
      RohanBookClient.receive(packet);
   }

   public static void receiveFx(RohanFxS2CPacket packet) {
      RohanFx.receive(packet);
   }
}
