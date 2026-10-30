package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Caster-side Road Roller cinema: RollerCamera shots applied in Camera.setup
 * (DioSceneCameraMixin), letterbox, hidden first-person hands, a locked mouse
 * and DIO facing his landing point. Everything is derived every frame from the
 * live ult (snapshot + RollerLeapClient clock): a cancelled ult, death, level
 * change or logout simply stop producing a view, and the camera is the vanilla
 * one again on that very frame (the exit blend already converged at the end).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class RollerCinema {
   @Nullable
   private static Vec3 facing;
   private static float barsO, bars;

   private RollerCinema() {
   }

   @Nullable
   private static Vec3 land(LocalPlayer player) {
      HeroPublicSnapshot snapshot = DioView.of(player);
      return snapshot != null && DioView.is(snapshot, dev.baranhan.viltrumitecore.hero.HeroAction.ROAD_ROLLER) ? snapshot.actionTarget() : null;
   }

   /** The local DIO's ult owns the view right now. */
   public static boolean active() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && player.isAlive() && RollerLeapClient.start() != null && RollerLeapClient.clock() >= 0.0 && land(player) != null
         && RollerCamera.weight(RollerLeapClient.clock()) > 0.0;
   }

   /** Camera.setup TAIL: replace the view, or null to keep the vanilla one. */
   @Nullable
   public static RollerCamera.View apply(Entity entity, float partialTick, Vec3 ownPos, float ownYaw, float ownPitch) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (entity != player || player == null || !player.isAlive() || client.level == null) {
         return null;
      }

      Vec3 start = RollerLeapClient.start();
      Vec3 land = land(player);
      double clock = RollerLeapClient.clock();
      if (start == null || land == null || clock < 0.0) {
         return null;
      }

      double t = Math.min(DioRules.ROLLER_LENGTH, clock + (client.isPaused() ? 0.0 : partialTick));
      if (RollerCamera.weight(t) <= 0.0) {
         return null;
      }

      Vec3 f = facing != null ? facing : RollerCamera.facing(start, land, player.getYRot());
      RollerCamera.View view = RollerCamera.view(t, player.getPosition(partialTick), start, land, f, ownPos, ownYaw, ownPitch);
      // Keep the lens out of terrain: clip from what it looks at back to where it wants to be.
      Vec3 look = view.pos().add(RollerCamera.direction(view.yaw(), view.pitch()).scale(4.0));
      BlockHitResult hit = client.level.clip(new ClipContext(look, view.pos(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
      if (hit.getType() != HitResult.Type.MISS) {
         Vec3 safe = hit.getLocation().add(look.subtract(view.pos()).normalize().scale(0.25));
         view = new RollerCamera.View(safe, view.yaw(), view.pitch(), view.bars());
      }

      return view;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      barsO = bars;
      Vec3 start = RollerLeapClient.start();
      Vec3 land = player == null ? null : land(player);
      if (player == null || start == null || land == null || !player.isAlive()) {
         facing = null;
         bars = 0.0F;
         return;
      }

      if (facing == null) {
         facing = RollerCamera.facing(start, land, player.getYRot());
      }

      double t = RollerLeapClient.clock();
      bars = t < 0.0 ? 0.0F : RollerCamera.bars(t);
      if (t >= 0.0 && t < RollerCamera.EXIT_START) {
         // DIO faces his landing point through the ult (the exit blend ends on this view).
         float yaw = (float)Math.toDegrees(Math.atan2(-facing.x, facing.z));
         player.setYRot(yaw);
         player.yRotO = yaw;
         player.setYHeadRot(yaw);
         player.setXRot(Math.max(-10.0F, Math.min(30.0F, player.getXRot())));
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRenderHand(RenderHandEvent event) {
      if (active()) {
         event.setCanceled(true);
      }
   }

   /** Vanilla HUD pieces hidden while the cinematic runs (hero subtitles and chat stay). */
   private static final java.util.Set<String> HIDDEN = java.util.Set.of("hotbar", "player_health", "armor_level", "food_level",
      "air_level", "mount_health", "jump_bar", "experience_bar", "item_name", "crosshair", "potion_icons");

   @SubscribeEvent
   public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
      if (event.getOverlay().id().getNamespace().equals("minecraft") && HIDDEN.contains(event.getOverlay().id().getPath()) && active()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      float b = barsO + (bars - barsO) * event.getPartialTick();
      if (b <= 0.001F) {
         return;
      }

      GuiGraphics g = event.getGuiGraphics();
      int h = Math.round(g.guiHeight() * 0.12F * b);
      g.fill(0, 0, g.guiWidth(), h, 0xFF000000);
      g.fill(0, g.guiHeight() - h, g.guiWidth(), g.guiHeight(), 0xFF000000);
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      facing = null;
      bars = barsO = 0.0F;
   }
}
