package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import net.minecraft.world.phys.Vec3;

/** Test access to the package-private timing of {@link WorldCutErasure}. */
public final class WorldCutErasureProbe {
   private WorldCutErasureProbe() {}
   public static int segments() { return WorldCutErasure.SEGMENTS; }
   public static double cutTick(int k) { return WorldCutErasure.cutTick(k); }
   public static double front(double t) { return WorldCutErasure.front(t); }
   public static float offset(int k) { return WorldCutErasure.offset(k); }
   public static float length(int k) { return WorldCutErasure.length(k); }
   public static float strokeTime(int k, double t) { return WorldCutErasure.strokeTime(k, t); }
   public static boolean active(double t) { return WorldCutErasure.active(t); }
   public static Vec3[] axis(int[] surface) {
      return WorldCutErasure.axis(new WorldCutShape(Vec3.ZERO, new Vec3(0, 0, 1), 1), surface);
   }
}
