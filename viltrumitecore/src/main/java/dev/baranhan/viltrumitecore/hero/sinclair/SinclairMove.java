package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import javax.annotation.Nullable;

/**
 * Sinclair's timed body moves. The ordinal is the snapshot {@code actionId} (hero-owned wire value,
 * append only); the length is the authoritative clip length in ticks, shared by every view.
 */
public enum SinclairMove {
   BOLT_CAST(SinclairRules.BOLT_WINDUP + SinclairRules.BOLT_RECOVERY, "bolt_cast"),
   BOLT_SNAP(8, "bolt_snap"),
   SUMMON(SinclairRules.ASSIST_CAST, "summon"),
   SWAP(SinclairRules.SWAP_TICKS, "swap"),
   HEX_CAST(SinclairRules.HEX_CAST, "hex_cast"),
   SNAP(SinclairRules.MIS_CAST, "snap"),
   CAPTURE(SinclairRules.ULT_CAPTURE, "capture"),
   ACT_BEAM(ActScript.BEAM.length(), "act_beam"),
   ACT_NOVA(ActScript.NOVA.length(), "act_nova"),
   ACT_CUT(ActScript.CUT.length(), "act_cut"),
   ACT_BARRAGE(ActScript.BARRAGE.length(), "act_barrage"),
   ACT_AEGIS(ActScript.AEGIS.length(), "act_aegis"),
   ACT_FINALE(ActScript.FINALE.length(), "act_finale"),
   REFUSE(SinclairRules.REFUSE_TICKS, "refuse"),
   /** The copy cinematic after a read card (design 2026-11-02 §2): both participants held, the card shown. */
   COPY(CopyCinematic.LENGTH, "copy");

   private final int length;
   private final String clip;

   SinclairMove(int length, String clip) {
      this.length = length;
      this.clip = clip;
   }

   public int length() {
      return this.length;
   }

   public String clip() {
      return this.clip;
   }

   public boolean isAct() {
      return this.ordinal() >= ACT_BEAM.ordinal() && this.ordinal() <= ACT_FINALE.ordinal();
   }

   /** Moves that may not be interrupted by another cast (the act, the capture and the copy cinematic own the body). */
   public boolean locksCasting() {
      return this.isAct() || this == CAPTURE || this == COPY;
   }

   public static SinclairMove act(ActShape shape) {
      return switch (shape) {
         case BEAM -> ACT_BEAM;
         case NOVA -> ACT_NOVA;
         case CUT -> ACT_CUT;
         case BARRAGE -> ACT_BARRAGE;
         case AEGIS -> ACT_AEGIS;
         case FINALE -> ACT_FINALE;
      };
   }

   @Nullable
   public static SinclairMove byId(int id) {
      SinclairMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }
}
