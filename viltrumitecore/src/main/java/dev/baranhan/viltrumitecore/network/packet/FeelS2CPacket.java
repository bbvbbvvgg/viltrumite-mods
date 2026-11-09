package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.cinematic.feel.ClientFeel;
import dev.baranhan.viltrumitecore.combat.feel.ShakeProfile;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Cinematic combat feel cue (wave1-22): camera shake, impact frame or air distortion ring at a
 * world point. Values are clamped on both ends so a bad peer cannot request a blinding or endless
 * effect. Wire: kind, profile, x, y, z, power, range, varint duration.
 */
public final class FeelS2CPacket {
   public static final byte SHAKE = 0;
   public static final byte IMPACT = 1;
   public static final byte WAVE = 2;

   public static final float MAX_POWER = 4.0F;
   public static final float MAX_RANGE = 256.0F;
   public static final int MAX_DURATION = 200;

   public final byte kind;
   public final byte profile;
   public final Vec3 at;
   public final float power;
   public final float range;
   public final int duration;

   public FeelS2CPacket(byte kind, int profile, Vec3 at, float power, float range, int duration) {
      this.kind = kind;
      this.profile = (byte)Mth.clamp(profile, 0, 127);
      this.at = at == null || !Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z) ? Vec3.ZERO : at;
      this.power = power > 0.0F ? Math.min(MAX_POWER, power) : 0.0F;
      this.range = range > 0.0F ? Math.min(MAX_RANGE, range) : 0.0F;
      this.duration = Mth.clamp(duration, 0, MAX_DURATION);
   }

   public FeelS2CPacket(FriendlyByteBuf buf) {
      this(buf.readByte(), buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readVarInt());
   }

   public static FeelS2CPacket shake(Vec3 at, ShakeProfile profile, float trauma, double range) {
      return new FeelS2CPacket(SHAKE, profile == null ? 0 : profile.ordinal(), at, trauma, (float)range, 0);
   }

   public static FeelS2CPacket impact(Vec3 at, float strength, double range) {
      return new FeelS2CPacket(IMPACT, 0, at, strength, (float)range, 0);
   }

   public static FeelS2CPacket wave(Vec3 at, float strength, int ticks) {
      return new FeelS2CPacket(WAVE, 0, at, strength, 0.0F, ticks);
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeByte(this.profile);
      buf.writeDouble(this.at.x);
      buf.writeDouble(this.at.y);
      buf.writeDouble(this.at.z);
      buf.writeFloat(this.power);
      buf.writeFloat(this.range);
      buf.writeVarInt(this.duration);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFeel.handle(this)));
      context.get().setPacketHandled(true);
   }
}
