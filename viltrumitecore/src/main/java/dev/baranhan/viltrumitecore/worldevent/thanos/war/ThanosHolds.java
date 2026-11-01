package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ThanosWarS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Every hold the war has on a hero right now (server): Thanos's time stop, compulsion and reality lift,
 * Ebony Maw's telekinetic grab, Cull Obsidian's grab and chain. Lengths come from the shared
 * {@link ThanosControlLedger} (diminishing returns, no chained holds); a hero whose definition refuses
 * the control kind (or impulses, for the moving holds) is never held. A held player's own movement
 * input is turned off on his client for the length of the hold ({@link ThanosWarS2CPacket#HOLD}) and the
 * server drives him: frozen in place, carried to a point, or dragged along. A hold ends early when its
 * holder falls, is staggered or lets go.
 */
public final class ThanosHolds {
   public enum Kind {
      /** Time stop: still in place. */
      FREEZE(ControlKind.FREEZE),
      /** Carried towards a moving point (telekinesis, a fist, a hook). */
      CARRY(ControlKind.VILTRUMITE_GRAB),
      /** Pulled along a line (compulsion, space). */
      DRAG(ControlKind.PULL);

      final ControlKind control;

      Kind(ControlKind control) {
         this.control = control;
      }
   }

   private static final class Hold {
      final LivingEntity target;
      final Kind kind;
      final Entity holder;
      final Supplier<Vec3> point;
      final double speed;
      final Vec3 anchor;
      int left;

      Hold(LivingEntity target, Kind kind, Entity holder, Supplier<Vec3> point, double speed, int left) {
         this.target = target;
         this.kind = kind;
         this.holder = holder;
         this.point = point;
         this.speed = speed;
         this.anchor = target.position();
         this.left = left;
      }
   }

   private final ThanosWar war;
   private final List<Hold> holds = new ArrayList<>();

   ThanosHolds(ThanosWar war) {
      this.war = war;
   }

   /** May a hold of this kind land on the target at all (its protections, a world pause)? */
   public static boolean allows(LivingEntity target, Kind kind) {
      if (!target.isAlive() || WorldPause.isProtected(target) || !HeroRegistry.allowsExternalControl(target, kind.control)) {
         return false;
      }

      return kind == Kind.FREEZE || HeroRegistry.allowsImpulse(target);
   }

   /** May a new hold land on this hero now (protections and the ledger)? */
   public boolean canHold(LivingEntity target, Kind kind) {
      return allows(target, kind) && this.war.ledger.controllable(target.getUUID(), this.war.now()) && this.find(target) == null;
   }

   /**
    * Holds {@code target} for up to {@code ticks} (the ledger decides how long): the granted ticks, 0
    * when refused. {@code point} is where a carry or drag takes him (ignored by a freeze), {@code speed}
    * how fast (blocks per tick).
    */
   public int hold(LivingEntity target, Kind kind, Entity holder, int ticks, Supplier<Vec3> point, double speed) {
      if (!allows(target, kind) || this.find(target) != null) {
         return 0;
      }

      int granted = this.war.ledger.grant(target.getUUID(), ticks, this.war.now());
      if (granted <= 0) {
         return 0;
      }

      this.holds.add(new Hold(target, kind, holder, point, speed, granted));
      if (target instanceof ServerPlayer p) {
         CoreMessages.sendToPlayer(ThanosWarS2CPacket.hold(granted, kind.ordinal()), p);
      }

      return granted;
   }

   @Nullable
   private Hold find(LivingEntity target) {
      for (Hold h : this.holds) {
         if (h.target == target) {
            return h;
         }
      }

      return null;
   }

   public boolean held(LivingEntity target) {
      return this.find(target) != null;
   }

   /** Is anyone held by {@code holder} right now (Thanos follows up on a pinned target)? */
   public boolean holding(Entity holder) {
      for (Hold h : this.holds) {
         if (h.holder == holder) {
            return true;
         }
      }

      return false;
   }

   /** Is this target held by anyone of the war? */
   public boolean pinned(@Nullable LivingEntity target) {
      return target != null && this.find(target) != null;
   }

   /** Lets go of everyone {@code holder} holds (it was hit hard, fell or threw them). */
   public void releaseFrom(Entity holder) {
      for (Iterator<Hold> it = this.holds.iterator(); it.hasNext(); ) {
         Hold h = it.next();
         if (h.holder == holder) {
            it.remove();
            this.ended(h);
         }
      }
   }

   public void release(LivingEntity target) {
      Hold h = this.find(target);
      if (h != null) {
         this.holds.remove(h);
         this.ended(h);
      }
   }

   private void ended(Hold h) {
      this.war.ledger.release(h.target.getUUID(), this.war.now());
      if (h.target instanceof ServerPlayer p) {
         CoreMessages.sendToPlayer(ThanosWarS2CPacket.hold(0, h.kind.ordinal()), p);
      }
   }

   public void tick(ServerLevel level) {
      for (Iterator<Hold> it = this.holds.iterator(); it.hasNext(); ) {
         Hold h = it.next();
         boolean holderGone = !h.holder.isAlive() || h.holder.isRemoved() || h.holder.level() != level;
         if (--h.left <= 0 || !h.target.isAlive() || h.target.level() != level || holderGone || !allows(h.target, h.kind)) {
            it.remove();
            this.ended(h);
            continue;
         }

         LivingEntity t = h.target;
         t.fallDistance = 0.0F;
         switch (h.kind) {
            case FREEZE -> {
               t.setDeltaMovement(Vec3.ZERO);
               if (t.position().distanceToSqr(h.anchor) > 0.6) {
                  t.teleportTo(h.anchor.x, h.anchor.y, h.anchor.z);
               }
            }
            case CARRY, DRAG -> {
               Vec3 to = h.point.get();
               Vec3 d = to.subtract(t.position());
               double len = d.length();
               Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(h.speed, len * 0.5) / len);
               t.setDeltaMovement(v);
               if (h.kind == Kind.CARRY) {
                  t.setOnGround(false);
               }
            }
         }

         t.hasImpulse = true;
         t.hurtMarked = true;
      }
   }

   public int size() {
      return this.holds.size();
   }

   public List<UUID> heldIds() {
      List<UUID> out = new ArrayList<>();
      for (Hold h : this.holds) {
         out.add(h.target.getUUID());
      }

      return out;
   }

   public void clear() {
      for (Hold h : this.holds) {
         if (h.target instanceof ServerPlayer p) {
            CoreMessages.sendToPlayer(ThanosWarS2CPacket.hold(0, h.kind.ordinal()), p);
         }
      }

      this.holds.clear();
   }
}
