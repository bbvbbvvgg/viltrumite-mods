package dev.baranhan.viltrumitecore.hero.dio;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * One local stopped-time field of one DIO. Living victims are ordinary
 * {@code ControlManager} FREEZE records under {@link #effectId}; non-living
 * things (items, projectiles, falling blocks, TNT) keep their saved motion
 * here and get it back at the resume. Hits DIO lands on his frozen victims
 * collect in the ledger and are paid in one blow when time moves again.
 */
public final class TimeStopField {
   public final UUID effectId = UUID.randomUUID();
   public final UUID caster;
   public final int casterEntityId;
   public final ServerLevel level;
   public final Vec3 center;
   public final double radius;
   /** Stopped ticks from the first capture to the resume. */
   public final int duration;
   /** True for the Road Roller field: its owner ends it, not the clock. */
   public final boolean scripted;
   public int age;
   public boolean resumeAnnounced;
   public boolean ended;
   final Set<UUID> living = new LinkedHashSet<>();
   final Map<UUID, Thing> things = new HashMap<>();
   final Map<UUID, Ledger> ledger = new HashMap<>();
   /** Bumped whenever the frozen id list changes, so clients get a resend. */
   int version;
   int sentVersion = -1;

   TimeStopField(UUID caster, int casterEntityId, ServerLevel level, Vec3 center, double radius, int duration, boolean scripted) {
      this.caster = caster;
      this.casterEntityId = casterEntityId;
      this.level = level;
      this.center = center;
      this.radius = radius;
      this.duration = duration;
      this.scripted = scripted;
   }

   public int left() {
      return Math.max(0, this.duration - this.age);
   }

   public boolean resuming() {
      return !this.scripted && this.left() <= DioRules.TIME_STOP_RESUME;
   }

   /** Capture front radius: the wave leaves The World and reaches the edge in 8 ticks. */
   public double front() {
      return this.radius * Math.min(1.0, (this.age + 1) / 8.0);
   }

   public boolean isFrozen(UUID entity) {
      return this.living.contains(entity) || this.things.containsKey(entity);
   }

   /** Saved motion of a non-living thing. */
   static final class Thing {
      final Vec3 motion;
      final boolean noGravity;
      final double powerX, powerY, powerZ;

      Thing(Vec3 motion, boolean noGravity, double powerX, double powerY, double powerZ) {
         this.motion = motion;
         this.noGravity = noGravity;
         this.powerX = powerX;
         this.powerY = powerY;
         this.powerZ = powerZ;
      }
   }

   /** Damage and push DIO owes one frozen victim. */
   static final class Ledger {
      float damage;
      int hits;
      Vec3 push = Vec3.ZERO;
   }
}
