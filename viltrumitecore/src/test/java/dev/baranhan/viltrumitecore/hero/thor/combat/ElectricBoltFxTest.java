package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** B-m6: Thor's lightning is a client-drawn FX, never a LightningBolt entity in the world. */
class ElectricBoltFxTest {
   @Test
   void noThorCodeSpawnsALightningBoltEntity() throws IOException {
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor");
      try (Stream<Path> files = Files.walk(root)) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            String text = Files.readString(file);
            assertFalse(text.contains("EntityType.LIGHTNING_BOLT"), file + " spawns a vanilla bolt");
            assertFalse(text.contains("new LightningBolt("), file + " spawns a vanilla bolt");
         }
      }
   }

   @Test
   void boltPacketRoundTripsCompactly() {
      HeroFxS2CPacket sent = new HeroFxS2CPacket(HeroFxS2CPacket.ELECTRIC_BOLT, new Vec3(1.5, 64.0, -3.5), 1.0F, 0, new float[]{40.0F, 0.0F, 0.0F}, -1);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      sent.encode(buf);
      int size = buf.readableBytes();
      HeroFxS2CPacket read = new HeroFxS2CPacket(buf);
      assertEquals(HeroFxS2CPacket.ELECTRIC_BOLT, read.kind);
      assertEquals(new Vec3(1.5, 64.0, -3.5), read.origin);
      assertEquals(40.0F, read.points[0]);
      assertTrue(size <= 48, "one bolt is a few dozen bytes, was " + size);
   }

   @Test
   void hostileHeightsAndPowersAreClamped() {
      assertEquals(HeroFx.BOLT_MAX_HEIGHT, HeroFx.clampBoltHeight(1.0E9F));
      assertEquals(HeroFx.BOLT_MAX_HEIGHT, HeroFx.clampBoltHeight(Float.NaN));
      assertEquals(2.0F, HeroFx.clampBoltHeight(-5.0F));
      assertEquals(2.0F, HeroFx.clampBoltPower(100.0F));
      assertEquals(1.0F, HeroFx.clampBoltPower(Float.POSITIVE_INFINITY));
      assertEquals(0.25F, HeroFx.clampBoltPower(0.0F));
   }
}
