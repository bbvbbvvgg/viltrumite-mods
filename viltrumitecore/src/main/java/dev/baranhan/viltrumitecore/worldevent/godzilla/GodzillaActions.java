package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaPart;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.world.collapse.StructuralDamage;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lands the moves on the server, tick by tick inside each move's contact window: the claw arcs and the tail whip
 * (moving hit spheres that cut through buildings and hit what they touch once per move), the stomp's shock wave
 * and crater, the body slam (a lunge and big queued smashes), the roar's pressure wave (windows burst, people are
 * pushed back), the fall and the death impacts along the lying body, the footfalls, and the body pushing through
 * the city while it walks.
 */
final class GodzillaActions {
   /** Hardest block a body blow breaks (stone, concrete, iron; obsidian and the like survive). */
   static final float BODY_HARDNESS = 10.0F;
   /** Parts swept by the walking body, a few per tick in turn. */
   private static final int[] SWEEP = {
      GodzillaRules.P_LEG_L, GodzillaRules.P_LEG_R, GodzillaRules.P_HIPS, GodzillaRules.P_BELLY, GodzillaRules.P_CHEST,
      GodzillaRules.P_NECK, GodzillaRules.P_HEAD, GodzillaRules.P_ARM_L, GodzillaRules.P_ARM_R,
      GodzillaRules.P_TAIL, GodzillaRules.P_TAIL + 1, GodzillaRules.P_TAIL + 2, GodzillaRules.P_TAIL + 3, GodzillaRules.P_TAIL + 4,
      GodzillaRules.P_TAIL + 5, GodzillaRules.P_TAIL + 6};
   static final int SWEEP_PER_TICK = 3;

   private GodzillaActions() {
   }

   /** The move's work at move tick {@code t}. */
   static void contact(ServerLevel level, GodzillaSession s, GodzillaEntity e, GodzillaMove move, int t) {
      switch (move) {
         case ROAR -> roar(level, s, e, t);
         case CHARGE -> s.breath.charge(level, s, e, t);
         case FIRE -> s.breath.fire(level, s, e, t);
         case BREATH_END -> {
            if (t == 0) {
               s.breath.end(e);
            }
         }
         case CLAW_L, CLAW_R -> claw(level, s, e, move, t);
         case TAIL -> tail(level, s, e, t);
         case STOMP -> {
            if (t == move.contact) {
               stomp(level, s, e);
            }
         }
         case SLAM -> slam(level, s, e, t);
         case FALL -> {
            if (t == move.contact) {
               bodyImpact(level, s, e, GodzillaRules.FALL_DAMAGE, false);
            }
         }
         case RISE -> {
            if (t == move.contact) {
               GodzillaNet.fx(level, GodzillaFxS2CPacket.RISE, e.bodyPoint(GodzillaRules.PART_REST[GodzillaRules.P_HIPS]), Vec3.ZERO, 1.0F, 0);
            }
         }
         case DEATH -> {
            if (t == move.contact) {
               bodyImpact(level, s, e, GodzillaRules.FALL_DAMAGE * 1.5F, true);
            }
         }
         default -> {
         }
      }
   }

   /** The move after a finished one (CHARGE → FIRE → BREATH_END, FALL → DOWN → RISE), or NONE. */
   static void next(ServerLevel level, GodzillaSession s, GodzillaEntity e, GodzillaMove move) {
      switch (move) {
         case CHARGE -> s.start(e, GodzillaMove.FIRE, GodzillaRules.fireTicks(s.rage(), s.phase == GodzillaPhase.FINALE));
         case FIRE -> {
            s.breath.end(e);
            s.start(e, GodzillaMove.BREATH_END, 0);
         }
         case FALL -> s.start(e, GodzillaMove.DOWN, 0);
         case DOWN -> s.start(e, GodzillaMove.RISE, 0);
         case DEATH -> {
         }
         default -> e.endMove();
      }
   }

   static boolean finished(GodzillaEntity e, GodzillaMove move, int t) {
      if (move == GodzillaMove.NONE || move == GodzillaMove.DEATH) {
         return false;
      }
      int length = move == GodzillaMove.FIRE ? e.fireLength() : move.length;
      return t >= length;
   }

   // ------------------------------------------------------------------ moves

   private static void roar(ServerLevel level, GodzillaSession s, GodzillaEntity e, int t) {
      GodzillaMove move = GodzillaMove.ROAR;
      if (t == move.contact) {
         GodzillaNet.always(level, GodzillaFxS2CPacket.ROAR_WAVE, e.mouth(), e.headDir(), GodzillaRules.ROAR_WAVE, 0, GodzillaRules.FX_FAR);
         CityManager city = CityManager.of(level);
         if (city != null) {
            Vec3 m = e.mouth();
            city.danger(m.x, e.getY(), m.z, GodzillaRules.HEIGHT * 1.4F, 300);
         }
      }
      if (move.inContact(t) && (t & 1) == 0) {
         Vec3 dir = e.headDir();
         s.destruction.shatter(level, e.mouth(), new Vec3(dir.x, 0.0, dir.z).normalize(), GodzillaRules.ROAR_WAVE * 1.5, 10);
         if (t % 6 == 0) {
            Vec3 m = e.mouth();
            AABB box = new AABB(m, m).inflate(GodzillaRules.ROAR_WAVE);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof GodzillaEntity))) {
               Vec3 to = living.position().subtract(m);
               double d = to.length();
               if (d < 1.0 || d > GodzillaRules.ROAR_WAVE || to.normalize().dot(dir) < 0.25 || !HeroRegistry.allowsImpulse(living)) {
                  continue;
               }
               Vec3 push = to.normalize().scale(0.35 * (1.0 - d / GodzillaRules.ROAR_WAVE));
               living.push(push.x, 0.05, push.z);
               living.hurtMarked = true;
            }
         }
      }
   }

   /** Hand position of a claw swipe in the world: an arc about the shoulder, high to low across the front. */
   static Vec3 clawHand(GodzillaEntity e, GodzillaMove move, float t) {
      int side = move == GodzillaMove.CLAW_L ? 1 : -1;
      float a = GodzillaRules.clawSweep(t, move) * Mth.DEG_TO_RAD;
      float u = Mth.clamp((t - move.contact) / (float)(move.contactEnd - move.contact), 0.0F, 1.0F);
      float arc = GodzillaRules.CLAW_ARC / GodzillaRules.HEIGHT;
      double l = side * (GodzillaRules.SHOULDER_L[0] + Mth.sin(a) * arc);
      double f = GodzillaRules.SHOULDER_L[2] + Mth.cos(a) * arc;
      double up = 0.56 - 0.2 * u;
      return e.bodyPoint(l, up, f);
   }

   private static void claw(ServerLevel level, GodzillaSession s, GodzillaEntity e, GodzillaMove move, int t) {
      if (!move.inContact(t)) {
         return;
      }
      Vec3 hand = clawHand(e, move, t);
      Vec3 prev = clawHand(e, move, t - 1);
      Vec3 sweep = hand.subtract(prev);
      Vec3 push = sweep.lengthSqr() < 1.0E-4 ? GodzillaEntity.forward(e.getYRot()) : sweep.normalize();
      int hit = hurtSphere(level, s, e, hand, GodzillaRules.CLAW_RADIUS, GodzillaRules.CLAW_DAMAGE, 1.4, false, GodzillaDamage.crush(level, e), s.hitThisMove);
      int blocks = s.destruction.smashNow(level, hand, GodzillaRules.CLAW_RADIUS * 0.62, 1.0, BODY_HARDNESS, push.scale(0.6), 0.05F, 260, "claw");
      if ((hit > 0 || blocks > 12) && !s.clawFx) {
         s.clawFx = true;
         GodzillaNet.fx(level, GodzillaFxS2CPacket.CLAW_HIT, hand, push, 1.0F, blocks);
      }
   }

   private static void tail(ServerLevel level, GodzillaSession s, GodzillaEntity e, int t) {
      GodzillaMove move = GodzillaMove.TAIL;
      if (!move.inContact(t)) {
         return;
      }
      int side = e.moveArg() >= 0 ? 1 : -1;
      Vec3 lf = GodzillaEntity.left(e.getYRot()).scale(side);
      GodzillaPart[] parts = e.parts();
      for (int k = 2; k < GodzillaRules.TAIL_SEGMENTS; k++) {
         GodzillaPart p = parts[GodzillaRules.P_TAIL + k];
         Vec3 c = p.getBoundingBox().getCenter();
         float r = GodzillaRules.PART_SIZE[GodzillaRules.P_TAIL + k][0] * GodzillaRules.HEIGHT * 0.6F;
         int hit = hurtSphere(level, s, e, c, r + 2.0F, GodzillaRules.TAIL_DAMAGE, 2.2, false, GodzillaDamage.crush(level, e), s.hitThisMove);
         int blocks = s.destruction.smashNow(level, c, r, 1.0, BODY_HARDNESS, lf.scale(0.9).add(0.0, 0.25, 0.0), 0.05F, 140, "tail");
         if ((hit > 0 || blocks > 20) && !s.tailFx) {
            s.tailFx = true;
            GodzillaNet.fx(level, GodzillaFxS2CPacket.TAIL_HIT, c, lf, r, blocks);
         }
      }
   }

   private static void stomp(ServerLevel level, GodzillaSession s, GodzillaEntity e) {
      boolean left = e.moveArg() >= 0;
      Vec3 foot = e.foot(left);
      float wave = GodzillaRules.STOMP_WAVE * (s.rage() ? 1.2F : 1.0F);
      AABB box = new AABB(foot, foot).inflate(wave, 12.0, wave);
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof GodzillaEntity))) {
         double dx = living.getX() - foot.x;
         double dz = living.getZ() - foot.z;
         double d = Math.sqrt(dx * dx + dz * dz);
         if (d > wave || living.getY() - foot.y > 10.0) {
            continue;
         }
         float f = Math.max(0.25F, GodzillaRules.falloff(d, wave));
         if (living.hurt(GodzillaDamage.crush(level, e), GodzillaRules.STOMP_DAMAGE * f)) {
            s.noteHit(living);
         }
         if (HeroRegistry.allowsImpulse(living)) {
            Vec3 out = d < 0.5 ? Vec3.ZERO : new Vec3(dx / d, 0.0, dz / d);
            living.push(out.x * 0.9 * f, 0.55 + 0.6 * f, out.z * 0.9 * f);
            living.hurtMarked = true;
         }
      }
      Vec3 crater = foot.add(0.0, 1.0, 0.0);
      StructuralDamage.run(GodzillaDestruction.CAUSE, () -> StructuralDamage.impact(level, foot, GodzillaDestruction.CAUSE));
      s.destruction.smashNow(level, crater, 9.0, 2.0, BODY_HARDNESS, new Vec3(0.0, 0.7, 0.0), 0.10F, 520, "stomp");
      s.destruction.queue(foot.add(0.0, 2.0, 0.0), wave * 0.45, 2.5, 2.0F, new Vec3(0.0, 0.5, 0.0), 0.02F, 30, "stomp");
      GodzillaNet.always(level, GodzillaFxS2CPacket.STOMP, foot, Vec3.ZERO, wave, 0, GodzillaRules.FX_FAR);
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.blast(foot.x, foot.y, foot.z, wave);
         city.danger(foot.x, foot.y, foot.z, wave * 2.5F, 200);
      }
   }

   private static void slam(ServerLevel level, GodzillaSession s, GodzillaEntity e, int t) {
      GodzillaMove move = GodzillaMove.SLAM;
      if (!move.inContact(t)) {
         return;
      }
      e.lunge = GodzillaRules.SLAM_LUNGE / (move.contactEnd - move.contact + 1);
      Vec3 fw = GodzillaEntity.forward(e.getYRot());
      if (t == move.contact) {
         Vec3 c = e.bodyPoint(0.0, 0.46, 0.30);
         s.destruction.queue(c, GodzillaRules.SLAM_RADIUS, 0.9, BODY_HARDNESS, fw.scale(0.8).add(0.0, 0.2, 0.0), 0.03F, 50, "slam");
         StructuralDamage.impact(level, c, GodzillaDestruction.CAUSE);
         GodzillaNet.always(level, GodzillaFxS2CPacket.SMASH, c, fw, GodzillaRules.SLAM_RADIUS, 2000, GodzillaRules.FX_FAR);
      } else if (t == move.contact + 6) {
         s.destruction.queue(e.bodyPoint(0.0, 0.30, 0.34), GodzillaRules.SLAM_RADIUS * 0.75, 0.9, BODY_HARDNESS, fw.scale(0.7), 0.03F, 50, "slam");
      } else if (t == move.contact + 12) {
         s.destruction.queue(e.bodyPoint(0.0, 0.64, 0.26), GodzillaRules.SLAM_RADIUS * 0.7, 1.0, BODY_HARDNESS, fw.scale(0.7), 0.03F, 45, "slam");
      }
      if (t <= move.contact + 10) {
         hurtSphere(level, s, e, e.bodyPoint(0.0, 0.45, 0.32), GodzillaRules.SLAM_RADIUS, GodzillaRules.SLAM_DAMAGE, 1.8, true,
            GodzillaDamage.crush(level, e), s.hitThisMove);
      }
   }

   /** The body hits the ground (fall, death): smashes along the lying body, a shock wave, a big city blast. */
   private static void bodyImpact(ServerLevel level, GodzillaSession s, GodzillaEntity e, float damage, boolean death) {
      GodzillaPart[] parts = e.parts();
      int[] along = {GodzillaRules.P_HEAD, GodzillaRules.P_NECK, GodzillaRules.P_CHEST, GodzillaRules.P_BELLY, GodzillaRules.P_HIPS,
         GodzillaRules.P_TAIL, GodzillaRules.P_TAIL + 1};
      float r = 0.11F * GodzillaRules.HEIGHT;
      for (int i : along) {
         Vec3 c = parts[i].getBoundingBox().getCenter();
         s.destruction.queue(c, r, 1.0, BODY_HARDNESS, new Vec3(0.0, -0.3, 0.0), 0.02F, 60, death ? "death" : "fall");
         hurtSphere(level, s, e, c, r * 1.25F, damage, 1.2, true, GodzillaDamage.crush(level, e), null);
         StructuralDamage.impact(level, c, GodzillaDestruction.CAUSE);
      }
      Vec3 belly = parts[GodzillaRules.P_BELLY].getBoundingBox().getCenter();
      Vec3 lf = GodzillaEntity.left(e.getYRot());
      GodzillaNet.always(level, death ? GodzillaFxS2CPacket.DEATH : GodzillaFxS2CPacket.FALL_IMPACT, new Vec3(belly.x, e.getY(), belly.z), lf,
         GodzillaRules.HEIGHT * 0.6F, 0, GodzillaRules.FX_FAR);
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.blast(belly.x, e.getY(), belly.z, GodzillaRules.HEIGHT * 0.6F);
         city.danger(belly.x, e.getY(), belly.z, GodzillaRules.HEIGHT * 1.2F, 400);
      }
   }

   // ------------------------------------------------------------------ steps and the walking body

   static void footfall(ServerLevel level, GodzillaSession s, GodzillaEntity e, boolean left) {
      Vec3 foot = e.foot(left);
      s.steps++;
      s.destruction.smashNow(level, foot.add(0.0, 2.5, 0.0), GodzillaRules.STEP_RADIUS, 1.6, BODY_HARDNESS, new Vec3(0.0, 0.35, 0.0), 0.05F, 320, "step");
      hurtSphere(level, s, e, foot.add(0.0, 2.0, 0.0), GodzillaRules.STEP_RADIUS * 1.15F, GodzillaRules.STEP_DAMAGE, 0.6, true,
         GodzillaDamage.crush(level, e), null);
      GodzillaNet.always(level, GodzillaFxS2CPacket.STEP, foot, GodzillaEntity.forward(e.getYRot()), s.rage() ? 1.25F : 1.0F, left ? 1 : 0,
         GodzillaRules.FX_FAR);
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.blast(foot.x, foot.y, foot.z, 12.0F);
      }
      if ((s.steps & 3) == 0) {
         StructuralDamage.impact(level, foot, GodzillaDestruction.CAUSE);
      }
   }

   /** The body pushes through what it walks into: a few parts per tick, a little ahead of where they go. */
   static void sweep(ServerLevel level, GodzillaSession s, GodzillaEntity e, boolean walking, boolean turning) {
      if (!walking && !turning) {
         return;
      }
      GodzillaPart[] parts = e.parts();
      Vec3 fw = GodzillaEntity.forward(e.getYRot());
      for (int i = 0; i < SWEEP_PER_TICK; i++) {
         int p = SWEEP[(s.sweepCursor++ & 0x7FFFFFFF) % SWEEP.length];
         boolean tail = p >= GodzillaRules.P_TAIL;
         if (!walking && !tail) {
            continue; // turning on the spot: only the tail swings through things
         }
         Vec3 c = parts[p].getBoundingBox().getCenter();
         Vec3 ahead = tail ? Vec3.ZERO : fw.scale(3.0);
         float w = GodzillaRules.PART_SIZE[p][0];
         float h = GodzillaRules.PART_SIZE[p][1];
         double r = w * GodzillaRules.HEIGHT * 0.5;
         s.destruction.queue(c.add(ahead), r, w / h, BODY_HARDNESS, fw.scale(0.4), 0.012F, tail ? 12 : 10, "body");
      }
   }

   /**
    * Hurts the living things within {@code radius} of {@code centre} (not the kaiju), with an optional falloff and a
    * knock away from the centre. {@code once}: ids already hit in this move are skipped (and added). Returns hits.
    */
   static int hurtSphere(ServerLevel level, GodzillaSession s, GodzillaEntity e, Vec3 centre, float radius, float damage, double knock, boolean falloff,
      DamageSource source, @Nullable Set<Integer> once) {
      AABB box = new AABB(centre, centre).inflate(radius);
      int hits = 0;
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof GodzillaEntity))) {
         if (once != null && once.contains(living.getId())) {
            continue;
         }
         Vec3 c = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
         double d = c.distanceTo(centre);
         if (d > radius + living.getBbWidth() * 0.5) {
            continue;
         }
         float f = falloff ? Math.max(0.3F, GodzillaRules.falloff(d, radius)) : 1.0F;
         if (once != null) {
            once.add(living.getId());
         }
         if (living.hurt(source, damage * f)) {
            hits++;
            s.noteHit(living);
         }
         if (knock > 0.0 && HeroRegistry.allowsImpulse(living)) {
            Vec3 away = c.subtract(centre);
            away = away.lengthSqr() < 1.0E-4 ? GodzillaEntity.forward(e.getYRot()) : away.normalize();
            living.push(away.x * knock * f, 0.25 + 0.35 * knock * f, away.z * knock * f);
            living.hurtMarked = true;
         }
      }
      return hits;
   }

   static boolean isPart(Object o) {
      return o instanceof GodzillaPart;
   }
}
