package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Saw Sphere start (overhaul item 19): the whole scene in one packet — caster, centre (feet at
 * activation, Y0), anchor yaw, the scene age when sent (late viewers join mid-scene), the cell
 * count both sides enumerate from the centre and the barrier's activity bits. Saws, spin and
 * timing are derived from {@link SawChoreography}; no per-block or per-frame traffic.
 */
public record SawSphereS2CPacket(int casterId, double x, double y0, double z, float anchorYaw, int age, int cellCount, long[] mask) {
   /** 32 768 longs = 2 M cells, far above the ~10 k of a 12-block shell. */
   public static final int MAX_MASK = 32768;

   public SawSphereS2CPacket {
      if (!Double.isFinite(x) || !Double.isFinite(y0) || !Double.isFinite(z) || !Float.isFinite(anchorYaw) || age < 0
         || age > SawChoreography.LENGTH || cellCount < 0 || mask == null || mask.length > MAX_MASK) {
         throw new IllegalArgumentException("saw sphere");
      }
      mask = mask.clone();
   }

   public SawSphereS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt(), b.readVarInt(), readMask(b));
   }

   private static long[] readMask(FriendlyByteBuf b) {
      int n = b.readVarInt();
      if (n < 0 || n > MAX_MASK) throw new IllegalArgumentException("saw sphere mask");
      long[] out = new long[n];
      for (int i = 0; i < n; i++) out[i] = b.readLong();
      return out;
   }

   public SawSphereS2CPacket withAge(int now) {
      return new SawSphereS2CPacket(this.casterId, this.x, this.y0, this.z, this.anchorYaw, Math.max(0, Math.min(SawChoreography.LENGTH, now)), this.cellCount, this.mask);
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeVarInt(this.casterId);
      b.writeDouble(this.x);
      b.writeDouble(this.y0);
      b.writeDouble(this.z);
      b.writeFloat(this.anchorYaw);
      b.writeVarInt(this.age);
      b.writeVarInt(this.cellCount);
      b.writeVarInt(this.mask.length);
      for (long word : this.mask) b.writeLong(word);
   }

   @Override
   public long[] mask() {
      return this.mask.clone();
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof SawSphereS2CPacket p && p.casterId == this.casterId && p.x == this.x && p.y0 == this.y0 && p.z == this.z
         && p.anchorYaw == this.anchorYaw && p.age == this.age && p.cellCount == this.cellCount && Arrays.equals(p.mask, this.mask);
   }

   @Override
   public int hashCode() {
      return Integer.hashCode(this.casterId) * 31 + Arrays.hashCode(this.mask);
   }

   public void handle(Supplier<NetworkEvent.Context> c) {
      c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.reinhard.saw.SawSphereClient.start(this)));
      c.get().setPacketHandled(true);
   }
}
