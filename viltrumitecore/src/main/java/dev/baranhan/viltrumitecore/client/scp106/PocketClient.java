package dev.baranhan.viltrumitecore.client.scp106;

import dev.baranhan.viltrumitecore.scp.scp106.Scp106Entity;
import dev.baranhan.viltrumitecore.scp.scp106.Scp106Sounds;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Kind;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Room;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Zone;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client side of the pocket dimension (fed by Scp106S2CPacket): the instance the player is in, its layout (built
 * locally from the synced seed, off the render thread), fog distance and colour plus a lightmap tint per zone, the
 * ambience beds (hum, void drone, water) and positional one-shots around the listener (far steps, creaks, metal,
 * drips, whispers), a slow camera sway, the capture darkness, scare flashes, the escape fade, the sigil/key HUD,
 * the notes (a book screen) and the victim being pulled into the floor for everybody watching. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class PocketClient {
   private static final RandomSource RANDOM = RandomSource.create();
   public static int slot = -1;
   public static int flags;
   public static long seed;
   @Nullable
   private static PocketLayout layout;
   @Nullable
   private static CompletableFuture<PocketLayout> pending;
   private static long pendingSeed;
   private static int clock;
   private static int captureStart = -10_000;
   private static boolean captureDrag;
   private static int arriveStart = -10_000;
   private static int escapeStart = -10_000;
   private static int scareStart = -10_000;
   private static int scareKind;
   private static int nextOneShot = 100;
   private static float fogFar = 32.0F;
   private static float fogTarget = 32.0F;
   private static final float[] FOG = {0.08F, 0.06F, 0.04F};
   private static final float[] FOG_TARGET = {0.08F, 0.06F, 0.04F};
   private static final float[] TINT = {1.0F, 1.0F, 1.0F};
   private static final float[] TINT_TARGET = {1.0F, 1.0F, 1.0F};
   private static float danger;
   @Nullable
   private static Zone zone;
   @Nullable
   private static Kind kind;
   private static final Map<String, PocketLoopSound> BEDS = new HashMap<>();
   private static final Map<Integer, Scp106Entity> HELD = new HashMap<>();
   private static boolean pushed;

   private PocketClient() {
   }

   // ------------------------------------------------------------------ packets

   public static void onPacket(byte kindId, int a, int b, long seedIn) {
      switch (kindId) {
         case 0 -> {
            boolean was = slot >= 0;
            slot = a;
            flags = b;
            if (a >= 0 && seedIn != seed) {
               seed = seedIn;
               layout = null;
            }
            if (a >= 0 && !was) {
               arriveStart = clock;
            }
            if (a < 0) {
               stopBeds();
            }
         }
         case 1 -> {
            captureStart = clock;
            captureDrag = b == 1;
         }
         case 2 -> openNote(a, b == 1);
         case 3 -> {
            scareKind = a;
            scareStart = clock;
            if (a == 1 || a == 2 || a == 3) {
               ui(Scp106Sounds.SCARE.get(), 1.0F, 0.9F + RANDOM.nextFloat() * 0.2F);
            }
         }
         case 4 -> {
            escapeStart = clock;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(10, 70, 30);
            mc.gui.setSubtitle(Component.translatableWithFallback("scp106.escape.route." + a, ""));
            mc.gui.setTitle(Component.translatableWithFallback("scp106.escape.title", "Out."));
         }
         default -> {
         }
      }
   }

   private static void openNote(int idx, boolean diary) {
      Component[] pages = diary
         ? new Component[]{Component.translatableWithFallback("scp106.diary." + idx + ".1", "..."), Component.translatableWithFallback("scp106.diary." + idx + ".2", "...")}
         : new Component[]{Component.translatableWithFallback("scp106.note." + idx, "...")};
      Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
         @Override
         public int getPageCount() {
            return pages.length;
         }

         @Override
         public FormattedText getPageRaw(int index) {
            return pages[Mth.clamp(index, 0, pages.length - 1)];
         }
      }));
   }

   // ------------------------------------------------------------------ queries

   /** One line for the opt-in QA driver (SubtitlePlaytest "scp106state"): pocket state, zone, fog, ambience. */
   public static String qaSummary() {
      StringBuilder b = new StringBuilder();
      b.append(String.format(java.util.Locale.ROOT, "pocket slot=%d flags=%d seed=%x layout=%s zone=%s kind=%s fog=%.1f/%.1f rgb=%.2f,%.2f,%.2f danger=%.2f beds=",
         slot, flags, seed, layout != null, zone, kind, fogFar, fogTarget, FOG[0], FOG[1], FOG[2], danger));
      for (Map.Entry<String, PocketLoopSound> e : BEDS.entrySet()) {
         b.append(e.getKey()).append(e.getValue().isStopped() ? "(stopped)" : "").append(',');
      }
      return b.append(" held=").append(HELD.keySet()).toString();
   }

   public static boolean inPocket() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && PocketWorld.is(mc.level);
   }

   static Vec3 fogColor() {
      return new Vec3(FOG[0], FOG[1], FOG[2]);
   }

   static float[] tint() {
      return TINT;
   }

   @Nullable
   private static PocketLayout layout() {
      if (slot < 0) {
         return null;
      }
      if (layout != null && layout.seed == seed && layout.slot == slot) {
         return layout;
      }
      if (pending == null || pendingSeed != seed) {
         final long s = seed;
         final int sl = slot;
         pendingSeed = s;
         pending = CompletableFuture.supplyAsync(() -> PocketLayout.of(s, sl), Util.backgroundExecutor());
      }
      if (pending.isDone()) {
         try {
            layout = pending.getNow(null);
         } catch (RuntimeException ex) {
            layout = null;
         }
         pending = null;
      }
      return layout;
   }

   // ------------------------------------------------------------------ ticking

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      clock++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      HELD.clear();
      if (mc.level != null) {
         for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Scp106Entity s && s.heldId() >= 0) {
               HELD.put(s.heldId(), s);
            }
         }
      }
      if (p == null || !inPocket()) {
         stopBeds();
         zone = null;
         kind = null;
         return;
      }
      PocketLayout l = layout();
      Room room = null;
      if (l != null) {
         int lx = p.getBlockX() - PocketLayout.slotOriginX(slot);
         int lz = p.getBlockZ() - PocketLayout.slotOriginZ(slot);
         room = l.roomAtBlock(lx, lz);
      }
      zone = room == null ? Zone.DECAY : room.zone;
      kind = room == null ? null : room.kind;
      // the Old Man near: fog closes in and turns red
      float near = 0.0F;
      for (Entity e : mc.level.entitiesForRendering()) {
         if (e instanceof Scp106Entity s && !s.isInvisible()) {
            float d = (float)Math.sqrt(s.distanceToSqr(p));
            near = Math.max(near, Mth.clamp(1.0F - (d - 6.0F) / 26.0F, 0.0F, 1.0F));
         }
      }
      danger += (near - danger) * 0.05F;
      preset(zone, kind);
      fogTarget *= 1.0F - 0.45F * danger;
      FOG_TARGET[0] += (0.16F - FOG_TARGET[0]) * danger;
      FOG_TARGET[1] *= 1.0F - 0.7F * danger;
      FOG_TARGET[2] *= 1.0F - 0.7F * danger;
      fogFar += (fogTarget - fogFar) * 0.04F;
      for (int i = 0; i < 3; i++) {
         FOG[i] += (FOG_TARGET[i] - FOG[i]) * 0.04F;
         TINT[i] += (TINT_TARGET[i] - TINT[i]) * 0.04F;
      }
      beds(mc, zone, kind);
      oneShots(mc, p, zone, kind);
   }

   /** Fog distance, colour and light tint of each zone (room kinds override: huge spaces see far, sanctuaries warm). */
   private static void preset(Zone z, @Nullable Kind k) {
      float far;
      float r;
      float g;
      float b;
      switch (z) {
         case VOID -> { far = 64; r = 0.015F; g = 0.015F; b = 0.025F; }
         case FLOODED -> { far = 20; r = 0.04F; g = 0.085F; b = 0.075F; }
         case FALSE_SAFE -> { far = 42; r = 0.17F; g = 0.16F; b = 0.12F; }
         case DOMAIN -> { far = 16; r = 0.13F; g = 0.025F; b = 0.015F; }
         case LABYRINTH -> { far = 18; r = 0.06F; g = 0.06F; b = 0.06F; }
         default -> { far = 24; r = 0.1F; g = 0.075F; b = 0.045F; }
      }
      if (k != null) {
         switch (k) {
            case CATHEDRAL -> { far = 96; r = 0.05F; g = 0.04F; b = 0.06F; }
            case CHASM -> { far = 80; r = 0.03F; g = 0.03F; b = 0.04F; }
            case ESCHER -> { far = 56; r = 0.05F; g = 0.05F; b = 0.08F; }
            case SANCTUARY -> { far = 48; r = 0.2F; g = 0.14F; b = 0.07F; }
            case GALLERY -> far = Math.min(far, 30);
            case THRONE -> { far = 28; r = 0.15F; g = 0.02F; b = 0.01F; }
            default -> {
            }
         }
      }
      fogTarget = far;
      FOG_TARGET[0] = r;
      FOG_TARGET[1] = g;
      FOG_TARGET[2] = b;
      float m = Math.max(r, Math.max(g, b));
      // light tint: lean toward the fog hue (never darker than 70 %)
      for (int i = 0; i < 3; i++) {
         float c = i == 0 ? r : i == 1 ? g : b;
         TINT_TARGET[i] = m <= 0 ? 1.0F : 0.7F + 0.3F * (c / m);
      }
      if (k == Kind.SANCTUARY) {
         TINT_TARGET[0] = 1.0F;
         TINT_TARGET[1] = 0.9F;
         TINT_TARGET[2] = 0.72F;
      }
   }

   private static void beds(Minecraft mc, Zone z, @Nullable Kind k) {
      boolean big = k == Kind.CATHEDRAL || k == Kind.CHASM || k == Kind.ESCHER || z == Zone.VOID;
      boolean wet = z == Zone.FLOODED || k == Kind.FLOODED || k == Kind.FLOODED_CORRIDOR;
      boolean calm = k == Kind.SANCTUARY;
      bed(mc, "hum", Scp106Sounds.HUM.get(), calm ? 0.1F : big ? 0.25F : 0.55F + 0.3F * danger);
      bed(mc, "void", Scp106Sounds.VOID_DRONE.get(), calm ? 0.0F : big ? 0.75F : z == Zone.DOMAIN ? 0.45F : 0.15F);
      bed(mc, "water", Scp106Sounds.WATER.get(), wet ? 0.7F : 0.0F);
   }

   /** Ticks a fresh bed may stay inactive in the engine before it counts as dropped (the engine starts it a bit later). */
   static final int START_GRACE_TICKS = 40;
   /** Delay between two restarts of the same bed, so a broken sound device does not get a new instance every tick. */
   static final int RETRY_TICKS = 20;

   private static void bed(Minecraft mc, String name, SoundEvent sound, float target) {
      PocketLoopSound s = BEDS.get(name);
      boolean gone = s == null || abandoned(s.isStopped(), mc.getSoundManager().isActive(s), clock - s.born);
      if (gone) {
         // F3+T, a resource-pack/language change or a sound-device restart drop the instance without stopping it:
         // detect that independently of the target volume and start a new one (after the retry delay)
         if (s != null && !s.isStopped()) {
            s.halt();
         }
         if (target <= 0.0F || s != null && clock - s.born < RETRY_TICKS) {
            return;
         }
         s = new PocketLoopSound(sound);
         s.born = clock;
         BEDS.put(name, s);
         mc.getSoundManager().play(s);
      }
      s.target = target;
   }

   /** Pure: a bed is to be replaced (stopped, or inactive in the engine past the start grace). */
   static boolean abandoned(boolean stopped, boolean active, int age) {
      return stopped || !active && age > START_GRACE_TICKS;
   }

   private static void stopBeds() {
      for (PocketLoopSound s : BEDS.values()) {
         s.halt();
      }
      BEDS.clear();
   }

   @Nullable
   private static SoundEvent lastOneShot;

   private static void oneShots(Minecraft mc, LocalPlayer p, Zone z, @Nullable Kind k) {
      if (--nextOneShot > 0) {
         return;
      }
      boolean calm = k == Kind.SANCTUARY;
      nextOneShot = (calm ? 300 : 110) + RANDOM.nextInt(calm ? 400 : 220);
      if (calm) {
         return;
      }
      // the place itself (drips, creaks, pipes) most of the time; steps and voices rarely, never twice running
      SoundEvent s;
      boolean whisperZone = z == Zone.DOMAIN || z == Zone.FALSE_SAFE;
      do {
         int roll = RANDOM.nextInt(100);
         if (roll < (whisperZone ? 18 : 6)) {
            s = Scp106Sounds.WHISPER.get();
         } else if (roll < 22) {
            s = Scp106Sounds.FAR_STEPS.get();
         } else if (roll < 48) {
            s = Scp106Sounds.CREAK.get();
         } else if (roll < 70) {
            s = Scp106Sounds.METAL.get();
         } else {
            s = Scp106Sounds.DRIP.get();
         }
      } while (s == lastOneShot && s != Scp106Sounds.DRIP.get());
      lastOneShot = s;
      double ang = RANDOM.nextDouble() * Math.PI * 2.0;
      double d = 8.0 + RANDOM.nextDouble() * 14.0;
      double x = p.getX() + Math.cos(ang) * d;
      double y = p.getEyeY() + (RANDOM.nextDouble() - 0.4) * 6.0;
      double zz = p.getZ() + Math.sin(ang) * d;
      mc.getSoundManager().play(new SimpleSoundInstance(s, SoundSource.AMBIENT, 0.6F + RANDOM.nextFloat() * 0.4F,
         0.85F + RANDOM.nextFloat() * 0.3F, RandomSource.create(RANDOM.nextLong()), x, y, zz));
   }

   private static void ui(SoundEvent s, float volume, float pitch) {
      Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(s.getLocation(), SoundSource.HOSTILE, volume, pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
   }

   // ------------------------------------------------------------------ view

   @SubscribeEvent
   public static void onFog(ViewportEvent.RenderFog event) {
      if (!inPocket() || event.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
         return;
      }
      float far = fogFar;
      event.setNearPlaneDistance(far * 0.05F);
      event.setFarPlaneDistance(far);
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onFogColor(ViewportEvent.ComputeFogColor event) {
      if (inPocket()) {
         event.setRed(FOG[0]);
         event.setGreen(FOG[1]);
         event.setBlue(FOG[2]);
      }
   }

   @SubscribeEvent
   public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
      float t = (float)(clock + event.getPartialTick());
      float since = t - captureStart;
      if (since >= 0 && since < 60) {
         float k = Mth.clamp(since / 52.0F, 0.0F, 1.0F);
         event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * 6.0F * k);
         event.setPitch(event.getPitch() + 18.0F * k * k);
      }
      if (inPocket()) {
         float sway = 0.9F + (zone == Zone.DOMAIN ? 1.6F : 0.0F) + 2.0F * danger;
         event.setRoll(event.getRoll() + Mth.sin(t * 0.021F) * sway + Mth.sin(t * 0.053F) * sway * 0.35F);
      }
   }

   @SubscribeEvent
   public static void onFov(ComputeFovModifierEvent event) {
      if (inPocket()) {
         float t = clock;
         event.setNewFovModifier(event.getNewFovModifier() * (1.0F + 0.015F * Mth.sin(t * 0.04F) - 0.06F * danger));
      }
   }

   // ------------------------------------------------------------------ overlays

   @SubscribeEvent
   public static void onGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float t = clock + event.getPartialTick();
      float since = t - captureStart;
      if (since >= 0 && since < 75) {
         // the floor rises over the eyes: black from the bottom, the edges first
         float k = Mth.clamp(since / 52.0F, 0.0F, 1.0F);
         int top = (int)(h * (1.0F - k * 1.05F));
         int a = (int)(Mth.clamp(k * 1.4F, 0.0F, 1.0F) * 255);
         g.fillGradient(0, Math.max(0, top - 60), w, Math.max(0, top), 0x00000000, a << 24 | 0x0A0603);
         g.fill(0, Math.max(0, top), w, h, a << 24 | 0x0A0603);
         if (since > 52) {
            g.fill(0, 0, w, h, 0xFF000000);
         }
         vignette(g, w, h, k);
      }
      float arrive = t - arriveStart;
      if (arrive >= 0 && arrive < 60 && slot >= 0) {
         g.fill(0, 0, w, h, (int)((1.0F - arrive / 60.0F) * 255) << 24);
      }
      float esc = t - escapeStart;
      if (esc >= 0 && esc < 70) {
         int a = (int)((1.0F - esc / 70.0F) * 255);
         g.fill(0, 0, w, h, a << 24 | 0xF2EEE4);
      }
      float sc = t - scareStart;
      int dur = switch (scareKind) {
         case 0 -> 45;
         case 1 -> 35;
         case 2 -> 55;
         case 3 -> 18;
         default -> 70;
      };
      if (sc >= 0 && sc < dur) {
         float f = 1.0F - sc / dur;
         int rgb = switch (scareKind) {
            case 1 -> sc < 4 ? 0xFFFFF8 : 0x000000;
            case 2 -> 0x3A0503;
            case 3 -> 0x120404;
            default -> 0x000000;
         };
         g.fill(0, 0, w, h, (int)(Mth.clamp(f * 1.3F, 0.0F, 1.0F) * 255) << 24 | rgb);
      }
      if (inPocket()) {
         vignette(g, w, h, 0.35F + 0.5F * danger);
         hud(g, mc, w);
      }
   }

   private static void vignette(GuiGraphics g, int w, int h, float k) {
      int a = (int)(Mth.clamp(k, 0.0F, 1.0F) * 200);
      int band = Math.max(8, h / 5);
      g.fillGradient(0, 0, w, band, a << 24, 0);
      g.fillGradient(0, h - band, w, h, 0, a << 24);
   }

   private static void hud(GuiGraphics g, Minecraft mc, int w) {
      int sig = flags & 7;
      boolean key = (flags & 8) != 0;
      if (sig == 0 && !key) {
         return;
      }
      int x = 8;
      int y = 8;
      String[] n = {"I", "II", "III"};
      for (int i = 0; i < 3; i++) {
         boolean got = (sig & 1 << i) != 0;
         g.drawString(mc.font, n[i], x, y, got ? 0xD8C9A0 : 0x3A332A, true);
         x += mc.font.width(n[i]) + 6;
      }
      if (key) {
         g.drawString(mc.font, Component.translatableWithFallback("scp106.hud.key", "key"), x + 4, y, 0xB8A27A, true);
      }
   }

   // ------------------------------------------------------------------ the victim pulled into the floor

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      Scp106Entity by = HELD.get(event.getEntity().getId());
      pushed = false;
      if (by == null) {
         return;
      }
      float since = (by.tickCount + event.getPartialTick() - by.clientStateStart) / 52.0F;
      float k = Mth.clamp(since, 0.0F, 1.0F);
      event.getPoseStack().pushPose();
      event.getPoseStack().translate(0.0, -k * k * 1.95, 0.0);
      pushed = true;
   }

   @SubscribeEvent
   public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
      if (pushed) {
         event.getPoseStack().popPose();
         pushed = false;
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      slot = -1;
      flags = 0;
      layout = null;
      pending = null;
      stopBeds();
      HELD.clear();
   }
}
