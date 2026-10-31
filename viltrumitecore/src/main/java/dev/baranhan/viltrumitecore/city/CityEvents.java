package dev.baranhan.viltrumitecore.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.fx.CombatNoise;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Forge wiring of the living city: lifecycle, the per-level tick, danger sources
 * (hits, explosions, hero combat noise) and the /city command. Everything returns at
 * once in worlds without a prepared city profile.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class CityEvents {
   private static boolean listening;

   private CityEvents() {
   }

   @SubscribeEvent
   public static void onLevelLoad(LevelEvent.Load event) {
      if (event.getLevel() instanceof ServerLevel level) {
         if (!listening) {
            listening = true;
            CombatNoise.listen(CityEvents::onCombatNoise);
         }

         CityManager.onLevelLoad(level);
      }
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         CityManager.onLevelUnload(level);
      }
   }

   /** Autosave and /save-all write the chunks: write the journal with them, so a crash never leaves
    *  changed chunks on disk whose original blocks were only in memory. */
   @SubscribeEvent
   public static void onLevelSave(LevelEvent.Save event) {
      CityManager m = CityManager.active();
      if (m != null && event.getLevel() == m.level() && m.journal().dirty()) {
         m.saveJournal(false);
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      CityManager.onServerStopping();
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase != TickEvent.Phase.END || event.side.isClient() || !(event.level instanceof ServerLevel level)) {
         return;
      }

      CityManager city = CityManager.of(level);
      // A world pause (Reinhard's N erasure) stops time: the city waits too, so no citizen
      // spawns, despawns or repath happens and no block is restored mid-scene.
      if (city != null && !dev.baranhan.viltrumitecore.hero.control.TimeFreeze.worldStopped(level)) {
         city.tick();
      }
   }

   @SubscribeEvent
   public static void onChunkUnload(ChunkEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         CityManager city = CityManager.of(level);
         if (city != null) {
            city.onChunkUnload(event.getChunk().getPos().x, event.getChunk().getPos().z);
         }
      }
   }

   /** Any hit on a living entity marks a fight zone (attack event: also fires for blocked or absorbed hits). */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onAttack(LivingAttackEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide || victim instanceof CitizenEntity) {
         return; // a hurt citizen marks its own (smaller) panic zone
      }

      CityManager city = CityManager.of(victim.level());
      if (city == null) {
         return;
      }

      Entity attacker = event.getSource().getEntity();
      if (victim instanceof Player || attacker instanceof Player || event.getAmount() >= 4.0F) {
         city.danger(victim.getX(), victim.getY(), victim.getZ(), DangerMap.FIGHT_RADIUS, DangerMap.DEFAULT_LIFETIME);
         // a heavy blow (a hammer, a super punch) shakes the street around the victim
         city.blast(victim.getX(), victim.getY(), victim.getZ(), dev.baranhan.viltrumitecore.city.danger.ThreatRules.hitBlast(event.getAmount()));
      }
   }

   @SubscribeEvent
   public static void onExplosion(ExplosionEvent.Detonate event) {
      CityManager city = CityManager.of(event.getLevel());
      if (city != null) {
         Vec3 at = event.getExplosion().getPosition();
         city.danger(at.x, at.y, at.z, DangerMap.EXPLOSION_RADIUS, DangerMap.DEFAULT_LIFETIME);
         city.blast(at.x, at.y, at.z, dev.baranhan.viltrumitecore.city.danger.ThreatRules.explosionBlast(event.getAffectedBlocks().size()));
      }
   }

   private static void onCombatNoise(ServerLevel level, Vec3 at, float radius) {
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.danger(at.x, at.y, at.z, Math.max(DangerMap.FIGHT_RADIUS, radius), DangerMap.DEFAULT_LIFETIME);
         city.blast(at.x, at.y, at.z, dev.baranhan.viltrumitecore.city.danger.ThreatRules.noiseBlast(radius));
      }
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent event) {
      CityCommands.register(event.getDispatcher());
   }

   /** Mod bus: citizen attributes. */
   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD)
   public static final class ModBus {
      private ModBus() {
      }

      @SubscribeEvent
      public static void onAttributes(EntityAttributeCreationEvent event) {
         event.put(ViltrumiteEntities.CITIZEN.get(), CitizenEntity.createAttributes().build());
      }
   }
}
