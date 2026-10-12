package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.RegistryObject;

/**
 * Reinhard sound events. Final slot names; the sounds are vanilla
 * placeholders in sounds.json until the recorded ones arrive (spec §10).
 * Registered on ViltrumiteCore.SOUND_EVENTS; {@link #init()} runs from the
 * mod constructor so the holders exist before registration.
 */
public final class ReinhardSounds {
   public static final RegistryObject<SoundEvent> AIR_STEP = reg("reinhard.air_step");
   public static final RegistryObject<SoundEvent> MODE_RESTRAINED = reg("reinhard.mode_restrained");
   public static final RegistryObject<SoundEvent> MODE_KNIGHT = reg("reinhard.mode_knight");
   public static final RegistryObject<SoundEvent> MODE_HOLY_SWORD = reg("reinhard.mode_holy_sword");
   public static final RegistryObject<SoundEvent> REFUSE = reg("reinhard.refuse");
   public static final RegistryObject<SoundEvent> PHOENIX = reg("reinhard.phoenix");

   private ReinhardSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Loads the holders (class init). */
   public static void init() {
   }

   /** The "something in the world changed" sound of a mode (spec §5). */
   public static SoundEvent mode(ReinhardMode mode) {
      return switch (mode) {
         case RESTRAINED -> MODE_RESTRAINED.get();
         case KNIGHT -> MODE_KNIGHT.get();
         case HOLY_SWORD -> MODE_HOLY_SWORD.get();
      };
   }

   /** Everyone nearby hears it, the player included. */
   public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
   }
}
