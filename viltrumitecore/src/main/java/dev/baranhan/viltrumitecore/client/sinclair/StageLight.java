package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;

/**
 * The stage around an act (spec §5.6, §6.1): spotlight cones (on the volunteer during the capture,
 * on Sinclair during the act), the drum-roll hush and the silence of the pause (every other sound
 * near the stage is ducked, Sinclair's own sounds are spared), the house-lights dimming, and the
 * per-act camera beats (CameraShake/FlashOverlay) on the {@link ActScript} ticks.
 */
public final class StageLight {
   /** Listener radius of the hush and of the dimming. */
   static final double HUSH_RANGE = 32.0;
   static final float HUSH_VOLUME = 0.12F;
   private static final Object DUCK_OWNER = new Object();
   private static final Set<ResourceLocation> OWN_SOUNDS = new HashSet<>();
   private static final Map<Integer, int[]> SEEN = new HashMap<>();
   private static float dim;
   private static float dimTarget;
   private static boolean ducking;


   private StageLight() {
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      Player viewer = mc.player;
      if (level == null || viewer == null) {
         release();
         SEEN.clear();
         dimTarget = 0.0F;
         dim = 0.0F;
         return;
      }

      boolean quiet = false;
      float dimWanted = 0.0F;
      Set<Integer> live = new HashSet<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = SinclairView.of(player);
         SinclairMove move = snapshot == null ? null : SinclairView.move(snapshot);
         ActScript script = SinclairPoseRules.script(move);
         if (script == null) {
            continue;
         }

         live.add(player.getId());
         int tick = snapshot.actionElapsed();
         int[] seen = SEEN.computeIfAbsent(player.getId(), id -> new int[]{-1, -1});
         if (seen[0] != snapshot.actionId() || tick < seen[1]) {
            seen[0] = snapshot.actionId();
            seen[1] = tick - 1;
         }

         Vec3 focus = snapshot.actionTarget() != null ? snapshot.actionTarget() : player.position();
         double near = Math.min(viewer.distanceTo(player), viewer.position().distanceTo(focus));
         for (int k = seen[1] + 1; k <= tick; k++) {
            float[] beat = StageBeats.beat(script.shape(), k);
            if (beat[0] > 0.0F) {
               CameraShake.addAt(focus, beat[0], 28.0);
            }

            if (beat[1] > 0.0F && near < 24.0) {
               FlashOverlay.flash(beat[1] * (float) (1.0 - near / 24.0), 6);
            }
         }

         seen[1] = tick;
         if (near < HUSH_RANGE) {
            if (StageBeats.quiet(script, tick)) {
               quiet = true;
               dimWanted = Math.max(dimWanted, script.paused(tick) ? 0.38F : 0.26F);
            } else if (tick < script.bowStart()) {
               dimWanted = Math.max(dimWanted, 0.16F);
            }
         }
      }

      SEEN.keySet().retainAll(live);
      if (quiet && !ducking) {
         if (OWN_SOUNDS.isEmpty()) {
            for (String name : SinclairSounds.NAMES) {
               OWN_SOUNDS.add(SinclairSounds.location(name));
            }
         }

         SoundDuck.INSTANCE.duck(DUCK_OWNER, SoundSource.MASTER, HUSH_VOLUME, 4, OWN_SOUNDS);
         ducking = true;
      } else if (!quiet && ducking) {
         release();
      }

      dimTarget = dimWanted;
      dim += (dimTarget - dim) * 0.25F;
   }

   private static void release() {
      if (ducking) {
         SoundDuck.INSTANCE.release(DUCK_OWNER, 6);
         ducking = false;
      }
   }

   /** House lights: the world darkens around the stage (under the HUD). */
   public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
      if (dim <= 0.01F || Minecraft.getInstance().options.hideGui) {
         return;
      }

      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      int a = (int) (dim * 255.0F);
      g.fill(0, 0, w, h, a << 24 | 0x0A0612);
      int edge = Math.min(255, (int) (a * 1.6F));
      g.fillGradient(0, 0, w, h / 4, edge << 24 | 0x0A0612, 0x000A0612);
      g.fillGradient(0, h - h / 4, w, h, 0x000A0612, edge << 24 | 0x0A0612);
   }

   /** Spotlight cones, drawn in the Sinclair VFX batch. */
   static void render(SinclairVfx.Ctx c, Player player, HeroPublicSnapshot snapshot, ClientLevel level) {
      SinclairMove move = SinclairView.move(snapshot);
      if (move == null) {
         return;
      }

      double t = SinclairView.moveTicks(player, snapshot, c.partialTick());
      if (move == SinclairMove.CAPTURE) {
         Entity volunteer = snapshot.controlTargetId() >= 0 ? level.getEntity(snapshot.controlTargetId()) : null;
         if (volunteer != null) {
            cone(c, SinclairVfx.pos(volunteer, c.partialTick()), Math.max(0.7, volunteer.getBbWidth()), Mth.clamp((float) t / 4.0F, 0.0F, 1.0F));
         }

         return;
      }

      if (move == SinclairMove.COPY) {
         // Two follow-spots on the stopped pair; they swell with the card and fade on the return.
         float s = (float) (CopyCinematic.glow(t) * 0.6 + CopyCinematic.cameraWeight(t) * 0.5);
         cone(c, SinclairVfx.pos(player, c.partialTick()), 1.0, Mth.clamp(s, 0.0F, 1.2F));
         Entity volunteer = snapshot.controlTargetId() >= 0 ? level.getEntity(snapshot.controlTargetId()) : null;
         if (volunteer != null) {
            cone(c, SinclairVfx.pos(volunteer, c.partialTick()), Math.max(0.7, volunteer.getBbWidth()), Mth.clamp(s, 0.0F, 1.2F));
         }

         return;
      }

      ActScript script = SinclairPoseRules.script(move);
      if (script == null) {
         return;
      }

      float in = Mth.clamp((float) t / 5.0F, 0.0F, 1.0F);
      float out = 1.0F - Mth.clamp((float) (t - (script.length() - 6)) / 6.0F, 0.0F, 1.0F);
      float bright = script.paused((int) t) ? 1.3F : 1.0F;
      cone(c, SinclairVfx.pos(player, c.partialTick()), 1.2, in * out * bright);
   }

   /**
    * A soft follow-spot: fine motes along a narrow cone (faint at the lamp, brighter near the floor) and a lit
    * pool of small dots. Playtest 2026-10-28: big 0.18-block squares read as a cloud of tiles, not light.
    */
   private static void cone(SinclairVfx.Ctx c, Vec3 feet, double radius, float strength) {
      if (strength <= 0.01F) {
         return;
      }

      Vec3 top = feet.add(0.0, 7.0, 0.0);
      int lines = 18;
      for (int i = 0; i < lines; i++) {
         double a = Math.PI * 2.0 * i / lines + c.seconds() * 0.15;
         Vec3 base = feet.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
         for (int k = 3; k <= 28; k++) {
            double u = k / 28.0;
            double drift = Math.sin(c.seconds() * 1.7 + i * 0.9 + k * 0.37) * 0.015;
            Vec3 p = top.lerp(base, u).add(drift, 0.0, -drift);
            c.dot(p, 0.012F + 0.014F * (float) u, SinclairPalette.IVORY, (int) ((6 + 34 * u * u) * strength));
         }
      }

      for (int ring = 1; ring <= 4; ring++) {
         double r = radius * ring / 4.0;
         int count = 8 + ring * 8;
         c.ring(feet.add(0.0, 0.04, 0.0), r, count, c.seconds() * 0.3 + ring, 0.035F, SinclairPalette.IVORY, (int) ((ring == 4 ? 110 : 45) * strength));
      }
   }

   @Nullable
   static ActShape shapeOf(@Nullable SinclairMove move) {
      return SinclairPoseRules.shape(move);
   }
}
