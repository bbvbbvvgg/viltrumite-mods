package dev.baranhan.viltrumitecore.hero.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import net.minecraft.nbt.CompoundTag;

/**
 * The Dragon Sword Reid (spec §7.1, §7.3): sheathed → drawing (6 s event) →
 * drawn (auto-return after 60 s without combat) → sheathing (1 s) → sheathed.
 * Pure state machine; the server drives it from {@code ReinhardHero.tick}.
 * Saved by phase name; the transient phases load as SHEATHED.
 */
public final class Reid {
   public enum Phase {
      SHEATHED,
      DRAWING,
      DRAWN,
      SHEATHING;
   }

   public enum Event {
      NONE,
      DRAW_DONE,
      SHEATHE_DONE,
      AUTO_RETURN;
   }

   private Phase phase = Phase.SHEATHED;
   /** Ticks spent in DRAWING / SHEATHING. */
   private int ticks;
   /** DRAWN: ticks left before the auto-return. */
   private int idle;

   public Phase phase() {
      return this.phase;
   }

   /** Ticks since the draw / sheathe started (0 in the stable phases). */
   public int phaseTicks() {
      return this.ticks;
   }

   public int idleLeft() {
      return this.phase == Phase.DRAWN ? this.idle : 0;
   }

   /** Drawing or sheathing: no mode switch, no other action. */
   public boolean busy() {
      return this.phase == Phase.DRAWING || this.phase == Phase.SHEATHING;
   }

   public boolean drawn() {
      return this.phase == Phase.DRAWN;
   }

   /** The slot 1 press: SHEATHED → DRAWING, DRAWN → SHEATHING; ignored midway. */
   public boolean toggle() {
      if (this.phase == Phase.SHEATHED) {
         this.enter(Phase.DRAWING);
         return true;
      }

      if (this.phase == Phase.DRAWN) {
         this.enter(Phase.SHEATHING);
         return true;
      }

      return false;
   }

   public Event tick() {
      switch (this.phase) {
         case DRAWING -> {
            if (++this.ticks >= ReinhardRules.REID_DRAW_TICKS) {
               this.enter(Phase.DRAWN);
               this.idle = ReinhardRules.REID_IDLE_TICKS;
               return Event.DRAW_DONE;
            }
         }
         case DRAWN -> {
            if (--this.idle <= 0) {
               this.enter(Phase.SHEATHING);
               return Event.AUTO_RETURN;
            }
         }
         case SHEATHING -> {
            if (++this.ticks >= ReinhardRules.REID_SHEATHE_TICKS) {
               this.enter(Phase.SHEATHED);
               return Event.SHEATHE_DONE;
            }
         }
         default -> {
         }
      }

      return Event.NONE;
   }

   /** Combat (he hit, was hit, started an ability): the idle timer starts over while DRAWN. */
   public void combat() {
      if (this.phase == Phase.DRAWN) {
         this.idle = ReinhardRules.REID_IDLE_TICKS;
      }
   }

   /**
    * Leaving Holy Sword (spec §5): DRAWN starts the calm sheathe; DRAWING is cut
    * short to SHEATHED. Returns true when a draw was cut short (its scene must end).
    */
   public boolean sheatheNow() {
      if (this.phase == Phase.DRAWN) {
         this.enter(Phase.SHEATHING);
         return false;
      }

      if (this.phase == Phase.DRAWING) {
         this.enter(Phase.SHEATHED);
         return true;
      }

      return false;
   }

   /** Death, logout, hero change: sheathed at once. */
   public void reset() {
      this.enter(Phase.SHEATHED);
   }

   private void enter(Phase next) {
      this.phase = next;
      this.ticks = 0;
      this.idle = 0;
   }

   /**
    * Pure slot conditions (spec §7.1): Sheath Strike only sheathed; Rain of Cuts,
    * World Cut and World Erasure only drawn; everything else (the toggle itself,
    * the other modes) always. Lethal Chop is the kit chop and always works.
    */
   public static boolean allows(ReinhardAbility ability, Phase phase) {
      return switch (ability) {
         case SHEATH_STRIKE -> phase == Phase.SHEATHED;
         case CUT_RAIN, WORLD_CUT, WORLD_ERASURE -> phase == Phase.DRAWN;
         default -> true;
      };
   }

   /** Snapshot bits REID_DRAWN / REID_DRAWING / REID_SHEATHING (none = sheathed). */
   public static int withFlags(int flags, Phase phase) {
      int cleared = flags & ~(1 << ReinhardFlags.REID_DRAWN | 1 << ReinhardFlags.REID_DRAWING | 1 << ReinhardFlags.REID_SHEATHING);
      return switch (phase) {
         case DRAWN -> cleared | 1 << ReinhardFlags.REID_DRAWN;
         case DRAWING -> cleared | 1 << ReinhardFlags.REID_DRAWING;
         case SHEATHING -> cleared | 1 << ReinhardFlags.REID_SHEATHING;
         default -> cleared;
      };
   }

   public static Phase phaseOf(int flags) {
      if (ReinhardFlags.has(flags, ReinhardFlags.REID_DRAWING)) {
         return Phase.DRAWING;
      }

      if (ReinhardFlags.has(flags, ReinhardFlags.REID_SHEATHING)) {
         return Phase.SHEATHING;
      }

      return ReinhardFlags.has(flags, ReinhardFlags.REID_DRAWN) ? Phase.DRAWN : Phase.SHEATHED;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Phase", this.phase.name());
      tag.putInt("Idle", this.idle);
      return tag;
   }

   public void load(CompoundTag tag) {
      Phase loaded = Phase.SHEATHED;
      try {
         loaded = Phase.valueOf(tag.getString("Phase"));
      } catch (IllegalArgumentException ignored) {
      }

      if (loaded == Phase.DRAWN) {
         this.enter(Phase.DRAWN);
         int saved = tag.getInt("Idle");
         this.idle = saved > 0 ? Math.min(saved, ReinhardRules.REID_IDLE_TICKS) : ReinhardRules.REID_IDLE_TICKS;
      } else {
         this.enter(Phase.SHEATHED);
      }
   }
}
