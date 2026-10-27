package dev.baranhan.viltrumitecore.client.thor.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.scene.DoubleTap;
import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityPlan;
import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityScene;
import dev.baranhan.viltrumitecore.hero.thor.scene.GrabAction;
import dev.baranhan.viltrumitecore.hero.thor.scene.GrabFlow;
import dev.baranhan.viltrumitecore.hero.thor.scene.ResistKey;
import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame;
import dev.baranhan.viltrumitecore.hero.thor.scene.SceneSync;
import dev.baranhan.viltrumitecore.hero.thor.scene.TeleportRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorMovementRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneConfig;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneView;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Client side of the Thor scenes: sound duck, the local Thor puppet in a
 * fatality, the victim's resist key, the V teleport aim / confirm / cancel,
 * double-tap dashes, scene body rotations and the HUDs (grab choice strip,
 * resist key + meter, teleport timer / hint, grab-window cue). The server
 * judges every input again. Client thread only.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThorSceneClient {
   private static final ResourceLocation[] ICONS = {
      new ResourceLocation("viltrumitecore", "textures/gui/thor/scene/slam.png"),
      new ResourceLocation("viltrumitecore", "textures/gui/thor/scene/throw.png"),
      new ResourceLocation("viltrumitecore", "textures/gui/thor/scene/barrage.png")
   };
   private static final Object DUCK = new Object();
   private static final Set<Integer> ROTATED = new HashSet<>();
   private static final DoubleTap TAPS = new DoubleTap();
   private static final boolean[] RESIST_DOWN = new boolean[ResistKey.values().length];
   private static boolean ducked;
   private static int dashLeft;
   private static Vec3 dashVelocity = Vec3.ZERO;
   private static long dashReady;
   @Nullable
   private static BlockPos aim;
   private static boolean aimValid;

   private ThorSceneClient() {
   }

   /** S2C hook (after ThorSceneView took the sync). */
   public static void onSync(SceneSync sync) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null) {
         return;
      }

      boolean own = sync.thorId() == player.getId();
      if (sync.kind() == SceneSync.Kind.TELEPORT && own && !sync.end() && sync.tick() == 0) {
         player.setXRot(70.0F);
      }

      refreshDuck(player);
   }

   /** Fatality: everything but the scene goes quiet for the participants and anyone close. */
   private static void refreshDuck(LocalPlayer player) {
      boolean want = false;
      for (ThorSceneView.Live live : ThorSceneView.all().values()) {
         FatalityPlan plan = live.sync().kind() == SceneSync.Kind.FATALITY ? live.sync().plan() : null;
         if (plan != null && (live.sync().thorId() == player.getId() || live.sync().victimId() == player.getId()
            || plan.anchor().distanceToSqr(player.position()) < 32 * 32)) {
            want = true;
         }
      }

      if (want && !ducked) {
         SoundDuck.INSTANCE.duck(DUCK, SoundSource.MASTER, 0.25F, 10, Set.of());
      } else if (!want && ducked) {
         SoundDuck.INSTANCE.release(DUCK);
      }
      ducked = want;
   }

   // --- ticks ---

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null) {
         return;
      }

      if (minecraft.isPaused()) {
         return;
      }

      ThorSceneView.clientTick();
      refreshDuck(player);
      ThorSceneView.Live own = ThorSceneView.of(player.getId());
      if (own != null && own.sync().kind() == SceneSync.Kind.FATALITY) {
         puppet(player, own);
      }

      if (own != null && own.sync().kind() == SceneSync.Kind.TELEPORT) {
         player.setDeltaMovement(Vec3.ZERO);
         aim(player);
      } else {
         aim = null;
      }

      resist(minecraft, player);
      dash(minecraft, player, own);
      drip(player.level());
   }

   /** The local Thor follows the plan exactly (the server reads it from his movement). */
   private static void puppet(LocalPlayer player, ThorSceneView.Live live) {
      FatalityPlan plan = live.sync().plan();
      if (plan == null) {
         return;
      }

      double tick = Math.min(ThorSceneView.tick(live, 0), plan.length());
      Vec3 at = plan.thorAt(tick);
      player.setPos(at.x, at.y, at.z);
      player.setDeltaMovement(Vec3.ZERO);
      float yaw = plan.thorYawAt(tick);
      player.setYRot(yaw);
      player.yBodyRot = yaw;
      player.yHeadRot = yaw;
   }

   private static void resist(Minecraft minecraft, LocalPlayer player) {
      ThorSceneView.Live live = ThorSceneView.victimOf(player.getId());
      SceneSync sync = live == null ? null : live.sync();
      boolean active = sync != null && sync.kind() == SceneSync.Kind.GRAB && sync.grabStage() == GrabFlow.Stage.HOLD && sync.resistKey() >= 0;
      for (ResistKey key : ResistKey.values()) {
         boolean down = switch (key) {
            case W -> minecraft.options.keyUp.isDown();
            case A -> minecraft.options.keyLeft.isDown();
            case S -> minecraft.options.keyDown.isDown();
            case D -> minecraft.options.keyRight.isDown();
            case SPACE -> minecraft.options.keyJump.isDown();
         };
         if (active && down && !RESIST_DOWN[key.ordinal()]) {
            CoreMessages.sendToServer(new ThorSceneC2SPacket(ThorSceneC2SPacket.Op.RESIST, key.ordinal(), BlockPos.ZERO));
         }
         RESIST_DOWN[key.ordinal()] = down;
      }
   }

   private static boolean localThor(LocalPlayer player) {
      HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
      return snapshot != null && snapshot.heroId() == HeroId.THOR;
   }

   /** Double-tap W/A/S/D: a short burst on the ground or in HOVER (predicted here, confirmed by the server). */
   private static void dash(Minecraft minecraft, LocalPlayer player, @Nullable ThorSceneView.Live own) {
      if (dashLeft > 0) {
         dashLeft--;
         player.setDeltaMovement(dashVelocity.x, player.getDeltaMovement().y, dashVelocity.z);
      }

      if (!localThor(player) || minecraft.screen != null) {
         TAPS.reset();
         return;
      }

      boolean[] keys = {minecraft.options.keyUp.isDown(), minecraft.options.keyLeft.isDown(), minecraft.options.keyDown.isDown(),
         minecraft.options.keyRight.isDown()};
      long now = player.level().getGameTime();
      int direction = TAPS.tick(keys, now, ThorSceneConfig.dashTapWindow);
      if (direction < 0 || now < dashReady || own != null) {
         return;
      }

      FlightState state = player instanceof ViltrumiteFlightPlayer flight ? flight.getFlightState() : null;
      if (!ThorMovementRules.dashAllowed(ThorScenes.claimsMouse(player), HeroHeldTargets.holder(player) != null, state, player.onGround())) {
         return;
      }

      float yaw = player.getYRot() + ThorMovementRules.dashYawOffset(direction);
      dashVelocity = SceneFrame.forward(yaw).scale(ThorSceneConfig.dashSpeed);
      dashLeft = ThorSceneConfig.dashTicks;
      dashReady = now + ThorSceneConfig.dashCooldown;
      player.setDeltaMovement(dashVelocity.x, Math.max(player.getDeltaMovement().y, 0.0), dashVelocity.z);
      CoreMessages.sendToServer(new ThorSceneC2SPacket(ThorSceneC2SPacket.Op.DASH, direction, BlockPos.ZERO));
   }

   /** The block under the teleport crosshair (the camera may start inside rock: the ray begins at the first air). */
   private static void aim(LocalPlayer player) {
      Vec3 camera = ThorSceneCamera.teleportCamera();
      if (camera == null) {
         aim = null;
         return;
      }

      Level level = player.level();
      Vec3 end = camera.add(player.getLookAngle().scale(256.0));
      Vec3 start = firstAir(level, camera, end);
      if (start == null) {
         aim = null;
         return;
      }

      BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         aim = null;
         return;
      }

      BlockPos pos = hit.getBlockPos();
      aim = pos;
      Vec3 feet = Vec3.atBottomCenterOf(pos.above());
      boolean solid = level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
      boolean room = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
         && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty();
      aimValid = solid && room && TeleportRules.inView(camera, feet, ThorSceneConfig.teleportMaxRadius);
   }

   @Nullable
   private static Vec3 firstAir(Level level, Vec3 from, Vec3 to) {
      Vec3 dir = to.subtract(from);
      double length = dir.length();
      dir = dir.scale(1.0 / length);
      for (double d = 0; d < length; d += 0.5) {
         Vec3 at = from.add(dir.scale(d));
         BlockPos pos = BlockPos.containing(at);
         if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return at;
         }
      }
      return null;
   }

   /** Blood dripping from Thor's hand after a fatality's gore beat. */
   private static void drip(Level level) {
      for (ThorSceneView.Live live : ThorSceneView.all().values()) {
         FatalityPlan plan = live.sync().kind() == SceneSync.Kind.FATALITY ? live.sync().plan() : null;
         Entity thor = plan == null ? null : level.getEntity(live.sync().thorId());
         if (thor == null || ThorSceneView.clientTicks() % 3 != 0) {
            continue;
         }

         double t = plan.mainTick(ThorSceneView.tick(live, 0));
         int gore = plan.scene().beatTicks()[0];
         if (t > gore) {
            Vec3 hand = thor.position().add(SceneFrame.right(thor.getYRot()).scale(0.4)).add(SceneFrame.forward(thor.getYRot()).scale(0.3))
               .add(0, 0.75, 0);
            level.addParticle(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.0F), hand.x, hand.y, hand.z, 0, -0.05, 0);
         }
      }
   }

   // --- input ---

   @SubscribeEvent
   public static void onMovementInput(MovementInputUpdateEvent event) {
      LocalPlayer player = event.getEntity() instanceof LocalPlayer local ? local : null;
      ThorSceneView.Live own = player == null ? null : ThorSceneView.of(player.getId());
      if (own == null || own.sync().kind() == SceneSync.Kind.GRAB) {
         return;
      }

      var input = event.getInput();
      input.forwardImpulse = 0;
      input.leftImpulse = 0;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   @SubscribeEvent
   public static void onScreen(ScreenEvent.Opening event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && event.getScreen() instanceof PauseScreen && teleporting(minecraft.player)) {
         event.setCanceled(true);
         CoreMessages.sendToServer(new ThorSceneC2SPacket(ThorSceneC2SPacket.Op.TELEPORT_CANCEL, 0, BlockPos.ZERO));
      }
   }

   private static boolean teleporting(LocalPlayer player) {
      ThorSceneView.Live own = ThorSceneView.of(player.getId());
      return own != null && own.sync().kind() == SceneSync.Kind.TELEPORT;
   }

   /** LMB confirms the teleport point; scene victims and Thor in a fatality use no vanilla click. */
   @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null) {
         return;
      }

      if (teleporting(player)) {
         event.setCanceled(true);
         event.setSwingHand(false);
         if (event.isAttack() && aim != null) {
            CoreMessages.sendToServer(new ThorSceneC2SPacket(ThorSceneC2SPacket.Op.TELEPORT_CONFIRM, 0, aim));
         }
         return;
      }

      ThorSceneView.Live own = ThorSceneView.of(player.getId());
      ThorSceneView.Live victim = ThorSceneView.victimOf(player.getId());
      if (own != null && own.sync().kind() == SceneSync.Kind.FATALITY || victim != null && ThorSceneView.holds(victim.sync())) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      ThorSceneView.clear();
      ThorSceneCamera.reset();
      if (ducked) {
         SoundDuck.INSTANCE.release(DUCK);
         ducked = false;
      }
      ROTATED.clear();
      dashLeft = 0;
      aim = null;
   }

   // --- rendering ---

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      if (ThorSceneCamera.active()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onHighlight(RenderHighlightEvent.Block event) {
      if (ThorSceneCamera.active()) {
         event.setCanceled(true);
      }
   }

   /** Plan yaw for both bodies of a fatality and the victim going down to the ground. */
   @SubscribeEvent
   public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
      LivingEntity entity = event.getEntity();
      ThorSceneView.Live own = ThorSceneView.of(entity.getId());
      if (own != null && own.sync().kind() == SceneSync.Kind.FATALITY && own.sync().plan() != null) {
         float yaw = own.sync().plan().thorYawAt(Math.min(ThorSceneView.tick(own, event.getPartialTick()), own.sync().plan().length()));
         face(entity, yaw);
         return;
      }

      ThorSceneView.Live live = ThorSceneView.victimOf(entity.getId());
      FatalityPlan plan = live != null && live.sync().kind() == SceneSync.Kind.FATALITY ? live.sync().plan() : null;
      if (plan == null) {
         return;
      }

      double tick = Math.min(ThorSceneView.tick(live, event.getPartialTick()), plan.length());
      float yaw = plan.victimYawAt(tick);
      face(entity, yaw);
      double lying = plan.victimLying(tick);
      if (lying > 0.001) {
         PoseStack pose = event.getPoseStack();
         pose.pushPose();
         boolean onBack = plan.victimBody(tick) == FatalityScene.Body.ON_BACK;
         pose.mulPose(Axis.YP.rotationDegrees(-yaw));
         pose.mulPose(Axis.XP.rotationDegrees((float)((onBack ? -90.0 : 90.0) * lying)));
         pose.mulPose(Axis.YP.rotationDegrees(yaw));
         pose.translate(0, -0.1 * lying, 0);
         ROTATED.add(entity.getId());
      }
   }

   private static void face(LivingEntity entity, float yaw) {
      entity.yBodyRot = yaw;
      entity.yBodyRotO = yaw;
      entity.yHeadRot = yaw;
      entity.yHeadRotO = yaw;
   }

   @SubscribeEvent
   public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
      if (ROTATED.remove(event.getEntity().getId())) {
         event.getPoseStack().popPose();
      }
   }

   /** Teleport target marker and the torn-out heart in Thor's hand. */
   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || minecraft.level == null) {
         return;
      }

      PoseStack pose = event.getPoseStack();
      Vec3 cam = event.getCamera().getPosition();
      MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
      if (aim != null && teleporting(minecraft.player)) {
         float r = aimValid ? 0.35F : 1.0F;
         float g = aimValid ? 0.8F : 0.25F;
         AABB box = new AABB(aim).inflate(0.02).move(-cam.x, -cam.y, -cam.z);
         LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, 1.0F, 1.0F);
         VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
         LevelRenderer.addChainedFilledBoxVertices(pose, fill, box.minX, box.maxY - 0.06, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, 1.0F, 0.35F);
         buffers.endBatch(RenderType.debugFilledBox());
         buffers.endBatch(RenderType.lines());
      }

      for (ThorSceneView.Live live : ThorSceneView.all().values()) {
         FatalityPlan plan = live.sync().kind() == SceneSync.Kind.FATALITY ? live.sync().plan() : null;
         Entity thor = plan == null || plan.scene() != FatalityScene.HEART_RIP ? null : minecraft.level.getEntity(live.sync().thorId());
         if (thor == null) {
            continue;
         }

         int[] beats = plan.scene().beatTicks();
         double t = plan.mainTick(ThorSceneView.tick(live, event.getPartialTick()));
         if (t >= beats[1] && t < beats[2]) {
            float yaw = Mth.rotLerp(event.getPartialTick(), thor.yRotO, thor.getYRot());
            Vec3 hand = thor.getPosition(event.getPartialTick()).add(SceneFrame.right(yaw).scale(0.38)).add(SceneFrame.forward(yaw).scale(0.55))
               .add(0, 1.15, 0).subtract(cam);
            VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
            LevelRenderer.addChainedFilledBoxVertices(pose, fill, hand.x - 0.09, hand.y - 0.1, hand.z - 0.09, hand.x + 0.09, hand.y + 0.1, hand.z + 0.09,
               0.55F, 0.02F, 0.05F, 1.0F);
            buffers.endBatch(RenderType.debugFilledBox());
         }
      }
   }

   @SubscribeEvent
   public static void onHud(RenderGuiEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.options.hideGui) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      int width = graphics.guiWidth();
      int height = graphics.guiHeight();
      Font font = minecraft.font;
      float partial = event.getPartialTick();
      ThorSceneView.Live own = ThorSceneView.of(player.getId());
      if (own != null && own.sync().kind() == SceneSync.Kind.GRAB) {
         grabStrip(graphics, font, minecraft, own.sync(), width, height);
      } else if (own != null && own.sync().kind() == SceneSync.Kind.TELEPORT) {
         teleportHud(graphics, font, own, width, height, partial);
      }

      ThorSceneView.Live victim = ThorSceneView.victimOf(player.getId());
      if (victim != null && victim.sync().kind() == SceneSync.Kind.GRAB) {
         resistHud(graphics, font, minecraft, victim.sync(), width, height);
      }

      HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
      if (own == null && snapshot != null && snapshot.heroId() == HeroId.THOR && ThorFlags.grabWindow(snapshot.heroFlags())) {
         grabCue(graphics, font, minecraft, width, height, player.tickCount + partial);
      }
   }

   /** Thor's three choices at the bottom: icon + key; the chosen one stays lit. */
   private static void grabStrip(GuiGraphics graphics, Font font, Minecraft minecraft, SceneSync sync, int width, int height) {
      GrabFlow.Stage stage = sync.grabStage();
      if (stage != GrabFlow.Stage.HOLD && stage != GrabFlow.Stage.ACTION) {
         return;
      }

      GrabAction chosen = stage == GrabFlow.Stage.ACTION ? GrabAction.byId(sync.variant()) : null;
      Component[] keys = {minecraft.options.keyAttack.getTranslatedKeyMessage(), minecraft.options.keyUse.getTranslatedKeyMessage(),
         minecraft.options.keyPickItem.getTranslatedKeyMessage()};
      int size = 32;
      int gap = 14;
      int total = 3 * size + 2 * gap;
      int x0 = (width - total) / 2;
      int y = height - 92;
      for (GrabAction action : GrabAction.values()) {
         int i = action.ordinal();
         int x = x0 + i * (size + gap);
         boolean lit = chosen == null || chosen == action;
         graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, chosen == action ? 0xC0FFC860 : 0x80101420);
         if (lit) {
            graphics.blit(ICONS[i], x, y, 0, 0, size, size, size, size);
         } else {
            graphics.setColor(0.4F, 0.4F, 0.4F, 0.6F);
            graphics.blit(ICONS[i], x, y, 0, 0, size, size, size, size);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
         graphics.drawCenteredString(font, keys[i], x + size / 2, y + size + 4, lit ? 0xFFFFFF : 0x808080);
         graphics.drawCenteredString(font, Component.translatable("hud.viltrumitecore.thor.grab." + action.name().toLowerCase(java.util.Locale.ROOT)),
            x + size / 2, y - 12, lit ? 0xFFE0A0 : 0x707070);
      }
   }

   /** The victim's one key, big in the middle, and the escape meter. */
   private static void resistHud(GuiGraphics graphics, Font font, Minecraft minecraft, SceneSync sync, int width, int height) {
      if (sync.grabStage() != GrabFlow.Stage.HOLD || sync.resistKey() < 0) {
         return;
      }

      ResistKey key = ResistKey.byId(sync.resistKey());
      if (key == null) {
         return;
      }

      Component label = switch (key) {
         case W -> minecraft.options.keyUp.getTranslatedKeyMessage();
         case A -> minecraft.options.keyLeft.getTranslatedKeyMessage();
         case S -> minecraft.options.keyDown.getTranslatedKeyMessage();
         case D -> minecraft.options.keyRight.getTranslatedKeyMessage();
         case SPACE -> minecraft.options.keyJump.getTranslatedKeyMessage();
      };
      int cx = width / 2;
      int cy = height / 2 + 30;
      int box = Math.max(28, font.width(label) * 2 + 16);
      boolean pulse = ThorSceneView.clientTicks() / 4 % 2 == 0;
      graphics.fill(cx - box / 2, cy - 16, cx + box / 2, cy + 16, pulse ? 0xD0FFFFFF : 0xD0C8C8C8);
      graphics.pose().pushPose();
      graphics.pose().translate(cx, cy - 7, 0);
      graphics.pose().scale(2.0F, 2.0F, 1.0F);
      graphics.drawCenteredString(font, label, 0, 0, 0x101010);
      graphics.pose().popPose();
      graphics.drawCenteredString(font, Component.translatable("hud.viltrumitecore.thor.grab.resist"), cx, cy + 20, 0xFFFFFF);
      int barWidth = 120;
      int filled = (int)(barWidth * Mth.clamp(sync.meter(), 0.0F, 1.0F));
      graphics.fill(cx - barWidth / 2 - 1, cy + 33, cx + barWidth / 2 + 1, cy + 40, 0xC0000000);
      graphics.fill(cx - barWidth / 2, cy + 34, cx - barWidth / 2 + filled, cy + 39, 0xFF60D0FF);
   }

   private static void teleportHud(GuiGraphics graphics, Font font, ThorSceneView.Live live, int width, int height, float partial) {
      SceneSync sync = live.sync();
      int limit = Math.max(1, sync.variant());
      double left = Math.max(0.0, limit - ThorSceneView.tick(live, partial));
      int barWidth = 160;
      int filled = (int)(barWidth * left / limit);
      int cx = width / 2;
      graphics.fill(cx - barWidth / 2 - 1, 19, cx + barWidth / 2 + 1, 26, 0xC0000000);
      graphics.fill(cx - barWidth / 2, 20, cx - barWidth / 2 + filled, 25, 0xFF80C8FF);
      graphics.drawCenteredString(font, Component.translatable("hud.viltrumitecore.thor.teleport.timer", String.format(java.util.Locale.ROOT, "%.1f", left / 20.0)),
         cx, 30, 0xFFFFFF);
      graphics.drawCenteredString(font, Component.translatable("hud.viltrumitecore.thor.teleport.hint"), cx, height - 70, 0xE0E0E0);
      if (aim != null) {
         graphics.drawCenteredString(font, Component.translatable(aimValid ? "hud.viltrumitecore.thor.teleport.valid" : "hud.viltrumitecore.thor.teleport.invalid"),
            cx, height / 2 + 14, aimValid ? 0x80FF80 : 0xFF6060);
      }
   }

   /** The grab window is open: a pulsing ring round the crosshair and the MMB key. */
   private static void grabCue(GuiGraphics graphics, Font font, Minecraft minecraft, int width, int height, float time) {
      int cx = width / 2;
      int cy = height / 2;
      float pulse = 0.5F + 0.5F * Mth.sin(time * 0.6F);
      int radius = 11 + (int)(3 * pulse);
      int alpha = 0x90 + (int)(0x6F * pulse);
      int color = alpha << 24 | 0xFFC860;
      for (int i = 0; i < 24; i++) {
         double a = Math.PI * 2 * i / 24;
         int x = cx + (int)Math.round(Math.cos(a) * radius);
         int y = cy + (int)Math.round(Math.sin(a) * radius);
         graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
      }
      graphics.drawCenteredString(font, Component.translatable("hud.viltrumitecore.thor.grab.window", minecraft.options.keyPickItem.getTranslatedKeyMessage()),
         cx, cy + radius + 6, 0xFFE0A0);
   }
}
