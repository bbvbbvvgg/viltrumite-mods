package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One running uprising: the seeded scenario, the network, what the machines learned, the live bodies and
 * the clocks. Everything that matters after a restart goes through {@link #save()} / {@link #load}; the
 * bodies are not saved (they are rebuilt in ticking chunks, machines from the reserve — builds in the
 * foundries and machines waiting at the pads go there too).
 */
public final class UltronSession {
   /** One build in a foundry. */
   static final class Fab {
      final UltronUnitKind kind;
      int left;
      final int total;
      final boolean reserve;

      Fab(UltronUnitKind kind, int total, boolean reserve) {
         this.kind = kind;
         this.left = total;
         this.total = total;
         this.reserve = reserve;
      }
   }

   /** A player's hack of a node core. */
   static final class Hack {
      final int node;
      int progress;
      int last;

      Hack(int node, int now) {
         this.node = node;
         this.last = now;
      }
   }

   final MinecraftServer server;
   final ResourceKey<Level> dimension;
   final long seed;
   final UltronScenario scenario;
   final Vec3 anchor;
   final double radius;
   final boolean inCity;
   final UltronNetwork network = new UltronNetwork();
   final UltronAdaptation adaptation;
   final UltronIntel intel = new UltronIntel();
   final UltronCapture capture = new UltronCapture();
   UltronPhase phase = UltronPhase.AWAKENING;
   int phaseTick;
   int clock;
   UltronDoctrine doctrine = UltronDoctrine.EXPANSION;
   int doctrineHeld;
   int styleTurn;
   int builtTotal;
   int builtPhase;
   /** Machines lost to an unloaded chunk or a restart: redeployed by the foundries without spending the budget. */
   final int[] reserve = new int[UltronUnitKind.values().length];
   final Map<Integer, Fab> fabs = new HashMap<>();
   final Map<UUID, UltronUnit> units = new LinkedHashMap<>();
   int nextNetId = 1;
   final ArrayDeque<UltronDirector.Spawn> queue = new ArrayDeque<>();
   final Map<UUID, Hack> hacks = new HashMap<>();
   /** Ultron Prime: his body, the share of health he keeps over a restart, his stage, the upload. */
   @Nullable
   UUID boss;
   float bossShare = 1.0F;
   int bossStage;
   float upload;
   int assemblyNode = -1;
   @Nullable
   Vec3 assembly;
   int bossMissing;
   int summonClock;
   /** The upload runs (from his first stand on); uplinks carry it. */
   boolean uploading;
   /** Node that last held an attack (FORTIFY covers it), relocation target site, pending re-route tick (-1: none). */
   int heldNode = -1;
   int relocationSite = -1;
   int relinkAt = -1;
   int lastLossSector = -1;
   /** Free golems / suits in the controllers' reach (role and doctrine input, counted every two seconds). */
   int captureTargets;
   /** Sensors online a second ago (-1: unknown) — the "frozen" line fires when the last one goes. */
   int sensorsSeen = -1;
   /** Nodes whose spot on the ground is settled. */
   final Set<Integer> placed = new HashSet<>();
   /** Players who got the HUD state (they get it switched off when they leave the range). */
   final Set<UUID> viewing = new HashSet<>();
   /** Last alert tick per node id. */
   final int[] alertAt = new int[dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket.MAX_NODES];
   @Nullable
   List<ServerPlayer> viewersCache;
   int viewersAt = -1;
   @Nullable
   ServerLevel viewersLevel;
   /** 0 running, 1 victory, 2 defeat; endTick counts the closing sequence. */
   int outcome;
   int endTick;
   final int[] killed = new int[UltronUnitKind.values().length];
   int nodesDestroyed;
   int nodesDisabled;
   float recentLosses;
   int emptyTicks;
   boolean dirty = true;
   boolean saveDirty = true;
   final Map<String, Integer> said = new HashMap<>();
   /** Ultron's queued lines and the queued line he reads now (not saved: news is stale after a restart). */
   final java.util.ArrayDeque<UltronVoice.Pending> voiceQueue = new java.util.ArrayDeque<>();
   String voiceQueued;
   int voiceQueuedAt;
   final ServerBossEvent bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
   @Nullable
   ServerBossEvent bossBar;

   UltronSession(MinecraftServer server, ResourceKey<Level> dimension, long seed, Vec3 anchor, double radius, boolean inCity) {
      this.server = server;
      this.dimension = dimension;
      this.seed = seed;
      this.scenario = UltronScenario.of(seed);
      this.anchor = anchor;
      this.radius = radius;
      this.inCity = inCity;
      this.adaptation = new UltronAdaptation(this.scenario.maxProtocols());
      this.bar.setDarkenScreen(false);
   }

   public UltronPhase phase() {
      return this.phase;
   }

   public int phaseTick() {
      return this.phaseTick;
   }

   public int clock() {
      return this.clock;
   }

   public long seed() {
      return this.seed;
   }

   public UltronScenario scenario() {
      return this.scenario;
   }

   public UltronNetwork network() {
      return this.network;
   }

   public UltronAdaptation adaptation() {
      return this.adaptation;
   }

   public UltronDoctrine doctrine() {
      return this.doctrine;
   }

   public Vec3 anchor() {
      return this.anchor;
   }

   public double radius() {
      return this.radius;
   }

   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public int units() {
      return this.units.size();
   }

   public int builtTotal() {
      return this.builtTotal;
   }

   public int outcome() {
      return this.outcome;
   }

   public float upload() {
      return this.upload;
   }

   /** The upload as a share 0..1 of the scenario's upload time ({@link #upload} counts ticks). */
   public float uploadShare() {
      return UltronRules.share(this.upload, this.scenario.uploadTicks());
   }

   boolean in(Level level) {
      return level.dimension() == this.dimension && level.getServer() == this.server;
   }

   int nextNetId() {
      int id = this.nextNetId++;
      if (this.nextNetId > 1_000_000) {
         this.nextNetId = 1;
      }
      return id;
   }

   // ------------------------------------------------------------------ persistence

   CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putInt("v", 1);
      t.putLong("seed", this.seed);
      t.putString("dim", this.dimension.location().toString());
      t.putDouble("ax", this.anchor.x);
      t.putDouble("ay", this.anchor.y);
      t.putDouble("az", this.anchor.z);
      t.putDouble("radius", this.radius);
      t.putBoolean("city", this.inCity);
      t.putInt("phase", this.phase.ordinal());
      t.putInt("phaseTick", this.phaseTick);
      t.putInt("clock", this.clock);
      t.putInt("doctrine", this.doctrine.ordinal());
      t.putInt("doctrineHeld", this.doctrineHeld);
      t.putInt("styleTurn", this.styleTurn);
      t.putInt("builtTotal", this.builtTotal);
      t.putInt("builtPhase", this.builtPhase);
      int[] bodies = new int[UltronUnitKind.values().length];
      for (UltronUnit u : this.units.values()) {
         net.minecraft.world.entity.Entity.RemovalReason reason = u.getRemovalReason();
         if (u.isRemoved() ? reason != null && UltronRules.backToReserve(reason.shouldDestroy(), this.outcome, u.getHealth()) : u.isAlive()) {
            bodies[u.kind().ordinal()]++;
         }
      }
      int[] pending = new int[UltronUnitKind.values().length];
      for (Fab f : this.fabs.values()) {
         pending[f.kind.ordinal()]++;
      }
      for (UltronDirector.Spawn sp : this.queue) {
         pending[sp.kind().ordinal()]++;
      }
      t.putIntArray("reserve", UltronRules.savedReserve(this.reserve, bodies, pending));
      t.putIntArray("killed", this.killed);
      t.putInt("nodesDestroyed", this.nodesDestroyed);
      t.putInt("nodesDisabled", this.nodesDisabled);
      ListTag nodes = new ListTag();
      for (UltronNetwork.Node n : this.network.all()) {
         CompoundTag nt = new CompoundTag();
         nt.putInt("kind", n.kind.ordinal());
         nt.putInt("site", n.site);
         nt.putDouble("x", n.x);
         nt.putDouble("y", n.y);
         nt.putDouble("z", n.z);
         nt.putInt("state", n.state.ordinal());
         nt.putFloat("hull", n.hull);
         nt.putFloat("shield", n.shield);
         nt.putFloat("build", n.build);
         nt.putBoolean("discovered", n.discovered);
         nt.putInt("fortify", n.fortifyTicks);
         nt.putInt("disabled", n.disabledTicks);
         nt.putInt("reboot", n.rebootTicks);
         nodes.add(nt);
      }
      t.put("nodes", nodes);
      CompoundTag ad = new CompoundTag();
      for (UltronAdaptation.Tactic tactic : UltronAdaptation.Tactic.values()) {
         ad.putFloat("p_" + tactic.name(), this.adaptation.pressure(tactic));
      }
      for (UltronAdaptation.Protocol p : UltronAdaptation.Protocol.values()) {
         ad.putIntArray("r_" + p.name(), new int[]{this.adaptation.ticksLeft(p), this.adaptation.cooldown(p), this.adaptation.level(p)});
      }
      ad.putInt("activations", this.adaptation.activations());
      ad.putInt("focus", this.adaptation.focusSector());
      ad.putInt("fortified", this.adaptation.fortifiedNode());
      t.put("adapt", ad);
      t.putFloat("bossShare", this.bossShare);
      t.putInt("bossStage", this.bossStage);
      t.putFloat("upload", this.upload);
      t.putInt("assemblyNode", this.assemblyNode);
      if (this.assembly != null) {
         t.putDouble("asx", this.assembly.x);
         t.putDouble("asy", this.assembly.y);
         t.putDouble("asz", this.assembly.z);
      }
      t.putBoolean("uploading", this.uploading);
      t.putInt("heldNode", this.heldNode);
      t.putInt("relocationSite", this.relocationSite);
      t.putIntArray("placed", this.placed.stream().mapToInt(Integer::intValue).toArray());
      t.putInt("outcome", this.outcome);
      t.putInt("endTick", this.endTick);
      return t;
   }

   @Nullable
   static UltronSession load(MinecraftServer server, CompoundTag t) {
      if (!t.contains("seed") || t.getInt("v") != 1) {
         return null;
      }
      ResourceLocation dimId = ResourceLocation.tryParse(t.getString("dim"));
      if (dimId == null) {
         return null;
      }
      ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
      if (server.getLevel(dim) == null) {
         return null;
      }
      UltronSession s = new UltronSession(server, dim, t.getLong("seed"), new Vec3(t.getDouble("ax"), t.getDouble("ay"), t.getDouble("az")),
         t.getDouble("radius"), t.getBoolean("city"));
      s.phase = UltronPhase.byId(t.getInt("phase"));
      s.phaseTick = t.getInt("phaseTick");
      s.clock = t.getInt("clock");
      s.doctrine = UltronDoctrine.byId(t.getInt("doctrine"));
      s.doctrineHeld = t.getInt("doctrineHeld");
      s.styleTurn = t.getInt("styleTurn");
      s.builtTotal = t.getInt("builtTotal");
      s.builtPhase = t.getInt("builtPhase");
      copy(t.getIntArray("reserve"), s.reserve);
      copy(t.getIntArray("killed"), s.killed);
      s.nodesDestroyed = t.getInt("nodesDestroyed");
      s.nodesDisabled = t.getInt("nodesDisabled");
      ListTag nodes = t.getList("nodes", Tag.TAG_COMPOUND);
      for (int i = 0; i < nodes.size(); i++) {
         CompoundTag nt = nodes.getCompound(i);
         UltronNetwork.Node n = s.network.add(UltronNodeKind.byId(nt.getInt("kind")), nt.getInt("site"), nt.getDouble("x"), nt.getDouble("y"), nt.getDouble("z"));
         n.state = UltronNodeState.byId(nt.getInt("state"));
         n.hull = nt.getFloat("hull");
         n.shield = nt.getFloat("shield");
         n.build = nt.getFloat("build");
         n.discovered = nt.getBoolean("discovered");
         n.fortifyTicks = nt.getInt("fortify");
         n.disabledTicks = nt.getInt("disabled");
         n.rebootTicks = nt.getInt("reboot");
      }
      CompoundTag ad = t.getCompound("adapt");
      for (UltronAdaptation.Tactic tactic : UltronAdaptation.Tactic.values()) {
         s.adaptation.setPressure(tactic, ad.getFloat("p_" + tactic.name()));
      }
      for (UltronAdaptation.Protocol p : UltronAdaptation.Protocol.values()) {
         int[] r = ad.getIntArray("r_" + p.name());
         if (r.length == 3) {
            s.adaptation.restore(p, r[0], r[1], r[2]);
         }
      }
      s.adaptation.setActivations(ad.getInt("activations"));
      s.adaptation.setFocusSector(ad.contains("focus") ? ad.getInt("focus") : -1);
      s.adaptation.setFortifiedNode(ad.contains("fortified") ? ad.getInt("fortified") : -1);
      s.bossShare = t.contains("bossShare") ? t.getFloat("bossShare") : 1.0F;
      s.bossStage = t.getInt("bossStage");
      s.upload = t.getFloat("upload");
      s.assemblyNode = t.contains("assemblyNode") ? t.getInt("assemblyNode") : -1;
      if (t.contains("asx")) {
         s.assembly = new Vec3(t.getDouble("asx"), t.getDouble("asy"), t.getDouble("asz"));
      }
      s.uploading = t.getBoolean("uploading");
      s.heldNode = t.contains("heldNode") ? t.getInt("heldNode") : -1;
      s.relocationSite = t.contains("relocationSite") ? t.getInt("relocationSite") : -1;
      for (int id : t.getIntArray("placed")) {
         s.placed.add(id);
      }
      s.outcome = t.getInt("outcome");
      s.endTick = t.getInt("endTick");
      s.network.relink();
      s.network.repower();
      return s;
   }

   private static void copy(int[] from, int[] to) {
      for (int i = 0; i < Math.min(from.length, to.length); i++) {
         to[i] = Math.max(0, from[i]);
      }
   }
}
