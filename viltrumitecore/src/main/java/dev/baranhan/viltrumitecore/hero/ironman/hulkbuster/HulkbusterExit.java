package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Climbing out of the Mark 48 (fix spec 2026-10-28 item 2: 2.5 s, no vanishing) and the empty shell after it.
 * <p>
 * The facing is frozen at the exit start ({@code HulkbusterKit.sceneYaw}, quantised like the wire) and the shell
 * stands at the frozen origin ({@code IronManState.equipSource}, on the wire as the action target). Phases (ticks):
 * <ul>
 *   <li>0–{@link #VENT_END} (0–0.4 s): hydraulic hiss, steam from the joints, the reactor dims in steps;</li>
 *   <li>{@link #VENT_END}–{@link #STEP_START} (0.4–1.2 s): the chest doors swing open, the helmet rises;</li>
 *   <li>{@link #STEP_START}–{@link #STEP_END} (1.2–1.8 s): Tony steps out forward and down, the shell kneels; the
 *       hitbox shrinks at {@link #STEP_START} and the real player moves along {@link #path} (server + owner
 *       prediction), so the exit ends where Tony stands: nothing snaps;</li>
 *   <li>{@link #STEP_END}–{@link HulkbusterLayer#EXIT_TICKS} (1.8–2.5 s): the empty shell stands up and closes.</li>
 * </ul>
 * After the exit the empty shell stays at the origin ({@link #shellTick}): under open sky Veronica takes it back
 * (modules detach one by one, arms → legs → torso, and fly up on their thrusters); otherwise it stands
 * {@link #CRUMBLE_AT} ticks (10 s) and crumbles into plates. Pure timing here; the client draws it
 * (client.ironman.hulkbuster.HulkbusterShells) from the same numbers.
 */
public final class HulkbusterExit {
   public static final int VENT_END = 8;
   public static final int STEP_START = 24;
   public static final int STEP_END = 36;
   /** The chest doors close again while the empty shell stands up. */
   public static final int CLOSE_START = 42;
   /** Tony's feet inside the shell (the same chest-high place as in the suit-up). */
   public static final double INSIDE = HulkbusterSuitUp.INSIDE;
   /** Tony steps this far forward of the shell (clear of the kneeling knee). */
   public static final double STEP_OUT = 1.6;
   /** Vanilla Tony is hidden until the chest doors are open enough to show him. */
   public static final int TONY_SHOWN = 12;
   /** Reactor dims in steps over the vent phase: 1 → 0.1. */
   public static final float[] REACTOR_STEPS = {1.0F, 0.72F, 0.48F, 0.26F, 0.1F};
   /** Empty shell after the exit: Veronica's module launches (arms, legs, torso), ticks after the exit end. */
   public static final int[] MODULE_LAUNCH = {10, 24, 38};
   public static final int MODULES = MODULE_LAUNCH.length;
   public static final int MODULE_ARMS = 0;
   public static final int MODULE_LEGS = 1;
   public static final int MODULE_TORSO = 2;
   /** A launched module is gone (above any view) after this many ticks. */
   public static final int MODULE_FLIGHT = 60;
   public static final int VERONICA_LIFE = MODULE_LAUNCH[MODULES - 1] + MODULE_FLIGHT;
   /** No Veronica (no open sky): the empty shell stands 10 s, then crumbles into plates. */
   public static final int CRUMBLE_AT = 200;
   public static final int CRUMBLE_TICKS = 30;
   public static final int CRUMBLE_LIFE = CRUMBLE_AT + CRUMBLE_TICKS;

   private HulkbusterExit() {
   }

   /** The frozen facing as the wire carries it (a byte), so server and clients use the very same yaw. */
   public static float quantize(float yaw) {
      return HulkbusterWire.sceneYaw(HulkbusterWire.scene(0, 0, yaw, -1));
   }

   static Vec3 forward(float yaw) {
      return HulkbusterSuitUp.forward(yaw);
   }

   /** Where Tony ends up: in front of the shell when that place is free, else in place (pure). */
   public static Vec3 spot(Vec3 origin, float yaw, boolean stepOut) {
      return stepOut ? origin.add(forward(yaw).scale(STEP_OUT)) : origin;
   }

   /** Reactor brightness at an exit tick (steps over 0–0.4 s, then the empty shell's dim glow). */
   public static float reactor(double tick) {
      int step = Mth.clamp((int)Math.floor(tick / 2.0), 0, REACTOR_STEPS.length - 1);
      return REACTOR_STEPS[step];
   }

   /** 0..1 progress of the step out at an exit tick. */
   public static double stepProgress(double tick) {
      return smooth((tick - STEP_START) / (double)(STEP_END - STEP_START));
   }

   /**
    * Where vanilla Tony is drawn at an exit tick (feet, world): inside the chest until the step, then forward and
    * down onto {@code spot} with a small lift over the kneeling thigh.
    */
   public static Vec3 tony(Vec3 origin, Vec3 spot, double tick) {
      Vec3 inside = origin.add(0.0, INSIDE, 0.0);
      if (tick < STEP_START) {
         return inside;
      }

      double s = stepProgress(tick);
      return inside.add(spot.subtract(inside).scale(s)).add(0.0, 0.25 * Math.sin(Math.PI * s), 0.0);
   }

   /**
    * The real feet of the player at an exit tick (server and owner prediction): the big body stands at the origin
    * until the step; from {@link #STEP_START} the small body follows {@link #tony}.
    */
   public static Vec3 path(Vec3 origin, Vec3 spot, int tick) {
      return tick < STEP_START ? origin : tony(origin, spot, tick);
   }

   /** The big body (hitbox) during the exit: until Tony starts to step out. */
   public static boolean big(int tick) {
      return tick < STEP_START;
   }

   /** Height a launched module has climbed, blocks (accelerating thrust). */
   public static double moduleRise(double sinceLaunch) {
      if (sinceLaunch <= 0.0) {
         return 0.0;
      }

      return 0.02 * sinceLaunch * sinceLaunch + 0.05 * sinceLaunch;
   }

   /** Life of the empty shell after the exit. */
   public static int shellLife(boolean veronica) {
      return veronica ? VERONICA_LIFE : CRUMBLE_LIFE;
   }

   private static double smooth(double t) {
      t = Mth.clamp(t, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   // ---------------------------------------------------------------- server

   /** "Костюм" while ACTIVE: freeze the facing and the origin, decide the step-out spot and Veronica. */
   public static void begin(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Vec3 origin = player.position();
      float yaw = quantize(player.getYRot());
      kit.sceneYaw = yaw;
      kit.sceneStart = origin;
      state.equipSource = origin;
      Vec3 out = spot(origin, yaw, true);
      kit.exitStepOut = player.level().noCollision(player, standingBox(out));
      kit.exitVeronica = player.level().canSeeSky(BlockPos.containing(origin.x, origin.y + 3.6, origin.z));
      kit.shellTicks = -1;
      player.setDeltaMovement(Vec3.ZERO);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.EXIT, origin.add(0.0, 2.0, 0.0), 1.0F);
   }

   static AABB standingBox(Vec3 feet) {
      return new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
   }

   /** One exit tick (after HulkbusterLayer.tick): hold the player on the path, play the beats. */
   public static void tick(ServerPlayer player, IronManState state, int tick) {
      HulkbusterKit kit = state.hulkKit;
      Vec3 origin = state.equipSource;
      if (origin == null) {
         return;
      }

      Vec3 expected = path(origin, spot(origin, kit.sceneYaw, kit.exitStepOut), Math.max(0, tick - 1));
      if (player.position().distanceTo(expected) > 6.0) {
         // Teleported or changed dimension mid-exit: the shell and the path move with the player, never pull him back.
         origin = player.position().subtract(expected.subtract(origin));
         state.equipSource = origin;
      }

      Vec3 at = path(origin, spot(origin, kit.sceneYaw, kit.exitStepOut), tick);
      if (player.position().distanceTo(at) > 1.0) {
         player.teleportTo(at.x, at.y, at.z);
      } else {
         player.setPos(at.x, at.y, at.z);
      }

      player.setDeltaMovement(Vec3.ZERO);
      player.resetFallDistance();
      Vec3 chest = origin.add(0.0, 2.4, 0.0).add(forward(kit.sceneYaw).scale(0.4));
      if (tick == VENT_END) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.CLAMP, chest, 0.8F);
      } else if (tick == STEP_START) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH, chest, 0.85F);
      } else if (tick == STEP_START + 8) {
         // The knee touches the ground.
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.STEP, origin.add(forward(kit.sceneYaw).scale(0.6)), 0.85F);
      } else if (tick == CLOSE_START) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.CLAMP, chest, 0.9F);
      }
   }

   /** The exit finished: the player stands on the spot (already there), the empty shell takes over. */
   public static void finish(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      Vec3 origin = state.equipSource;
      state.equipSource = null;
      kit.sceneStart = null;
      if (origin == null) {
         return;
      }

      kit.shellAt = origin;
      kit.shellYaw = kit.sceneYaw;
      kit.shellTicks = 0;
      kit.shellVeronica = kit.exitVeronica;
      Vec3 spot = spot(origin, kit.sceneYaw, kit.exitStepOut);
      if (player.position().distanceToSqr(spot) > 1.0E-4) {
         player.teleportTo(spot.x, spot.y, spot.z);
      }
   }

   /** The empty shell's server beats (sounds and the crumble debris), every tick until its life ends. */
   public static void shellTick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (kit.shellTicks < 0 || kit.shellAt == null) {
         return;
      }

      int t = ++kit.shellTicks;
      Vec3 at = kit.shellAt;
      Vec3 fwd = forward(kit.shellYaw);
      Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
      if (kit.shellVeronica) {
         for (int i = 0; i < MODULES; i++) {
            if (t == MODULE_LAUNCH[i]) {
               Vec3 module = switch (i) {
                  case MODULE_ARMS -> at.add(0.0, 2.6, 0.0);
                  case MODULE_LEGS -> at.add(0.0, 1.0, 0.0);
                  default -> at.add(0.0, 2.2, 0.0);
               };
               HulkbusterSounds.play(player, HulkbusterSounds.Cue.CLAMP, module, 1.15F);
               HulkbusterSounds.play(player, HulkbusterSounds.Cue.JET_LAUNCH, module, 1.25F - 0.15F * i);
            }
         }
      } else if (t == CRUMBLE_AT) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.BREAK, at.add(0.0, 1.6, 0.0), 0.9F);
         HeroFx.shards(player, at.add(0.0, 2.0, 0.0), 1.0F, null, new float[0], null);
         HeroFx.shards(player, at.add(right.scale(0.9)).add(0.0, 1.2, 0.0), 0.7F, null, new float[0], null);
         HeroFx.shards(player, at.add(right.scale(-0.9)).add(0.0, 1.2, 0.0), 0.7F, null, new float[0], null);
      }

      if (t >= shellLife(kit.shellVeronica)) {
         kit.shellTicks = -1;
         kit.shellAt = null;
      }
   }
}
