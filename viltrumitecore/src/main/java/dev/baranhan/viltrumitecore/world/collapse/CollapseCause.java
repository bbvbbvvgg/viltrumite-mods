package dev.baranhan.viltrumitecore.world.collapse;

import java.util.Locale;

/**
 * Why blocks of a structure were destroyed. The {@code weight} scales how much one removed block counts
 * toward structural failure (1 = full, 0 = never triggers a collapse). Stable ids are part of the public
 * contract ({@code docs/city-collapse-integration.md}); new sources (a future Ultron attack, an
 * infrastructure blast) use {@link #custom(String, float)} and need no change in this package.
 */
public record CollapseCause(String id, float weight) {
   /** A Leviathan ploughs through floors or crashes. */
   public static final CollapseCause LEVIATHAN = new CollapseCause("leviathan", 1.0F);
   /** A meteor impact (Meteor Rain 2.0 or a random meteor). */
   public static final CollapseCause METEOR = new CollapseCause("meteor", 1.0F);
   /** Other Chitauri destruction: chariot crashes, siege lances, cannons. */
   public static final CollapseCause CHITAURI = new CollapseCause("chitauri", 0.6F);
   /** Hero powers that break blocks (shared HeroDestruction, legacy punch and thunderclap). */
   public static final CollapseCause HERO = new CollapseCause("hero", 0.8F);
   /** Any other explosion (TNT, creepers, mod explosions): needs a series to bring a section down. */
   public static final CollapseCause EXPLOSION = new CollapseCause("explosion", 0.35F);
   /** Operator QA command ({@code /collapse strike}). */
   public static final CollapseCause COMMAND = new CollapseCause("command", 1.0F);
   /** Ultron's machines (integration of #124-#127): full weight, like a meteor or the Leviathan. */
   public static final CollapseCause ULTRON = new CollapseCause("ultron", 1.0F);
   /** Damage done by a collapse itself (rubble, ground impact): never feeds another collapse. */
   public static final CollapseCause SECONDARY = new CollapseCause("secondary", 0.0F);

   public CollapseCause {
      id = id == null || id.isBlank() ? "unknown" : id.toLowerCase(Locale.ROOT);
      weight = Float.isFinite(weight) ? Math.max(0.0F, Math.min(1.0F, weight)) : 0.0F;
   }

   /** A cause from another system, for example {@code custom("ultron:core_blast", 1.0F)}. */
   public static CollapseCause custom(String id, float weight) {
      return new CollapseCause(id, weight);
   }

   /** True when damage of this cause can feed a collapse. */
   public boolean feeds() {
      return this.weight > 0.0F;
   }

   /** Known causes by id ({@code /collapse strike ... <cause>}); unknown ids give a full-weight custom cause. */
   public static CollapseCause byId(String id) {
      for (CollapseCause c : new CollapseCause[]{LEVIATHAN, METEOR, CHITAURI, HERO, EXPLOSION, COMMAND, ULTRON, SECONDARY}) {
         if (c.id.equals(id)) {
            return c;
         }
      }
      return custom(id, 1.0F);
   }
}
