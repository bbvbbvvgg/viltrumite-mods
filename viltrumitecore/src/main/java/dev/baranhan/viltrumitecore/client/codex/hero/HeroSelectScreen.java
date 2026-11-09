package dev.baranhan.viltrumitecore.client.codex.hero;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.client.codex.CodexClientState;
import dev.baranhan.viltrumitecore.client.codex.CodexEntry;
import dev.baranhan.viltrumitecore.client.codex.CodexPortraits;
import dev.baranhan.viltrumitecore.codex.CodexNetwork;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexUnlockRules;
import dev.baranhan.viltrumitecore.codex.data.HeroCard;
import dev.baranhan.viltrumitecore.codex.hero.HeroSelectService;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Hero select screen (key H, {@code /heroselect}): a role filter, a grid of hero portraits, a live 3D preview
 * of the chosen hero and an info panel (role, play style, difficulty, lore, abilities). The screen only
 * asks; the server answers with a status (cooldown, permission, refused) and does the switch itself.
 * Keyboard: arrows move, Enter confirms, H or Esc closes; double click confirms too.
 */
public class HeroSelectScreen extends Screen {
   static final int TILE = 36;
   static final int GAP = 6;
   static final int PAD = 14;
   static final int TOP = 42;
   static final long CLOSE_DELAY_MS = 900L;
   static final long DOUBLE_CLICK_MS = 350L;
   static final String P = "heroselect.viltrumitecore.";
   static final String ALL = "all";

   private final List<HeroId> heroes = HeroSelectService.selectable();
   private final List<String> roles = new ArrayList<>();
   private final List<int[]> chipRects = new ArrayList<>();
   private final EnumMap<HeroId, List<Component>> abilityCache = new EnumMap<>(HeroId.class);
   private List<HeroId> shown = this.heroes;
   private String roleFilter = ALL;
   private HeroId selected;
   @Nullable
   private HeroId current;
   @Nullable
   private HeroSelectService.Status status;
   private int cooldownTicks;
   private long cooldownAt;
   private boolean queried;
   private boolean pending;
   private boolean serverReady;
   private long closeAt = -1L;
   private long lastClickMs;
   @Nullable
   private HeroId lastClickHero;
   @Nullable
   private Component message;
   private int messageColor = 0xFFB8BCC4;
   private Button confirm;
   @Nullable
   private HeroPreviewPlayer dummy;
   @Nullable
   private ClientLevel dummyLevel;
   private boolean previewFailed;

   private int leftW;
   private int cols;
   private int tile;
   private int gridY;
   private int rightX;
   private int previewW;
   private int previewH;
   private int infoX;
   private int infoW;
   private int buttonY;

   public HeroSelectScreen() {
      super(Component.translatable(P + "title"));
   }

   @Override
   protected void init() {
      this.current = currentHero();
      if (this.selected == null) {
         this.selected = this.current != null && this.heroes.contains(this.current) ? this.current : this.heroes.get(0);
      }
      this.roles.clear();
      this.roles.add(ALL);
      for (HeroId hero : this.heroes) {
         String role = card(hero).role();
         if (!this.roles.contains(role)) {
            this.roles.add(role);
         }
      }
      this.layout();
      this.confirm = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.confirmSelection())
         .bounds(this.infoX, this.buttonY, Math.max(80, Math.min(190, this.infoW)), 20).build());
      if (!this.queried) {
         this.queried = true;
         this.query();
      }
      this.refreshButton();
   }

   private void layout() {
      this.leftW = Mth.clamp((int)(this.width * 0.40F), 3 * (TILE + GAP), 7 * (TILE + GAP));
      this.chipRects.clear();
      int x = PAD;
      int y = TOP;
      for (String role : this.roles) {
         int w = this.font.width(roleLabel(role)) + 12;
         if (x + w > PAD + this.leftW && x > PAD) {
            x = PAD;
            y += 16;
         }
         this.chipRects.add(new int[]{x, y, w, 13});
         x += w + 4;
      }
      this.gridY = y + 13 + 10;
      this.cols = Math.max(1, (this.leftW + GAP) / (TILE + GAP));
      int rows = Math.max(1, (this.heroes.size() + this.cols - 1) / this.cols);
      this.tile = TILE;
      if (this.gridY + rows * (TILE + GAP) > this.height - 16) {
         this.tile = Math.max(18, (this.height - 16 - this.gridY) / rows - GAP);
      }
      this.rightX = PAD + this.leftW + 18;
      int rightW = Math.max(60, this.width - this.rightX - PAD);
      this.previewW = rightW >= 300 ? Math.min(140, rightW * 2 / 5) : 0;
      this.previewH = this.height - TOP - 18;
      this.infoX = this.rightX + (this.previewW > 0 ? this.previewW + 14 : 0);
      this.infoW = Math.max(60, this.width - this.infoX - PAD);
      this.buttonY = this.height - 38;
   }

   // ---- state ----

   @Nullable
   static HeroId currentHero() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && (Object)mc.player instanceof HeroPlayer hero && hero.getHeroId() != null) {
         return HeroId.fromKey(hero.getHeroId().key());
      }
      return null;
   }

   static HeroCard card(HeroId hero) {
      return CodexData.get().card(hero.key());
   }

   @Nullable
   private static Connection connection() {
      Minecraft mc = Minecraft.getInstance();
      return mc.getConnection() == null ? null : mc.getConnection().getConnection();
   }

   private void query() {
      this.pending = true;
      this.serverReady = CodexNetwork.sendToServer(connection(), new CodexNetwork.HeroSelectRequest(""));
      if (!this.serverReady) {
         this.pending = false;
         this.setMessage(Component.translatable(P + "status.no_server"), 0xFFFF6B6B);
      }
   }

   private int cooldownLeft() {
      if (this.cooldownTicks <= 0) {
         return 0;
      }
      long elapsed = (Util.getMillis() - this.cooldownAt) / 50L;
      return (int)Math.max(0L, this.cooldownTicks - elapsed);
   }

   private boolean blocked() {
      return this.status == HeroSelectService.Status.DISABLED || this.status == HeroSelectService.Status.NO_PERMISSION;
   }

   private void refreshButton() {
      if (this.confirm == null) {
         return;
      }
      int left = this.cooldownLeft();
      boolean same = this.selected == this.current;
      this.confirm.active = this.serverReady && !this.pending && !this.blocked() && !same && left == 0 && this.closeAt < 0L;
      if (same) {
         this.confirm.setMessage(Component.translatable(P + "button.current"));
      } else if (left > 0) {
         this.confirm.setMessage(Component.translatable(P + "button.wait", (left + 19) / 20));
      } else {
         this.confirm.setMessage(Component.translatable(P + "button.become", heroName(this.selected)));
      }
   }

   private void setMessage(@Nullable Component text, int color) {
      this.message = text;
      this.messageColor = color;
   }

   /** Server answer (CodexClientHooks). */
   public void onResult(HeroSelectService.Status result, String hero, int ticks) {
      this.pending = false;
      this.serverReady = true;
      this.status = result;
      Minecraft mc = Minecraft.getInstance();
      switch (result) {
         case READY -> {
            this.cooldownTicks = 0;
            this.setMessage(null, 0);
         }
         case COOLDOWN -> {
            this.cooldownTicks = ticks;
            this.cooldownAt = Util.getMillis();
            this.setMessage(null, 0);
         }
         case OK -> {
            this.current = HeroId.fromKey(hero);
            this.cooldownTicks = ticks;
            this.cooldownAt = Util.getMillis();
            this.closeAt = Util.getMillis() + CLOSE_DELAY_MS;
            this.setMessage(statusText(result, hero, ticks), 0xFF7CFF8A);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F));
         }
         default -> {
            if (result == HeroSelectService.Status.COOLDOWN || result == HeroSelectService.Status.SAME_HERO) {
               this.cooldownTicks = ticks;
               this.cooldownAt = Util.getMillis();
            }
            this.setMessage(statusText(result, hero, ticks), 0xFFFF6B6B);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.8F));
         }
      }
      this.refreshButton();
   }

   /** Status line text: %1$s = seconds left, %2$s = hero name. */
   public static Component statusText(HeroSelectService.Status status, String heroKey, int ticks) {
      HeroId hero = HeroId.fromKey(heroKey);
      Component name = hero == null ? Component.literal(heroKey) : heroName(hero);
      return Component.translatable(status.langKey(), (ticks + 19) / 20, name);
   }

   private void confirmSelection() {
      this.refreshButton();
      Minecraft mc = Minecraft.getInstance();
      if (this.confirm == null || !this.confirm.active) {
         if (this.selected == this.current) {
            this.setMessage(Component.translatable(P + "status.pick_other"), 0xFFFFC857);
         }
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F));
         return;
      }
      this.pending = true;
      this.setMessage(Component.translatable(P + "status.pending"), 0xFFB8BCC4);
      if (!CodexNetwork.sendToServer(connection(), new CodexNetwork.HeroSelectRequest(this.selected.key()))) {
         this.pending = false;
         this.serverReady = false;
         this.setMessage(Component.translatable(P + "status.no_server"), 0xFFFF6B6B);
      }
      this.refreshButton();
   }

   private void select(HeroId hero) {
      if (hero != this.selected) {
         this.selected = hero;
         this.setMessage(null, 0);
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
      }
      this.refreshButton();
   }

   private void move(int delta) {
      int index = Math.max(0, this.shown.indexOf(this.selected));
      this.select(this.shown.get(Mth.clamp(index + delta, 0, this.shown.size() - 1)));
   }

   private void applyFilter(String role) {
      this.roleFilter = role;
      List<HeroId> list = new ArrayList<>();
      for (HeroId hero : this.heroes) {
         if (ALL.equals(role) || card(hero).role().equals(role)) {
            list.add(hero);
         }
      }
      this.shown = list.isEmpty() ? this.heroes : list;
      if (!this.shown.contains(this.selected)) {
         this.select(this.shown.get(0));
      }
   }

   @Override
   public void tick() {
      if (this.closeAt > 0L && Util.getMillis() >= this.closeAt) {
         this.onClose();
         return;
      }
      this.refreshButton();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      this.dummy = null;
      this.dummyLevel = null;
      super.removed();
   }

   // ---- input ----

   @Override
   public boolean keyPressed(int key, int scan, int modifiers) {
      if (HeroSelectKeys.OPEN.matches(key, scan)) {
         this.onClose();
         return true;
      }
      switch (key) {
         case GLFW.GLFW_KEY_LEFT -> {
            this.move(-1);
            return true;
         }
         case GLFW.GLFW_KEY_RIGHT -> {
            this.move(1);
            return true;
         }
         case GLFW.GLFW_KEY_UP -> {
            this.move(-this.cols);
            return true;
         }
         case GLFW.GLFW_KEY_DOWN -> {
            this.move(this.cols);
            return true;
         }
         case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            this.confirmSelection();
            return true;
         }
         default -> {
            return super.keyPressed(key, scan, modifiers);
         }
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         for (int i = 0; i < this.chipRects.size(); i++) {
            int[] r = this.chipRects.get(i);
            if (inside(mx, my, r[0], r[1], r[2], r[3])) {
               this.applyFilter(this.roles.get(i));
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.3F));
               return true;
            }
         }
         HeroId hit = this.tileAt(mx, my);
         if (hit != null) {
            long now = Util.getMillis();
            if (hit == this.lastClickHero && now - this.lastClickMs <= DOUBLE_CLICK_MS) {
               this.select(hit);
               this.confirmSelection();
               this.lastClickHero = null;
            } else {
               this.select(hit);
               this.lastClickHero = hit;
               this.lastClickMs = now;
            }
            return true;
         }
      }
      return super.mouseClicked(mx, my, button);
   }

   @Nullable
   private HeroId tileAt(double mx, double my) {
      for (int i = 0; i < this.shown.size(); i++) {
         int x = this.tileX(i);
         int y = this.tileY(i);
         if (inside(mx, my, x, y, this.tile, this.tile)) {
            return this.shown.get(i);
         }
      }
      return null;
   }

   private int tileX(int i) {
      return PAD + i % this.cols * (this.tile + GAP);
   }

   private int tileY(int i) {
      return this.gridY + i / this.cols * (this.tile + GAP);
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   // ---- render ----

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      HeroCard card = card(this.selected);
      int accent = card.accent() & 0xFFFFFF;
      this.renderBackground(g);
      g.fillGradient(0, 0, this.width, this.height, 0xC0080A0E, 0xD0000000 | scale(accent, 0.22F));
      g.drawString(this.font, this.title, PAD, 10, 0xFFFFFFFF, true);
      g.drawString(this.font, Component.translatable(P + "subtitle"), PAD, 22, 0xFFA0A4AC, false);
      int[] progress = CodexUnlockRules.progress(CodexData.get().entries().values(), CodexClientState.snapshot());
      if (progress[1] > 0) {
         Component codex = Component.translatable("codex2.viltrumitecore.progress", progress[0], progress[1]);
         g.drawString(this.font, codex, this.width - PAD - this.font.width(codex), 10, 0xFFFFD34D, false);
      }
      this.renderChips(g, mouseX, mouseY);
      HeroId hovered = this.renderGrid(g, mouseX, mouseY);
      if (this.previewW > 0) {
         this.renderPreview(g, mouseX, mouseY, accent);
      }
      this.renderInfo(g, card, accent);
      if (this.message != null) {
         g.drawString(this.font, this.message, this.infoX, this.buttonY - 12, this.messageColor, false);
      } else if (this.blocked() && this.status != null) {
         g.drawString(this.font, statusText(this.status, this.selected.key(), 0), this.infoX, this.buttonY - 12, 0xFFFF6B6B, false);
      }
      super.render(g, mouseX, mouseY, partial);
      if (hovered != null) {
         g.renderTooltip(this.font, heroName(hovered), mouseX, mouseY);
      }
   }

   private void renderChips(GuiGraphics g, int mx, int my) {
      for (int i = 0; i < this.chipRects.size(); i++) {
         int[] r = this.chipRects.get(i);
         String role = this.roles.get(i);
         boolean on = role.equals(this.roleFilter);
         boolean hover = inside(mx, my, r[0], r[1], r[2], r[3]);
         g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? 0xC0FFD34D : hover ? 0x60FFFFFF : 0x40000000);
         outline(g, r[0], r[1], r[2], r[3], on ? 0xFFFFD34D : 0x50FFFFFF);
         g.drawString(this.font, roleLabel(role), r[0] + 6, r[1] + 3, on ? 0xFF15120A : 0xFFE0E0E0, false);
      }
   }

   @Nullable
   private HeroId renderGrid(GuiGraphics g, int mx, int my) {
      HeroId hovered = null;
      double t = Util.getMillis() / 250.0;
      for (int i = 0; i < this.shown.size(); i++) {
         HeroId hero = this.shown.get(i);
         int x = this.tileX(i);
         int y = this.tileY(i);
         int accent = card(hero).accent() & 0xFFFFFF;
         boolean hover = inside(mx, my, x, y, this.tile, this.tile);
         if (hover) {
            hovered = hero;
         }
         g.fill(x, y, x + this.tile, y + this.tile, 0xA0101218);
         CodexPortraits.heroBadge(g, hero.key(), x + 1, y + 1, this.tile - 2, 1.0F);
         if (hero == this.selected) {
            int alpha = 0xA0 + (int)(0x5F * (0.5 + 0.5 * Math.sin(t)));
            int color = alpha << 24 | accent;
            outline(g, x - 2, y - 2, this.tile + 4, this.tile + 4, color);
            outline(g, x - 1, y - 1, this.tile + 2, this.tile + 2, color);
         } else {
            outline(g, x, y, this.tile, this.tile, hover ? 0xFFFFFFFF : 0x40FFFFFF);
         }
         if (hero == this.current) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            g.drawString(this.font, "\u2605", x + this.tile - 8, y + 1, 0xFFFFD34D, true);
            g.pose().popPose();
         }
      }
      return hovered;
   }

   private void renderPreview(GuiGraphics g, int mx, int my, int accent) {
      int x = this.rightX;
      int y = TOP;
      int w = this.previewW;
      int h = this.previewH;
      g.fill(x, y, x + w, y + h, 0x70000000);
      g.fillGradient(x + 1, y + h - 44, x + w - 1, y + h - 1, 0x00000000, 0x60000000 | accent);
      outline(g, x, y, w, h, 0x60000000 | accent);
      LivingEntity entity = this.previewFailed ? null : this.previewEntity();
      if (entity != null) {
         int scale = Mth.clamp((int)((h - 34) / 2.1F), 18, 110);
         int cx = x + w / 2;
         int foot = y + h - 14;
         float sway = (float)Math.sin(Util.getMillis() / 900.0) * 24.0F;
         boolean mouseIn = inside(mx, my, x, y, w, h);
         float lookX = mouseIn ? cx - mx : sway;
         float lookY = mouseIn ? foot - scale * 1.6F - my : -6.0F;
         g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
         try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, foot, scale, Mth.clamp(lookX, -80.0F, 80.0F), Mth.clamp(lookY, -60.0F, 60.0F), entity);
         } catch (RuntimeException failure) {
            this.previewFailed = true;
         }
         g.disableScissor();
      } else {
         CodexPortraits.draw(g, CodexEntry.Visual.hero(this.selected.key()), accent, x + 4, y + 4, w - 8, h - 8, 1.0F);
      }
   }

   @Nullable
   private LivingEntity previewEntity() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      }
      if (this.selected == this.current && mc.player != null) {
         return mc.player;
      }
      if (this.dummy == null || this.dummyLevel != mc.level) {
         this.dummy = new HeroPreviewPlayer(mc.level);
         this.dummyLevel = mc.level;
      }
      this.dummy.wear(this.selected);
      this.dummy.tickCount = (int)(Util.getMillis() / 50L);
      return this.dummy;
   }

   private void renderInfo(GuiGraphics g, HeroCard card, int accent) {
      int x = this.infoX;
      int y = TOP;
      int maxY = this.buttonY - 16;
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.pose().scale(2.0F, 2.0F, 1.0F);
      g.drawString(this.font, heroName(this.selected), 0, 0, 0xFF000000 | accent, true);
      g.pose().popPose();
      y += 20;
      if (this.selected == this.current) {
         g.drawString(this.font, Component.translatable(P + "current"), x, y, 0xFFFFD34D, false);
         y += 11;
      }
      MutableComponent kind = Component.translatable(HeroCard.roleKey(card.role()));
      for (String style : card.styles()) {
         kind.append(Component.literal(" \u00b7 ")).append(Component.translatable(HeroCard.styleKey(style)));
      }
      for (FormattedCharSequence line : this.font.split(kind, this.infoW)) {
         g.drawString(this.font, line, x, y, 0xFFD8DCE4, false);
         y += 10;
      }
      y += 2;
      Component difficulty = Component.translatable(P + "difficulty");
      g.drawString(this.font, difficulty, x, y, 0xFFA0A4AC, false);
      int px = x + this.font.width(difficulty) + 6;
      for (int i = 1; i <= HeroCard.MAX_DIFFICULTY; i++) {
         int color = i <= card.difficulty() ? 0xFF000000 | accent : 0x40FFFFFF;
         g.fill(px, y + 1, px + 7, y + 8, color);
         px += 10;
      }
      y += 16;
      Component lore = lore(this.selected, card);
      if (lore != null) {
         int lines = 0;
         for (FormattedCharSequence line : this.font.split(lore, this.infoW)) {
            if (y + 10 > maxY - 40 || lines >= 6) {
               break;
            }
            g.drawString(this.font, line, x, y, 0xFFC8CCD4, false);
            y += 10;
            lines++;
         }
         y += 6;
      }
      List<Component> abilities = this.abilities(this.selected);
      if (!abilities.isEmpty() && y + 22 < maxY) {
         g.drawString(this.font, Component.translatable(P + "abilities"), x, y, 0xFFFFD34D, false);
         y += 12;
         int colW = Math.max(40, (this.infoW - 8) / 2);
         for (int i = 0; i < abilities.size() && i < 6; i++) {
            int ax = x + i % 2 * (colW + 8);
            int ay = y + i / 2 * 11;
            if (ay + 10 > maxY) {
               break;
            }
            g.fill(ax, ay + 3, ax + 3, ay + 6, 0xFF000000 | accent);
            String name = this.font.plainSubstrByWidth(abilities.get(i).getString(), colW - 8);
            g.drawString(this.font, name, ax + 6, ay, 0xFFE8E8E8, false);
         }
      }
   }

   @Nullable
   private static Component lore(HeroId hero, HeroCard card) {
      if (I18n.exists(card.loreKey())) {
         return Component.translatable(card.loreKey());
      }
      String codex = CodexEntry.key(hero.key(), "lore");
      return I18n.exists(codex) ? Component.translatable(codex) : null;
   }

   private List<Component> abilities(HeroId hero) {
      return this.abilityCache.computeIfAbsent(hero, h -> {
         List<Component> out = new ArrayList<>();
         try {
            for (ViltrumiteAbility ability : HeroRegistry.get(h).panelAbilities()) {
               List<Component> tooltip = ability.getTooltip();
               String key = "ability.viltrumitecore." + ability.getId() + ".name";
               out.add(I18n.exists(key) ? Component.translatable(key) : tooltip.isEmpty() ? Component.literal(ability.getId()) : tooltip.get(0));
            }
         } catch (RuntimeException ignored) {
            // a hero without a panel simply shows no ability list
         }
         return out;
      });
   }

   // ---- helpers ----

   static Component roleLabel(String role) {
      return ALL.equals(role) ? Component.translatable(P + "filter.all") : Component.translatable(HeroCard.roleKey(role));
   }

   /** Hero display name: codex name, then hero select name, then the key itself. */
   public static Component heroName(HeroId hero) {
      String codex = CodexEntry.key(hero.key(), "name");
      if (I18n.exists(codex)) {
         return Component.translatable(codex);
      }
      String own = P + "hero." + hero.key() + ".name";
      if (I18n.exists(own)) {
         return Component.translatable(own);
      }
      String key = hero.key().replace('_', ' ');
      return Component.literal(key.isEmpty() ? key : key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1));
   }

   static int scale(int rgb, float f) {
      int r = (int)((rgb >> 16 & 0xFF) * f);
      int gr = (int)((rgb >> 8 & 0xFF) * f);
      int b = (int)((rgb & 0xFF) * f);
      return r << 16 | gr << 8 | b;
   }

   static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x, y, x + w, y + 1, color);
      g.fill(x, y + h - 1, x + w, y + h, color);
      g.fill(x, y + 1, x + 1, y + h - 1, color);
      g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
   }
}
