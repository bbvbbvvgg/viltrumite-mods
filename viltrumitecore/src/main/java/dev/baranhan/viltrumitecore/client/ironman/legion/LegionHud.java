package dev.baranhan.viltrumitecore.client.ironman.legion;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.gui.KeyLabels;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.jarvis.JarvisVoice;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionTactics;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Compact Legion column at the right edge (design §9): order and target
 * priority, then one row per suit with its mark, durability bar and what it
 * is doing; the call cooldown when the Legion is back. The Iron Man HUD owns
 * the left edge; the column sits left of the ability bar and its key labels
 * (the first playtest drew it over them), so nothing overlaps. Without the helmet (JARVIS hints off)
 * the Legion's JARVIS subtitle is drawn here too.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class LegionHud {
   private static final int WIDTH = 96;
   private static final int ROW = 22;
   private static final int ACCENT = 0xFF9FE8FF;
   private static final int DIM = 0xFF6A8A9A;
   private static final int AMBER = 0xFFFFB020;
   private static final int RED = 0xFFFF4030;
   private static final int CYAN = 0xFF60E0FF;

   private LegionHud() {
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (client.options.hideGui || player == null || player.isSpectator() || client.level == null) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      LegionStatus status = ClientLegion.status();
      GuiGraphics graphics = event.getGuiGraphics();
      Font font = client.font;
      if (snapshot != null && (status.active() || status.cooldown() > 0)) {
         draw(graphics, font, status, player.tickCount, columnX(graphics.guiWidth(), widestKeyLabel(font)), ClientLegion.statusAge());
      }

      if (!JarvisVoice.online(snapshot)) {
         subtitle(graphics, font, client, event.getPartialTick());
      }
   }

   /**
    * Left edge of the column: right of it come 3 px of panel padding, a 3 px
    * gap, then the ability bar's key labels (ViltrumiteInGameHudMixin: the
    * bar is 22 wide, 5 from the right edge, slots 3 in, each label
    * {@code width + 6} left of its slot).
    */
   static int columnX(int guiWidth, int keyLabelWidth) {
      int slotX = guiWidth - 5 - 22 + 3;
      int labelLeft = slotX - Math.max(0, keyLabelWidth) - 6;
      return labelLeft - 6 - WIDTH;
   }

   /**
    * Cooldown ticks still to wait: the server sends the status when the
    * cooldown starts and when it ends, so the HUD counts down from the
    * packet itself (the first playtest showed a frozen "10 s").
    */
   static int cooldownLeft(int cooldown, long statusAge) {
      return (int) Math.max(0L, cooldown - Math.max(0L, statusAge));
   }

   private static int widestKeyLabel(Font font) {
      int widest = 0;
      for (KeyMapping key : new KeyMapping[]{AbilityInputManager.abilityKey1, AbilityInputManager.abilityKey2, AbilityInputManager.abilityKey3,
         AbilityInputManager.abilityKey4, AbilityInputManager.abilityKey5, AbilityInputManager.abilityKey6}) {
         if (key != null) {
            widest = Math.max(widest, font.width(KeyLabels.shortName(key)));
         }
      }

      return widest;
   }

   private static void draw(GuiGraphics graphics, Font font, LegionStatus status, int ticks, int x, long statusAge) {
      int y = graphics.guiHeight() / 2 - 44;
      boolean blink = (ticks / 5) % 2 == 0;
      if (!status.active()) {
         int left = cooldownLeft(status.cooldown(), statusAge);
         if (left > 0) {
            small(graphics, font, Component.translatable("hud.viltrumitecore.legion.cooldown", (left + 19) / 20), x, y, WIDTH, DIM);
         }

         return;
      }

      int rows = Math.max(1, status.suits().size());
      graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + 20 + rows * ROW, 0x70081420);
      graphics.fill(x - 3, y - 3, x - 2, y + 20 + rows * ROW, CYAN);
      small(graphics, font, Component.translatable("hud.viltrumitecore.legion.header",
         Component.translatable("gui.viltrumitecore.legion.order." + status.orderValue().key())), x, y, WIDTH, ACCENT);
      small(graphics, font, Component.translatable("gui.viltrumitecore.legion.priority." + status.priorityValue().key()), x, y + 9, WIDTH, DIM);
      int row = y + 20;
      for (LegionStatus.Suit suit : status.suits()) {
         MarkId mark = suit.markId();
         if (mark == null) {
            continue;
         }

         small(graphics, font, Component.translatable("mark.viltrumitecore." + mark.key() + ".name"), x, row, WIDTH - 24, 0xFFDFF6FF);
         String percent = Math.round(suit.fraction() * 100.0F) + "%";
         small(graphics, font, Component.literal(percent), x + WIDTH - font.width(percent) * 3 / 4, row, 24, barColor(suit.fraction(), blink));
         graphics.fill(x, row + 8, x + WIDTH, row + 10, 0xA0081420);
         graphics.fill(x, row + 8, x + Math.round(WIDTH * suit.fraction()), row + 10, barColor(suit.fraction(), blink));
         small(graphics, font, state(suit), x, row + 12, WIDTH, stateColor(suit));
         row += ROW;
      }
   }

   /** The Legion's JARVIS line when the helmet HUD is not up (same look as JarvisHints). */
   private static void subtitle(GuiGraphics graphics, Font font, Minecraft client, float partialTick) {
      Component line = JarvisVoice.subtitle(client.level.getGameTime());
      if (line == null || !legionLine(line)) {
         return;
      }

      float alpha = JarvisVoice.subtitleAlpha(client.level.getGameTime(), partialTick);
      int a = Math.max(8, Math.round(alpha * 255.0F));
      int textWidth = font.width(line);
      int x = (graphics.guiWidth() - textWidth) / 2;
      int y = graphics.guiHeight() - 78;
      graphics.fill(x - 4, y - 3, x + textWidth + 4, y + 11, (Math.round(alpha * 0x90) << 24) | 0x041018);
      graphics.fill(x - 4, y - 3, x - 2, y + 11, (a << 24) | (CYAN & 0xFFFFFF));
      graphics.drawString(font, line, x, y, (a << 24) | 0xBFF4FF, false);
   }

   private static boolean legionLine(Component line) {
      return line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
         && contents.getKey().startsWith("jarvis.viltrumitecore.legion_");
   }

   static int barColor(float fraction, boolean blink) {
      if (fraction < 0.25F) {
         return blink ? RED : AMBER;
      }

      return fraction < 0.5F ? AMBER : 0xFF50D8FF;
   }

   /** What the suit is doing: its flight phase, or in the fight its stance and role. */
   static Component state(LegionStatus.Suit suit) {
      LegionPhase phase = suit.phaseValue();
      if (phase != LegionPhase.ACTIVE) {
         return Component.translatable("gui.viltrumitecore.legion.phase." + phase.name().toLowerCase(Locale.ROOT));
      }

      LegionTactics.Stance stance = suit.stanceValue();
      Component text = Component.translatable("gui.viltrumitecore.legion.stance." + stance.name().toLowerCase(Locale.ROOT));
      if (stance == LegionTactics.Stance.ENGAGE) {
         return Component.translatable("gui.viltrumitecore.legion.state_role", text,
            Component.translatable("gui.viltrumitecore.legion.role." + suit.roleValue().name().toLowerCase(Locale.ROOT)));
      }

      return text;
   }

   static int stateColor(LegionStatus.Suit suit) {
      LegionPhase phase = suit.phaseValue();
      if (phase == LegionPhase.CRASH) {
         return RED;
      }

      if (phase == LegionPhase.LEAVE || suit.stanceValue() == LegionTactics.Stance.RETREAT || suit.stanceValue() == LegionTactics.Stance.RETURN) {
         return AMBER;
      }

      return suit.engaged() ? 0xFFFF8060 : DIM;
   }

   private static void small(GuiGraphics graphics, Font font, Component text, int x, int y, int max, int color) {
      float scale = Math.min(0.75F, max / (float)Math.max(1, font.width(text)));
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(x, y, 0.0F);
      pose.scale(scale, scale, 1.0F);
      graphics.drawString(font, text, 0, 0, color, true);
      pose.popPose();
   }
}
