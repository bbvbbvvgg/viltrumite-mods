package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The suit-up scene (rework spec §4): always the full-size Mark 48, made of its real plates.
 * Air variant (room overhead): Veronica's capsule falls from the sky and hangs over the player, the player
 * flies up on the repulsors (script, no control), the capsule opens on the open frame, the player backs in,
 * the plates close one by one, then the machine drops and lands like a superhero (HulkMove.HERO_LANDING).
 * Ground variant (no room overhead): the capsule lands behind the player, opens, the player backs in.
 * The scripted path is one pure function ({@link #path}) for the server and the owner's client; the server
 * corrects a client that strays more than a block. Before the torso closes, control or the suit key stops
 * the scene with no cooldown; after it the scene has hyper armour to its end.
 */
public final class HulkbusterSuitUp {
   /** Frame hang height above the start in the air variant (spec §4.1: ~12 blocks; lower under a ceiling). */
   public static final double AIR_HEIGHT = 12.0;
   public static final double AIR_MIN = 6.0;
   /** Chest-high offset of the player inside the open frame (the vanilla body hangs in the torso). */
   public static final double INSIDE = 1.4;
   /** The player waits behind the open back before backing in. */
   public static final double BEHIND = 1.5;
   /** Ground variant: the frame stands this far behind the player. */
   public static final double GROUND_BACK = 2.0;
   public static final int RISE_TICKS = 30;
   public static final int ENTRY_TICKS = 10;
   private static final double CORRECT = 1.0;

   private HulkbusterSuitUp() {
   }

   /** Enough free air over the player for the hanging frame (box of the big body at AIR_MIN or more). */
   public static boolean airRoom(ServerPlayer player) {
      return headroom(player) >= AIR_MIN;
   }

   static double headroom(ServerPlayer player) {
      float width = 0.6F * HulkbusterRules.WIDTH_SCALE;
      float height = 1.8F * HulkbusterRules.HEIGHT_SCALE;
      double free = 0.0;
      for (double h = 1.0; h <= AIR_HEIGHT; h += 1.0) {
         AABB box = new AABB(player.getX() - width / 2, player.getY() + h, player.getZ() - width / 2, player.getX() + width / 2,
            player.getY() + h + height, player.getZ() + width / 2);
         if (!player.level().noCollision(player, box)) {
            break;
         }

         free = h;
      }

      return free;
   }

   /** Feet of the frame (the big body's feet when the torso closes). */
   public static Vec3 podPoint(ServerPlayer player, boolean air) {
      if (air) {
         return player.position().add(0.0, Math.min(AIR_HEIGHT, headroom(player)), 0.0);
      }

      Vec3 back = player.position().subtract(forward(player.getYRot()).scale(GROUND_BACK));
      Vec3 fit = IronManHulkbuster.freeSpot(player, back);
      return fit != null ? fit : player.position();
   }

   static Vec3 forward(float yaw) {
      Vec3 f = Vec3.directionFromRotation(0.0F, yaw);
      return new Vec3(f.x, 0.0, f.z).normalize();
   }

   /**
    * Feet of the player at a scene tick (pure, both sides). {@code start} = the feet when the scene began,
    * {@code frame} = the frame's feet, {@code yaw} = the frame's facing.
    */
   public static Vec3 path(Vec3 start, Vec3 frame, float yaw, boolean air, int sceneTick, int dropTicks) {
      Vec3 forward = forward(yaw);
      Vec3 inside = frame.add(0.0, INSIDE, 0.0);
      int assemble = sceneTick - dropTicks;
      if (air) {
         Vec3 behind = inside.subtract(forward.scale(BEHIND));
         if (sceneTick < RISE_TICKS) {
            return lerp(start, behind, smooth(sceneTick / (double)RISE_TICKS));
         }

         if (assemble < 0) {
            return behind;
         }

         if (assemble < ENTRY_TICKS) {
            return lerp(behind, inside, smooth(assemble / (double)ENTRY_TICKS));
         }
      } else {
         if (assemble < 0) {
            return start;
         }

         if (assemble < ENTRY_TICKS) {
            return lerp(start, frame, smooth(assemble / (double)ENTRY_TICKS));
         }

         if (assemble < HulkbusterLayer.TORSO_TICK) {
            return lerp(frame, inside, smooth((assemble - ENTRY_TICKS) / (double)(HulkbusterLayer.TORSO_TICK - ENTRY_TICKS)));
         }
      }

      return assemble < HulkbusterLayer.TORSO_TICK ? inside : frame;
   }

   private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
      return a.add(b.subtract(a).scale(t));
   }

   private static double smooth(double t) {
      t = Mth.clamp(t, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   // ---------------------------------------------------------------- server scene

   public static void begin(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      kit.sceneStart = player.position();
      kit.sceneYaw = player.getYRot();
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.POD_FALL, state.equipSource == null ? player.position() : state.equipSource, 1.0F);
   }

   public static void tick(ServerPlayer player, IronManState state) {
      HulkbusterLayer layer = state.hulkbuster;
      HulkbusterKit kit = state.hulkKit;
      Vec3 frame = state.equipSource;
      int tick = layer.sceneTick();
      if (frame == null || tick < 0 || kit.sceneStart == null) {
         return;
      }

      Vec3 at = path(kit.sceneStart, frame, kit.sceneYaw, layer.air(), tick, layer.dropTicks());
      if (player.position().distanceTo(at) > CORRECT) {
         player.teleportTo(at.x, at.y, at.z);
      } else {
         player.setPos(at.x, at.y, at.z);
      }

      player.setDeltaMovement(Vec3.ZERO);
      player.resetFallDistance();
      int drop = layer.dropTicks();
      if (tick == (layer.air() ? 20 : 12)) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.POD_OPEN, frame.add(0.0, 1.7, 0.0), 1.0F);
         HeroFx.flash(player, frame.add(0.0, 1.7, 0.0), 0.7F);
         if (!layer.air()) {
            BlockPos ground = BlockPos.containing(frame.x, frame.y - 0.5, frame.z);
            HeroFx.slam(player, frame, player.level().getBlockState(ground), 3.0F);
         }
      }

      int assemble = tick - drop;
      for (int i = 0; i < HulkbusterLayer.PARTS; i++) {
         if (assemble == HulkbusterLayer.PART_LOCK[i]) {
            Vec3 plate = frame.add(0.0, switch (i) {
               case 0 -> 0.9;
               case 1 -> 2.2;
               case 2 -> 2.0;
               case 3 -> 2.8;
               default -> 3.1;
            }, 0.0);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.CLAMP, plate, 0.9F + 0.05F * i);
            HeroFx.flash(player, plate, 0.35F);
         }
      }

      if (assemble == HulkbusterLayer.TORSO_TICK + 2) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.REACTOR, frame.add(0.0, 2.4, 0.0), 1.0F);
      }
   }

   /** Control took the player during the scene: before the torso closes the frame flies back (no cooldown). */
   public static void interrupt(ServerPlayer player, IronManState state) {
      if (!state.hulkbuster.torsoClosed()) {
         cancel(player, state, null);
      }
   }

   /** Stop before the torso closed (control, suit key, no room): the frame goes back to Veronica, no cooldown. */
   public static void cancel(ServerPlayer player, IronManState state, @Nullable String message) {
      boolean big = state.hulkbuster.big();
      if (!state.hulkbuster.cancel() && message != null) {
         // The torso already closed and the room is gone: nothing grows, the layer goes (no cooldown).
         state.hulkbuster.refuse();
      }

      Vec3 frame = state.equipSource;
      state.equipSource = null;
      end(player, state);
      if (frame != null) {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.JET_LAUNCH, frame, 1.2F);
      }

      if (message != null) {
         player.displayClientMessage(Component.translatable(message), true);
         HulkbusterSounds.notify(player, HulkbusterSounds.Cue.REFUSE);
      }

      if (big) {
         player.refreshDimensions();
      }
   }

   /** The scene is done: the machine is on. Air: it falls into the superhero landing. */
   public static void finish(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      kit.sceneStart = null;
      if (state.hulkbuster.air()) {
         kit.timeline.requestAt(HulkMove.HERO_LANDING, -1, 0);
      } else {
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
      }
   }

   public static void end(ServerPlayer player, IronManState state) {
      state.hulkKit.sceneStart = null;
   }
}
