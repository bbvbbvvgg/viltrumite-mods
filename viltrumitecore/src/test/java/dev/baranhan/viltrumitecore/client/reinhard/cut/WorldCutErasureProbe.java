package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import net.minecraft.world.phys.Vec3;

/** Test access to the package-private timing and geometry of {@link WorldCutErasure}. */
public final class WorldCutErasureProbe {
   private WorldCutErasureProbe() {}
   public static final float SCALE = WorldCutErasure.SCALE, REVEAL = WorldCutErasure.REVEAL,
      EARLIEST = WorldCutErasure.EARLIEST, HOLD = WorldCutErasure.HOLD, CLOSE = WorldCutErasure.CLOSE,
      TAIL_BLOCKS = WorldCutErasure.TAIL_BLOCKS;
   public static final double RADIUS = WorldCutErasure.RADIUS, GROUND = WorldCutErasure.GROUND,
      CLEARANCE = WorldCutErasure.CLEARANCE, EYE = WorldCutErasure.EYE, EYE_HOLD = WorldCutErasure.EYE_HOLD,
      CASTER_SPAN = WorldCutErasure.CASTER_SPAN;
   public static double front(double t) { return WorldCutErasure.front(t); }
   public static float closeAt(double doneElapsed) { return WorldCutErasure.closeAt(doneElapsed); }
   public static boolean active(double t, float closeAt) { return WorldCutErasure.active(t, closeAt); }
   public static Vec3[] axis(int[] surface) {
      return WorldCutErasure.axis(new WorldCutShape(Vec3.ZERO, new Vec3(0, 0, 1), 1), surface);
   }
   public static Vec3[] axis(WorldCutShape shape, int[] surface) { return WorldCutErasure.axis(shape, surface); }
   public static ErasurePath path(WorldCutShape shape, int[] surface) { return WorldCutErasure.path(shape, surface); }
   /** The one stroke of a cast at elapsed tick t; null outside its window. */
   public static ErasureStroke stroke(WorldCutShape shape, int[] surface, double t, float closeAt, float pulse) {
      return WorldCutErasure.stroke(WorldCutErasure.built(shape, surface), t, closeAt, pulse);
   }
}
