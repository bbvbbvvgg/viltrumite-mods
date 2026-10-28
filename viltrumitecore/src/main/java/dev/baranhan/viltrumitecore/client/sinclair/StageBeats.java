package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;

/** Pure camera beats and quiet windows of the acts (tested; used by {@link StageLight}). */
public final class StageBeats {
   private StageBeats() {
   }

   /** Pure: the beat strength of an act on one tick: {shake, flash}; zeros when nothing happens. */
   public static float[] beat(ActShape shape, int tick) {
      return switch (shape) {
         case BEAM -> tick == ActScript.BEAM_START ? new float[]{0.4F, 0.25F} : ActScript.beamPulse(tick) ? new float[]{0.12F, 0.0F} : NONE;
         case NOVA -> tick == ActScript.NOVA.reveal() ? new float[]{1.0F, 0.45F} : tick == ActScript.NOVA_CATCH ? new float[]{0.2F, 0.0F} : NONE;
         case CUT -> tick == ActScript.CUT.reveal() ? new float[]{0.9F, 0.3F} : tick == ActScript.CUT_TELEGRAPH ? new float[]{0.15F, 0.0F} : NONE;
         case BARRAGE -> ActScript.barrageVolley(tick) ? new float[]{0.25F, 0.0F} : NONE;
         case AEGIS -> tick == ActScript.AEGIS.reveal() ? new float[]{0.9F, 0.35F} : tick == 1 ? new float[]{0.15F, 0.0F} : NONE;
         case FINALE -> tick == ActScript.FINALE.reveal() ? new float[]{1.0F, 0.45F} : tick == ActScript.FINALE_LAND ? new float[]{0.7F, 0.0F}
            : ActScript.finaleSword(tick) ? new float[]{0.3F, 0.0F} : NONE;
      };
   }

   static final float[] NONE = {0.0F, 0.0F};

   /** Pure: hush (drum roll before the reveal) or pause (silence after it). */
   public static boolean quiet(ActScript script, int tick) {
      return script.hushed(tick) || script.paused(tick);
   }

}
