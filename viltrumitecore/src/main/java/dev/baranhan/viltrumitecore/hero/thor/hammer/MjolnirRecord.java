package dev.baranhan.viltrumitecore.hero.thor.hammer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * The saved truth about the one Mjölnir of a save (pure, no world access):
 * identity, phase, where it is, who may call it, the first-fall event and
 * the open flight facts that must survive a restart. Only {@link MjolnirWorld}
 * writes it (through {@link #go}); everything else reads.
 */
public final class MjolnirRecord {
   static final String ID = "Id";
   static final String PHASE = "Phase";
   static final String DIM = "Dim";
   static final String POS = "Pos";
   static final String VEL = "Vel";
   static final String ACTIVE = "Active";
   static final String LAST = "Last";
   static final String FALL_ARMED = "FallArmed";
   static final String FALL_DONE = "FallDone";
   static final String FALL_CANCELLED = "FallCancelled";
   static final String EMBEDDED = "Embedded";
   static final String LYING = "Lying";
   static final String CHARGED = "Charged";
   static final String ORIGIN = "Origin";
   static final String DEATH = "Death";
   static final String DEATH_DIM = "DeathDim";
   static final String SAFE = "Safe";
   static final String SAFE_DIM = "SafeDim";
   static final String RECALL_PENDING = "RecallPending";
   static final String YAW = "Yaw";
   static final String PITCH = "Pitch";

   @Nullable
   UUID logicalId;
   MjolnirPhase phase = MjolnirPhase.NOT_CREATED;
   @Nullable
   String dimension;
   double x;
   double y;
   double z;
   double vx;
   double vy;
   double vz;
   /** The player with the right to be Thor and call the hammer now (never reserves the slot offline). */
   @Nullable
   UUID activeOwner;
   /** History only (spec §3.1); reserves nothing. */
   @Nullable
   UUID lastOwner;
   /** Game time the first-fall timer started, -1 = not armed yet. */
   long fallArmedAt = -1L;
   boolean fallDone;
   boolean fallCancelled;
   /** Stuck in a block after a normal throw (rip on recall, №158). */
   boolean embedded;
   /**
    * Landed: stands planted upright, head in the ground (rework §6,
    * MjolnirPose; first fall №194, every rest after a fall, a throw into the
    * top of a block). Key "lying" kept for old saves: a saved lying hammer
    * loads planted on the same rest point.
    */
   boolean lying;
   /** The running throw is charged. */
   boolean charged;
   double originX;
   double originY;
   double originZ;
   /** Fixed death point of a Thor who died during the recall (№169). */
   @Nullable
   double[] death;
   @Nullable
   String deathDimension;
   /** Last resting point, the void-recovery target (PLACEHOLDER). */
   @Nullable
   double[] safe;
   @Nullable
   String safeDimension;
   /** Recall asked while the hammer could not leave yet (first fall still in the air). */
   boolean recallPending;
   /** Orientation of a resting hammer (embedded head-first along the throw; planted: only the yaw of the head). */
   float yaw;
   float pitch;

   // --- transient (never saved): facts of the running flight that must outlive a body re-spawn (A-m13) ---
   /** Entity ids hit by the running throw / return (never twice, №33), kept when the body is spawned again. */
   final Set<Integer> flightHits = new HashSet<>();
   /** Ticks of the running throw (THROW_MAX_TICKS), kept when the body is spawned again. */
   int flightTicks;
   /** The recall ripped it out of a block: the body plays the rip when it next ticks (№158), also after a far recall. */
   boolean ripPending;
   /** Void recoveries since the last real landing (A-m17: no endless recovery loop). */
   int voidAttempts;
   /** Parked by the last-resort void recovery: rests without support until it is recalled ({@link MjolnirOwnership#voidStep}). */
   boolean parked;

   public boolean exists() {
      return this.logicalId != null && this.phase != MjolnirPhase.NOT_CREATED;
   }

   @Nullable
   public UUID logicalId() {
      return this.logicalId;
   }

   public MjolnirPhase phase() {
      return this.phase;
   }

   @Nullable
   public String dimension() {
      return this.dimension;
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   @Nullable
   public UUID activeOwner() {
      return this.activeOwner;
   }

   @Nullable
   public UUID lastOwner() {
      return this.lastOwner;
   }

   public boolean fallDone() {
      return this.fallDone;
   }

   public boolean fallCancelled() {
      return this.fallCancelled;
   }

   public long fallArmedAt() {
      return this.fallArmedAt;
   }

   public boolean embedded() {
      return this.embedded;
   }

   public boolean lying() {
      return this.lying;
   }

   public boolean charged() {
      return this.charged;
   }

   /** Distance of a point from the throw origin. */
   public double fromOrigin(double px, double py, double pz) {
      double dx = px - this.originX;
      double dy = py - this.originY;
      double dz = pz - this.originZ;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /**
    * The one phase writer. False (nothing changes) for a transition the
    * state machine does not allow; the caller then keeps the old phase.
    */
   boolean go(MjolnirPhase next) {
      if (!this.phase.canGo(next)) {
         return false;
      }

      this.phase = next;
      if (next != MjolnirPhase.RESTING) {
         this.embedded = false;
         this.parked = false;
      }

      return true;
   }

   /** A new flight starts (throw, return, owner change): forget the hits and the flight clock. */
   void clearFlight() {
      this.flightHits.clear();
      this.flightTicks = 0;
      this.ripPending = false;
   }

   /** First creation (NOT_CREATED only): the logical id is chosen here, once per save. */
   boolean create(UUID id, MjolnirPhase first) {
      if (this.logicalId != null || this.phase != MjolnirPhase.NOT_CREATED || !this.phase.canGo(first)) {
         return false;
      }

      this.logicalId = id;
      this.phase = first;
      return true;
   }

   void place(@Nullable String dimension, double x, double y, double z) {
      this.dimension = dimension;
      this.x = x;
      this.y = y;
      this.z = z;
   }

   void velocity(double vx, double vy, double vz) {
      this.vx = vx;
      this.vy = vy;
      this.vz = vz;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      if (this.logicalId != null) {
         tag.putUUID(ID, this.logicalId);
      }

      tag.putString(PHASE, this.phase.key());
      if (this.dimension != null) {
         tag.putString(DIM, this.dimension);
      }

      putVec(tag, POS, this.x, this.y, this.z);
      putVec(tag, VEL, this.vx, this.vy, this.vz);
      if (this.activeOwner != null) {
         tag.putUUID(ACTIVE, this.activeOwner);
      }

      if (this.lastOwner != null) {
         tag.putUUID(LAST, this.lastOwner);
      }

      tag.putLong(FALL_ARMED, this.fallArmedAt);
      tag.putBoolean(FALL_DONE, this.fallDone);
      tag.putBoolean(FALL_CANCELLED, this.fallCancelled);
      tag.putBoolean(EMBEDDED, this.embedded);
      tag.putBoolean(LYING, this.lying);
      tag.putBoolean(CHARGED, this.charged);
      tag.putBoolean(RECALL_PENDING, this.recallPending);
      tag.putFloat(YAW, this.yaw);
      tag.putFloat(PITCH, this.pitch);
      putVec(tag, ORIGIN, this.originX, this.originY, this.originZ);
      if (this.death != null && this.deathDimension != null) {
         putVec(tag, DEATH, this.death[0], this.death[1], this.death[2]);
         tag.putString(DEATH_DIM, this.deathDimension);
      }

      if (this.safe != null && this.safeDimension != null) {
         putVec(tag, SAFE, this.safe[0], this.safe[1], this.safe[2]);
         tag.putString(SAFE_DIM, this.safeDimension);
      }

      return tag;
   }

   /**
    * Load the saved truth. A hammer whose phase key is unknown or lost its
    * id is recovered as RESTING at its saved point; a hammer saved "in the
    * hand" of nobody (crash) is detached later by {@link MjolnirWorld}.
    */
   public static MjolnirRecord load(CompoundTag tag) {
      MjolnirRecord record = new MjolnirRecord();
      record.logicalId = tag.hasUUID(ID) ? tag.getUUID(ID) : null;
      MjolnirPhase phase = MjolnirPhase.fromKey(tag.getString(PHASE));
      if (record.logicalId == null) {
         record.phase = MjolnirPhase.NOT_CREATED;
      } else {
         record.phase = phase == null || phase == MjolnirPhase.NOT_CREATED ? MjolnirPhase.RESTING : phase;
      }

      record.dimension = tag.contains(DIM, 8) ? tag.getString(DIM) : null;
      double[] pos = getVec(tag, POS);
      record.x = pos[0];
      record.y = pos[1];
      record.z = pos[2];
      double[] vel = getVec(tag, VEL);
      record.vx = vel[0];
      record.vy = vel[1];
      record.vz = vel[2];
      record.activeOwner = tag.hasUUID(ACTIVE) ? tag.getUUID(ACTIVE) : null;
      record.lastOwner = tag.hasUUID(LAST) ? tag.getUUID(LAST) : null;
      record.fallArmedAt = tag.contains(FALL_ARMED) ? tag.getLong(FALL_ARMED) : -1L;
      record.fallDone = tag.getBoolean(FALL_DONE);
      record.fallCancelled = tag.getBoolean(FALL_CANCELLED);
      record.embedded = tag.getBoolean(EMBEDDED);
      record.lying = tag.getBoolean(LYING);
      record.charged = tag.getBoolean(CHARGED);
      record.recallPending = tag.getBoolean(RECALL_PENDING);
      record.yaw = tag.getFloat(YAW);
      record.pitch = tag.getFloat(PITCH);
      double[] origin = getVec(tag, ORIGIN);
      record.originX = origin[0];
      record.originY = origin[1];
      record.originZ = origin[2];
      if (tag.contains(DEATH) && tag.contains(DEATH_DIM, 8)) {
         record.death = getVec(tag, DEATH);
         record.deathDimension = tag.getString(DEATH_DIM);
      }

      if (tag.contains(SAFE) && tag.contains(SAFE_DIM, 8)) {
         record.safe = getVec(tag, SAFE);
         record.safeDimension = tag.getString(SAFE_DIM);
      }

      if (record.exists() && record.dimension == null) {
         record.dimension = "minecraft:overworld";
      }

      return record;
   }

   private static void putVec(CompoundTag tag, String key, double x, double y, double z) {
      CompoundTag vec = new CompoundTag();
      vec.putDouble("x", x);
      vec.putDouble("y", y);
      vec.putDouble("z", z);
      tag.put(key, vec);
   }

   private static double[] getVec(CompoundTag tag, String key) {
      CompoundTag vec = tag.getCompound(key);
      return new double[]{vec.getDouble("x"), vec.getDouble("y"), vec.getDouble("z")};
   }
}
