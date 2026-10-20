package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** One finishing accent per local audio window across both cut types.
 * Coalescing concurrent DONE events avoids stacking identical full-level transients.
 * Volume increases the audible radius, not PCM gain beyond the vanilla mixer limit.
 */
final class CutFinishSound {
   private static Object level;
   private static long last = Long.MIN_VALUE;
   private CutFinishSound() {}
   static void clear() { level = null; last = Long.MIN_VALUE; }
   static void play(Vec3 at, long now) {
      var world = Minecraft.getInstance().level;
      if (world == null) return;
      if (level != world) { level = world; last = Long.MIN_VALUE; }
      if (last != Long.MIN_VALUE && now >= last && now - last < 12) return;
      last = now;
      world.playLocalSound(at.x, at.y, at.z, ReinhardSounds.WORLD_CUT_BLAST.get(), SoundSource.PLAYERS, 5F, .92F, false);
   }
}
