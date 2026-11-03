package dev.baranhan.viltrumitecore.client.city.life;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The living street around the listener (issue #136), client side and from what is really there: every
 * {@link #SCAN_EVERY} ticks the citizens within {@link #SCAN} blocks are weighed by distance and state.
 * <p><b>Beds</b> (loops that fade toward a target): the murmur of the calm crowd (mono, placed toward where the
 * people are, so it comes from the right side), a thinner murmur of people further down the streets, footsteps
 * of the walkers, running feet and a crowd in panic (toward the panic), the distant city by day and by night,
 * the wind between the buildings (stronger up high and near ruins), and the stunned hush after a disaster.
 * Indoors everything is quieter, high over the street the people fade and the wind rises.
 * <p><b>Details</b> (one-shots at real places): pigeons taking off (and when a panic starts), a bicycle bell,
 * laughter from a calm group of citizens, distant church bells in the morning and at noon, dogs at night and in
 * the aftermath.
 * <p><b>Memory</b>: panics and ruins nearby raise a client "trauma" that fades with a two-minute half-life;
 * it hushes the murmur and the streets, so the city comes back to life step by step.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CityLifeAmbience {
   static final double SCAN = 64.0;
   static final int SCAN_EVERY = 10;
   static final float FADE_UP = 0.02F;
   static final float FADE_DOWN = 0.035F;
   static final int RETRY = 20;
   static final String[] BEDS = {"crowd", "crowd_far", "steps", "running", "street_day", "street_night", "wind", "panic", "hush"};
   /**
    * Beds that are mono and placed toward their people (the rest are wide stereo around the listener). A placed
    * bed plays as two layers of the same loop: one from the side of its people, one centred, split by how
    * one-sided they are ({@link CityLifeMixer#sideShare}) - a single placed source would be hard-panned.
    */
   static final boolean[] PLACED = {true, true, true, true, false, false, false, true, false};
   static final int CROWD = 0;
   static final int STEPS = 2;
   static final int RUNNING = 3;
   static final int PANIC = 7;

   private static final Loop[] LOOPS = new Loop[BEDS.length];
   /** The centred layer of the placed beds. */
   private static final Loop[] WIDE = new Loop[BEDS.length];
   /** Side share of each placed bed (0 = all centred). */
   private static final float[] SIDE = new float[BEDS.length];
   private static final float[] TARGET = new float[BEDS.length];
   private static final Vec3[] AT = new Vec3[BEDS.length];
   private static final int[] RETRIES = new int[BEDS.length];
   private static final SoundEvent[] EVENTS = new SoundEvent[BEDS.length];
   private static final RandomSource RANDOM = RandomSource.create();
   private static float trauma;
   private static float lastPanic;
   private static double medianY = Double.NaN;
   private static boolean indoor;
   private static double above;
   private static int clock;
   private static int citizens;
   private static final int[] cooldown = new int[6];
   /** The cooldown slot shared by all details. */
   private static final int GAP = 5;
   private static int details;
   @Nullable
   private static ClientLevel lastLevel;
   private static final List<CitizenEntity> calmNear = new ArrayList<>();

   private CityLifeAmbience() {
   }

   private static SoundEvent event(String path) {
      return SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", path));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != lastLevel) {
         stopAll();
         lastLevel = level;
      }

      if (level == null || mc.player == null || mc.isPaused() || mc.gameRenderer == null) {
         return;
      }

      clock++;
      if (clock % SCAN_EVERY != 0) {
         return;
      }

      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      scan(mc, level, ear);
      CityLifeVoices.housekeeping(level.getGameTime());
      for (int i = 0; i < BEDS.length; i++) {
         if (TARGET[i] > 0.04F && !alive(LOOPS[i]) && --RETRIES[i] <= 0) {
            if (LOOPS[i] != null) {
               LOOPS[i].halt();
            }

            if (EVENTS[i] == null) {
               EVENTS[i] = event("city_life.bed." + BEDS[i]);
            }

            final int k = i;
            LOOPS[i] = PLACED[i] ? new Loop(EVENTS[i], () -> CityLifeMixer.layer(TARGET[k], SIDE[k], true), () -> AT[k], false)
               : new Loop(EVENTS[i], () -> TARGET[k], () -> AT[k], true);
            RETRIES[i] = RETRY / SCAN_EVERY;
            mc.getSoundManager().play(LOOPS[i]);
         }

         if (PLACED[i] && TARGET[i] > 0.04F && !alive(WIDE[i]) && alive(LOOPS[i])) {
            if (WIDE[i] != null) {
               WIDE[i].halt();
            }

            final int k = i;
            WIDE[i] = new Loop(EVENTS[i], () -> CityLifeMixer.layer(TARGET[k], SIDE[k], false), () -> null, true);
            mc.getSoundManager().play(WIDE[i]);
         }
      }

      details(mc, level, ear);
   }

   private static void scan(Minecraft mc, ClientLevel level, Vec3 ear) {
      float calm = 0.0F;
      float far = 0.0F;
      float walking = 0.0F;
      float running = 0.0F;
      float panic = 0.0F;
      double cx = 0.0;
      double cy = 0.0;
      double cz = 0.0;
      double px = 0.0;
      double py = 0.0;
      double pz = 0.0;
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      double rx = 0.0;
      double ry = 0.0;
      double rz = 0.0;
      // sums of the weighted unit directions per placed group (calm, walking, running, panic)
      double[] ux = new double[4];
      double[] uz = new double[4];
      calmNear.clear();
      List<CitizenEntity> all = level.getEntitiesOfClass(CitizenEntity.class, new AABB(ear, ear).inflate(SCAN, 32.0, SCAN));
      double[] ys = new double[all.size()];
      int n = 0;
      for (CitizenEntity c : all) {
         double d = Math.sqrt(c.distanceToSqr(ear));
         if (d > SCAN) {
            continue;
         }

         ys[n++] = c.getY();
         double hx = c.getX() - ear.x;
         double hz = c.getZ() - ear.z;
         double hd = Math.sqrt(hx * hx + hz * hz);
         hx = hd > 0.5 ? hx / hd : 0.0;
         hz = hd > 0.5 ? hz / hd : 0.0;
         CitizenState s = c.citizenState();
         if (s.panicked()) {
            float w = CityLifeMixer.weight(d, 48.0);
            panic += w;
            px += c.getX() * w;
            py += c.getY() * w;
            pz += c.getZ() * w;
            ux[3] += hx * w;
            uz[3] += hz * w;
            if (s == CitizenState.FLEEING) {
               float r = CityLifeMixer.weight(d, 28.0);
               running += r;
               rx += c.getX() * r;
               ry += c.getY() * r;
               rz += c.getZ() * r;
               ux[2] += hx * r;
               uz[2] += hz * r;
            }
         } else {
            float w = CityLifeMixer.weight(d, 24.0) * (s == CitizenState.TALKING ? 1.4F : s == CitizenState.ALERT ? 0.4F : 1.0F);
            calm += w;
            cx += c.getX() * w;
            cy += c.getY() * w;
            cz += c.getZ() * w;
            ux[0] += hx * w;
            uz[0] += hz * w;
            if (d > 20.0) {
               far += CityLifeMixer.weight(d - 20.0, 44.0);
            }

            if (s == CitizenState.WALKING || s == CitizenState.RECOVERING) {
               float st = CityLifeMixer.weight(d, 18.0);
               walking += st;
               sx += c.getX() * st;
               sy += c.getY() * st;
               sz += c.getZ() * st;
               ux[1] += hx * st;
               uz[1] += hz * st;
            }

            if (d < 20.0 && (s == CitizenState.TALKING || s == CitizenState.IDLE)) {
               calmNear.add(c);
            }
         }
      }

      citizens = n;
      if (n > 0) {
         Arrays.sort(ys, 0, n);
         double m = ys[n / 2];
         medianY = Double.isNaN(medianY) ? m : medianY + (m - medianY) * 0.3;
      }

      above = Double.isNaN(medianY) ? 0.0 : Math.max(0.0, ear.y - 1.6 - medianY);
      indoor = indoor(level, ear);
      float ruin = dev.baranhan.viltrumitecore.client.aftermath.AftermathClient.ruinNear();
      float p = CityLifeMixer.amount(panic, 3.0F);
      trauma = CityLifeMixer.trauma(trauma, SCAN_EVERY, Math.max(p * 0.9F, ruin * 0.8F));
      float[] t = CityLifeMixer.beds(calm, far, walking, running, panic, trauma, CityLifeMixer.daylight(level.getDayTime()), level.isRaining(), indoor,
         above, ruin);
      System.arraycopy(t, 0, TARGET, 0, t.length);
      AT[CROWD] = toward(ear, calm, cx, cy, cz, 5.0);
      AT[1] = AT[CROWD];
      AT[STEPS] = toward(ear, walking, sx, sy, sz, 4.0);
      AT[RUNNING] = toward(ear, running, rx, ry, rz, 4.0);
      AT[PANIC] = toward(ear, panic, px, py, pz, 7.0);
      SIDE[CROWD] = side(calm, ux[0], uz[0]);
      // the far murmur is the whole street around: mostly centred
      SIDE[1] = SIDE[CROWD] * 0.5F;
      SIDE[STEPS] = side(walking, ux[1], uz[1]);
      SIDE[RUNNING] = side(running, ux[2], uz[2]);
      SIDE[PANIC] = side(panic, ux[3], uz[3]);
      for (int i = 0; i < AT.length; i++) {
         if (AT[i] == null || !PLACED[i]) {
            AT[i] = ear;
         }
      }

      // a panic starting nearby sends the pigeons up
      if (p > 0.3F && lastPanic <= 0.3F && level.getDayTime() % 24000L < 12500L) {
         oneShot(mc, "city_life.pigeons", around(ear, 6.0, 14.0, 5.0), 1.0F, 0.95F + RANDOM.nextFloat() * 0.1F);
      }

      lastPanic = p;
   }

   private static float side(float w, double ux, double uz) {
      return w <= 1.0E-4F ? 0.0F : CityLifeMixer.sideShare(Math.sqrt(ux * ux + uz * uz) / w);
   }

   /** The weighted centre of a group, brought to within {@code keep} blocks of the ear (direction kept). */
   @Nullable
   static Vec3 toward(Vec3 ear, float w, double x, double y, double z, double keep) {
      if (w <= 1.0E-4F) {
         return null;
      }

      Vec3 c = new Vec3(x / w, y / w + 1.2, z / w);
      double d = c.distanceTo(ear);
      if (d < 1.0E-3) {
         return ear;
      }

      double k = Math.min(d, keep) / d;
      return ear.add(c.subtract(ear).scale(k));
   }

   /** Under a roof with walls around (three of four horizontal rays hit within 8 blocks). */
   private static boolean indoor(ClientLevel level, Vec3 ear) {
      if (level.canSeeSky(BlockPos.containing(ear))) {
         return false;
      }

      int walls = 0;
      Minecraft mc = Minecraft.getInstance();
      for (int i = 0; i < 4; i++) {
         double a = i * Math.PI / 2.0;
         Vec3 to = ear.add(Math.cos(a) * 8.0, 0.0, Math.sin(a) * 8.0);
         if (level.clip(new ClipContext(ear, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) {
            walls++;
         }
      }

      return walls >= 3;
   }

   private static Vec3 around(Vec3 ear, double min, double max, double up) {
      double a = RANDOM.nextDouble() * Math.PI * 2.0;
      double r = min + RANDOM.nextDouble() * (max - min);
      return new Vec3(ear.x + Math.cos(a) * r, ear.y + up * RANDOM.nextDouble(), ear.z + Math.sin(a) * r);
   }

   private static void oneShot(Minecraft mc, String path, Vec3 at, float volume, float pitch) {
      mc.getSoundManager().play(new SimpleSoundInstance(new ResourceLocation("viltrumitecore", path), SoundSource.AMBIENT, volume, pitch, RANDOM, false, 0,
         SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false));
      details++;
      // a breath between any two details: walking into a busy street does not set off pigeons, a bell and laughter at once
      cooldown[GAP] = 16 + RANDOM.nextInt(24);
   }

   /** Street details at real places, each with its own cooldown (in scans). */
   private static void details(Minecraft mc, ClientLevel level, Vec3 ear) {
      for (int i = 0; i < cooldown.length; i++) {
         if (cooldown[i] > 0) {
            cooldown[i]--;
         }
      }

      if (indoor || cooldown[GAP] > 0) {
         return;
      }

      float day = CityLifeMixer.daylight(level.getDayTime());
      float crowd = TARGET[CROWD];
      boolean calmStreet = TARGET[PANIC] < 0.05F && trauma < 0.25F;
      int time = (int)(level.getDayTime() % 24000L);
      double street = Double.isNaN(medianY) ? ear.y : medianY + 1.0;
      if (cooldown[0] == 0 && calmStreet && day > 0.5F && citizens > 0 && RANDOM.nextFloat() < 0.04F) {
         Vec3 a = around(ear, 8.0, 22.0, 0.0);
         oneShot(mc, "city_life.pigeons", new Vec3(a.x, street + 3.0 + RANDOM.nextDouble() * 4.0, a.z), 0.9F, 0.95F + RANDOM.nextFloat() * 0.1F);
         // details are rare, so they stay details (cooldowns in scans of half a second)
         cooldown[0] = 120 + RANDOM.nextInt(120);
      }

      if (cooldown[1] == 0 && calmStreet && day > 0.5F && !level.isRaining() && crowd > 0.2F && RANDOM.nextFloat() < 0.04F) {
         Vec3 a = around(ear, 10.0, 24.0, 0.0);
         oneShot(mc, "city_life.bike_bell", new Vec3(a.x, street, a.z), 0.8F, 0.97F + RANDOM.nextFloat() * 0.06F);
         cooldown[1] = 100 + RANDOM.nextInt(120);
      }

      if (cooldown[2] == 0 && calmStreet && trauma < 0.1F && !calmNear.isEmpty() && RANDOM.nextFloat() < 0.05F) {
         // laughter from a group that is really there
         CitizenEntity c = calmNear.get(RANDOM.nextInt(calmNear.size()));
         oneShot(mc, "city_life.laugh", c.position().add(0.0, 1.5, 0.0), 0.7F, 0.96F + RANDOM.nextFloat() * 0.08F);
         cooldown[2] = 70 + RANDOM.nextInt(90);
      }

      if (cooldown[3] == 0 && (time < 1200 || time > 5600 && time < 6400) && RANDOM.nextFloat() < 0.08F) {
         // church bells far over the roofs in the morning and at noon
         Vec3 a = around(ear, 70.0, 90.0, 0.0);
         oneShot(mc, "city_life.bells", new Vec3(a.x, street + 30.0, a.z), 3.0F, 1.0F);
         cooldown[3] = 240;
      }

      boolean night = day < 0.3F;
      float dogChance = (night ? 0.03F : 0.0F) + (trauma > 0.3F ? 0.04F : 0.0F);
      if (cooldown[4] == 0 && dogChance > 0.0F && RANDOM.nextFloat() < dogChance) {
         Vec3 a = around(ear, 25.0, 55.0, 0.0);
         oneShot(mc, "city_life.dog", new Vec3(a.x, street, a.z), 1.6F, 0.93F + RANDOM.nextFloat() * 0.12F);
         cooldown[4] = 50 + RANDOM.nextInt(60);
      }
   }

   static void stopAll() {
      for (int i = 0; i < LOOPS.length; i++) {
         if (LOOPS[i] != null) {
            LOOPS[i].halt();
            LOOPS[i] = null;
         }

         if (WIDE[i] != null) {
            WIDE[i].halt();
            WIDE[i] = null;
         }

         TARGET[i] = 0.0F;
         SIDE[i] = 0.0F;
      }

      trauma = 0.0F;
      lastPanic = 0.0F;
      medianY = Double.NaN;
      // a fresh world does not open with pigeons, a bell and laughter in the same second
      for (int i = 0; i < cooldown.length; i++) {
         cooldown[i] = 10 + RANDOM.nextInt(60);
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      stopAll();
   }

   /**
    * A bed loop that really still plays: not stopped by itself and still known to the sound engine. A resource reload
    * (language, resource packs, F3+T) or a restart of the sound engine drops every sound without stopping the
    * instance; the beds would stay silent until the next world without this check.
    */
   private static boolean alive(@Nullable Loop l) {
      return l != null && !l.isStopped() && Minecraft.getInstance().getSoundManager().isActive(l);
   }

   /** QA line: bed targets and levels, the scene, the voices and the engine's source counts. */
   public static String describe() {
      StringBuilder b = new StringBuilder();
      int playing = 0;
      for (int i = 0; i < BEDS.length; i++) {
         boolean on = alive(LOOPS[i]);
         if (on) {
            playing++;
         }

         if (TARGET[i] > 0.005F || on) {
            b.append(BEDS[i]).append('=').append(String.format(Locale.ROOT, "%.2f", TARGET[i]));
            if (on) {
               b.append('/').append(String.format(Locale.ROOT, "%.2f", LOOPS[i].level));
            }

            if (PLACED[i]) {
               boolean wide = alive(WIDE[i]);
               b.append(String.format(Locale.ROOT, " side=%.2f%s", SIDE[i], wide ? "+wide" : ""));
            }

            b.append(' ');
         }
      }

      Minecraft mc = Minecraft.getInstance();
      return String.format(Locale.ROOT, "citylife citizens=%d trauma=%.2f indoor=%s above=%.1f beds=%d [%s] details=%d %s engine=%s", citizens, trauma, indoor,
         above, playing, b.toString().trim(), details, CityLifeVoices.describe(), mc.getSoundManager().getDebugString());
   }

   /** A bed: fades toward its target, follows its place, stops itself when silent. */
   static final class Loop extends AbstractTickableSoundInstance {
      private final Supplier<Float> target;
      private final Supplier<Vec3> at;
      float level;
      private int silent;

      Loop(SoundEvent event, Supplier<Float> target, Supplier<Vec3> at, boolean centred) {
         super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
         this.target = target;
         // centred: at the listener, in both ears (stereo beds and the centred layer of a placed one)
         this.at = centred ? () -> null : at;
         this.relative = centred;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.attenuation = SoundInstance.Attenuation.NONE;
         this.move();
      }

      private void move() {
         Vec3 p = this.at.get();
         if (p != null) {
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
         }
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      void halt() {
         this.stop();
      }

      @Override
      public void tick() {
         float goal = Mth.clamp(this.target.get(), 0.0F, 1.0F);
         this.level = goal > this.level ? Math.min(goal, this.level + FADE_UP) : Math.max(goal, this.level - FADE_DOWN);
         this.silent = goal <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 40) {
            this.stop();
            return;
         }

         this.volume = Math.max(0.001F, this.level);
         Vec3 p = this.at.get();
         if (p != null) {
            // glide, never jump: the murmur moves with the crowd
            this.x += (p.x - this.x) * 0.15;
            this.y += (p.y - this.y) * 0.15;
            this.z += (p.z - this.z) * 0.15;
         }
      }
   }
}
