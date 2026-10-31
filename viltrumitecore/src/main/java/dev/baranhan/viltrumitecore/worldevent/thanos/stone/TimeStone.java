package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPlacement;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Time;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Green. A dome where time nearly stops (heroes crawl, projectiles hang), and his own rewind: a
 * few seconds back in place with part of the recent damage undone (capped by the rules).
 */
public final class TimeStone {
   private static final int MAX_DOMES = 3;
   private final ThanosCombat c;
   private final List<Dome> domes = new ArrayList<>();

   public TimeStone(ThanosCombat combat) {
      this.c = combat;
   }

   private static final class Dome {
      final Vec3 center;
      final double radius;
      final int life;
      final Set<UUID> slowed = new HashSet<>();
      int age;

      Dome(Vec3 center, double radius, int life) {
         this.center = center;
         this.radius = radius;
         this.life = life;
      }
   }

   public void tick() {
      for (Iterator<Dome> it = this.domes.iterator(); it.hasNext(); ) {
         Dome d = it.next();
         double factor = Time.slowFactor(Math.round(d.age * (Time.DOME_LIFE / (float)Math.max(1, d.life))));
         double r2 = d.radius * d.radius;
         for (Projectile p : this.c.level.getEntitiesOfClass(Projectile.class, new net.minecraft.world.phys.AABB(d.center, d.center).inflate(d.radius))) {
            if (p.position().distanceToSqr(d.center) <= r2 && d.slowed.add(p.getUUID())) {
               p.setDeltaMovement(p.getDeltaMovement().scale(Time.PROJECTILE_FACTOR));
               p.hurtMarked = true;
            }
         }

         for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, d.center, d.radius)) {
            if (!(e instanceof Player) && e.position().distanceToSqr(d.center) <= r2) {
               // Players crawl through their own client (it draws the same dome).
               e.setDeltaMovement(e.getDeltaMovement().multiply(factor, 1.0, factor));
            }
         }

         if (++d.age >= d.life) {
            it.remove();
         }
      }
   }

   /** Marks the dome during the wind-up (a thin green ring on the ground). */
   public void telegraphDome(Vec3 at, int ticks) {
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.TIME_DOME, this.c.thanos.getId(), -1, at, Vec3.ZERO, 0.0F, ticks);
   }

   public void dome(Vec3 at, double radius, int life) {
      if (this.domes.size() >= MAX_DOMES) {
         this.domes.remove(0);
      }

      this.domes.add(new Dome(at, radius, life));
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.TIME_DOME, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)radius, life);
      ThanosSounds.play(this.c.level, at, ThanosSounds.TIME_DOME, 2.0F, 0.9F, 64.0);
   }

   /** Back where he stood three seconds ago, with part of the damage of the last seconds undone. */
   public void rewind() {
      float max = this.c.thanos.getMaxHealth();
      float heal = ThanosRules.rewindHeal(this.c.thanos.damageCap().recent(), max);
      Vec3 from = this.c.thanos.position();
      double[] back = this.c.thanos.trail().back(ThanosRules.REWIND_BACK);
      Vec3 to = from;
      if (back != null) {
         Vec3 spot = ThanosPlacement.standable(this.c.level, this.c.thanos, this.c.thanos.getDimensions(this.c.thanos.getPose()),
            new Vec3(back[0], back[1], back[2]), 2, 3);
         if (spot != null) {
            to = spot;
         }
      }

      this.c.thanos.stoneHeal(heal);
      this.c.thanos.damageCap().clearRecent();
      if (to.distanceToSqr(from) > 0.25) {
         this.c.thanos.teleportTo(to.x, to.y, to.z);
         this.c.thanos.setDeltaMovement(Vec3.ZERO);
      }

      this.c.thanos.trail().clear();
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.TIME_REWIND, this.c.thanos.getId(), -1, from, to.subtract(from), heal, ThanosRules.REWIND_BACK);
      ThanosSounds.play(this.c.level, to, ThanosSounds.TIME_REWIND, 2.0F, 1.0F, 64.0);
   }

   /** Is a point inside a running dome (tests, client parity)? */
   public boolean inside(Vec3 at) {
      for (Dome d : this.domes) {
         if (at.distanceToSqr(d.center) <= d.radius * d.radius) {
            return true;
         }
      }

      return false;
   }

   public void clear() {
      this.domes.clear();
   }
}