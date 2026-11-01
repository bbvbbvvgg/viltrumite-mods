package dev.baranhan.viltrumitecore.client.thanos.war;

import dev.baranhan.viltrumitecore.client.hero.WorldPauseClient;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.network.packet.ThanosWarS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client side of the Thanos war ({@link ThanosWarS2CPacket}): the Sanctuary II follows the same
 * {@link ShipPath} as the server from the synced start, hover point and ticks (no position stream),
 * with its hum, the rumble of its arrival, the clouds it breaks through and its shadow; the war sky
 * (smoke of the invasion, the Gauntlet's violet); the siren when the city has none of its own; the
 * player's own input while a Black Order or stone hold has him (never longer than the server says).
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosWarClient {
   private static final Object TINT = new Object();
   private static final int TINT_PRIORITY = 95;
   private static final List<Puff> PUFFS = new ArrayList<>();
   private static boolean active;
   private static boolean leavingOut;
   private static int phase = -1;
   private static int ship = ShipPath.NONE;
   private static double shipTick;
   private static Vec3 start = Vec3.ZERO;
   private static Vec3 hover = Vec3.ZERO;
   /**
    * A jump of the synced path (a correction, the climb away starting under the bob or in mid-glide)
    * is eased out over {@link #BLEND} ticks from the pose last drawn instead of snapping a ship the
    * size of a district.
    */
   private static final double BLEND = 40.0;
   private static Vec3 blendAt = Vec3.ZERO;
   private static float blendYaw;
   private static float blendPitch;
   private static float blendRoll;
   private static double blendLeft;
   private static int flags;
   private static int army;
   private static int strikes;
   private static int hold;
   private static int holdKind;
   private static int holdMax;
   private static long clock;
   private static boolean tinted;
   private static float flash;
   @Nullable
   private static Loop hum;
   @Nullable
   private static Loop siren;

   private ThanosWarClient() {
   }

   /** A soft cloud the ship pushes through. */
   public static final class Puff {
      Vec3 at;
      Vec3 last;
      Vec3 drift = Vec3.ZERO;
      final double radius;
      final float peak;
      final float shade;
      final double spin;
      int age;
      int fade = -1;

      Puff(Vec3 at, double radius, float peak, float shade, double spin) {
         this.at = at;
         this.last = at;
         this.radius = radius;
         this.peak = peak;
         this.shade = shade;
         this.spin = spin;
      }

      Vec3 at(float partial) {
         return this.last.add(this.at.subtract(this.last).scale(partial));
      }

      float alpha(float partial) {
         float in = Mth.clamp((this.age + partial) / 40.0F, 0.0F, 1.0F);
         float out = this.fade < 0 ? 1.0F : Mth.clamp(1.0F - (this.fade + partial) / 160.0F, 0.0F, 1.0F);
         return this.peak * in * out;
      }
   }

   // ---- packets ---------------------------------------------------------------------------------

   public static void accept(ThanosWarS2CPacket p) {
      switch (p.op()) {
         case ThanosWarS2CPacket.STATE -> {
            int was = ship;
            active = true;
            leavingOut = false;
            phase = p.phase();
            jump(() -> {
               ship = p.ship();
               shipTick = p.shipTick();
               start = new Vec3(p.sx(), p.sy(), p.sz());
               hover = new Vec3(p.hx(), p.hy(), p.hz());
            });
            flags = p.flags();
            army = p.a();
            strikes = p.b();
            if (ship == ShipPath.ARRIVING && was != ShipPath.ARRIVING && shipTick < ThanosWarRules.SHIP_ARRIVAL * 0.6) {
               clouds();
            }
         }
         case ThanosWarS2CPacket.NONE -> {
            active = false;
            flags = 0;
            army = 0;
            strikes = 0;
            hold = 0;
            if (ship == ShipPath.HOVER) {
               // the war ended under it (or the player walked out of range): it climbs away
               jump(() -> {
                  ship = ShipPath.LEAVING;
                  shipTick = 0.0;
               });
            }

            if (ship == ShipPath.LEAVING) {
               leavingOut = true;
            } else {
               drop();
            }
         }
         case ThanosWarS2CPacket.HOLD -> {
            hold = Math.max(0, p.a());
            holdMax = Math.max(1, hold);
            holdKind = p.b();
         }
         default -> {
         }
      }
   }

   public static boolean active() {
      return active;
   }

   @Nullable
   public static ThanosPhase phase() {
      ThanosPhase[] all = ThanosPhase.values();
      return active && phase >= 0 && phase < all.length ? all[phase] : null;
   }

   public static boolean held() {
      return hold > 0;
   }

   static boolean shipVisible() {
      return ship == ShipPath.ARRIVING || ship == ShipPath.HOVER || ship == ShipPath.LEAVING;
   }

   static Vec3 shipAt(float partial) {
      Vec3 at = ShipPath.at(ship, shipTick + partial, start, hover);
      double w = blend(partial);
      return w > 0.0 ? at.add(blendAt.scale(w)) : at;
   }

   static float shipYaw(float partial) {
      return ShipPath.yaw(ship, shipTick + partial, start, hover) + blendYaw * (float)blend(partial);
   }

   static float shipPitch(float partial) {
      return ShipPath.pitch(ship, shipTick + partial) + blendPitch * (float)blend(partial);
   }

   static float shipRoll(float partial) {
      return ShipPath.roll(ship, shipTick + partial) + blendRoll * (float)blend(partial);
   }

   /** Weight (1 -> 0, smooth) of the eased-out jump at this partial tick. */
   private static double blend(float partial) {
      if (blendLeft <= 0.0) {
         return 0.0;
      }

      double x = Mth.clamp((blendLeft - partial) / BLEND, 0.0, 1.0);
      return x * x * (3.0 - 2.0 * x);
   }

   /**
    * Applies a change of the path's inputs; while the ship stays in the sky, the difference between
    * the pose drawn before and the new path is eased out (a jump beyond 64 blocks is a new war, not a
    * correction, and is taken as it is).
    */
   private static void jump(Runnable change) {
      boolean seen = shipVisible();
      Vec3 oldAt = seen ? shipAt(0.0F) : Vec3.ZERO;
      float oldYaw = seen ? shipYaw(0.0F) : 0.0F;
      float oldPitch = seen ? shipPitch(0.0F) : 0.0F;
      float oldRoll = seen ? shipRoll(0.0F) : 0.0F;
      change.run();
      blendLeft = 0.0;
      if (!seen || !shipVisible()) {
         return;
      }

      Vec3 d = oldAt.subtract(ShipPath.at(ship, shipTick, start, hover));
      float dYaw = Mth.wrapDegrees(oldYaw - ShipPath.yaw(ship, shipTick, start, hover));
      float dPitch = oldPitch - ShipPath.pitch(ship, shipTick);
      float dRoll = oldRoll - ShipPath.roll(ship, shipTick);
      boolean moved = d.lengthSqr() > 1.0E-4 || Math.abs(dYaw) > 0.01F || Math.abs(dPitch) > 0.01F || Math.abs(dRoll) > 0.01F;
      if (moved && d.lengthSqr() < 64.0 * 64.0) {
         blendAt = d;
         blendYaw = dYaw;
         blendPitch = dPitch;
         blendRoll = dRoll;
         blendLeft = BLEND;
      }
   }

   private static void drop() {
      active = false;
      leavingOut = false;
      ship = ShipPath.NONE;
      shipTick = 0.0;
      blendLeft = 0.0;
      flags = 0;
      hold = 0;
      for (Puff p : PUFFS) {
         if (p.fade < 0) {
            p.fade = 0;
         }
      }

      if (tinted) {
         SkyTint.INSTANCE.remove(TINT);
         tinted = false;
      }

      stopLoops();
   }

   private static void stopLoops() {
      if (hum != null) {
         hum.end();
         hum = null;
      }

      if (siren != null) {
         siren.end();
         siren = null;
      }
   }

   /** The cloud bank on the last third of the glide and round the hover point, above the ship. */
   private static void clouds() {
      PUFFS.clear();
      RandomSource r = RandomSource.create(Double.doubleToLongBits(hover.x * 31.0 + hover.z));
      Vec3 dir = ShipPath.heading(start, hover);
      Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
      for (int i = 0; i < 70; i++) {
         double f = 0.55 + r.nextDouble() * 0.4;
         Vec3 p = ShipPath.at(ShipPath.ARRIVING, f * ThanosWarRules.SHIP_ARRIVAL, start, hover);
         Vec3 at = p.add(side.scale((r.nextDouble() - 0.5) * 220.0)).add(dir.scale((r.nextDouble() - 0.5) * 60.0)).add(0.0, -6.0 + r.nextDouble() * 34.0, 0.0);
         PUFFS.add(new Puff(at, 14.0 + r.nextDouble() * 18.0, 0.22F + r.nextFloat() * 0.25F, 0.85F + r.nextFloat() * 0.2F, r.nextDouble() * 6.0));
      }

      for (int i = 0; i < 40; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = 70.0 + r.nextDouble() * 90.0;
         Vec3 at = hover.add(Math.cos(a) * rr, 22.0 + r.nextDouble() * 26.0, Math.sin(a) * rr);
         PUFFS.add(new Puff(at, 18.0 + r.nextDouble() * 20.0, 0.18F + r.nextFloat() * 0.2F, 0.8F + r.nextFloat() * 0.2F, r.nextDouble() * 6.0));
      }
   }

   // ---- ticking ---------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (active || ship != ShipPath.NONE || !PUFFS.isEmpty()) {
            drop();
            PUFFS.clear();
         }

         return;
      }

      if (mc.isPaused()) {
         return;
      }

      clock++;
      hold = Math.max(0, hold - 1);
      flash = Math.max(0.0F, flash - 0.05F);
      boolean frozen = WorldPauseClient.active();
      if (shipVisible() && !frozen) {
         shipTick++;
         blendLeft = Math.max(0.0, blendLeft - 1.0);
         if (ship == ShipPath.ARRIVING && shipTick >= ThanosWarRules.SHIP_ARRIVAL) {
            ship = ShipPath.HOVER;
            shipTick = 0.0;
         } else if (ship == ShipPath.LEAVING && shipTick >= ThanosWarRules.SHIP_LEAVE) {
            ship = ShipPath.GONE;
            if (leavingOut) {
               drop();
            }
         }
      }

      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      if (shipVisible()) {
         Vec3 at = shipAt(0.0F);
         double d = at.distanceTo(cam);
         if (ship == ShipPath.ARRIVING) {
            // the air shakes as it comes in, most as it passes over
            float near = (float)Mth.clamp(1.0 - d / 520.0, 0.0, 1.0);
            CameraShake.add(0.1F + 0.35F * near * near);
         } else if (ship == ShipPath.LEAVING && shipTick < 80) {
            CameraShake.add(0.12F * (float)Mth.clamp(1.0 - d / 400.0, 0.0, 1.0));
         }

         if (hum == null || hum.isStopped()) {
            hum = new Loop(ThanosWarSounds.SHIP_HUM.get(), () -> shipVisible() ? (float)Mth.clamp(1.15 - shipAt(0.0F).distanceTo(cam()) / 420.0, 0.0, 1.0) : 0.0F,
               () -> shipAt(0.0F), 0.85F);
            mc.getSoundManager().play(hum);
         }
      }

      boolean alarm = active && (flags & ThanosWarS2CPacket.ALARM) != 0;
      if (alarm && (siren == null || siren.isStopped())) {
         siren = new Loop(ThanosWarSounds.SIREN.get(), () -> active && (flags & ThanosWarS2CPacket.ALARM) != 0 ? 0.55F : 0.0F, null, 1.0F);
         mc.getSoundManager().play(siren);
      }

      tickPuffs();
      tint();
   }

   private static Vec3 cam() {
      return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
   }

   private static void tickPuffs() {
      Vec3 s = shipVisible() ? shipAt(0.0F) : null;
      for (Iterator<Puff> it = PUFFS.iterator(); it.hasNext(); ) {
         Puff p = it.next();
         p.age++;
         p.last = p.at;
         if (s != null) {
            Vec3 away = p.at.subtract(s);
            double d = away.length();
            if (d < 95.0 && d > 1.0E-3) {
               p.drift = p.drift.add(away.scale(0.05 * (1.0 - d / 95.0) / d));
            }
         }

         p.at = p.at.add(p.drift).add(0.02, 0.0, 0.01);
         p.drift = p.drift.scale(0.95);
         boolean late = ship == ShipPath.HOVER && shipTick > 400 || ship == ShipPath.LEAVING || ship == ShipPath.GONE || ship == ShipPath.NONE;
         if (late && p.fade < 0) {
            p.fade = 0;
         }

         if (p.fade >= 0 && ++p.fade > 160) {
            it.remove();
         }
      }
   }

   private static void tint() {
      boolean want = active && (shipVisible() || (flags & (ThanosWarS2CPacket.SMOKE | ThanosWarS2CPacket.GAUNTLET)) != 0);
      if (want && !tinted) {
         SkyTint.INSTANCE.push(TINT, TINT_PRIORITY, ThanosWarClient::currentTint);
         tinted = true;
      } else if (!want && tinted) {
         SkyTint.INSTANCE.remove(TINT);
         tinted = false;
      }
   }

   /** The war sky now: smoke, the Gauntlet's violet, and the dark under the ship. */
   static SkyTint.Tint currentTint() {
      float under = 0.0F;
      if (shipVisible()) {
         Vec3 s = shipAt(0.0F);
         Vec3 c = cam();
         double yaw = shipYaw(0.0F) * Mth.DEG_TO_RAD;
         under = SanctuaryRenderer.cover(s, Math.cos(yaw), Math.sin(yaw), c.x, c.z) * (c.y < s.y ? 1.0F : 0.0F);
      }

      boolean smoke = (flags & ThanosWarS2CPacket.SMOKE) != 0;
      boolean gauntlet = (flags & ThanosWarS2CPacket.GAUNTLET) != 0;
      float darken = 0.08F + 0.22F * under + (smoke ? 0.12F : 0.0F) + (gauntlet ? 0.08F : 0.0F);
      if (gauntlet) {
         return new SkyTint.Tint(0.82F, 0.7F, 0.92F, darken, 1.0F, 0.42F, 0.26F, 0.58F, 0.38F);
      }

      if (smoke) {
         return new SkyTint.Tint(0.88F, 0.74F, 0.64F, darken, 1.0F, 0.56F, 0.38F, 0.26F, 0.34F);
      }

      return new SkyTint.Tint(0.9F, 0.88F, 0.9F, darken, 1.0F);
   }

   /** The belly glows when its guns fire (SHIP_LANCE packets call this through the FX). */
   public static void gunFlash() {
      flash = 1.0F;
   }

   // ---- input -----------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onMovement(MovementInputUpdateEvent event) {
      if (hold <= 0 || event.getEntity() != Minecraft.getInstance().player) {
         return;
      }

      var input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   // ---- rendering -------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      float partial = event.getPartialTick();
      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
         if (shipVisible()) {
            float frozen = WorldPauseClient.active() ? 0.0F : partial;
            Vec3 at = shipAt(frozen);
            SanctuaryRenderer.draw(event, level, at, shipYaw(frozen), shipPitch(frozen), shipRoll(frozen),
               flash + ((flags & ThanosWarS2CPacket.SMOKE) != 0 ? 0.25F : 0.0F));
         }
      } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
         if (shipVisible() || !PUFFS.isEmpty()) {
            float frozen = WorldPauseClient.active() ? 0.0F : partial;
            Vec3 at = shipVisible() ? shipAt(frozen) : null;
            float shadow = 0.0F;
            if (at != null) {
               float day = Mth.clamp((level.getSkyDarken(partial) - 0.2F) / 0.8F, 0.0F, 1.0F);
               double alt = at.y - level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
               shadow = day * (float)Mth.clamp(1.4 - alt / 260.0, 0.0, 1.0);
            }

            SanctuaryRenderer.atmosphere(event, level, at, at == null ? 0.0F : shipYaw(frozen), shadow, PUFFS);
         }
      }
   }

   // ---- HUD -------------------------------------------------------------------------------------

   /** War line on the left (army left, ship strikes), and the hold vignette. */
   public static void renderHud(GuiGraphics g, float partial, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui) {
         return;
      }

      if (hold > 0) {
         int rgb = holdKind == 0 ? 0x7FE8FF : holdKind == 1 ? 0x9FB8FF : 0xFFD630;
         float k = Mth.clamp(hold / 8.0F, 0.0F, 1.0F);
         int a = (int)(70.0F * k) << 24 | rgb;
         int band = Math.max(8, height / 6);
         g.fillGradient(0, 0, width, band, a, rgb);
         g.fillGradient(0, height - band, width, height, rgb, a);
         Component c = Component.translatable("hud.viltrumitecore.thanos_war.held").withStyle(ChatFormatting.AQUA);
         g.drawCenteredString(mc.font, c, width / 2, height / 2 + 24, 0xFFFFFF);
      }

      if (!active) {
         return;
      }

      int y = height / 3;
      ThanosPhase ph = phase();
      if (ph != null) {
         g.drawString(mc.font, Component.translatable("hud.viltrumitecore.thanos_war.phase." + ph.name().toLowerCase(java.util.Locale.ROOT))
            .withStyle(ChatFormatting.LIGHT_PURPLE), 6, y, 0xFFFFFF);
         y += 11;
      }

      if ((flags & ThanosWarS2CPacket.ARMY) != 0 || army > 0) {
         g.drawString(mc.font, Component.translatable("hud.viltrumitecore.thanos_war.army", army).withStyle(ChatFormatting.GRAY), 6, y, 0xFFFFFF);
         y += 11;
      }

      if (strikes > 0) {
         g.drawString(mc.font, Component.translatable("hud.viltrumitecore.thanos_war.strikes", strikes).withStyle(ChatFormatting.GOLD), 6, y, 0xFFFFFF);
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      drop();
      PUFFS.clear();
      SanctuaryRenderer.close();
   }

   /** State for tests and the QA log. */
   static String describe() {
      return "active=" + active + " phase=" + phase + " ship=" + ship + "@" + (int)shipTick + " flags=" + flags + " army=" + army + " puffs=" + PUFFS.size()
         + " lod=" + SanctuaryRenderer.lastLod + " quads=" + SanctuaryRenderer.lastQuads + " ns=" + SanctuaryRenderer.lastNanos;
   }

   // ---- loops -----------------------------------------------------------------------------------

   /** A looping sound whose volume (and position) follow suppliers; it stops itself at silence. */
   static final class Loop extends AbstractTickableSoundInstance {
      private final java.util.function.Supplier<Float> target;
      @Nullable
      private final java.util.function.Supplier<Vec3> where;
      private float level;
      private int silent;

      Loop(SoundEvent event, java.util.function.Supplier<Float> target, @Nullable java.util.function.Supplier<Vec3> where, float pitch) {
         super(event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
         this.target = target;
         this.where = where;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.pitch = pitch;
         if (where == null) {
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
         } else {
            this.attenuation = SoundInstance.Attenuation.NONE;
            Vec3 at = where.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         }
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
         float want = this.target.get();
         this.level += Mth.clamp(want - this.level, -0.03F, 0.03F);
         this.silent = want <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2) {
            this.stop();
            return;
         }

         this.volume = Math.max(0.001F, this.level);
         if (this.where != null) {
            Vec3 at = this.where.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         }
      }
   }
}
