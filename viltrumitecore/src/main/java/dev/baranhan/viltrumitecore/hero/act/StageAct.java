package dev.baranhan.viltrumitecore.hero.act;

import javax.annotation.Nullable;

/**
 * Data-only description of a hero's signature act (spec docs/design/2026-10-28-sinclair-design.md §6.2).
 * A hero returns one from {@code HeroDefinition.signatureAct}; a performer reads it and stages the
 * shape with its own code. It carries no behaviour, no entity and no state reference on purpose.
 *
 * @param shape   stage shape; null = unknown (the performer falls back)
 * @param power   relative strength, nominal 1.0 (the performer clamps it)
 * @param reach   reach in blocks (the performer clamps it)
 * @param color   0xRRGGBB tint of the act
 * @param nameKey translation key of the act's name, or null
 */
public record StageAct(@Nullable ActShape shape, float power, float reach, int color, @Nullable String nameKey) {
   public static final int MAX_KEY_LENGTH = 128;

   /** True when every field is usable: a known shape, finite positive numbers and a sane key. */
   public boolean wellFormed() {
      return this.shape != null
         && Float.isFinite(this.power) && this.power > 0.0F && this.power < 100.0F
         && Float.isFinite(this.reach) && this.reach > 0.0F && this.reach < 1024.0F
         && (this.nameKey == null || validKey(this.nameKey));
   }

   /** The act with every value inside [min, max]; color keeps only RGB. Call only on a well-formed act. */
   public StageAct clamped(float minPower, float maxPower, float minReach, float maxReach) {
      return new StageAct(this.shape, clamp(this.power, minPower, maxPower), clamp(this.reach, minReach, maxReach), this.color & 0xFFFFFF, this.nameKey);
   }

   static boolean validKey(String key) {
      if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
         return false;
      }

      for (int i = 0; i < key.length(); i++) {
         char c = key.charAt(i);
         if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '-')) {
            return false;
         }
      }

      return true;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}
