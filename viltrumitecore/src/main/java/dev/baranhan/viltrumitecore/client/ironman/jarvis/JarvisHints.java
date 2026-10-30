package dev.baranhan.viltrumitecore.client.ironman.jarvis;

import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.entity.FlareEntity;
import dev.baranhan.viltrumitecore.entity.MicroMissileEntity;
import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import dev.baranhan.viltrumitecore.hero.ironman.jarvis.ThreatScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * JARVIS hints (spec §11.1): small marks only, never a big UI. Threat
 * brackets on screen (amber mobs, red players and bosses), edge arrows for
 * threats out of view, red arrows for projectiles whose path passes close,
 * the subtitle of the current JARVIS line, and short warnings at the left edge.
 * Only with the suit ready and the helmet closed.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class JarvisHints {
   private static final int AMBER = 0xFFFFB020;
   private static final int RED = 0xFFFF4030;
   private static final int CYAN = 0xFF60E0FF;

   private JarvisHints() {
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (client.options.hideGui || player == null || player.isSpectator() || client.level == null) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (!JarvisVoice.online(snapshot)) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      int width = graphics.guiWidth();
      int height = graphics.guiHeight();
      float partialTick = event.getPartialTick();
      boolean firstPerson = client.options.getCameraType().isFirstPerson();
      for (int id : ClientHeroData.section(OwnerSection.THREATS)) {
         Entity entity = client.level.getEntity(id);
         if (entity == null || entity.isRemoved() || !entity.isAlive()) {
            continue;
         }

         int color = entity instanceof Player || JarvisVoice.critical(entity) ? RED : AMBER;
         threat(graphics, entity, partialTick, width, height, color, firstPerson);
      }

      Vec3 centre = player.getPosition(partialTick).add(0.0, player.getBbHeight() * 0.5, 0.0);
      double range = IronManRules.THREAT_RANGE;
      for (Entity entity : client.level.getEntities(player, new AABB(centre, centre).inflate(range), e -> e instanceof Projectile)) {
         Projectile projectile = (Projectile)entity;
         if (projectile.getOwner() == player || entity instanceof FlareEntity || entity instanceof RepulsorBlastEntity && projectile.getOwner() == player
            || entity instanceof MicroMissileEntity && projectile.getOwner() == player || projectile.onGround()) {
            continue;
         }

         if (ThreatScan.projectilePassesNear(entity.position(), entity.getDeltaMovement(), centre, range, IronManRules.THREAT_PROJECTILE_MISS)) {
            ScreenProjector.Point point = ScreenProjector.project(entity.getPosition(partialTick), width, height);
            if (point != null) {
               edgeOrMark(graphics, point, width, height, RED, true);
            }
         }
      }

      warnings(graphics, client.font, snapshot, player, height);
      Component line = JarvisVoice.subtitle(client.level.getGameTime());
      if (line != null) {
         float alpha = JarvisVoice.subtitleAlpha(client.level.getGameTime(), partialTick);
         int a = Math.max(8, Math.round(alpha * 255.0F));
         Font font = client.font;
         int textWidth = font.width(line);
         int x = (width - textWidth) / 2;
         int y = height - 78;
         graphics.fill(x - 4, y - 3, x + textWidth + 4, y + 11, (Math.round(alpha * 0x90) << 24) | 0x041018);
         graphics.fill(x - 4, y - 3, x - 2, y + 11, (a << 24) | (CYAN & 0xFFFFFF));
         graphics.drawString(font, line, x, y, (a << 24) | 0xBFF4FF, false);
      }
   }

   private static void threat(GuiGraphics graphics, Entity entity, float partialTick, int width, int height, int color, boolean firstPerson) {
      // The interpolated render box: the same frame position the entity is drawn at.
      Vec3 base = entity.getPosition(partialTick);
      AABB box = entity.getBoundingBox().move(base.subtract(entity.position()));
      ScreenProjector.Point[] corners = ScreenProjector.corners(box, width, height);
      if (corners == null) {
         return;
      }

      // All eight corners, not only feet/head: looking down from flight, bottom and top project onto
      // nearly the same point and the old feet-anchored bracket shrank away from the body.
      float[] rect = ScreenProjector.bounds(corners);
      if (rect == null || rect[2] < 0 || rect[3] < 0 || rect[0] > width || rect[1] > height) {
         ScreenProjector.Point centre = ScreenProjector.project(box.getCenter(), width, height);
         if (centre != null) {
            edgeOrMark(graphics, centre, width, height, color, false);
         }

         return;
      }

      int x0 = Math.round(Math.min(rect[0], (rect[0] + rect[2]) * 0.5F - 2.5F) - 2.0F);
      int x1 = Math.round(Math.max(rect[2], (rect[0] + rect[2]) * 0.5F + 2.5F) + 2.0F);
      int y0 = Math.round(Math.min(rect[1], (rect[1] + rect[3]) * 0.5F - 3.0F) - 2.0F);
      int y1 = Math.round(Math.max(rect[3], (rect[1] + rect[3]) * 0.5F + 3.0F) + 2.0F);
      int arm = Math.max(2, Math.min(6, (x1 - x0) / 4));
      bracket(graphics, x0, y0, arm, 1, 1, color);
      bracket(graphics, x1, y0, arm, -1, 1, color);
      bracket(graphics, x0, y1, arm, 1, -1, color);
      bracket(graphics, x1, y1, arm, -1, -1, color);
   }

   private static void bracket(GuiGraphics graphics, int x, int y, int arm, int dx, int dy, int color) {
      graphics.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm) + 1, y + 1, color);
      graphics.fill(x, Math.min(y, y + dy * arm), x + 1, Math.max(y, y + dy * arm) + 1, color);
   }

   /** On screen: a small diamond (projectiles) ; off screen: a triangle at the edge pointing to it. */
   private static void edgeOrMark(GuiGraphics graphics, ScreenProjector.Point point, int width, int height, int color, boolean projectile) {
      if (point.onScreen(width, height)) {
         if (projectile) {
            int x = Math.round(point.x());
            int y = Math.round(point.y());
            graphics.fill(x - 1, y - 3, x + 2, y + 4, color);
            graphics.fill(x - 3, y - 1, x + 4, y + 2, color);
         }

         return;
      }

      float cx = width * 0.5F;
      float cy = height * 0.5F;
      float dx = point.x() - cx;
      float dy = point.y() - cy;
      if (Math.abs(dx) < 1.0E-3F && Math.abs(dy) < 1.0E-3F) {
         dy = 1.0F;
      }

      float margin = 14.0F;
      float scale = Math.min((cx - margin) / Math.max(1.0E-3F, Math.abs(dx)), (cy - margin) / Math.max(1.0E-3F, Math.abs(dy)));
      float ex = cx + dx * scale;
      float ey = cy + dy * scale;
      float angle = (float)Math.atan2(dy, dx);
      // Triangle as stacked 1 px spans along the direction.
      int size = projectile ? 6 : 5;
      for (int i = 0; i < size; i++) {
         float half = (size - i) * 0.6F;
         float px = ex + Mth.cos(angle) * i;
         float py = ey + Mth.sin(angle) * i;
         float nx = -Mth.sin(angle) * half;
         float ny = Mth.cos(angle) * half;
         int ax = Math.round(px - nx);
         int ay = Math.round(py - ny);
         int bx = Math.round(px + nx);
         int by = Math.round(py + ny);
         graphics.fill(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx) + 1, Math.max(ay, by) + 1, color);
      }
   }

   private static void warnings(GuiGraphics graphics, Font font, HeroPublicSnapshot snapshot, LocalPlayer player, int height) {
      boolean blink = (player.tickCount / 6) % 2 == 0;
      int y = height / 2 + 52;
      if (IronManView.energy(snapshot) < IronManRules.JARVIS_LOW_ENERGY) {
         graphics.drawString(font, Component.translatable("hud.viltrumitecore.ironman.low_energy"), 8, y, blink ? 0xFFB020 : 0x906010, true);
      }
   }
}
