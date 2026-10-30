package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import net.minecraft.nbt.CompoundTag;

/**
 * Hulkbuster Mark 48 as a layer over the current suit (rework spec §4, §11): the suit under it is untouched
 * and resumes after. The suit-up scene runs DROPPING (Veronica's capsule comes, opens; in the air variant the
 * player flies up to it) then ASSEMBLING (the player backs into the open frame, the plates close one by one:
 * legs, torso, arms, shoulders, helmet). The body grows when the torso closes; after that the scene cannot be
 * stopped. ACTIVE has its own durability; a break or an exit starts the 10 min cooldown. A suit-up stopped
 * before the torso closes costs no cooldown. Persisted under {@code Hulkbuster} (phase names unchanged).
 */
public final class HulkbusterLayer {
   public static final String KEY = "Hulkbuster";
   public static final float DURABILITY = 800.0F;
   public static final double ARMOR = 30.0;
   /** Durability loss per point of an absorbed hit (heavier than any mark). */
   public static final float PLATING = 0.36F;
   public static final int COOLDOWN = 12000;
   /** Air variant: the capsule falls (0-20), opens (20-40) while the player flies up to it. */
   public static final int DROP_TICKS_AIR = 40;
   /** Ground variant: the capsule lands beside the player and opens. */
   public static final int DROP_TICKS_GROUND = 30;
   public static final int ASSEMBLE_TICKS = 50;
   /** Exit (fix spec item 2: 2.5 s): vent, the chest opens, Tony steps out, the empty shell stands (HulkbusterExit). */
   public static final int EXIT_TICKS = 50;
   /** Plates lock at these assemble ticks: legs, torso, arms, shoulders, helmet (spec §4.3). */
   public static final int[] PART_LOCK = {8, 18, 28, 36, 46};
   /** Assemble tick at which the torso closes: the hitbox grows, the scene can no longer be stopped. */
   public static final int TORSO_TICK = PART_LOCK[1];
   public static final int PARTS = PART_LOCK.length;
   public static final int FULL_PARTS = (1 << PARTS) - 1;
   /** Height factor of the hitbox (bodyScale); the width has its own factor (HulkbusterRules.WIDTH_SCALE). */
   public static final float SCALE = HulkbusterRules.HEIGHT_SCALE;

   public enum Phase {
      NONE,
      DROPPING,
      ASSEMBLING,
      /** Legacy saves only (stage 5): loads as NONE. */
      PARTIAL,
      ACTIVE,
      EXITING
   }

   public enum Event {
      NONE,
      /** The torso just closed: the caller checks the space for the big body. */
      TORSO_CLOSED,
      /** The scene is done: the caller activates. */
      READY,
      EXITED
   }

   private Phase phase = Phase.NONE;
   private int ticks;
   private float durability = DURABILITY;
   private int cooldown;
   private int parts;
   private boolean air;
   /** Loaded ACTIVE: the first server tick checks the space. */
   private boolean needsFitCheck;
   /** Veronica already sent the spare arm during this suit-up (spec §6.8, saved). */
   private boolean spareUsed;

   public Phase phase() {
      return this.phase;
   }

   public int ticks() {
      return this.ticks;
   }

   public float durability() {
      return this.durability;
   }

   public int cooldown() {
      return this.cooldown;
   }

   public int parts() {
      return this.parts;
   }

   public boolean air() {
      return this.air;
   }

   public boolean active() {
      return this.phase == Phase.ACTIVE;
   }

   /** Any Hulkbuster part on or coming: the suit under it is frozen in place. */
   public boolean present() {
      return this.phase != Phase.NONE;
   }

   /** Suit-up scene or exit running: no kit. */
   public boolean busy() {
      return this.phase == Phase.DROPPING || this.phase == Phase.ASSEMBLING || this.phase == Phase.EXITING;
   }

   /** The torso is closed: the big body, and the scene can no longer be stopped. */
   public boolean torsoClosed() {
      return this.phase == Phase.ASSEMBLING && this.ticks >= TORSO_TICK || this.phase == Phase.ACTIVE || this.phase == Phase.EXITING;
   }

   /** Big body (bodyScale): from the torso closing until Tony starts to step out of the exit (HulkbusterExit). */
   public boolean big() {
      if (this.phase == Phase.EXITING) {
         return HulkbusterExit.big(this.ticks);
      }

      return this.torsoClosed();
   }

   /**
    * {@link #big()} as both sides read it from the public snapshot: the phase ordinal
    * (IronManFlags HULKBUSTER_PHASE) and the scene word (HulkbusterWire.SCENE).
    */
   public static boolean bigOnWire(int phaseOrdinal, int sceneWord) {
      if (phaseOrdinal == Phase.ACTIVE.ordinal()) {
         return true;
      }

      if (phaseOrdinal == Phase.EXITING.ordinal()) {
         return HulkbusterExit.big(HulkbusterWire.sceneTick(sceneWord));
      }

      return phaseOrdinal == Phase.ASSEMBLING.ordinal() && HulkbusterWire.sceneAssemble(sceneWord) >= TORSO_TICK;
   }

   public boolean needsFitCheck() {
      return this.needsFitCheck;
   }

   public int dropTicks() {
      return this.air ? DROP_TICKS_AIR : DROP_TICKS_GROUND;
   }

   public int phaseLength() {
      return switch (this.phase) {
         case DROPPING -> this.dropTicks();
         case ASSEMBLING -> ASSEMBLE_TICKS;
         case EXITING -> EXIT_TICKS;
         default -> 0;
      };
   }

   /** Scene tick from the delivery (DROPPING then ASSEMBLING), the exit tick while EXITING, -1 otherwise. */
   public int sceneTick() {
      return switch (this.phase) {
         case DROPPING -> this.ticks;
         case ASSEMBLING -> this.dropTicks() + this.ticks;
         case EXITING -> this.ticks;
         default -> -1;
      };
   }

   /** Start the suit-up. Refused on cooldown or when anything is already on. */
   public boolean start(boolean air) {
      if (this.cooldown > 0 || this.phase != Phase.NONE && this.phase != Phase.PARTIAL) {
         return false;
      }

      this.durability = DURABILITY;
      this.spareUsed = false;
      this.parts = 0;
      this.air = air;
      this.set(Phase.DROPPING);
      return true;
   }

   public boolean start() {
      return this.start(false);
   }

   public Event tick() {
      if (this.cooldown > 0) {
         this.cooldown--;
      }

      switch (this.phase) {
         case DROPPING -> {
            if (++this.ticks >= this.dropTicks()) {
               this.set(Phase.ASSEMBLING);
            }
         }
         case ASSEMBLING -> {
            this.ticks++;
            this.parts = lockedParts(this.ticks);
            if (this.ticks == TORSO_TICK) {
               return Event.TORSO_CLOSED;
            }

            if (this.ticks >= ASSEMBLE_TICKS) {
               return Event.READY;
            }
         }
         case EXITING -> {
            if (++this.ticks >= EXIT_TICKS) {
               this.clearToNone();
               this.cooldown = COOLDOWN;
               return Event.EXITED;
            }
         }
         default -> {
         }
      }

      return Event.NONE;
   }

   public static int lockedParts(int assembleTicks) {
      int mask = 0;
      for (int i = 0; i < PARTS; i++) {
         if (assembleTicks >= PART_LOCK[i]) {
            mask |= 1 << i;
         }
      }

      return mask;
   }

   /** Space confirmed: the Hulkbuster is on. */
   public void activate() {
      this.parts = FULL_PARTS;
      this.needsFitCheck = false;
      this.set(Phase.ACTIVE);
   }

   /**
    * Od Lagna's Wish (docs/design/2026-10-30-od-lagna-wish-design.md): the Mark 48 is simply there, whole
    * and fresh, through the same ACTIVE + first-tick fit check a loaded save takes (no room = refuse).
    */
   public void grantByWish() {
      this.durability = DURABILITY;
      this.parts = FULL_PARTS;
      this.set(Phase.ACTIVE);
      this.needsFitCheck = true;
   }

   /** No room for the big body: it goes back, no cooldown. */
   public void refuse() {
      this.clearToNone();
   }

   /** Control or the suit key before the torso closes: the frame flies back, no cooldown (spec §4). */
   public boolean cancel() {
      if ((this.phase == Phase.DROPPING || this.phase == Phase.ASSEMBLING) && !this.torsoClosed()) {
         this.clearToNone();
         return true;
      }

      return false;
   }

   /** "Костюм": ACTIVE → climb out (cooldown after). */
   public boolean exit() {
      if (this.phase == Phase.ACTIVE) {
         this.set(Phase.EXITING);
         return true;
      }

      if (this.phase == Phase.PARTIAL) {
         this.clearToNone();
         return true;
      }

      return false;
   }

   /** Durability 0 or Tony died: it falls apart, cooldown starts once the torso was closed. */
   public void breakNow() {
      if (this.phase == Phase.NONE) {
         return;
      }

      boolean wasOn = this.torsoClosed();
      this.clearToNone();
      if (wasOn) {
         this.cooldown = COOLDOWN;
      }
   }

   /** Whole hit on the layer while ACTIVE; true when absorbed. The emptying hit does not spill. */
   public boolean absorb(float amount) {
      if (this.phase != Phase.ACTIVE || this.durability <= 0.0F) {
         return false;
      }

      this.durability = Math.max(0.0F, this.durability - Math.max(0.0F, amount) * PLATING);
      return true;
   }

   /** Spare arm: durability back, capped at the maximum. */
   public void restore(float amount) {
      if (this.phase == Phase.ACTIVE) {
         this.durability = Math.min(DURABILITY, this.durability + Math.max(0.0F, amount));
      }
   }

   /** Self damage of the ultimate (spec §7): never below 1 point (the ram itself does not break the suit). */
   public void absorbSelf(float amount) {
      if (this.phase == Phase.ACTIVE) {
         this.durability = Math.max(1.0F, this.durability - Math.max(0.0F, amount));
      }
   }

   public boolean spareUsed() {
      return this.spareUsed;
   }

   public void markSpareUsed() {
      this.spareUsed = true;
   }

   /** Durability as 0..1 of the maximum. */
   public float fraction() {
      return this.durability / DURABILITY;
   }

   public boolean broken() {
      return this.phase == Phase.ACTIVE && this.durability <= 0.0F;
   }

   /** Dev command: durability set directly (clamped). */
   public void setDurability(float value) {
      this.durability = Math.max(0.0F, Math.min(DURABILITY, value));
   }

   /** Dev command: cooldown cleared. */
   public void clearCooldown() {
      this.cooldown = 0;
   }

   /** Hero change: everything off, no cooldown. */
   public void reset() {
      this.clearToNone();
      this.cooldown = 0;
      this.durability = DURABILITY;
      this.spareUsed = false;
   }

   private void clearToNone() {
      this.set(Phase.NONE);
      this.parts = 0;
      this.needsFitCheck = false;
   }

   private void set(Phase next) {
      this.phase = next;
      this.ticks = 0;
   }

   public void save(CompoundTag tag) {
      CompoundTag layer = new CompoundTag();
      // Relog mid-scene (spec §4): before the torso closed = cancelled without cooldown; after = ACTIVE.
      Phase saved = switch (this.phase) {
         case ACTIVE -> Phase.ACTIVE;
         case DROPPING, ASSEMBLING -> this.torsoClosed() ? Phase.ACTIVE : Phase.NONE;
         default -> Phase.NONE;
      };
      layer.putString("Phase", saved.name());
      layer.putFloat("Durability", this.durability);
      // Climbing out when saved: the exit finishes, the cooldown counts.
      layer.putInt("Cooldown", this.phase == Phase.EXITING ? COOLDOWN : this.cooldown);
      layer.putInt("Parts", 0);
      layer.putBoolean("SpareUsed", this.spareUsed);
      tag.put(KEY, layer);
   }

   public void load(CompoundTag tag) {
      this.reset();
      if (!tag.contains(KEY, 10)) {
         return;
      }

      CompoundTag layer = tag.getCompound(KEY);
      Phase loaded;
      try {
         loaded = Phase.valueOf(layer.getString("Phase"));
      } catch (IllegalArgumentException ignored) {
         loaded = Phase.NONE;
      }

      float durability = layer.getFloat("Durability");
      this.durability = Float.isFinite(durability) ? Math.max(0.0F, Math.min(DURABILITY, durability)) : DURABILITY;
      this.cooldown = Math.max(0, Math.min(COOLDOWN, layer.getInt("Cooldown")));
      this.spareUsed = layer.getBoolean("SpareUsed");
      if (loaded == Phase.ACTIVE && this.durability > 0.0F) {
         this.parts = FULL_PARTS;
         this.set(Phase.ACTIVE);
         this.needsFitCheck = true;
      }
      // PARTIAL (stage 5) and anything else: nothing on, no cooldown added.
   }

   public void copyCooldownFrom(HulkbusterLayer other) {
      this.cooldown = other.cooldown;
   }
}
