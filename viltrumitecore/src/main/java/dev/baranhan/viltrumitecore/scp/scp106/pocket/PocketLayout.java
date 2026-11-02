package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pure, deterministic plan of one pocket-dimension instance (docs/scp106/pocket-dimension.md): a 32 x 32 grid of
 * 16-block cells (one chunk each) split into five themed zones, rooms of several kinds on top of it, a route graph
 * (randomised spanning tree + loops, so every room is reachable), the purposes of dead ends, the three escape routes
 * and the controlled anomalies. Same seed, same plan: the chunk generator, the server rules and the client ambience
 * all rebuild it from the instance seed instead of saving it. No Minecraft classes, unit-tested.
 */
public final class PocketLayout {
   public static final int CELL = 16;
   public static final int GRID = 32;
   public static final int SIZE = CELL * GRID;
   /** Top block of the common floor; feet stand at FLOOR + 1. */
   public static final int FLOOR = 64;
   /** Instance n occupies blocks [n * SLOT_STRIDE, n * SLOT_STRIDE + SIZE) on X and [0, SIZE) on Z. */
   public static final int SLOT_STRIDE = 4096;
   public static final int MAX_SLOTS = 64;
   public static final int NOTE_COUNT = 9;
   public static final int N = 0;
   public static final int E = 1;
   public static final int S = 2;
   public static final int W = 3;
   static final int[] DX = {0, 1, 0, -1};
   static final int[] DZ = {-1, 0, 1, 0};

   /** The five spaces of the design: corridors of decay, colossal voids, flooded sections, false safe zones, the Old Man's domain. */
   public enum Zone {
      DECAY, VOID, FLOODED, FALSE_SAFE, DOMAIN;

      private static final Zone[] VALUES = values();

      public static Zone byId(int id) {
         return id >= 0 && id < VALUES.length ? VALUES[id] : DECAY;
      }
   }

   public enum Kind {
      CORRIDOR, HALL, VOID, FLOODED, FLOODED_CORRIDOR, APARTMENT, STREET, SHAFT, THRONE
   }

   public enum Purpose {
      NONE, ARRIVAL, NOTE, SIGIL, SECRET_EXIT, HOME_DOOR, BREACH, LISTENING, VANTAGE
   }

   /** One room: a rectangle of cells with a kind, a zone, a private seed and its doors. */
   public static final class Room {
      public final int id;
      public final int cx;
      public final int cz;
      public final int w;
      public final int h;
      public final Kind kind;
      public final Zone zone;
      public final long seed;
      public final List<Door> doors = new ArrayList<>();
      public Purpose purpose = Purpose.NONE;
      public int note = -1;
      public int sigil;
      /** 0..3: rot grows towards the domain, the first escape route can be followed by it. */
      public int corrosion;
      /** Side of the cell wall that hides the secret passage (SECRET_EXIT only). */
      public int secretSide = -1;
      /** Rooms between the arrival and this one. */
      public int depth = -1;
      /** VOID: has the stair that returns to the hall. */
      public boolean loopStair;
      /** APARTMENT: has a door that opens somewhere else. */
      public boolean wrongDoor;

      Room(int id, int cx, int cz, int w, int h, Kind kind, Zone zone, long seed) {
         this.id = id;
         this.cx = cx;
         this.cz = cz;
         this.w = w;
         this.h = h;
         this.kind = kind;
         this.zone = zone;
         this.seed = seed;
      }

      public boolean contains(int cellX, int cellZ) {
         return cellX >= this.cx && cellX < this.cx + this.w && cellZ >= this.cz && cellZ < this.cz + this.h;
      }

      /** Slot-local block bounds. */
      public int minX() {
         return this.cx * CELL;
      }

      public int minZ() {
         return this.cz * CELL;
      }

      public int sizeX() {
         return this.w * CELL;
      }

      public int sizeZ() {
         return this.h * CELL;
      }

      public double centerX() {
         return this.minX() + this.sizeX() / 2.0;
      }

      public double centerZ() {
         return this.minZ() + this.sizeZ() / 2.0;
      }

      public int degree() {
         return this.doors.size();
      }

      @Override
      public String toString() {
         return this.kind + "#" + this.id + "@" + this.cx + "," + this.cz + " " + this.w + "x" + this.h + " " + this.zone + " " + this.purpose;
      }
   }

   /** A doorway on the wall between cell (cellX, cellZ) of room a and its neighbour on {@code side}, room b. */
   public record Door(int cellX, int cellZ, int side, int a, int b) {
      public int other(int room) {
         return room == this.a ? this.b : this.a;
      }

      /** Slot-local block coordinates of the middle of the opening on the shared wall line. */
      public int blockX() {
         return switch (this.side) {
            case E -> (this.cellX + 1) * CELL;
            case W -> this.cellX * CELL;
            default -> this.cellX * CELL + 8;
         };
      }

      public int blockZ() {
         return switch (this.side) {
            case S -> (this.cellZ + 1) * CELL;
            case N -> this.cellZ * CELL;
            default -> this.cellZ * CELL + 8;
         };
      }
   }

   /** Kinds of controlled anomalies that move a player (the navigation stays correct, see PocketAnomalies). */
   public enum PortalType {
      /** The stair that climbs out of a void hall and comes back in through its own door. */
      LOOP_STAIR,
      /** An apartment door that opens into another apartment. */
      WRONG_DOOR,
      /** A corridor doorway that sometimes leads into another identical doorway. */
      SHIFTING,
      /** A corridor that runs off the edge of the instance and comes back from the opposite edge. */
      WRAP
   }

   /** Entering box [x0..x1) x [y0..y1) x [z0..z1) moves the player by (dx, dy, dz) with the given chance per entry. */
   public record Portal(int id, PortalType type, int x0, int y0, int z0, int x1, int y1, int z1, int dx, int dy, int dz, float chance, int room) {
      public boolean contains(double x, double y, double z) {
         return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1 && z >= this.z0 && z < this.z1;
      }
   }

   public final long seed;
   public final int slot;
   final Zone[] zones = new Zone[GRID * GRID];
   final int[] roomAt = new int[GRID * GRID];
   /** Per cell: bit (1 << side) when that cell wall has a doorway. */
   final byte[] doorMask = new byte[GRID * GRID];
   final List<Room> rooms = new ArrayList<>();
   final List<Door> doors = new ArrayList<>();
   final List<Portal> portals = new ArrayList<>();
   int arrival = -1;
   int breach = -1;
   int secret = -1;
   int home = -1;
   final int[] sigils = {-1, -1, -1};
   int domainCx;
   int domainCz;

   private PocketLayout(long seed, int slot) {
      this.seed = seed;
      this.slot = slot;
   }

   /** The instance seed of a slot: mixes the world-derived base with the slot index. */
   public static long instanceSeed(long base, int slot) {
      return mix(base ^ (0x9E3779B97F4A7C15L * (slot + 1)));
   }

   public static long mix(long z) {
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      return z ^ (z >>> 31);
   }

   /** Small cache: generation threads, the server and the client ask for the same few instances again and again. */
   private static final int CACHE = 8;
   private static final PocketLayout[] RECENT = new PocketLayout[CACHE];
   private static int recentNext;

   public static PocketLayout of(long seed, int slot) {
      synchronized (RECENT) {
         for (PocketLayout l : RECENT) {
            if (l != null && l.seed == seed && l.slot == slot) {
               return l;
            }
         }
      }
      PocketLayout made = generate(seed, slot);
      synchronized (RECENT) {
         RECENT[recentNext] = made;
         recentNext = (recentNext + 1) % CACHE;
      }
      return made;
   }

   public static PocketLayout generate(long seed, int slot) {
      PocketLayout l = new PocketLayout(seed, slot);
      l.build();
      return l;
   }

   // ------------------------------------------------------------------ queries

   public static int slotOfBlockX(double x) {
      return (int)Math.floor(x / SLOT_STRIDE);
   }

   public static int slotOriginX(int slot) {
      return slot * SLOT_STRIDE;
   }

   public static boolean insideGrid(int lx, int lz) {
      return lx >= 0 && lz >= 0 && lx < SIZE && lz < SIZE;
   }

   public List<Room> rooms() {
      return Collections.unmodifiableList(this.rooms);
   }

   public List<Door> doors() {
      return Collections.unmodifiableList(this.doors);
   }

   public List<Portal> portals() {
      return Collections.unmodifiableList(this.portals);
   }

   public Room room(int id) {
      return this.rooms.get(id);
   }

   /** Room at slot-local block coordinates, or null outside the grid. */
   public Room roomAtBlock(int lx, int lz) {
      if (!insideGrid(lx, lz)) {
         return null;
      }
      return this.rooms.get(this.roomAt[(lz >> 4) * GRID + (lx >> 4)]);
   }

   public Room roomAtCell(int cx, int cz) {
      return this.rooms.get(this.roomAt[cz * GRID + cx]);
   }

   public Zone zoneAtCell(int cx, int cz) {
      return this.zones[cz * GRID + cx];
   }

   public int doorMask(int cx, int cz) {
      return this.doorMask[cz * GRID + cx];
   }

   public Room arrival() {
      return this.rooms.get(this.arrival);
   }

   public Room breach() {
      return this.rooms.get(this.breach);
   }

   public Room secret() {
      return this.rooms.get(this.secret);
   }

   public Room home() {
      return this.rooms.get(this.home);
   }

   /**
    * Feet positions (slot-local) where the abyss drop lands: just inside the first doorway of every void hall, on its
    * ring walkway. Falling into an abyss never kills; the place simply puts the body somewhere else.
    */
   public List<int[]> abyssLandings() {
      List<int[]> out = new ArrayList<>();
      for (Room room : this.rooms) {
         if (room.kind == Kind.VOID && !room.doors.isEmpty()) {
            int[] in = PocketPainter.doorInside(room, room.doors.get(0));
            out.add(new int[]{in[0], in[1], in[2], room.id});
         }
      }
      if (out.isEmpty()) {
         int[] cp = PocketPainter.checkpoint(this.arrival());
         out.add(new int[]{cp[0], cp[1], cp[2], this.arrival});
      }
      return out;
   }

   /** Room of sigil n (1..3). */
   public Room sigil(int n) {
      return this.rooms.get(this.sigils[n - 1]);
   }

   // ------------------------------------------------------------------ generation

   private void build() {
      SplittableRandom r = new SplittableRandom(this.seed);
      Arrays.fill(this.roomAt, -1);
      this.zones(r);
      this.rooms(r);
      this.graph(r);
      this.purposes(r);
      this.anomalies(r);
   }

   /** Weighted Voronoi with hashed jitter: the arrival corner is decay, the opposite corner the domain. */
   private void zones(SplittableRandom r) {
      int corner = r.nextInt(4);
      int ax = (corner & 1) == 0 ? 3 + r.nextInt(3) : GRID - 4 - r.nextInt(3);
      int az = (corner & 2) == 0 ? 3 + r.nextInt(3) : GRID - 4 - r.nextInt(3);
      this.domainCx = (corner & 1) == 0 ? GRID - 7 - r.nextInt(3) : 4 + r.nextInt(3);
      this.domainCz = (corner & 2) == 0 ? GRID - 7 - r.nextInt(3) : 4 + r.nextInt(3);
      List<int[]> seeds = new ArrayList<>();
      seeds.add(new int[]{ax, az, Zone.DECAY.ordinal()});
      int[][] plan = {{Zone.DECAY.ordinal(), 2}, {Zone.VOID.ordinal(), 3}, {Zone.FLOODED.ordinal(), 2}, {Zone.FALSE_SAFE.ordinal(), 2}};
      for (int[] p : plan) {
         for (int i = 0; i < p[1]; i++) {
            int sx;
            int sz;
            int tries = 0;
            do {
               sx = 2 + r.nextInt(GRID - 4);
               sz = 2 + r.nextInt(GRID - 4);
               tries++;
            } while (tries < 40 && (dist2(sx, sz, this.domainCx, this.domainCz) < 81 || tooClose(seeds, sx, sz)));
            seeds.add(new int[]{sx, sz, p[0]});
         }
      }
      long jitter = r.nextLong();
      for (int z = 0; z < GRID; z++) {
         for (int x = 0; x < GRID; x++) {
            Zone zone;
            if (dist2(x, z, this.domainCx + 1.5, this.domainCz + 1.5) <= 36.0) {
               zone = Zone.DOMAIN;
            } else {
               double best = Double.MAX_VALUE;
               int pick = Zone.DECAY.ordinal();
               for (int[] s : seeds) {
                  double d = dist2(x, z, s[0], s[1]) * (0.75 + 0.5 * unit(jitter, x * 31 + s[0], z * 17 + s[1]));
                  if (d < best) {
                     best = d;
                     pick = s[2];
                  }
               }
               zone = Zone.byId(pick);
            }
            this.zones[z * GRID + x] = zone;
         }
      }
   }

   private static boolean tooClose(List<int[]> seeds, int x, int z) {
      for (int[] s : seeds) {
         if (dist2(x, z, s[0], s[1]) < 25) {
            return true;
         }
      }
      return false;
   }

   private static double dist2(double ax, double az, double bx, double bz) {
      return (ax - bx) * (ax - bx) + (az - bz) * (az - bz);
   }

   /** Hash to [0, 1). */
   static double unit(long seed, int a, int b) {
      return (mix(seed ^ (a * 0x632BE59BD9B4E019L) ^ (b * 0x8CB92BA72F3D8DD7L)) >>> 11) * 0x1.0p-53;
   }

   private Room add(int cx, int cz, int w, int h, Kind kind, Zone zone, SplittableRandom r) {
      Room room = new Room(this.rooms.size(), cx, cz, w, h, kind, zone, r.nextLong());
      this.rooms.add(room);
      for (int z = cz; z < cz + h; z++) {
         for (int x = cx; x < cx + w; x++) {
            this.roomAt[z * GRID + x] = room.id;
         }
      }
      return room;
   }

   private boolean free(int cx, int cz, int w, int h, Zone zone) {
      if (cx < 0 || cz < 0 || cx + w > GRID || cz + h > GRID) {
         return false;
      }
      for (int z = cz; z < cz + h; z++) {
         for (int x = cx; x < cx + w; x++) {
            if (this.roomAt[z * GRID + x] >= 0 || this.zones[z * GRID + x] != zone) {
               return false;
            }
         }
      }
      return true;
   }

   /** Big rooms first (throne, voids, flooded halls, apartments, decay halls), single cells fill the rest. */
   private void rooms(SplittableRandom r) {
      int tx = Math.max(0, Math.min(GRID - 4, this.domainCx));
      int tz = Math.max(0, Math.min(GRID - 4, this.domainCz));
      for (int z = tz; z < tz + 4; z++) {
         for (int x = tx; x < tx + 4; x++) {
            this.zones[z * GRID + x] = Zone.DOMAIN;
         }
      }
      this.add(tx, tz, 4, 4, Kind.THRONE, Zone.DOMAIN, r);
      int[][] voidSizes = {{5, 5}, {5, 4}, {4, 5}, {4, 4}, {4, 3}, {3, 4}, {3, 3}};
      this.place(r, Zone.VOID, Kind.VOID, voidSizes, 260);
      this.place(r, Zone.FLOODED, Kind.FLOODED, new int[][]{{3, 2}, {2, 3}, {2, 2}}, 160);
      this.place(r, Zone.FALSE_SAFE, Kind.STREET, new int[][]{{3, 2}, {2, 3}}, 6);
      this.place(r, Zone.FALSE_SAFE, Kind.APARTMENT, new int[][]{{2, 1}, {1, 2}, {2, 2}}, 120);
      this.place(r, Zone.DECAY, Kind.HALL, new int[][]{{2, 2}, {3, 2}, {2, 3}}, 10);
      for (int z = 0; z < GRID; z++) {
         for (int x = 0; x < GRID; x++) {
            if (this.roomAt[z * GRID + x] >= 0) {
               continue;
            }
            Zone zone = this.zones[z * GRID + x];
            Kind kind = switch (zone) {
               case FLOODED -> Kind.FLOODED_CORRIDOR;
               case FALSE_SAFE -> Kind.APARTMENT;
               case VOID -> r.nextInt(5) == 0 ? Kind.SHAFT : Kind.CORRIDOR;
               case DECAY -> r.nextInt(14) == 0 ? Kind.SHAFT : Kind.CORRIDOR;
               default -> Kind.CORRIDOR;
            };
            this.add(x, z, 1, 1, kind, zone, r);
         }
      }
   }

   private void place(SplittableRandom r, Zone zone, Kind kind, int[][] sizes, int tries) {
      for (int t = 0; t < tries; t++) {
         int[] s = sizes[r.nextInt(sizes.length)];
         int cx = r.nextInt(GRID - s[0] + 1);
         int cz = r.nextInt(GRID - s[1] + 1);
         if (this.free(cx, cz, s[0], s[1], zone)) {
            this.add(cx, cz, s[0], s[1], kind, zone, r);
         }
      }
   }

   private record Edge(int cellX, int cellZ, int side, int a, int b, double weight) {
      int other(int room) {
         return room == this.a ? this.b : this.a;
      }
   }

   /** Kruskal over every shared cell wall (zone crossings cost more), then loops, then dead ends get loops or purposes. */
   private void graph(SplittableRandom r) {
      List<Edge> candidates = new ArrayList<>();
      // one candidate per room pair: the shared wall cell is picked at random among all of them
      int n = this.rooms.size();
      List<List<int[]>> walls = new ArrayList<>();
      java.util.Map<Long, Integer> pairs = new java.util.LinkedHashMap<>();
      for (int z = 0; z < GRID; z++) {
         for (int x = 0; x < GRID; x++) {
            int a = this.roomAt[z * GRID + x];
            for (int side : new int[]{E, S}) {
               int nx = x + DX[side];
               int nz = z + DZ[side];
               if (nx >= GRID || nz >= GRID) {
                  continue;
               }
               int b = this.roomAt[nz * GRID + nx];
               if (a == b) {
                  continue;
               }
               long key = Math.min(a, b) * 100000L + Math.max(a, b);
               Integer idx = pairs.get(key);
               if (idx == null) {
                  idx = walls.size();
                  pairs.put(key, idx);
                  walls.add(new ArrayList<>());
               }
               walls.get(idx).add(new int[]{x, z, side, a, b});
            }
         }
      }
      for (List<int[]> w : walls) {
         int[] pick = w.get(r.nextInt(w.size()));
         Room ra = this.rooms.get(pick[3]);
         Room rb = this.rooms.get(pick[4]);
         double weight = r.nextDouble();
         if (ra.zone != rb.zone) {
            weight += 4.0;
         }
         if (ra.kind == Kind.THRONE || rb.kind == Kind.THRONE) {
            weight += 1.5;
         }
         candidates.add(new Edge(pick[0], pick[1], pick[2], pick[3], pick[4], weight));
      }
      candidates.sort((p, q) -> Double.compare(p.weight, q.weight));
      int[] parent = new int[n];
      for (int i = 0; i < n; i++) {
         parent[i] = i;
      }
      List<Edge> rest = new ArrayList<>();
      for (Edge e : candidates) {
         int pa = find(parent, e.a);
         int pb = find(parent, e.b);
         if (pa != pb) {
            parent[pa] = pb;
            this.connect(e);
         } else {
            rest.add(e);
         }
      }
      for (Edge e : rest) {
         Room ra = this.rooms.get(e.a);
         Room rb = this.rooms.get(e.b);
         double p = ra.zone == rb.zone ? (ra.zone == Zone.DECAY ? 0.30 : 0.20) : 0.035;
         if (r.nextDouble() < p) {
            this.connect(e);
         }
      }
      // dead ends of plain corridors: open them into a neighbour when possible
      for (Room room : this.rooms) {
         if (room.degree() != 1 || room.kind != Kind.CORRIDOR && room.kind != Kind.FLOODED_CORRIDOR || r.nextInt(10) >= 7) {
            continue;
         }
         Edge best = null;
         for (Edge e : rest) {
            if ((e.a == room.id || e.b == room.id) && !this.connected(e.a, e.b)) {
               Room other = this.rooms.get(e.other(room.id));
               if (best == null || other.zone == room.zone && this.rooms.get(best.other(room.id)).zone != room.zone) {
                  best = e;
               }
            }
         }
         if (best != null) {
            this.connect(best);
         }
      }
   }

   private boolean connected(int a, int b) {
      for (Door d : this.rooms.get(a).doors) {
         if (d.other(a) == b) {
            return true;
         }
      }
      return false;
   }

   private static int find(int[] p, int i) {
      while (p[i] != i) {
         p[i] = p[p[i]];
         i = p[i];
      }
      return i;
   }

   private void connect(Edge e) {
      Door d = new Door(e.cellX, e.cellZ, e.side, e.a, e.b);
      this.doors.add(d);
      this.rooms.get(e.a).doors.add(d);
      this.rooms.get(e.b).doors.add(d);
      this.doorMask[e.cellZ * GRID + e.cellX] |= (byte)(1 << e.side);
      int nx = e.cellX + DX[e.side];
      int nz = e.cellZ + DZ[e.side];
      this.doorMask[nz * GRID + nx] |= (byte)(1 << ((e.side + 2) & 3));
   }

   /** BFS distances from a room over the doors. */
   public int[] distances(int from) {
      int[] dist = new int[this.rooms.size()];
      Arrays.fill(dist, -1);
      ArrayDeque<Integer> q = new ArrayDeque<>();
      dist[from] = 0;
      q.add(from);
      while (!q.isEmpty()) {
         int cur = q.poll();
         for (Door d : this.rooms.get(cur).doors) {
            int o = d.other(cur);
            if (dist[o] < 0) {
               dist[o] = dist[cur] + 1;
               q.add(o);
            }
         }
      }
      return dist;
   }

   /** Arrival, the breach, the secret passage, sigils and the home door, notes in the remaining dead ends. */
   private void purposes(SplittableRandom r) {
      Room throne = null;
      for (Room room : this.rooms) {
         if (room.kind == Kind.THRONE) {
            throne = room;
         }
      }
      this.breach = throne.id;
      throne.purpose = Purpose.BREACH;
      // arrival: the quietest decay corridor, farthest from the throne by route
      int[] fromThrone = this.distances(throne.id);
      Room arrive = null;
      for (Room room : this.rooms) {
         if (room.zone == Zone.DECAY && (room.kind == Kind.CORRIDOR || room.kind == Kind.HALL)
            && (arrive == null || fromThrone[room.id] > fromThrone[arrive.id])) {
            arrive = room;
         }
      }
      if (arrive == null) {
         arrive = this.rooms.get(this.rooms.size() - 1);
      }
      this.arrival = arrive.id;
      arrive.purpose = Purpose.ARRIVAL;
      arrive.note = 0;
      int[] depth = this.distances(arrive.id);
      int max = 0;
      for (Room room : this.rooms) {
         room.depth = depth[room.id];
         max = Math.max(max, room.depth);
         int d = fromThrone[room.id];
         room.corrosion = room.zone == Zone.DOMAIN ? 3 : d <= 3 ? 2 : d <= 8 ? 1 : 0;
      }
      // secret passage: a plain corridor half way, with a wall side free of doors and inside the grid
      List<Room> secretCandidates = new ArrayList<>();
      for (Room room : this.rooms) {
         if (room.kind == Kind.CORRIDOR && room.zone != Zone.DOMAIN && room.purpose == Purpose.NONE && room.depth >= max * 0.35
            && room.depth <= max * 0.75 && this.freeSide(room) >= 0) {
            secretCandidates.add(room);
         }
      }
      if (secretCandidates.isEmpty()) {
         for (Room room : this.rooms) {
            if (room.kind == Kind.CORRIDOR && room.purpose == Purpose.NONE && this.freeSide(room) >= 0) {
               secretCandidates.add(room);
            }
         }
      }
      Room secretRoom = secretCandidates.get(r.nextInt(secretCandidates.size()));
      secretRoom.purpose = Purpose.SECRET_EXIT;
      secretRoom.secretSide = this.freeSide(secretRoom);
      this.secret = secretRoom.id;
      // sigils: I where there is no floor (a void), II in the water, III in a home; the home door in another home
      Kind[] sigilKinds = {Kind.VOID, Kind.FLOODED, Kind.APARTMENT};
      for (int i = 0; i < 3; i++) {
         Room pick = this.pickFar(r, sigilKinds[i], max);
         if (pick == null) {
            pick = this.pickFar(r, Kind.CORRIDOR, max);
         }
         pick.purpose = Purpose.SIGIL;
         pick.sigil = i + 1;
         this.sigils[i] = pick.id;
      }
      Room homeRoom = this.pickFar(r, Kind.APARTMENT, max);
      if (homeRoom == null) {
         homeRoom = this.pickFar(r, Kind.STREET, max);
      }
      if (homeRoom == null) {
         homeRoom = this.pickFar(r, Kind.CORRIDOR, max);
      }
      homeRoom.purpose = Purpose.HOME_DOOR;
      if (homeRoom.kind == Kind.CORRIDOR) {
         homeRoom.secretSide = this.freeSide(homeRoom);
      }
      this.home = homeRoom.id;
      // every remaining dead end gets a reason to be there
      int note = 1;
      for (Room room : this.rooms) {
         if (room.purpose != Purpose.NONE || room.degree() > 1) {
            continue;
         }
         if (room.kind == Kind.APARTMENT && r.nextInt(3) == 0) {
            room.purpose = Purpose.LISTENING;
         } else if (room.kind == Kind.VOID || room.kind == Kind.SHAFT) {
            room.purpose = Purpose.VANTAGE;
         } else {
            room.purpose = Purpose.NOTE;
            room.note = note;
            note = note % (NOTE_COUNT - 1) + 1;
         }
      }
      // a few notes also in rooms on the way, so the rules of the place can be learned
      for (Room room : this.rooms) {
         if (room.purpose == Purpose.NONE && (room.kind == Kind.CORRIDOR || room.kind == Kind.APARTMENT) && r.nextInt(16) == 0) {
            room.purpose = Purpose.NOTE;
            room.note = note;
            note = note % (NOTE_COUNT - 1) + 1;
         }
      }
      for (Room room : this.rooms) {
         if (room.purpose == Purpose.NONE && room.kind == Kind.APARTMENT && r.nextInt(6) == 0) {
            room.purpose = Purpose.LISTENING;
         }
      }
   }

   private Room pickFar(SplittableRandom r, Kind kind, int max) {
      List<Room> c = new ArrayList<>();
      for (Room room : this.rooms) {
         if (room.kind == kind && room.purpose == Purpose.NONE && room.depth >= max * 0.3) {
            c.add(room);
         }
      }
      if (c.isEmpty()) {
         for (Room room : this.rooms) {
            if (room.kind == kind && room.purpose == Purpose.NONE) {
               c.add(room);
            }
         }
      }
      return c.isEmpty() ? null : c.get(r.nextInt(c.size()));
   }

   /** A side of a 1 x 1 room with no doorway whose wall stays inside the grid, or -1. */
   int freeSide(Room room) {
      int mask = this.doorMask(room.cx, room.cz);
      for (int side = 0; side < 4; side++) {
         int nx = room.cx + DX[side];
         int nz = room.cz + DZ[side];
         if ((mask & 1 << side) == 0 && nx >= 0 && nz >= 0 && nx < GRID && nz < GRID) {
            return side;
         }
      }
      return -1;
   }

   private void anomalies(SplittableRandom r) {
      int floor = FLOOR + 1;
      for (Room room : this.rooms) {
         if (room.kind == Kind.VOID && room.purpose != Purpose.SIGIL && !room.doors.isEmpty() && PocketPainter.loopStairSide(room) >= 0
            && r.nextInt(10) < 6) {
            room.loopStair = true;
            // the landing doorway on the north wall leads back to the room's first doorway, as if entering anew
            int[] top = PocketPainter.loopStairLanding(room);
            Door d = room.doors.get(0);
            int[] in = PocketPainter.doorInside(room, d);
            this.portals.add(new Portal(this.portals.size(), PortalType.LOOP_STAIR, top[0], top[1], top[2], top[0] + 3, top[1] + 3, top[2] + 1,
               in[0] - top[0], floor - top[1], in[2] - top[2], 1.0F, room.id));
         }
      }
      // wrong doors: closets on the north partition of apartments are paired with each other
      List<Room> closets = new ArrayList<>();
      for (Room room : this.rooms) {
         if (room.kind == Kind.APARTMENT && room.purpose != Purpose.HOME_DOOR && room.purpose != Purpose.SIGIL && r.nextInt(10) < 4) {
            closets.add(room);
         }
      }
      // a wrong door is a rare event: at most three pairs, picked anywhere in the false safe zones
      while (closets.size() > 6) {
         closets.remove(r.nextInt(closets.size()));
      }
      for (int i = 0; i + 1 < closets.size(); i += 2) {
         Room a = closets.get(i);
         Room b = closets.get(i + 1);
         a.wrongDoor = true;
         b.wrongDoor = true;
         int[] ca = PocketPainter.closet(a);
         int[] cb = PocketPainter.closet(b);
         this.portals.add(new Portal(this.portals.size(), PortalType.WRONG_DOOR, ca[0], floor, ca[2], ca[0] + 1, floor + 2, ca[2] + 1,
            cb[0] - ca[0], 0, cb[2] - ca[2] + 2, 1.0F, a.id));
         this.portals.add(new Portal(this.portals.size(), PortalType.WRONG_DOOR, cb[0], floor, cb[2], cb[0] + 1, floor + 2, cb[2] + 1,
            ca[0] - cb[0], 0, ca[2] - cb[2] + 2, 1.0F, b.id));
      }
      // shifting doorways: straight corridor cells (doors on two opposite sides) on the same axis swap travellers
      List<Room> straights = new ArrayList<>();
      for (Room room : this.rooms) {
         if (room.kind == Kind.CORRIDOR && room.purpose == Purpose.NONE && this.doorMask(room.cx, room.cz) == (1 << N | 1 << S) && r.nextInt(4) == 0) {
            straights.add(room);
         }
      }
      for (int i = 0; i + 1 < straights.size() && i < 8; i += 2) {
         Room a = straights.get(i);
         Room b = straights.get(i + 1);
         int ax = a.minX() + 7;
         int az = a.minZ() + 7;
         int bx = b.minX() + 7;
         int bz = b.minZ() + 7;
         this.portals.add(new Portal(this.portals.size(), PortalType.SHIFTING, ax, floor, az, ax + 3, floor + 3, az + 2, bx - ax, 0, bz - az, 0.35F, a.id));
         this.portals.add(new Portal(this.portals.size(), PortalType.SHIFTING, bx, floor, bz, bx + 3, floor + 3, bz + 2, ax - bx, 0, az - bz, 0.35F, b.id));
      }
      // wrap: corridor cells on opposite edges of the same column/row continue into each other
      List<Integer> wrapColumns = new ArrayList<>();
      for (int x = 1; x < GRID - 1; x++) {
         Room top = this.roomAtCell(x, 0);
         Room bottom = this.roomAtCell(x, GRID - 1);
         if (top.kind == Kind.CORRIDOR && bottom.kind == Kind.CORRIDOR && top.purpose == Purpose.NONE && bottom.purpose == Purpose.NONE
            && top.w == 1 && bottom.w == 1 && top.degree() > 0 && bottom.degree() > 0) {
            wrapColumns.add(x);
         }
      }
      // one or two edges of the instance quietly continue into the opposite edge
      int wrapsWanted = Math.min(wrapColumns.size(), 1 + r.nextInt(2));
      for (int i = 0; i < wrapsWanted; i++) {
         int x = wrapColumns.remove(r.nextInt(wrapColumns.size()));
         this.wraps(this.roomAtCell(x, 0), this.roomAtCell(x, GRID - 1), true);
      }
   }

   /** Marks the two edge cells as wrap ends; the painter extends their tubes to the instance edge. */
   private void wraps(Room top, Room bottom, boolean vertical) {
      this.doorMask[top.cz * GRID + top.cx] |= (byte)(1 << 4);
      this.doorMask[bottom.cz * GRID + bottom.cx] |= (byte)(1 << 4);
      int floor = FLOOR + 1;
      int tx = top.minX() + 7;
      int bx = bottom.minX() + 7;
      // stepping into the last two rows of a wrap tube moves the traveller to the matching rows on the other side
      this.portals.add(new Portal(this.portals.size(), PortalType.WRAP, tx, floor, 0, tx + 3, floor + 4, 1, bx - tx, 0, SIZE - 3, 1.0F, top.id));
      this.portals.add(new Portal(this.portals.size(), PortalType.WRAP, bx, floor, SIZE - 1, bx + 3, floor + 4, SIZE, tx - bx, 0, -(SIZE - 3), 1.0F, bottom.id));
   }

   /** Wrap flag of a cell (the tube runs to the instance edge on its N or S side). */
   public boolean wrapCell(int cx, int cz) {
      return (this.doorMask[cz * GRID + cx] & 1 << 4) != 0;
   }

   public int domainCenterX() {
      return this.breach().minX() + this.breach().sizeX() / 2;
   }

   public int domainCenterZ() {
      return this.breach().minZ() + this.breach().sizeZ() / 2;
   }
}
