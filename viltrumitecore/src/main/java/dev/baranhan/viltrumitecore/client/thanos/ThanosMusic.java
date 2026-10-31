package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Event music: the battle loop, and the harder final-minute loop it crossfades into (2 s). Both
 * are own CC0 synthesis (tools/sfx/thanos.py) on the music channel; vanilla music is held off
 * while they play. Silence and stop on the end, a level change or a logout.
 */
final class ThanosMusic {
   static final int FADE_TICKS = 40;
   private static Loop battle;
   private static Loop last;

   private ThanosMusic() {
   }

   /** Per client tick: which loop should be heard (none, battle or final) and how loud 0..1. */
   static void update(boolean on, boolean finalMinute, float gain) {
      battle = drive(battle, on && !finalMinute, gain, false);
      last = drive(last, on && finalMinute, gain, true);
      if (on && (battle != null || last != null)) {
         Minecraft.getInstance().getMusicManager().stopPlaying();
      }
   }

   private static Loop drive(Loop loop, boolean wanted, float gain, boolean finalLoop) {
      if (wanted && (loop == null || loop.isStopped())) {
         SoundEvent event = (finalLoop ? ThanosSounds.MUSIC_FINAL : ThanosSounds.MUSIC_BATTLE).get();
         loop = new Loop(event);
         Minecraft.getInstance().getSoundManager().play(loop);
      }

      if (loop != null) {
         loop.target = wanted ? gain : 0.0F;
         if (loop.isStopped()) {
            loop = null;
         }
      }

      return loop;
   }

   static void stop() {
      if (battle != null) {
         battle.cut();
      }

      if (last != null) {
         last.cut();
      }

      battle = null;
      last = null;
   }

   static final class Loop extends AbstractTickableSoundInstance {
      float target = 1.0F;
      private float level;

      Loop(SoundEvent event) {
         super(event, SoundSource.MUSIC, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
         this.volume = 0.001F;
      }

      @Override
      public void tick() {
         float step = 1.0F / FADE_TICKS;
         this.level += Math.max(-step, Math.min(step, this.target - this.level));
         this.volume = Math.max(0.001F, this.level * 0.85F);
         if (this.level <= 0.0F && this.target <= 0.0F) {
            this.stop();
         }
      }

      void cut() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
