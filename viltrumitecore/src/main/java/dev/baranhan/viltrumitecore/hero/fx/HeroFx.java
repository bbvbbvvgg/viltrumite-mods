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
      flash(source,at,1.0F);
   }

   /** Compact contact flash; power controls size/shake without a ground wave or block damage. */
   public static void flash(ServerPlayer source, Vec3 at, float power) {
      send(source, HeroFxS2CPacket.FLASH, at, Math.max(.1F,Math.min(2,power)), null, null, null);
   }

   /** Detached effects have no tracking source (offline caster, environment hazard). */
   public static void flash(net.minecraft.server.level.ServerLevel level, Vec3 at) {
      HeroFxS2CPacket packet = new HeroFxS2CPacket(HeroFxS2CPacket.FLASH, at, 1, 0, null, -1);
      for (ServerPlayer viewer : level.players())
         if (viewer.position().distanceToSqr(at) <= 96 * 96) CoreMessages.sendToPlayer(packet, viewer);
   }

   /** Radius in which level-anchored electric FX are sent (the vanilla particle range is 32; sparks are small). */
   public static final double ELECTRIC_FX_RANGE = 48.0;

   /**
    * A jagged electric arc from {@code from} to {@code to}, drawn by each
    * client from one packet (Thor B-M8: was one particle packet per spark
    * per viewer). The wire cost is one packet per viewer per arc.
    */
   public static void electricArc(net.minecraft.server.level.ServerLevel level, Vec3 from, Vec3 to) {
      sendNear(level, from.lerp(to, 0.5), new HeroFxS2CPacket(HeroFxS2CPacket.ELECTRIC_ARC, from, 1.0F, 0,
         new float[]{(float)to.x, (float)to.y, (float)to.z}, -1));
   }

   /** A flat electric ring, drawn by each client from one packet. */
   public static void electricRing(net.minecraft.server.level.ServerLevel level, Vec3 center, double radius) {
      sendNear(level, center, new HeroFxS2CPacket(HeroFxS2CPacket.ELECTRIC_RING, center, 1.0F, 0, new float[]{(float)radius, 0.0F, 0.0F}, -1));
   }

   /** Sparks crawling on a point, drawn by each client from one packet. */
   public static void electricCrackle(net.minecraft.server.level.ServerLevel level, Vec3 at, int count) {
      sendNear(level, at, new HeroFxS2CPacket(HeroFxS2CPacket.ELECTRIC_CRACKLE, at, count, 0, null, -1));
   }

   /** Radius in which a lightning bolt FX is sent (the bolt is tall; still local atmosphere, #137). */
   public static final double BOLT_FX_RANGE = 96.0;
   /** Max channel height the client draws above the strike point. */
   public static final float BOLT_MAX_HEIGHT = 64.0F;

   /**
    * A lightning bolt onto {@code at}, falling from {@code height} blocks
    * above: one packet per viewer within {@link #BOLT_FX_RANGE}; each client
    * draws the channel, the flash and plays the thunder itself.
    */
   public static void electricBolt(net.minecraft.server.level.ServerLevel level, Vec3 at, float height, float power) {
      electricBolt(level, at, height, power, null);
   }

   /**
    * Same, with the hero's own thunder: each client plays {@code thunder} with the flash instead of the vanilla
    * bolt sounds (null keeps the vanilla pair). Thor passes his crack (ThorSounds.boltCrack, R5).
    */
   public static void electricBolt(net.minecraft.server.level.ServerLevel level, Vec3 at, float height, float power, @Nullable SoundEvent thunder) {
      HeroFxS2CPacket packet = new HeroFxS2CPacket(HeroFxS2CPacket.ELECTRIC_BOLT, at, clampBoltPower(power), 0,
         new float[]{clampBoltHeight(height), 0.0F, 0.0F}, thunder == null ? -1 : BuiltInRegistries.SOUND_EVENT.getId(thunder));
      for (ServerPlayer viewer : level.players()) {
         if (viewer.position().distanceToSqr(at) <= BOLT_FX_RANGE * BOLT_FX_RANGE) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }

   /** Pure, both sides: the drawn channel height (a hostile packet cannot ask for a kilometre-high bolt). */
   public static float clampBoltHeight(float height) {
      return Float.isFinite(height) ? Math.max(2.0F, Math.min(BOLT_MAX_HEIGHT, height)) : BOLT_MAX_HEIGHT;
   }

   public static float clampBoltPower(float power) {
      return Float.isFinite(power) ? Math.max(0.25F, Math.min(2.0F, power)) : 1.0F;
   }

   /** Pure: does a viewer at {@code viewer} get a level-anchored FX at {@code at}? */
   public static boolean inElectricRange(Vec3 viewer, Vec3 at) {
      return viewer.distanceToSqr(at) <= ELECTRIC_FX_RANGE * ELECTRIC_FX_RANGE;
   }

   private static void sendNear(net.minecraft.server.level.ServerLevel level, Vec3 at, HeroFxS2CPacket packet) {
      for (ServerPlayer viewer : level.players()) {
         if (inElectricRange(viewer.position(), at)) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }

   /** Scale range of a preset (1 = sized for a 3.2-block hero). */
   public static final float PRESET_MIN_SCALE = 0.25F;
   public static final float PRESET_MAX_SCALE = 4.0F;
   /** Hit-stop a preset may ask for (ticks). */
   public static final int PRESET_MAX_HIT_STOP = 10;

   /**
    * A named layered FX recipe at {@code at} (looking at / reaching to
    * {@code target}, or none), for every player tracking the source and the
    * source itself. {@code hitStopTicks} > 0 freezes {@code entityIds} on
    * every client for that long (contact hit-stop). Gameplay never waits for
    * it. The recipe lives on the client ({@code client.render.vfx.FxRecipe}).
    */
   public static void preset(ServerPlayer source, FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale, @Nullable BlockState material,
      int hitStopTicks, int... entityIds) {
      int stateId = material == null ? 0 : Block.getId(material);
      CoreMessages.sendToTrackingAndSelf(presetPacket(preset, at, target, scale, stateId, source.getRandom().nextInt(65536), hitStopTicks, entityIds), source);
   }

   /** A preset with no tracking source (hammer in the world, environment), sent within {@link #BOLT_FX_RANGE}. */
   public static void preset(net.minecraft.server.level.ServerLevel level, FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale,
      @Nullable BlockState material) {
      HeroFxS2CPacket packet = presetPacket(preset, at, target, scale, material == null ? 0 : Block.getId(material), level.getRandom().nextInt(65536), 0);
      for (ServerPlayer viewer : level.players()) {
         if (viewer.position().distanceToSqr(at) <= BOLT_FX_RANGE * BOLT_FX_RANGE) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }

   /** Pure: the preset packet (tests, both senders). */
   public static HeroFxS2CPacket presetPacket(FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale, int stateId, int seed, int hitStopTicks,
      int... entityIds) {
      Vec3 to = target == null ? at : target;
      int[] ids = entityIds == null ? new int[0] : entityIds;
      int count = Math.min(ids.length, 62);
      float[] points = new float[6 + count * 3];
      points[0] = preset.netId();
      points[1] = seed & 0xFFFF;
      points[2] = Math.max(0, Math.min(PRESET_MAX_HIT_STOP, hitStopTicks));
      points[3] = (float)to.x;
      points[4] = (float)to.y;
      points[5] = (float)to.z;
      float[] encoded = encodeIds(java.util.Arrays.copyOf(ids, count));
      System.arraycopy(encoded, 0, points, 6, encoded.length);
      return new HeroFxS2CPacket(HeroFxS2CPacket.PRESET, at, clampPresetScale(scale), stateId, points, -1);
   }

   public static float clampPresetScale(float scale) {
      return Float.isFinite(scale) ? Math.max(PRESET_MIN_SCALE, Math.min(PRESET_MAX_SCALE, scale)) : 1.0F;
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
