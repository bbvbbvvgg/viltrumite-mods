package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote's two weaknesses (spec §7): fire burns through it (more damage, no regeneration for five seconds, a
 * recoil) and a loud sound - a bell, a horn, a blast, thunder, a sonic boom, anything played at volume 2 or more
 * close by - makes it recoil from the host. A recoil drops a held body, the shell and the zip; a three-second
 * immunity follows so it cannot be chain-stunned. A loud sound near a ridden host helps him throw the symbiote off,
 * and the boss recoils like a player Venom.
 */
public final class VenomWeakness {
   /** Sounds that are loud whatever volume they are played at. */
   static final Set<ResourceLocation> LOUD = Set.of(
      new ResourceLocation("minecraft", "block.bell.use"),
      new ResourceLocation("minecraft", "block.bell.resonate"),
      new ResourceLocation("minecraft", "entity.generic.explode"),
      new ResourceLocation("minecraft", "entity.warden.sonic_boom"),
      new ResourceLocation("minecraft", "entity.lightning_bolt.thunder"),
      new ResourceLocation("minecraft", "event.raid.horn"),
      new ResourceLocation("minecraft", "entity.ender_dragon.growl"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.0"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.1"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.2"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.3"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.4"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.5"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.6"),
      new ResourceLocation("minecraft", "item.goat_horn.sound.7"));

   private VenomWeakness() {
   }

   /** Pure: does a sound of this id and volume make the symbiote recoil at this distance. */
   public static boolean loud(ResourceLocation id, float volume, double distance) {
      if (id != null && "viltrumitecore".equals(id.getNamespace()) && id.getPath().startsWith("venom.")) {
         return false; // its own voice
      }
      boolean listed = id != null && LOUD.contains(id);
      double reach = VenomRules.SOUND_RADIUS * Math.max(1.0, Math.min(3.0, volume));
      return distance <= reach && (listed || volume >= VenomRules.SOUND_MIN_VOLUME);
   }

   /** Pure: the distance rule alone (for a sound already known loud or not). */
   static boolean loudDistance(double distance, float volume, boolean listed) {
      return distance <= VenomRules.SOUND_RADIUS * Math.max(1.0, Math.min(3.0, volume)) && (listed || volume >= VenomRules.SOUND_MIN_VOLUME);
   }

   static void burnt(ServerPlayer player, VenomState state) {
      state.fireBlock = VenomRules.FIRE_REGEN_BLOCK;
      recoil(player, state, VenomLines.RECOIL);
   }

   /**
    * hellfire_scalds_symbiote: the regeneration stops for {@link VenomRules#SCALD_REGEN_BLOCK} ticks whatever the
    * symbiote is doing; the shriek and the recoil follow the usual recoil rules (not while reeling, transforming or
    * riding a host).
    */
   public static void scald(ServerPlayer player, VenomState state) {
      state.fireBlock = Math.max(state.fireBlock, VenomRules.SCALD_REGEN_BLOCK);
      recoil(player, state, VenomLines.RECOIL);
   }

   /** The symbiote reels; {@code line} names what hurt it (fire or a loud sound) so the subtitle never lies about it. */
   static void recoil(ServerPlayer player, VenomState state, HeroLine line) {
      if (!state.form || state.recoilImmune > 0 || state.possessing() || state.is(VenomMove.TRANSFORM) || state.is(VenomMove.REVERT)) {
         return;
      }
      VenomSkills.interrupt(player, state);
      VenomCombat.release(player, state, dev.baranhan.viltrumitecore.hero.control.ReleaseReason.NORMAL_END, null);
      VenomSkills.endShell(player, state);
      state.start(VenomMove.RECOIL);
      state.recoilImmune = VenomRules.RECOIL_IMMUNITY;
      VenomFx.send(player, VenomFx.RECOIL, VenomTargets.center(player), null, 1.0F, 0);
      VenomSounds.play(player, VenomSounds.SHRIEK, 1.1F, VenomSounds.jitter(player, 1.0F));
      HeroSpeech.say(player, line);
   }

   /** Server: a sound was played in the world. */
   /** Venom whose own cosmetic sound is being played right now (server thread only): it never stuns its caster. */
   @javax.annotation.Nullable
   private static java.util.UUID ownSound;

   /**
    * Server: plays one of Venom's own cosmetic layers (e.g. the slam's blast) so it still reaches everyone, still
    * frightens other symbiotes, but does not put its own caster into {@code RECOIL}.
    */
   public static void playOwn(ServerPlayer caster, Vec3 at, SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch) {
      java.util.UUID before = ownSound;
      ownSound = caster.getUUID();
      try {
         caster.level().playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
      } finally {
         ownSound = before;
      }
   }

   /** Pure: whether a loud sound hits this listener (its own cosmetic playback does not). */
   static boolean hears(java.util.UUID listener, @javax.annotation.Nullable java.util.UUID own) {
      return !listener.equals(own);
   }

   public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume) {
      ResourceLocation id = sound.getLocation();
      for (ServerPlayer p : level.players()) {
         if (p instanceof HeroPlayer hp && hp.getHeroId() == HeroId.VENOM && hears(p.getUUID(), ownSound)) {
            VenomState state = VenomState.of(p);
            if (state != null && state.form && loud(id, volume, p.position().distanceTo(at))) {
               recoil(p, state, VenomLines.RECOIL_SOUND);
            }
         }
      }
      if (id == null || !"viltrumitecore".equals(id.getNamespace()) || !id.getPath().startsWith("venom.")) {
         VenomPossession.loudSound(level, at, volume, LOUD.contains(id));
         dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity.loudSound(level, at, id, volume);
      }
   }

   /** Server: an explosion went off (its blast wave is a loud sound for every symbiote near it). */
   public static void explosion(ServerLevel level, Vec3 at, float radius) {
      for (ServerPlayer p : level.players()) {
         if (p instanceof HeroPlayer hp && hp.getHeroId() == HeroId.VENOM && p.position().distanceTo(at) <= Math.max(6.0, radius * 3.0)) {
            VenomState state = VenomState.of(p);
            if (state != null && state.form) {
               recoil(p, state, VenomLines.RECOIL_SOUND);
            }
         }
      }
      VenomPossession.loudSound(level, at, 4.0F, true);
      dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity.loudSound(level, at, new ResourceLocation("minecraft", "entity.generic.explode"), 4.0F);
   }
}
