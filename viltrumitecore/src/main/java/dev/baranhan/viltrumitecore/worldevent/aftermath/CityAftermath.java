package dev.baranhan.viltrumitecore.worldevent.aftermath;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * City disaster aftermath, server side and public API. Destruction leaves a <b>ruin zone</b> that lives on
 * after the threat is gone: fresh dust and sparks, then smoke from the windows and smouldering spots, then
 * only the ruin and the wind in it (stages: {@link AftermathStage}). Zones are saved with the world
 * ({@link AftermathData}), merge when hits land close together, age by game time and are wiped by
 * {@code /city reset}. Clients draw and play everything ({@code client.aftermath}); citizens keep away from
 * fresh ruins and come back step by step.
 *
 * <p>Zones come from three ways in:
 * <ul>
 * <li>bursts of destroyed blocks seen by the block hook ({@link AftermathTracker}), tagged with the cause of
 *     a nearby {@link #hint} (explosions, Chitauri weapons, hero destruction, city fire);</li>
 * <li>explicit reports of this mod's events (meteor impacts, the Leviathan crash);</li>
 * <li>other systems through the shared contract: {@link #registerDamage}, {@link #registerCollapse},
 *     {@link #setPower} or the tag form {@link #accept} ({@code docs/city-aftermath-integration.md}).</li>
 * </ul>
 */
public final class CityAftermath {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/aftermath");
   /** Hints older than this are not used to name the cause of a burst. */
   static final int HINT_TICKS = 60;

   private CityAftermath() {
   }

   public static boolean enabled() {
      return ViltrumiteCoreConfig.INSTANCE.aftermathEnabled;
   }

   /** Config duration scale (1 = 45 s fresh, 6 min smouldering, 16 min late at severity 0.5). */
   public static double scale() {
      double s = ViltrumiteCoreConfig.INSTANCE.aftermathDurationScale;
      return Double.isFinite(s) ? Mth.clamp(s, 0.1, 10.0) : 1.0;
   }

   // ------------------------------------------------------------------ contract API

   /**
    * Registers damage (shared contract). The zone starts (or restarts) its fresh stage now; repeats of the same
    * {@code eventId} refresh the zone instead of counting twice. Returns the zone id, 0 when ignored (aftermath
    * off, unknown dimension).
    */
   public static int registerDamage(ServerLevel level, AftermathEvent event) {
      return declare(level, event, null);
   }

   /**
    * Registers a structural collapse (shared contract): like {@link #registerDamage} with cause "collapse" unless
    * another is given, and with an optional box of the fallen part. Dust, smoke, smouldering and the collapse
    * sounds then concentrate inside that box (falling back to the event's affected blocks and sphere).
    */
   public static int registerCollapse(ServerLevel level, AftermathEvent event, @Nullable BoundingBox box) {
      AftermathEvent e = event;
      if (AftermathCause.of(event.cause()) == AftermathCause.OTHER) {
         e = new AftermathEvent(event.eventId(), event.dimension(), event.center(), event.radius(), event.severity(), AftermathCause.COLLAPSE.id,
            event.affectedBlocks(), event.timestamp());
      }

      int[] focus = box == null ? null : new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
      return declare(level, e, focus == null ? new int[0] : focus);
   }

   /**
    * Tag form of the contract for senders that only use vanilla types: the {@link AftermathEvent#toTag()} fields
    * plus {@code "kind"}: "damage" (default), "collapse" (optional int array {@code "box"} = minX..maxZ) or
    * "power" (with {@code "powered"} boolean, {@code eventId} as the source id). Returns the zone id or 0.
    */
   public static int accept(ServerLevel level, CompoundTag tag) {
      AftermathEvent e = AftermathEvent.fromTag(tag);
      String kind = tag.getString("kind");
      if ("power".equals(kind)) {
         setPower(level, e.eventId(), e.center(), e.radius(), tag.getBoolean("powered"));
         return 0;
      }

      if ("collapse".equals(kind)) {
         int[] b = tag.getIntArray("box");
         return registerCollapse(level, e, b.length == 6 ? BoundingBox.fromCorners(new BlockPos(b[0], b[1], b[2]), new BlockPos(b[3], b[4], b[5])) : null);
      }

      return registerDamage(level, e);
   }

   /**
    * Infrastructure state (shared contract): {@code powered = false} marks a region without electricity, so
    * ruins inside it stop sparking from live wires; {@code true} (or {@link #clear}) lifts it. Saved with the world.
    */
   public static void setPower(ServerLevel level, String sourceId, BlockPos center, float radius, boolean powered) {
      if (sourceId == null || sourceId.isBlank()) {
         return;
      }

      AftermathData data = AftermathData.get(level);
      if (powered) {
         if (data.outages.remove(sourceId) == null) {
            return;
         }
      } else {
         if (!data.outages.containsKey(sourceId) && data.outages.size() >= AftermathData.MAX_OUTAGES) {
            Iterator<String> it = data.outages.keySet().iterator();
            it.next();
            it.remove();
         }

         data.outages.put(sourceId, new AftermathData.Outage(sourceId, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
            Mth.clamp(radius, 1.0F, 512.0F)));
      }

      data.setDirty();
      refreshFlags(level, data);
   }

   /** True when a point lies in a region another system reported without power. */
   public static boolean unpowered(ServerLevel level, double x, double y, double z) {
      for (AftermathData.Outage o : AftermathData.get(level).outages.values()) {
         double dx = x - o.x();
         double dy = y - o.y();
         double dz = z - o.z();
         if (dx * dx + dy * dy + dz * dz <= (double)o.radius() * o.radius()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Names the cause of destruction about to happen around a point (cheap, call it as often as needed): bursts
    * of broken blocks within {@code radius} + 24 in the next {@value #HINT_TICKS} ticks take this cause.
    */
   public static void hint(ServerLevel level, AftermathCause cause, double x, double y, double z, float radius) {
      AftermathTracker.hint(level, cause, x, y, z, radius);
   }

   /** Drops every ruin zone of a level (world restore, /aftermath clear); outages stay (their owners lift them). */
   public static void clear(ServerLevel level) {
      AftermathData data = AftermathData.get(level);
      AftermathTracker.forget(level);
      if (!data.zones.isEmpty()) {
         data.zones.clear();
         data.setDirty();
      }

      AftermathNet.clear(level);
   }

   /** Living city reset ({@code /city reset}, also a reset resumed after a restart): the ruins are being rebuilt. */
   public static void onCityReset(ServerLevel level) {
      int n = AftermathData.get(level).zones.size();
      clear(level);
      if (n > 0) {
         LOG.info("City reset: {} aftermath zones cleared", n);
      }
   }

   public static List<AftermathZone> zones(ServerLevel level) {
      return AftermathData.get(level).zones;
   }

   @Nullable
   public static AftermathZone zone(ServerLevel level, int id) {
      for (AftermathZone z : AftermathData.get(level).zones) {
         if (z.id == id) {
            return z;
         }
      }

      return null;
   }

   /** The zone whose sphere is nearest to (or contains) a point within {@code margin}, or null. */
   @Nullable
   public static AftermathZone nearest(ServerLevel level, double x, double y, double z, double margin) {
      AftermathZone best = null;
      double bestD = Double.MAX_VALUE;
      for (AftermathZone zn : AftermathData.get(level).zones) {
         double d = Math.sqrt(zn.distanceSq(x, y, z)) - zn.radius;
         if (d <= margin && d < bestD) {
            bestD = d;
            best = zn;
         }
      }

      return best;
   }

   // ------------------------------------------------------------------ citizens

   /** Citizens never pick a walk target here: inside the hazard radius of a fresh or smouldering ruin. */
   public static boolean avoid(Level level, double x, double y, double z) {
      if (!(level instanceof ServerLevel sl) || !enabled()) {
         return false;
      }

      AftermathData data = AftermathData.get(sl);
      if (data.zones.isEmpty()) {
         return false;
      }

      long now = sl.getGameTime();
      double scale = scale();
      for (AftermathZone zn : data.zones) {
         float hr = AftermathRules.hazardRadius(zn.radius, zn.age(now), zn.factor(scale));
         if (hr > 0.0F && zn.distanceSq(x, y, z) < (double)hr * hr) {
            return true;
         }
      }

      return false;
   }

   /** 0..1: how readily citizens appear at this spot (1 = no ruin around; near a fresh ruin nobody shows up yet). */
   public static float welcome(Level level, double x, double y, double z) {
      if (!(level instanceof ServerLevel sl) || !enabled()) {
         return 1.0F;
      }

      AftermathData data = AftermathData.get(sl);
      if (data.zones.isEmpty()) {
         return 1.0F;
      }

      long now = sl.getGameTime();
      double scale = scale();
      float w = 1.0F;
      for (AftermathZone zn : data.zones) {
         double reach = zn.radius + 24.0;
         if (zn.distanceSq(x, y, z) < reach * reach) {
            float f = zn.factor(scale);
            long age = zn.age(now);
            float hr = AftermathRules.hazardRadius(zn.radius, age, f);
            if (hr > 0.0F && zn.distanceSq(x, y, z) < (double)hr * hr) {
               return 0.0F;
            }

            w = Math.min(w, AftermathRules.returnFactor(age, f));
         }
      }

      return w;
   }

   /** A citizen just appeared: near a ruin that is not quiet yet it walks slowly and watchfully, and keeps off a smoking one. */
   public static void onCitizenSpawn(ServerLevel level, CitizenEntity c) {
      if (!enabled()) {
         return;
      }

      AftermathZone zn = nearest(level, c.getX(), c.getY(), c.getZ(), 24.0);
      if (zn == null) {
         return;
      }

      long now = level.getGameTime();
      float back = AftermathRules.returnFactor(zn.age(now), zn.factor(scale()));
      if (back < 1.0F) {
         c.returnCautiously(zn.x, zn.z, now, 300 + Math.round(600 * (1.0F - back)) + level.random.nextInt(200), back < 0.6F);
      }
   }

   /** Every 5 s in the city: fresh ruins are dangers (people run), smouldering and late ones are something to stop and look at. */
   static void citizens(ServerLevel level, AftermathData data, CityManager city, long now) {
      double scale = scale();
      for (AftermathZone zn : data.zones) {
         float f = zn.factor(scale);
         long age = zn.age(now);
         AftermathStage st = AftermathRules.stage(age, f);
         if (st == AftermathStage.IMPACT) {
            city.danger(zn.x, zn.y, zn.z, Math.min(zn.radius + 8.0F, 64.0F), 120);
         } else if (st == AftermathStage.SMOLDER) {
            float hr = AftermathRules.hazardRadius(zn.radius, age, f);
            city.dangerMap().add(zn.x, zn.y, zn.z, 0.0F, hr + 10.0F, now, 120);
         } else if (st == AftermathStage.LATE && zn.severity >= 0.3F) {
            city.dangerMap().add(zn.x, zn.y, zn.z, 0.0F, zn.radius * 0.4F + 6.0F, now, 120);
         }
      }
   }

   // ------------------------------------------------------------------ zones

   @Nullable
   static ServerLevel resolve(ServerLevel level, String dimension) {
      if (dimension == null || dimension.equals(level.dimension().location().toString())) {
         return level;
      }

      ResourceLocation id = ResourceLocation.tryParse(dimension);
      return id == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
   }

   private static int declare(ServerLevel level, AftermathEvent e, @Nullable int[] focus) {
      if (!enabled()) {
         return 0;
      }

      ServerLevel target = resolve(level, e.dimension());
      if (target == null) {
         LOG.warn("Aftermath event {} for unknown dimension {} ignored", e.eventId(), e.dimension());
         return 0;
      }

      List<BlockPos> blocks = e.affectedBlocks();
      long[] seeds = new long[Math.min(blocks.size(), 512)];
      int step = Math.max(1, blocks.size() / Math.max(1, seeds.length));
      for (int i = 0; i < seeds.length; i++) {
         seeds[i] = blocks.get(Math.min(blocks.size() - 1, i * step)).asLong();
      }

      AftermathZone z = hit(target, e.causeType().bit(), e.center().getX() + 0.5, e.center().getY() + 0.5, e.center().getZ() + 0.5, e.radius(),
         Math.max(blocks.size(), 1), e.severity(), true, seeds, seeds.length, e.eventId(), focus != null && focus.length == 6 ? focus : null);
      return z == null ? 0 : z.id;
   }

   /** A burst of destroyed blocks seen by the tracker. */
   static void burst(ServerLevel level, int causes, double x, double y, double z, float radius, int blocks, long[] seeds, int seedCount) {
      hit(level, causes, x, y, z, radius, blocks, AftermathRules.severityOf(blocks), false, seeds, seedCount, null, null);
   }

   @Nullable
   static AftermathZone hit(ServerLevel level, int causes, double x, double y, double z, float radius, int blocks, float severity, boolean declared,
      long[] seeds, int seedCount, @Nullable String eventId, @Nullable int[] focus) {
      AftermathData data = AftermathData.get(level);
      long now = level.getGameTime();
      AftermathZone target = null;
      if (eventId != null) {
         for (AftermathZone zn : data.zones) {
            if (zn.hasEvent(eventId)) {
               target = zn;
               break;
            }
         }
      }

      boolean repeat = target != null;
      if (target == null) {
         double best = Double.MAX_VALUE;
         for (AftermathZone zn : data.zones) {
            double d = Math.sqrt(zn.distanceSq(x, y, z));
            if (AftermathRules.merges(d, zn.radius, radius) && d < best) {
               best = d;
               target = zn;
            }
         }
      }

      if (target == null) {
         if (data.zones.size() >= AftermathRules.MAX_ZONES && !evict(data, now)) {
            // full of live ruins: the nearest one takes it
            double best = Double.MAX_VALUE;
            for (AftermathZone zn : data.zones) {
               double d = zn.distanceSq(x, y, z);
               if (d < best) {
                  best = d;
                  target = zn;
               }
            }
         }

         if (target == null) {
            target = new AftermathZone(data.nextId++, x, y, z, radius, now);
            target.severity = 0.0F;
            data.zones.add(target);
         } else {
            target.cover(x, y, z, radius);
         }
      } else {
         target.cover(x, y, z, radius);
      }

      long quiet = target.age(now);
      if (quiet > 200L) {
         target.freshHit();
      }

      for (int i = 0; i < seedCount && i < seeds.length; i++) {
         target.addSeed(seeds[i], level.random);
      }

      if (!repeat) {
         target.blocks += Math.max(0, blocks);
      }

      float old = target.severity;
      float sev = Math.max(old, AftermathRules.severityOf(target.blocks));
      if (declared) {
         target.declared = Math.max(target.declared, severity);
         target.flags |= AftermathRules.FLAG_DECLARED;
         sev = Math.max(sev, repeat ? severity : AftermathRules.mergeSeverity(old, severity));
      } else {
         sev = Math.max(sev, severity);
      }

      target.severity = Mth.clamp(sev, 0.0F, 1.0F);
      // a small hit on a big old ruin wakes up only part of it; anything of its size starts it over
      float share = Mth.clamp((declared ? severity : AftermathRules.severityOf(blocks)) / Math.max(0.05F, target.severity) * 1.5F, 0.15F, 1.0F);
      target.lastHit = now - Math.round(quiet * (1.0F - share));
      target.causes |= causes;
      if (eventId != null) {
         target.addEventId(eventId);
      }

      if (focus != null) {
         target.focus = focus.clone();
         target.flags |= AftermathRules.FLAG_FOCUS;
      }

      updateFlags(level, data, target);
      target.version++;
      data.setDirty();
      AftermathNet.urgent(level);
      if (AftermathEvents.QA_LOG) {
         LOG.info("AMQA hit zone #{} {} at {} {} {} r={} sev={} blocks={} causes={} event={} declared={}", target.id, repeat ? "repeat" : "merge/new",
            Math.round(target.x), Math.round(target.y), Math.round(target.z), Math.round(target.radius), String.format(java.util.Locale.ROOT, "%.2f", target.severity),
            target.blocks, Integer.toBinaryString(target.causes), eventId, declared);
      }

      return target;
   }

   /** Frees a slot: the oldest scar, else the oldest quiet (late) ruin. False when every zone is still live. */
   private static boolean evict(AftermathData data, long now) {
      double scale = scale();
      AftermathZone worst = null;
      long oldest = Long.MIN_VALUE;
      for (AftermathZone zn : data.zones) {
         AftermathStage st = zn.stage(now, scale);
         if ((st == AftermathStage.SCAR || st == AftermathStage.LATE || st == AftermathStage.GONE) && zn.age(now) > oldest) {
            oldest = zn.age(now);
            worst = zn;
         }
      }

      if (worst == null) {
         return false;
      }

      data.zones.remove(worst);
      return true;
   }

   private static void updateFlags(ServerLevel level, AftermathData data, AftermathZone zn) {
      int flags = zn.flags & ~(AftermathRules.FLAG_POWER_OUT | AftermathRules.FLAG_CITY);
      for (AftermathData.Outage o : data.outages.values()) {
         if (o.covers(zn)) {
            flags |= AftermathRules.FLAG_POWER_OUT;
            break;
         }
      }

      CityManager city = CityManager.of(level);
      if (city != null && city.profile().inArena((int)Math.floor(zn.x), (int)Math.floor(zn.y), (int)Math.floor(zn.z))) {
         flags |= AftermathRules.FLAG_CITY;
      }

      if (flags != zn.flags) {
         zn.flags = flags;
         zn.version++;
         data.setDirty();
      }
   }

   static void refreshFlags(ServerLevel level, AftermathData data) {
      for (AftermathZone zn : data.zones) {
         updateFlags(level, data, zn);
      }

      AftermathNet.urgent(level);
   }

   /** Once a second: drop ended zones. */
   static void maintain(ServerLevel level, AftermathData data, long now) {
      if (data.zones.isEmpty()) {
         return;
      }

      double scale = scale();
      List<AftermathZone> gone = null;
      for (AftermathZone zn : data.zones) {
         if (zn.stage(now, scale) == AftermathStage.GONE) {
            if (gone == null) {
               gone = new ArrayList<>();
            }

            gone.add(zn);
         }
      }

      if (gone != null) {
         data.zones.removeAll(gone);
         data.setDirty();
      }
   }

   /** Moves a zone's clock so it is {@code ticks} into its life (QA: /aftermath age). */
   static void setAge(ServerLevel level, AftermathZone zn, long ticks) {
      zn.lastHit = level.getGameTime() - Math.max(0L, ticks);
      zn.version++;
      AftermathData.get(level).setDirty();
      AftermathNet.urgent(level);
   }

   static void remove(ServerLevel level, AftermathZone zn) {
      AftermathData data = AftermathData.get(level);
      if (data.zones.remove(zn)) {
         data.setDirty();
         AftermathNet.urgent(level);
      }
   }
}
