package dev.baranhan.viltrumitecore.hero.fx;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server API for one-shot hero world FX. Every player who tracks the source
 * (and the source itself) receives the packet; the client side is
 * {@code client.render.vfx.HeroImpactFx}. Gameplay never waits for FX: resolve
 * damage and blocks first, then call one of these.
 */
public final class HeroFx {
   private HeroFx() {
   }

   /** Visible flying shards from origin to each end point (flat xyz triples, max 64), with an optional pass-by whiz. */
   public static void shards(ServerPlayer source, Vec3 origin, float power, @Nullable BlockState material, float[] ends, @Nullable SoundEvent whiz) {
      send(source, HeroFxS2CPacket.SHARDS, origin, power, material, ends, whiz);
   }

   /** Expanding ground ring + dust + camera shake (landings). Power 0..1+. */
   public static void shockwave(ServerPlayer source, Vec3 origin, float power, @Nullable BlockState material, float radius) {
      send(source, HeroFxS2CPacket.SHOCKWAVE, origin, power, material, new float[]{radius, 0.0F, 0.0F}, null);
   }

   /** Heavy impact: double ring, white flash, lots of dust, strong shake. */
   public static void slam(ServerPlayer source, Vec3 origin, @Nullable BlockState material, float radius) {
      send(source, HeroFxS2CPacket.SLAM, origin, 1.0F, material, new float[]{radius, 0.0F, 0.0F}, null);
   }

   /** A thin bright blade streak from origin to end, with an optional pass-by whiz. */
   public static void blade(ServerPlayer source, Vec3 origin, Vec3 end, @Nullable SoundEvent whiz) {
      send(source, HeroFxS2CPacket.BLADE, origin, 1.0F, null, new float[]{(float)end.x, (float)end.y, (float)end.z}, whiz);
   }

   /** Ground puff ring under a launch (super jump, take-off). */
   public static void launch(ServerPlayer source, Vec3 feet, float power, @Nullable BlockState material) {
      send(source, HeroFxS2CPacket.LAUNCH, feet, power, material, null, null);
   }

   /** Short white flash + medium shake (a hit that sends someone flying). */
   public static void flash(ServerPlayer source, Vec3 at) {
      send(source, HeroFxS2CPacket.FLASH, at, 1.0F, null, null, null);
   }

   /** Detached effects have no tracking source (offline caster, environment hazard). */
   public static void flash(net.minecraft.server.level.ServerLevel level, Vec3 at) {
      HeroFxS2CPacket packet = new HeroFxS2CPacket(HeroFxS2CPacket.FLASH, at, 1, 0, null, -1);
      for (ServerPlayer viewer : level.players())
         if (viewer.position().distanceToSqr(at) <= 96 * 96) CoreMessages.sendToPlayer(packet, viewer);
   }

   /** Hit-stop (spec §9.3): the listed entities hold still on every client for {@code ticks}. */
   public static void hitStop(ServerPlayer source, Vec3 at, int ticks, int... entityIds) {
      send(source, HeroFxS2CPacket.HIT_STOP, at, ticks, null, encodeIds(entityIds), null);
   }

   /** A white sonic ring at a kick / throw contact (spec §9.3). */
   public static void sonicRing(ServerPlayer source, Vec3 at) {
      send(source, HeroFxS2CPacket.SONIC_RING, at, 1.0F, null, null, null);
   }

   /** A white air trail behind a launched entity (spec §9.3); the client decides its length. */
   public static void airTrail(ServerPlayer source, Entity target) {
      send(source, HeroFxS2CPacket.AIR_TRAIL, target.position(), 1.0F, null, encodeIds(target.getId()), null);
   }

   /** Pure: entity ids as packet points, one {hi16, lo16, 0} triple each (floats hold 16-bit halves exactly). */
   public static float[] encodeIds(int... ids) {
      float[] out = new float[ids.length * 3];
      for (int i = 0; i < ids.length; i++) {
         out[i * 3] = (ids[i] >>> 16) & 0xFFFF;
         out[i * 3 + 1] = ids[i] & 0xFFFF;
      }

      return out;
   }

   /** Pure: the inverse of {@link #encodeIds}. */
   public static int[] decodeIds(float[] points) {
      int[] out = new int[points.length / 3];
      for (int i = 0; i < out.length; i++) {
         out[i] = ((int) points[i * 3] & 0xFFFF) << 16 | ((int) points[i * 3 + 1] & 0xFFFF);
      }

      return out;
   }

   private static void send(ServerPlayer source, byte kind, Vec3 origin, float power, @Nullable BlockState material, @Nullable float[] points, @Nullable SoundEvent sound) {
      int stateId = material == null ? 0 : Block.getId(material);
      int soundId = sound == null ? -1 : BuiltInRegistries.SOUND_EVENT.getId(sound);
      CoreMessages.sendToTrackingAndSelf(new HeroFxS2CPacket(kind, origin, power, stateId, points, soundId), source);
   }
}
