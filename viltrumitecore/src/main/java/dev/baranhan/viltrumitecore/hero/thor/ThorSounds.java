package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * R5 (spec §8): the one place Thor's sounds are played from. Events are Thor's own files (own synthesis, CC0,
 * tools/sfx/thor.py, tools/assets/thor_sources.md), registered on {@code ViltrumiteCore.SOUND_EVENTS};
 * {@link #init()} runs from the mod constructor. Moves name a {@link ThorCue}; no Thor code plays a vanilla
 * {@code SoundEvents} sound (ThorSoundsTest). Loops are client instances ({@code client.thor.ThorSoundLoops}).
 */
public final class ThorSounds {
   public static final String NAMESPACE = "viltrumitecore";
   private static final Map<String, RegistryObject<SoundEvent>> EVENTS = new LinkedHashMap<>();
   /** Last server bolt impact / rumble tick per level ({@link ThorCue#gate}). */
   private static final Map<ResourceKey<Level>, long[]> BOLTS = new HashMap<>();

   static {
      for (String name : ThorCue.EVENTS) {
         String path = "thor." + name;
         EVENTS.put(name, ViltrumiteCore.SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(NAMESPACE, path))));
      }
   }

   private ThorSounds() {
   }

   /** Loads the holders (class init) before the register is attached to the mod bus. */
   public static void init() {
   }

   public static Map<String, RegistryObject<SoundEvent>> events() {
      return Collections.unmodifiableMap(EVENTS);
   }

   /** The event of a layer name ({@link ThorCue#EVENTS}). */
   public static SoundEvent event(String name) {
      RegistryObject<SoundEvent> holder = EVENTS.get(name);
      if (holder == null) {
         throw new IllegalArgumentException("Unknown Thor sound event " + name);
      }

      return holder.get();
   }

   /** Everyone nearby hears it, the player included, at the chest. */
   public static void play(ServerPlayer player, ThorCue cue) {
      playAt(player.serverLevel(), player.position().add(0.0, 1.0, 0.0), cue, 1.0F, 1.0F);
   }

   public static void playAt(ServerLevel level, Vec3 at, ThorCue cue) {
      playAt(level, at, cue, 1.0F, 1.0F);
   }

   /** All layers of a cue at a point; {@code volume} scales each layer (above 1: a wider range), {@code pitch} multiplies. */
   public static void playAt(ServerLevel level, Vec3 at, ThorCue cue, float volume, float pitch) {
      RandomSource random = level.getRandom();
      for (ThorCue.Layer layer : cue.layers()) {
         level.playSound(null, at.x, at.y, at.z, event(layer.event()), cue.source(), layer.volume() * volume, pitch(layer, pitch, random));
      }
   }

   /** Only this player hears it (a refused press). */
   public static void refuse(ServerPlayer player) {
      notify(player, ThorCue.REFUSE);
   }

   public static void notify(ServerPlayer player, ThorCue cue) {
      for (ThorCue.Layer layer : cue.layers()) {
         player.playNotifySound(event(layer.event()), cue.source(), layer.volume(), pitch(layer, 1.0F, player.getRandom()));
      }
   }

   /** Cuts a cue short for every player in its range around {@code at} (a riser ended by its contact or a cancel). */
   public static void stop(ServerLevel level, Vec3 at, ThorCue cue) {
      double range = cue.range() + 16.0;
      for (ThorCue.Layer layer : cue.layers()) {
         ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(new ResourceLocation(NAMESPACE, "thor." + layer.event()), cue.source());
         for (ServerPlayer viewer : level.players()) {
            if (viewer.position().distanceToSqr(at) <= range * range) {
               viewer.connection.send(packet);
            }
         }
      }
   }

   // --- the attack table (ThorCue#windup / hit / ground / tail) ---

   public static void strikeWindup(ServerPlayer player, @Nullable ThorStrike strike) {
      if (strike != null) {
         play(player, ThorCue.windup(strike));
      }
   }

   /** Contact: the body layers when something was struck, the ground layers at the shock centre. */
   public static void strikeContact(ServerPlayer player, ThorStrike strike, boolean landed, @Nullable Vec3 shockCenter) {
      if (landed) {
         play(player, ThorCue.hit(strike));
      }

      ThorCue ground = ThorCue.ground(strike);
      if (ground != null && shockCenter != null) {
         playAt(player.serverLevel(), shockCenter, ground);
      }
   }

   public static void strikeTail(ServerPlayer player, @Nullable ThorStrike strike) {
      ThorCue tail = strike == null ? null : ThorCue.tail(strike);
      if (tail != null) {
         play(player, tail);
      }
   }

   // --- bolts ---

   /** The crack each client plays with a drawn bolt (HeroFx.electricBolt). */
   public static SoundEvent boltCrack() {
      return event(ThorCue.BOLT_CRACK);
   }

   /** The server half of a bolt: the impact (gated per level) and the long rumble (at most one per RUMBLE_GAP). */
   public static void bolt(ServerLevel level, Vec3 at, float power) {
      long now = level.getGameTime();
      long[] last = BOLTS.computeIfAbsent(level.dimension(), key -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
      if (ThorCue.gate(last[0], now, ThorCue.IMPACT_GAP)) {
         last[0] = now;
         playAt(level, at, ThorCue.BOLT, power, 1.0F);
      }

      if (ThorCue.gate(last[1], now, ThorCue.RUMBLE_GAP)) {
         last[1] = now;
         playAt(level, at, ThorCue.BOLT_RUMBLE, power, 1.0F);
      }
   }

   private static float pitch(ThorCue.Layer layer, float scale, RandomSource random) {
      float spread = layer.jitter() <= 0.0F ? 0.0F : (random.nextFloat() * 2.0F - 1.0F) * layer.jitter();
      return Math.max(0.5F, Math.min(2.0F, layer.pitch() * scale + spread));
   }
}
