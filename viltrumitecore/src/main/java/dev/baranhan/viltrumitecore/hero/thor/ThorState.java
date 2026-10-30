package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side per-player Thor state. Saved under NBT key {@code Thor}: only
 * where Mjölnir is, as last projected. The world hammer store
 * (hero/thor/hammer/MjolnirWorld, SavedData) is the one truth and the only
 * writer of {@link #hammer} / {@link #hammerDetached} between the world and
 * Thor (only the technical DRAW / HOLSTER swap belt and hand). The mode and the running move are transient.
 */
public final class ThorState {
   static final String KEY = "Thor";
   static final String HAMMER_KEY = "Hammer";

   private HammerState hammer = HammerState.HOLSTERED;
   /** Projected by the world store: the hammer lies / falls in the world away from Thor (ThorFlags bit 5). Transient. */
   private boolean hammerDetached;
   /** Transient; written by its owner (session B's mode toggle) through {@link #setMode}. */
   private ThorMode mode = ThorMode.NORMAL;
   /** The one staged move player; transient. */
   public final StagedTimeline<ThorMove> timeline = new StagedTimeline<>();
   /** Bridge grab window, ticks left (ThorBridge only); transient. */
   private int grabWindow;
   /** Bridge grab window target entity id, -1 when none (ThorBridge only); transient. */
   private int grabTarget = -1;
   /** Bridge scene lock owner, null when free (ThorBridge only); transient. */
   @Nullable
   private String sceneLock;
   /** Bridge camera lease owner, null when free (ThorBridge only); transient. */
   @Nullable
   private String cameraLease;
   /** Combo window (session B, ThorBridge only): the chain's move, null when closed; transient. */
   @Nullable
   private ThorMove comboMove;
   /** Combo window ticks left; transient. */
   private int comboWindow;
   /** Session-owned strike pose id of the running move (0 = default clip), synced as snapshot resource; transient. */
   private int combatPose;
   /** Move chosen on the press of a held input, with ticks held; the release goes to it. Transient. */
   final EnumMap<ThorInput, ThorMove> pressed = new EnumMap<>(ThorInput.class);
   final EnumMap<ThorInput, Integer> heldTicks = new EnumMap<>(ThorInput.class);
   /**
    * The move the flow last handed to a handler (ThorActions): a tick that finds
    * the timeline on another move knows the remembered one was stopped outside
    * the lifecycle API and still owes it onStop. Transient.
    */
   @Nullable
   ThorMove lastMove;

   // --- reads (everyone) ---

   public HammerState hammer() {
      return this.hammer;
   }

   public boolean hammerDetached() {
      return this.hammerDetached;
   }

   public ThorMode mode() {
      return this.mode;
   }

   public int grabWindowTicks() {
      return this.grabWindow;
   }

   public int grabTarget() {
      return this.grabTarget;
   }

   @Nullable
   public String sceneLock() {
      return this.sceneLock;
   }

   @Nullable
   public String cameraLease() {
      return this.cameraLease;
   }

   @Nullable
   public ThorMove comboMove() {
      return this.comboMove;
   }

   public int comboWindow() {
      return this.comboWindow;
   }

   public int combatPose() {
      return this.combatPose;
   }

   // --- owner writes ---

   /** The mode toggle (session B owns the mode; the foundation resets it on entry). */
   public void setMode(ThorMode mode) {
      this.mode = mode == null ? ThorMode.NORMAL : mode;
   }

   /** The running move's strike pose (the session owning the running move). */
   public void setCombatPose(int pose) {
      this.combatPose = pose;
   }

   /** The foundation's technical DRAW / HOLSTER swap of belt and hand (ThorActions). */
   void projectTechnical(HammerState hammer) {
      this.hammer = hammer;
   }

   /** Foundation tick: the bridge windows count down (ThorActions). */
   void tickWindows() {
      if (this.grabWindow > 0 && --this.grabWindow == 0) {
         this.grabTarget = -1;
      }

      if (this.comboWindow > 0 && --this.comboWindow == 0) {
         this.comboMove = null;
      }
   }

   /**
    * The one writer of where Mjölnir is (the world hammer store, session A).
    * Claimed once per JVM; a second claim fails, so no other session can
    * hold a writer (review foundation m1 / A m6).
    */
   public static final class HammerWriter {
      private HammerWriter() {
      }

      public void project(ThorState state, HammerState hammer, boolean detached) {
         state.hammer = hammer;
         state.hammerDetached = detached;
      }
   }

   /**
    * The one writer of the bridge flags (scene lock, camera lease, grab and
    * combo windows): ThorBridge, whose owner checks guard every write.
    */
   public static final class BridgeWriter {
      private BridgeWriter() {
      }

      public void grabWindow(ThorState state, int ticks, int target) {
         state.grabWindow = ticks;
         state.grabTarget = target;
      }

      public void sceneLock(ThorState state, @Nullable String owner) {
         state.sceneLock = owner;
      }

      public void cameraLease(ThorState state, @Nullable String owner) {
         state.cameraLease = owner;
      }

      public void comboWindow(ThorState state, @Nullable ThorMove move, int ticks) {
         state.comboMove = move;
         state.comboWindow = ticks;
      }
   }

   @Nullable
   private static String hammerWriterOwner;
   @Nullable
   private static String bridgeWriterOwner;

   public static synchronized HammerWriter claimHammerWriter(String owner) {
      hammerWriterOwner = claim(hammerWriterOwner, owner, "hammer");
      return new HammerWriter();
   }

   public static synchronized BridgeWriter claimBridgeWriter(String owner) {
      bridgeWriterOwner = claim(bridgeWriterOwner, owner, "bridge");
      return new BridgeWriter();
   }

   private static String claim(@Nullable String held, String owner, String what) {
      if (owner == null || owner.isBlank()) {
         throw new IllegalArgumentException("owner is required");
      }

      if (held != null) {
         throw new IllegalStateException("the Thor " + what + " writer is already held by " + held);
      }

      return owner;
   }

   /** Tests only (same package): put Mjölnir somewhere without the world store. */
   void projectForTests(HammerState hammer, boolean detached) {
      this.hammer = hammer;
      this.hammerDetached = detached;
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putString(HAMMER_KEY, this.hammer.key());
      nbt.put(KEY, tag);
   }

   /**
    * The saved projection is kept as it was (a hammer saved in flight is not
    * put back into the hand): the world store corrects it on the first tick.
    * The mode starts normal.
    */
   public void load(CompoundTag nbt) {
      this.timeline.clear();
      this.lastMove = null;
      this.mode = ThorMode.NORMAL;
      this.clearTransient();
      if (!nbt.contains(KEY, 10)) {
         return;
      }

      this.hammer = HammerState.fromKey(nbt.getCompound(KEY).getString(HAMMER_KEY));
   }

   /** Audit v3: server hit-stop, ticks the running move's timeline holds on its contact frame (never saved). */
   int hitStop;

   /** Hold the running move for {@code ticks} (the longer of a running and a new hold). */
   public void holdTimeline(int ticks) {
      this.hitStop = Math.max(this.hitStop, Math.max(0, Math.min(10, ticks)));
   }

   public int hitStop() {
      return this.hitStop;
   }

   /** Drop the bridge flags and held presses (never saved). */
   void clearTransient() {
      this.hitStop = 0;
      this.grabWindow = 0;
      this.grabTarget = -1;
      this.sceneLock = null;
      this.cameraLease = null;
      this.comboMove = null;
      this.comboWindow = 0;
      this.combatPose = 0;
      this.pressed.clear();
      this.heldTicks.clear();
   }

   /**
    * A fresh body: normal mode, nothing running, no bridge flags; the hammer
    * projection (where Mjölnir is, attached or detached) is carried over as
    * the last projection: the world store (session A) re-projects it on the
    * first tick, and a carried IN_HAND never strands Mjölnir on the belt.
    */
   public ThorState respawnCopy() {
      ThorState copy = new ThorState();
      copy.hammer = this.hammer;
      copy.hammerDetached = this.hammerDetached;
      return copy;
   }

   /** Same-entity death keeps nothing of the body either. */
   public ThorState erasedCopy() {
      return this.respawnCopy();
   }

   @Nullable
   public static ThorState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.THOR) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof ThorState state ? state : null;
      }

      return null;
   }

   public static ThorState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof ThorState existing) {
         return existing;
      }

      ThorState state = new ThorState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
