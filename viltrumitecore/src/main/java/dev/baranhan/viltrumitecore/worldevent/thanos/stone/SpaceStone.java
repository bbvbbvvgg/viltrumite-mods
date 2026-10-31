package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPlacement;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Space;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Blue. Steps through a portal behind a target, grabs a target across space to his fist, turns
 * projectiles back with a ward, and opens the portals of the Space combos (fist through a portal
 * beside the target, the ring of portals of the trinity).
 */
public final class SpaceStone {
   private static final int MAX_REDIRECTED = 64;
   private final ThanosCombat c;
   @Nullable
   private LivingEntity pulled;
   private int pullTick;
   private int pullTicks;
   private int ward;
   private final Set<UUID> redirected = new HashSet<>();
   private final List<Vec3> ring = new ArrayList<>();
   private int ringTicks;
   @Nullable
   private Vec3 stepTo;

   public SpaceStone(ThanosCombat combat) {
      this.c = combat;
   }

   public void tick() {
      if (this.pulled != null) {
         this.tickPull();
      }

      if (this.ward > 0) {
         this.ward--;
         this.tickWard();
         if (this.ward == 0) {
            this.redirected.clear();
         }
      }

      if (this.ringTicks > 0 && --this.ringTicks == 0) {
         this.ring.clear();
      }
   }

   // ---- step -----------------------------------------------------------------------------------
   /** Picks the exit behind the target and opens both portals; no free spot = no step. */
   public void planStep() {
      LivingEntity t = this.c.target;
      if (t == null) {
         this.stepTo = null;
         this.c.abort();
         return;
      }

      Vec3 look = t.getLookAngle();
      double[] xz = Space.behind(t.getX(), t.getZ(), look.x, look.z, this.c.thanos.getX(), this.c.thanos.getZ());
      Vec3 exit = ThanosPlacement.near(this.c.level, this.c.thanos, this.c.thanos.getDimensions(this.c.thanos.getPose()),
         new Vec3(xz[0], t.getY(), xz[1]), Space.STEP_BEHIND, 3, 6);
      if (exit == null) {
         this.stepTo = null;
         this.c.abort();
         return;
      }

      this.stepTo = exit;
      this.c.aux = exit;
      int life = this.c.telegraph + 12;
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), -1, this.c.thanos.position().add(this.c.facing().scale(1.2)),
         this.c.facing(), 2.4F, life);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), -1, exit, exit.subtract(t.position()), 2.4F, life);
      ThanosSounds.play(this.c.level, this.c.thanos.position(), ThanosSounds.SPACE_OPEN, 1.6F, 1.0F, 64.0);
      ThanosSounds.play(this.c.level, exit, ThanosSounds.SPACE_OPEN, 1.6F, 1.0F, 64.0);
   }

   /** Through the portal: he comes out behind the target, already facing it. */
   public void step() {
      Vec3 to = this.stepTo;
      this.stepTo = null;
      if (to == null) {
         return;
      }

      Vec3 from = this.c.thanos.position();
      this.c.thanos.teleportTo(to.x, to.y, to.z);
      this.c.thanos.setDeltaMovement(Vec3.ZERO);
      if (this.c.target != null) {
         this.c.thanos.faceTowards(this.c.target.position(), 180.0F);
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_STEP, this.c.thanos.getId(), -1, from, to.subtract(from), 1.0F, 0);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_STEP, this.c.thanos.getId(), -1, to, to.subtract(from), 1.0F, 1);
      ThanosSounds.play(this.c.level, to, ThanosSounds.SPACE_STEP, 2.0F, 1.0F, 64.0);
   }

   // ---- pull -------------------------------------------------------------------------------------
   /** Grabs the target across space toward his fist for {@code ticks}. */
   public void pull(@Nullable LivingEntity target, int ticks) {
      if (target == null || !ThanosTargets.victim(target, this.c.thanos)) {
         this.c.abort();
         return;
      }

      this.pulled = target;
      this.pullTick = 0;
      this.pullTicks = Math.max(1, ticks);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PULL, this.c.thanos.getId(), target.getId(), target.position(), Vec3.ZERO, 1.0F, ticks);
      // heard where the victim is: the air rushes past them toward the fist
      ThanosSounds.play(this.c.level, target.position(), ThanosSounds.SPACE_PULL, 1.8F, 1.0F, 64.0);
   }

   private void tickPull() {
      LivingEntity t = this.pulled;
      if (t == null || !t.isAlive() || t.level() != this.c.level || !ThanosTargets.victim(t, this.c.thanos) || this.pullTick >= this.pullTicks + 2) {
         this.pulled = null;
         return;
      }

      Vec3 hand = this.c.hand();
      Vec3 to = hand.subtract(t.position().add(0.0, t.getBbHeight() * 0.5, 0.0));
      double distance = to.length();
      double speed = Space.pullSpeed(distance, this.pullTick++);
      if (speed > 0.0) {
         ThanosTargets.drag(t, to.scale(speed / Math.max(1.0E-3, distance)));
      }
   }

   /** End of the grab: whoever reached the fist takes the hit. */
   public void endPull(float damage) {
      LivingEntity t = this.pulled;
      this.pulled = null;
      if (t != null && t.isAlive() && t.distanceToSqr(this.c.hand()) <= (Space.PULL_STOP + 2.0) * (Space.PULL_STOP + 2.0)) {
         ThanosTargets.hurt(this.c.thanos, t, damage, 1.0F);
         Vec3 f = this.c.facing();
         ThanosTargets.push(t, new Vec3(f.x * 0.8, 0.4, f.z * 0.8));
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MELEE_HIT, this.c.thanos.getId(), t.getId(), t.position().add(0.0, 1.0, 0.0), f, 1.0F, 1);
         ThanosSounds.play(this.c.level, t.position(), ThanosSounds.HIT, 1.6F, 0.9F, 48.0);
      }
   }

   // ---- ward ---------------------------------------------------------------------------------------
   /** For {@code ticks} projectiles flying at him are turned back the way they came. */
   public void ward(int ticks) {
      this.ward = Math.max(this.ward, ticks + this.c.telegraph);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_WARD, this.c.thanos.getId(), -1, this.c.chest(), Vec3.ZERO, (float)Space.WARD_RADIUS,
         this.ward);
      ThanosSounds.play(this.c.level, this.c.thanos.position(), ThanosSounds.SPACE_WARD, 1.6F, 1.0F, 64.0);
   }

   private void tickWard() {
      Vec3 chest = this.c.chest();
      double r = Space.WARD_RADIUS;
      for (Projectile p : this.c.level.getEntitiesOfClass(Projectile.class, this.c.thanos.getBoundingBox().inflate(r))) {
         if (p.getOwner() == this.c.thanos || this.redirected.contains(p.getUUID()) || this.redirected.size() >= MAX_REDIRECTED) {
            continue;
         }

         Vec3 v = p.getDeltaMovement();
         Vec3 to = chest.subtract(p.position());
         if (v.lengthSqr() < 0.04 || v.dot(to) <= 0.0 || to.lengthSqr() > r * r) {
            continue;
         }

         double[] back = Space.redirect(v.x, v.y, v.z);
         p.setDeltaMovement(back[0], back[1], back[2]);
         p.hurtMarked = true;
         this.redirected.add(p.getUUID());
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_REDIRECT, this.c.thanos.getId(), p.getId(), p.position(), new Vec3(back[0], back[1], back[2]),
            1.0F, 0);
      }
   }

   public boolean warding() {
      return this.ward > 0;
   }

   // ---- combos -------------------------------------------------------------------------------------
   /** Space+Power: a portal by his fist, its twin beside the target. */
   public void planPortalPunch() {
      LivingEntity t = this.c.target;
      if (t == null) {
         this.c.abort();
         return;
      }

      Vec3 toThanos = this.c.thanos.position().subtract(t.position());
      Vec3 flat = new Vec3(toThanos.x, 0.0, toThanos.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
      // Beside the target (a quarter turn off the line to Thanos), at chest height.
      Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
      Vec3 exit = t.position().add(side.scale(2.6)).add(flat.scale(0.8)).add(0.0, t.getBbHeight() * 0.6, 0.0);
      this.c.aux = exit;
      int life = this.c.telegraph + 16;
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), -1, this.c.hand(), this.c.facing(), 1.6F, life);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), t.getId(), exit, t.position().subtract(exit), 1.6F, life);
      ThanosSounds.play(this.c.level, exit, ThanosSounds.SPACE_OPEN, 1.8F, 1.2F, 64.0);
   }

   /** The fist comes out of the target's portal (aimed where the target is now). */
   public void portalPunch(float damage) {
      Vec3 exit = this.c.aux;
      LivingEntity t = this.c.target;
      Vec3 at = t != null ? t.getBoundingBox().getCenter() : this.c.aim;
      Vec3 dir = at.subtract(exit);
      this.c.power.punch(exit, dir, damage, 4.5, false);
   }

   /** Trinity: a ring of portals around him that the reality shards fly out of. */
   public void portalRing(int ticks) {
      this.ring.clear();
      int n = 5;
      double r = 6.0;
      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2.0 / n + this.c.level.random.nextDouble() * 0.3;
         Vec3 at = this.c.thanos.position().add(Math.cos(a) * r, 2.6 + this.c.level.random.nextDouble() * 1.4, Math.sin(a) * r);
         this.ring.add(at);
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SPACE_PORTAL, this.c.thanos.getId(), -1, at, at.subtract(this.c.chest()), 1.4F, ticks);
      }

      this.ringTicks = ticks;
      ThanosSounds.play(this.c.level, this.c.thanos.position(), ThanosSounds.SPACE_OPEN, 2.0F, 0.8F, 64.0);
   }

   /** Portals of the running ring (empty when none). */
   public List<Vec3> ring() {
      return this.ring;
   }

   public void clear() {
      this.pulled = null;
      this.ward = 0;
      this.redirected.clear();
      this.ring.clear();
      this.ringTicks = 0;
      this.stepTo = null;
   }
}