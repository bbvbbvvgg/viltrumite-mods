package dev.baranhan.viltrumitecore.client.city.life;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The citizens' voices on this client (issue #136). Every city voice the server plays at a citizen (chat lines,
 * calls, screams, coughs and sobs) is taken over here before it starts:
 * <ul>
 * <li>its loudness follows the distance with our own linear curve (the range of the event and its volume, like
 * the engine), a two-way ray test between the camera and the mouth (open 100 %, a thin wall or window
 * {@link CityLifeMixer#THIN}, a thick wall or a building {@link CityLifeMixer#THICK}, eased) and a fade;</li>
 * <li>at most {@link CityLifeMixer#cap} voices of a kind sound at once, the nearest win (a farther one fades out
 * for a nearer one, a new farther one is not started), and the same line is not doubled nearby;</li>
 * <li>a chat line fades out within a few ticks when its speaker stops talking (startled, knocked down): the
 * conversation is cut off mid-sentence;</li>
 * <li>who is speaking right now is known to the poser ({@link #speaking}): the speaker gestures, the other
 * listens and nods.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CityLifeVoices {
   static final double TRACE_RANGE = 72.0;
   static final int TRACE_EVERY = 4;
   static final float EASE = 0.35F;
   static final double MOUTH = 1.5;
   static final float CUT_FADE = 0.25F;
   static final float YIELD_FADE = 0.2F;

   private static final List<Voice> ACTIVE = new ArrayList<>();
   private static final Int2ObjectOpenHashMap<Voice> SPEAKING = new Int2ObjectOpenHashMap<>();
   private static final Int2ObjectOpenHashMap<long[]> HEARD = new Int2ObjectOpenHashMap<>();
   // QA
   static final int[] started = new int[CityLifeMixer.KINDS];
   static final int[] dropped = new int[CityLifeMixer.KINDS];
   static int cut;
   static int doubled;

   private CityLifeVoices() {
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onPlaySound(PlaySoundEvent event) {
      SoundInstance s = event.getSound();
      if (s == null || s instanceof Voice || s.isRelative() || !"viltrumitecore".equals(s.getLocation().getNamespace())) {
         return;
      }

      String path = s.getLocation().getPath();
      int kind = CityLifeMixer.kindOf(path);
      if (kind < 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.gameRenderer == null) {
         return;
      }

      prune();
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      double d = ear.distanceTo(new Vec3(s.getX(), s.getY(), s.getZ()));
      String line = CityLifeMixer.lineOf(path);
      int now = (int)mc.level.getGameTime();
      for (Voice v : ACTIVE) {
         if (!v.fading() && v.line.equals(line) && now - v.born < CityLifeMixer.DUPLICATE_TICKS && v.distanceTo(s.getX(), s.getY(), s.getZ()) < CityLifeMixer.DUPLICATE_RANGE) {
            doubled++;
            event.setSound(null);
            return;
         }
      }

      // the nearest voices of a kind win
      int count = 0;
      Voice farthest = null;
      for (Voice v : ACTIVE) {
         if (v.kind == kind && !v.fading()) {
            count++;
            if (farthest == null || v.ear(ear) > farthest.ear(ear)) {
               farthest = v;
            }
         }
      }

      if (count >= CityLifeMixer.cap(kind)) {
         if (farthest == null || farthest.ear(ear) <= d) {
            dropped[kind]++;
            event.setSound(null);
            return;
         }

         farthest.fadeOut(YIELD_FADE);
      }

      CitizenEntity speaker = speakerAt(mc.level, s.getX(), s.getY(), s.getZ());
      Voice v = new Voice(s, kind, line, speaker, now);
      ACTIVE.add(v);
      if (speaker != null && kind == CityLifeMixer.TALK) {
         SPEAKING.put(speaker.getId(), v);
         HEARD.put(speaker.getId(), new long[]{now});
      }

      started[kind]++;
      event.setSound(v);
   }

   /** The citizen an entity-bound sound starts at (the engine copies the feet position as floats). */
   @Nullable
   static CitizenEntity speakerAt(ClientLevel level, double x, double y, double z) {
      CitizenEntity best = null;
      double bestD = 0.36;
      for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0))) {
         double dd = c.distanceToSqr(x, y, z);
         if (dd < bestD) {
            bestD = dd;
            best = c;
         }
      }

      return best;
   }

   private static void prune() {
      net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
      long now = level == null ? Long.MAX_VALUE / 2 : level.getGameTime();
      Iterator<Voice> it = ACTIVE.iterator();
      while (it.hasNext()) {
         Voice v = it.next();
         // not active any more: ended, or dropped by a resource reload / engine restart (then it is not ticked either,
         // so the age by game time, not its own tick counter)
         if (v.isStopped() || now - v.born > 20 && !Minecraft.getInstance().getSoundManager().isActive(v)) {
            it.remove();
            if (v.speaker != null && SPEAKING.get(v.speaker.getId()) == v) {
               SPEAKING.remove(v.speaker.getId());
            }
         }
      }
   }

   /**
    * For the poser: 1 while this citizen says a chat line (gestures), 0 while he is in a voiced chat but the other
    * one speaks (listens, nods), -1 when nothing is known (a silent chat: the old idle gesturing).
    */
   public static float speaking(CitizenEntity e) {
      Voice v = SPEAKING.get(e.getId());
      if (v != null && !v.isStopped() && !v.fading()) {
         return 1.0F;
      }

      long[] h = HEARD.get(e.getId());
      if (h != null && e.level().getGameTime() - h[0] < 260L) {
         return 0.0F;
      }

      // the partner of a voiced chat listens: someone within 4 blocks is speaking
      for (Voice o : ACTIVE) {
         if (o.kind == CityLifeMixer.TALK && o.speaker != null && o.speaker != e && !o.fading() && o.speaker.distanceToSqr(e) < 16.0) {
            return 0.0F;
         }
      }

      return -1.0F;
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      ACTIVE.clear();
      SPEAKING.clear();
      HEARD.clear();
   }

   /** Called from the ambience tick: forgets old speakers. */
   static void housekeeping(long now) {
      prune();
      HEARD.int2ObjectEntrySet().removeIf(e -> now - e.getValue()[0] > 600L);
   }

   /** QA: active voices per kind and what the limits did. */
   static String describe() {
      int[] n = new int[CityLifeMixer.KINDS];
      for (Voice v : ACTIVE) {
         if (!v.isStopped()) {
            n[v.kind]++;
         }
      }

      return "voices talk=" + n[0] + " call=" + n[1] + " scream=" + n[2] + " quiet=" + n[3] + " started=" + java.util.Arrays.toString(started)
         + " dropped=" + java.util.Arrays.toString(dropped) + " doubled=" + doubled + " cut=" + cut;
   }

   static int activeCount() {
      int n = 0;
      for (Voice v : ACTIVE) {
         if (!v.isStopped()) {
            n++;
         }
      }

      return n;
   }

   /** Occlusion factor of a voice at a point (feet), measured from the camera; NaN when too far to bother. */
   static float measure(double x, double y, double z) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.gameRenderer == null || mc.player == null) {
         return CityLifeMixer.OPEN;
      }

      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 mouth = new Vec3(x, y + MOUTH, z);
      double d = ear.distanceTo(mouth);
      if (d < 1.0 || d > TRACE_RANGE) {
         return Float.NaN;
      }

      BlockHitResult in = level.clip(new ClipContext(ear, mouth, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      if (in.getType() == HitResult.Type.MISS) {
         return CityLifeMixer.OPEN;
      }

      BlockHitResult out = level.clip(new ClipContext(mouth, ear, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      double thick = out.getType() == HitResult.Type.MISS ? 1.0 : in.getLocation().distanceTo(out.getLocation());
      return CityLifeMixer.occlusion(true, thick);
   }

   /**
    * A city voice: the server's sound (it keeps following its citizen and stops with him) played with attenuation
    * off and our own gain (distance, occlusion, fade), so walls and the limits work for loud calls as well.
    */
   static final class Voice implements TickableSoundInstance {
      final SoundInstance inner;
      final int kind;
      final String line;
      @Nullable
      final CitizenEntity speaker;
      final int born;
      int age;
      private float occlusion;
      private float fade = 1.0F;
      private float fadeStep;
      private double range = -1.0;
      private boolean stopped;

      Voice(SoundInstance inner, int kind, String line, @Nullable CitizenEntity speaker, int born) {
         this.inner = inner;
         this.kind = kind;
         this.line = line;
         this.speaker = speaker;
         this.born = born;
         float f = measure(inner.getX(), inner.getY(), inner.getZ());
         this.occlusion = Float.isNaN(f) ? CityLifeMixer.OPEN : f;
      }

      boolean fading() {
         return this.fadeStep > 0.0F;
      }

      void fadeOut(float step) {
         this.fadeStep = Math.max(this.fadeStep, step);
      }

      double distanceTo(double x, double y, double z) {
         double dx = this.getX() - x;
         double dy = this.getY() - y;
         double dz = this.getZ() - z;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }

      double ear(Vec3 ear) {
         return this.distanceTo(ear.x, ear.y, ear.z);
      }

      @Override
      public boolean isStopped() {
         return this.stopped || this.inner instanceof TickableSoundInstance t && t.isStopped();
      }

      @Override
      public boolean canStartSilent() {
         return false;
      }

      @Override
      public void tick() {
         this.age++;
         if (this.inner instanceof TickableSoundInstance t) {
            t.tick();
         }

         // a chat line is cut off when its speaker stops talking (a scream, a blast, he runs)
         if (this.kind == CityLifeMixer.TALK && this.speaker != null && !this.fading()
            && (this.speaker.isRemoved() || this.speaker.citizenState() != CitizenState.TALKING)) {
            cut++;
            this.fadeOut(CUT_FADE);
         }

         if (this.fadeStep > 0.0F) {
            this.fade -= this.fadeStep;
            if (this.fade <= 0.0F) {
               this.stopped = true;
               return;
            }
         }

         if (this.age % TRACE_EVERY == 0) {
            float f = measure(this.getX(), this.getY(), this.getZ());
            if (!Float.isNaN(f)) {
               this.occlusion += (f - this.occlusion) * EASE;
            }
         }
      }

      private double range() {
         if (this.range < 0.0) {
            Sound s = this.inner.getSound();
            this.range = s == null ? 16.0 : Math.max(1.0F, this.inner.getVolume()) * s.getAttenuationDistance();
         }

         return this.range;
      }

      @Override
      public float getVolume() {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gameRenderer == null) {
            return 0.0F;
         }

         Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
         float gain = Math.min(1.0F, this.inner.getVolume()) * CityLifeMixer.linear(this.ear(ear), this.range());
         return gain * this.occlusion * Math.max(0.0F, this.fade);
      }

      @Override
      public ResourceLocation getLocation() {
         return this.inner.getLocation();
      }

      @Nullable
      @Override
      public WeighedSoundEvents resolve(SoundManager manager) {
         return this.inner.resolve(manager);
      }

      @Override
      public Sound getSound() {
         return this.inner.getSound();
      }

      @Override
      public SoundSource getSource() {
         return this.inner.getSource();
      }

      @Override
      public boolean isLooping() {
         return this.inner.isLooping();
      }

      @Override
      public boolean isRelative() {
         return false;
      }

      @Override
      public int getDelay() {
         return this.inner.getDelay();
      }

      @Override
      public float getPitch() {
         return this.inner.getPitch();
      }

      @Override
      public double getX() {
         return this.inner.getX();
      }

      @Override
      public double getY() {
         return this.inner.getY();
      }

      @Override
      public double getZ() {
         return this.inner.getZ();
      }

      @Override
      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }
   }
}
