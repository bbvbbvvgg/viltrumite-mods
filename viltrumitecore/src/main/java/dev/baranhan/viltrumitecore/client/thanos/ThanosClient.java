package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client state of the Thanos event from {@link ThanosStateS2CPacket}: the stage, the countdown
 * (estimated between packets on the unpaused client clock), the music, the last ten seconds'
 * beeps, the final minute's sky; the HUD is {@link ThanosHud}, the snap scene
 * {@link ThanosSceneClient}. A watchdog drops everything if the server goes silent for 3 s (10 s
 * in the snap scene, which the server sends to every player: silence there is a lag spike, and the
 * scene holds through it); a logout or level unload clears it too.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosClient {
   public static final int WATCHDOG_TICKS = 60;
   public static final int SCENE_WATCHDOG_TICKS = 200;
   private static final Object TINT = new Object();
   private static int stage = ThanosStateS2CPacket.NONE;
   private static int thanosId = -1;
   private static int ticksLeft;
   private static int duration = ThanosRules.DURATION_TICKS;
   private static int phase;
   private static int stageTick;
   private static boolean hud;
   private static long clientTicks;
   private static long received;
   private static int lastBeep = -1;

   private ThanosClient() {
   }

   public static void accept(ThanosStateS2CPacket p) {
      if (p.stage() == ThanosStateS2CPacket.NONE) {
         clear();
         return;
      }

      int before = stage;
      stage = p.stage();
      thanosId = p.thanosId();
      ticksLeft = p.ticksLeft();
      duration = Math.max(1, p.duration());
      phase = p.phase();
      stageTick = p.stageTick();
      hud = p.hud();
      received = clientTicks;
      ThanosSceneClient.accept(before, p);
   }

   public static int stage() {
      return stage;
   }

   public static boolean active() {
      return stage != ThanosStateS2CPacket.NONE;
   }

   /** Does this player see the fight (HUD, music) rather than only the universe scene? */
   public static boolean hud() {
      return hud;
   }

   public static int thanosId() {
      return thanosId;
   }

   public static ThanosPhase phase() {
      return ThanosPhase.byIndex(phase);
   }

   /** The Thanos entity of the event in this client's level, if loaded. */
   @Nullable
   public static ThanosEntity thanos() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || thanosId < 0) {
         return null;
      }

      Entity e = mc.level.getEntity(thanosId);
      return e instanceof ThanosEntity t && !t.isRemoved() ? t : null;
   }

   /** Stage clock: the server's stage tick plus the local unpaused ticks since that packet (capped). */
   public static float stageTime(float partial) {
      return ThanosSceneTimeline.estimate(stageTick, clientTicks - received, partial);
   }

   /** Ticks left on the timer, estimated between packets (the timer holds while the world is stopped). */
   public static int ticksLeft() {
      if (stage != ThanosStateS2CPacket.FIGHT || dev.baranhan.viltrumitecore.client.hero.WorldPauseClient.active()) {
         return ticksLeft;
      }

      return (int)Math.max(0L, ticksLeft - (clientTicks - received));
   }

   public static int duration() {
      return duration;
   }

   public static boolean finalMinute() {
      return stage == ThanosStateS2CPacket.FIGHT && ticksLeft() <= ThanosRules.FINAL_TICKS;
   }

   /** Ticks into his defeat: the stage clock when it is known, else his own state clock. */
   public static float victoryTime(ThanosEntity e, float partial) {
      if (stage == ThanosStateS2CPacket.VICTORY && e.getId() == thanosId) {
         return stageTime(partial);
      }

      return Math.max(0.0F, e.tickCount + partial - e.clientStateStart);
   }

   public static long clientTicks() {
      return clientTicks;
   }

   static void clear() {
      int before = stage;
      stage = ThanosStateS2CPacket.NONE;
      thanosId = -1;
      hud = false;
      lastBeep = -1;
      ThanosMusic.stop();
      SkyTint.INSTANCE.remove(TINT);
      ThanosSceneClient.accept(before, null);
      ThanosFx.clear();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (active()) {
            clear();
         }

         return;
      }

      if (mc.isPaused()) {
         return;
      }

      clientTicks++;
      ThanosSceneClient.tick(mc);
      if (!active()) {
         ThanosMusic.update(false, false, 0.0F);
         return;
      }

      boolean scene = stage == ThanosStateS2CPacket.SNAP || stage == ThanosStateS2CPacket.SNAP_WAIT;
      if (clientTicks - received > (scene ? SCENE_WATCHDOG_TICKS : WATCHDOG_TICKS)) {
         clear();
         return;
      }

      boolean fight = stage == ThanosStateS2CPacket.FIGHT || stage == ThanosStateS2CPacket.INTRO;
      boolean late = finalMinute();
      ThanosMusic.update(hud && fight, late, 1.0F);
      if (hud && late) {
         SkyTint.INSTANCE.push(TINT, 4, () -> new SkyTint.Tint(1.0F, 0.62F, 0.6F, 0.14F, 1.0F));
      } else {
         SkyTint.INSTANCE.remove(TINT);
      }

      if (hud && stage == ThanosStateS2CPacket.FIGHT) {
         int seconds = ThanosRules.secondsLeft(ticksLeft());
         if (seconds <= 10 && seconds >= 1 && seconds != lastBeep) {
            lastBeep = seconds;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ThanosSounds.COUNTDOWN.get(), 0.8F + 0.045F * (10 - seconds), 0.9F));
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide() && active() && stage != ThanosStateS2CPacket.SNAP) {
         // a dimension change mid-fight: the next packet brings the state back
         ThanosFx.clear();
      }
   }
}
