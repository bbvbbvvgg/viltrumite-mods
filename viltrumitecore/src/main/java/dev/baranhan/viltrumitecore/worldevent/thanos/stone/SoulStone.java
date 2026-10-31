package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Soul;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Orange. Tethers to the heroes that drain their life into him (break them by running out of
 * reach or by staggering him), and the nova: a burst of soul light with ghostly silhouettes. No
 * creature is ever summoned: the silhouettes are client visuals.
 */
public final class SoulStone {
   /** All the healing of one tether set: at most this share of his max health. */
   private static final float HEAL_CAP = 0.03F;
   private static final int MAX_TETHERS = 6;
   private final ThanosCombat c;
   private final List<Tether> tethers = new ArrayList<>();
   private float healed;

   public SoulStone(ThanosCombat combat) {
      this.c = combat;
   }

   private static final class Tether {
      final LivingEntity hero;
      final Vec3 anchor;
      final boolean illusion;
      final int life;
      int age;

      Tether(LivingEntity hero, Vec3 anchor, boolean illusion, int life) {
         this.hero = hero;
         this.anchor = anchor;
         this.illusion = illusion;
         this.life = life;
      }

      Vec3 from(ThanosCombat c) {
         return this.illusion ? this.anchor : c.handPos();
      }
   }

   public void tick() {
      for (Iterator<Tether> it = this.tethers.iterator(); it.hasNext(); ) {
         Tether t = it.next();
         LivingEntity h = t.hero;
         Vec3 from = t.from(this.c);
         if (!h.isAlive() || h.level() != this.c.level || !ThanosTargets.victim(h, this.c.thanos) || !Soul.tetherHolds(h.position().distanceTo(from))) {
            this.snap(t);
            it.remove();
            continue;
         }

         if (++t.age >= t.life) {
            ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SOUL_TETHER_BREAK, this.c.thanos.getId(), h.getId(), h.position(), Vec3.ZERO, 0.0F, 0);
            it.remove();
            continue;
         }

         // A slow drag toward the source: standing still is not enough to break it.
         Vec3 to = from.subtract(h.position());
         double d = to.length();
         if (d > 4.0) {
            ThanosTargets.push(h, to.scale(0.035 / d));
         }

         if (t.age % Soul.DRAIN_EVERY == 0) {
            float damage = ThanosMove.SOUL_TETHER.damage * this.c.scale();
            if (ThanosTargets.hurt(this.c.thanos, h, damage, 1.0F) && !t.illusion) {
               float cap = HEAL_CAP * this.c.thanos.getMaxHealth();
               float heal = Math.min(Soul.HEAL_PER_DRAIN, Math.max(0.0F, cap - this.healed));
               if (heal > 0.0F) {
                  this.healed += heal;
                  this.c.thanos.stoneHeal(heal);
               }
            }

            ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SOUL_DRAIN, h.getId(), this.c.thanos.getId(), h.position().add(0.0, h.getBbHeight() * 0.6, 0.0),
               from.subtract(h.position()), t.illusion ? 0.0F : 1.0F, 0);
         }
      }

      if (this.tethers.isEmpty()) {
         this.healed = 0.0F;
      }
   }

   /** Tethers from {@code from} to the closest heroes (more heroes, more tethers, at most 3 per cast). */
   public void tether(Vec3 from, int ticks, boolean illusion) {
      List<ServerPlayer> heroes = new ArrayList<>(this.c.heroes());
      heroes.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
      int n = Soul.tethers(this.c.event.heroCount());
      int made = 0;
      for (ServerPlayer p : heroes) {
         if (made >= n || this.tethers.size() >= MAX_TETHERS) {
            break;
         }

         if (p.position().distanceTo(from) > Soul.TETHER_RANGE || this.tethered(p, illusion)) {
            continue;
         }

         this.tethers.add(new Tether(p, from, illusion, Math.max(1, ticks)));
         made++;
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SOUL_TETHER, this.c.thanos.getId(), p.getId(), from, Vec3.ZERO, illusion ? 0.0F : 1.0F, ticks);
      }

      if (made > 0) {
         ThanosSounds.play(this.c.level, from, ThanosSounds.SOUL_TETHER, 1.6F, illusion ? 1.2F : 0.9F, 64.0);
      }
   }

   private boolean tethered(LivingEntity hero, boolean illusion) {
      for (Tether t : this.tethers) {
         if (t.hero == hero && t.illusion == illusion) {
            return true;
         }
      }

      return false;
   }

   /** Burst of soul light: damage falls off to the edge, everything is thrown out. */
   public void nova(Vec3 at, double radius, float damage) {
      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, at, radius)) {
         double distance = e.position().distanceTo(at);
         float dmg = Soul.novaDamage(damage, distance * (Soul.NOVA_RADIUS / Math.max(1.0, radius)));
         if (dmg > 0.0F) {
            ThanosTargets.hurt(this.c.thanos, e, dmg, 1.0F);
            Vec3 out = e.position().subtract(at);
            Vec3 flat = new Vec3(out.x, 0.0, out.z);
            flat = flat.lengthSqr() < 1.0E-4 ? this.c.facing() : flat.normalize();
            ThanosTargets.push(e, new Vec3(flat.x * 1.2, 0.6, flat.z * 1.2));
         }
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SOUL_NOVA, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)radius, this.c.level.random.nextInt());
      ThanosSounds.play(this.c.level, at, ThanosSounds.SOUL_NOVA, 2.4F, 0.9F, 96.0);
   }

   private void snap(Tether t) {
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SOUL_TETHER_BREAK, this.c.thanos.getId(), t.hero.getId(), t.hero.position(), Vec3.ZERO, 1.0F, 1);
   }

   /** Every tether snaps at once (he was staggered). */
   public void breakAll() {
      for (Tether t : this.tethers) {
         this.snap(t);
      }

      this.tethers.clear();
   }

   public int tethers() {
      return this.tethers.size();
   }

   public void clear() {
      this.tethers.clear();
      this.healed = 0.0F;
   }
}