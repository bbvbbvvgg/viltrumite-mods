package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.ironman.legion.ThreatScore.Relation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One owner's Iron Legion on the server (design §4–§5): the suits by slot,
 * the current order and priority, the threat memory and the coordinator
 * that hands every suit its target, role and stance. Transient: it lives in
 * {@link IronManLegion} by owner UUID and never touches NBT.
 */
public final class LegionSquad {
   /** Bit flags of {@link #events}: JARVIS lines the owner's client says once. */
   public static final int EVENT_ON = 1;
   public static final int EVENT_THREE = 2;
   public static final int EVENT_THREAT = 4;
   public static final int EVENT_CRITICAL = 8;
   public static final int EVENT_RETURN = 16;
   private static final int BOARD_CAP = 16;
   private static final int THREAT_LINE_GAP = 600;
   private static final int TONY_TARGET_MEMORY = 100;

   final List<LegionSuitEntity> suits = new ArrayList<>(LegionRules.MAX_SUITS);
   LegionOrder order = LegionOrder.GUARD;
   LegionPriority priority = LegionPriority.NEAREST;
   @Nullable
   Vec3 holdPoint;
   int orderedTargetId = -1;
   /** Call cooldown left (starts when the last suit has left). */
   int cooldown;
   /** The arrival of this call was answered with "three suits" already. */
   boolean announcedReady;
   long lastThreatLine = Long.MIN_VALUE / 2;
   boolean hadTargets;
   /** JARVIS events not yet sent to the client. */
   int events;
   /** Something changed that the HUD must see now. */
   boolean dirty = true;
   int statusTimer;
   /** Owner yaw at the call (the arrival curves keep it). */
   float callYaw;
   final Map<Integer, Long> hitOwner = new HashMap<>();
   final Map<Integer, Long> hitSuit = new HashMap<>();
   /** Last plan by suit slot (target, role, stance), for the HUD and the brains. */
   final Map<Integer, LegionTactics.Assignment> plan = new HashMap<>();

   public boolean active() {
      return !this.suits.isEmpty();
   }

   public List<LegionSuitEntity> suits() {
      return this.suits;
   }

   public LegionOrder order() {
      return this.order;
   }

   public LegionPriority priority() {
      return this.priority;
   }

   @Nullable
   public Vec3 holdPoint() {
      return this.holdPoint;
   }

   public int cooldown() {
      return this.cooldown;
   }

   public void event(int flag) {
      this.events |= flag;
      this.dirty = true;
   }

   /** Fighting suits in slot order (formation indices). */
   public List<LegionSuitEntity> fighting() {
      List<LegionSuitEntity> out = new ArrayList<>(this.suits.size());
      for (LegionSuitEntity suit : this.suits) {
         if (suit.isAlive() && suit.phase().fighting()) {
            out.add(suit);
         }
      }

      return out;
   }

   /** Formation index of a suit among the fighting ones (-1 when it does not fight). */
   public int formationIndex(LegionSuitEntity suit) {
      int i = 0;
      for (LegionSuitEntity other : this.suits) {
         if (other == suit) {
            return suit.phase().fighting() ? i : -1;
         }

         if (other.isAlive() && other.phase().fighting()) {
            i++;
         }
      }

      return -1;
   }

   @Nullable
   public LegionTactics.Assignment plan(int slot) {
      return this.plan.get(slot);
   }

   void noteOwnerHit(@Nullable Entity attacker, long now) {
      if (attacker != null && !(attacker instanceof LegionSuitEntity)) {
         this.hitOwner.put(attacker.getId(), now);
      }
   }

   void noteSuitHit(@Nullable Entity attacker, long now) {
      if (attacker != null && !(attacker instanceof LegionSuitEntity)) {
         this.hitSuit.put(attacker.getId(), now);
      }
   }

   /** Coordinator round: threat board → assignments → every suit's brain. */
   void plan(ServerPlayer owner) {
      ServerLevel level = owner.serverLevel();
      long now = level.getGameTime();
      this.hitOwner.values().removeIf(t -> now - t > LegionRules.THREAT_MEMORY);
      this.hitSuit.values().removeIf(t -> now - t > LegionRules.THREAT_MEMORY);
      List<LegionSuitEntity> fighting = new ArrayList<>();
      for (LegionSuitEntity suit : this.suits) {
         if (suit.isAlive() && suit.phase() == LegionPhase.ACTIVE) {
            fighting.add(suit);
         }
      }

      if (this.orderedTargetId >= 0) {
         Entity ordered = level.getEntity(this.orderedTargetId);
         if (!(ordered instanceof LivingEntity living) || !living.isAlive() || ordered.distanceTo(owner) > LegionRules.ORDER_REACH + 16.0) {
            this.orderedTargetId = -1;
         }
      }

      for (LegionSuitEntity suit : this.suits) {
         suit.setOrderedTarget(this.orderedTargetId);
      }

      if (fighting.isEmpty()) {
         this.plan.clear();
         return;
      }

      List<LegionTactics.Target> targets = this.order == LegionOrder.RETURN ? List.of() : this.board(owner, fighting, now);
      List<LegionTactics.Suit> view = new ArrayList<>(fighting.size());
      for (LegionSuitEntity suit : fighting) {
         LegionProfile profile = LegionProfile.of(suit.mark());
         view.add(new LegionTactics.Suit(suit.slot(), profile, suit.position(), suit.fraction(), true));
      }

      this.plan.clear();
      for (LegionTactics.Assignment a : LegionTactics.assign(view, targets)) {
         this.plan.put(a.slot(), a);
      }

      for (LegionSuitEntity suit : fighting) {
         LegionTactics.Assignment a = this.plan.get(suit.slot());
         if (suit.brain != null && a != null) {
            Entity target = a.hasTarget() ? level.getEntity(a.targetId()) : null;
            suit.brain.assign(a, target instanceof LivingEntity living ? living : null);
         }
      }

      boolean any = !targets.isEmpty();
      if (any && !this.hadTargets && now - this.lastThreatLine > THREAT_LINE_GAP) {
         this.lastThreatLine = now;
         this.event(EVENT_THREAT);
      }

      if (any != this.hadTargets) {
         this.dirty = true;
      }

      this.hadTargets = any;
   }

   /** Eligible, in-reach threats around the anchor, scored for the current priority. */
   private List<LegionTactics.Target> board(ServerPlayer owner, List<LegionSuitEntity> fighting, long now) {
      ServerLevel level = owner.serverLevel();
      Vec3 anchor = this.order == LegionOrder.HOLD && this.holdPoint != null ? this.holdPoint : owner.position();
      double radius = this.order.engageRadius();
      AABB box = new AABB(anchor, anchor).inflate(LegionRules.SCAN_RADIUS);
      if (this.order != LegionOrder.HOLD) {
         box = box.minmax(new AABB(owner.position(), owner.position()).inflate(LegionRules.SCAN_RADIUS));
      }

      List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
         e -> e.isAlive() && e != owner && !(e instanceof LegionSuitEntity) && !e.isSpectator());
      // ATTACK may pick a target up to ORDER_REACH away: it joins the board even outside the scan box.
      Entity ordered = this.orderedTargetId >= 0 ? level.getEntity(this.orderedTargetId) : null;
      candidates = withOrdered(candidates, ordered instanceof LivingEntity living && living.isAlive() && living != owner
         && !(living instanceof LegionSuitEntity) && !living.isSpectator() ? living : null);
      candidates.sort((a, b) -> Double.compare(a.distanceToSqr(anchor), b.distanceToSqr(anchor)));
      int tonyTarget = owner.getLastHurtMob() != null && owner.tickCount - owner.getLastHurtMobTimestamp() < TONY_TARGET_MEMORY
         ? owner.getLastHurtMob().getId() : -1;
      List<LegionTactics.Target> out = new ArrayList<>();
      LegionSuitEntity probe = fighting.get(0);
      int n = 0;
      for (LivingEntity e : candidates) {
         if (n >= BOARD_CAP && e.getId() != this.orderedTargetId) {
            continue;
         }

         Relation relation = LegionAllies.relation(owner, probe, e);
         Long ownerHit = this.hitOwner.get(e.getId());
         Long suitHit = this.hitSuit.get(e.getId());
         boolean onTony = e instanceof Mob mob && mob.getTarget() == owner;
         int sinceOwner = ownerHit == null ? Integer.MAX_VALUE / 2 : (int)Math.min(Integer.MAX_VALUE / 2, now - ownerHit);
         if (onTony && relation == Relation.HOSTILE && sinceOwner > LegionRules.THREAT_MEMORY) {
            // A monster that is after Tony counts like a fading attacker.
            sinceOwner = LegionRules.THREAT_MEMORY - 20;
         }

         int sinceSuit = suitHit == null ? Integer.MAX_VALUE / 2 : (int)Math.min(Integer.MAX_VALUE / 2, now - suitHit);
         double toOwner = e.distanceTo(owner);
         double toAnchor = Math.sqrt(e.distanceToSqr(anchor));
         ThreatScore.Threat threat = new ThreatScore.Threat(relation, sinceOwner, sinceSuit, danger(e), toAnchor, visible(level, fighting, owner, e),
            e.getHealth(), e.getMaxHealth(), e.getArmorValue(), flying(e), e.getId() == tonyTarget, e.getId() == this.orderedTargetId);
         if (!ThreatScore.eligible(threat) || !ThreatScore.inReach(threat, radius)) {
            continue;
         }

         n++;
         double score = ThreatScore.score(threat, this.priority, radius);
         boolean onOwner = toOwner <= LegionRules.INTERRUPT_RADIUS && (sinceOwner < LegionRules.INTERRUPT_WINDOW || onTony && toOwner < 4.0);
         out.add(new LegionTactics.Target(e.getId(), score, threat.effectiveHealth(), e.position(), toOwner, onOwner, threat.flying()));
      }

      return out;
   }

   /** The scan result plus the ordered target when the scan box missed it (no duplicate). */
   static <T> List<T> withOrdered(List<T> candidates, @Nullable T ordered) {
      List<T> out = new ArrayList<>(candidates);
      if (ordered != null && !out.contains(ordered)) {
         out.add(ordered);
      }

      return out;
   }

   static float danger(LivingEntity e) {
      if (e instanceof Creeper) {
         return 8.0F;
      }

      var attack = e.getAttribute(Attributes.ATTACK_DAMAGE);
      float melee = attack == null ? 0.0F : (float)attack.getValue();
      if (e instanceof RangedAttackMob || e instanceof Blaze) {
         melee = Math.max(melee, 4.0F);
      }

      if (e instanceof Player) {
         melee = Math.max(melee, 6.0F);
      }

      return Math.max(1.0F, melee);
   }

   static boolean flying(LivingEntity e) {
      if (e instanceof FlyingMob || e instanceof Blaze || e instanceof Vex || e instanceof WitherBoss || e instanceof EnderDragon) {
         return true;
      }

      if (e instanceof FlyingAnimal animal && animal.isFlying()) {
         return true;
      }

      return e instanceof Player player && (player.getAbilities().flying || player.isFallFlying());
   }

   private static boolean visible(ServerLevel level, List<LegionSuitEntity> fighting, ServerPlayer owner, LivingEntity e) {
      Vec3 to = e.getEyePosition();
      if (clear(level, owner.getEyePosition(), to, owner)) {
         return true;
      }

      for (LegionSuitEntity suit : fighting) {
         if (clear(level, suit.getEyePosition(), to, suit)) {
            return true;
         }
      }

      return false;
   }

   static boolean clear(ServerLevel level, Vec3 from, Vec3 to, Entity viewer) {
      return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
   }

   /** Drop dead or foreign bodies from the list (they release themselves). */
   void prune() {
      Iterator<LegionSuitEntity> it = this.suits.iterator();
      while (it.hasNext()) {
         LegionSuitEntity suit = it.next();
         if (suit.isRemoved()) {
            it.remove();
            this.dirty = true;
         }
      }
   }
}
