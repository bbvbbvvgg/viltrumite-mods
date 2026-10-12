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
   public static final RegistryObject<SoundEvent> PARRY = reg("reinhard.parry");
   public static final RegistryObject<SoundEvent> SUPER_JUMP = reg("reinhard.super_jump");
   // Stage 3 abilities (spec §6, §10.2: mod sounds where named, vanilla placeholders otherwise).
   public static final RegistryObject<SoundEvent> LIGHT_PUNCH = reg("reinhard.light_punch");
   public static final RegistryObject<SoundEvent> STEP_DASH = reg("reinhard.step_dash");
   public static final RegistryObject<SoundEvent> CATCH = reg("reinhard.catch");
   public static final RegistryObject<SoundEvent> COMBO_HIT = reg("reinhard.combo_hit");
   public static final RegistryObject<SoundEvent> COMBO_FINAL = reg("reinhard.combo_final");
   public static final RegistryObject<SoundEvent> KICK_TOSS = reg("reinhard.kick_toss");
   public static final RegistryObject<SoundEvent> SKY_KICK = reg("reinhard.sky_kick");
   public static final RegistryObject<SoundEvent> GRAB = reg("reinhard.grab");
   public static final RegistryObject<SoundEvent> THROW = reg("reinhard.throw");
   public static final RegistryObject<SoundEvent> DASH_BEHIND = reg("reinhard.dash_behind");
   public static final RegistryObject<SoundEvent> STOMP = reg("reinhard.stomp");
   public static final RegistryObject<SoundEvent> REFLECT = reg("reinhard.reflect");
   // Stage 4: the Reid draw scene.
   public static final RegistryObject<SoundEvent> REID_RUMBLE = reg("reinhard.reid_rumble");
   public static final RegistryObject<SoundEvent> REID_UNSHEATHE = reg("reinhard.reid_unsheathe");
   public static final RegistryObject<SoundEvent> REID_CHOIR = reg("reinhard.reid_choir");
   public static final RegistryObject<SoundEvent> REID_FAR = reg("reinhard.reid_far");
   /** The calm 1 s sheathe click (manual, auto-return, leaving Holy Sword). */
   public static final RegistryObject<SoundEvent> REID_SHEATHE = reg("reinhard.reid_sheathe");

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

   /** Same, at a point (impact, arrival). */
   public static void playAt(ServerPlayer player, net.minecraft.world.phys.Vec3 at, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }
}
