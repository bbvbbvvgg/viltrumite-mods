package dev.baranhan.viltrumitecore.client.hero.speech;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.mixin.GuiTimersAccessor;
import dev.baranhan.viltrumitecore.config.ViltrumiteClientConfig;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client side of the cinematic hero subtitles: receives the server's lines,
 * keeps one on screen ({@link SubtitleQueue}) and draws it as a HUD overlay
 * above the hotbar and status bars, under the chat. Only draws: no input,
 * camera or gameplay effect. Cleared on logout, world change and respawn.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class HeroSubtitles {
   public static final String OVERLAY_ID = "hero_subtitles";
   private static final SubtitleQueue<Cue> QUEUE = new SubtitleQueue<>();
   private static long ticks;
   @Nullable
   private static ClientLevel level;
   @Nullable
   private static Shown shown;
   /** Smoothed bottom edge (GUI y); NaN = snap on the next frame. */
   private static float bottom = Float.NaN;
   private static int bottomScreenHeight;
   private static long lastFrame;
   /** ForgeGui left height right after an armor row that drew nothing (no armor), or -1. */
   private static int emptyArmorLeft = -1;

   /** One received line, its text resolved in the client language at arrival. */
   record Cue(int serial, UUID speaker, int entityId, String name, @Nullable HeroId hero, String lineId, List<String> fragments, int priority,
      int durationTicks, boolean own) implements SubtitleQueue.Cue {
   }

   /** The laid-out shown cue: re-laid when the screen width changes. */
   private record Shown(Cue cue, int screenWidth, SubtitleLayout.Result layout, int[] pageTicks) {
   }

   private HeroSubtitles() {
   }

   public static void receive(HeroLineS2CPacket packet) {
      double now = now(0.0F);
      switch (packet.op()) {
         case HeroLineS2CPacket.CLEAR -> clear();
         case HeroLineS2CPacket.STOP -> QUEUE.stop(packet.serial(), now);
         default -> {
            if (!ViltrumiteClientConfig.INSTANCE.heroSubtitles) {
               return;
            }

            Cue cue = resolve(packet);
            if (cue != null) {
               QUEUE.offer(cue, now);
            }
         }
      }
   }

   @Nullable
   private static Cue resolve(HeroLineS2CPacket packet) {
      Language language = Language.getInstance();
      List<String> fragments = new ArrayList<>(packet.fragments().size());
      for (String fragment : packet.fragments()) {
         String text = packet.literal() ? fragment : language.getOrDefault(fragment);
         if (!text.isBlank()) {
            fragments.add(text.replace("\\n", "\n"));
         }
      }

      if (fragments.isEmpty()) {
         return null;
      }

      String name = !packet.speakerKey().isEmpty() && language.has(packet.speakerKey()) ? language.getOrDefault(packet.speakerKey()) : packet.speakerName();
      Minecraft mc = Minecraft.getInstance();
      boolean own = mc.player != null && mc.player.getUUID().equals(packet.speaker());
      return new Cue(packet.serial(), packet.speaker(), packet.entityId(), name.toUpperCase(Locale.ROOT), HeroId.fromKey(packet.heroKey()),
         packet.lineId(), List.copyOf(fragments), packet.priority(), packet.durationTicks(), own);
   }

   public static void clear() {
      QUEUE.clear();
      shown = null;
   }

   private static double now(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      return ticks + (mc.isPaused() ? 0.0F : partialTick);
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != level) {
         // New world or dimension: lines of the old one are over.
         level = mc.level;
         clear();
      }

      if (mc.level == null || mc.isPaused() || QUEUE.isEmpty()) {
         return;
      }

      ticks++;
      double now = ticks;
      if (mc.player != null && mc.player.isDeadOrDying()) {
         QUEUE.stopIf(Cue::own, now);
      }

      ClientLevel world = mc.level;
      QUEUE.stopIf(cue -> {
         Entity speaker = world.getEntity(cue.entityId());
         return speaker instanceof AbstractClientPlayer player && player.getUUID().equals(cue.speaker()) && player.isDeadOrDying();
      }, now);
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   @SubscribeEvent
   public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
      QUEUE.stopIf(Cue::own, now(0.0F));
   }

   /** The HUD layer (registered under the chat). */
   public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || QUEUE.isEmpty()) {
         bottom = Float.NaN;
         return;
      }

      Font font = mc.font;
      double now = now(partialTick);
      int reserve = rightReserve(mc, font);
      // Room for the panel: the hero ability bar on the right edge stays uncovered on narrow screens.
      int room = reserve > 0 ? Math.min(width, width - reserve - 2 + SubtitleLayout.SCREEN_MARGIN) : width;
      SubtitleQueue.Slot<Cue> slot = QUEUE.update(now, cue -> measure(cue, font, room));
      if (slot == null) {
         shown = null;
         return;
      }

      if (!ViltrumiteClientConfig.INSTANCE.heroSubtitles || mc.player.isDeadOrDying()) {
         return;
      }

      Shown current = shown;
      if (current == null || current.cue() != slot.cue() || current.screenWidth() != room) {
         current = lay(slot.cue(), font, room);
         if (current == null) {
            QUEUE.stop(slot.cue().serial(), now);
            return;
         }

         shown = current;
         slot.length(SubtitleTiming.length(current.pageTicks()));
      }

      SubtitleTiming.Frame frame = SubtitleTiming.frame(current.pageTicks(), now - slot.started(), slot.forcedAt());
      if (frame.alpha() <= 0.004F) {
         return;
      }

      SubtitleLayout.Result layout = current.layout();
      // Forge reserves the armor row even without armor; when nothing was drawn there and nothing
      // came after it on the left, the panel may use that empty row (as in the reference).
      int left = gui.leftHeight == emptyArmorLeft ? gui.leftHeight - 10 : gui.leftHeight;
      int bars = Math.max(left, gui.rightHeight);
      GuiTimersAccessor timers = (GuiTimersAccessor)gui;
      boolean actionBar = timers.viltrumitecore$overlayMessageTime() > 0;
      boolean itemName = timers.viltrumitecore$toolHighlightTimer() > 0 && !timers.viltrumitecore$lastToolHighlight().isEmpty();
      boolean survival = mc.gameMode != null && mc.gameMode.canHurtPlayer();
      int target = SubtitleLayout.bottom(height, bars, actionBar, itemName, survival);
      if (dev.baranhan.viltrumitecore.client.dio.RollerCinema.active()) {
         // DIO's Road Roller cinematic: the panel sits in the lower letterbox bar, off the subject.
         target = height - 4;
      }

      float edge = smoothBottom(target, height);
      double scale = mc.getWindow().getGuiScale();
      float x = (width - layout.totalWidth()) / 2.0F;
      if (reserve > 0) {
         x = Math.max(SubtitleLayout.SCREEN_MARGIN, Math.min(x, width - reserve - 2 - layout.totalWidth()));
      }

      float y = edge - layout.height() + frame.slide();
      x = snap((float)Math.floor(x), scale);
      y = snap(y, scale);
      SubtitleLayout.Page page = layout.pages().get(Math.min(frame.page(), layout.pages().size() - 1));
      graphics.pose().pushPose();
      graphics.pose().translate(x, y, 0.0F);
      SubtitleRenderer.panel(graphics, layout.textWidth(), layout.height(), frame.alpha());
      SubtitleRenderer.text(graphics, font, layout, page, frame.alpha(), frame.textAlpha(), snap(frame.textDrift(), scale));
      if (layout.hasPortrait()) {
         graphics.pose().translate(layout.textWidth() + SubtitleLayout.GAP, 0.0F, 0.0F);
         SubtitleRenderer.panel(graphics, layout.portraitWidth(), layout.height(), frame.alpha());
      }

      graphics.pose().popPose();
      if (layout.hasPortrait()) {
         Cue cue = slot.cue();
         float px = x + layout.textWidth() + SubtitleLayout.GAP;
         HeroPortraits.Frame box = new HeroPortraits.Frame(px + 2.0F, y + 2.0F, layout.portraitWidth() - 4.0F, layout.height() - 4.0F, frame.alpha(),
            Util.getMillis() / 1000.0, cue.speaker(), speaker(mc, cue));
         HeroPortraits.resolve(cue.hero()).render(graphics, box);
      }
   }

   /**
    * GUI px at the right screen edge taken by the hero ability bar (5 px margin, 22 px bar,
    * key labels left of the slots; see ViltrumiteInGameHudMixin), or 0 when it is not shown.
    */
   private static int rightReserve(Minecraft mc, Font font) {
      if (mc.options.hideGui || mc.player == null || !HeroRegistry.get(mc.player).hasAbilityPanel(mc.player)) {
         return 0;
      }

      int label = 0;
      int cap = font.width("SHFT");
      for (KeyMapping key : new KeyMapping[]{AbilityInputManager.abilityKey1, AbilityInputManager.abilityKey2, AbilityInputManager.abilityKey3,
         AbilityInputManager.abilityKey4, AbilityInputManager.abilityKey5, AbilityInputManager.abilityKey6}) {
         if (key != null) {
            label = Math.max(label, Math.min(cap, font.width(key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT))));
         }
      }

      return 5 + 22 - 3 + label + 6;
   }

   private static int measure(Cue cue, Font font, int width) {
      Shown laid = lay(cue, font, width);
      if (laid == null) {
         return 0;
      }

      shown = laid;
      return SubtitleTiming.length(laid.pageTicks());
   }

   @Nullable
   private static Shown lay(Cue cue, Font font, int width) {
      SubtitleLayout.Result layout = SubtitleLayout.layout(cue.name(), cue.fragments(), font::width, width, true);
      if (layout == null) {
         return null;
      }

      int[] pages = SubtitleTiming.pageTicks(layout.pageChars(), cue.durationTicks(), ViltrumiteClientConfig.INSTANCE.heroSubtitleDurationScale);
      return new Shown(cue, width, layout, pages);
   }

   @Nullable
   private static AbstractClientPlayer speaker(Minecraft mc, Cue cue) {
      if (mc.level == null) {
         return null;
      }

      Entity entity = mc.level.getEntity(cue.entityId());
      if (entity instanceof AbstractClientPlayer player && player.getUUID().equals(cue.speaker())) {
         return player;
      }

      return mc.level.getPlayerByUUID(cue.speaker()) instanceof AbstractClientPlayer player ? player : null;
   }

   /** Eases the panel up over a shown action bar / item name quickly, and back down a little slower. */
   /** Moves the panel smoothly when the HUD below it changes (status rows, item name, action bar); snaps on a new screen size. */
   private static float smoothBottom(int target, int screenHeight) {
      long nowNanos = System.nanoTime();
      float dt = lastFrame == 0L ? 1.0F : Math.min(0.25F, (nowNanos - lastFrame) / 1.0E9F);
      lastFrame = nowNanos;
      if (Float.isNaN(bottom) || screenHeight != bottomScreenHeight) {
         bottom = target;
         bottomScreenHeight = screenHeight;
         return bottom;
      }

      float tau = target < bottom ? 0.07F : 0.16F;
      bottom += (target - bottom) * (1.0F - (float)Math.exp(-dt / tau));
      if (Math.abs(target - bottom) < 0.05F) {
         bottom = target;
      }

      return bottom;
   }

   @SubscribeEvent
   public static void onGuiPre(RenderGuiEvent.Pre event) {
      emptyArmorLeft = -1;
   }

   @SubscribeEvent
   public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) && mc.player != null && mc.gui instanceof ForgeGui gui) {
         emptyArmorLeft = mc.player.getArmorValue() <= 0 ? gui.leftHeight : -1;
      }
   }

   private static float snap(float value, double scale) {
      return (float)(Math.round(value * scale) / scale);
   }

   @EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), OVERLAY_ID, HeroSubtitles::render);
      }
   }
}
