package dev.baranhan.viltrumitecore.worldevent.ultron;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Ultron's node network (pure: no Minecraft classes, unit-tested). Standing nodes link to their
 * neighbours (a minimum spanning tree from the core over links of at most {@link UltronRules#LINK_RANGE},
 * plus redundant links up to {@link UltronRules#MAX_LINKS} each); power flows from an online core over
 * online nodes, so cutting a hub isolates its branch. Damage goes through the shield, then the hull,
 * under the per-hit and per-second caps.
 */
public final class UltronNetwork {
   public static final class Node {
      public final int id;
      public final UltronNodeKind kind;
      public final int site;
      public double x;
      public double y;
      public double z;
      public UltronNodeState state = UltronNodeState.PLANNED;
      public float hull;
      public float shield;
      /** Construction progress 0..1 (welding ticks / {@link UltronRules#BUILD_TICKS}). */
      public float build;
      public boolean discovered;
      public boolean powered;
      public int fortifyTicks;
      public int disabledTicks;
      public int rebootTicks;
      public int sinceHit = 1_000_000;
      /** Damage taken in the current attack (defense-held tracking) and ticks since that attack began. */
      public float attackDamage;
      public int attackTicks;
      /** Damage taken within the last second (per-second cap). */
      public float secondDamage;
      public int secondStart;
      public final List<Integer> links = new ArrayList<>();
      @Nullable
      public UUID entity;

      Node(int id, UltronNodeKind kind, int site, double x, double y, double z) {
         this.id = id;
         this.kind = kind;
         this.site = site;
         this.x = x;
         this.y = y;
         this.z = z;
         this.hull = UltronRules.hull(kind);
         this.shield = UltronRules.shield(kind);
      }

      public boolean standing() {
         return this.state.standing();
      }

      public boolean online() {
         return this.state == UltronNodeState.ONLINE;
      }

      /** Online and connected to the core: the node works. */
      public boolean active() {
         return this.state == UltronNodeState.ONLINE && this.powered;
      }

      public boolean fortified() {
         return this.fortifyTicks > 0;
      }

      public float maxShield() {
         return UltronRules.shield(this.kind, this.fortified());
      }

      public float hullFraction() {
         return this.hull / UltronRules.hull(this.kind);
      }

      public double distanceSq(double ox, double oy, double oz) {
         double dx = this.x - ox;
         double dy = this.y - oy;
         double dz = this.z - oz;
         return dx * dx + dy * dy + dz * dz;
      }

      double horizontalSq(Node o) {
         double dx = this.x - o.x;
         double dz = this.z - o.z;
         return dx * dx + dz * dz;
      }
   }

   /** What one hit did to a node. */
   public record Hit(float toShield, float toHull, boolean shieldBroke, boolean destroyed) {
      public static final Hit NONE = new Hit(0.0F, 0.0F, false, false);

      public float total() {
         return this.toShield + this.toHull;
      }
   }

   private final List<Node> nodes = new ArrayList<>();

   public Node add(UltronNodeKind kind, int site, double x, double y, double z) {
      Node n = new Node(this.nodes.size(), kind, site, x, y, z);
      this.nodes.add(n);
      return n;
   }

   public List<Node> all() {
      return Collections.unmodifiableList(this.nodes);
   }

   @Nullable
   public Node get(int id) {
      return id >= 0 && id < this.nodes.size() ? this.nodes.get(id) : null;
   }

   @Nullable
   public Node core() {
      for (Node n : this.nodes) {
         if (n.kind == UltronNodeKind.CORE) {
            return n;
         }
      }
      return null;
   }

   public int count(UltronNodeState state) {
      int c = 0;
      for (Node n : this.nodes) {
         if (n.state == state) {
            c++;
         }
      }
      return c;
   }

   public int standing() {
      int c = 0;
      for (Node n : this.nodes) {
         if (n.standing()) {
            c++;
         }
      }
      return c;
   }

   /** Online and powered nodes of a kind (CORE counts as an uplink for {@code controls}). */
   public int active(UltronNodeKind kind) {
      int c = 0;
      for (Node n : this.nodes) {
         if (n.active() && n.kind == kind) {
            c++;
         }
      }
      return c;
   }

   public int activeControllers() {
      int c = 0;
      for (Node n : this.nodes) {
         if (n.active() && n.kind.controls()) {
            c++;
         }
      }
      return c;
   }

   /** Nodes built (online, disabled or under construction past the start) — the spread's progress. */
   public int built() {
      int c = 0;
      for (Node n : this.nodes) {
         if (n.state == UltronNodeState.ONLINE || n.state == UltronNodeState.DISABLED) {
            c++;
         }
      }
      return c;
   }

   public boolean coreStanding() {
      Node core = this.core();
      return core != null && core.standing();
   }

   /**
    * Rebuilds the links of the standing nodes: Prim's tree from the core (or the first standing node)
    * over links within range, then each node takes its nearest extra neighbours up to the link cap.
    * Nodes out of range of everything stay unlinked (isolated, unpowered).
    */
   public void relink() {
      List<Node> up = new ArrayList<>();
      for (Node n : this.nodes) {
         n.links.clear();
         if (n.standing()) {
            up.add(n);
         }
      }
      if (up.isEmpty()) {
         return;
      }
      double range2 = UltronRules.LINK_RANGE * UltronRules.LINK_RANGE;
      Node root = this.core() != null && this.core().standing() ? this.core() : up.get(0);
      List<Node> tree = new ArrayList<>();
      tree.add(root);
      List<Node> rest = new ArrayList<>(up);
      rest.remove(root);
      while (!rest.isEmpty()) {
         Node bestA = null;
         Node bestB = null;
         double best = Double.MAX_VALUE;
         for (Node a : tree) {
            if (a.links.size() >= UltronRules.MAX_LINKS) {
               continue;
            }
            for (Node b : rest) {
               double d = a.horizontalSq(b);
               if (d <= range2 && d < best) {
                  best = d;
                  bestA = a;
                  bestB = b;
               }
            }
         }
         if (bestA == null) {
            // the remaining nodes cannot reach the tree: start a separate (unpowered) component
            Node orphan = rest.remove(0);
            tree.add(orphan);
            continue;
         }
         link(bestA, bestB);
         tree.add(bestB);
         rest.remove(bestB);
      }
      // redundancy: nearest extra neighbour for nodes with a single link
      for (Node a : up) {
         if (a.links.size() != 1) {
            continue;
         }
         Node bestB = null;
         double best = Double.MAX_VALUE;
         for (Node b : up) {
            if (b == a || a.links.contains(b.id) || b.links.size() >= UltronRules.MAX_LINKS) {
               continue;
            }
            double d = a.horizontalSq(b);
            if (d <= range2 && d < best) {
               best = d;
               bestB = b;
            }
         }
         if (bestB != null) {
            link(a, bestB);
         }
      }
   }

   private static void link(Node a, Node b) {
      if (!a.links.contains(b.id)) {
         a.links.add(b.id);
      }
      if (!b.links.contains(a.id)) {
         b.links.add(a.id);
      }
   }

   /** Power flows from an online core over online nodes. Returns how many nodes are powered. */
   public int repower() {
      for (Node n : this.nodes) {
         n.powered = false;
      }
      Node core = this.core();
      if (core == null || !core.online()) {
         return 0;
      }
      Deque<Node> queue = new ArrayDeque<>();
      core.powered = true;
      queue.add(core);
      int count = 1;
      while (!queue.isEmpty()) {
         Node n = queue.poll();
         for (int id : n.links) {
            Node m = this.nodes.get(id);
            if (!m.powered && m.online()) {
               m.powered = true;
               count++;
               queue.add(m);
            }
         }
      }
      return count;
   }

   /** A powered bulwark is linked to this node (faster shield regen). */
   public boolean bulwarkLinked(Node n) {
      for (int id : n.links) {
         Node m = this.nodes.get(id);
         if (m.kind == UltronNodeKind.BULWARK && m.active()) {
            return true;
         }
      }
      return false;
   }

   /** A powered repair node is linked to (or is) this node. */
   public boolean repairLinked(Node n) {
      if (n.kind == UltronNodeKind.REPAIR && n.active()) {
         return true;
      }
      for (int id : n.links) {
         Node m = this.nodes.get(id);
         if (m.kind == UltronNodeKind.REPAIR && m.active()) {
            return true;
         }
      }
      return false;
   }

   /**
    * One hit on a standing node at server tick {@code now}: capped per hit and per second, shield first,
    * then hull. A disabled node has no shield. A destroyed node takes nothing.
    */
   public static Hit damage(Node n, float requested, int now) {
      if (!n.standing() || !(requested > 0.0F)) {
         return Hit.NONE;
      }
      if (now - n.secondStart >= UltronRules.TPS) {
         n.secondStart = now;
         n.secondDamage = 0.0F;
      }
      float room = Math.max(0.0F, UltronRules.nodeSecondCap(n.kind) - n.secondDamage);
      float amount = Math.min(Math.min(requested, UltronRules.nodeHitCap(n.kind)), room);
      if (amount <= 0.0F) {
         return Hit.NONE;
      }
      n.secondDamage += amount;
      n.sinceHit = 0;
      n.attackDamage += amount;
      float toShield = 0.0F;
      boolean broke = false;
      if (n.state != UltronNodeState.DISABLED && n.shield > 0.0F) {
         toShield = Math.min(n.shield, amount);
         n.shield -= toShield;
         amount -= toShield;
         broke = n.shield <= 0.0F;
      }
      float toHull = Math.min(n.hull, amount);
      n.hull -= toHull;
      boolean destroyed = n.hull <= 0.0F;
      if (destroyed) {
         n.hull = 0.0F;
         n.shield = 0.0F;
         n.state = UltronNodeState.DESTROYED;
         n.powered = false;
      }
      return new Hit(toShield, toHull, broke, destroyed);
   }

   /** Nearest undiscovered standing node within {@code range} of a point, or null. */
   @Nullable
   public Node nearestUnknown(double x, double y, double z, double range) {
      Node best = null;
      double bestD = range * range;
      for (Node n : this.nodes) {
         if (n.standing() && !n.discovered) {
            double d = n.distanceSq(x, y, z);
            if (d <= bestD) {
               bestD = d;
               best = n;
            }
         }
      }
      return best;
   }

   /** Nearest active node (any kind, or {@code kind} when not null) to a point, or null. */
   @Nullable
   public Node nearestActive(double x, double y, double z, @Nullable UltronNodeKind kind) {
      Node best = null;
      double bestD = Double.MAX_VALUE;
      for (Node n : this.nodes) {
         if (n.active() && (kind == null || n.kind == kind)) {
            double d = n.distanceSq(x, y, z);
            if (d < bestD) {
               bestD = d;
               best = n;
            }
         }
      }
      return best;
   }

   /** Is a point inside the network's telemetry coverage (near any powered node)? */
   public boolean covered(double x, double y, double z) {
      double r2 = UltronRules.COVERAGE * UltronRules.COVERAGE;
      for (Node n : this.nodes) {
         if (n.active() && n.distanceSq(x, y, z) <= r2) {
            return true;
         }
      }
      return false;
   }

   /** The strongest standing node of a kind (hull + shield + links), or of any kind when none: the finale's assembly point. */
   @Nullable
   public Node strongest(@Nullable UltronNodeKind kind) {
      Node best = null;
      double bestScore = -1.0;
      for (Node n : this.nodes) {
         if (!n.standing() || kind != null && n.kind != kind) {
            continue;
         }
         double score = n.hull + n.shield + 40.0 * n.links.size() + (n.powered ? 60.0 : 0.0);
         if (score > bestScore) {
            bestScore = score;
            best = n;
         }
      }
      if (best == null && kind != null) {
         return this.strongest(null);
      }
      return best;
   }
}
