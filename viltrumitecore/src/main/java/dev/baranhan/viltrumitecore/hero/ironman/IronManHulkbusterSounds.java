package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Iron Man Stage 5 sounds: the Hulkbuster Mark 48 (own synthesis,
 * tools/sfx/ironman_stage5.sh). Registered on ViltrumiteCore.SOUND_EVENTS.
 */
public final class IronManHulkbusterSounds {
   public static final RegistryObject<SoundEvent> DROP = reg("ironman_hulkbuster_drop");
   public static final RegistryObject<SoundEvent> ASSEMBLE = reg("ironman_hulkbuster_assemble");
   public static final RegistryObject<SoundEvent> EXIT = reg("ironman_hulkbuster_exit");
   public static final RegistryObject<SoundEvent> BREAK = reg("ironman_hulkbuster_break");
   public static final RegistryObject<SoundEvent> STEP = reg("ironman_hulkbuster_step");
   public static final RegistryObject<SoundEvent> SERVO = reg("ironman_hulkbuster_servo");
   public static final RegistryObject<SoundEvent> PUNCH = reg("ironman_hulkbuster_punch");
   public static final RegistryObject<SoundEvent> JACKHAMMER = reg("ironman_hulkbuster_jackhammer");
   public static final RegistryObject<SoundEvent> GRAB = reg("ironman_hulkbuster_grab");
   public static final RegistryObject<SoundEvent> THROW = reg("ironman_hulkbuster_throw");
   public static final RegistryObject<SoundEvent> SLAM = reg("ironman_hulkbuster_slam");
   public static final RegistryObject<SoundEvent> HOP = reg("ironman_hulkbuster_hop");
   // Rework 2026-10-28 (cue sheet docs/plans/2026-10-28-hulkbuster-cues.md); played only through HulkbusterSounds.
   public static final RegistryObject<SoundEvent> SCREECH = reg("ironman_hulkbuster_screech");
   public static final RegistryObject<SoundEvent> SWING = reg("ironman_hulkbuster_swing");
   public static final RegistryObject<SoundEvent> HAMMER = reg("ironman_hulkbuster_hammer");
   public static final RegistryObject<SoundEvent> CHARGE = reg("ironman_hulkbuster_charge");
   public static final RegistryObject<SoundEvent> CHARGED_PUNCH = reg("ironman_hulkbuster_charged_punch");
   public static final RegistryObject<SoundEvent> FINISH_PUNCH = reg("ironman_hulkbuster_finish_punch");
   public static final RegistryObject<SoundEvent> CLAMP = reg("ironman_hulkbuster_clamp");
   public static final RegistryObject<SoundEvent> BLOCK_HIT = reg("ironman_hulkbuster_block_hit");
   public static final RegistryObject<SoundEvent> REPULSOR_CHARGE = reg("ironman_hulkbuster_repulsor_charge");
   public static final RegistryObject<SoundEvent> REPULSOR_BEAM = reg("ironman_hulkbuster_repulsor_beam");
   public static final RegistryObject<SoundEvent> DASH = reg("ironman_hulkbuster_dash");
   public static final RegistryObject<SoundEvent> COUGH = reg("ironman_hulkbuster_cough");
   public static final RegistryObject<SoundEvent> LAND = reg("ironman_hulkbuster_land");
   public static final RegistryObject<SoundEvent> ULT_IGNITE = reg("ironman_hulkbuster_ult_ignite");
   public static final RegistryObject<SoundEvent> ULT_IMPACT = reg("ironman_hulkbuster_ult_impact");
   public static final RegistryObject<SoundEvent> REFUSE = reg("ironman_hulkbuster_refuse");
   /** Client loops (HulkbusterLoops): servo hum, thrusters, elbow / repulsor charge, damage sparks. */
   public static final RegistryObject<SoundEvent> SERVO_LOOP = reg("ironman_hulkbuster_servo_loop");
   public static final RegistryObject<SoundEvent> THRUSTER_LOOP = reg("ironman_hulkbuster_thruster_loop");
   public static final RegistryObject<SoundEvent> CHARGE_LOOP = reg("ironman_hulkbuster_charge_loop");
   public static final RegistryObject<SoundEvent> SPARK_LOOP = reg("ironman_hulkbuster_spark_loop");

   private IronManHulkbusterSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Loads the holders (class init). */
   public static void init() {
   }
}
