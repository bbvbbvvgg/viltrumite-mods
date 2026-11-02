package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of Venom. Every file is a real CC0 recording from Freesound, processed (trim, pitch, fade, mono) by
 * {@code tools/sfx/venom.py}; sources, authors and the licence proof per file are in docs/venom/audio-sources.md and
 * the credits ship in the JAR (META-INF/credits/venom-audio.md). No vanilla stand-ins, no stubs. Spoken lines are
 * subtitles only (VenomLines). {@link #init()} runs from the mod constructor.
 */
public final class VenomSounds {
   // the symbiote
   public static final RegistryObject<SoundEvent> TRANSFORM = reg("venom.transform");
   public static final RegistryObject<SoundEvent> REVERT = reg("venom.revert");
   public static final RegistryObject<SoundEvent> ROAR = reg("venom.roar");
   public static final RegistryObject<SoundEvent> SQUIRM = reg("venom.squirm");
   public static final RegistryObject<SoundEvent> REFORM = reg("venom.reform");
   public static final RegistryObject<SoundEvent> SHRIEK = reg("venom.shriek");
   // claws and tendrils
   public static final RegistryObject<SoundEvent> CLAW = reg("venom.claw");
   public static final RegistryObject<SoundEvent> CLAW_HIT = reg("venom.claw_hit");
   public static final RegistryObject<SoundEvent> TENDRIL_SHOT = reg("venom.tendril_shot");
   public static final RegistryObject<SoundEvent> TENDRIL_STICK = reg("venom.tendril_stick");
   public static final RegistryObject<SoundEvent> ZIP = reg("venom.zip");
   public static final RegistryObject<SoundEvent> LASH = reg("venom.lash");
   public static final RegistryObject<SoundEvent> SLAM = reg("venom.slam");
   public static final RegistryObject<SoundEvent> GRAB = reg("venom.grab");
   public static final RegistryObject<SoundEvent> THROW = reg("venom.throw");
   public static final RegistryObject<SoundEvent> SHELL = reg("venom.shell");
   // the Spider
   public static final RegistryObject<SoundEvent> WEB_TEAR = reg("venom.web_tear");
   public static final RegistryObject<SoundEvent> WEB_EAT = reg("venom.web_eat");
   public static final RegistryObject<SoundEvent> CLASH = reg("venom.clash");
   // the takeover
   public static final RegistryObject<SoundEvent> ENGULF = reg("venom.engulf");
   public static final RegistryObject<SoundEvent> HEARTBEAT = reg("venom.heartbeat");
   public static final RegistryObject<SoundEvent> BREATH = reg("venom.breath");
   public static final RegistryObject<SoundEvent> WHISPER = reg("venom.whisper");
   public static final RegistryObject<SoundEvent> RELEASE = reg("venom.release");
   public static final RegistryObject<SoundEvent> REFUSE = reg("venom.refuse");

   private VenomSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }

   public static void play(Entity at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), at instanceof net.minecraft.world.entity.player.Player
         ? SoundSource.PLAYERS : SoundSource.HOSTILE, volume, pitch);
   }

   public static void playAt(Entity source, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      source.level().playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   public static float jitter(Entity e, float base) {
      return base + (e.level().random.nextFloat() - 0.5F) * 0.14F;
   }
}
