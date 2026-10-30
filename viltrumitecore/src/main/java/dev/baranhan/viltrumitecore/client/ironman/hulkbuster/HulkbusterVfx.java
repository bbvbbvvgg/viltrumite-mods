package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterCage;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Hulkbuster Mark 48 client VFX (rework spec §9, H5), drawn with the shared pixel pipeline (PixelVfx
 * billboards, CameraShake) on top of the server's HeroFx presets (slam, shockwave, shards, flash, hit-stop)
 * that every contact already sends. Everything here is read from the synced snapshot, so every viewer sees
 * the same scene; nothing is sent per particle.
 *
 * <ul>
 *   <li>Steps: ground-coloured dust ring, kicked chunks, a flat shock ring, hydraulic steam at the knees,
 *       a distance-scaled shake. Sprint ram adds a dust bow wave and furrows behind the feet.</li>
 *   <li>Thrusters: volumetric plumes (white-hot core, orange flame, grey smoke, heat shimmer) from the four
 *       boot nozzles and the two back nozzles for jet charge, rise, hover, dash, dive and the Jet Ram, with
 *       ground scorch sparks when the plume reaches the floor.</li>
 *   <li>Contacts (entering MAIN of a strike): sparks, metal shavings and an impact ring at the fist; the
 *       jackhammer adds a piston burst of sparks on each grab punch.</li>
 *   <li>Heavy repulsors (beam core, cyan halo, muzzle flares, gathering motes), seismic rift (a travelling
 *       line of ground eruptions), the elbow thruster of the charged punch.</li>
 *   <li>Veronica's cage as a 3D scene: target ring and growing shadow, the cage falling from the sky,
 *       bars with an energy shimmer while it holds, fading when empty.</li>
 *   <li>Damage stages: stage 1 sparks and a thin smoke wisp; stage 2 thick black smoke from two points,
 *       frequent spark showers, a flickering reactor.</li>
 *   <li>Heavy landing / dive / Jet Ram contact: a large dust crater ring, debris and a long smoke tail.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterVfx {
   /** Hard cap on live motes (oldest dropped first). */
   public static final int MAX_MOTES = 4000;
   public static final int STEP_DUST = 46;
   public static final int STEP_CHUNKS = 10;
   private static final Random RANDOM = new Random();
   private static final List<Mote> MOTES = new ArrayList<>();
   private static final List<Rift> RIFTS = new ArrayList<>();
   private static final List<Tail> TAILS = new ArrayList<>();
   private static final Map<Integer, Track> TRACKS = new HashMap<>();
   private static long frameTick;

   private HulkbusterVfx() {
   }

   /** Per-player memory of the last tick's state (edge detection). */
   private static final class Track {
      int stride = Integer.MIN_VALUE;
      @Nullable
      HulkMove move;
      MovePhase phase = MovePhase.INTRO;
      int step = -1;
      HulkbusterMotion.State motion = HulkbusterMotion.State.NONE;
      int punches;
   }

   /** One pixel mote. {@code glow} motes are drawn additively, the rest alpha-blended (dust, smoke). */
   private static final class Mote {
      double x, y, z, vx, vy, vz;
      final int life;
      int age;
      final float size0, size1;
      final int r, g, b, a;
      final boolean glow;
      final float gravity, drag;

      Mote(Vec3 p, Vec3 v, int life, float size0, float size1, int rgb, int a, boolean glow, float gravity, float drag) {
         this.x = p.x;
         this.y = p.y;
         this.z = p.z;
         this.vx = v.x;
         this.vy = v.y;
         this.vz = v.z;
         this.life = Math.max(1, life);
         this.size0 = size0;
         this.size1 = size1;
         this.r = rgb >> 16 & 255;
         this.g = rgb >> 8 & 255;
         this.b = rgb & 255;
         this.a = a;
         this.glow = glow;
         this.gravity = gravity;
         this.drag = drag;
      }
   }

   private record Rift(Vec3 start, Vec3 dir, int length, int[] age) {
   }

   private record Tail(Vec3 at, int life, int[] age) {
   }

   // ---------------------------------------------------------------- body anchors

   /** Height of the machine for anchor math (the hitbox, ~3.2). */
   private static double h(Player p) {
      return Math.max(1.8, p.getBbHeight());
   }

   private static Vec3 forward(Player p) {
      double yaw = Math.toRadians(p.yBodyRot);
      return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
   }

   private static Vec3 right(Player p) {
      Vec3 f = forward(p);
      return new Vec3(-f.z, 0.0, f.x);
   }

   private static Vec3 at(Player p, double side, double up, double fwd) {
      double s = h(p) / 3.2;
      return p.position().add(right(p).scale(side * s)).add(0.0, up * s, 0.0).add(forward(p).scale(fwd * s));
   }

   private static Vec3 rightFist(Player p) {
      return at(p, 0.95, 1.55, 0.9);
   }

   private static Vec3 leftFist(Player p) {
      return at(p, -0.95, 1.55, 0.9);
   }

   // ---------------------------------------------------------------- tick

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.isPaused()) {
         return;
      }

      frameTick++;
      // Age and move the old motes first, so a mote born this tick is drawn at least until the next one.
      MOTES.removeIf(m -> ++m.age >= m.life);
      for (Mote m : MOTES) {
         m.x += m.vx;
         m.y += m.vy;
         m.z += m.vz;
         m.vx *= m.drag;
         m.vz *= m.drag;
         m.vy = m.vy * m.drag - m.gravity;
      }

      List<Integer> seen = new ArrayList<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null) {
            continue;
         }

         seen.add(player.getId());
         Track track = TRACKS.computeIfAbsent(player.getId(), id -> new Track());
         HulkbusterLayer.Phase phase = HulkbusterView.phase(snapshot);
         if (!HulkbusterView.big(phase)) {
            track.stride = Integer.MIN_VALUE;
            continue;
         }

         tickPlayer(level, player, snapshot, track);
         cage(level, player, snapshot);
      }

      TRACKS.keySet().retainAll(seen);
      tickJobs(level);
   }

   private static void tickPlayer(ClientLevel level, Player player, HeroPublicSnapshot snapshot, Track track) {
      // Steps (stride from the walk distance, in step with the walk clip).
      int cycle = (int)Math.floor(player.walkAnimation.position() / (HulkbusterClips.STRIDE_UNITS / 2.0));
      if (track.stride != Integer.MIN_VALUE && track.stride != cycle && player.onGround() && player.walkAnimation.speed() > 0.15F) {
         boolean leftFoot = (cycle & 1) == 0;
         step(level, player, at(player, leftFoot ? -0.45 : 0.45, 0.0, 0.1), Math.min(1.6F, 0.7F + player.walkAnimation.speed()));
      }
      track.stride = cycle;

      HulkbusterMotion.State motion = HulkbusterView.motion(snapshot);
      int ticks = HulkbusterView.motionTicks(snapshot);
      HulkMove move = HulkbusterView.move(snapshot);
      MovePhase mp = HulkbusterView.movePhase(snapshot);
      int moveStep = HulkbusterView.step(snapshot);

      thrusters(level, player, motion, ticks, move, mp);
      if ((motion == HulkbusterMotion.State.DASH || motion == HulkbusterMotion.State.RAM) && player.onGround()) {
         skid(level, player, motion == HulkbusterMotion.State.RAM);
      }

      if (motion != track.motion && motion == HulkbusterMotion.State.LANDING) {
         crater(level, player.position(), track.motion == HulkbusterMotion.State.DIVE ? 2.0F : 1.2F);
      }

      boolean entered = move != null && (move != track.move || mp != track.phase || moveStep != track.step);
      if (entered && mp == MovePhase.MAIN) {
         contact(level, player, move, moveStep);
      }

      if (move == HulkMove.CHARGED && mp == MovePhase.INTRO) {
         elbow(player, HulkbusterView.chargeLevel(snapshot));
      }

      if (move == HulkMove.HEAVY_REPULSORS) {
         repulsors(level, player, mp);
      }

      if (move == HulkMove.JET_RAM && track.move == HulkMove.JET_RAM && track.phase == MovePhase.MAIN && mp == MovePhase.FINISH) {
         Vec3 hit = at(player, 0.0, 0.0, 1.6);
         crater(level, hit, 3.0F);
         TAILS.add(new Tail(hit, 120, new int[1]));
      }

      damage(player, HulkbusterView.damageStage(snapshot));
      track.motion = motion;
      track.move = move;
      track.phase = mp;
      track.step = moveStep;
   }

   private static void tickJobs(ClientLevel level) {
      Iterator<Rift> rifts = RIFTS.iterator();
      while (rifts.hasNext()) {
         Rift rift = rifts.next();
         int i = rift.age()[0]++;
         if (i >= rift.length()) {
            rifts.remove();
            continue;
         }

         Vec3 p = ground(level, rift.start().add(rift.dir().scale(1.0 + i)));
         eruption(level, p, 1.0F - 0.5F * i / (float)rift.length());
      }

      Iterator<Tail> tails = TAILS.iterator();
      while (tails.hasNext()) {
         Tail tail = tails.next();
         int i = tail.age()[0]++;
         if (i >= tail.life()) {
            tails.remove();
            continue;
         }

         float k = 1.0F - i / (float)tail.life();
         for (int n = 0; n < 3; n++) {
            Vec3 p = tail.at().add(gauss(1.6), 0.2 + RANDOM.nextDouble() * 0.6, gauss(1.6));
            add(new Mote(p, new Vec3(gauss(0.02), 0.05 + RANDOM.nextDouble() * 0.05, gauss(0.02)), 50 + RANDOM.nextInt(40), 0.25F, 0.9F,
               mix(0x2A2A2A, 0x6B6159, RANDOM.nextFloat()), (int)(150 * k), false, -0.001F, 0.96F));
         }

         if (RANDOM.nextFloat() < 0.6F * k) {
            Vec3 p = tail.at().add(gauss(1.2), 0.1, gauss(1.2));
            add(new Mote(p, new Vec3(gauss(0.03), 0.08 + RANDOM.nextDouble() * 0.06, gauss(0.03)), 30, 0.05F, 0.02F, 0xFF8A2A, 230, true, 0.0F,
               0.97F));
         }
      }
   }

   // ---------------------------------------------------------------- effects

   private static void step(ClientLevel level, Player player, Vec3 foot, float power) {
      int dust = colourUnder(level, foot);
      for (int i = 0; i < STEP_DUST; i++) {
         double a = RANDOM.nextDouble() * Math.PI * 2.0;
         double sp = (0.06 + RANDOM.nextDouble() * 0.09) * power;
         Vec3 v = new Vec3(Math.cos(a) * sp, 0.015 + RANDOM.nextDouble() * 0.04, Math.sin(a) * sp);
         add(new Mote(foot.add(Math.cos(a) * 0.35, 0.05, Math.sin(a) * 0.35), v, 18 + RANDOM.nextInt(18), 0.08F, 0.22F,
            shade(dust, 0.8F + 0.4F * RANDOM.nextFloat()), 190, false, -0.0015F, 0.9F));
      }

      for (int i = 0; i < STEP_CHUNKS; i++) {
         double a = RANDOM.nextDouble() * Math.PI * 2.0;
         Vec3 v = new Vec3(Math.cos(a) * 0.08, 0.16 + RANDOM.nextDouble() * 0.14, Math.sin(a) * 0.08).scale(power);
         add(new Mote(foot.add(0.0, 0.1, 0.0), v, 16 + RANDOM.nextInt(8), 0.06F, 0.05F, shade(dust, 0.55F), 255, false, 0.04F, 0.98F));
      }

      ring(foot.add(0.0, 0.04, 0.0), 0.5, 0.14 * power, 36, 10, shade(dust, 1.15F), 160, false);
      // Hydraulic steam from the knee pistons.
      Vec3 knee = foot.add(0.0, 1.0 * h(player) / 3.2, 0.0).add(forward(player).scale(0.25));
      for (int i = 0; i < 6; i++) {
         add(new Mote(knee, new Vec3(gauss(0.03), 0.02 + RANDOM.nextDouble() * 0.03, gauss(0.03)), 14, 0.06F, 0.18F, 0xDCE3E8, 120, false,
            -0.002F, 0.9F));
      }

      CameraShake.addAt(foot, 0.28F * power, 14.0);
   }

   private static void skid(ClientLevel level, Player player, boolean ram) {
      int dust = colourUnder(level, player.position());
      Vec3 back = forward(player).scale(-1.0);
      for (int side = -1; side <= 1; side += 2) {
         Vec3 foot = at(player, side * 0.45, 0.05, -0.1);
         for (int i = 0; i < 6; i++) {
            Vec3 v = back.scale(0.05 + RANDOM.nextDouble() * 0.08).add(gauss(0.04), 0.03 + RANDOM.nextDouble() * 0.06, gauss(0.04));
            add(new Mote(foot, v, 20 + RANDOM.nextInt(15), 0.1F, 0.3F, shade(dust, 0.9F), 170, false, -0.001F, 0.92F));
         }

         // The furrow: a line of settled dust behind each foot.
         add(new Mote(foot.add(gauss(0.05), -0.02, gauss(0.05)), Vec3.ZERO, 60, 0.12F, 0.1F, shade(dust, 0.6F), 200, false, 0.0F, 1.0F));
         if (RANDOM.nextFloat() < 0.7F) {
            Vec3 v = back.scale(0.15).add(gauss(0.08), 0.1 + RANDOM.nextDouble() * 0.1, gauss(0.08));
            add(new Mote(foot, v, 8 + RANDOM.nextInt(6), 0.035F, 0.01F, 0xFFD27A, 255, true, 0.03F, 0.95F));
         }
      }

      if (ram) {
         // Bow wave of dust in front of the shoulders.
         Vec3 front = at(player, 0.0, 0.3, 1.2);
         for (int i = 0; i < 10; i++) {
            Vec3 v = forward(player).scale(0.1).add(right(player).scale(gauss(0.15))).add(0.0, 0.05 + RANDOM.nextDouble() * 0.08, 0.0);
            add(new Mote(front.add(gauss(0.4), 0.0, gauss(0.4)), v, 20, 0.12F, 0.4F, shade(dust, 1.0F), 150, false, -0.001F, 0.9F));
         }
      }
   }

   /** Thruster plume intensity per motion state (0 = off) and whether the boots fire downward. */
   static float thrust(HulkbusterMotion.State motion, int ticks, @Nullable HulkMove move, MovePhase mp) {
      if (move == HulkMove.JET_RAM && mp != MovePhase.FINISH) {
         return mp == MovePhase.MAIN ? 1.4F : 0.6F;
      }

      return switch (motion) {
         case JET_CHARGE -> 0.25F + 0.5F * Math.min(1.0F, ticks / (float)HulkbusterRules.JET_CHARGE_MAX);
         case JET_RISE -> 1.3F;
         case HOVER -> 0.9F;
         case DASH -> 1.0F;
         case DIVE -> 1.1F;
         case RAM -> 0.5F;
         default -> 0.0F;
      };
   }

   private static void thrusters(ClientLevel level, Player player, HulkbusterMotion.State motion, int ticks, @Nullable HulkMove move, MovePhase mp) {
      float k = thrust(motion, ticks, move, mp);
      if (k <= 0.0F) {
         return;
      }

      boolean horizontal = motion == HulkbusterMotion.State.DASH || motion == HulkbusterMotion.State.RAM || move == HulkMove.JET_RAM;
      Vec3 back = forward(player).scale(-1.0);
      Vec3 bootDir = horizontal ? back.add(0.0, -0.4, 0.0).normalize() : new Vec3(0.0, -1.0, 0.0);
      Vec3 backDir = horizontal ? back : back.add(0.0, -0.8, 0.0).normalize();
      for (int side = -1; side <= 1; side += 2) {
         plume(level, at(player, side * 0.42, 0.05, -0.05), bootDir, k);
         plume(level, at(player, side * 0.38, 2.15, -0.6), backDir, k * 0.85F);
      }
   }

   private static void plume(ClientLevel level, Vec3 nozzle, Vec3 dir, float k) {
      int n = Math.round(6 * k) + 2;
      for (int i = 0; i < n; i++) {
         double sp = (0.35 + RANDOM.nextDouble() * 0.25) * k;
         Vec3 v = dir.scale(sp).add(gauss(0.03), gauss(0.03), gauss(0.03));
         add(new Mote(nozzle, v, 4 + RANDOM.nextInt(3), 0.11F * k + 0.05F, 0.03F, 0xF4FBFF, 255, true, 0.0F, 0.8F));
         add(new Mote(nozzle, v.scale(0.85).add(gauss(0.05), gauss(0.05), gauss(0.05)), 7 + RANDOM.nextInt(5), 0.14F * k + 0.06F, 0.06F,
            mix(0xFF9A2E, 0xFFD36B, RANDOM.nextFloat()), 220, true, 0.0F, 0.85F));
      }

      if (RANDOM.nextFloat() < 0.5F * k) {
         Vec3 v = dir.scale(0.12 * k).add(gauss(0.02), 0.02, gauss(0.02));
         add(new Mote(nozzle.add(dir.scale(0.8)), v, 30 + RANDOM.nextInt(20), 0.15F, 0.6F, mix(0x5A5A5A, 0x8C8C8C, RANDOM.nextFloat()), 110,
            false, -0.002F, 0.94F));
      }

      // Heat shimmer: faint pale motes trailing the flame.
      add(new Mote(nozzle.add(dir.scale(1.0)), dir.scale(0.05).add(gauss(0.02), 0.03, gauss(0.02)), 12, 0.2F, 0.35F, 0xFFF1D6, 28, true,
         -0.002F, 0.9F));
      // A plume that reaches the floor scorches it.
      if (dir.y < -0.3) {
         Vec3 floor = ground(level, nozzle);
         double drop = nozzle.y - floor.y;
         if (drop < 4.0 * k && RANDOM.nextFloat() < 0.6F) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            add(new Mote(floor.add(0.0, 0.05, 0.0), new Vec3(Math.cos(a) * 0.25, 0.04, Math.sin(a) * 0.25), 10, 0.05F, 0.02F, 0xFFB24A, 255, true,
               0.01F, 0.9F));
            add(new Mote(floor.add(0.0, 0.05, 0.0), new Vec3(Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12), 25, 0.15F, 0.45F,
               shade(colourUnder(level, floor), 0.9F), 120, false, -0.001F, 0.9F));
         }
      }
   }

   private static void contact(ClientLevel level, Player player, HulkMove move, int moveStep) {
      switch (move) {
         case COMBO, CHARGED, COUNTER, DASH_STRIKE -> {
            boolean jackhammer = move == HulkMove.COMBO && moveStep == 2 || move == HulkMove.COUNTER;
            Vec3 fist = jackhammer || move == HulkMove.CHARGED ? rightFist(player) : moveStep == 1 ? leftFist(player) : rightFist(player);
            impact(fist, move == HulkMove.CHARGED ? 1.8F : jackhammer ? 1.3F : 1.0F, forward(player));
            if (jackhammer) {
               piston(fist, forward(player));
            }
         }
         case GRAB_PUNCH, GRAB_FINISH -> {
            Vec3 fist = rightFist(player).add(0.0, 0.4, 0.0);
            impact(fist, move == HulkMove.GRAB_FINISH ? 2.2F : 0.9F + 0.1F * moveStep, forward(player));
            piston(fist, forward(player));
         }
         case GRAB_SLAM, HERO_LANDING -> crater(level, at(player, 0.0, 0.0, move == HulkMove.GRAB_SLAM ? 1.4 : 0.0), 1.6F);
         case SEISMIC_RIFT -> {
            Vec3 start = at(player, 0.0, 0.0, 1.2);
            RIFTS.add(new Rift(start, forward(player), 14, new int[1]));
            crater(level, start, 1.0F);
         }
         default -> {
         }
      }
   }

   /** Fist contact: white flash core, spark spray, hot metal shavings, an impact ring facing the hit. */
   private static void impact(Vec3 at, float power, Vec3 dir) {
      for (int i = 0; i < Math.round(30 * power); i++) {
         Vec3 v = dir.scale(0.1 + RANDOM.nextDouble() * 0.25).add(gauss(0.25), 0.05 + gauss(0.2), gauss(0.25)).scale(power);
         add(new Mote(at, v, 6 + RANDOM.nextInt(10), 0.04F, 0.01F, mix(0xFFE9A8, 0xFFFFFF, RANDOM.nextFloat()), 255, true, 0.03F, 0.92F));
      }

      for (int i = 0; i < Math.round(12 * power); i++) {
         Vec3 v = dir.scale(0.05).add(gauss(0.15), 0.12 + RANDOM.nextDouble() * 0.15, gauss(0.15));
         add(new Mote(at, v, 20 + RANDOM.nextInt(12), 0.035F, 0.03F, mix(0x9AA0A6, 0xD5D9DD, RANDOM.nextFloat()), 255, false, 0.035F, 0.97F));
      }

      glowBurst(at, 0.35 * power, 0xFFFFFF, 0xFFC56B, Math.round(26 * power));
      ring(at, 0.2, 0.2 * power, 28, 7, 0xFFF0C8, 200, true);
      CameraShake.addAt(at, 0.5F * power, 20.0);
   }

   /** Jackhammer piston: a rapid burst of sparks pushed out of the forearm. */
   private static void piston(Vec3 at, Vec3 dir) {
      for (int burst = 0; burst < 3; burst++) {
         Vec3 p = at.subtract(dir.scale(0.4 + 0.15 * burst));
         for (int i = 0; i < 8; i++) {
            Vec3 v = dir.scale(-0.05).add(gauss(0.18), 0.1 + RANDOM.nextDouble() * 0.12, gauss(0.18));
            add(new Mote(p, v, 5 + RANDOM.nextInt(6), 0.03F, 0.01F, 0xFFCF6A, 255, true, 0.03F, 0.92F));
         }
         add(new Mote(p, new Vec3(gauss(0.02), 0.04, gauss(0.02)), 12, 0.08F, 0.22F, 0xE4E8EC, 110, false, -0.002F, 0.9F));
      }
   }

   private static void elbow(Player player, int charge) {
      float k = 0.4F + 0.3F * Math.min(3, charge);
      Vec3 elbow = at(player, 1.05, 1.8, -0.45);
      Vec3 dir = forward(player).scale(-1.0).add(0.0, 0.2, 0.0).normalize();
      for (int i = 0; i < Math.round(5 * k); i++) {
         Vec3 v = dir.scale(0.25 * k).add(gauss(0.03), gauss(0.03), gauss(0.03));
         add(new Mote(elbow, v, 4 + RANDOM.nextInt(3), 0.08F * k + 0.04F, 0.02F, i % 2 == 0 ? 0xF4FBFF : 0xFFA640, 255, true, 0.0F, 0.82F));
      }
   }

   private static void repulsors(ClientLevel level, Player player, MovePhase mp) {
      Vec3 look = player.getLookAngle();
      Vec3[] palms = {leftFist(player).add(look.scale(0.3)), rightFist(player).add(look.scale(0.3))};
      if (mp == MovePhase.INTRO) {
         for (Vec3 palm : palms) {
            for (int i = 0; i < 4; i++) {
               Vec3 from = palm.add(gauss(1.0), gauss(1.0), gauss(1.0));
               add(new Mote(from, palm.subtract(from).scale(0.12), 8, 0.05F, 0.02F, 0x9FE6FF, 230, true, 0.0F, 1.0F));
            }
            glowBurst(palm, 0.12, 0xFFFFFF, 0xBDF1FF, 5);
         }
         return;
      }

      if (mp != MovePhase.MAIN) {
         return;
      }

      for (Vec3 palm : palms) {
         Vec3 end = palm.add(look.scale(28.0));
         var hit = level.clip(new net.minecraft.world.level.ClipContext(palm, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
         Vec3 stop = hit.getLocation();
         double len = palm.distanceTo(stop);
         for (double d = 0.0; d < len; d += 0.35) {
            Vec3 p = palm.add(look.scale(d));
            add(new Mote(p, Vec3.ZERO, 2, 0.16F, 0.12F, 0xFFFFFF, 255, true, 0.0F, 1.0F));
            add(new Mote(p.add(gauss(0.08), gauss(0.08), gauss(0.08)), look.scale(0.05), 4, 0.32F, 0.2F, 0x56CFFF, 120, true, 0.0F, 1.0F));
         }

         glowBurst(palm, 0.25, 0xFFFFFF, 0xD8F6FF, 10);
         for (int i = 0; i < 6; i++) {
            add(new Mote(stop, new Vec3(gauss(0.2), 0.05 + RANDOM.nextDouble() * 0.2, gauss(0.2)), 10, 0.05F, 0.01F, 0xBDF1FF, 255, true, 0.02F,
               0.92F));
         }
         glowBurst(stop, 0.4, 0xD8F6FF, 0x8FE0FF, 14);
      }

      CameraShake.addAt(player.position(), 0.25F, 10.0);
   }

   private static void eruption(ClientLevel level, Vec3 p, float power) {
      int dust = colourUnder(level, p);
      for (int i = 0; i < Math.round(20 * power); i++) {
         Vec3 v = new Vec3(gauss(0.08), 0.25 + RANDOM.nextDouble() * 0.35, gauss(0.08)).scale(power);
         add(new Mote(p.add(gauss(0.5), 0.1, gauss(0.5)), v, 18 + RANDOM.nextInt(10), 0.08F, 0.07F, shade(dust, 0.6F + 0.3F * RANDOM.nextFloat()),
            255, false, 0.04F, 0.98F));
      }

      for (int i = 0; i < Math.round(16 * power); i++) {
         Vec3 v = new Vec3(gauss(0.06), 0.06 + RANDOM.nextDouble() * 0.1, gauss(0.06));
         add(new Mote(p.add(gauss(0.6), 0.2, gauss(0.6)), v, 30 + RANDOM.nextInt(20), 0.2F, 0.7F, shade(dust, 1.0F), 150, false, -0.001F, 0.93F));
      }

      ring(p.add(0.0, 0.05, 0.0), 0.3, 0.16 * power, 24, 8, shade(dust, 1.2F), 170, false);
      CameraShake.addAt(p, 0.6F * power, 18.0);
   }

   /** Heavy landing / slam / Jet Ram: a wide dust ring, chunks, rising smoke. */
   private static void crater(ClientLevel level, Vec3 at, float power) {
      int dust = colourUnder(level, at);
      ring(at.add(0.0, 0.05, 0.0), 0.8, 0.22 * power, 64, 14, shade(dust, 1.1F), 200, false);
      ring(at.add(0.0, 0.2, 0.0), 0.6, 0.32 * power, 48, 6, 0xFFF3DA, 160, true);
      for (int i = 0; i < Math.round(60 * power); i++) {
         double a = RANDOM.nextDouble() * Math.PI * 2.0;
         double sp = (0.08 + RANDOM.nextDouble() * 0.2) * power;
         Vec3 v = new Vec3(Math.cos(a) * sp, 0.18 + RANDOM.nextDouble() * 0.3, Math.sin(a) * sp);
         add(new Mote(at.add(0.0, 0.1, 0.0), v, 20 + RANDOM.nextInt(15), 0.07F, 0.06F, shade(dust, 0.5F + 0.3F * RANDOM.nextFloat()), 255, false,
            0.045F, 0.98F));
      }

      for (int i = 0; i < Math.round(50 * power); i++) {
         double a = RANDOM.nextDouble() * Math.PI * 2.0;
         Vec3 v = new Vec3(Math.cos(a) * 0.1 * power, 0.03 + RANDOM.nextDouble() * 0.08, Math.sin(a) * 0.1 * power);
         add(new Mote(at.add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), v, 50 + RANDOM.nextInt(40), 0.25F, 1.0F,
            shade(dust, 0.9F + 0.2F * RANDOM.nextFloat()), 150, false, -0.001F, 0.95F));
      }

      CameraShake.addAt(at, 0.9F * power, 28.0);
   }

   /** Damage stages: 1 = sparks and a thin wisp, 2 = thick black smoke, spark showers, a flickering reactor. */
   private static void damage(Player player, int stage) {
      if (stage <= 0) {
         return;
      }

      Vec3[] joints = {at(player, 0.95, 2.6, 0.0), at(player, -0.95, 2.6, 0.0), at(player, 1.05, 1.8, -0.2), at(player, -1.05, 1.8, -0.2),
         at(player, 0.4, 1.1, 0.2), at(player, -0.4, 1.1, 0.2)};
      int every = stage >= 2 ? 4 : 14;
      if (frameTick % every == player.getId() % every) {
         Vec3 j = joints[RANDOM.nextInt(joints.length)];
         for (int i = 0; i < (stage >= 2 ? 18 : 8); i++) {
            Vec3 v = new Vec3(gauss(0.12), 0.05 + RANDOM.nextDouble() * 0.15, gauss(0.12));
            add(new Mote(j, v, 6 + RANDOM.nextInt(10), 0.03F, 0.01F, 0xFFE07A, 255, true, 0.035F, 0.93F));
         }
      }

      Vec3 wisp = at(player, 0.7, 2.9, -0.3);
      add(new Mote(wisp, new Vec3(gauss(0.01), 0.05, gauss(0.01)), 30, 0.1F, 0.4F, stage >= 2 ? 0x222222 : 0x7A7A7A, stage >= 2 ? 170 : 90, false,
         -0.001F, 0.96F));
      if (stage >= 2) {
         Vec3 second = at(player, -0.5, 2.0, 0.5);
         for (int i = 0; i < 2; i++) {
            add(new Mote(second.add(gauss(0.1), 0.0, gauss(0.1)), new Vec3(gauss(0.015), 0.06, gauss(0.015)), 45, 0.15F, 0.8F, 0x1A1A1A, 180,
               false, -0.002F, 0.96F));
         }

         if (RANDOM.nextFloat() < 0.35F) {
            glowBurst(at(player, 0.0, 2.25, 0.62), 0.12, 0xFFFFFF, 0x9FE6FF, 6);
         }

         if (RANDOM.nextFloat() < 0.3F) {
            add(new Mote(second, new Vec3(gauss(0.02), 0.04, gauss(0.02)), 14, 0.06F, 0.02F, 0xFF7A1F, 240, true, -0.001F, 0.95F));
         }
      }
   }

   /** Veronica's cage (actionTarget = cage point, HulkbusterWire.CAGE = phase + ticks). */
   private static void cage(ClientLevel level, Player player, HeroPublicSnapshot snapshot) {
      int wire = HulkbusterView.extra(snapshot, HulkbusterWire.CAGE);
      Vec3 point = snapshot.actionTarget();
      if (wire == 0 || point == null) {
         return;
      }

      int phase = (wire >> 12) - 1;
      int ticks = wire & 4095;
      if (phase == HulkbusterCage.Phase.MARKED.ordinal()) {
         float k = Math.min(1.0F, ticks / (float)HulkbusterRules.CAGE_DROP_DELAY);
         ring(point.add(0.0, 0.06, 0.0), HulkbusterCage.HALF * 1.4, 0.0, 40, 2, 0xFF4040, 200, true);
         // The shadow grows as the cage comes down.
         for (int i = 0; i < 12; i++) {
            Vec3 p = point.add((RANDOM.nextDouble() * 2 - 1) * HulkbusterCage.HALF * k, 0.04, (RANDOM.nextDouble() * 2 - 1) * HulkbusterCage.HALF * k);
            add(new Mote(p, Vec3.ZERO, 2, 0.25F, 0.25F, 0x101010, (int)(90 * k), false, 0.0F, 1.0F));
         }

         double fall = 40.0 * (1.0 - k) * (1.0 - k);
         bars(point.add(0.0, fall, 0.0), 1.0F, frameTick);
         Vec3 base = point.add(0.0, fall, 0.0);
         for (int i = 0; i < 3; i++) {
            add(new Mote(base.add(gauss(0.6), 0.0, gauss(0.6)), new Vec3(gauss(0.02), 0.15, gauss(0.02)), 10, 0.06F, 0.02F, 0xFFB24A, 230, true, 0.0F,
               0.9F));
         }

         if (ticks + 1 >= HulkbusterRules.CAGE_DROP_DELAY) {
            crater(level, point, 1.2F);
         }
      } else if (phase == HulkbusterCage.Phase.HOLDING.ordinal()) {
         bars(point, 1.0F, frameTick);
         if (frameTick % 3 == 0) {
            Vec3 p = point.add(gauss(1.0), RANDOM.nextDouble() * HulkbusterCage.HEIGHT, gauss(1.0));
            add(new Mote(p, Vec3.ZERO, 6, 0.08F, 0.01F, 0x9FE6FF, 220, true, 0.0F, 1.0F));
         }
      } else if (phase == HulkbusterCage.Phase.EMPTY.ordinal()) {
         bars(point, Math.max(0.0F, 1.0F - ticks / (float)HulkbusterRules.CAGE_EMPTY_LINGER), frameTick);
      }
   }

   /** One frame of cage geometry as short-lived motes: 4 corner posts, bars every 0.5 block, top/bottom frames. */
   private static void bars(Vec3 base, float alpha, long time) {
      double half = HulkbusterCage.HALF;
      double height = HulkbusterCage.HEIGHT;
      int a = (int)(230 * alpha);
      if (a <= 4) {
         return;
      }

      for (double s = -half; s <= half + 1e-3; s += 0.5) {
         for (Vec3 foot : new Vec3[]{new Vec3(s, 0, -half), new Vec3(s, 0, half), new Vec3(-half, 0, s), new Vec3(half, 0, s)}) {
            boolean post = Math.abs(Math.abs(foot.x) - half) < 1e-3 && Math.abs(Math.abs(foot.z) - half) < 1e-3;
            for (double y = 0.0; y <= height; y += 0.25) {
               add(new Mote(base.add(foot.x, y, foot.z), Vec3.ZERO, 1, post ? 0.07F : 0.04F, post ? 0.07F : 0.04F, post ? 0xB0B5BA : 0x8C9399, a, false,
                  0.0F, 1.0F));
            }
         }
      }

      for (double y : new double[]{0.05, height}) {
         ring(base.add(0.0, y, 0.0), half, 0.0, 0, 1, 0x666C72, a, false);
         for (double s = -half; s <= half; s += 0.2) {
            for (Vec3 p : new Vec3[]{new Vec3(s, y, -half), new Vec3(s, y, half), new Vec3(-half, y, s), new Vec3(half, y, s)}) {
               add(new Mote(base.add(p), Vec3.ZERO, 1, 0.06F, 0.06F, 0x5C6268, a, false, 0.0F, 1.0F));
            }
         }
      }

      // Energy shimmer travelling up the bars.
      double band = (time % 30) / 30.0 * height;
      for (double s = -half; s <= half; s += 0.25) {
         for (Vec3 p : new Vec3[]{new Vec3(s, band, -half), new Vec3(s, band, half), new Vec3(-half, band, s), new Vec3(half, band, s)}) {
            add(new Mote(base.add(p), Vec3.ZERO, 1, 0.09F, 0.09F, 0x7FD8FF, (int)(160 * alpha), true, 0.0F, 1.0F));
         }
      }
   }

   // ---------------------------------------------------------------- helpers

   /**
    * A soft flash: a cluster of small additive motes that bloom outward and fade, never one big flat
    * square (the pixel pipeline draws hard-edged quads; in game a single 1.4-block square read as a cheap
    * white panel, playtest 2026-10-28).
    */
   private static void glowBurst(Vec3 at, double radius, int core, int rim, int count) {
      for (int i = 0; i < count; i++) {
         Vec3 dir = new Vec3(gauss(1.0), gauss(1.0), gauss(1.0)).normalize();
         double r = radius * Math.sqrt(RANDOM.nextDouble());
         add(new Mote(at.add(dir.scale(r * 0.3)), dir.scale(r * 0.25), 3 + RANDOM.nextInt(3), 0.07F + 0.06F * RANDOM.nextFloat(), 0.02F,
            i % 3 == 0 ? core : rim, 230, true, 0.0F, 0.8F));
      }
   }

   /** A flat ring of motes expanding outward (count 0 = a static outline of radius {@code radius}). */
   private static void ring(Vec3 c, double radius, double speed, int count, int life, int rgb, int a, boolean glow) {
      int n = count > 0 ? count : 32;
      for (int i = 0; i < n; i++) {
         double ang = i * Math.PI * 2.0 / n;
         Vec3 dir = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
         add(new Mote(c.add(dir.scale(radius)), dir.scale(speed), life, 0.07F, 0.12F, rgb, a, glow, 0.0F, 0.88F));
      }
   }

   private static Vec3 ground(ClientLevel level, Vec3 p) {
      BlockPos pos = BlockPos.containing(p);
      for (int i = 0; i < 6 && level.getBlockState(pos.below()).isAir(); i++) {
         pos = pos.below();
      }

      return new Vec3(p.x, pos.getY(), p.z);
   }

   private static int colourUnder(ClientLevel level, Vec3 p) {
      BlockPos pos = BlockPos.containing(p.x, p.y - 0.2, p.z);
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
         return 0xA08C6E;
      }

      MapColor colour = state.getMapColor(level, pos);
      return colour == null || colour.col == 0 ? 0xA08C6E : mix(colour.col, 0xB9A88C, 0.35F);
   }

   private static int shade(int rgb, float k) {
      int r = Mth.clamp((int)((rgb >> 16 & 255) * k), 0, 255);
      int g = Mth.clamp((int)((rgb >> 8 & 255) * k), 0, 255);
      int b = Mth.clamp((int)((rgb & 255) * k), 0, 255);
      return r << 16 | g << 8 | b;
   }

   static int mix(int a, int b, float t) {
      int r = (int)Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
      int g = (int)Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
      int bl = (int)Mth.lerp(t, a & 255, b & 255);
      return r << 16 | g << 8 | bl;
   }

   private static double gauss(double s) {
      return RANDOM.nextGaussian() * s;
   }

   private static void add(Mote mote) {
      if (MOTES.size() >= MAX_MOTES) {
         MOTES.remove(0);
      }

      MOTES.add(mote);
   }

   /** Live motes (tests, debug). */
   public static int motes() {
      return MOTES.size();
   }

   // ---------------------------------------------------------------- render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MOTES.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      float partial = event.getPartialTick();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.levelView(modelViewStack, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         // Dust and smoke first (alpha blend), then everything hot on top (additive).
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
         draw(buffer, tessellator, camera, cameraPos, partial, false);
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         draw(buffer, tessellator, camera, cameraPos, partial, true);
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void draw(BufferBuilder buffer, Tesselator tessellator, Camera camera, Vec3 cameraPos, float partial, boolean glow) {
      buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      AABB near = new AABB(cameraPos, cameraPos).inflate(160.0);
      for (Mote m : MOTES) {
         if (m.glow != glow) {
            continue;
         }

         Vec3 p = new Vec3(m.x + m.vx * partial, m.y + m.vy * partial, m.z + m.vz * partial);
         if (!near.contains(p)) {
            continue;
         }

         float t = (m.age + partial) / m.life;
         float size = Mth.lerp(t, m.size0, m.size1);
         int alpha = (int)(m.a * (1.0F - t * t));
         if (alpha > 2) {
            PixelVfx.billboardPixel(buffer, cameraPos, camera, p, size, m.r, m.g, m.b, alpha);
         }
      }

      tessellator.end();
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      MOTES.clear();
      RIFTS.clear();
      TAILS.clear();
      TRACKS.clear();
   }
}
