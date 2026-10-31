package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Mind;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Yellow. The gaze (look away during the wind-up or be disoriented: the screen sways and false
 * images of him appear for a moment; controls are never taken) and the pulses (rings along the
 * ground: jump them). Mobs caught by the gaze lose their target and stagger.
 */
public final class MindStone {
   private static final int MAX_PULSES = 3;
   private final ThanosCombat c;
   private final List<Pulse> pulses = new ArrayList<>();

   public MindStone(ThanosCombat combat) {
      this.c = combat;
   }

   private static final class Pulse {
      final Vec3 center;
      final float damage;
      final List<Set<UUID>> hit = new ArrayList<>();
      int age;

      Pulse(Vec3 center, float damage) {
         this.center = center;
         this.damage = damage;
         for (int i = 0; i < Mind.RINGS; i++) {
            this.hit.add(new HashSet<>());
         }
      }
   }

   public void tick() {
      for (Iterator<Pulse> it = this.pulses.iterator(); it.hasNext(); ) {
         Pulse p = it.next();
         p.age++;
         boolean running = false;
         for (int ring = 0; ring < Mind.RINGS; ring++) {
            double r = Mind.ringRadius(ring, p.age);
            if (r < Mind.RING_MAX) {
               running = true;
            }

            if (r <= 0.5 || r >= Mind.RING_MAX) {
               continue;
            }

            for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, p.center, r + Mind.RING_BAND)) {
               double dx = e.getX() - p.center.x;
               double dz = e.getZ() - p.center.z;
               if (!p.hit.get(ring).contains(e.getUUID()) && Mind.ringHits(Math.sqrt(dx * dx + dz * dz), e.getY() - p.center.y, r)) {
                  p.hit.get(ring).add(e.getUUID());
                  ThanosTargets.hurt(this.c.thanos, e, p.damage, 1.0F);
                  this.disorient(e, 24);
               }
            }
         }

         if (!running) {
            it.remove();
         }
      }
   }

   /** The eyes and the stone light up: everyone close enough is told to look away. */
   public void chargeGaze(int ticks) {
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MIND_GAZE_CHARGE, this.c.thanos.getId(), -1, this.c.eyes(), Vec3.ZERO, (float)Mind.GAZE_RANGE,
         ticks);
      ThanosSounds.play(this.c.level, this.c.thanos.position(), ThanosSounds.MIND_GAZE, 1.6F, 0.9F, 64.0);
   }

   /** Whoever still looks at him is caught. */
   public void gaze(float damage) {
      Vec3 eyes = this.c.eyes();
      int caught = 0;
      for (ServerPlayer p : this.c.heroes()) {
         Vec3 to = eyes.subtract(p.getEyePosition());
         double distance = to.length();
         if (distance > Mind.GAZE_RANGE) {
            continue;
         }

         double dot = distance < 1.0E-3 ? 1.0 : p.getViewVector(1.0F).dot(to.scale(1.0 / distance));
         if (Mind.caught(dot, distance)) {
            caught++;
            ThanosTargets.hurt(this.c.thanos, p, damage, 1.0F);
            this.disorient(p, Mind.disorientTicks(dot, distance));
         } else {
            ThanosTargets.fxTo(p, ThanosFxS2CPacket.MIND_RESIST, this.c.thanos.getId(), p.getId(), p.getEyePosition(), Vec3.ZERO, 1.0F, 0);
         }
      }

      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, this.c.thanos.position(), Mind.GAZE_RANGE * 0.6)) {
         if (e instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Mind.DISORIENT_TICKS, 3, false, false));
         }
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MIND_GAZE_HIT, this.c.thanos.getId(), -1, eyes, this.c.facing(), caught, 0);
   }

   private void disorient(LivingEntity e, int ticks) {
      if (ticks <= 0) {
         return;
      }

      if (e instanceof ServerPlayer p) {
         ThanosTargets.fxTo(p, ThanosFxS2CPacket.MIND_GAZE_HIT, this.c.thanos.getId(), p.getId(), p.getEyePosition(), Vec3.ZERO, 1.0F, ticks);
         ThanosTargets.fxTo(p, ThanosFxS2CPacket.MIND_FALSE_TARGETS, this.c.thanos.getId(), p.getId(), this.c.thanos.position(), Vec3.ZERO, Mind.FALSE_TARGETS,
            ticks);
      } else if (e instanceof Mob mob && !(e instanceof Player)) {
         mob.setTarget(null);
      }
   }

   /** Three rings out from him along the ground, one after another. */
   public void pulse(Vec3 at, float damage) {
      if (this.pulses.size() >= MAX_PULSES) {
         this.pulses.remove(0);
      }

      this.pulses.add(new Pulse(at, damage));
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MIND_PULSE, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)Mind.RING_MAX, Mind.RINGS);
      ThanosSounds.play(this.c.level, at, ThanosSounds.MIND_PULSE, 2.0F, 1.0F, 64.0);
   }

   public void clear() {
      this.pulses.clear();
   }
}