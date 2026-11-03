package dev.baranhan.viltrumitecore.client.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenVoices;
import dev.baranhan.viltrumitecore.city.citizen.PerilRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Citizen voices (screams, calls for help, witnesses, a crowd in panic; issue #138) are quieter
 * behind walls: every voice is wrapped in a tickable sound whose volume follows a two-way ray
 * test between the camera and the mouth - open line 100 %, one thin wall or window
 * {@link #THIN}, a thick wall or a whole building {@link #THICK}, eased so it never pops.
 * The wrapped sound keeps following its citizen (entity-bound sounds move with the body) and
 * keeps the original id. A falling scream is also tracked per citizen, so a catch stops only that citizen's voice.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CitizenVoiceOcclusion {
   static final float OPEN = 1.0F;
   static final float THIN = 0.45F;
   static final float THICK = 0.25F;
   /** Blocks of solid between the two hit points up to which a wall still counts as thin. */
   static final double THIN_WALL = 1.6;
   /** Beyond this the voice is too faint to bother tracing (and stays at the last factor). */
   static final double TRACE_RANGE = 72.0;
   static final int TRACE_EVERY = 4;
   static final float EASE = 0.35F;
   /** Mouth height over the feet (entity sounds sit at the feet). */
   static final double MOUTH = 1.5;

   /** The falling scream of each citizen in earshot (entity id -> its voice), so a catch stops only that one. */
   private static final Int2ObjectOpenHashMap<Occluded> FALLING = new Int2ObjectOpenHashMap<>();

   private CitizenVoiceOcclusion() {
   }

   /** Client side of {@link CitizenEntity#EVENT_STOP_FALL_VOICE}: stops the falling scream of this citizen only. */
   public static void stopFallVoice(int entityId) {
      Occluded v = FALLING.remove(entityId);
      if (v != null) {
         v.kill();
      }
   }

   /** Pure: the sound is a falling scream (male or female). */
   static boolean isFallScream(ResourceLocation id) {
      return id.equals(CitizenVoices.FALL_MALE.getId()) || id.equals(CitizenVoices.FALL_FEMALE.getId());
   }

   /** The citizen a just-started entity-bound voice belongs to: entity sounds start exactly at the feet of their body. */
   @Nullable
   private static CitizenEntity owner(SoundInstance s) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return null;
      }
      Vec3 at = new Vec3(s.getX(), s.getY(), s.getZ());
      CitizenEntity best = null;
      double bestD = 0.75 * 0.75;
      for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(at, at).inflate(1.0))) {
         double d = c.position().distanceToSqr(at);
         if (d < bestD) {
            best = c;
            bestD = d;
         }
      }
      return best;
   }

   private static void track(SoundInstance original, Occluded wrapped) {
      SoundManager sounds = Minecraft.getInstance().getSoundManager();
      FALLING.int2ObjectEntrySet().removeIf(e -> e.getValue().done() || !sounds.isActive(e.getValue()));
      CitizenEntity c = owner(original);
      if (c != null) {
         Occluded old = FALLING.put(c.getId(), wrapped);
         if (old != null && old != wrapped) {
            old.kill();
         }
      }
   }

   /** Pure: the volume factor of a voice for a blocked/open line and the solid thickness on it. */
   static float factor(boolean blocked, double thickness) {
      if (!blocked) {
         return OPEN;
      }

      return thickness <= THIN_WALL ? THIN : THICK;
   }

   /** Pure: one easing step of the applied factor toward the measured one. */
   static float ease(float current, float target) {
      return current + (target - current) * EASE;
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onPlaySound(PlaySoundEvent event) {
      SoundInstance s = event.getSound();
      if (s == null || s instanceof Occluded || s.isRelative() || !PerilRules.isVoice(s.getLocation())) {
         return;
      }

      Occluded wrapped = new Occluded(s);
      if (isFallScream(s.getLocation())) {
         track(s, wrapped);
      }
      event.setSound(wrapped);
   }

   /** Current factor for a voice at a point (feet position), measured from the camera. */
   static float measure(double x, double y, double z) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.gameRenderer == null || mc.player == null) {
         return OPEN;
      }

      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 mouth = new Vec3(x, y + MOUTH, z);
      double d = ear.distanceTo(mouth);
      if (d < 1.0 || d > TRACE_RANGE) {
         return Float.NaN;
      }

      BlockHitResult in = level.clip(new ClipContext(ear, mouth, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      if (in.getType() == HitResult.Type.MISS) {
         return OPEN;
      }

      BlockHitResult out = level.clip(new ClipContext(mouth, ear, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
      double thick = out.getType() == HitResult.Type.MISS ? 1.0 : in.getLocation().distanceTo(out.getLocation());
      return factor(true, thick);
   }

   /** A voice played through the occlusion factor; everything else is the original sound. */
   static final class Occluded implements TickableSoundInstance {
      private final SoundInstance inner;
      private float factor;
      private int age;
      private boolean killed;

      Occluded(SoundInstance inner) {
         this.inner = inner;
         float f = measure(inner.getX(), inner.getY(), inner.getZ());
         this.factor = Float.isNaN(f) ? OPEN : f;
      }

      float factor() {
         return this.factor;
      }

      /** Stops this voice on the next sound tick (a hero caught its citizen). */
      void kill() {
         this.killed = true;
      }

      /** Stopped or long finished (a falling scream is a few seconds): no longer worth tracking. */
      boolean done() {
         return this.killed || this.age > 20 * 20;
      }

      @Override
      public boolean isStopped() {
         return this.killed || this.inner instanceof TickableSoundInstance t && t.isStopped();
      }

      @Override
      public void tick() {
         if (this.inner instanceof TickableSoundInstance t) {
            t.tick();
         }

         if (++this.age % TRACE_EVERY == 0) {
            float f = measure(this.inner.getX(), this.inner.getY(), this.inner.getZ());
            if (!Float.isNaN(f)) {
               this.factor = ease(this.factor, f);
            }
         }
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
         return this.inner.isRelative();
      }

      @Override
      public int getDelay() {
         return this.inner.getDelay();
      }

      @Override
      public float getVolume() {
         return this.inner.getVolume() * this.factor;
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
         return this.inner.getAttenuation();
      }

      @Override
      public boolean canStartSilent() {
         return this.inner.canStartSilent();
      }

      @Override
      public boolean canPlaySound() {
         return this.inner.canPlaySound();
      }

      @Override
      public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
         return this.inner.getStream(library, sound, looping);
      }
   }
}
