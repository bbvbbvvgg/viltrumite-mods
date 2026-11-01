package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UltronNetworkTest {
   /** core at the origin, a chain east (a → b) and a node north (c), all online. */
   private static UltronNetwork chain() {
      UltronNetwork net = new UltronNetwork();
      net.add(UltronNodeKind.CORE, 0, 0, 64, 0).state = UltronNodeState.ONLINE;
      net.add(UltronNodeKind.UPLINK, 1, 60, 64, 0).state = UltronNodeState.ONLINE;
      net.add(UltronNodeKind.FOUNDRY, 2, 120, 64, 0).state = UltronNodeState.ONLINE;
      net.add(UltronNodeKind.SENSOR, 3, 0, 64, 70).state = UltronNodeState.ONLINE;
      net.relink();
      net.repower();
      return net;
   }

   @Test
   void linksFormATreeWithinRange() {
      UltronNetwork net = chain();
      assertTrue(net.get(0).links.contains(1));
      assertTrue(net.get(1).links.contains(2));
      assertTrue(net.get(0).links.contains(3));
      assertFalse(net.get(0).links.contains(2), "120 blocks is beyond the link range");
      for (UltronNetwork.Node n : net.all()) {
         assertTrue(n.links.size() <= UltronRules.MAX_LINKS);
         for (int id : n.links) {
            assertTrue(net.get(id).links.contains(n.id), "links are symmetric");
         }
      }
      assertEquals(4, net.repower());
   }

   @Test
   void cuttingAHubIsolatesItsBranch() {
      UltronNetwork net = chain();
      net.get(1).state = UltronNodeState.DISABLED;
      net.repower();
      assertTrue(net.get(0).powered);
      assertFalse(net.get(1).powered);
      assertFalse(net.get(2).powered, "the foundry behind the hacked uplink loses power");
      assertFalse(net.get(2).active());
      assertTrue(net.get(3).active());
      assertEquals(0, net.active(UltronNodeKind.FOUNDRY));
      assertEquals(1, net.activeControllers(), "only the core controls now");
   }

   @Test
   void noCoreNoPower() {
      UltronNetwork net = chain();
      net.get(0).state = UltronNodeState.DESTROYED;
      net.relink();
      assertEquals(0, net.repower());
      assertFalse(net.coreStanding());
      for (UltronNetwork.Node n : net.all()) {
         assertFalse(n.active());
      }
   }

   @Test
   void farNodeStaysIsolated() {
      UltronNetwork net = chain();
      net.add(UltronNodeKind.REPAIR, 4, 500, 64, 500).state = UltronNodeState.ONLINE;
      net.relink();
      net.repower();
      assertTrue(net.get(4).links.isEmpty());
      assertFalse(net.get(4).powered);
   }

   @Test
   void hitsAreCappedPerHitAndPerSecond() {
      UltronNetwork net = chain();
      UltronNetwork.Node core = net.get(0);
      float cap = UltronRules.nodeHitCap(UltronNodeKind.CORE);
      UltronNetwork.Hit hit = UltronNetwork.damage(core, 10_000.0F, 100);
      assertEquals(cap, hit.total(), 1.0E-4);
      assertEquals(cap, hit.toShield(), 1.0E-4, "the shield takes it first");
      float second = UltronRules.nodeSecondCap(UltronNodeKind.CORE);
      float dealt = cap;
      for (int i = 0; i < 10; i++) {
         dealt += UltronNetwork.damage(core, 10_000.0F, 100 + i).total();
      }
      assertEquals(second, dealt, 1.0E-3, "a second holds at most the per-second cap");
      assertTrue(UltronNetwork.damage(core, 10_000.0F, 119).total() == 0.0F);
      assertTrue(UltronNetwork.damage(core, 10_000.0F, 120).total() > 0.0F, "a new second opens");
   }

   @Test
   void shieldThenHullThenDestroyed() {
      UltronNetwork net = chain();
      UltronNetwork.Node sensor = net.get(3);
      float shield = sensor.shield;
      float hull = sensor.hull;
      int now = 0;
      boolean broke = false;
      UltronNetwork.Hit last = UltronNetwork.Hit.NONE;
      int hits = 0;
      while (sensor.standing() && hits < 1000) {
         last = UltronNetwork.damage(sensor, 5.0F, now);
         broke |= last.shieldBroke();
         now += 20;
         hits++;
      }
      assertTrue(broke);
      assertTrue(last.destroyed());
      assertEquals(UltronNodeState.DESTROYED, sensor.state);
      assertEquals((int)Math.ceil((shield + hull) / 5.0F), hits);
      assertSame(UltronNetwork.Hit.NONE, UltronNetwork.damage(sensor, 5.0F, now + 20));
   }

   @Test
   void disabledNodeHasNoShield() {
      UltronNetwork net = chain();
      UltronNetwork.Node n = net.get(1);
      n.state = UltronNodeState.DISABLED;
      UltronNetwork.Hit hit = UltronNetwork.damage(n, 10.0F, 0);
      assertEquals(0.0F, hit.toShield());
      assertEquals(10.0F, hit.toHull(), 1.0E-4);
   }

   @Test
   void supportLinksCoverageAndSearch() {
      UltronNetwork net = chain();
      net.add(UltronNodeKind.BULWARK, 4, 60, 64, 60).state = UltronNodeState.ONLINE;
      net.add(UltronNodeKind.REPAIR, 5, -60, 64, 0).state = UltronNodeState.ONLINE;
      net.relink();
      net.repower();
      UltronNetwork.Node bulwark = net.get(4);
      assertFalse(bulwark.links.isEmpty());
      UltronNetwork.Node neighbour = net.get(bulwark.links.get(0));
      assertTrue(net.bulwarkLinked(neighbour));
      assertTrue(net.repairLinked(net.get(5)));
      assertTrue(net.repairLinked(net.get(0)));
      assertTrue(net.covered(0, 64, 40));
      assertFalse(net.covered(400, 64, 400));
      assertSame(net.get(1), net.nearestUnknown(55, 64, 0, 30));
      net.get(1).discovered = true;
      assertNull(net.nearestUnknown(55, 64, 0, 30));
      assertSame(net.get(2), net.nearestActive(130, 64, 0, null));
      assertSame(net.get(5), net.nearestActive(130, 64, 0, UltronNodeKind.REPAIR));
      assertSame(net.get(2), net.strongest(UltronNodeKind.FOUNDRY));
      net.get(2).state = UltronNodeState.DESTROYED;
      assertNotNull(net.strongest(UltronNodeKind.FOUNDRY), "no foundry left: the strongest node of any kind");
   }

   @Test
   void builtCountsOnlineAndDisabled() {
      UltronNetwork net = chain();
      net.add(UltronNodeKind.REPAIR, 4, -60, 64, 0).state = UltronNodeState.BUILDING;
      net.add(UltronNodeKind.REPAIR, 5, -60, 64, 60);
      net.get(3).state = UltronNodeState.DISABLED;
      assertEquals(4, net.built());
      assertEquals(5, net.standing());
      assertEquals(1, net.count(UltronNodeState.PLANNED));
   }
}
