package dev.baranhan.viltrumitecore;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbilities;
import dev.baranhan.viltrumitecore.block.ViltrumiteBlocks;
import dev.baranhan.viltrumitecore.command.CoreCommands;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatConfig;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatModule;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirModule;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import dev.baranhan.viltrumitecore.item.ViltrumiteCreativeTabs;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import dev.baranhan.viltrumitecore.potion.ViltrumitePotions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("viltrumitecore")
public class ViltrumiteCore {
   public static final String MOD_ID = "viltrumitecore";
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "viltrumitecore");
   public static final RegistryObject<SoundEvent> PUNCH_IMPACT_EVENT = SOUND_EVENTS.register(
      "punch_impact", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "punch_impact"))
   );
   public static final RegistryObject<SoundEvent> DASH_EVENT = SOUND_EVENTS.register(
      "dash", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "dash"))
   );
   public static final RegistryObject<SoundEvent> BLOCK_EVENT = SOUND_EVENTS.register(
      "block", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "block"))
   );
   public static final RegistryObject<SoundEvent> INFINITY_GUN_SHOOT_EVENT = SOUND_EVENTS.register(
      "infinity_gun_shoot", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "infinity_gun_shoot"))
   );

   public static final RegistryObject<SoundEvent> HOMELANDER_LASER_CHARGE = SOUND_EVENTS.register(
      "homelander_laser_charge", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "homelander_laser_charge"))
   );
   public static final RegistryObject<SoundEvent> HOMELANDER_LASER_LOOP = SOUND_EVENTS.register(
      "homelander_laser_loop", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "homelander_laser_loop"))
   );
   public static final RegistryObject<SoundEvent> HOMELANDER_LASER_RELEASE = SOUND_EVENTS.register(
      "homelander_laser_release", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "homelander_laser_release"))
   );
   public static final RegistryObject<SoundEvent> HOMELANDER_ROAR = SOUND_EVENTS.register(
      "homelander_roar", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "homelander_roar"))
   );
   public static final RegistryObject<SoundEvent> HOMELANDER_ROAR_DEEP = SOUND_EVENTS.register(
      "homelander_roar_deep", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "homelander_roar_deep"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_SHARD_WHIZ = SOUND_EVENTS.register(
      "regulus_shard_whiz", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_shard_whiz"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_IMPACT_HEAVY = SOUND_EVENTS.register(
      "regulus_impact_heavy", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_impact_heavy"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_KICK_CRACK = SOUND_EVENTS.register(
      "regulus_kick_crack", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_kick_crack"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_AIR_BLADE = SOUND_EVENTS.register(
      "regulus_air_blade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_air_blade"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_PUNCH_HIT = SOUND_EVENTS.register(
      "regulus_punch_hit", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_punch_hit"))
   );
   public static final RegistryObject<SoundEvent> REGULUS_JUMP_CHARGE = SOUND_EVENTS.register(
      "regulus_jump_charge", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "regulus_jump_charge"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_NANO_DEPLOY = SOUND_EVENTS.register(
      "ironman_nano_deploy", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_nano_deploy"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_NANO_RETRACT = SOUND_EVENTS.register(
      "ironman_nano_retract", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_nano_retract"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_THRUSTER_LOOP = SOUND_EVENTS.register(
      "ironman_thruster_loop", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_thruster_loop"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_THRUSTER_SONIC = SOUND_EVENTS.register(
      "ironman_thruster_sonic", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_thruster_sonic"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_LANDING_SOFT = SOUND_EVENTS.register(
      "ironman_landing_soft", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_landing_soft"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_LANDING_HEAVY = SOUND_EVENTS.register(
      "ironman_landing_heavy", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_landing_heavy"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_AIR_STRIKE = SOUND_EVENTS.register(
      "ironman_air_strike", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_air_strike"))
   );
   public static final RegistryObject<SoundEvent> IRONMAN_FLYBY_HIT = SOUND_EVENTS.register(
      "ironman_flyby_hit", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", "ironman_flyby_hit"))
   );

   public ViltrumiteCore() {
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
      ViltrumiteCoreConfig.load();
      dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds.init();
      dev.baranhan.viltrumitecore.hero.ironman.IronManJarvisSounds.init();
      dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds.init();
      dev.baranhan.viltrumitecore.hero.ironman.IronManHulkbusterSounds.init();
      dev.baranhan.viltrumitecore.hero.ironman.IronManLegionSounds.init();
      dev.baranhan.viltrumitecore.worldevent.meteor.MeteorRainSounds.init();
      dev.baranhan.viltrumitecore.city.power.CityPowerSounds.init();
      dev.baranhan.viltrumitecore.world.collapse.CollapseSounds.init();
      dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds.init();
      dev.baranhan.viltrumitecore.hero.thor.ThorSounds.init();
      dev.baranhan.viltrumitecore.hero.dio.DioSounds.init();
      // Spec 2026-10-28-sinclair §11: Sinclair sounds, his own entity register and the action lock.
      dev.baranhan.viltrumitecore.hero.sinclair.SinclairSetup.init(modEventBus);
      // Thanos city event (docs/design/2026-10-31-thanos-six-stones-design.md): his sound holders.
      dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds.init();
      // Regulus Reborn (docs/design/2026-10-30-regulus-reborn-design.md): his own sound holders.
      dev.baranhan.viltrumitecore.hero.regulus.RegulusSetup.init(modEventBus);
      dev.baranhan.viltrumitecore.hero.jinwoo.JinwooSounds.init();
      dev.baranhan.viltrumitecore.hero.captain.CaptainSounds.init();
      // Red Hulk (docs/design/2026-11-01-red-hulk-design.md): his sound holders and FX preset ids 700+.
      dev.baranhan.viltrumitecore.hero.redhulk.RedHulkSounds.init();
      dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFx.init();
      // Contextual interactions (docs/contextual-interactions.json): their own sound holders.
      dev.baranhan.viltrumitecore.interaction.InteractionSounds.init();
      // Chitauri invasion (docs/plans/2026-10-31-chitauri-invasion.md): sounds and the invaders' entity register.
      dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSetup.init(modEventBus);
      // ULTRON — ВОССТАНИЕ МАШИН (docs/plans/2026-11-01-ultron-uprising.md): sounds and the machines' entity register.
      dev.baranhan.viltrumitecore.worldevent.ultron.UltronSetup.init(modEventBus);
      SOUND_EVENTS.register(modEventBus);
      modEventBus.addListener(this::commonSetup);
      MinecraftForge.EVENT_BUS.addListener(this::onCommandRegister);
      ViltrumiteItems.register(modEventBus);
      ViltrumiteCreativeTabs.register(modEventBus);
      ViltrumiteEntities.register(modEventBus);
      ViltrumiteParticles.register(modEventBus);
      ViltrumiteEffects.register(modEventBus);
      ViltrumiteBlocks.register(modEventBus);
      ViltrumitePotions.register(modEventBus);
      // Thor session A: world Mjölnir moves, hammer access and dev commands on the shared bridge.
      MjolnirModule.bootstrap();
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> {
         CoreMessages.register();
         ViltrumiteAbilities.registerAll();
         HeroRegistry.registerDefaults();
         // Od Lagna's Wish: after the heroes, the registry names them by key.
         dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes.registerDefaults();
         dev.baranhan.viltrumitecore.hero.wish.WishRitual.init();
         dev.baranhan.viltrumitecore.hero.wish.qa.WishQaSelfTest.init();
         ThorCombatConfig.load();
         ThorCombatModule.install();
         ThorScenes.bootstrap();
         // Contextual interactions: after the heroes (their combat rules join the shared rule set).
         dev.baranhan.viltrumitecore.interaction.ContextualInteractions.bootstrap();
         // Last: the move table is complete, its fingerprint goes into the login handshake.
         CoreMessages.registerThorMoveHandshake();
      });
   }

   private void onCommandRegister(RegisterCommandsEvent event) {
      CoreCommands.register(event.getDispatcher());
      dev.baranhan.viltrumitecore.command.HeroCommands.register(event.getDispatcher());
   }
}
