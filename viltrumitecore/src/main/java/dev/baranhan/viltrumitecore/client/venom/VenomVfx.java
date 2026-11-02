package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.venom.VenomFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Vector3f;

/**
 * The symbiote's goo for Venom's presets (740-749): black wet splatter and strands (dust in symbiote black and a
 * blue-black sheen, squid-ink smears), white web fluff for torn webs, a strand burst for the takeover. Client only.
 */
public final class VenomVfx {
   static final DustParticleOptions GOO = new DustParticleOptions(new Vector3f(0.03F, 0.03F, 0.05F), 1.4F);
   static final DustParticleOptions SHEEN = new DustParticleOptions(new Vector3f(0.18F, 0.22F, 0.42F), 0.9F);
   static final DustParticleOptions WEB = new DustParticleOptions(new Vector3f(0.92F, 0.92F, 0.95F), 0.8F);
   private static final RandomSource RANDOM = RandomSource.create();
   private static boolean listening;

   private VenomVfx() {
   }

   static void init() {
      if (!listening) {
         listening = true;
         dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx.addPresetListener(VenomVfx::onPreset);
      }
   }

   /** Pure: does a preset id belong to Venom. */
   static boolean ours(int netId) {
      return netId >= 740 && netId <= 749;
   }

   static void onPreset(FxPreset preset, Vec3 at, Vec3 target) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null || !ours(preset.netId())) {
         return;
      }
      if (preset == VenomFx.TRANSFORM) {
         burst(level, GOO, at, 26, 0.35);
         burst(level, SHEEN, at, 10, 0.3);
         burst(level, ParticleTypes.SQUID_INK, at, 6, 0.12);
      } else if (preset == VenomFx.CLAW) {
         burst(level, GOO, at, 8, 0.25);
         burst(level, ParticleTypes.CRIT, at, 4, 0.3);
      } else if (preset == VenomFx.STICK) {
         burst(level, GOO, at, 10, 0.18);
      } else if (preset == VenomFx.SLAM || preset == VenomFx.LEAP) {
         double r = Math.max(1.0, target == null ? 1.5 : target.x - at.x);
         ring(level, GOO, at.add(0, 0.15, 0), r * 0.6, 22, 0.18);
         burst(level, ParticleTypes.SQUID_INK, at.add(0, 0.2, 0), 8, 0.2);
      } else if (preset == VenomFx.SWEEP) {
         double r = Math.max(1.0, target == null ? 3.0 : target.x - at.x);
         ring(level, GOO, at, r, 28, 0.05);
      } else if (preset == VenomFx.RECOIL) {
         burst(level, GOO, at, 20, 0.4);
         burst(level, ParticleTypes.SMOKE, at, 8, 0.1);
      } else if (preset == VenomFx.WEB) {
         burst(level, WEB, at, 14, 0.2);
         burst(level, GOO, at, 6, 0.15);
      } else if (preset == VenomFx.CLASH) {
         burst(level, GOO, at, 18, 0.45);
         burst(level, WEB, at, 10, 0.4);
      } else if (preset == VenomFx.ENGULF) {
         // strands between the husk and the host
         if (target != null) {
            Vec3 d = target.subtract(at);
            for (int i = 0; i < 24; i++) {
               Vec3 p = at.add(d.scale(RANDOM.nextDouble()));
               level.addParticle(GOO, p.x, p.y, p.z, 0, 0.02, 0);
            }
         }
         burst(level, GOO, at, 30, 0.3);
         burst(level, ParticleTypes.SQUID_INK, at, 8, 0.15);
      }
   }

   static void burst(ClientLevel level, ParticleOptions p, Vec3 at, int n, double speed) {
      for (int i = 0; i < n; i++) {
         level.addParticle(p, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 2 * speed, (RANDOM.nextDouble() - 0.3) * 2 * speed,
            (RANDOM.nextDouble() - 0.5) * 2 * speed);
      }
   }

   static void ring(ClientLevel level, ParticleOptions p, Vec3 at, double radius, int n, double out) {
      for (int i = 0; i < n; i++) {
         double ang = Math.PI * 2 * i / n + RANDOM.nextDouble() * 0.1;
         double cx = Math.cos(ang), cz = Math.sin(ang);
         level.addParticle(p, at.x + cx * radius, at.y, at.z + cz * radius, cx * out, 0.02, cz * out);
      }
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      // nothing persistent yet (particles die with the level)
   }
}
