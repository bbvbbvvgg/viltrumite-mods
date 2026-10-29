package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.thor.ThorCooldowns;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Session B's transient per-player combat state, keyed by the foundation
 * ThorState (a new body gets a fresh one). Never saved: no world hammer, no
 * SavedData of B. The foundation lifecycle (ThorLifecycle) clears it on a
 * dimension change and drops it with the body (death, logout, hero change,
 * clone). Cooldowns are not here: they live in the shared
 * {@link PersistentCooldowns} (relog, hero change and death keep them).
 */
public final class ThorCombatState {
   private static final Map<ThorState, ThorCombatState> STATES = new WeakHashMap<>();

   /** Cooldown keys (no mana, only cooldowns, #82). */
   public enum Cooldown {
      STORM(ThorCooldowns.STORM),
      WRATH(ThorCooldowns.WRATH);

      private final String key;

      Cooldown(String key) {
         this.key = key;
      }

      /** PersistentCooldowns key (foundation-allocated, ThorCooldowns). */
      public String key() {
         return this.key;
      }
   }

   /**
    * A delayed server effect (expanding rings, the sky strike), bound to the
    * dimension it was scheduled in (null: any, pure tests). It runs only while
    * its Thor is still alive, online, in that dimension and not held by a
    * scene ({@link ThorCombatRuntime#runs}).
    */
   public record Scheduled(int dueTick, @Nullable ResourceKey<Level> dimension, Runnable action) {
   }

   // --- melee chain ---
   @Nullable
   public ThorMove chainMove;
   public ComboBook.Starter chainStarter = ComboBook.Starter.ANY;
   public final List<ComboBook.Button> chainButtons = new ArrayList<>();
   @Nullable
   public ComboBook.Route chainRoute;
   public boolean chainLast;
   public boolean chainCompleted;
   public boolean chainPosted = true;
   public int chainAttackId;
   /** Button of a buffered press (QUEUE), consumed by the CHAIN event. */
   @Nullable
   public ComboBook.Button pendingButton;
   @Nullable
   public ThorStrike strike;
   public int strikeAttackId;
   /** The swing inside its effective part (STRIKE_START posted, STRIKE_END owed); 0 when none. */
   public int openStrike;
   @Nullable
   public ThorMove openStrikeMove;
   /** First enemy hit by the chain's latest strike (COMBO_END targetId). */
   @Nullable
   public UUID chainTarget;
   /** Ticks (of {@link #ticks}) until an idle chain may still continue. */
   public int chainOpenUntil = Integer.MIN_VALUE;
   /** Tick on which a running chain was cut by another move (MMB finisher). */
   public int finisherTick = -1;
   /** Movement and context when the current step began. */
   public MoveDirection stepDirection = MoveDirection.NONE;
   public boolean stepAirborne;

   // --- electric ---
   public final ElectricRecognizer recognizer = new ElectricRecognizer();
   /** RMB pressed into ELECTRIC_WAVE and not yet released. */
   public boolean rmbHeld;
   /** Ticks of the running storm, -1 when none. */
   public int stormTicks = -1;
   public double stormRadius;
   /** Charge stage of the running storm (1..stormStages). */
   public int stormStage = 1;
   public int electricStep;
   @Nullable
   public Vec3 wrathAim;

   // --- bookkeeping ---
   public int attackCounter;
   public int ticks;
   @Nullable
   public Vec3 lastPosition;
   public Vec3 velocity = Vec3.ZERO;
   /** Cooldowns of a body without a player (pure tests only); a real Thor uses PersistentCooldowns. */
   private final CompoundTag localCooldowns = new CompoundTag();
   public final List<Scheduled> scheduled = new ArrayList<>();
   /** Where the sky lightning of a successful combo falls when no entity is chosen (by COMBO_END attackId). */
   public final Map<Integer, Vec3> skyFallback = new HashMap<>();
   /** Last hit ids of the current electric action (filled by the action, read by the recognizer). */
   public final List<UUID> actionPrimary = new ArrayList<>();
   public final List<UUID> actionAll = new ArrayList<>();

   public static synchronized ThorCombatState of(ThorState state) {
      return STATES.computeIfAbsent(state, s -> new ThorCombatState());
   }

   @Nullable
   public static synchronized ThorCombatState peek(ThorState state) {
      return STATES.get(state);
   }

   /** The body is gone (death, logout, hero change, clone): forget its combat state. */
   public static synchronized void forget(ThorState state) {
      STATES.remove(state);
   }

   public static synchronized void clearAll() {
      STATES.clear();
   }

   public int nextAttackId() {
      return ++this.attackCounter;
   }

   public boolean ready(@Nullable Player player, Cooldown cooldown) {
      return this.cooldownLeft(player, cooldown) <= 0;
   }

   /** Ticks left (online time, PersistentCooldowns); {@code player} null only in pure tests. */
   public int cooldownLeft(@Nullable Player player, Cooldown cooldown) {
      return player == null ? PersistentCooldowns.get(this.localCooldowns, cooldown.key()) : PersistentCooldowns.get(player, cooldown.key());
   }

   public void startCooldown(@Nullable ServerPlayer player, Cooldown cooldown, int ticks) {
      if (player == null) {
         PersistentCooldowns.set(this.localCooldowns, cooldown.key(), ticks);
      } else {
         PersistentCooldowns.set(player, cooldown.key(), ticks);
      }
   }

   /** Dev reset of every B cooldown. */
   public void clearCooldowns(@Nullable ServerPlayer player) {
      for (Cooldown cooldown : Cooldown.values()) {
         this.startCooldown(player, cooldown, 0);
      }
   }

   /** Pure tests: one tick of the player-less cooldowns (a real Thor is ticked by HeroEvents). */
   void tickLocalCooldowns() {
      PersistentCooldowns.tickTag(this.localCooldowns);
   }

   /** Schedule an effect bound to the player's current dimension. */
   public void schedule(int delay, ServerPlayer player, Runnable action) {
      this.scheduled.add(new Scheduled(this.ticks + Math.max(0, delay), player.level().dimension(), action));
   }

   /** Pure tests: an effect bound to no dimension. */
   void schedule(int delay, Runnable action) {
      this.scheduled.add(new Scheduled(this.ticks + Math.max(0, delay), null, action));
   }

   /**
    * Lifecycle stop (ThorLifecycle): every delayed effect, the sky fallbacks,
    * a running storm, the held RMB, the recognizer and the chain are dropped.
    * Cooldowns stay (PersistentCooldowns).
    */
   /** Target entity id → {attackId, game tick} of its last i-frame-skipping hit (B-M5 one-hit accounting). */
   private final Map<Integer, long[]> skipped = new HashMap<>();

   /**
    * B-M5: one swing skips a target's i-frames at most once per tick. A second
    * skip of the same target by the same attack id in the same tick (two
    * overlapping shapes of one strike, a re-entrant hit) is refused and falls
    * back to the vanilla i-frames, so nothing can multiply one swing's damage.
    * Different swings, and a later tick of the same swing (scheduled
    * follow-ups), are untouched. Entries of past ticks are pruned on the way.
    */
   public boolean claimSkip(int targetId, int attackId, long tick) {
      this.skipped.values().removeIf(entry -> entry[1] != tick);
      long[] last = this.skipped.get(targetId);
      if (last != null && last[0] == attackId) {
         return false;
      }

      this.skipped.put(targetId, new long[]{attackId, tick});
      return true;
   }

   public void cancelTransient() {
      this.skipped.clear();
      this.scheduled.clear();
      this.skyFallback.clear();
      this.stormTicks = -1;
      this.stormStage = 1;
      this.rmbHeld = false;
      this.recognizer.clear();
      this.resetChain();
      this.strike = null;
      this.finisherTick = -1;
      this.wrathAim = null;
      this.actionPrimary.clear();
      this.actionAll.clear();
   }

   /** Forget the melee chain (new chain on the next press). */
   public void resetChain() {
      this.chainMove = null;
      this.chainStarter = ComboBook.Starter.ANY;
      this.chainButtons.clear();
      this.chainRoute = null;
      this.chainLast = false;
      this.chainCompleted = false;
      this.chainPosted = true;
      this.pendingButton = null;
      this.chainTarget = null;
      this.chainOpenUntil = Integer.MIN_VALUE;
   }
}
