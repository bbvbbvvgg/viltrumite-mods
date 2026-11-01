package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriPhase;
import dev.baranhan.viltrumitecore.worldevent.meteor.MeteorRain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * What hits the city grid, without touching the event code: explosions (a meteor strike, a Chitauri chariot or
 * bolt, TNT), lightning, bursts of destroyed blocks (Chitauri smashes, a leviathan crash, hero fights; fed by the
 * city journal hook), and the alarm leases of the running world events, polled once a second (the Chitauri
 * invasion phase, meteor showers over the city). Everything returns at once in worlds without a city.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class PowerTriggers {
   /** Lease length of the polled world events (refreshed every second while they run). */
   static final int POLL_LEASE = 80;

   private PowerTriggers() {
   }

   private static PowerGrid grid(net.minecraft.world.level.Level level) {
      CityManager city = CityManager.of(level);
      return city == null ? null : city.power();
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onExplosion(ExplosionEvent.Detonate event) {
      PowerGrid g = grid(event.getLevel());
      if (g == null) {
         return;
      }
      Explosion e = event.getExplosion();
      Vec3 at = e.getPosition();
      Entity by = e.getDirectSourceEntity();
      int blocks = event.getAffectedBlocks().size();
      if (by instanceof MeteorEntity) {
         g.damage(at.x, at.y, at.z, 12.0F, 0.9F, "meteor", "meteor_rain:impact");
      } else if (by != null && isChitauri(by)) {
         g.damage(at.x, at.y, at.z, (float)PowerRules.explosionRadius(blocks), (float)Math.max(0.3, PowerRules.explosionSeverity(blocks)), "chitauri",
            "chitauri:" + net.minecraft.world.entity.EntityType.getKey(by.getType()).getPath());
      } else if (by != null && by.getClass().getName().startsWith("dev.baranhan.viltrumitecore.entity.ultron.") && blocks > 0) {
         g.damage(at.x, at.y, at.z, (float)PowerRules.explosionRadius(blocks), (float)PowerRules.explosionSeverity(blocks), "ultron",
            "ultron:" + net.minecraft.world.entity.EntityType.getKey(by.getType()).getPath());
      } else if (blocks > 0) {
         g.damage(at.x, at.y, at.z, (float)PowerRules.explosionRadius(blocks), (float)PowerRules.explosionSeverity(blocks), "explosion", "explosion");
      }
   }

   static boolean isChitauri(Entity e) {
      return e.getClass().getName().contains(".chitauri.");
   }

   @SubscribeEvent
   public static void onEntityJoin(EntityJoinLevelEvent event) {
      if (event.getEntity() instanceof LightningBolt bolt && event.getLevel() instanceof ServerLevel level && !event.loadedFromDisk()) {
         PowerGrid g = grid(level);
         if (g != null) {
            g.damage(bolt.getX(), bolt.getY(), bolt.getZ(), 4.0F, 0.34F, "lightning", "lightning");
         }
      }
   }

   @SubscribeEvent
   public static void onChunkLoad(ChunkEvent.Load event) {
      if (event.getLevel() instanceof ServerLevel level) {
         PowerGrid g = grid(level);
         if (g != null) {
            g.onChunkLoad(event.getChunk().getPos().x, event.getChunk().getPos().z);
         }
      }
   }

   /** Who smashed the blocks: the Chitauri when their invasion is fighting around that spot, else plain destruction. */
   static String burstCause(ServerLevel level, double x, double z) {
      ChitauriInvasion.Session s = ChitauriInvasion.session();
      if (s != null && s.dimension() == level.dimension() && s.phase().fighting()) {
         double r = ChitauriInvasion.areaRadius(level) + 64;
         Vec3 a = s.anchor();
         if ((a.x - x) * (a.x - x) + (a.z - z) * (a.z - z) < r * r) {
            return "chitauri";
         }
      }
      return "destruction";
   }

   /** Once a second: the alarm leases of the running world events over this city. */
   static void poll(PowerGrid g, long now) {
      ServerLevel level = g.level;
      ChitauriInvasion.Session s = ChitauriInvasion.session();
      if (s != null && s.dimension() == level.dimension() && s.phase() != ChitauriPhase.ENDED && near(g, s.anchor(), 400)) {
         AlarmStage stage = switch (s.phase()) {
            case OPENING -> AlarmStage.WARNING;
            case GROUND_ASSAULT, AIR_ASSAULT -> AlarmStage.ACTIVE;
            case LEVIATHAN, CLIMAX -> AlarmStage.CRITICAL;
            default -> AlarmStage.NONE;
         };
         if (stage != AlarmStage.NONE) {
            g.alarm().lease("chitauri", stage, s.anchor(), (float)ChitauriInvasion.areaRadius(level), POLL_LEASE);
         } else {
            g.alarm().release("chitauri");
         }
      }
      for (MeteorRain.Session m : MeteorRain.sessions()) {
         if (m.dimension() != level.dimension()) {
            continue;
         }
         ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner());
         if (owner == null || owner.level() != level || !near(g, owner.position(), 300)) {
            continue;
         }
         AlarmStage stage = m.ageTicks(level.getServer()) < 160 ? AlarmStage.WARNING : AlarmStage.ACTIVE;
         g.alarm().lease("meteor:" + m.owner(), stage, owner.position(), 260.0F, POLL_LEASE);
      }
   }

   static boolean near(PowerGrid g, Vec3 at, double margin) {
      var p = g.city.profile();
      return at.x > p.minX - margin && at.x < p.maxX + margin && at.z > p.minZ - margin && at.z < p.maxZ + margin;
   }
}
