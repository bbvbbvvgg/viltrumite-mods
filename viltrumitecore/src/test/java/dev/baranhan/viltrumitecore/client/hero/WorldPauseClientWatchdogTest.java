package dev.baranhan.viltrumitecore.client.hero;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene;
import dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorldPauseClientWatchdogTest {
   UUID id=UUID.randomUUID(),owner=UUID.randomUUID();
   AtomicInteger begin(int left){WorldPauseClient.accept(new WorldPauseS2CPacket(false,id,owner,0));var ended=new AtomicInteger();WorldPauseClient.onEnd(ended::incrementAndGet);WorldPauseClient.accept(new WorldPauseS2CPacket(true,id,owner,left));return ended;}
   @Test void realGamePauseAgesNeitherWatchdogNorSceneClock(){var ended=begin(145);var clock=new ErasureScene.Clock(45,100);for(int i=0;i<1000;i++)WorldPauseClient.tick(true);assertTrue(WorldPauseClient.active());assertEquals(145,WorldPauseClient.remainingTicks());assertEquals(0,ended.get());assertEquals(45,clock.at(100));for(int i=0;i<3;i++)WorldPauseClient.tick(false);assertEquals(142,WorldPauseClient.remainingTicks());assertEquals(48,clock.at(103));assertEquals(0,ended.get());}
   @Test void runningClientStillHasBoundedCleanup(){var ended=begin(3);WorldPauseClient.tick(false);WorldPauseClient.tick(false);assertTrue(WorldPauseClient.active());assertEquals(1,WorldPauseClient.remainingTicks());WorldPauseClient.tick(false);assertFalse(WorldPauseClient.active());assertEquals(1,ended.get());for(int i=0;i<100;i++)WorldPauseClient.tick(false);assertEquals(1,ended.get());}
   @Test void catchupKeepsTheSameSceneAndStaleEndCannotClearIt(){var ended=begin(145);for(int i=0;i<1000;i++)WorldPauseClient.tick(true);WorldPauseClient.accept(new WorldPauseS2CPacket(true,id,owner,145));WorldPauseClient.accept(new WorldPauseS2CPacket(false,UUID.randomUUID(),owner,0));assertTrue(WorldPauseClient.active());assertEquals(145,WorldPauseClient.remainingTicks());assertEquals(0,ended.get());WorldPauseClient.accept(new WorldPauseS2CPacket(false,id,owner,0));assertEquals(1,ended.get());}
   @Test void liveCallbackUsesTheSameGamePauseClock() throws Exception{String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/client/hero/WorldPauseClient.java"));assertTrue(s.contains("tick(mc.isPaused())"));}
}
