package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.ironman.IronManHulkbusterSounds;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The one door for every Hulkbuster sound (rework spec §1.3.11, §10): a cue names the event, its
 * loudness and pitch spread. No vanilla sound events. Server cues play for everyone around (one
 * playSound, no client duplicate); client loops go through client.ironman.hulkbuster.HulkbusterLoops.
 * The event behind a cue comes from IronManHulkbusterSounds (sound-design skill, cue sheet
 * docs/plans/2026-10-28-hulkbuster-cues.md).
 */
public final class HulkbusterSounds {
   public enum Cue {
      STEP(IronManHulkbusterSounds.STEP, 0.9F, 0.08F),
      SCREECH(IronManHulkbusterSounds.SCREECH, 0.8F, 0.12F),
      SWING(IronManHulkbusterSounds.SWING, 0.9F, 0.1F),
      PUNCH(IronManHulkbusterSounds.PUNCH, 1.0F, 0.08F),
      HAMMER(IronManHulkbusterSounds.HAMMER, 1.2F, 0.06F),
      CHARGE(IronManHulkbusterSounds.CHARGE, 0.9F, 0.0F),
      CHARGED_PUNCH(IronManHulkbusterSounds.CHARGED_PUNCH, 1.3F, 0.05F),
      FACE_PUNCH(IronManHulkbusterSounds.JACKHAMMER, 1.1F, 0.06F),
      FINISH_PUNCH(IronManHulkbusterSounds.FINISH_PUNCH, 1.4F, 0.03F),
      GRAB(IronManHulkbusterSounds.GRAB, 1.0F, 0.05F),
      GRAB_MISS(IronManHulkbusterSounds.SERVO, 0.8F, 0.1F),
      THROW(IronManHulkbusterSounds.THROW, 1.1F, 0.05F),
      SLAM(IronManHulkbusterSounds.SLAM, 1.3F, 0.05F),
      ESCAPE(IronManHulkbusterSounds.SCREECH, 1.0F, 0.1F),
      BLOCK(IronManHulkbusterSounds.CLAMP, 0.8F, 0.06F),
      BLOCK_HIT(IronManHulkbusterSounds.BLOCK_HIT, 1.0F, 0.12F),
      REPULSOR_CHARGE(IronManHulkbusterSounds.REPULSOR_CHARGE, 1.0F, 0.0F),
      REPULSOR_BEAM(IronManHulkbusterSounds.REPULSOR_BEAM, 1.3F, 0.03F),
      RIFT(IronManHulkbusterSounds.SLAM, 1.4F, 0.04F),
      CAGE_MARK(IronManHulkbusterSounds.SERVO, 0.7F, 0.05F),
      CAGE_FALL(IronManHulkbusterSounds.DROP, 1.2F, 0.05F),
      CAGE_LAND(IronManHulkbusterSounds.CLAMP, 1.4F, 0.04F),
      ARM_DETACH(IronManHulkbusterSounds.BREAK, 0.9F, 0.05F),
      ARM_DOCK(IronManHulkbusterSounds.CLAMP, 1.2F, 0.05F),
      DASH(IronManHulkbusterSounds.DASH, 1.1F, 0.06F),
      JET_LAUNCH(IronManHulkbusterSounds.HOP, 1.2F, 0.05F),
      COUGH(IronManHulkbusterSounds.COUGH, 0.9F, 0.1F),
      DIVE_IMPACT(IronManHulkbusterSounds.SLAM, 1.5F, 0.04F),
      LAND_HEAVY(IronManHulkbusterSounds.LAND, 1.1F, 0.06F),
      ULT_IGNITE(IronManHulkbusterSounds.ULT_IGNITE, 1.4F, 0.0F),
      ULT_IMPACT(IronManHulkbusterSounds.ULT_IMPACT, 2.0F, 0.0F),
      POD_FALL(IronManHulkbusterSounds.DROP, 1.4F, 0.0F),
      POD_OPEN(IronManHulkbusterSounds.EXIT, 1.1F, 0.0F),
      CLAMP(IronManHulkbusterSounds.CLAMP, 1.0F, 0.06F),
      REACTOR(IronManHulkbusterSounds.ASSEMBLE, 1.0F, 0.0F),
      HERO_LANDING(IronManHulkbusterSounds.LAND, 1.6F, 0.0F),
      EXIT(IronManHulkbusterSounds.EXIT, 1.0F, 0.0F),
      BREAK(IronManHulkbusterSounds.BREAK, 1.2F, 0.0F),
      REFUSE(IronManHulkbusterSounds.REFUSE, 0.7F, 0.0F);

      private final Supplier<SoundEvent> event;
      private final float volume;
      private final float spread;

      Cue(Supplier<SoundEvent> event, float volume, float spread) {
         this.event = event;
         this.volume = volume;
         this.spread = spread;
      }

      public SoundEvent event() {
         return this.event.get();
      }

      public float volume() {
         return this.volume;
      }

      public float spread() {
         return this.spread;
      }
   }

   private HulkbusterSounds() {
   }

   /** Everyone around hears it at the player. */
   public static void play(ServerPlayer player, Cue cue) {
      play(player, cue, player.position(), 1.0F);
   }

   public static void play(ServerPlayer player, Cue cue, Vec3 at, float pitch) {
      float p = pitch + (player.getRandom().nextFloat() * 2.0F - 1.0F) * cue.spread();
      player.level().playSound(null, at.x, at.y, at.z, cue.event(), SoundSource.PLAYERS, cue.volume(), Math.max(0.5F, p));
   }

   /** Only the owner hears it (refusals). */
   public static void notify(ServerPlayer player, Cue cue) {
      player.playNotifySound(cue.event(), SoundSource.PLAYERS, cue.volume(), 1.0F);
   }
}
