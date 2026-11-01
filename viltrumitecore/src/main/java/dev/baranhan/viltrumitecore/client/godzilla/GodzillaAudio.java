package dev.baranhan.viltrumitecore.client.godzilla;

import dev.baranhan.viltrumitecore.client.meteor.MeteorRainAudio;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaMove;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaSounds;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.RegistryObject;

/**
 * The kaiju's sound, all client side (the server plays nothing). Cues come from the synced move starts (the roar
 * as the jaw opens, the charge as the first plates ignite, the swishes before the contact frames) and from the
 * effect packets (steps, stomps, impacts, the fall). Every cue sits at its source with our own distance curves
 * (attenuation off, the position still pans): a near layer that carries the detail and a far layer that rolls
 * across the city, delayed by the speed of sound ({@link MeteorRainAudio#soundDelay}). Impacts are rate limited so
 * a tail sweep through a block does not stack a wall of identical hits. The beam has two loops that follow the
 * mouth and the impact point and fade out with it. Own synthesis only (tools/sfx/godzilla.py, CC0).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class GodzillaAudio {
   /** Gain on every one-shot: the roar and the fall must sit under the aftermath explosions, not on top of them. */
   public static final float HEADROOM = 0.85F;
   /** Minimum ticks between two impact cues of one kind. */
   public static final int IMPACT_GAP = 7;

   private static final Map<Integer, State> STATES = new HashMap<>();
   private static final Map<Integer, Integer> LAST = new HashMap<>();
   private static ClientLevel lastLevel;
   private static int clock;

   private GodzillaAudio() {
   }

   /** Pure: a near layer at a distance: full up close, gone by {@code range}. */
   public static float near(double distance, double range) {
      if (distance >= range) {
         return 0.0F;
      }
      float t = (float)(1.0 - distance / range);
      return Mth.clamp(0.15F * t + 0.85F * t * t, 0.0F, 1.0F);
   }

   /** Pure: a far layer: quiet up close (the near layer carries it), strongest across the district, gone by {@code range}. */
   public static float far(double distance, double from, double range) {
      if (distance >= range) {
         return 0.0F;
      }
      float in = (float)Mth.clamp(distance / Math.max(1.0, from), 0.25, 1.0);
      float out = (float)(1.0 - Math.max(0.0, distance - from) / Math.max(1.0, range - from));
      return Mth.clamp(in * (0.25F + 0.75F * out), 0.0F, 1.0F);
   }

   /** Pure: true when an impact of this kind may sound now (and records it). */
   static boolean due(int kind, int now) {
      Integer last = LAST.get(kind);
      if (last != null && now - last < IMPACT_GAP) {
         return false;
      }
      LAST.put(kind, now);
      return true;
   }

   // ------------------------------------------------------------------ packet cues

   public static void fx(GodzillaFxS2CPacket p, double d) {
      Vec3 at = p.at();
      switch (p.kind()) {
         case GodzillaFxS2CPacket.STEP -> {
            once(GodzillaSounds.STEP, at, near(d, 220.0) * 0.9F * Math.min(1.0F, p.power()), 0.92F + rnd(0.12F), d);
            once(GodzillaSounds.STEP_FAR, at, far(d, 90.0, 720.0) * 0.8F, 0.95F + rnd(0.1F), d);
         }
         case GodzillaFxS2CPacket.STOMP -> {
            once(GodzillaSounds.STOMP, at, near(d, 320.0), 0.95F + rnd(0.08F), d);
            once(GodzillaSounds.STEP_FAR, at, far(d, 120.0, 900.0), 0.85F, d);
         }
         case GodzillaFxS2CPacket.TAIL_HIT -> {
            if (due(p.kind(), clock)) {
               once(GodzillaSounds.TAIL_HIT, at, near(d, 240.0), 0.9F + rnd(0.15F), d);
            }
         }
         case GodzillaFxS2CPacket.CLAW_HIT -> {
            if (due(p.kind(), clock)) {
               once(GodzillaSounds.CLAW_HIT, at, near(d, 220.0), 0.9F + rnd(0.15F), d);
            }
         }
         case GodzillaFxS2CPacket.SMASH -> {
            if (due(p.kind(), clock)) {
               float size = Mth.clamp(0.45F + p.extra() / 1500.0F, 0.45F, 1.0F);
               once(GodzillaSounds.SMASH, at, near(d, 240.0) * size, 0.88F + rnd(0.2F), d);
            }
         }
         case GodzillaFxS2CPacket.FALL_IMPACT -> {
            once(GodzillaSounds.FALL, at, near(d, 380.0), 1.0F, d);
            once(GodzillaSounds.STEP_FAR, at, far(d, 120.0, 1000.0), 0.7F, d);
         }
         case GodzillaFxS2CPacket.DEATH -> {
            if (p.power() > 0.0F) {
               once(GodzillaSounds.FALL, at, near(d, 380.0), 0.92F, d);
               once(GodzillaSounds.STEP_FAR, at, far(d, 120.0, 1000.0), 0.65F, d);
            }
         }
         case GodzillaFxS2CPacket.ARRIVE -> once(GodzillaSounds.ARRIVE, at, Math.max(0.35F, near(d, 1100.0)), 1.0F, 0.0);
         case GodzillaFxS2CPacket.RETREAT -> {
            if (p.power() > 0.0F) {
               once(GodzillaSounds.RETREAT, at, Math.max(0.3F, near(d, 900.0)), 1.0F, d);
            }
         }
         case GodzillaFxS2CPacket.CLEAR -> stopAll();
         default -> {
         }
      }
   }

   // ------------------------------------------------------------------ move cues

   private static void moveStarted(Minecraft mc, GodzillaEntity g, GodzillaMove move, Vec3 ear) {
      Vec3 head = mouth(g);
      double d = head.distanceTo(ear);
      Vec3 body = g.position().add(0.0, GodzillaRules.HEIGHT * 0.5, 0.0);
      double db = body.distanceTo(ear);
      switch (move) {
         case ROAR -> roar(head, d, 16, 1.0F);
         case CHARGE -> cue(GodzillaSounds.CHARGE, body, Math.max(near(db, 420.0), far(db, 150.0, 700.0) * 0.6F), 1.0F, GodzillaRules.CHARGE_BRACE, db);
         case FIRE -> {
            cue(GodzillaSounds.BEAM_START, head, Math.max(near(d, 460.0), far(d, 160.0, 900.0) * 0.7F), 1.0F, 0, d);
         }
         case BREATH_END -> cue(GodzillaSounds.BEAM_END, head, near(d, 420.0), 1.0F, 0, d);
         case CLAW_R, CLAW_L -> cue(GodzillaSounds.CLAW_SWISH, head, near(d, 200.0), 0.9F + rnd(0.2F), 15, d);
         case TAIL -> cue(GodzillaSounds.TAIL_SWISH, body, near(db, 240.0), 0.9F + rnd(0.15F), 20, db);
         case SLAM -> cue(GodzillaSounds.GROWL, head, near(d, 260.0), 0.85F, 4, d);
         case HURT -> {
            cue(GodzillaSounds.HURT, head, near(d, 320.0), 0.95F + rnd(0.1F), 2, d);
            cue(GodzillaSounds.ROAR_FAR, head, far(d, 160.0, 800.0) * 0.5F, 1.1F, 2, d);
         }
         case FALL -> cue(GodzillaSounds.HURT, head, near(d, 320.0), 0.85F, 0, d);
         case RISE -> cue(GodzillaSounds.RISE, body, near(db, 300.0), 1.0F, 8, db);
         case DEATH -> {
            cue(GodzillaSounds.DEATH, head, Math.max(0.25F, near(d, 700.0)), 1.0F, 4, d);
         }
         default -> {
         }
      }
   }

   private static void roar(Vec3 at, double d, int delay, float pitch) {
      cue(GodzillaSounds.ROAR, at, near(d, 420.0), pitch, delay, d);
      cue(GodzillaSounds.ROAR_FAR, at, far(d, 140.0, 1100.0), pitch, delay, d);
   }

   private static Vec3 mouth(GodzillaEntity g) {
      return g.clientMouth != null && g.tickCount - g.clientMouthTick < 40 ? g.clientMouth : g.mouth();
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         stopAll();
      }
      if (mc.level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      clock++;
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      for (Entity en : mc.level.entitiesForRendering()) {
         if (!(en instanceof GodzillaEntity g) || g.isRemoved()) {
            continue;
         }
         State s = STATES.computeIfAbsent(g.getId(), id -> new State());
         s.seen = clock;
         GodzillaMove move = g.move();
         if (s.moveStart != g.clientMoveStart || s.move != move) {
            boolean first = s.move == null;
            s.moveStart = g.clientMoveStart;
            s.move = move;
            if (!first || g.moveTime(0.0F) < 10.0F) {
               moveStarted(mc, g, move, ear);
            }
         }
         if (g.flag(GodzillaEntity.FLAG_BEAM)) {
            if (s.beam == null || s.beam.isStopped()) {
               s.beam = new Loop(GodzillaSounds.BEAM_LOOP.get(), g, true);
               mc.getSoundManager().play(s.beam);
            }
            if (s.impact == null || s.impact.isStopped()) {
               s.impact = new Loop(GodzillaSounds.BEAM_IMPACT.get(), g, false);
               mc.getSoundManager().play(s.impact);
            }
         }
         // a threat growl now and then while it stands and looks around
         if (move == GodzillaMove.NONE && g.isAlive() && clock >= s.nextGrowl) {
            if (s.nextGrowl > 0) {
               Vec3 head = mouth(g);
               double d = head.distanceTo(ear);
               once(GodzillaSounds.GROWL, head, near(d, 240.0) * 0.8F, 0.9F + rnd(0.2F), d);
            }
            s.nextGrowl = clock + 260 + mc.level.random.nextInt(340);
         }
      }
      Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
      while (it.hasNext()) {
         State s = it.next().getValue();
         if (clock - s.seen > 40) {
            s.stop();
            it.remove();
         }
      }
   }

   public static void stopAll() {
      for (State s : STATES.values()) {
         s.stop();
      }
      STATES.clear();
      LAST.clear();
   }

   // ------------------------------------------------------------------ playback

   private static float rnd(float spread) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0F : (mc.level.random.nextFloat() - 0.5F) * spread;
   }

   private static void once(Supplier<SoundEvent> event, Vec3 at, float volume, float pitch, double distance) {
      cue(event, at, volume, pitch, 0, distance);
   }

   private static void cue(Supplier<SoundEvent> event, Vec3 at, float volume, float pitch, int delay, double distance) {
      float v = volume * HEADROOM;
      if (v <= 0.01F) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      SimpleSoundInstance sound = new SimpleSoundInstance(event.get().getLocation(), SoundSource.HOSTILE, Mth.clamp(v, 0.0F, 1.0F), pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
      int total = delay + MeteorRainAudio.soundDelay(distance);
      if (total > 0) {
         mc.getSoundManager().playDelayed(sound, total);
      } else {
         mc.getSoundManager().play(sound);
      }
   }

   private static final class State {
      GodzillaMove move;
      int moveStart = Integer.MIN_VALUE;
      int seen;
      int nextGrowl;
      Loop beam;
      Loop impact;

      void stop() {
         if (this.beam != null) {
            this.beam.kill();
         }
         if (this.impact != null) {
            this.impact.kill();
         }
         this.beam = null;
         this.impact = null;
      }
   }

   /** A beam loop: at the mouth or at the impact point, following it, fading in and out with the beam. */
   private static final class Loop extends AbstractTickableSoundInstance {
      private final GodzillaEntity owner;
      private final boolean mouth;
      private float level;
      private boolean dead;

      Loop(SoundEvent event, GodzillaEntity owner, boolean mouth) {
         super(event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
         this.owner = owner;
         this.mouth = mouth;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.attenuation = SoundInstance.Attenuation.NONE;
         this.place();
      }

      private void place() {
         Vec3 at = this.mouth ? GodzillaAudio.mouth(this.owner) : this.owner.clientBeamEnd;
         this.x = at.x;
         this.y = at.y;
         this.z = at.z;
      }

      void kill() {
         this.dead = true;
         this.stop();
      }

      @Override
      public void tick() {
         if (this.dead || this.owner.isRemoved()) {
            this.stop();
            return;
         }
         boolean on = this.owner.flag(GodzillaEntity.FLAG_BEAM);
         this.level = on ? Math.min(1.0F, this.level + 0.25F) : this.level - 0.12F;
         if (this.level <= 0.0F) {
            this.stop();
            return;
         }
         this.place();
         Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         double d = ear.distanceTo(new Vec3(this.x, this.y, this.z));
         float base = this.mouth ? Math.max(near(d, 460.0), far(d, 160.0, 800.0) * 0.5F) : near(d, 300.0);
         this.volume = Mth.clamp(base * this.level * HEADROOM, 0.001F, 1.0F);
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
