package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import net.minecraft.nbt.CompoundTag;

/**
 * Hulkbuster Mark 48 as a layer over the current suit (spec §4.4, §14): the
 * suit under it is untouched and resumes after. Parts drop from Veronica,
 * assemble over Tony, then the layer is ACTIVE with its own durability; a
 * break or an exit starts the 10 min cooldown. Control during the assembly
 * keeps the locked parts as PARTIAL (no scale, no kit, no cooldown).
 * Persisted under {@code Hulkbuster}.
 */
public final class HulkbusterLayer {
   public static final String KEY = "Hulkbuster";
   public static final float DURABILITY = 800.0F;
   public static final double ARMOR = 30.0;
   /** Durability loss per point of an absorbed hit (heavier than any mark). */
   public static final float PLATING = 0.36F;
   public static final int COOLDOWN = 12000;
   public static final int DROP_TICKS = 20;
   public static final int ASSEMBLE_TICKS = 60;
   public static final int EXIT_TICKS = 30;
   /** Parts lock at these assemble ticks: legs, torso, arms, helmet (spec §14.1, plan Task 5). */
   public static final int[] PART_LOCK = {15, 30, 45, 60};
   public static final int PARTS = PART_LOCK.length;
   public static final int FULL_PARTS = (1 << PARTS) - 1;
   public static final float SCALE = 1.7F;

   public enum Phase {
      NONE,
      DROPPING,
      ASSEMBLING,
      PARTIAL,
      ACTIVE,
      EXITING
   }

   public enum Event {
      NONE,
      /** Assembly finished: the caller checks the space and calls activate() or refuse(). */
      READY,
      EXITED
   }

   private Phase phase = Phase.NONE;
   private int ticks;
   private float durability = DURABILITY;
   private int cooldown;
   private int parts;
   private int startParts;
   /** Loaded mid-assembly or ACTIVE: the first server tick checks the space (plan Task 1). */
   private boolean needsFitCheck;

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

   public boolean active() {
      return this.phase == Phase.ACTIVE;
   }

   /** Any Hulkbuster part on or coming: the suit under it is frozen in place. */
   public boolean present() {
      return this.phase != Phase.NONE;
   }

   /** Assembly or exit running: rooted, no kit (plan Task 5). */
   public boolean busy() {
      return this.phase == Phase.DROPPING || this.phase == Phase.ASSEMBLING || this.phase == Phase.EXITING;
   }

   /** Big body (bodyScale): only while fully on and while climbing out. */
   public boolean big() {
      return this.phase == Phase.ACTIVE || this.phase == Phase.EXITING;
   }

   public boolean needsFitCheck() {
      return this.needsFitCheck;
   }

   public int phaseLength() {
      return switch (this.phase) {
         case DROPPING -> DROP_TICKS;
         case ASSEMBLING -> ASSEMBLE_TICKS;
         case EXITING -> EXIT_TICKS;
         default -> 0;
      };
   }

   /** Drop the parts (from NONE, or PARTIAL: only the missing ones). Refused on cooldown. */
   public boolean start() {
      if (this.cooldown > 0 || this.phase != Phase.NONE && this.phase != Phase.PARTIAL) {
         return false;
      }

      if (this.phase == Phase.NONE) {
         this.durability = DURABILITY;
         this.startParts = 0;
      } else {
         this.startParts = this.parts;
      }

      this.parts = this.startParts;
      this.set(Phase.DROPPING);
      return true;
   }

   public Event tick() {
      if (this.cooldown > 0) {
         this.cooldown--;
      }

      switch (this.phase) {
         case DROPPING -> {
            if (++this.ticks >= DROP_TICKS) {
               this.set(Phase.ASSEMBLING);
            }
         }
         case ASSEMBLING -> {
            this.ticks++;
            this.parts = this.startParts | lockedParts(this.ticks);
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

   /** No room for the big body: parts fly back, no cooldown (plan Global Constraints). */
   public void refuse() {
      this.clearToNone();
   }

   /** Control interrupts the assembly: locked parts stay (PARTIAL), the rest fly back. */
   public void interrupt() {
      if (this.phase == Phase.DROPPING) {
         this.parts = this.startParts;
         if (this.parts == 0) {
            this.clearToNone();
         } else {
            this.set(Phase.PARTIAL);
         }
      } else if (this.phase == Phase.ASSEMBLING) {
         if (this.parts == 0) {
            this.clearToNone();
         } else {
            this.set(Phase.PARTIAL);
         }
      }
   }

   /** "Костюм": ACTIVE → climb out (cooldown after); PARTIAL → parts fly back without a cooldown. */
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

   /** Durability 0 or Tony died: it falls apart, cooldown starts (spec §14.1). */
   public void breakNow() {
      if (this.phase == Phase.NONE) {
         return;
      }

      boolean wasOn = this.phase == Phase.ACTIVE || this.phase == Phase.EXITING;
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

   public boolean broken() {
      return this.phase == Phase.ACTIVE && this.durability <= 0.0F;
   }

   /** Hero change: everything off, no cooldown. */
   public void reset() {
      this.clearToNone();
      this.cooldown = 0;
      this.durability = DURABILITY;
   }

   private void clearToNone() {
      this.set(Phase.NONE);
      this.parts = 0;
      this.startParts = 0;
      this.needsFitCheck = false;
   }

   private void set(Phase next) {
      this.phase = next;
      this.ticks = 0;
   }

   public void save(CompoundTag tag) {
      CompoundTag layer = new CompoundTag();
      Phase saved = switch (this.phase) {
         case DROPPING, ASSEMBLING, ACTIVE -> Phase.ACTIVE;
         case PARTIAL -> Phase.PARTIAL;
         default -> Phase.NONE;
      };
      layer.putString("Phase", saved.name());
      layer.putFloat("Durability", this.durability);
      // Climbing out when saved: the exit finishes, the cooldown counts.
      layer.putInt("Cooldown", this.phase == Phase.EXITING ? COOLDOWN : this.cooldown);
      layer.putInt("Parts", this.phase == Phase.PARTIAL ? this.parts : 0);
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
      if (loaded == Phase.ACTIVE && this.durability > 0.0F) {
         this.parts = FULL_PARTS;
         this.set(Phase.ACTIVE);
         this.needsFitCheck = true;
      } else if (loaded == Phase.PARTIAL) {
         this.parts = layer.getInt("Parts") & FULL_PARTS;
         if (this.parts != 0) {
            this.set(Phase.PARTIAL);
         }
      }
   }

   public void copyCooldownFrom(HulkbusterLayer other) {
      this.cooldown = other.cooldown;
   }
}
