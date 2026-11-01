package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronBolt;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMob;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionMove;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionOverride;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Machines Ultron's uplinks (and his engineers) take over: iron golems and Iron Legion suits. A golem
 * fights for him until the uplink that holds it falls, it is beaten down (freed at
 * {@link UltronRules#GOLEM_FREE_HEALTH}) or the event ends. A Legion suit gets a {@link LegionOverride}:
 * every hit goes into a shell of {@link UltronRules#SUIT_SHELL} instead of the owner's roster, and the
 * roster durability is put back as it was when the suit is handed back (liberation, node loss, event end,
 * owner logout or any Legion cleanup). Nothing of the capture is saved: a restart frees everything.
 */
public final class UltronCapture {
   /** Client copy of the captured entity ids (state packet), for {@link #controlled} on the client. */
   private static volatile int[] clientCaptured = new int[0];

   static final class Captive {
      final UUID id;
      final boolean suit;
      int node;
      float shell = UltronRules.SUIT_SHELL;
      float snapshot = -1.0F;
      int since;
      int fireCooldown = 30;
      int strafe = 1;

      Captive(UUID id, boolean suit, int node, int since) {
         this.id = id;
         this.suit = suit;
         this.node = node;
         this.since = since;
      }
   }

   final Map<UUID, Captive> captives = new LinkedHashMap<>();
   int lastCapture = -1_000_000;
   int captures;
   int releases;

   /** Ultron controls this entity right now (golem or Legion suit). Both sides. */
   public static boolean controlled(@Nullable Entity e) {
      if (e == null) {
         return false;
      }
      if (e instanceof LegionSuitEntity suit) {
         return suit.hijacked();
      }
      if (!(e instanceof IronGolem)) {
         return false;
      }
      if (e.level().isClientSide) {
         int id = e.getId();
         for (int c : clientCaptured) {
            if (c == id) {
               return true;
            }
         }
         return false;
      }
      UltronSession s = UltronUprising.current();
      return s != null && s.capture.captives.containsKey(e.getUUID());
   }

   public static void setClientCaptured(int[] ids) {
      clientCaptured = ids.clone();
   }

   int golems() {
      int n = 0;
      for (Captive c : this.captives.values()) {
         if (!c.suit) {
            n++;
         }
      }
      return n;
   }

   int suits() {
      return this.captives.size() - this.golems();
   }

   /** Entity ids of the captives that stand in this level (state packet). */
   int[] ids(ServerLevel level) {
      List<Integer> out = new ArrayList<>();
      for (Captive c : this.captives.values()) {
         Entity e = level.getEntity(c.id);
         if (e != null) {
            out.add(e.getId());
         }
      }
      int[] a = new int[out.size()];
      for (int i = 0; i < a.length; i++) {
         a[i] = out.get(i);
      }
      return a;
   }

   /** Free golems / suits within an active controller's reach (strategy input), loaded entities only. */
   int targetsNear(ServerLevel level, UltronSession s) {
      int n = 0;
      for (UltronNetwork.Node node : s.network.all()) {
         if (!node.active() || !node.kind.controls()) {
            continue;
         }
         AABB box = new AABB(node.x, node.y, node.z, node.x, node.y, node.z).inflate(UltronRules.CAPTURE_RADIUS);
         n += level.getEntitiesOfClass(LivingEntity.class, box, this::capturable).size();
      }
      return n;
   }

   boolean capturable(LivingEntity e) {
      if (!e.isAlive() || this.captives.containsKey(e.getUUID())) {
         return false;
      }
      if (e instanceof IronGolem) {
         return true;
      }
      return e instanceof LegionSuitEntity suit && !suit.hijacked() && suit.phase() == LegionPhase.ACTIVE && suit.owner() != null;
   }

   boolean roomFor(LivingEntity e) {
      return e instanceof LegionSuitEntity ? this.suits() < UltronRules.MAX_CAPTURED_SUITS : this.golems() < UltronRules.MAX_CAPTURED_GOLEMS;
   }

   /** One uplink pulse: takes the nearest free machine in reach (once per capture gap). */
   void pulse(ServerLevel level, UltronSession s, boolean eager) {
      int gap = eager ? UltronRules.CAPTURE_GAP_TICKS / 2 : UltronRules.CAPTURE_GAP_TICKS;
      if (s.clock - this.lastCapture < gap) {
         return;
      }
      for (UltronNetwork.Node node : s.network.all()) {
         if (!node.active() || !node.kind.controls()) {
            continue;
         }
         Vec3 at = new Vec3(node.x, node.y + 3.0, node.z);
         AABB box = new AABB(at, at).inflate(UltronRules.CAPTURE_RADIUS);
         LivingEntity best = null;
         double bestD = Double.MAX_VALUE;
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::capturable)) {
            double d = e.distanceToSqr(at);
            if (d <= UltronRules.CAPTURE_RADIUS * UltronRules.CAPTURE_RADIUS && d < bestD && this.roomFor(e)) {
               bestD = d;
               best = e;
            }
         }
         if (best != null && this.take(level, s, best, node.id, at)) {
            return;
         }
      }
   }

   /** Takes a machine over (an uplink pulse or an engineer's hack from {@code from}). */
   boolean take(ServerLevel level, UltronSession s, LivingEntity e, int node, Vec3 from) {
      if (!this.capturable(e) || !this.roomFor(e)) {
         return false;
      }
      Captive c = new Captive(e.getUUID(), e instanceof LegionSuitEntity, node, s.clock);
      if (e instanceof LegionSuitEntity suit) {
         ServerPlayer owner = suit.owner();
         IronManState state = owner == null ? null : IronManState.of(owner);
         if (state != null) {
            c.snapshot = state.roster.durability(suit.mark());
         }
         this.captives.put(c.id, c);
         suit.setOverride(new SuitControl(c));
         suit.setTarget(null);
      } else if (e instanceof IronGolem golem) {
         this.captives.put(c.id, c);
         golem.stopBeingAngry();
         golem.setTarget(null);
      }
      this.lastCapture = s.clock;
      this.captures++;
      Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.7, 0.0);
      UltronNet.fxAlways(level, new UltronFxS2CPacket(UltronFxS2CPacket.CAPTURE, from, to, 1.0F, e.getId()), to, 96.0);
      UltronSounds.play(level, to, UltronSounds.CAPTURE, 2.0F, 1.0F);
      UltronVoice.say(level, s, c.suit ? "capture_suit" : "capture_golem", true);
      s.dirty = true;
      return true;
   }

   /** Each second: lost bodies are forgotten, captives out of every controller's reach are freed, golems get targets. */
   void second(ServerLevel level, UltronSession s) {
      List<UUID> free = new ArrayList<>();
      for (Captive c : this.captives.values()) {
         Entity e = level.getEntity(c.id);
         if (!(e instanceof LivingEntity living) || !living.isAlive()) {
            if (e == null && !c.suit) {
               free.add(c.id); // unloaded golem: nothing of the capture is in its save
            } else if (e == null || !living(e)) {
               free.add(c.id);
            }
            continue;
         }
         UltronNetwork.Node holder = this.holder(s, living.position());
         if (holder == null) {
            free.add(c.id);
            continue;
         }
         c.node = holder.id;
         if (living instanceof IronGolem golem) {
            LivingEntity t = golem.getTarget();
            if (t == null || !t.isAlive() || UltronMob.side(t) || golem.distanceToSqr(t) > 24.0 * 24.0) {
               golem.setTarget(nearestFoe(level, golem, 20.0));
            }
            if (s.clock % 60 == 0) {
               Vec3 node = new Vec3(holder.x, holder.y + 3.0, holder.z);
               UltronNet.fx(level, new UltronFxS2CPacket(UltronFxS2CPacket.CAPTURE, node, golem.position().add(0.0, 2.2, 0.0), 0.5F, golem.getId()),
                  golem.position(), 64.0);
            }
         }
      }
      for (UUID id : free) {
         this.release(level, s, id, false);
      }
   }

   private static boolean living(Entity e) {
      return e instanceof LivingEntity l && l.isAlive();
   }

   /** The active controller that holds a point (twice the capture reach), or null. */
   @Nullable
   UltronNetwork.Node holder(UltronSession s, Vec3 at) {
      double reach = UltronRules.CAPTURE_RADIUS * 2.0;
      UltronNetwork.Node best = null;
      double bestD = reach * reach;
      for (UltronNetwork.Node n : s.network.all()) {
         if (n.active() && n.kind.controls()) {
            double d = n.distanceSq(at.x, at.y, at.z);
            if (d <= bestD) {
               bestD = d;
               best = n;
            }
         }
      }
      return best;
   }

   /** Frees one captive (liberated by the players when {@code liberated}). */
   void release(ServerLevel level, UltronSession s, UUID id, boolean liberated) {
      Captive c = this.captives.remove(id);
      if (c == null) {
         return;
      }
      this.releases++;
      Entity e = level.getEntity(id);
      if (e instanceof LegionSuitEntity suit) {
         if (suit.override() instanceof SuitControl) {
            suit.setOverride(null); // SuitControl.released restores the roster
         }
      } else if (e instanceof IronGolem golem) {
         golem.setTarget(null);
         golem.stopBeingAngry();
      }
      if (e != null) {
         Vec3 at = e.position().add(0.0, e.getBbHeight() * 0.6, 0.0);
         UltronNet.fxAlways(level, new UltronFxS2CPacket(UltronFxS2CPacket.RELEASE, at, Vec3.ZERO, 1.0F, e.getId()), at, 96.0);
         UltronSounds.play(level, at, UltronSounds.RELEASE, 1.8F, 1.0F);
      }
      if (liberated) {
         UltronVoice.say(level, s, "liberated", false);
      }
      s.dirty = true;
   }

   /** Event end / stop / server stop: every captive goes back. */
   void releaseAll(ServerLevel level, UltronSession s) {
      for (UUID id : new ArrayList<>(this.captives.keySet())) {
         this.release(level, s, id, false);
      }
   }

   /** A captive golem took a lethal hit: freed at a share of its health instead of dying. */
   boolean saveGolem(ServerLevel level, UltronSession s, IronGolem golem) {
      if (!this.captives.containsKey(golem.getUUID())) {
         return false;
      }
      golem.setHealth(golem.getMaxHealth() * UltronRules.GOLEM_FREE_HEALTH);
      this.release(level, s, golem.getUUID(), true);
      return true;
   }

   @Nullable
   static LivingEntity nearestFoe(ServerLevel level, LivingEntity from, double range) {
      LivingEntity best = null;
      double bestD = range * range;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range), UltronMob::validTarget)) {
         if (e == from) {
            continue;
         }
         double d = e.distanceToSqr(from);
         if (d < bestD && ChitauriTerrain.clear(level, from.getEyePosition(), e.getEyePosition())) {
            bestD = d;
            best = e;
         }
      }
      return best;
   }

   /** The override a taken Legion suit runs: guards its uplink, fights Ultron's foes with red bolts, takes hits into the shell. */
   final class SuitControl implements LegionOverride {
      private final Captive captive;
      private Vec3 velocity = Vec3.ZERO;

      SuitControl(Captive captive) {
         this.captive = captive;
      }

      @Override
      public void tick(LegionSuitEntity suit, ServerPlayer owner) {
         if (!(suit.level() instanceof ServerLevel level)) {
            return;
         }
         UltronSession s = UltronUprising.current();
         if (s == null || !s.in(level)) {
            suit.setOverride(null);
            return;
         }
         UltronNetwork.Node node = s.network.get(this.captive.node);
         Vec3 home = node == null ? suit.position() : new Vec3(node.x, node.y + 7.0, node.z);
         LivingEntity target = suit.getTarget();
         if (target == null || !target.isAlive() || !UltronMob.validTarget(target) || suit.distanceToSqr(target) > 30.0 * 30.0 || suit.tickCount % 40 == 0) {
            target = nearestFoe(level, suit, 24.0);
            suit.setTarget(target);
         }
         Vec3 want;
         if (target != null) {
            Vec3 to = target.position().subtract(suit.position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            Vec3 dir = d < 1.0E-3 ? Vec3.ZERO : new Vec3(to.x / d, 0.0, to.z / d);
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(this.captive.strafe * 0.25);
            double keep = d > 14.0 ? 0.45 : d < 9.0 ? -0.35 : 0.0;
            double up = Mth.clamp((target.getY() + 5.0 - suit.getY()) * 0.08, -0.3, 0.3);
            want = dir.scale(keep).add(side).add(0.0, up, 0.0);
            if (suit.tickCount % 50 == 0) {
               this.captive.strafe = -this.captive.strafe;
            }
            float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            suit.setYRot(Mth.approachDegrees(suit.getYRot(), yaw, 12.0F));
            suit.yBodyRot = suit.getYRot();
            suit.yHeadRot = suit.getYRot();
            suit.setAim(target.getId());
            if (--this.captive.fireCooldown <= 0 && ChitauriTerrain.clear(level, suit.getEyePosition(), target.getEyePosition())) {
               this.captive.fireCooldown = 26 + suit.getRandom().nextInt(14);
               boolean right = suit.getRandom().nextBoolean();
               suit.playMove(right ? LegionMove.FIRE_RIGHT : LegionMove.FIRE_LEFT);
               Vec3 from = suit.hand(right);
               Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0).subtract(from).normalize();
               UltronBolt.shoot(level, suit, from, aim, 1.6, BalanceTable.ULTRON_SENTRY_BOLT * UltronUprising.unitDamageScale(level), UltronBolt.STYLE_REPULSOR);
               UltronSounds.play(level, from, UltronSounds.BOLT_IMPACT, 1.0F, 1.5F);
            }
         } else {
            Vec3 to = home.subtract(suit.position());
            double ang = (suit.tickCount + this.captive.id.hashCode() % 100) * 0.03;
            Vec3 orbit = new Vec3(Math.cos(ang) * 5.0, 0.0, Math.sin(ang) * 5.0);
            Vec3 aim = to.add(orbit);
            want = aim.lengthSqr() > 1.0 ? aim.normalize().scale(Math.min(0.5, aim.length() * 0.08)) : Vec3.ZERO;
            suit.setAim(-1);
            float yaw = (float)(Mth.atan2(want.z, want.x) * Mth.RAD_TO_DEG) - 90.0F;
            if (want.lengthSqr() > 1.0E-3) {
               suit.setYRot(Mth.approachDegrees(suit.getYRot(), yaw, 8.0F));
               suit.yBodyRot = suit.getYRot();
               suit.yHeadRot = suit.getYRot();
            }
         }
         // never into unloaded chunks
         Vec3 next = suit.position().add(want.scale(3.0));
         if (!ChitauriTerrain.loaded(level, next.x, next.z)) {
            want = Vec3.ZERO;
         }
         this.velocity = this.velocity.add(want.subtract(this.velocity).scale(0.15));
         suit.setThrottle((float)Mth.clamp(0.35 + this.velocity.length() * 1.2, 0.35, 1.0));
         suit.fly(this.velocity);
      }

      @Override
      public boolean hurt(LegionSuitEntity suit, DamageSource source, float amount) {
         Entity attacker = source.getEntity();
         if (UltronMob.side(attacker) || UltronMob.side(source.getDirectEntity()) || amount <= 0.0F) {
            return false;
         }
         if (!(suit.level() instanceof ServerLevel level)) {
            return false;
         }
         UltronSession s = UltronUprising.current();
         this.captive.shell -= source.is(DamageTypeTags.BYPASSES_ARMOR) ? amount : amount * 0.85F;
         suit.hurtTime = suit.hurtDuration = 6;
         suit.playMove(LegionMove.HIT);
         Vec3 at = suit.position().add(0.0, suit.getBbHeight() * 0.6, 0.0);
         UltronNet.fx(level, new UltronFxS2CPacket(UltronFxS2CPacket.BOLT_IMPACT, at, Vec3.ZERO, 0.6F, suit.getId()), at, 48.0);
         if (attacker instanceof LivingEntity living && living != suit) {
            suit.setLastHurtByMob(living);
         }
         if (this.captive.shell <= 0.0F && s != null) {
            UltronCapture.this.release(level, s, this.captive.id, true);
         }
         return true;
      }

      @Override
      public void released(LegionSuitEntity suit) {
         UltronCapture.this.captives.remove(this.captive.id);
         suit.setTarget(null);
         suit.setAim(-1);
         ServerPlayer owner = suit.owner();
         IronManState state = owner == null ? null : IronManState.of(owner);
         if (state != null && this.captive.snapshot >= 0.0F) {
            MarkLocation where = state.roster.location(suit.mark());
            if (where == MarkLocation.LEGION || where == MarkLocation.STORED) {
               float now = state.roster.durability(suit.mark());
               if (now < this.captive.snapshot) {
                  state.roster.setDurability(suit.mark(), this.captive.snapshot);
               }
               float max = state.roster.maxDurability(suit.mark());
               suit.syncFraction(dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules.fraction(state.roster.durability(suit.mark()), max));
            }
         }
      }

      @Override
      public boolean friend(@Nullable Entity other) {
         return UltronMob.side(other);
      }
   }
}
