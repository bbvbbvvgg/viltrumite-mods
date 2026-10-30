package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pure text plan of the copy card (design 2026-11-02 §2.3): which lang keys the card shows for a volunteer.
 * Every key lives in Sinclair's own lang pack ({@code assets/viltrumitecore_sinclair/lang}); nothing reads
 * another hero's strings. The card never presents the spare number as the volunteer's own ultimate.
 */
public final class CopyCard {
   static final String P = "copy.viltrumitecore.sinclair.";
   /** Heroes whose signature Sinclair parodies with his own number (SinclairRepertoire.HEROES). */
   static final Set<String> PARODIED = Set.of("homelander", "reinhard", "ironman", "regulus");
   /** Every public hero key the card can name (HeroId keys). */
   static final Set<String> HEROES = Set.of("human", "viltrumite", "regulus", "homelander", "ironman", "reinhard", "thor", "dio", "sinclair", "jinwoo");

   /**
    * @param title    act name (e.g. "Footlight Beam")
    * @param origin   what was copied from the volunteer ("Heat Vision"), or the spare-number note
    * @param heroName hero name of a player volunteer, or null for a mob (the client shows the mob type name)
    * @param desc     short description of what the number does
    * @param fallback the spare number: the card wears a "spare act" ribbon instead of a stolen signature
    */
   public record Plan(String title, String origin, @Nullable String heroName, String desc, String header, boolean fallback) {
   }

   private CopyCard() {
   }

   /**
    * @param shape   the captured act's shape
    * @param heroKey public hero key of a player volunteer, or null for a mob
    * @param source  where the number came from (snapshot flags: contract / fallback, otherwise repertoire)
    */
   public static Plan plan(ActShape shape, @Nullable String heroKey, SinclairRepertoire.Source source) {
      boolean fallback = source == SinclairRepertoire.Source.FALLBACK || shape == ActShape.FINALE;
      String hero = heroKey == null ? null : P + "hero." + (HEROES.contains(heroKey) ? heroKey : "unknown");
      String origin;
      if (fallback) {
         origin = P + "origin.fallback";
      } else if (source == SinclairRepertoire.Source.CONTRACT) {
         origin = P + "origin.contract";
      } else if (heroKey != null && PARODIED.contains(heroKey)) {
         origin = P + "origin." + heroKey;
      } else {
         origin = P + "origin.mob." + shape.key();
      }

      return new Plan(SinclairRepertoire.nameKey(shape), origin, hero, P + "desc." + shape.key(), P + (fallback ? "header.fallback" : "header"), fallback);
   }

   /** Every key {@link #plan} can return, for the lang test. */
   public static java.util.List<String> allKeys() {
      java.util.List<String> keys = new java.util.ArrayList<>();
      keys.add(P + "header");
      keys.add(P + "header.fallback");
      keys.add(P + "header.volunteer");
      keys.add(P + "origin.fallback");
      keys.add(P + "origin.contract");
      keys.add(P + "hero.unknown");
      keys.add(P + "volunteer");
      for (String hero : PARODIED) {
         keys.add(P + "origin." + hero);
      }

      for (String hero : HEROES) {
         keys.add(P + "hero." + hero);
      }

      for (ActShape shape : ActShape.values()) {
         keys.add(P + "desc." + shape.key());
         if (shape != ActShape.FINALE) {
            keys.add(P + "origin.mob." + shape.key());
         }
      }

      return keys;
   }
}
