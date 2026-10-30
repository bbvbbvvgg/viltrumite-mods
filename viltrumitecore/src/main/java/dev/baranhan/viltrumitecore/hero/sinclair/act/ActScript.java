package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.act.ActShape;

/**
 * Pure beat table of each stage act (spec docs/design/2026-10-28-sinclair-design.md §6.1, reworked in
 * docs/design/2026-11-02-sinclair-cinematic-polish.md §3). Ticks count from the second B press. The server
 * deals damage on these ticks; the client stages poses, props, camera and silence on the same numbers.
 *
 * <p>Every number has four parts: <b>tension</b> {@code [0, main)} (the set-up and the drum roll),
 * <b>main</b> {@code [main, hushStart)} (the trick itself), <b>climax</b> {@code [hushStart, pauseEnd)}
 * (the hush, the reveal on {@code reveal} and the frozen silence after it) and <b>finish</b>
 * {@code [pauseEnd, length)} (the aftermath and the bow from {@code bowStart}).
 *
 * @param main       first tick of the main part
 * @param reveal     the reveal (climax contact) tick
 * @param hushStart  first tick of the drum-roll hush before the reveal
 * @param pauseStart start of the silence after the reveal
 * @param pauseEnd   end of the silence
 * @param bowStart   start of the bow
 * @param length     whole act
 */
public record ActScript(ActShape shape, int main, int reveal, int hushStart, int pauseStart, int pauseEnd, int bowStart, int length) {
   /** The four dramatic parts of a number. */
   public enum Part {
      TENSION,
      MAIN,
      CLIMAX,
      FINISH
   }

   // BEAM "Footlight Ray" (Homelander's heat vision, played with the card as a mirror):
   // tension 0-28 (the footlights pour into the card), main 28-70 (the ray, a pulse every 5 t),
   // hush 70-78 (the ray thins to a thread), climax 78 (the overdrive flare, wide until 88),
   // pause 88-96, bow 96-120.
   public static final ActScript BEAM = new ActScript(ActShape.BEAM, 28, 78, 70, 88, 96, 96, 120);
   public static final int BEAM_START = 28;
   public static final int BEAM_END = 70;
   public static final int BEAM_PERIOD = 5;
   public static final float BEAM_DAMAGE = 0.75F;
   public static final double BEAM_WIDTH = 0.7;
   public static final int BEAM_BURST = 78;
   public static final int BEAM_BURST_END = 88;
   public static final float BEAM_BURST_DAMAGE = 2.85F;
   public static final double BEAM_BURST_WIDTH = 1.4;
   // NOVA "Hat Whirl": tension 0-20 (the hat spins on the wand), throw 20, flight 20-30, whirl and suction
   // 30-46, hush 46-52, burst 52, pause 52-66, the hat flies home 66-74, bow 80-104.
   public static final ActScript NOVA = new ActScript(ActShape.NOVA, 20, 52, 46, 52, 66, 80, 104);
   public static final int NOVA_HAT_OFF = 3;
   public static final int NOVA_THROW = 20;
   public static final int NOVA_ARRIVE = 30;
   public static final int NOVA_CATCH = 74;
   public static final float NOVA_DAMAGE = 7.0F;
   public static final double NOVA_RADIUS = 5.0;
   public static final double NOVA_THROW_RANGE = 14.0;
   // CUT "Sawing": tension 0-24 (the card blade grows, the line is drawn on the floor from 14), main 24-44
   // (two saw strokes along the line, 30 and 38), hush 44-50 (blade overhead), the grand cut 50,
   // pause 50-66 (the seam glows), bow 76-100.
   public static final ActScript CUT = new ActScript(ActShape.CUT, 24, 50, 44, 50, 66, 76, 100);
   public static final int CUT_TELEGRAPH = 14;
   public static final int[] CUT_SAWS = {30, 38};
   public static final float CUT_SAW_DAMAGE = 1.0F;
   public static final float CUT_DAMAGE = 6.0F;
   public static final double CUT_LENGTH = 24.0;
   public static final double CUT_WIDTH = 1.2;
   // BARRAGE "Card Fan": tension 0-20 (a fan of twelve cards opens), volleys 20/28/36, hush 42-50 (the last
   // three gather overhead), the finale volley 50, pause 56-64, bow 76-100.
   public static final ActScript BARRAGE = new ActScript(ActShape.BARRAGE, 20, 50, 42, 56, 64, 76, 100);
   public static final int[] BARRAGE_VOLLEYS = {20, 28, 36, 50};
   public static final int BARRAGE_CARDS_PER_VOLLEY = 3;
   public static final float BARRAGE_DAMAGE = 1.35F;
   // AEGIS "Curtain": the curtain falls 0-12 (-75 % damage and reflection from tick 0 to the reveal),
   // the slow turn inside 12-44, hush 44-50, burst open 50, the shreds rain 50-62, bow 72-96.
   public static final ActScript AEGIS = new ActScript(ActShape.AEGIS, 12, 50, 44, 50, 62, 72, 96);
   public static final float AEGIS_DAMAGE_FACTOR = 0.25F;
   public static final float AEGIS_DAMAGE = 4.0F;
   public static final double AEGIS_RADIUS = 4.0;
   // FINALE "Metamorphosis" (the spare number): the wand calls the chest 0-24 (lands 24), swords 30/38/46,
   // hush 52-58 (the chest rattles), reveal 58 (swap), pause 58-68, bow 76-100.
   public static final ActScript FINALE = new ActScript(ActShape.FINALE, 24, 58, 52, 58, 68, 76, 100);
   public static final int FINALE_LAND = 24;
   public static final int[] FINALE_SWORDS = {30, 38, 46};
   public static final float FINALE_SWORD_DAMAGE = 2.5F;
   public static final double FINALE_RADIUS = 1.6;
   public static final double FINALE_DROP_RANGE = 20.0;

   public static ActScript of(ActShape shape) {
      return switch (shape) {
         case BEAM -> BEAM;
         case NOVA -> NOVA;
         case CUT -> CUT;
         case BARRAGE -> BARRAGE;
         case AEGIS -> AEGIS;
         case FINALE -> FINALE;
      };
   }

   /** Pure: the dramatic part a tick belongs to. */
   public Part part(double tick) {
      if (tick < this.main) {
         return Part.TENSION;
      }

      if (tick < this.hushStart) {
         return Part.MAIN;
      }

      return tick < this.pauseEnd ? Part.CLIMAX : Part.FINISH;
   }

   public boolean hushed(int tick) {
      return tick >= this.hushStart && tick < this.reveal;
   }

   public boolean paused(int tick) {
      return tick >= this.pauseStart && tick < this.pauseEnd;
   }

   public boolean bowing(int tick) {
      return tick >= this.bowStart && tick < this.length;
   }

   /** BEAM damage ticks of the main ray: 28, 33, ... up to (not including) 70. */
   public static boolean beamPulse(int tick) {
      return tick >= BEAM_START && tick < BEAM_END && (tick - BEAM_START) % BEAM_PERIOD == 0;
   }

   /** BEAM: the ray is drawn (main ray, the thinning thread and the overdrive flare). */
   public static boolean beamLit(double tick) {
      return tick >= BEAM_START && tick < BEAM_BURST_END;
   }

   public static boolean barrageVolley(int tick) {
      return contains(BARRAGE_VOLLEYS, tick);
   }

   /** The last volley is the climax (all remaining cards, a tight spread). */
   public static boolean barrageFinale(int tick) {
      return tick == BARRAGE_VOLLEYS[BARRAGE_VOLLEYS.length - 1];
   }

   public static boolean cutSaw(int tick) {
      return contains(CUT_SAWS, tick);
   }

   public static boolean finaleSword(int tick) {
      return contains(FINALE_SWORDS, tick);
   }

   private static boolean contains(int[] ticks, int tick) {
      for (int v : ticks) {
         if (v == tick) {
            return true;
         }
      }

      return false;
   }
}
