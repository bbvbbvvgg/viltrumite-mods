package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPlacement;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Reality;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Red. Zones of unstable matter under the heroes (they grow, pulse damage and slow, then shrink
 * back to nothing), false images of Thanos around a target, and the shards of the trinity.
 * Nothing here writes a block: every effect is entity-side and ends by itself.
 */
public final class RealityStone {
   private static final int MAX_ZONES = 8;
   private final ThanosCombat c;
   private final List<Vec3> planned = new ArrayList<>();
   private final List<Zone> zones = new ArrayList<>();
   private final List<Vec3> mirages = new ArrayList<>();
   private int mirageTicks;

   public RealityStone(ThanosCombat combat) {
      this.c = combat;
   }

   private static final class Zone {
      final Vec3 center;
      final float damage;
      int age;

      Zone(Vec3 center, float damage) {
         this.center = center;
         this.damage = damage;
      }
   }

   public void tick() {
      for (Iterator<Zone> it = this.zones.iterator(); it.hasNext(); ) {
         Zone z = it.next();
         float scale = Reality.zoneScale(z.age);
         if (z.age % Reality.ZONE_PULSE == 0 && scale > 0.3F) {
            for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, z.center, Reality.ZONE_RADIUS)) {
               if (Reality.inZone(e.getX() - z.center.x, e.getY() - z.center.y, e.getZ() - z.center.z, scale)) {
                  ThanosTargets.hurt(this.c.thanos, e, z.damage, 1.0F);
                  if (!(e instanceof Player)) {
                     // Mobs are slowed here; players by their own client (it knows the zones).
                     e.setDeltaMovement(e.getDeltaMovement().multiply(Reality.ZONE_SLOW, 1.0, Reality.ZONE_SLOW));
                  }
               }
            }
         }

         if (++z.age >= Reality.ZONE_LIFE) {
            it.remove();
         }
      }

      if (this.mirageTicks > 0) {
         this.mirageTicks--;
         this.tickMirages();
         if (this.mirageTicks == 0) {
            this.popAll();
         }
      }
   }

   // ---- zones --------------------------------------------------------------------------------------
   /** Marks where the zones will open (under the heroes, the rest around him). */
   public void planZones() {
      this.planned.clear();
      int count = Reality.zones(this.c.event.heroCount());
      List<ServerPlayer> heroes = new ArrayList<>(this.c.heroes());
      heroes.sort((a, b) -> Double.compare(a.distanceToSqr(this.c.thanos), b.distanceToSqr(this.c.thanos)));
      for (ServerPlayer p : heroes) {
         if (this.planned.size() >= count || p.distanceToSqr(this.c.thanos) > 32.0 * 32.0) {
            break;
         }

         this.planned.add(this.floor(p.position().add(this.jitter(1.2))));
      }

      while (this.planned.size() < count) {
         double a = this.c.level.random.nextDouble() * Math.PI * 2.0;
         double r = 5.0 + this.c.level.random.nextDouble() * 9.0;
         Vec3 base = this.c.target != null ? this.c.target.position() : this.c.thanos.position();
         this.planned.add(this.floor(base.add(Math.cos(a) * r, 0.0, Math.sin(a) * r)));
      }

      for (Vec3 at : this.planned) {
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.REALITY_ZONE, this.c.thanos.getId(), -1, at, Vec3.ZERO, 0.0F, this.c.telegraph);
      }
   }

   public void spawnZones(float damage) {
      for (Vec3 at : this.planned) {
         if (this.zones.size() >= MAX_ZONES) {
            this.zones.remove(0);
         }

         this.zones.add(new Zone(at, damage));
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.REALITY_ZONE, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)Reality.ZONE_RADIUS,
            Reality.ZONE_LIFE);
      }

      if (!this.planned.isEmpty()) {
         ThanosSounds.play(this.c.level, this.planned.get(0), ThanosSounds.REALITY_WARP, 2.0F, 0.8F, 64.0);
      }

      this.planned.clear();
   }

   // ---- mirages ------------------------------------------------------------------------------------
   /**
    * Three images of him on a ring around the target; he is one of them. {@code soul}: the
    * images throw soul tethers (Reality+Soul combo) instead of striking.
    */
   public void mirages(boolean soul) {
      this.popAll();
      LivingEntity t = this.c.target;
      Vec3 center = t != null ? t.position() : this.c.thanos.position();
      int n = Reality.MIRAGES;
      long seed = this.c.level.random.nextLong();
      int real = Reality.realIndex(seed, n);
      double start = this.c.level.random.nextDouble() * Math.PI * 2.0;
      List<Vec3> spots = new ArrayList<>();
      for (int i = 0; i < n; i++) {
         double a = start + i * Math.PI * 2.0 / n;
         Vec3 at = ThanosPlacement.near(this.c.level, this.c.thanos, this.c.thanos.getDimensions(this.c.thanos.getPose()),
            center.add(Math.cos(a) * Reality.MIRAGE_RING, 0.0, Math.sin(a) * Reality.MIRAGE_RING), 2.0, 3, 6);
         spots.add(at != null ? at : this.c.thanos.position());
      }

      Vec3 from = this.c.thanos.position();
      Vec3 to = spots.get(real);
      this.c.thanos.teleportTo(to.x, to.y, to.z);
      this.c.thanos.setDeltaMovement(Vec3.ZERO);
      this.c.thanos.faceTowards(center, 180.0F);
      this.mirageTicks = Reality.MIRAGE_LIFE;
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MIRAGE_POP, this.c.thanos.getId(), -1, from, Vec3.ZERO, 1.0F, 0);
      for (int i = 0; i < n; i++) {
         if (i == real) {
            continue;
         }

         Vec3 at = spots.get(i);
         this.mirages.add(at);
         Vec3 face = center.subtract(at);
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.REALITY_MIRAGE, this.c.thanos.getId(), i, at, face, soul ? 1.0F : 0.0F, Reality.MIRAGE_LIFE);
         if (soul) {
            this.c.soul.tether(at.add(0.0, 2.4, 0.0), ThanosMove.COMBO_SOUL_MIRAGE.active, true);
         }
      }

      if (soul) {
         this.c.soul.tether(this.c.handPos(), ThanosMove.COMBO_SOUL_MIRAGE.active, false);
      }
   }

   /** A hero that walks into an image pops it. */
   private void tickMirages() {
      for (Iterator<Vec3> it = this.mirages.iterator(); it.hasNext(); ) {
         Vec3 at = it.next();
         for (ServerPlayer p : this.c.heroes()) {
            if (p.position().distanceToSqr(at) <= Reality.MIRAGE_POP * Reality.MIRAGE_POP) {
               this.pop(at, 0.0F);
               it.remove();
               break;
            }
         }
      }
   }

   /** The images rush the target: the real one cleaves, every false one bursts around itself. */
   public void strike(float damage) {
      for (Vec3 at : this.mirages) {
         this.pop(at, damage * 0.5F);
      }

      this.mirages.clear();
      this.mirageTicks = 0;
      if (this.c.target != null) {
         this.c.thanos.faceTowards(this.c.target.position(), 180.0F);
      }

      this.c.power.cleave(damage);
   }

   private void pop(Vec3 at, float damage) {
      if (damage > 0.0F) {
         for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, at, 2.6)) {
            ThanosTargets.hurt(this.c.thanos, e, damage, 1.0F);
         }
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MIRAGE_POP, this.c.thanos.getId(), -1, at, Vec3.ZERO, damage > 0.0F ? 2.6F : 1.0F, 1);
      ThanosSounds.play(this.c.level, at, ThanosSounds.REALITY_MIRAGE, 1.4F, 1.1F, 48.0);
   }

   private void popAll() {
      for (Vec3 at : this.mirages) {
         this.pop(at, 0.0F);
      }

      this.mirages.clear();
      this.mirageTicks = 0;
   }

   // ---- trinity shards --------------------------------------------------------------------------------
   /** A red shard from each open portal (or his fist) at the nearest hero in sight. */
   public void shards(float damage) {
      List<Vec3> from = this.c.space.ring().isEmpty() ? List.of(this.c.handPos()) : this.c.space.ring();
      for (Vec3 origin : from) {
         LivingEntity best = null;
         double bestD = 30.0 * 30.0;
         for (ServerPlayer p : this.c.heroes()) {
            double d = p.distanceToSqr(origin);
            if (d < bestD && this.sees(origin, p.getEyePosition())) {
               bestD = d;
               best = p;
            }
         }

         if (best == null) {
            continue;
         }

         Vec3 at = best.getBoundingBox().getCenter();
         ThanosTargets.hurt(this.c.thanos, best, damage, 1.0F);
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.REALITY_SHARD, this.c.thanos.getId(), best.getId(), origin, at.subtract(origin), 1.0F, 0);
      }

      ThanosSounds.play(this.c.level, this.c.thanos.position(), ThanosSounds.REALITY_MIRAGE, 1.8F, 1.5F, 64.0);
   }

   private boolean sees(Vec3 from, Vec3 to) {
      return this.c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.c.thanos)).getType() == HitResult.Type.MISS;
   }

   private Vec3 floor(Vec3 at) {
      HitResult hit = this.c.level.clip(new ClipContext(at.add(0.0, 1.5, 0.0), at.add(0.0, -8.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
         this.c.thanos));
      return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
   }

   private Vec3 jitter(double r) {
      return new Vec3((this.c.level.random.nextDouble() - 0.5) * 2.0 * r, 0.0, (this.c.level.random.nextDouble() - 0.5) * 2.0 * r);
   }

   public int zones() {
      return this.zones.size();
   }

   public void clear() {
      this.planned.clear();
      this.zones.clear();
      this.mirages.clear();
      this.mirageTicks = 0;
   }
}