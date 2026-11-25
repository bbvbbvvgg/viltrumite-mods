package dev.baranhan.viltrumitecore.client.cinematic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * A keyframed camera path of a cinematic scene (wave1-22, task T4). Every frame is an offset from
 * the scene focus in the frame the shot was started in: x right, y up, z forward. Pure math (only
 * {@link Vec3} as a value, no Minecraft client classes), so the blend is unit tested.
 */
public final class CameraPath {
   /** One frame: where the camera stands, what it looks at, an optional fov (0 = keep the player's). */
   public record Key(Vec3 offset, Vec3 look, float fov) {}

   /** Look point of a frame that names none: a touch above the focus feet. */
   public static final Vec3 DEFAULT_LOOK = new Vec3(0.0, 1.2, 0.0);

   private final List<Key> keys;

   public CameraPath(List<Key> keys) {
      this.keys = bakeFov(List.copyOf(keys));
   }

   public int size() {
      return this.keys.size();
   }

   public boolean isEmpty() {
      return this.keys.isEmpty();
   }

   public Key key(int index) {
      return this.keys.get(index);
   }

   /**
    * The frame at {@code progress} 0..1: a straight blend of the two neighbouring keys. Only the
    * position is scaled by {@code amplitude} (reduced camera motion), the aim stays on the subject.
    */
   public Key sample(float progress, float amplitude) {
      if (this.keys.isEmpty()) {
         return new Key(Vec3.ZERO, DEFAULT_LOOK, 0.0F);
      }
      if (this.keys.size() == 1) {
         return scaled(this.keys.get(0), amplitude);
      }
      float p = (float)clamp01(progress);
      float span = p * (this.keys.size() - 1);
      int index = Math.min(this.keys.size() - 2, (int)span);
      return blend(this.keys.get(index), this.keys.get(index + 1), span - index, amplitude);
   }

   /** World position of a focus-relative offset in the frame facing {@code yawDeg}. */
   public static Vec3 toWorld(Vec3 offset, Vec3 origin, float yawDeg) {
      double yaw = Math.toRadians(yawDeg);
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      return origin.add(-fz * offset.x + fx * offset.z, offset.y, fx * offset.x + fz * offset.z);
   }

   /**
    * Resolves the lens of every frame once, at construction, instead of borrowing a neighbour inside
    * a segment: a frame without its own fov gets a straight interpolation by index between the two
    * nearest frames that name one (before the first and after the last it keeps that frame's value).
    * Without any lens the whole path stays 0, i.e. the player's own fov. Resolving per segment instead
    * would hold one value for a segment and jump at the next cut.
    */
   private static List<Key> bakeFov(List<Key> keys) {
      List<Key> baked = new ArrayList<>(keys.size());
      int previous = -1;
      for (int i = 0; i < keys.size(); i++) {
         Key key = keys.get(i);
         if (key.fov() > 0.0F) {
            previous = i;
            baked.add(key);
            continue;
         }
         int next = nextLens(keys, i + 1);
         baked.add(new Key(key.offset(), key.look(), lens(keys, previous, next, i)));
      }
      return baked;
   }

   private static float lens(List<Key> keys, int previous, int next, int index) {
      if (previous < 0 && next < 0) {
         return 0.0F;
      }
      if (previous < 0) {
         return keys.get(next).fov();
      }
      if (next < 0) {
         return keys.get(previous).fov();
      }
      float t = (index - previous) / (float)(next - previous);
      return keys.get(previous).fov() + (keys.get(next).fov() - keys.get(previous).fov()) * t;
   }

   private static int nextLens(List<Key> keys, int from) {
      for (int i = from; i < keys.size(); i++) {
         if (keys.get(i).fov() > 0.0F) {
            return i;
         }
      }
      return -1;
   }

   private static Key scaled(Key key, float amplitude) {
      return new Key(key.offset().scale(amplitude), key.look(), key.fov());
   }

   private static Key blend(Key a, Key b, float t, float amplitude) {
      return new Key(a.offset().lerp(b.offset(), t).scale(amplitude), a.look().lerp(b.look(), t),
         (float)(a.fov() + (b.fov() - a.fov()) * t));
   }

   static double clamp01(double u) {
      return u < 0.0 ? 0.0 : (u > 1.0 ? 1.0 : u);
   }
}
