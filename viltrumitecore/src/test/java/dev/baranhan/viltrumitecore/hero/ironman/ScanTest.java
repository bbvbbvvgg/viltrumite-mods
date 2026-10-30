package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.OwnerSections;
import dev.baranhan.viltrumitecore.hero.ScanInfo;
import dev.baranhan.viltrumitecore.hero.ScanLine;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero;
import dev.baranhan.viltrumitecore.hero.ironman.scan.ScanAnalyzer;
import dev.baranhan.viltrumitecore.hero.ironman.scan.ScanProgress;
import dev.baranhan.viltrumitecore.hero.ironman.scan.ScanTraits;
import dev.baranhan.viltrumitecore.hero.ironman.scan.WeakSpots;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanTest {
   private static ScanProgress started() {
      ScanProgress scan = new ScanProgress();
      assertTrue(scan.toggle());
      return scan;
   }

   private static List<String> keys(List<ScanLine> lines) {
      return lines.stream().map(ScanLine::key).toList();
   }

   @Test
   void needs30TicksOnSameTarget() {
      ScanProgress scan = started();
      for (int i = 0; i < 29; i++) {
         assertSame(ScanProgress.Event.PROGRESS, scan.tick(7, true, 10, true, true), "tick " + i);
      }

      assertSame(ScanProgress.Event.DONE, scan.tick(7, true, 10, true, true));
      assertFalse(scan.active());
      assertEquals(30, IronManRules.SCAN_TICKS);
   }

   @Test
   void lockedTargetKeepsScanningWithoutAim() {
      ScanProgress scan = started();
      assertTrue(scan.seeking());
      for (int i = 0; i < 20; i++) {
         scan.tick(7, true, 10, true, true);
      }

      // Another entity offered (crosshair moved): the lock stays, no aim needed.
      scan.tick(8, true, 10, true, true);
      assertEquals(7, scan.targetId());
      assertEquals(21, scan.ticks());
      assertEquals(7, scan.resolve(8));
      for (int i = 0; i < 8; i++) {
         scan.tick(-1, true, 10, true, true);
      }

      assertSame(ScanProgress.Event.DONE, scan.tick(-1, true, 10, true, true));
   }

   @Test
   void pressAgainCancels() {
      ScanProgress scan = started();
      assertFalse(scan.toggle());
      assertFalse(scan.active());
   }

   @Test
   void cancelledWhenHelmetOpens() {
      ScanProgress scan = started();
      scan.tick(7, true, 10, true, true);
      assertSame(ScanProgress.Event.CANCELLED, scan.tick(7, true, 10, true, false));
      assertFalse(scan.active());
      assertEquals(-1, scan.targetId(), "no stuck reticle");
   }

   @Test
   void cancelledOnTargetDeath() {
      ScanProgress scan = started();
      scan.tick(7, true, 10, true, true);
      assertSame(ScanProgress.Event.CANCELLED, scan.tick(-1, false, 10, true, true));
   }

   @Test
   void cancelledOutOfRange() {
      ScanProgress scan = started();
      scan.tick(7, true, 10, true, true);
      assertSame(ScanProgress.Event.CANCELLED, scan.tick(7, true, IronManRules.SCAN_RANGE + 1, true, true));
   }

   @Test
   void lineOfSightGrace() {
      ScanProgress scan = started();
      scan.tick(7, true, 10, true, true);
      for (int i = 0; i < IronManRules.SCAN_LOS_GRACE; i++) {
         assertSame(ScanProgress.Event.PROGRESS, scan.tick(-1, true, 10, false, true));
      }

      assertSame(ScanProgress.Event.CANCELLED, scan.tick(-1, true, 10, false, true));
   }

   @Test
   void hiddenFromScanRefused() {
      assertFalse(ScanProgress.scannable(true, false, true), "Mark 15 camo");
      assertFalse(ScanProgress.scannable(true, true, false), "spectator");
      assertTrue(ScanProgress.scannable(true, false, false));
   }

   @Test
   void fireImmuneListed() {
      ScanTraits blaze = new ScanTraits(false, true, 0, false, 0, false, false, false, false, false, true, ScanInfo.EMPTY);
      assertTrue(keys(ScanAnalyzer.resists(blaze)).contains("scan.viltrumitecore.resist.fire_immune"));
      assertTrue(keys(WeakSpots.of(blaze)).contains("scan.viltrumitecore.weak.water"));
   }

   @Test
   void undeadWeakSpot() {
      ScanTraits zombie = new ScanTraits(false, false, 0, false, 0, false, false, false, true, false, false, ScanInfo.EMPTY);
      assertEquals(List.of("scan.viltrumitecore.weak.undead"), keys(WeakSpots.of(zombie)));
   }

   @Test
   void plainMobHasNoInventedWeakSpot() {
      // Cow / iron golem: no vanilla rule matches → "not detected".
      assertEquals(List.of(WeakSpots.NONE), keys(WeakSpots.of(ScanTraits.plain(false))));
      ScanTraits golem = new ScanTraits(false, false, 0, false, 1.0, false, false, false, false, false, false, ScanInfo.EMPTY);
      assertEquals(List.of(WeakSpots.NONE), keys(WeakSpots.of(golem)));
      assertEquals(List.of("scan.viltrumitecore.resist.knockback"), keys(ScanAnalyzer.resists(golem)));
      assertEquals("100", ScanAnalyzer.resists(golem).get(0).args().get(0));
   }

   @Test
   void playerWithoutHeroInfo() {
      ScanTraits player = ScanTraits.plain(true);
      assertTrue(ScanAnalyzer.resists(player).isEmpty());
      assertEquals(List.of(WeakSpots.NONE), keys(WeakSpots.of(player)));
   }

   @Test
   void regulusLionActiveVsInactive() {
      assertTrue(RegulusHero.scanInfoFor(false).isEmpty());
      ScanInfo active = RegulusHero.scanInfoFor(true);
      assertEquals(List.of("scan.viltrumitecore.regulus.stopped_time"), keys(active.protections()));
      ScanTraits regulus = new ScanTraits(true, false, 0, false, 0, false, false, false, false, false, false, active);
      assertTrue(keys(ScanAnalyzer.resists(regulus)).contains("scan.viltrumitecore.regulus.stopped_time"));
   }

   @Test
   void homelanderReductionListed() {
      ScanInfo info = HomelanderHero.scanInfoFor(true, 60.0F, 2.5F);
      assertEquals(List.of("scan.viltrumitecore.homelander.reduction", "scan.viltrumitecore.homelander.ignore"), keys(info.protections()));
      assertEquals("60", info.protections().get(0).args().get(0));
      assertEquals("2.5", info.protections().get(1).args().get(0));
      assertTrue(HomelanderHero.scanInfoFor(false, 60.0F, 2.5F).isEmpty(), "no kit, no stats");
   }

   @Test
   void resistanceEffectListed() {
      ScanTraits resisting = new ScanTraits(false, false, 2, false, 0, false, false, false, false, false, false, ScanInfo.EMPTY);
      ScanLine line = ScanAnalyzer.resists(resisting).get(0);
      assertEquals("scan.viltrumitecore.resist.resistance", line.key());
      assertEquals(List.of("2", "40"), line.args());
   }

   @Test
   void ironManScanInfoMirrorsSuit() {
      ScanInfo bare = IronManHero.scanInfoFor(false, false, 0, false, true);
      assertTrue(bare.isEmpty());
      ScanInfo suit = IronManHero.scanInfoFor(true, true, 2, true, false);
      assertEquals(List.of("scan.viltrumitecore.ironman.nano_armor", "scan.viltrumitecore.ironman.no_fall", "scan.viltrumitecore.ironman.shield"),
         keys(suit.protections()));
      assertTrue(suit.weakSpots().isEmpty(), "no invented weak spot");
      assertEquals(List.of("scan.viltrumitecore.ironman.overheats", "scan.viltrumitecore.ironman.weapons_offline", "scan.viltrumitecore.ironman.helmet_open"),
         keys(suit.conditions()));
   }

   @Test
   void highlightLasts200() {
      OwnerSections sections = new OwnerSections();
      sections.set(OwnerSection.SCAN, new HeroOwnerSnapshot.Section(new int[]{7}, new int[]{IronManRules.SCAN_HIGHLIGHT_TICKS}), 1000L);
      assertEquals(1, sections.ids(OwnerSection.SCAN, 1199L).length);
      assertEquals(0, sections.ids(OwnerSection.SCAN, 1200L).length);
   }

   @Test
   void threatUpdateKeepsScanExpiry() {
      OwnerSections sections = new OwnerSections();
      sections.set(OwnerSection.SCAN, new HeroOwnerSnapshot.Section(new int[]{7}, new int[]{200}), 0L);
      sections.set(OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(new int[]{3, 4}), 50L);
      sections.set(OwnerSection.MARKS, HeroOwnerSnapshot.Section.of(new int[]{3}), 60L);
      sections.set(OwnerSection.THREATS, HeroOwnerSnapshot.Section.EMPTY, 70L);
      assertEquals(7, sections.ids(OwnerSection.SCAN, 150L)[0]);
      assertEquals(0, sections.ids(OwnerSection.SCAN, 200L).length);
   }
}
