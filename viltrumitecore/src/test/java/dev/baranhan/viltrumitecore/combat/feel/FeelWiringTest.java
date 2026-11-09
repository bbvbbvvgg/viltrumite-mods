package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.network.packet.FeelS2CPacket;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Wire format and the seams the cinematic combat feel plugs into (wave1-22). */
class FeelWiringTest {
   private static final String MAIN = "../viltrumitecore/src/main/";
   private static final String JAVA = MAIN + "java/dev/baranhan/viltrumitecore/";

   private static String read(String path) throws Exception {
      return Files.readString(Path.of(path));
   }

   @Test
   void packetRoundTrip() {
      FeelS2CPacket sent = FeelS2CPacket.shake(new Vec3(1.5, 64.25, -9.75), ShakeProfile.EXPLOSION, 0.7F, 40.0);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      sent.encode(buf);
      FeelS2CPacket got = new FeelS2CPacket(buf);
      assertEquals(0, buf.readableBytes(), "decoder must consume exactly what the encoder wrote");
      assertEquals(FeelS2CPacket.SHAKE, got.kind);
      assertEquals(ShakeProfile.EXPLOSION, ShakeProfile.byId(got.profile));
      assertEquals(new Vec3(1.5, 64.25, -9.75), got.at);
      assertEquals(0.7F, got.power, 0.0F);
      assertEquals(40.0F, got.range, 0.0F);

      FeelS2CPacket wave = FeelS2CPacket.wave(Vec3.ZERO, 1.0F, 14);
      buf = new FriendlyByteBuf(Unpooled.buffer());
      wave.encode(buf);
      FeelS2CPacket waveBack = new FeelS2CPacket(buf);
      assertEquals(FeelS2CPacket.WAVE, waveBack.kind);
      assertEquals(14, waveBack.duration);
   }

   @Test
   void hostileValuesAreClamped() {
      FeelS2CPacket bad = new FeelS2CPacket(FeelS2CPacket.IMPACT, 999, new Vec3(Double.NaN, 0, 0), Float.NaN, 1.0E9F, 1_000_000);
      assertEquals(0.0F, bad.power, 0.0F);
      assertEquals(FeelS2CPacket.MAX_RANGE, bad.range, 0.0F);
      assertEquals(FeelS2CPacket.MAX_DURATION, bad.duration);
      assertEquals(Vec3.ZERO, bad.at);
      assertTrue(bad.profile >= 0);
      FeelS2CPacket loud = new FeelS2CPacket(FeelS2CPacket.SHAKE, 0, Vec3.ZERO, 50.0F, -3.0F, -3);
      assertEquals(FeelS2CPacket.MAX_POWER, loud.power, 0.0F);
      assertEquals(0.0F, loud.range, 0.0F);
      assertEquals(0, loud.duration);
   }

   @Test
   void packetIsRegisteredLastUnderItsOwnProtocol() throws Exception {
      String messages = read(JAVA + "network/CoreMessages.java");
      int scp = messages.indexOf("Scp106S2CPacket.class, id()");
      int feel = messages.indexOf("FeelS2CPacket.class, id()");
      assertTrue(scp > 0 && feel > scp, "append-only packet table: FeelS2CPacket goes after Scp106S2CPacket");
      assertTrue(messages.contains("PROTOCOL_VERSION = \"32\""));
   }

   @Test
   void legacyShakeKeepsItsExactFormulaButNoLongerOwnsTheCamera() throws Exception {
      String shake = read(JAVA + "client/render/vfx/CameraShake.java");
      assertFalse(shake.contains("onCameraAngles"), "CameraFeel is the single ComputeCameraAngles owner");
      assertTrue(shake.contains("public static boolean legacyOffsets(double partialTick, float[] out)"));
      assertTrue(shake.contains("amp * 1.6F * (Mth.sin(t * 2.9F) * 0.6F + Mth.sin(t * 5.3F + 1.3F) * 0.4F)"));
      assertTrue(shake.contains("amp * 1.3F * (Mth.sin(t * 3.7F + 0.7F) * 0.6F + Mth.sin(t * 6.1F + 2.1F) * 0.4F)"));
      assertTrue(shake.contains("amp * 1.1F * Mth.sin(t * 4.3F + 0.4F)"));
      String feel = read(JAVA + "client/cinematic/feel/CameraFeel.java");
      assertTrue(feel.contains("CameraShake.legacyOffsets("));
      assertTrue(feel.contains("ViewportEvent.ComputeCameraAngles"));
   }

   @Test
   void ringsRideTheSharedDashImpactPass() throws Exception {
      String mixin = read(JAVA + "client/mixin/GameRendererDashMixin.java");
      assertTrue(mixin.contains("ShockwaveDistortion.post(partialTicks)"));
      assertTrue(mixin.contains("feelWaves[3] + feelWaves[7] > 0.001F"), "rings must be part of the early-out");
      assertTrue(mixin.contains("getUniform(\"FeelWaveA\")") && mixin.contains("getUniform(\"FeelWaveB\")"));
      String program = read(MAIN + "resources/assets/minecraft/shaders/program/dash_impact.json");
      assertTrue(program.contains("\"FeelWaveA\"") && program.contains("\"FeelWaveB\""));
      String fsh = read(MAIN + "resources/assets/minecraft/shaders/program/dash_impact.fsh");
      assertTrue(fsh.contains("uniform vec4 FeelWaveA;") && fsh.contains("uniform vec4 FeelWaveB;"));
      assertTrue(fsh.contains("FeelWaveA.w") && fsh.contains("vec3 feelWave("));
      assertFalse(Files.exists(Path.of(MAIN + "resources/assets/viltrumitecore/shaders/post/cinematic_shockwave.json")), "no second post chain");
   }
}
