package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;

/**
 * Spec items 5, 6, 8 (2026-10-28): hitbox and eye height after hero change, suit exit, death,
 * respawn, relog, dimension change, and the crouch path. A pure model of the live body
 * (Entity.dimensions + eyeHeight), the size event (HeroBodySize) and the tracker decision
 * (HeroBodyTracker.shouldRefresh + HeroBodySize.matches), driven through the event orders that
 * left a stale box before the fix.
 */
class HeroBodyTransitionsTest {
   private static final float EPS = 1.0E-4F;
   private static final Map<Pose, EntityDimensions> VANILLA = new EnumMap<>(Pose.class);
   private static final Map<Pose, Float> EYE = new EnumMap<>(Pose.class);
   private static final HeroBodySize.Scale HUMAN = HeroBodySize.Scale.VANILLA;
   private static final HeroBodySize.Scale THOR = HeroBodySize.Scale.of(ThorHero.BODY_WIDTH_SCALE, ThorHero.BODY_HEIGHT_SCALE);
   private static final HeroBodySize.Scale HULK = HeroBodySize.Scale.of(HulkbusterRules.WIDTH_SCALE, HulkbusterRules.HEIGHT_SCALE);

   static {
      put(Pose.STANDING, EntityDimensions.scalable(0.6F, 1.8F), 1.62F);
      put(Pose.CROUCHING, EntityDimensions.scalable(0.6F, 1.5F), 1.27F);
      put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.SLEEPING, EntityDimensions.fixed(0.2F, 0.2F), 0.2F);
      put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F), 1.62F);
   }

   private static void put(Pose pose, EntityDimensions dims, float eye) {
      VANILLA.put(pose, dims);
      EYE.put(pose, eye);
   }

   /**
    * One side's player object: the hero scale its synced data gives now, the live dimensions as
    * the last refreshDimensions left them, and the tracker memory (LAST, GAVE_UP).
    */
   private static final class Body {
      HeroBodySize.Scale scale;
      Pose pose = Pose.STANDING;
      float width;
      float height;
      float eye;
      HeroBodySize.Scale last;
      boolean gaveUp;
      int refreshes;

      /** Entity constructor: the size event runs with whatever hero data the new object has. */
      Body(HeroBodySize.Scale atConstruction) {
         this.scale = atConstruction;
         this.refresh();
         this.refreshes = 0;
      }

      /** Entity.refreshDimensions -> EntityEvent.Size -> HeroSizeEvents. */
      void refresh() {
         EntityDimensions d = HeroBodySize.dimensions(VANILLA.get(this.pose), this.pose, this.scale);
         this.width = d.width;
         this.height = d.height;
         this.eye = HeroBodySize.eyeHeight(EYE.get(this.pose), this.pose, this.scale);
         this.refreshes++;
      }

      /** Entity.setPose / a DATA_POSE packet: vanilla refreshes on the pose key. */
      void pose(Pose pose) {
         this.pose = pose;
         this.refresh();
      }

      boolean matches() {
         return HeroBodySize.matches(this.width, this.height, this.eye, VANILLA.get(this.pose), EYE.get(this.pose), this.pose, this.scale);
      }

      /** HeroBodyTracker.sync (player tick END, and every hero id / snapshot change). */
      void sync() {
         HeroBodySize.Scale previous = this.last;
         this.last = this.scale;
         if (HeroBodyTracker.shouldRefresh(previous, this.scale, this.matches(), this.gaveUp)) {
            this.refresh();
         }
      }

      void assertSize(HeroBodySize.Scale expected, String why) {
         EntityDimensions d = HeroBodySize.dimensions(VANILLA.get(this.pose), this.pose, expected);
         assertEquals(d.width, this.width, EPS, why + " width");
         assertEquals(d.height, this.height, EPS, why + " height");
         assertEquals(HeroBodySize.eyeHeight(EYE.get(this.pose), this.pose, expected), this.eye, EPS, why + " eye");
      }
   }

   @Test
   void staleSizeFromConstructionIsHealedOnTheFirstTick() {
      // A new player object sized from stale hero data, then the real data says human: before the fix
      // the first sight of a vanilla scale did nothing and the box stayed big until a crouch.
      Body body = new Body(THOR);
      body.scale = HUMAN;
      body.sync();
      body.assertSize(HUMAN, "respawn / relog / dimension change as a human");
      // The opposite: built vanilla, the data says Thor.
      Body thor = new Body(HUMAN);
      thor.scale = THOR;
      thor.sync();
      thor.assertSize(THOR, "first tick as Thor");
   }

   @Test
   void poseRefreshInsideAHalfAppliedPacketIsHealed() {
      // Client: one entity data packet carries DATA_POSE (low id, applied first) and the hero id /
      // snapshot (high ids). The pose refresh runs with the old hero; the old tracker only looked at
      // its own scale history, which could already equal the new scale.
      Body body = new Body(HUMAN);
      body.sync();
      body.scale = THOR;
      body.sync();
      body.assertSize(THOR, "Thor");
      // Packet: pose STANDING -> CROUCHING applied while the hero data still reads Thor ...
      body.pose(Pose.CROUCHING);
      // ... then the hero key lands: human. The key change resizes at once (onSyncedDataUpdated).
      body.scale = HUMAN;
      body.sync();
      body.assertSize(HUMAN, "human after the packet");
      // A pose refresh computed from a transient scale with no net scale change (LAST == now):
      body.scale = THOR;
      body.pose(Pose.STANDING);
      body.scale = HUMAN;
      body.sync();
      body.assertSize(HUMAN, "level-triggered: a mismatch alone refreshes");
   }

   @Test
   void everyHeroTransitionEndsAtTheHeroSizeInEveryPose() {
      HeroBodySize.Scale[] heroes = {HUMAN, THOR, HULK};
      for (Pose pose : VANILLA.keySet()) {
         for (HeroBodySize.Scale from : heroes) {
            for (HeroBodySize.Scale to : heroes) {
               Body body = new Body(from);
               body.pose(pose);
               body.sync();
               body.scale = to;
               body.sync();
               body.assertSize(to, pose + " " + from + " -> " + to);
               // A crouch and back never changes the hero size (the "shift resets it" symptom).
               body.pose(Pose.CROUCHING);
               body.sync();
               body.assertSize(to, pose + " crouch");
               body.pose(pose);
               body.sync();
               body.assertSize(to, pose + " back");
            }
         }
      }
   }

   @Test
   void deathAndRespawnKeepTheRightBody() {
      Body dying = new Body(THOR);
      dying.sync();
      dying.pose(Pose.DYING);
      dying.sync();
      assertEquals(0.2F, dying.height, EPS, "a dying body is the fixed vanilla 0.2 box");
      // Respawn: a new object, constructed before the hero data is restored.
      Body respawned = new Body(HUMAN);
      respawned.sync();
      respawned.scale = THOR;
      respawned.sync();
      respawned.assertSize(THOR, "Thor respawns as Thor");
      // A Hulkbuster dies: the layer is reset (IronManHulkbuster.onDeath), the new body is Tony.
      Body tony = new Body(HULK);
      tony.scale = HUMAN;
      tony.sync();
      tony.assertSize(HUMAN, "Tony after the Hulkbuster died");
   }

   @Test
   void aSizeAnotherModOwnsIsNotRefreshedEveryTick() {
      Body body = new Body(THOR);
      body.sync();
      body.width = 5.0F;
      body.gaveUp = false;
      assertTrue(HeroBodyTracker.shouldRefresh(THOR, THOR, false, false), "first mismatch: try once");
      assertFalse(HeroBodyTracker.shouldRefresh(THOR, THOR, false, true), "same scale + pose already failed: stop");
      assertTrue(HeroBodyTracker.shouldRefresh(THOR, HUMAN, false, true), "a new scale always refreshes");
      assertFalse(HeroBodyTracker.shouldRefresh(THOR, THOR, true, false), "a matching body is left alone");
   }

   @Test
   void hulkbusterBigOnTheWireEqualsTheServerLayer() {
      // Both sides now read the snapshot (IronManHero.hulkBig); it must agree with the live layer on
      // every tick of drop, assembly, active and exit, so the box changes on the same tick.
      for (boolean air : new boolean[]{false, true}) {
         HulkbusterLayer layer = new HulkbusterLayer();
         assertTrue(layer.start(air));
         int guard = 0;
         while (layer.phase() != HulkbusterLayer.Phase.ACTIVE && guard++ < 1000) {
            assertEquals(layer.big(), wireBig(layer), layer.phase() + " t" + layer.ticks());
            HulkbusterLayer.Event event = layer.tick();
            if (event == HulkbusterLayer.Event.READY) {
               layer.activate();
            }
         }

         assertTrue(layer.big() && wireBig(layer), "active");
         assertTrue(layer.exit());
         guard = 0;
         while (layer.phase() == HulkbusterLayer.Phase.EXITING && guard++ < 1000) {
            assertEquals(layer.big(), wireBig(layer), "exit t" + layer.ticks());
            layer.tick();
         }

         assertFalse(layer.big());
         assertFalse(wireBig(layer), "after the exit: vanilla on both sides");
      }
   }

   private static boolean wireBig(HulkbusterLayer layer) {
      int scene = layer.busy()
         ? HulkbusterWire.scene(layer.sceneTick(), layer.air() ? 1 : 2, 0.0F, layer.phase() == HulkbusterLayer.Phase.ASSEMBLING ? layer.ticks() : -1)
         : 0;
      return HulkbusterLayer.bigOnWire(layer.phase().ordinal(), scene);
   }

   @Test
   void thorBoxIsTheModelBox() {
      Body thor = new Body(THOR);
      assertEquals(2.0F, thor.width, EPS, "width = model width with arms (geo +-1.03)");
      assertEquals(3.15F, thor.height, EPS);
      assertEquals(2.835F, thor.eye, EPS);
   }
}
