package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * REINHARD OVERHAUL, Sword Drawn state: a quiet seamless hum that follows every Reinhard whose
 * Reid is out and settled in the hand (the same rule as the gold aura). One looping instance per
 * player, faded in over a second, faded out quickly on sheathing, removal or a level change.
 * Client only; the server state is the synced REID_DRAWN flag.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidDrawnHum {
   public static final float VOLUME = 0.22F;
   public static final int FADE_IN = 20;
   public static final int FADE_OUT_STEP = 3;
   /** Beyond this distance no instance is started (the sound itself attenuates at 16 blocks). */
   public static final double START_RANGE = 24.0;
   private static final Map<Integer, Loop> PLAYING = new HashMap<>();
   private static ClientLevel lastLevel;

   private ReidDrawnHum() {
   }

   /** Pure: next fade level 0..{@link #FADE_IN}: one step up per tick while drawn, faster down otherwise. */
   public static int fade(int level, boolean drawn) {
      return Math.max(0, Math.min(FADE_IN, drawn ? level + 1 : level - FADE_OUT_STEP));
   }

   /** Pure: loop volume at a fade level. */
   public static float volume(int level) {
      return VOLUME * Math.max(0, Math.min(FADE_IN, level)) / FADE_IN;
   }

   static boolean drawn(Player player) {
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      return snapshot != null && !player.isRemoved() && ReidAura.active(snapshot.heroFlags());
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         // The sound engine drops every instance on a level change; forget ours too.
         lastLevel = client.level;
         PLAYING.values().forEach(Loop::end);
         PLAYING.clear();
      }
      if (client.level == null || client.player == null) {
         return;
      }
      PLAYING.values().removeIf(Loop::isStopped);
      for (Player player : client.level.players()) {
         if (!PLAYING.containsKey(player.getId()) && drawn(player) && player.distanceTo(client.player) < START_RANGE) {
            Loop loop = new Loop(player);
            PLAYING.put(player.getId(), loop);
            client.getSoundManager().play(loop);
         }
      }
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Player player;
      private int level = 1;

      Loop(Player player) {
         super(ReinhardSounds.REID_DRAWN_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.player = player;
         this.looping = true;
         this.delay = 0;
         this.volume = ReidDrawnHum.volume(this.level);
         this.follow();
      }

      private void follow() {
         this.x = this.player.getX();
         this.y = this.player.getY() + this.player.getBbHeight() * 0.5;
         this.z = this.player.getZ();
      }

      void end() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         this.level = ReidDrawnHum.fade(this.level, drawn(this.player));
         if (this.level <= 0) {
            this.stop();
            return;
         }
         this.volume = ReidDrawnHum.volume(this.level);
         this.follow();
      }
   }
}
