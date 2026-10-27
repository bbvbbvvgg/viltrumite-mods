package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Session B's transient per-player combat state, keyed by the foundation
 * ThorState (a new body gets a fresh one). Never saved: no world hammer, no
 * SavedData of B.
 */
public final class ThorCombatState {
   private static final Map<ThorState, ThorCombatState> STATES = new WeakHashMap<>();

   /** Cooldown keys (no mana, only cooldowns, #82). */
   public enum Cooldown {
      STORM,
      WRATH
   }

   /** A delayed server effect (expanding rings, the sky strike). */
   public record Scheduled(int dueTick, Runnable action) {
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
   public int electricStep;
   @Nullable
   public Vec3 wrathAim;

   // --- bookkeeping ---
   public int attackCounter;
   public int ticks;
   @Nullable
   public Vec3 lastPosition;
   public Vec3 velocity = Vec3.ZERO;
   public final EnumMap<Cooldown, Integer> cooldownUntil = new EnumMap<>(Cooldown.class);
   public final List<Scheduled> scheduled = new ArrayList<>();
   /** Where the sky lightning of a successful combo falls when no entity is chosen (by COMBO_END attackId). */
   public final Map<Integer, Vec3> skyFallback = new java.util.HashMap<>();
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

   public static synchronized void clearAll() {
      STATES.clear();
   }

   public int nextAttackId() {
      return ++this.attackCounter;
   }

   public boolean ready(Cooldown cooldown) {
      return this.cooldownLeft(cooldown) <= 0;
   }

   public int cooldownLeft(Cooldown cooldown) {
      return Math.max(0, this.cooldownUntil.getOrDefault(cooldown, 0) - this.ticks);
   }

   public void startCooldown(Cooldown cooldown, int ticks) {
      this.cooldownUntil.put(cooldown, this.ticks + Math.max(0, ticks));
   }

   public void schedule(int delay, Runnable action) {
      this.scheduled.add(new Scheduled(this.ticks + Math.max(0, delay), action));
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
