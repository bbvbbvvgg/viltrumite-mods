package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of THANOS 2.0 (the war for Atheria): the Sanctuary II, the new stone moves, Ebony Maw,
 * Cull Obsidian and the city. Every file is original procedural audio (CC0, {@code tools/sfx/thanos_war.py},
 * ledger {@code tools/assets/thanos_war_sources.md}); no vanilla sound stands in. Events live in
 * {@code src/main/sound-overlays/thanos_war.json}.
 */
public final class ThanosWarSounds {
   // ---- the ship
   public static final RegistryObject<SoundEvent> SHIP_ARRIVAL = reg("thanos_war.ship_arrival");
   public static final RegistryObject<SoundEvent> SHIP_HUM = reg("thanos_war.ship_hum");
   public static final RegistryObject<SoundEvent> SHIP_HORN = reg("thanos_war.ship_horn");
   public static final RegistryObject<SoundEvent> SHIP_CHARGE = reg("thanos_war.ship_charge");
   public static final RegistryObject<SoundEvent> SHIP_LANCE = reg("thanos_war.ship_lance");
   public static final RegistryObject<SoundEvent> SHIP_BEAM = reg("thanos_war.ship_beam");
   public static final RegistryObject<SoundEvent> SHIP_DEPART = reg("thanos_war.ship_depart");
   public static final RegistryObject<SoundEvent> PORTAL_OPEN = reg("thanos_war.portal_open");
   public static final RegistryObject<SoundEvent> SIREN = reg("thanos_war.siren");
   // ---- Thanos
   public static final RegistryObject<SoundEvent> COMMAND = reg("thanos_war.command");
   public static final RegistryObject<SoundEvent> ABSORB = reg("thanos_war.absorb");
   public static final RegistryObject<SoundEvent> RUPTURE = reg("thanos_war.rupture");
   public static final RegistryObject<SoundEvent> BARRAGE = reg("thanos_war.barrage");
   public static final RegistryObject<SoundEvent> REWRITE = reg("thanos_war.rewrite");
   public static final RegistryObject<SoundEvent> ANOMALY = reg("thanos_war.anomaly");
   public static final RegistryObject<SoundEvent> COMPULSION = reg("thanos_war.compulsion");
   public static final RegistryObject<SoundEvent> HARVEST = reg("thanos_war.harvest");
   public static final RegistryObject<SoundEvent> TIME_STOP = reg("thanos_war.time_stop");
   public static final RegistryObject<SoundEvent> VOLLEY = reg("thanos_war.volley");
   public static final RegistryObject<SoundEvent> ROCK_IMPACT = reg("thanos_war.rock_impact");
   public static final RegistryObject<SoundEvent> INFINITY_STAGE = reg("thanos_war.infinity_stage");
   // ---- Ebony Maw
   public static final RegistryObject<SoundEvent> MAW_VOICE = reg("thanos_war.maw_voice");
   public static final RegistryObject<SoundEvent> MAW_LIFT = reg("thanos_war.maw_lift");
   public static final RegistryObject<SoundEvent> MAW_THROW = reg("thanos_war.maw_throw");
   public static final RegistryObject<SoundEvent> MAW_GRAB = reg("thanos_war.maw_grab");
   public static final RegistryObject<SoundEvent> MAW_BARRIER = reg("thanos_war.maw_barrier");
   public static final RegistryObject<SoundEvent> MAW_CATCH = reg("thanos_war.maw_catch");
   public static final RegistryObject<SoundEvent> MAW_HURT = reg("thanos_war.maw_hurt");
   public static final RegistryObject<SoundEvent> MAW_DEATH = reg("thanos_war.maw_death");
   // ---- Cull Obsidian
   public static final RegistryObject<SoundEvent> CULL_ROAR = reg("thanos_war.cull_roar");
   public static final RegistryObject<SoundEvent> CULL_SWING = reg("thanos_war.cull_swing");
   public static final RegistryObject<SoundEvent> CULL_SLAM = reg("thanos_war.cull_slam");
   public static final RegistryObject<SoundEvent> CULL_CHARGE = reg("thanos_war.cull_charge");
   public static final RegistryObject<SoundEvent> CULL_WALL = reg("thanos_war.cull_wall");
   public static final RegistryObject<SoundEvent> CULL_CHAIN = reg("thanos_war.cull_chain");
   public static final RegistryObject<SoundEvent> CULL_GRAB = reg("thanos_war.cull_grab");
   public static final RegistryObject<SoundEvent> CULL_HURT = reg("thanos_war.cull_hurt");
   public static final RegistryObject<SoundEvent> CULL_DEATH = reg("thanos_war.cull_death");
   public static final RegistryObject<SoundEvent> CULL_STEP = reg("thanos_war.cull_step");
   public static final RegistryObject<SoundEvent> DEBRIS_IMPACT = reg("thanos_war.debris_impact");

   private ThanosWarSounds() {
   }

   /** Class-load hook from the mod constructor: the holders above register on first touch. */
   public static void init() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Positional one-shot (hostile source) heard within {@code range}. */
   public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch, double range) {
      dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds.play(level, at, sound, volume, pitch, range);
   }
}
