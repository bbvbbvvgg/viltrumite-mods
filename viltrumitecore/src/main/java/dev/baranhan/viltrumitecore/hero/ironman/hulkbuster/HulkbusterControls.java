package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import javax.annotation.Nullable;

/**
 * The single Hulkbuster input resolver (rework spec §1.3.1, §6): context → move. Pure. The server press,
 * the client mouse claim and the panel grey-out all ask this class, in this order of contexts. A press
 * that resolves to a refusal answers with a HUD line and the refusal sound (§1.3.5); {@link Decision#silent}
 * presses mean nothing in that context (LMB in a dive) and are not claimed.
 */
public final class HulkbusterControls {
   public enum Input {
      LMB,
      /** LMB held past HulkbusterRules.HOLD_THRESHOLD. */
      LMB_HOLD,
      RMB,
      GUARD,
      SLOT_1,
      SLOT_2,
      SLOT_3,
      SLOT_4,
      ULTIMATE
   }

   public static final String COOLDOWN = "hud.viltrumitecore.ironman.hulkbuster.cooldown";
   public static final String NO_ENERGY = "hud.viltrumitecore.ironman.hulkbuster.no_energy";
   public static final String HANDS_BUSY = "hud.viltrumitecore.ironman.hulkbuster.hands_busy";
   public static final String NOT_DAMAGED = "hud.viltrumitecore.ironman.hulkbuster.not_damaged";
   public static final String SPARE_USED = "hud.viltrumitecore.ironman.hulkbuster.spare_used";
   public static final String GROUND_ONLY = "hud.viltrumitecore.ironman.hulkbuster.ground_only";
   public static final String BUSY = "hud.viltrumitecore.ironman.hulkbuster.busy";

   /**
    * What the resolver sees.
    *
    * @param cooldown ticks left for the input (0 = ready)
    */
   public record Context(boolean holding, int punches, boolean diving, boolean airborne, boolean dashingForward, int cooldown, boolean energy,
      float durability, boolean spareUsed, boolean slamIntent, boolean staggered) {
   }

   /**
    * @param move   the move to request, null when refused or silent
    * @param step   combo step to start at (escalating face punches)
    * @param refusal HUD key of a refusal (null = accepted or silent)
    */
   public record Decision(@Nullable HulkMove move, int step, @Nullable String refusal) {
      static final Decision SILENT = new Decision(null, 0, null);

      static Decision of(HulkMove move) {
         return new Decision(move, 0, null);
      }

      static Decision refuse(String key) {
         return new Decision(null, 0, key);
      }

      public boolean accepted() {
         return this.move != null;
      }

      public boolean silent() {
         return this.move == null && this.refusal == null;
      }
   }

   private HulkbusterControls() {
   }

   public static Decision resolve(Input input, Context c) {
      if (c.staggered()) {
         return Decision.refuse(BUSY);
      }

      return switch (input) {
         case LMB, LMB_HOLD -> {
            if (c.holding()) {
               yield c.punches() < HulkbusterRules.GRAB_PUNCHES ? new Decision(HulkMove.GRAB_PUNCH, c.punches(), null) : Decision.of(HulkMove.GRAB_FINISH);
            }

            if (c.diving()) {
               yield Decision.SILENT;
            }

            if (input == Input.LMB_HOLD) {
               yield Decision.of(HulkMove.CHARGED);
            }

            yield c.dashingForward() ? Decision.of(HulkMove.DASH_STRIKE) : Decision.of(HulkMove.COMBO);
         }
         case RMB -> {
            if (c.holding()) {
               yield Decision.of(c.slamIntent() ? HulkMove.GRAB_SLAM : HulkMove.GRAB_THROW);
            }

            if (c.diving()) {
               yield Decision.SILENT;
            }

            yield c.cooldown() > 0 ? Decision.refuse(COOLDOWN) : Decision.of(HulkMove.GRAB_REACH);
         }
         case GUARD -> c.holding() ? Decision.refuse(HANDS_BUSY) : c.airborne() ? Decision.refuse(GROUND_ONLY) : Decision.of(HulkMove.BLOCK);
         case SLOT_1 -> ability(c, HulkMove.HEAVY_REPULSORS, true, false);
         case SLOT_2 -> ability(c, HulkMove.SEISMIC_RIFT, false, true);
         case SLOT_3 -> ability(c, HulkMove.VERONICA_CAGE, false, false);
         case SLOT_4 -> {
            if (c.spareUsed()) {
               yield Decision.refuse(SPARE_USED);
            }

            if (c.durability() >= HulkbusterRules.SPARE_ARM_BELOW) {
               yield Decision.refuse(NOT_DAMAGED);
            }

            yield ability(c, HulkMove.SPARE_ARM, false, false);
         }
         case ULTIMATE -> ability(c, HulkMove.JET_RAM, false, true);
      };
   }

   private static Decision ability(Context c, HulkMove move, boolean needsEnergy, boolean ground) {
      if (c.holding()) {
         return Decision.refuse(HANDS_BUSY);
      }

      if (c.cooldown() > 0) {
         return Decision.refuse(COOLDOWN);
      }

      if (needsEnergy && !c.energy()) {
         return Decision.refuse(NO_ENERGY);
      }

      if (ground && c.airborne()) {
         return Decision.refuse(GROUND_ONLY);
      }

      return Decision.of(move);
   }

   /** Panel slot / key → input. */
   @Nullable
   public static Input slot(int index) {
      return switch (index) {
         case 0 -> Input.SLOT_1;
         case 1 -> Input.SLOT_2;
         case 2 -> Input.SLOT_3;
         case 3 -> Input.SLOT_4;
         case 4 -> Input.ULTIMATE;
         default -> null;
      };
   }
}
