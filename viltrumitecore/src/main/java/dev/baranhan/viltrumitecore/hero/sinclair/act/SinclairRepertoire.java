package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.act.StageAct;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Sinclair's repertoire (spec docs/design/2026-10-28-sinclair-design.md §6.2): which number he
 * stages for a volunteer. Order: explicit contract → parody by public hero key / mob type →
 * the fallback "Metamorphosis". Pure: callers pass the public hero key, the mob type id and the
 * contract (already read through {@code HeroDefinition.signatureAct}); nothing here calls a hero.
 */
public final class SinclairRepertoire {
   public static final int GOLD = 0xF2C14E;
   public static final int VIOLET = 0x9B6BFF;
   public static final int LAVENDER = 0xC9B6FF;
   public static final int CRIMSON = 0x8E1B2C;
   public static final int IVORY = 0xF3EBDD;
   /** Thor's lightning blue, DIO's gold-yellow, Jinwoo's shadow violet (the newer heroes' numbers). */
   public static final int STORM = 0x7FD4FF;
   public static final int WORLD = 0xFFD23F;
   public static final int SHADOW = 0x6A3CFF;
   /** Parodies of known heroes by their public HeroId key. */
   private static final Map<String, ActShape> HEROES = Map.of(
      "homelander", ActShape.BEAM,
      "reinhard", ActShape.CUT,
      "ironman", ActShape.BARRAGE,
      "regulus", ActShape.AEGIS,
      // Thor: Mjolnir thrown, the thunderclap ring; DIO: a volley of knives; Jinwoo: the dagger's cut.
      "thor", ActShape.NOVA,
      "dio", ActShape.BARRAGE,
      "jinwoo", ActShape.CUT
   );
   /** Hero numbers staged in that hero's colour instead of the shape's default. */
   private static final Map<String, Integer> HERO_COLORS = Map.of(
      "thor", STORM,
      "dio", WORLD,
      "jinwoo", SHADOW
   );
   /** Parodies of mobs by entity type id. */
   private static final Map<String, ActShape> MOBS = Map.ofEntries(
      Map.entry("minecraft:creeper", ActShape.NOVA),
      Map.entry("minecraft:skeleton", ActShape.BARRAGE),
      Map.entry("minecraft:stray", ActShape.BARRAGE),
      Map.entry("minecraft:pillager", ActShape.BARRAGE),
      Map.entry("minecraft:blaze", ActShape.BARRAGE),
      Map.entry("minecraft:ghast", ActShape.BARRAGE),
      Map.entry("minecraft:evoker", ActShape.CUT),
      Map.entry("minecraft:vindicator", ActShape.CUT),
      Map.entry("minecraft:guardian", ActShape.BEAM),
      Map.entry("minecraft:elder_guardian", ActShape.BEAM),
      Map.entry("minecraft:iron_golem", ActShape.NOVA),
      Map.entry("minecraft:ravager", ActShape.NOVA),
      Map.entry("minecraft:shulker", ActShape.AEGIS)
   );

   /** Where the chosen number came from (HUD and tests). */
   public enum Source {
      CONTRACT,
      REPERTOIRE,
      FALLBACK
   }

   public record Choice(StageAct act, Source source) {
   }

   private SinclairRepertoire() {
   }

   /**
    * @param contract    the volunteer hero's contract, or null when it has none / is a mob
    * @param contractRead true when the volunteer is a hero whose contract was read (a throwing or absent hero is false)
    * @param heroKey     public HeroId key of a player volunteer, or null for a mob
    * @param mobTypeId   entity type id of a mob volunteer, or null for a player
    */
   public static Choice choose(@Nullable StageAct contract, boolean contractRead, @Nullable String heroKey, @Nullable String mobTypeId) {
      if (contractRead && contract != null) {
         if (contract.wellFormed()) {
            StageAct act = contract.clamped(SinclairRules.ACT_MIN_POWER, SinclairRules.ACT_MAX_POWER, SinclairRules.ACT_MIN_REACH, SinclairRules.ACT_MAX_REACH);
            return new Choice(act, Source.CONTRACT);
         }

         // A contract with an unknown shape or garbage numbers: the safe fallback number.
         return new Choice(fallback(), Source.FALLBACK);
      }

      ActShape shape = heroKey != null ? HEROES.get(heroKey) : mobTypeId != null ? MOBS.get(mobTypeId) : null;
      if (shape == null) {
         return new Choice(fallback(), Source.FALLBACK);
      }

      Integer color = heroKey != null ? HERO_COLORS.get(heroKey) : null;
      StageAct act = standard(shape);
      if (color != null) {
         act = new StageAct(shape, act.power(), act.reach(), color, act.nameKey());
      }

      return new Choice(act, Source.REPERTOIRE);
   }

   /** Sinclair's own staging of a shape at nominal power. */
   public static StageAct standard(ActShape shape) {
      return new StageAct(shape, 1.0F, defaultReach(shape), defaultColor(shape), nameKey(shape));
   }

   public static StageAct fallback() {
      return standard(ActShape.FINALE);
   }

   public static String nameKey(ActShape shape) {
      return "act.viltrumitecore.sinclair." + shape.key();
   }

   public static int defaultColor(ActShape shape) {
      return switch (shape) {
         case BEAM -> GOLD;
         case NOVA -> VIOLET;
         case CUT -> CRIMSON;
         case BARRAGE -> LAVENDER;
         case AEGIS -> CRIMSON;
         case FINALE -> GOLD;
      };
   }

   public static float defaultReach(ActShape shape) {
      return switch (shape) {
         case BEAM -> 32.0F;
         case NOVA -> (float) ActScript.NOVA_THROW_RANGE;
         case CUT -> (float) ActScript.CUT_LENGTH;
         case BARRAGE -> 24.0F;
         case AEGIS -> (float) ActScript.AEGIS_RADIUS;
         case FINALE -> (float) ActScript.FINALE_DROP_RANGE;
      };
   }
}
