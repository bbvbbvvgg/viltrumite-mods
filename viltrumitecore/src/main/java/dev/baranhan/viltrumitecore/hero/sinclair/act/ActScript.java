package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.act.ActShape;

/**
 * Pure beat table of each stage act (spec docs/design/2026-10-28-sinclair-design.md §6.1).
 * Ticks count from the second B press. The server deals damage on these ticks; the client
 * stages poses, props, camera and silence on the same numbers.
 *
 * @param reveal     the reveal (main contact) tick
 * @param hushStart  first tick of the drum-roll hush before the reveal
 * @param pauseStart start of the silence after the reveal
 * @param pauseEnd   end of the silence
 * @param bowStart   start of the bow
 * @param length     whole act
 */
public record ActScript(ActShape shape, int reveal, int hushStart, int pauseStart, int pauseEnd, int bowStart, int length) {
   // BEAM "Footlight Ray": prep 0-12, beam 12-42 (every 4 t), pause 42-50, bow 50-70.
   public static final ActScript BEAM = new ActScript(ActShape.BEAM, 12, 8, 42, 50, 50, 70);
   public static final int BEAM_START = 12;
   public static final int BEAM_END = 42;
   public static final int BEAM_PERIOD = 4;
   public static final float BEAM_DAMAGE = 1.2F;
   public static final double BEAM_WIDTH = 0.7;
   // NOVA "Hat Whirl": throw 0-10, suction 10-24, reveal 24, pause 24-40, catch 40-44, bow 44-64.
   public static final ActScript NOVA = new ActScript(ActShape.NOVA, 24, 18, 24, 40, 44, 64);
   public static final int NOVA_THROW = 10;
   public static final int NOVA_CATCH = 44;
   public static final float NOVA_DAMAGE = 7.0F;
   public static final double NOVA_RADIUS = 5.0;
   public static final double NOVA_THROW_RANGE = 14.0;
   // CUT "Sawing": prep 0-10, telegraph 10-20, cut 20, pause 20-36, bow 40-60.
   public static final ActScript CUT = new ActScript(ActShape.CUT, 20, 15, 20, 36, 40, 60);
   public static final int CUT_TELEGRAPH = 10;
   public static final float CUT_DAMAGE = 8.0F;
   public static final double CUT_LENGTH = 24.0;
   public static final double CUT_WIDTH = 1.2;
   // BARRAGE "Card Fan": fan 0-10, volleys 10/18/26, pause 34-42, bow 42-62.
   public static final ActScript BARRAGE = new ActScript(ActShape.BARRAGE, 10, 7, 34, 42, 42, 62);
   public static final int[] BARRAGE_VOLLEYS = {10, 18, 26};
   public static final int BARRAGE_CARDS_PER_VOLLEY = 3;
   public static final float BARRAGE_DAMAGE = 1.8F;
   // AEGIS "Curtain": curtain 0-50 (-75 % damage, reflect), reveal 50, bow 58-78.
   public static final ActScript AEGIS = new ActScript(ActShape.AEGIS, 50, 44, 50, 58, 58, 78);
   public static final float AEGIS_DAMAGE_FACTOR = 0.25F;
   public static final float AEGIS_DAMAGE = 4.0F;
   public static final double AEGIS_RADIUS = 4.0;
   // FINALE "Metamorphosis": fall 0-12, swords 18/26/34, reveal 44, pause 44-52, bow 52-72.
   public static final ActScript FINALE = new ActScript(ActShape.FINALE, 44, 38, 44, 52, 52, 72);
   public static final int FINALE_LAND = 12;
   public static final int[] FINALE_SWORDS = {18, 26, 34};
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

   public boolean hushed(int tick) {
      return tick >= this.hushStart && tick < this.reveal;
   }

   public boolean paused(int tick) {
      return tick >= this.pauseStart && tick < this.pauseEnd;
   }

   public boolean bowing(int tick) {
      return tick >= this.bowStart && tick < this.length;
   }

   /** BEAM damage ticks: 12, 16, ... up to (not including) 42. */
   public static boolean beamPulse(int tick) {
      return tick >= BEAM_START && tick < BEAM_END && (tick - BEAM_START) % BEAM_PERIOD == 0;
   }

   public static boolean barrageVolley(int tick) {
      for (int v : BARRAGE_VOLLEYS) {
         if (v == tick) {
            return true;
         }
      }

      return false;
   }

   public static boolean finaleSword(int tick) {
      for (int v : FINALE_SWORDS) {
         if (v == tick) {
            return true;
         }
      }

      return false;
   }
}
