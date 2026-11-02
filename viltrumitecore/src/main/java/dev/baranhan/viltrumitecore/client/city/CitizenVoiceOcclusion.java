package dev.baranhan.viltrumitecore.client.city;

import dev.baranhan.viltrumitecore.city.citizen.PerilRules;
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
import net.minecraft.world.level.ClipContext;
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
 * keeps the original id, so a stop packet (a citizen caught mid-fall) still finds it.
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

   private CitizenVoiceOcclusion() {
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

      event.setSound(new Occluded(s));
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

      Occluded(SoundInstance inner) {
         this.inner = inner;
         float f = measure(inner.getX(), inner.getY(), inner.getZ());
         this.factor = Float.isNaN(f) ? OPEN : f;
      }

      float factor() {
         return this.factor;
      }

      @Override
      public boolean isStopped() {
         return this.inner instanceof TickableSoundInstance t && t.isStopped();
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
